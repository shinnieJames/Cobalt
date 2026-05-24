package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.call.internal.transport.OfferTransportSpec;
import com.github.auties00.cobalt.call.internal.transport.ice.UdpDatagramTransport;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the WA-relay Allocate handshake against a single te2
 * endpoint and returns a connected {@link UdpDatagramTransport} on
 * success.
 *
 * <p>Phase 3 of the call-runtime buildout. The byte-level packet
 * building reuses the existing {@link WaRelayAllocateRequestBuilder}
 * (parity-tested against captured wasm output) and
 * {@link WaRelayMessageIntegrity}; this class adds:
 * <ul>
 *   <li>UDP socket lifecycle (one socket per allocate attempt);</li>
 *   <li>retry policy (3 attempts at 200 ms RTO);</li>
 *   <li>Allocate-Response parsing — extracts the
 *       {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} attribute and
 *       verifies the {@link WaRelayAttributeType#MESSAGE_INTEGRITY}
 *       MAC against the offer's
 *       {@link OfferTransportSpec#callKey() call key};</li>
 *   <li>response-side surface — returns an
 *       {@link UdpDatagramTransport} bound to the source port that
 *       received the response, ready to carry the next layer's
 *       traffic (Phase 4 DTLS-SRTP).</li>
 * </ul>
 *
 * <p>The {@code <key>} content in WA's offer is the BASE64 TEXT of
 * the relay key — the HMAC-SHA1 input is those ASCII bytes
 * (padding included), <em>not</em> the base64-decoded binary form
 * (per {@link WaRelayMessageIntegrity}'s javadoc).
 */
public final class WaRelayConnector {
    /**
     * STUN transaction-id length in bytes (RFC 5389 §6).
     */
    private static final int TRANSACTION_ID_LENGTH = 12;

    /**
     * Receive-timeout per attempt, in milliseconds. Tuned to the
     * 200 ms RTO that WA's wasm engine empirically uses for the first
     * retransmit.
     */
    private static final int RECV_TIMEOUT_MILLIS = 200;

    /**
     * Total number of allocate attempts before giving up.
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Random source for transaction ids.
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * Result of a successful Allocate handshake.
     *
     * @param transport         the connected {@link UdpDatagramTransport}
     *                          bound to the source port that received
     *                          the response; ready for the next layer
     * @param relayedAddress    the {@code XOR-RELAYED-ADDRESS} the
     *                          server allocated for this client
     * @param transactionId     the 12-byte transaction id used by the
     *                          request — useful for keepalive correlation
     */
    public record Allocation(
            UdpDatagramTransport transport,
            InetSocketAddress relayedAddress,
            byte[] transactionId
    ) {
    }

    /**
     * Performs the Allocate handshake against the te2 endpoint at the
     * given index of {@code spec.te2Endpoints()}.
     *
     * @param spec       the parsed offer transport spec — supplies
     *                   tokens, auth tokens, key, and te2 endpoints
     * @param te2Index   the index into {@code spec.te2Endpoints()} of
     *                   the endpoint to try
     * @return the allocation result
     * @throws WhatsAppCallException.Ice if the handshake fails after
     *                                   {@link #MAX_ATTEMPTS} attempts,
     *                                   if DNS resolution fails, or if
     *                                   the response is malformed /
     *                                   integrity-check rejected
     * @throws IllegalArgumentException  if {@code te2Index} is out of
     *                                   range or required tokens are
     *                                   missing from {@code spec}
     */
    public Allocation connect(OfferTransportSpec spec, int te2Index) {
        Objects.requireNonNull(spec, "spec cannot be null");
        var te2 = spec.te2Endpoints().get(te2Index);
        var relayToken = findToken(spec.tokens(), te2.tokenId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "te2[" + te2Index + "] references token_id=" + te2.tokenId()
                                + " not present in spec"));
        var authToken = findAuthToken(spec.authTokens(), te2.authTokenId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "te2[" + te2Index + "] references auth_token_id=" + te2.authTokenId()
                                + " not present in spec"));
        var callKey = Objects.requireNonNull(spec.callKey(),
                "spec.callKey() must be present for the HMAC stamp");

        InetAddress address;
        try {
            address = InetAddress.getByName(te2.domainName());
        } catch (Exception e) {
            throw new WhatsAppCallException.Ice("DNS lookup failed for " + te2.domainName(), e);
        }
        // WA's te2 endpoints listen on port 3478 (standard TURN port) —
        // verified against the relay-list-updates capture.
        var relayPort = 3478;
        var remote = new InetSocketAddress(address, relayPort);

        var callInfo = new WaRelayCallInfoBuilder()
                .entries(List.of(
                        new WaRelayCallInfoEntryBuilder()
                                .ipVersion(1)
                                .relayId(te2.relayId())
                                .priority(2113929471L)
                                .build()
                ))
                .build();

        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var transactionId = new byte[TRANSACTION_ID_LENGTH];
            random.nextBytes(transactionId);
            var request = WaRelayAllocateRequestBuilder.build(
                    transactionId, relayToken.bytes(), callInfo,
                    address, relayPort, callKey);
            // Open a fresh DatagramSocket per attempt so the receive
            // timeout doesn't accumulate state.
            try (var socket = new DatagramSocket()) {
                socket.setSoTimeout(RECV_TIMEOUT_MILLIS);
                socket.connect(remote);
                socket.send(new DatagramPacket(request, request.length, remote));
                var buffer = new byte[2048];
                var response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                var responseBytes = new byte[response.getLength()];
                System.arraycopy(buffer, 0, responseBytes, 0, response.getLength());
                var allocation = parseAllocateResponse(
                        responseBytes, transactionId, callKey, remote);
                // The temporary socket is closed at the end of the
                // try-with-resources — open a fresh UdpDatagramTransport
                // on a new socket for the higher layers to use.
                var transport = new UdpDatagramTransport(remote);
                return new Allocation(transport, allocation, transactionId);
            } catch (SocketTimeoutException _) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new WhatsAppCallException.Ice(
                            "allocate response timed out after "
                                    + MAX_ATTEMPTS + " attempts against "
                                    + te2.domainName());
                }
                // continue to next attempt
            } catch (IOException e) {
                throw new WhatsAppCallException.Ice(
                        "allocate I/O failed against " + te2.domainName(), e);
            }
        }
        // Unreachable — the loop either returns or throws.
        throw new WhatsAppCallException.Ice("allocate fell through retry loop");
    }

    /**
     * Parses an Allocate Success Response payload, verifies its
     * integrity, and extracts the relayed address.
     *
     * @param responseBytes the response packet bytes
     * @param transactionId the request's transaction id (used to verify
     *                      the response is a match)
     * @param relayKey      the HMAC-SHA1 key
     * @param relayRemote   the te2 endpoint we sent to (for error
     *                      messages)
     * @return the {@code XOR-RELAYED-ADDRESS} attribute decoded as a
     *         standard {@link InetSocketAddress}
     */
    private static InetSocketAddress parseAllocateResponse(
            byte[] responseBytes, byte[] transactionId, byte[] relayKey,
            InetSocketAddress relayRemote) {
        var packet = WaRelayPacket.decode(responseBytes);
        if (packet.messageType() != WaRelayMessageType.ALLOCATE_SUCCESS.wireValue()) {
            throw new WhatsAppCallException.Ice(
                    "allocate response from " + relayRemote
                            + " has unexpected message type 0x"
                            + Integer.toHexString(packet.messageType()));
        }
        if (!Arrays.equals(packet.transactionId(), transactionId)) {
            throw new WhatsAppCallException.Ice(
                    "allocate response transaction-id mismatch from " + relayRemote);
        }
        if (!WaRelayMessageIntegrity.verify(responseBytes, relayKey)) {
            throw new WhatsAppCallException.Ice(
                    "allocate response MAC verification failed from " + relayRemote);
        }
        for (var attr : packet.attributes()) {
            if (attr.type() == WaRelayAttributeType.XOR_RELAYED_ADDRESS.wireValue()) {
                var xor = WaRelayXorAddress.decode(attr.value(), transactionId);
                return new InetSocketAddress(xor.address(), xor.port());
            }
        }
        throw new WhatsAppCallException.Ice(
                "allocate response from " + relayRemote
                        + " did not carry an XOR-RELAYED-ADDRESS attribute");
    }

    /**
     * Looks up the {@link OfferTransportSpec.RelayToken} with the
     * given wire id.
     */
    private static Optional<OfferTransportSpec.RelayToken> findToken(
            List<OfferTransportSpec.RelayToken> tokens, int id) {
        for (var t : tokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * Looks up the {@link OfferTransportSpec.AuthToken} with the
     * given wire id.
     */
    private static Optional<OfferTransportSpec.AuthToken> findAuthToken(
            List<OfferTransportSpec.AuthToken> authTokens, int id) {
        for (var t : authTokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }
}
