package com.github.auties00.cobalt.call.transport.relay;

import com.github.auties00.cobalt.call.signaling.AuthToken;
import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.call.signaling.RelayEndpoint;
import com.github.auties00.cobalt.call.signaling.RelayToken;
import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.transport.ice.UdpDatagramTransport;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the WhatsApp relay Allocate handshake against a single {@code te2} endpoint and returns a
 * connected {@link UdpDatagramTransport} on success.
 *
 * <p>Each {@link #connect(CallRelay, int)} call resolves the chosen endpoint, builds an
 * {@link WaRelayMessageType#ALLOCATE_REQUEST} via {@link WaRelayAllocateRequestBuilder} keyed on the
 * offer's {@link CallRelay#callKey() call key}, and sends it over a fresh UDP socket. The
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
     * Interval at which the keepalive re-sends a fresh Allocate to the chosen relay for the call's
     * lifetime.
     *
     * @implNote This implementation uses 2000 ms, matching the cadence captured from the native
     * desktop client (Frida ws2_32), which re-Allocates the bridged relay roughly every two seconds so
     * the relay does not lapse the allocation and stop forwarding the participant's media.
     */
    private static final long RELAY_KEEPALIVE_MILLIS = 2000;

    /**
     * Provides random transaction ids, fresh per attempt.
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * Holds the local audio SSRC this client publishes, declared to the relay in the sender-subscriptions
     * bind so the SFU forwards the call's media.
     *
     * <p>Defaults to {@code 0} (no subscription sent); {@link #setPublishedAudioSsrc(int)} sets the real
     * local audio SSRC before the Allocate so the keepalive can carry the bind.
     */
    private volatile int publishedAudioSsrc;

    /**
     * Constructs a connector for the native raw-UDP relay transport.
     *
     * <p>This connector drives the Allocate over a raw UDP socket to the regular relay {@code :3478}
     * and then carries bare SRTP over the same socket; it is the transport every native WhatsApp
     * client uses and the one Cobalt selects for the native and web-to-mobile call paths. The web-to-web
     * path instead tunnels the Allocate and SRTP through an SCTP DataChannel established to the edgeray
     * {@code te2} endpoint; that bring-up lives in {@link com.github.auties00.cobalt.call.transport.ActiveCallTransport}
     * and reuses this class only to compose the Allocate request bytes, not to send them.
     *
     * @implNote Cobalt is a JVM application and can open raw UDP sockets directly, so the native
     * transport is a real UDP socket rather than a browser TURN allocation. The raw-UDP shape was
     * verified by a packet capture of a native client's relay bind: the Allocate is sent to the
     * regular relay {@code :3478}, the Allocate Success Response is matched on transaction id, and
     * bare SRTP then rides the same socket with no DTLS to the relay and no DataChannel framing.
     */
    public WaRelayConnector() {
    }

    /**
     * Declares the local audio SSRC this client publishes so the relay receives a sender-subscriptions
     * bind and begins forwarding the call's media.
     *
     * <p>Must be called before {@link #connectAny(CallRelay, String, InetSocketAddress)} for the bind
     * to be sent. A value of {@code 0} leaves the connector in its default state, sending no
     * sender-subscriptions message.
     *
     * @param audioSsrc the local audio SSRC this client sends
     */
    public void setPublishedAudioSsrc(int audioSsrc) {
        this.publishedAudioSsrc = audioSsrc;
    }

    /**
     * Carries the outcome of a successful Allocate handshake.
     *
     * <p>The {@code transport} is the live byte path the Allocate succeeded over and that the call's
     * SRTP traffic then rides; the caller must close it. On the native raw-UDP relay path it is a
     * {@link UdpDatagramTransport} (a raw UDP socket connected to the relay); on the web relay path it
     * is a DataChannel-backed {@link DatagramTransport} that tunnels both the Allocate and the SRTP
     * through the SCTP DataChannel established to the edgeray. The field is typed as the
     * {@link DatagramTransport} interface so both relay modes share one allocation shape.
     *
     * @param transport      the live byte path the Allocate succeeded over; stays open for the call's
     *                       SRTP traffic and must be closed by the caller
     * @param relayedAddress the {@code XOR-RELAYED-ADDRESS} the server allocated for this client
     * @param transactionId  the 12-byte transaction id used by the request, useful for keepalive
     *                       correlation
     */
    public record Allocation(
            DatagramTransport transport,
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
    public Allocation connect(CallRelay spec, int te2Index) {
        return connect(spec, te2Index, null);
    }

    /**
     * Performs the Allocate handshake using the token and call info of the {@code te2} endpoint at the
     * given index, but sends to {@code overrideRemote} when non-{@code null} instead of the te2's own
     * decoded address.
     *
     * <p>The relaylatency-advertised endpoints carry no token of their own, so they are allocated
     * against using a {@code te2} entry's token paired with the relaylatency transport address.
     *
     * @param spec          the parsed offer transport spec
     * @param te2Index      the index of the te2 entry supplying the token and call info
     * @param overrideRemote the relay address to send to, or {@code null} to use the te2's own address
     * @return the allocation result
     */
    public Allocation connect(CallRelay spec, int te2Index, InetSocketAddress overrideRemote) {
        Objects.requireNonNull(spec, "spec cannot be null");
        var te2 = List.copyOf(spec.endpoints()).get(te2Index);
        var relayToken = findToken(List.copyOf(spec.tokens()), te2.tokenId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "te2[" + te2Index + "] references token_id=" + te2.tokenId()
                                + " not present in spec"));
        // The offer-ACK <relay> for a companion->desktop 1:1 call carries <token> (selected above by
        // te2.token_id) but no <auth_token>, and the captured <te2> entries carry no auth_token_id. The
        // auth-token value is never used in the Allocate anyway (both the relay HMAC stamp and the ICE
        // STUN MESSAGE-INTEGRITY are keyed by the <key>), so it is intentionally not resolved here;
        // resolving it with orElseThrow previously bailed the entire media plane when <auth_token> was
        // absent.
        var callKey = spec.callKey().orElseThrow(() -> new IllegalStateException(
                "spec.callKey() must be present for the HMAC stamp"));

        // The <te2> element's content bytes ARE the relay address: 4 bytes IPv4 + 2 bytes port
        // (big-endian), or 16 bytes IPv6 + 2 bytes port. The auth-token + relay-key are bound to
        // the specific relay endpoint the server picked, so DNS-resolving the domain name to a
        // different IP routes the Allocate request to the wrong relay (and times out). Falls back
        // to DNS only when the content is missing or malformed.
        var remote = overrideRemote != null ? overrideRemote : decodeTe2Endpoint(te2);
        var address = remote.getAddress();
        var relayPort = remote.getPort();

        // WA_CALL_INFO must enumerate the FULL (ipVersion x relay) candidate matrix, matching the
        // native Desktop client; a single entry makes the relay silently drop the Allocate. The
        // captured working call-info is, for each ipVersion in {any, IPv4, IPv6}, an aggregate entry
        // (no relay) followed by one entry per distinct relay, each carrying a descending ICE-style
        // priority hint. The priority values are the client's preference and are not validated by the
        // relay, so a descending sequence suffices.
        var distinctRelayIds = new java.util.LinkedHashSet<Integer>();
        for (var ep : spec.endpoints()) {
            distinctRelayIds.add(ep.relayId());
        }
        System.getLogger(WaRelayConnector.class.getName()).log(System.Logger.Level.INFO,
                "relay block distinct relay_ids=" + distinctRelayIds + " endpointCount=" + spec.endpoints().size());
        var entries = new java.util.ArrayList<WaRelayCallInfoEntry>();
        var priority = 4272282031L;
        for (Integer ipVersion : new Integer[]{null, 1, 2}) {
            entries.add(new WaRelayCallInfoEntryBuilder()
                    .ipVersion(ipVersion).relayId(null).priority(priority).build());
            priority -= 100_000_000L;
            for (var relayId : distinctRelayIds) {
                if (relayId == 0) {
                    continue; // 0 collapses to the aggregate (null) entry already emitted
                }
                entries.add(new WaRelayCallInfoEntryBuilder()
                        .ipVersion(ipVersion).relayId(relayId).priority(priority).build());
                priority -= 100_000_000L;
            }
        }
        var callInfo = new WaRelayCallInfoBuilder()
                .entries(entries)
                .build();

        final var subSsrc = publishedAudioSsrc;
        final var subDescriptors = subSsrc == 0
                ? null
                : WaRelaySubscriptionBuilder.audioSenderSubscription(subSsrc);

        return connectViaRawUdp(te2, relayToken, callInfo, address, relayPort, callKey, remote, subDescriptors);
    }

    /**
     * Performs the Allocate handshake against the regular relay over a raw UDP socket and returns the
     * live {@link UdpDatagramTransport} ready to carry bare SRTP.
     *
     * <p>This is the transport every real WhatsApp client uses on the media plane (verified by A/B
     * packet capture): the Allocate Request ({@code STUN type 0x0003}) is sent directly to the relay's
     * {@code :3478} address; the Allocate Success Response ({@code 0x0103}) is matched on transaction
     * id and integrity-checked; and thereafter bare SRTP/SRTCP is written straight to the same socket
     * (no warp framing, no DTLS to the relay, no DataChannel). The relay forwards the call's media off
     * the Allocate plus a keepalive that re-sends a fresh Allocate every {@link #RELAY_KEEPALIVE_MILLIS}
     * for the call's lifetime; no STUN binding checks are exchanged with the relay.
     *
     * @param te2            the relay endpoint supplying the relay token (its address is overridden by
     *                       {@code remote}, the relaylatency-advertised regular-relay address)
     * @param relayToken     the relay token referenced by {@code te2}
     * @param callInfo       the {@code WA-CALL-INFO} payload
     * @param address        the relay address the Allocate's relayed-transport hint decodes to
     * @param relayPort      the relay port
     * @param callKey        the HMAC key for the integrity stamp
     * @param remote         the regular-relay socket address to send to
     * @param subDescriptors the serialized {@code 0x4024} sender-subscription stream descriptors, or
     *                       {@code null} when no audio SSRC has been published yet
     * @return the allocation carrying the live raw UDP socket
     * @throws WhatsAppCallException.Ice if the socket open or the Allocate handshake fails
     */
    private Allocation connectViaRawUdp(RelayEndpoint te2,
                                        RelayToken relayToken,
                                        WaRelayCallInfo callInfo,
                                        InetAddress address,
                                        int relayPort,
                                        byte[] callKey,
                                        java.net.InetSocketAddress remote,
                                        byte[] subDescriptors) {
        var logger = System.getLogger(WaRelayConnector.class.getName());
        var udp = new UdpDatagramTransport(remote);
        var responses = new java.util.concurrent.LinkedBlockingQueue<byte[]>();
        // During the Allocate handshake, queue only STUN-range datagrams (first byte <= 0x03); media and
        // 0x08 relay-keepalive replies are ignored. ActiveCallTransport replaces this listener with the
        // SRTP demux once it wires the media plane.
        udp.setInboundListener(datagram -> {
            if (datagram.length > 0 && (datagram[0] & 0xFF) <= 0x03) {
                responses.offer(datagram);
            }
        });
        var ok = false;
        if (subDescriptors != null) {
            logger.log(System.Logger.Level.INFO,
                    "RELAY-SUBSCRIBE (0x4024 stream-descriptor on allocate, method 3) ssrc=0x"
                            + Integer.toHexString(publishedAudioSsrc) + " to " + remote);
        }
        try {
            for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                var txid = new byte[TRANSACTION_ID_LENGTH];
                random.nextBytes(txid);
                var request = WaRelayAllocateRequestBuilder.build(
                        txid, relayToken.bytes(), callInfo, address, relayPort, callKey, subDescriptors);
                logger.log(System.Logger.Level.INFO,
                        "ALLOCATE-REQ (raw-udp) to " + remote + " len=" + request.length);
                udp.send(request);
                byte[] responseBytes;
                try {
                    responseBytes = responses.poll(RECV_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WhatsAppCallException.Ice(
                            "Interrupted awaiting Allocate over UDP from " + remote, e);
                }
                if (responseBytes == null) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw new WhatsAppCallException.Ice(
                                "allocate-over-udp timed out after " + MAX_ATTEMPTS
                                        + " attempts against " + te2.domainName() + " (" + remote + ")");
                    }
                    continue;
                }
                var relayedAddress = parseAllocateResponse(responseBytes, txid, callKey, remote);
                var kaToken = relayToken.bytes();
                final var kaAddr = address;
                final var kaPort = relayPort;
                Thread.ofVirtual().name("relay-udp-keepalive").start(() -> {
                    try {
                        while (true) {
                            Thread.sleep(RELAY_KEEPALIVE_MILLIS);
                            var kaTxid = new byte[TRANSACTION_ID_LENGTH];
                            random.nextBytes(kaTxid);
                            udp.send(WaRelayAllocateRequestBuilder.build(
                                    kaTxid, kaToken, callInfo, kaAddr, kaPort, callKey, subDescriptors));
                        }
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException _) {
                        // socket closed at call teardown; stop keepalive
                    }
                });
                ok = true;
                return new Allocation(udp, relayedAddress, txid);
            }
            throw new WhatsAppCallException.Ice("allocate-over-udp fell through retry loop");
        } finally {
            if (!ok) {
                udp.close();
            }
        }
    }

    /**
     * Allocates a raw UDP socket against the relaylatency-elected regular relay, using the relay token
     * of the {@code te2} entry whose {@code relay_name} matches the elected relay.
     *
     * <p>The WhatsApp SFU bridges a call's two legs only when both endpoints allocate on the same relay
     * cluster, which the server elects through the {@code <relaylatency>} probe exchange. Each cluster is
     * advertised at two addresses: the {@code <relaylatency><te>} regular-relay address (which speaks raw
     * STUN/SRTP) and the offer-ACK {@code <te2>} edgeray address (a decoy that the media plane never
     * uses). This method therefore sends the Allocate over a raw UDP socket to the elected relay's
     * {@code <relaylatency>} address ({@code preferredEndpoint}), paired with the matching te2 entry's
     * token; if that token is rejected it falls back to trying the remaining te2 tokens against the same
     * address.
     *
     * @param spec               the parsed offer transport spec (supplies te2 tokens)
     * @param preferredRelayName the {@code relay_name} of the relaylatency-elected relay, used to pick
     *                           the token; may be {@code null}
     * @param preferredEndpoint  the elected relay's {@code <relaylatency><te>} address to allocate to
     * @return the first successful allocation
     * @throws NullPointerException      if {@code spec} or {@code preferredEndpoint} is {@code null}
     * @throws WhatsAppCallException.Ice if no te2 token produced a successful Allocate
     */
    public Allocation connectAny(CallRelay spec, String preferredRelayName, InetSocketAddress preferredEndpoint) {
        Objects.requireNonNull(spec, "spec cannot be null");
        Objects.requireNonNull(preferredEndpoint, "preferredEndpoint cannot be null");
        var logger = System.getLogger(WaRelayConnector.class.getName());
        var endpoints = List.copyOf(spec.endpoints());
        if (endpoints.isEmpty()) {
            throw new WhatsAppCallException.Ice("relay-tokens block carried no te2 endpoints");
        }
        // Order the te2 indices so the one whose relay_name matches the elected relay is tried first
        // (its token is bound to that relay), then the rest as fallbacks against the same address.
        var order = new java.util.ArrayList<Integer>(endpoints.size());
        for (var i = 0; i < endpoints.size(); i++) {
            if (preferredRelayName != null && preferredRelayName.equals(endpoints.get(i).relayName())) {
                order.add(i);
            }
        }
        for (var i = 0; i < endpoints.size(); i++) {
            if (!order.contains(i)) {
                order.add(i);
            }
        }
        WhatsAppCallException.Ice last = null;
        for (var i : order) {
            try {
                logger.log(System.Logger.Level.INFO,
                        "relaylatency raw-UDP Allocate to " + preferredEndpoint + " (token from te2[" + i
                                + "] relay " + endpoints.get(i).relayName() + ")");
                return connect(spec, i, preferredEndpoint);
            } catch (WhatsAppCallException.Ice e) {
                logger.log(System.Logger.Level.INFO,
                        "te2[" + i + "] token rejected at " + preferredEndpoint + ": " + e.getMessage());
                last = e;
            } catch (IllegalArgumentException e) {
                logger.log(System.Logger.Level.INFO, "te2[" + i + "] skipped: " + e.getMessage());
            }
        }
        throw last != null
                ? last
                : new WhatsAppCallException.Ice("no te2 token produced a raw-UDP allocation to " + preferredEndpoint);
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
        System.Logger logger = System.getLogger(WaRelayConnector.class.getName());
        var attrSummary = new StringBuilder();
        for (var attr : packet.attributes()) {
            attrSummary.append(String.format(" 0x%04x(len=%d)",
                    attr.type(), attr.value() == null ? 0 : attr.value().length));
        }
        logger.log(System.Logger.Level.INFO,
                String.format("Allocate response: msgType=0x%04x len=%d attrs=[%s]",
                        packet.messageType(), responseBytes.length, attrSummary.toString().trim()));
        if (packet.messageType() != WaRelayMessageType.ALLOCATE_SUCCESS.wireValue()) {
            // Pull out an ERROR-CODE attribute (0x0009) value bytes for diagnostics if present.
            String errorBlurb = "";
            for (var attr : packet.attributes()) {
                if (attr.type() == 0x0009) {
                    errorBlurb = " ERROR-CODE bytes=" + java.util.HexFormat.of().formatHex(attr.value());
                    break;
                }
            }
            throw new WhatsAppCallException.Ice(
                    "allocate response from " + relayRemote
                            + " has unexpected message type 0x"
                            + Integer.toHexString(packet.messageType()) + errorBlurb);
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
            if (attr.type() == WaRelayAttributeType.WA_RELAYED_ADDRESS.wireValue()) {
                logger.log(System.Logger.Level.INFO,
                        "WA_RELAYED_ADDRESS bytes=" + java.util.HexFormat.of().formatHex(attr.value()));
                return decodeWaRelayedAddress(attr.value(), transactionId);
            }
            if (attr.type() == WaRelayAttributeType.XOR_RELAYED_ADDRESS.wireValue()) {
                var xor = WaRelayXorAddress.decode(attr.value(), transactionId);
                return new InetSocketAddress(xor.address(), xor.port());
            }
        }
        throw new WhatsAppCallException.Ice(
                "allocate response from " + relayRemote
                        + " did not carry a relayed-address attribute (looked for 0x4002 or 0x0016)");
    }

    /**
     * Decodes the WA-specific {@code WA-RELAYED-ADDRESS} (0x4002) 8-byte (IPv4) or 20-byte (IPv6)
     * attribute value.
     *
     * <p>The 8-byte IPv4 wire layout is the same as the RFC 5389 {@code XOR-MAPPED-ADDRESS}
     * format but with the address-family byte cleared to {@code 0x00}: 1 byte reserved,
     * 1 byte family (often zero in this WA-specific encoding), 2 bytes XOR'd port, 4 bytes XOR'd
     * IPv4 address. We treat any non-IPv6 length (8 bytes) as IPv4 regardless of the family byte
     * value.
     *
     * @param value         the attribute value bytes (8 for IPv4, 20 for IPv6)
     * @param transactionId the request's transaction id, used as part of the XOR mask
     * @return the decoded relayed transport address
     */
    private static InetSocketAddress decodeWaRelayedAddress(byte[] value, byte[] transactionId) {
        if (value == null || (value.length != 8 && value.length != 20)) {
            throw new WhatsAppCallException.Ice(
                    "WA-RELAYED-ADDRESS attribute has unexpected length " + (value == null ? 0 : value.length));
        }
        var withFamily = new byte[value.length];
        System.arraycopy(value, 0, withFamily, 0, value.length);
        // Force the address-family byte to the canonical RFC 5389 value so WaRelayXorAddress.decode
        // accepts it. The WA-specific 0x4002 encoding zero-fills the family byte rather than
        // setting 0x01 / 0x02; length is the authoritative discriminator.
        withFamily[1] = (byte) (value.length == 8 ? 0x01 : 0x02);
        var xor = WaRelayXorAddress.decode(withFamily, transactionId);
        return new InetSocketAddress(xor.address(), xor.port());
    }

    /**
     * Decodes the {@code <te2>} content bytes into the relay's {@link InetSocketAddress}.
     *
     * <p>The wire format is the relay's address followed by a big-endian 2-byte port. A 6-byte
     * payload is {@code 4 bytes IPv4 + 2 bytes port}; an 18-byte payload is
     * {@code 16 bytes IPv6 + 2 bytes port}.
     *
     * <p>Using the te2 bytes directly is required: the auth-token + relay-key returned in the
     * offer ACK are bound to the specific relay endpoint the server selected for this call.
     * DNS-resolving the domain name to a different load-balanced IP routes the Allocate request
     * to the wrong relay, which silently drops the request.
     *
     * @param te2 the candidate endpoint
     * @return the resolved relay socket address
     * @throws WhatsAppCallException.Ice if the content bytes are not the expected 6 or 18 bytes
     */
    private static InetSocketAddress decodeTe2Endpoint(RelayEndpoint te2) {
        var bytes = te2.bytes();
        if (bytes.length != 6 && bytes.length != 18) {
            throw new WhatsAppCallException.Ice(
                    "te2 endpoint content must be 6 (IPv4+port) or 18 (IPv6+port) bytes; got "
                            + bytes.length);
        }
        var addressLen = bytes.length == 6 ? 4 : 16;
        var addressBytes = new byte[addressLen];
        System.arraycopy(bytes, 0, addressBytes, 0, addressLen);
        var port = ((bytes[addressLen] & 0xFF) << 8) | (bytes[addressLen + 1] & 0xFF);
        try {
            System.getLogger(WaRelayConnector.class.getName()).log(System.Logger.Level.INFO,
                    "te2 '" + te2.domainName() + "' rawContent=" + java.util.HexFormat.of().formatHex(bytes)
                            + " -> decoded=" + InetAddress.getByAddress(addressBytes).getHostAddress() + ":" + port);
            return new InetSocketAddress(InetAddress.getByAddress(addressBytes), port);
        } catch (Exception e) {
            throw new WhatsAppCallException.Ice(
                    "te2 endpoint address bytes are invalid for " + te2.domainName(), e);
        }
    }

    /**
     * Looks up the {@link RelayToken} with the given wire id.
     *
     * @param tokens the candidate relay tokens
     * @param id     the wire id to match
     * @return the matching token, or {@link Optional#empty()} when none has that id
     */
    private static Optional<RelayToken> findToken(
            List<RelayToken> tokens, int id) {
        for (var t : tokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }

    /**
     * Looks up the {@link AuthToken} with the given wire id.
     *
     * @param authTokens the candidate auth tokens
     * @param id         the wire id to match
     * @return the matching auth token, or {@link Optional#empty()} when none has that id
     */
    private static Optional<AuthToken> findAuthToken(
            List<AuthToken> authTokens, int id) {
        for (var t : authTokens) {
            if (t.id() == id) return Optional.of(t);
        }
        return Optional.empty();
    }
}
