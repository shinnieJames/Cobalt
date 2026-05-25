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
 * Drives the WhatsApp relay Allocate handshake against a single {@code te2} endpoint and returns a
 * connected {@link UdpDatagramTransport} on success.
 *
 * <p>Each {@link #connect(OfferTransportSpec, int)} call resolves the chosen endpoint, builds an
 * {@link WaRelayMessageType#ALLOCATE_REQUEST} via {@link WaRelayAllocateRequestBuilder} keyed on the
 * offer's {@link OfferTransportSpec#callKey() call key}, and sends it over a fresh UDP socket. The
 * Allocate Success Response is matched on transaction id, integrity-checked via
 * {@link WaRelayMessageIntegrity}, and mined for its {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS}
 * attribute. The request is retried up to {@link #MAX_ATTEMPTS} times at the
 * {@link #RECV_TIMEOUT_MILLIS}-millisecond retransmit timeout, and on success the result is an
 * {@link UdpDatagramTransport} ready for the next layer's traffic.
 *
 * @implNote This implementation feeds the offer's call key directly to the HMAC stamp: the
 * {@code <key>} content in WhatsApp's offer is the base64 text of the relay key, and
 * {@link WaRelayMessageIntegrity} keys the HMAC-SHA1 on those ASCII bytes (padding included) rather
 * than the base64-decoded binary form.
 */
public final class WaRelayConnector {
    /**
     * Holds the STUN transaction-id length in bytes (RFC 5389 section 6).
     */
    private static final int TRANSACTION_ID_LENGTH = 12;

    /**
     * Holds the per-attempt receive timeout in milliseconds.
     *
     * @implNote This implementation uses 200 milliseconds to match the first-retransmit timeout that
     * WhatsApp's wasm engine empirically applies.
     */
    private static final int RECV_TIMEOUT_MILLIS = 200;

    /**
     * Holds the total number of Allocate attempts before the handshake gives up.
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Provides random transaction ids, fresh per attempt.
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * Carries the outcome of a successful Allocate handshake.
     *
     * @param transport      the connected {@link UdpDatagramTransport} bound to the source port that
     *                       received the response, ready for the next layer
     * @param relayedAddress the {@code XOR-RELAYED-ADDRESS} the server allocated for this client
     * @param transactionId  the 12-byte transaction id used by the request, useful for keepalive
     *                       correlation
     */
    public record Allocation(
            UdpDatagramTransport transport,
            InetSocketAddress relayedAddress,
            byte[] transactionId
    ) {
    }

    /**
     * Performs the Allocate handshake against the {@code te2} endpoint at the given index of the
     * spec's endpoint list.
     *
     * <p>Resolves the relay and auth tokens the endpoint references, resolves the endpoint's domain
     * name to an address, builds a single-entry IPv4 {@link WaRelayCallInfo}, and retransmits the
     * request up to {@link #MAX_ATTEMPTS} times, parsing the first valid Allocate Success Response.
     *
     * @param spec     the parsed offer transport spec supplying tokens, auth tokens, key, and
     *                 {@code te2} endpoints
     * @param te2Index the index into the spec's endpoint list of the endpoint to try
     * @return the allocation result
     * @throws NullPointerException      if {@code spec} is {@code null}
     * @throws WhatsAppCallException.Ice if DNS resolution fails, the handshake times out after
     *                                   {@link #MAX_ATTEMPTS} attempts, or the response is malformed,
     *                                   mismatched, or integrity-check rejected
     * @throws IllegalArgumentException  if {@code te2Index} is out of range or the spec is missing a
     *                                   token or auth token the endpoint references
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
        // 3478 is the standard TURN port; verified against the relay-list-updates capture.
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
                var transport = new UdpDatagramTransport(remote);
                return new Allocation(transport, allocation, transactionId);
            } catch (SocketTimeoutException _) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new WhatsAppCallException.Ice(
                            "allocate response timed out after "
                                    + MAX_ATTEMPTS + " attempts against "
                                    + te2.domainName());
                }
            } catch (IOException e) {
                throw new WhatsAppCallException.Ice(
                        "allocate I/O failed against " + te2.domainName(), e);
            }
        }
        throw new WhatsAppCallException.Ice("allocate fell through retry loop");
    }

    /**
     * Parses an Allocate Success Response, verifies its integrity, and extracts the relayed address.
     *
     * <p>Decodes the packet, asserts the message type is {@link WaRelayMessageType#ALLOCATE_SUCCESS}
     * and the transaction id matches the request, verifies the {@code MESSAGE-INTEGRITY} attribute via
     * {@link WaRelayMessageIntegrity#verify(byte[], byte[])}, and decodes the
     * {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} attribute into an {@link InetSocketAddress}.
     *
     * @param responseBytes the response packet bytes
     * @param transactionId the request's transaction id, used to confirm the response is a match
     * @param relayKey      the HMAC-SHA1 key
     * @param relayRemote   the {@code te2} endpoint that was sent to, used in error messages
     * @return the {@code XOR-RELAYED-ADDRESS} attribute decoded as an {@link InetSocketAddress}
     * @throws WhatsAppCallException.Ice if the message type is unexpected, the transaction id does not
     *                                   match, the MAC verification fails, or no
     *                                   {@code XOR-RELAYED-ADDRESS} attribute is present
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
     * Looks up the {@link OfferTransportSpec.RelayToken} with the given wire id.
     *
     * @param tokens the candidate relay tokens
     * @param id     the wire id to match
     * @return the matching token, or {@link Optional#empty()} when none has that id
     */
    private static Optional<OfferTransportSpec.RelayToken> findToken(
            List<OfferTransportSpec.RelayToken> tokens, int id) {
        for (var t : tokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * Looks up the {@link OfferTransportSpec.AuthToken} with the given wire id.
     *
     * @param authTokens the candidate auth tokens
     * @param id         the wire id to match
     * @return the matching auth token, or {@link Optional#empty()} when none has that id
     */
    private static Optional<OfferTransportSpec.AuthToken> findAuthToken(
            List<OfferTransportSpec.AuthToken> authTokens, int id) {
        for (var t : authTokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }
}
