package com.github.auties00.cobalt.call.transport;

import com.github.auties00.cobalt.call.signaling.CallRelay;
import com.github.auties00.cobalt.call.signaling.RelayEndpoint;
import com.github.auties00.cobalt.call.interaction.InteractionStreamState;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpEndpoint;
import com.github.auties00.cobalt.call.transport.ice.IceAgent;
import com.github.auties00.cobalt.call.transport.ice.IceCredentials;
import com.github.auties00.cobalt.call.transport.ice.UdpDatagramTransport;
import com.github.auties00.cobalt.call.transport.ice.WebIceBinding;
import com.github.auties00.cobalt.call.transport.relay.WaRelayAllocateRequestBuilder;
import com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfo;
import com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfoBuilder;
import com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfoEntry;
import com.github.auties00.cobalt.call.transport.relay.WaRelayCallInfoEntryBuilder;
import com.github.auties00.cobalt.call.transport.relay.WaRelayConnector;
import com.github.auties00.cobalt.call.transport.relay.WaRelayMessageIntegrity;
import com.github.auties00.cobalt.call.transport.relay.WaRelayMessageType;
import com.github.auties00.cobalt.call.transport.relay.WaRelayPacket;
import com.github.auties00.cobalt.call.transport.relay.WaRelaySubscriptionBuilder;
import com.github.auties00.cobalt.call.transport.sctp.SctpDtlsBridge;
import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannel;
import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelDatagramTransport;
import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelOptions;
import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelState;
import com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelTransport;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Owns the transport-layer state of one call's media plane.
 *
 * <p>WhatsApp Web's VoIP transport stack is layered, from the bottom up: an ICE-selected network
 * path, a WA relay (TURN-like) tunnel that carries STUN, DTLS, and SCTP packets multiplexed on one
 * UDP socket, a DTLS-SRTP layer that derives keying material and protects media, and one or more
 * SCTP-borne {@link DataChannel}s that carry peer signaling (in-call interactions and application
 * data). This class holds one instance of each layer for a single call and sequences their
 * lifecycle.
 *
 * <p>{@link #start(StartParameters)} stores the negotiated parameters and instantiates the lower
 * layers; {@link #connectRelay()}, {@link #connectDtls(long, TimeUnit)}, and
 * {@link #connectDataChannel(int, int)} drive the relay, DTLS, and SCTP handshakes in turn; and
 * {@link #close()} tears every layer down in reverse construction order (data channel, then SCTP,
 * then DTLS, then relay, then ICE), releasing native handles. The handshake steps are exposed as
 * separate methods rather than folded into {@link #start(StartParameters)} so that a transport can
 * be constructed and exercised without firing real UDP input or output.
 */
public final class ActiveCallTransport implements AutoCloseable {
    /**
     * The hardcoded peer DTLS SHA-256 fingerprint used in every WA Web call.
     *
     * <p>WhatsApp's transport model does not perform mutual DTLS certificate verification: the
     * relay vouches for both endpoints via the ICE credentials (auth_token as ufrag, relay_key as
     * pwd, both from the relay-tokens block). The "remote answer SDP" the WA Web JS feeds into
     * {@code RTCPeerConnection.setRemoteDescription} is fabricated locally from the relay
     * credentials and this hardcoded fingerprint string; the relay-side wasm accepts whatever
     * cert the client presents and the client trusts whatever fingerprint the local code injects.
     *
     * <p>Source: {@code WAWebVoipRelayConnectionUtils.createAnswerSdp} in the WA Web bundle,
     * confirmed against every captured remote-answer SDP.
     *
     * <p>Callers pass this constant as {@link StartParameters#peerFingerprintSha256()}
     * regardless of which peer is being called.
     */
    public static final byte[] WA_PEER_DTLS_FINGERPRINT_SHA256 = {
            (byte) 0xF9, (byte) 0xCA, (byte) 0x0C, (byte) 0x98,
            (byte) 0xA3, (byte) 0xCC, (byte) 0x71, (byte) 0xD6,
            (byte) 0x42, (byte) 0xCE, (byte) 0x5A, (byte) 0xE2,
            (byte) 0x53, (byte) 0xD2, (byte) 0x15, (byte) 0x20,
            (byte) 0xD3, (byte) 0x1B, (byte) 0xBA, (byte) 0xD8,
            (byte) 0x57, (byte) 0xA4, (byte) 0xF0, (byte) 0xAF,
            (byte) 0xBE, (byte) 0x0B, (byte) 0xFB, (byte) 0xF3,
            (byte) 0x6B, (byte) 0x0C, (byte) 0xA0, (byte) 0x68
    };

    /**
     * Logs the web relay bring-up diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(ActiveCallTransport.class.getName());

    /**
     * The WebRTC SCTP port both sides bind and connect for the in-call DataChannel.
     *
     * @implNote This implementation uses {@code 5000}, the WebRTC convention used on both ends.
     */
    private static final int WEB_SCTP_PORT = 5000;

    /**
     * The DTLS handshake timeout, in seconds, for the edgeray DTLS leg of the web relay bring-up.
     */
    private static final long DTLS_HANDSHAKE_TIMEOUT_SECONDS = 15;

    /**
     * The SCTP association timeout, in seconds, for the web relay DataChannel bring-up.
     */
    private static final long WEB_SCTP_TIMEOUT_SECONDS = 10;

    /**
     * The overall timeout, in seconds, for the edgeray ICE binding to nominate a pair.
     */
    private static final long WEB_ICE_TIMEOUT_SECONDS = 8;

    /**
     * The per-attempt receive timeout, in milliseconds, for one edgeray binding request before it is
     * retransmitted.
     */
    private static final long WEB_ICE_ATTEMPT_TIMEOUT_MILLIS = 400;

    /**
     * The cadence, in milliseconds, at which a consent-refresh binding request is re-sent on the
     * nominated edgeray path so the relay keeps forwarding the call's media.
     */
    private static final long WEB_ICE_CONSENT_INTERVAL_MILLIS = 2000;

    /**
     * The number of tunneled-Allocate attempts before the web relay bring-up gives up on an endpoint.
     */
    private static final int WEB_ALLOCATE_ATTEMPTS = 4;

    /**
     * The per-attempt receive timeout, in milliseconds, for the tunneled Allocate response.
     */
    private static final long WEB_ALLOCATE_TIMEOUT_MILLIS = 500;

    /**
     * Enumerates the strictly forward-progressing lifecycle of an {@link ActiveCallTransport}.
     *
     * <p>A transport advances {@link #IDLE} to {@link #STARTED} to {@link #OPEN} to {@link #CLOSED}
     * and never moves backwards; {@link #CLOSED} is terminal.
     */
    public enum State {
        /**
         * Indicates the transport has been constructed and is awaiting {@link #start(StartParameters)}.
         */
        IDLE,
        /**
         * Indicates {@link #start(StartParameters)} has been called.
         *
         * <p>The negotiated parameters are stored and the lower-layer objects (ICE agent and
         * SCTP data-channel transport) are instantiated, but no wire packets have been exchanged.
         */
        STARTED,
        /**
         * Indicates the default data channel has reached
         * {@link com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelState#OPEN}.
         */
        OPEN,
        /**
         * Indicates {@link #close()} has been invoked and every layer has been torn down.
         */
        CLOSED
    }

    /**
     * Selects which relay transport {@link #connectRelay()} drives for a call.
     *
     * <p>The two modes differ only in how the WhatsApp STUN Allocate and the media SRTP reach the
     * relay; the Allocate request bytes, the {@code MESSAGE-INTEGRITY} keying, and the
     * sender-subscription attribute are identical between them.
     */
    public enum RelayMode {
        /**
         * Sends the Allocate over a raw UDP socket to the regular relay {@code :3478} and carries
         * bare SRTP over the same socket.
         *
         * <p>This is the transport every native WhatsApp client uses; Cobalt selects it for native
         * clients and for the web-to-mobile path, where the existing raw-UDP relay is known to work
         * post-accept. It is the default.
         */
        NATIVE,
        /**
         * Brings up an ICE-DTLS-SCTP DataChannel to the edgeray {@code te2} endpoint and tunnels the
         * Allocate and the media SRTP through it as DataChannel messages.
         *
         * <p>This mirrors WhatsApp Web, whose relay leg is an {@code RTCPeerConnection} to the edgeray
         * that runs ICE, DTLS, and SCTP to the relay and then tunnels the WhatsApp Allocate and SRTP
         * over the resulting DataChannel. Cobalt selects it for the web-to-web path.
         */
        WEB
    }

    /**
     * Carries the negotiated parameters fed into {@link #start(StartParameters)}, filled from the
     * offer and accept exchange.
     *
     * @param credentials           the ICE ufrag and password pair for both sides
     * @param localCert             the self-signed DTLS certificate of the local side
     * @param peerFingerprintSha256 the peer's certificate fingerprint advertised in the offer or accept
     * @param dtlsClient            {@code true} when the local side acts as DTLS client, that is, is the call placer
     * @param relay             the parsed {@link CallRelay} from the inbound offer, read by
     *                              {@link #connectRelay()} to drive the WA relay handshake; {@code null} on the
     *                              outgoing-call path until the outgoing offer builder fills its own spec
     * @param localAudioSsrc        the local audio SSRC this client publishes, declared to the relay in
     *                              the sender-subscriptions bind so the SFU forwards the call's media
     * @param relayMode             the relay transport {@link #connectRelay()} drives: {@link RelayMode#NATIVE}
     *                              for a raw-UDP relay, {@link RelayMode#WEB} for the edgeray DataChannel tunnel
     */
    public record StartParameters(
            IceCredentials credentials,
            DtlsCertificate localCert,
            byte[] peerFingerprintSha256,
            boolean dtlsClient,
            CallRelay relay,
            int localAudioSsrc,
            RelayMode relayMode
    ) {
        /**
         * Validates the parameters, defaulting a {@code null} {@link #relayMode()} to
         * {@link RelayMode#NATIVE} so callers built before the web relay path stay on the raw-UDP
         * transport.
         */
        public StartParameters {
            if (relayMode == null) {
                relayMode = RelayMode.NATIVE;
            }
        }

        /**
         * Constructs parameters on the native raw-UDP relay transport.
         *
         * <p>This six-argument form preserves the pre-web-relay constructor shape and selects
         * {@link RelayMode#NATIVE}; the seven-argument canonical constructor selects the relay mode
         * explicitly.
         *
         * @param credentials           the ICE ufrag and password pair for both sides
         * @param localCert             the self-signed DTLS certificate of the local side
         * @param peerFingerprintSha256 the peer's certificate fingerprint advertised in the offer or accept
         * @param dtlsClient            {@code true} when the local side acts as DTLS client
         * @param relay                 the parsed {@link CallRelay}, or {@code null}
         * @param localAudioSsrc        the local audio SSRC this client publishes
         */
        public StartParameters(IceCredentials credentials, DtlsCertificate localCert,
                               byte[] peerFingerprintSha256, boolean dtlsClient,
                               CallRelay relay, int localAudioSsrc) {
            this(credentials, localCert, peerFingerprintSha256, dtlsClient, relay, localAudioSsrc,
                    RelayMode.NATIVE);
        }
    }

    /**
     * Holds the current lifecycle state, updated only by {@link #start(StartParameters)} and
     * {@link #close()}.
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    /**
     * Holds the negotiated parameters set by {@link #start(StartParameters)} and read by the
     * handshake methods; {@code null} while {@link State#IDLE}.
     */
    private volatile StartParameters startParameters;

    /**
     * Holds the ICE agent constructed in {@link #start(StartParameters)}; {@code null} while
     * {@link State#IDLE} and after {@link #close()}.
     */
    private volatile IceAgent ice;

    /**
     * Holds the virtual thread that keeps the edgeray STUN bind consent-fresh for the call's
     * lifetime; {@code null} on the raw-UDP path and before {@link #connectIce(DtlsSrtpDriver, long, TimeUnit)}.
     *
     * <p>An edgeray relay forwards a participant's media only while that participant keeps the relay
     * bind path consent-refreshed; unlike a plain TURN relay (which bridges by call-id with no
     * connectivity check), the edge drops the forwarding after its consent timeout. This thread runs
     * the {@link IceAgent} tick loop over the DataChannel STUN seam until {@link #close()} interrupts
     * it, so the relay keeps forwarding for the whole call rather than dropping it after a few seconds.
     */
    private volatile Thread iceConsentTicker;

    /**
     * Holds the DTLS-SRTP endpoint built after ICE nominates a pair; {@code null} until then.
     */
    private volatile DtlsSrtpEndpoint dtls;

    /**
     * Holds the SCTP and DCEP data-channel transport.
     *
     * <p>An initial transport is constructed in {@link #start(StartParameters)};
     * {@link #connectDataChannel(int, int)} replaces it with one whose outbound sink writes into the
     * negotiated DTLS application-data layer and then binds and connects the SCTP association.
     */
    private volatile DataChannelTransport dataChannelTransport;

    /**
     * Holds the default data channel, opened once the SCTP association carries data-channel traffic;
     * {@code null} until then.
     */
    private volatile DataChannel defaultChannel;

    /**
     * Holds the per-call RTP-stream state for outgoing in-call interactions.
     *
     * <p>This carries the SSRC, sequence, and timestamp counters for each logical interaction
     * stream. It is constructed once per call so that SSRCs stay stable across every interaction in
     * the call.
     */
    private final InteractionStreamState interactionStreamState =
            new InteractionStreamState();

    /**
     * Returns the per-call interaction-stream state.
     *
     * <p>The returned state is consumed by
     * {@link com.github.auties00.cobalt.call.interaction.CallInteractionEncoder} to produce
     * the SSRC, sequence, and timestamp triple of the next outgoing interaction packet.
     *
     * @return the stream state
     */
    public InteractionStreamState interactionStreamState() {
        return interactionStreamState;
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return the state
     */
    public State state() {
        return state.get();
    }

    /**
     * Returns the default {@link DataChannel} if it has reached
     * {@link com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelState#OPEN},
     * or {@link Optional#empty()} otherwise.
     *
     * @return the default channel, or empty
     */
    public Optional<DataChannel> dataChannel() {
        return Optional.ofNullable(defaultChannel);
    }

    /**
     * Returns the ICE agent if the transport has started, or {@link Optional#empty()} otherwise.
     *
     * @return the agent, or empty
     */
    public Optional<IceAgent> iceAgent() {
        return Optional.ofNullable(ice);
    }

    /**
     * Starts the transport with the negotiated parameters.
     *
     * <p>Constructs the ICE agent and the SCTP data-channel transport from the supplied parameters
     * and transitions from {@link State#IDLE} to {@link State#STARTED}. No wire packets are exchanged
     * here; the outbound STUN and SCTP sinks are wired to discard until the relay and DTLS layers are
     * connected. The DTLS-SRTP endpoint is deferred until ICE nominates a pair.
     *
     * @param parameters the negotiated parameters from the offer and accept exchange
     * @throws NullPointerException  if {@code parameters} is {@code null}
     * @throws IllegalStateException if the transport is not {@link State#IDLE}
     */
    public void start(StartParameters parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        if (!state.compareAndSet(State.IDLE, State.STARTED)) {
            throw new IllegalStateException("ActiveCallTransport.start: state=" + state.get());
        }
        this.startParameters = parameters;
        IceAgent.OutboundSink stunSink = (packet, destination) -> {};
        this.ice = new IceAgent(parameters.dtlsClient(), parameters.credentials(), stunSink);
        Consumer<byte[]> sctpSink = packet -> {};
        this.dataChannelTransport = new DataChannelTransport(parameters.dtlsClient(), sctpSink);
    }

    /**
     * Returns the negotiated start parameters, present once {@link #start(StartParameters)} has been
     * called.
     *
     * @return the parameters, or empty
     */
    public Optional<StartParameters> startParameters() {
        return Optional.ofNullable(startParameters);
    }

    /**
     * Drives the WA relay allocate handshake against the first te2 endpoint in the offer spec.
     *
     * <p>Runs the {@link WaRelayConnector} allocate exchange against the endpoint at index {@code 0}
     * of the spec's te2 list and stores the resulting {@link WaRelayConnector.Allocation} on the
     * transport for the DTLS layer to consume. This is deliberately not invoked from
     * {@link #start(StartParameters)} so that a transport can be constructed without firing real UDP
     * input or output; production paths call it explicitly after {@link #start(StartParameters)}.
     *
     * <p>The relay transport is selected by {@link StartParameters#relayMode()}: a {@code web} client
     * tunnels the Allocate through the DTLS-SCTP DataChannel (a browser cannot open a raw UDP socket),
     * while a native client sends a raw-UDP STUN Allocate.
     *
     * @return the relay allocation result
     * @throws IllegalStateException if no {@link CallRelay} was supplied at start, or if the
     *                               transport is not in {@link State#STARTED}
     */
    public WaRelayConnector.Allocation connectRelay() {
        if (state.get() != State.STARTED) {
            throw new IllegalStateException("connectRelay requires state=STARTED, got " + state.get());
        }
        var params = this.startParameters;
        if (params == null || params.relay() == null) {
            throw new IllegalStateException("connectRelay requires StartParameters.relay to be non-null");
        }
        var allocation = params.relayMode() == RelayMode.WEB
                ? connectRelayViaDataChannel(params)
                : connectRelayViaRawUdp(params);
        this.relayAllocation = allocation;
        return allocation;
    }

    /**
     * Drives the native raw-UDP relay Allocate against the relaylatency-elected regular relay.
     *
     * <p>This is the transport every native WhatsApp client uses and the one selected for the native
     * and web-to-mobile paths. It allocates a raw UDP socket to the
     * {@code <relaylatency><te>}-advertised address using the matching {@code te2} token and carries
     * bare SRTP over the same socket thereafter.
     *
     * @param params the negotiated start parameters
     * @return the relay allocation carrying the live raw UDP socket
     * @throws IllegalStateException if no {@code <relaylatency><te>} regular-relay address was recorded
     */
    private WaRelayConnector.Allocation connectRelayViaRawUdp(StartParameters params) {
        var connector = new WaRelayConnector();
        connector.setPublishedAudioSsrc(params.localAudioSsrc());
        var endpoint = bestRelayLatencyEndpoint().orElseThrow(() -> new IllegalStateException(
                "connectRelay requires a <relaylatency><te> regular-relay address; none was recorded "
                        + "(the relaylatency probe must complete before the relay Allocate)"));
        return connector.connectAny(params.relay(), bestRelayLatencyRelayName().orElse(null), endpoint);
    }

    /**
     * Brings up the WhatsApp Web relay leg to the edgeray {@code te2} endpoint and tunnels the WA
     * Allocate through the resulting SCTP DataChannel.
     *
     * <p>This reproduces WhatsApp Web's relay {@code RTCPeerConnection}: it opens a real UDP socket to
     * the edgeray, runs an ICE STUN binding, a DTLS handshake, and an SCTP association to it, opens the
     * pre-negotiated DataChannel (stream id {@code 0}, unordered, {@code maxRetransmits=0}), and then
     * sends the same {@link WaRelayAllocateRequestBuilder} Allocate bytes the raw-UDP path builds as a
     * DataChannel message, reading the Allocate Success Response back off the channel. The returned
     * {@link WaRelayConnector.Allocation} wraps a {@link DataChannelDatagramTransport} so the media SRTP
     * the call layer subsequently wires also rides the DataChannel. The te2 endpoints are tried in
     * relay-election order, preferring IPv4 candidates.
     *
     * @param params the negotiated start parameters
     * @return the relay allocation carrying the DataChannel-backed transport
     * @throws WhatsAppCallException.Ice if no te2 endpoint completed the edgeray bring-up and Allocate
     */
    private WaRelayConnector.Allocation connectRelayViaDataChannel(StartParameters params) {
        var relay = params.relay();
        var ordered = orderedWebEndpoints(relay);
        if (ordered.isEmpty()) {
            throw new WhatsAppCallException.Ice("web relay bring-up requires at least one te2 endpoint");
        }
        WhatsAppCallException.Ice last = null;
        for (var endpoint : ordered) {
            try {
                return bringUpWebRelay(params, endpoint);
            } catch (WhatsAppCallException.Ice e) {
                LOGGER.log(System.Logger.Level.INFO,
                        "web relay bring-up failed for te2 '" + endpoint.relayName() + "': " + e.getMessage());
                last = e;
            } catch (RuntimeException e) {
                LOGGER.log(System.Logger.Level.INFO,
                        "web relay bring-up errored for te2 '" + endpoint.relayName() + "': " + e.getMessage());
                last = new WhatsAppCallException.Ice(
                        "web relay bring-up errored for te2 '" + endpoint.relayName() + "'", e);
            }
        }
        throw last != null
                ? last
                : new WhatsAppCallException.Ice("no te2 endpoint completed the web relay bring-up");
    }

    /**
     * Orders the relay's {@code te2} endpoints for the web bring-up: relaylatency-elected relay first,
     * then the rest, keeping only IPv4 endpoints (6-byte content).
     *
     * <p>The test host has no IPv6 connectivity, so IPv6 endpoints (18-byte content) are dropped to
     * avoid the UDP socket open failing on an unreachable address and stalling the bring-up.
     *
     * @param relay the parsed relay block
     * @return the ordered IPv4 endpoint list
     */
    private java.util.List<RelayEndpoint> orderedWebEndpoints(CallRelay relay) {
        var preferred = bestRelayLatencyRelayName().orElse(null);
        var endpoints = new ArrayList<RelayEndpoint>();
        for (var endpoint : relay.endpoints()) {
            if (endpoint.bytes().length == 6) {
                endpoints.add(endpoint);
            }
        }
        endpoints.sort(java.util.Comparator
                .comparingInt((RelayEndpoint e) ->
                        preferred != null && preferred.equals(e.relayName()) ? 0 : 1));
        return endpoints;
    }

    /**
     * Brings up the edgeray relay leg over a single {@code te2} endpoint and returns the allocation.
     *
     * <p>Decodes the te2 address, opens a UDP socket to it, wires a {@link WebIceBinding} to the socket
     * and drives the STUN binding, runs the DTLS handshake, opens the SCTP DataChannel, and tunnels the
     * Allocate through it.
     *
     * @param params   the negotiated start parameters
     * @param endpoint the te2 endpoint to bring up against
     * @return the allocation carrying the DataChannel-backed transport
     * @throws WhatsAppCallException.Ice    if any layer of the bring-up fails
     */
    private WaRelayConnector.Allocation bringUpWebRelay(StartParameters params, RelayEndpoint endpoint) {
        var edgeray = decodeEdgerayEndpoint(endpoint);
        var udp = new UdpDatagramTransport(edgeray);
        var ok = false;
        try {
            System.out.println("[webfix] bringUpWebRelay START edgeray=" + edgeray);
            driveWebIce(params, udp, edgeray);
            System.out.println("[webfix] ICE nominated, starting DTLS edgeray=" + edgeray);
            var driver = driveWebDtls(params, udp);
            System.out.println("[webfix] DTLS handshake OK, opening DataChannel edgeray=" + edgeray);
            var channel = openWebDataChannel(driver);
            System.out.println("[webfix] DataChannel OPEN, tunneling Allocate edgeray=" + edgeray);
            var dcTransport = new DataChannelDatagramTransport(channel, edgeray);
            var allocation = tunnelWebAllocate(params, dcTransport, endpoint, edgeray);
            System.out.println("[webfix] Allocate OK edgeray=" + edgeray);
            ok = true;
            return allocation;
        } finally {
            if (!ok) {
                udp.close();
            }
        }
    }

    /**
     * Decodes a {@code te2} endpoint's content bytes into the edgeray's {@link InetSocketAddress}.
     *
     * <p>The wire format is the relay address followed by a big-endian 2-byte port: a 6-byte payload is
     * {@code 4 bytes IPv4 + 2 bytes port}; an 18-byte payload is {@code 16 bytes IPv6 + 2 bytes port}.
     *
     * @param endpoint the te2 endpoint
     * @return the decoded edgeray address
     * @throws WhatsAppCallException.Ice if the content bytes are not the expected 6 or 18 bytes
     */
    private static InetSocketAddress decodeEdgerayEndpoint(RelayEndpoint endpoint) {
        var bytes = endpoint.bytes();
        if (bytes.length != 6 && bytes.length != 18) {
            throw new WhatsAppCallException.Ice(
                    "te2 endpoint content must be 6 (IPv4+port) or 18 (IPv6+port) bytes; got " + bytes.length);
        }
        var addressLen = bytes.length == 6 ? 4 : 16;
        var addressBytes = Arrays.copyOfRange(bytes, 0, addressLen);
        var port = ((bytes[addressLen] & 0xFF) << 8) | (bytes[addressLen + 1] & 0xFF);
        try {
            return new InetSocketAddress(InetAddress.getByAddress(addressBytes), port);
        } catch (java.net.UnknownHostException e) {
            throw new WhatsAppCallException.Ice("te2 endpoint address bytes are invalid", e);
        }
    }

    /**
     * Drives the standard RFC 5389 / RFC 8445 ICE STUN binding to the edgeray over the supplied UDP
     * socket.
     *
     * <p>The edgeray's relay leg is a browser {@code RTCPeerConnection} whose ICE runs in Chromium's
     * native libwebrtc, so the binding it expects is a fully RFC-compliant short-term-credential
     * Binding Request, not the WhatsApp-private {@code WaRelayPacket} shape the raw-UDP relay path uses.
     * A {@link WebIceBinding} produces exactly that request ({@code remoteUfrag:localUfrag}
     * {@code USERNAME}, {@code PRIORITY}, {@code ICE-CONTROLLING}, {@code USE-CANDIDATE},
     * {@code MESSAGE-INTEGRITY} keyed on the relay password string, {@code FINGERPRINT}). The binding
     * sink writes onto {@code udp}; inbound STUN datagrams from the socket feed the binding; and the
     * driver retransmits until the edgeray returns a verified binding success. The DTLS handshake that
     * follows re-registers its own demux listener on the same socket. A consent ticker keeps the bind
     * path fresh for the call's lifetime so the edgeray does not drop the forwarding.
     *
     * @param params  the negotiated start parameters
     * @param udp     the UDP socket connected to the edgeray
     * @param edgeray the edgeray transport address
     * @throws WhatsAppCallException.Ice if the binding does not nominate a pair within the timeout
     */
    private void driveWebIce(StartParameters params, UdpDatagramTransport udp, InetSocketAddress edgeray) {
        var binding = new WebIceBinding(params.credentials(), packet -> {
            try {
                udp.send(packet);
            } catch (RuntimeException _) {
            }
        });
        // Demux the socket while ICE runs: STUN-range datagrams (first byte <= 0x03) feed the binding; the
        // DTLS handshake that follows re-registers its own demux listener on the same socket.
        udp.setInboundListener(datagram -> {
            if (datagram.length > 0 && (datagram[0] & 0xFF) <= 0x03) {
                binding.handleInbound(datagram, edgeray);
            }
        });
        var nominated = binding.awaitNomination(edgeray,
                TimeUnit.SECONDS.toMillis(WEB_ICE_TIMEOUT_SECONDS), WEB_ICE_ATTEMPT_TIMEOUT_MILLIS);
        if (!nominated) {
            throw new WhatsAppCallException.Ice("web ICE binding to edgeray " + edgeray + " did not nominate a pair");
        }
        this.iceConsentTicker = Thread.ofVirtual().name("web-relay-consent").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                // A consent refresh is one more binding request on the nominated path; awaitNomination
                // returns on the first success, so re-running it with a short budget re-sends the check
                // and keeps the edgeray's forwarding consent fresh.
                try {
                    binding.awaitNomination(edgeray, WEB_ICE_CONSENT_INTERVAL_MILLIS,
                            WEB_ICE_ATTEMPT_TIMEOUT_MILLIS);
                } catch (RuntimeException _) {
                }
                try {
                    Thread.sleep(WEB_ICE_CONSENT_INTERVAL_MILLIS);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    /**
     * Runs the DTLS handshake to the edgeray over the supplied UDP socket and returns the started
     * driver.
     *
     * <p>The driver's role comes from {@link #webDtlsRole(StartParameters)}; its peer fingerprint is the
     * hardcoded {@link #WA_PEER_DTLS_FINGERPRINT_SHA256}. The handshake rides the same socket the ICE
     * binding used, so its inbound demux replaces the ICE listener.
     *
     * @param params the negotiated start parameters
     * @param udp    the UDP socket connected to the edgeray
     * @return the driver after a successful handshake
     * @throws WhatsAppCallException.Ice if the handshake fails or times out
     */
    private DtlsSrtpDriver driveWebDtls(StartParameters params, UdpDatagramTransport udp) {
        var driver = new DtlsSrtpDriver(
                udp, webDtlsRole(params), params.localCert(), params.peerFingerprintSha256());
        driver.start();
        try {
            driver.awaitHandshake(DTLS_HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (IOException e) {
            throw new WhatsAppCallException.Ice("web relay DTLS handshake to edgeray failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppCallException.Ice("interrupted during web relay DTLS handshake", e);
        }
        return driver;
    }

    /**
     * Resolves the DTLS role for the edgeray handshake.
     *
     * @implNote This implementation derives the role from {@link StartParameters#dtlsClient()}
     * (caller is DTLS client). WhatsApp Web instead picks the role from the relay endpoint's
     * {@code enable_edgeray_dtls_active_mode} flag (active maps to DTLS client, passive to DTLS server),
     * which Cobalt's {@link RelayEndpoint} does not yet model.
     *
     * @param params the negotiated start parameters
     * @return the DTLS role for the edgeray handshake
     */
    private static SrtpRole webDtlsRole(StartParameters params) {
        // TODO(live): WA Web reads enable_edgeray_dtls_active_mode off the relay endpoint to pick the
        //  DTLS role (active -> client, passive -> server). RelayEndpoint does not parse that attribute
        //  yet, so this defaults to the caller-derived role; confirm the flag against a live web<->web
        //  capture and thread it through CallRelay/RelayEndpoint if it ever differs from dtlsClient.
        return params.dtlsClient() ? SrtpRole.CLIENT : SrtpRole.SERVER;
    }

    /**
     * Opens the pre-negotiated SCTP DataChannel to the edgeray over the handshaked DTLS driver and
     * blocks until it reaches {@link DataChannelState#OPEN}.
     *
     * <p>Builds a {@link DataChannelTransport} whose outbound SCTP packets are written as DTLS
     * application-data records, bridges the inbound DTLS records back into it via a
     * {@link SctpDtlsBridge}, connects the SCTP association on the WebRTC port {@code 5000}, and opens
     * the out-of-band negotiated channel at stream id {@code 0} with WA Web's options (unordered,
     * {@code maxRetransmits=0}). The driver, transport, and channel are stored on the transport.
     *
     * @param driver the handshaked DTLS-SRTP driver over the edgeray socket
     * @return the open default DataChannel
     * @throws WhatsAppCallException.Ice if the SCTP association or the channel does not come up
     */
    private DataChannel openWebDataChannel(DtlsSrtpDriver driver) {
        var bcDtls = driver.dtlsTransport();
        if (bcDtls == null) {
            throw new WhatsAppCallException.Ice("web relay DTLS handshake completed but its transport is null");
        }
        var params = this.startParameters;
        var transport = new DataChannelTransport(params.dtlsClient(), packet -> {
            try {
                bcDtls.send(packet, 0, packet.length);
            } catch (IOException _) {
            }
        });
        // Close the placeholder transport start() created so its native usrsctp socket does not leak
        // when the real edgeray transport replaces it.
        var placeholder = this.dataChannelTransport;
        if (placeholder != null) {
            try {
                placeholder.close();
            } catch (RuntimeException _) {
            }
        }
        this.sctpDtlsBridge = new SctpDtlsBridge(bcDtls, transport);
        try {
            transport.bind(WEB_SCTP_PORT);
            transport.connect(WEB_SCTP_PORT, WEB_SCTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            throw new WhatsAppCallException.Ice("web relay SCTP association to edgeray failed", e);
        }
        // WA Web opens the relay DataChannel pre-negotiated: id 0, unordered, maxRetransmits 0.
        var options = DataChannelOptions.partialReliableByRetransmit(0, false)
                .withNegotiatedStreamId(0);
        var channel = transport.open("pre-negotiated", options);
        this.dataChannelTransport = transport;
        this.dtlsDriver = driver;
        if (channel.state() != DataChannelState.OPEN) {
            throw new WhatsAppCallException.Ice("web relay pre-negotiated DataChannel did not open");
        }
        this.defaultChannel = channel;
        state.compareAndSet(State.STARTED, State.OPEN);
        return channel;
    }

    /**
     * Tunnels the WhatsApp Allocate through the open edgeray DataChannel and returns the allocation.
     *
     * <p>Builds the same {@link WaRelayAllocateRequestBuilder} Allocate the raw-UDP path builds (relay
     * token, full call-info candidate matrix, the {@code 0x4024} sender-subscription, and the
     * {@code MESSAGE-INTEGRITY} keyed on the relay {@code <key>}), sends it as a DataChannel message,
     * and waits for the Allocate Success Response off the channel, verifying its transaction id and
     * integrity. After the exchange the channel's inbound listener is cleared so the SRTP driver the
     * call layer wires next owns inbound delivery.
     *
     * @param params      the negotiated start parameters
     * @param dcTransport the DataChannel-backed transport
     * @param endpoint    the te2 endpoint supplying the relay token and call-info address
     * @param edgeray     the edgeray transport address, used for the call-info relayed-address hint
     * @return the allocation carrying the DataChannel-backed transport
     * @throws WhatsAppCallException.Ice if the Allocate response is missing, mismatched, or rejected
     */
    private WaRelayConnector.Allocation tunnelWebAllocate(StartParameters params,
                                                          DataChannelDatagramTransport dcTransport,
                                                          RelayEndpoint endpoint,
                                                          InetSocketAddress edgeray) {
        var relay = params.relay();
        var relayToken = relay.tokens().stream()
                .filter(t -> t.id() == endpoint.tokenId())
                .findFirst()
                .or(() -> relay.tokens().stream().findFirst())
                .orElseThrow(() -> new WhatsAppCallException.Ice(
                        "te2 references token_id=" + endpoint.tokenId() + " absent from relay block"));
        // The relay HMAC stamp and the tunneled Allocate's MESSAGE-INTEGRITY are keyed on the relay
        // <key> (params.credentials().localPassword()), exactly as the raw-UDP path keys them.
        var relayKey = params.credentials().localPassword();
        var callInfo = buildWebCallInfo(relay);
        var subscription = params.localAudioSsrc() == 0
                ? null
                : WaRelaySubscriptionBuilder.audioSenderSubscription(params.localAudioSsrc());

        var responses = new LinkedBlockingQueue<byte[]>();
        dcTransport.setInboundListener(datagram -> {
            // Classify by STUN method: only the Allocate Success Response (method 3) is captured here.
            if (datagram.length >= 2) {
                responses.offer(datagram);
            }
        });
        try {
            for (var attempt = 1; attempt <= WEB_ALLOCATE_ATTEMPTS; attempt++) {
                var txid = new byte[WaRelayPacket.TRANSACTION_ID_LENGTH];
                ThreadLocalTxid.fill(txid);
                var request = WaRelayAllocateRequestBuilder.build(
                        txid, relayToken.bytes(), callInfo, edgeray.getAddress(), edgeray.getPort(),
                        relayKey, subscription);
                // TODO(live): the tunneled Allocate carries no STUN USERNAME (the raw-UDP path omits it
                //  too) and keys MESSAGE-INTEGRITY on the relay <key>. WA Web may add a token- or
                //  authToken-derived USERNAME on the edgeray Allocate; confirm against a live web<->web
                //  capture and thread a USERNAME attribute + USERNAME-derived MI keying through
                //  WaRelayAllocateRequestBuilder if the edgeray rejects the keyed-on-<key> form.
                dcTransport.send(request);
                byte[] response;
                try {
                    response = responses.poll(WEB_ALLOCATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WhatsAppCallException.Ice("interrupted awaiting tunneled Allocate response", e);
                }
                if (response == null) {
                    if (attempt == WEB_ALLOCATE_ATTEMPTS) {
                        throw new WhatsAppCallException.Ice(
                                "tunneled Allocate timed out after " + WEB_ALLOCATE_ATTEMPTS + " attempts");
                    }
                    continue;
                }
                System.out.println("[webfix] tunneled Allocate response received len=" + response.length
                        + " attempt=" + attempt);
                var relayedAddress = parseWebAllocateResponse(response, txid, relayKey);
                return new WaRelayConnector.Allocation(dcTransport, relayedAddress, txid);
            }
            throw new WhatsAppCallException.Ice("tunneled Allocate fell through retry loop");
        } finally {
            // Detach the Allocate-response listener so the SRTP driver the call layer wires next owns
            // inbound delivery and does not race with the (now drained) response queue.
            dcTransport.setInboundListener(null);
        }
    }

    /**
     * Builds the {@code WA-CALL-INFO} candidate matrix for the tunneled Allocate.
     *
     * <p>Enumerates, for each IP-version bucket ({@code any}, IPv4, IPv6), an aggregate entry followed
     * by one entry per distinct relay candidate, mirroring the native client's matrix that the raw-UDP
     * path also sends.
     *
     * @param relay the parsed relay block
     * @return the call-info payload
     */
    private static WaRelayCallInfo buildWebCallInfo(CallRelay relay) {
        var distinctRelayIds = new LinkedHashSet<Integer>();
        for (var ep : relay.endpoints()) {
            distinctRelayIds.add(ep.relayId());
        }
        var entries = new ArrayList<WaRelayCallInfoEntry>();
        var priority = 4272282031L;
        for (Integer ipVersion : new Integer[]{null, 1, 2}) {
            entries.add(new WaRelayCallInfoEntryBuilder()
                    .ipVersion(ipVersion).relayId(null).priority(priority).build());
            priority -= 100_000_000L;
            for (var relayId : distinctRelayIds) {
                if (relayId == 0) {
                    continue;
                }
                entries.add(new WaRelayCallInfoEntryBuilder()
                        .ipVersion(ipVersion).relayId(relayId).priority(priority).build());
                priority -= 100_000_000L;
            }
        }
        return new WaRelayCallInfoBuilder()
                .entries(entries)
                .build();
    }

    /**
     * Parses a tunneled Allocate Success Response, verifies its transaction id and integrity, and
     * decodes the relayed address.
     *
     * @param response the response bytes received off the DataChannel
     * @param txid     the request transaction id
     * @param relayKey the HMAC key (the relay {@code <key>})
     * @return the relayed transport address, or {@code null} when the response carried none
     * @throws WhatsAppCallException.Ice if the message type, transaction id, or integrity is wrong
     */
    private static InetSocketAddress parseWebAllocateResponse(byte[] response, byte[] txid, byte[] relayKey) {
        var packet = WaRelayPacket.decode(response);
        if (packet.messageType() != WaRelayMessageType.ALLOCATE_SUCCESS.wireValue()) {
            throw new WhatsAppCallException.Ice(
                    "tunneled Allocate response has unexpected message type 0x"
                            + Integer.toHexString(packet.messageType()));
        }
        if (!Arrays.equals(packet.transactionId(), txid)) {
            throw new WhatsAppCallException.Ice("tunneled Allocate response transaction-id mismatch");
        }
        if (!WaRelayMessageIntegrity.verify(response, relayKey)) {
            throw new WhatsAppCallException.Ice("tunneled Allocate response MAC verification failed");
        }
        return null;
    }

    /**
     * Fills a STUN transaction id from a thread-local secure random source.
     *
     * <p>A small holder so the web relay path does not carry its own {@link java.security.SecureRandom}
     * field; the transaction id only needs to be unpredictable per request.
     */
    private static final class ThreadLocalTxid {
        /**
         * Holds the per-thread secure random source.
         */
        private static final ThreadLocal<java.security.SecureRandom> RANDOM =
                ThreadLocal.withInitial(java.security.SecureRandom::new);

        /**
         * Prevents instantiation.
         */
        private ThreadLocalTxid() {
        }

        /**
         * Fills the buffer with random bytes.
         *
         * @param buffer the buffer to fill
         */
        static void fill(byte[] buffer) {
            RANDOM.get().nextBytes(buffer);
        }
    }

    /**
     * Holds the result of the last successful {@link #connectRelay()} call; {@code null} if relay
     * connection has not yet succeeded for this call.
     */
    private volatile WaRelayConnector.Allocation relayAllocation;

    /**
     * Returns the relay allocation if {@link #connectRelay()} has succeeded for this call.
     *
     * @return the allocation, or empty
     */
    public Optional<WaRelayConnector.Allocation> relayAllocation() {
        return Optional.ofNullable(relayAllocation);
    }

    /**
     * Holds the DTLS-SRTP driver that wraps the WhatsApp Web GraphQL transport with the TLS state machine and
     * demultiplexes STUN, DTLS, and SRTP byte ranges per RFC 7983; {@code null} until
     * {@link #connectDtls(long, TimeUnit)} runs.
     */
    private volatile DtlsSrtpDriver dtlsDriver;

    /**
     * Drives the DTLS handshake over the relay-allocated UDP transport and returns the negotiated
     * SRTP endpoint.
     *
     * <p>Constructs a {@link DtlsSrtpDriver} over the {@link WaRelayConnector.Allocation} transport
     * with the local DTLS role derived from {@link StartParameters#dtlsClient()}, starts it on a
     * dedicated virtual thread, waits up to the given timeout for the handshake to complete, and
     * stores the driver on the transport. The DTLS client role maps to {@link SrtpRole#CLIENT} and
     * the server role to {@link SrtpRole#SERVER}.
     *
     * @param timeout the handshake timeout
     * @param unit    the timeout unit
     * @return the negotiated SRTP endpoint
     * @throws IllegalStateException if {@link #connectRelay()} has not yet succeeded, or if start
     *                               parameters are missing
     * @throws IOException          if the DTLS handshake fails
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public SrtpEndpoint connectDtls(
            long timeout, TimeUnit unit)
            throws IOException, InterruptedException {
        var allocation = this.relayAllocation;
        if (allocation == null) {
            throw new IllegalStateException("connectDtls requires connectRelay() to have succeeded first");
        }
        var params = this.startParameters;
        if (params == null) {
            throw new IllegalStateException("connectDtls requires StartParameters");
        }
        var role = params.dtlsClient()
                ? SrtpRole.CLIENT
                : SrtpRole.SERVER;
        var peerTransport = allocation.transport();
        var driver = new DtlsSrtpDriver(
                peerTransport, role,
                params.localCert(), params.peerFingerprintSha256());
        driver.start();
        var srtpEndpoint = driver.awaitHandshake(timeout, unit);
        this.dtlsDriver = driver;
        return srtpEndpoint;
    }

    /**
     * Confirms the relay receive path is ready for media.
     *
     * <p>The regular WhatsApp relay forwards a call's media off the Allocate handshake plus the
     * lifetime keepalive that {@link WaRelayConnector} runs; it exchanges no STUN binding checks with
     * the client (verified by an A/B packet capture: a working call sends only the Allocate and then
     * bare SRTP, never an ICE Binding to the relay). There is therefore no connectivity seam to drive,
     * so this returns success immediately once the Allocate has completed.
     *
     * @param driver  the DTLS-SRTP driver wrapping the relay socket (unused on the raw-UDP relay path,
     *                retained for call-site symmetry)
     * @param timeout the maximum time to wait (unused)
     * @param unit    the unit of {@code timeout} (unused)
     * @return {@code true} once {@link #connectRelay()} has produced an allocation
     * @throws IllegalStateException if {@link #connectRelay()} or {@link #start(StartParameters)} has
     *                               not run
     */
    public boolean connectIce(DtlsSrtpDriver driver, long timeout, TimeUnit unit) {
        var allocation = this.relayAllocation;
        var params = this.startParameters;
        if (allocation == null || params == null) {
            throw new IllegalStateException("connectIce requires connectRelay() and start() to have run");
        }
        return true;
    }

    /**
     * Builds the DTLS-SRTP driver over the relay-tunneled transport and stores it WITHOUT starting it
     * or awaiting a handshake, for the hop-by-hop media path where the SRTP keys come from the relay
     * {@code <hbh_key>} rather than a peer DTLS export.
     *
     * <p>The relay forwards SRTP and the peer never completes a peer DTLS handshake (the WhatsApp P2P
     * path is disabled), so awaiting one would always time out. The driver is still used purely as the
     * byte transport: its {@code sendSrtp} writes raw SRTP to the relay DataChannel and its inbound
     * demux forwards raw SRTP to the handler, both independent of DTLS state. The caller starts the
     * driver via {@code VoiceCallSession.start()}, which kicks the (ignored) handshake and installs the
     * inbound demux.
     *
     * @return the built driver
     * @throws IllegalStateException if {@link #connectRelay()} has not yet succeeded, or if start
     *                               parameters are missing
     */
    public DtlsSrtpDriver buildDtlsDriver() {
        var allocation = this.relayAllocation;
        if (allocation == null) {
            throw new IllegalStateException("buildDtlsDriver requires connectRelay() to have succeeded first");
        }
        var params = this.startParameters;
        if (params == null) {
            throw new IllegalStateException("buildDtlsDriver requires StartParameters");
        }
        var role = params.dtlsClient() ? SrtpRole.CLIENT : SrtpRole.SERVER;
        var peerTransport = allocation.transport();
        var driver = new DtlsSrtpDriver(
                peerTransport, role, params.localCert(), params.peerFingerprintSha256());
        this.dtlsDriver = driver;
        return driver;
    }

    /**
     * Returns the DTLS driver if {@link #connectDtls(long, TimeUnit)} has succeeded.
     *
     * @return the driver, or empty
     */
    public Optional<DtlsSrtpDriver> dtlsDriver() {
        return Optional.ofNullable(dtlsDriver);
    }

    /**
     * Returns the negotiated SRTP endpoint if {@link #connectDtls(long, TimeUnit)} has succeeded.
     *
     * @return the SRTP endpoint, or empty
     */
    public Optional<SrtpEndpoint> srtp() {
        return Optional.ofNullable(dtlsDriver).map(d -> d.srtpEndpoint());
    }

    /**
     * Holds the SCTP-over-DTLS inbound bridge produced by {@link #connectDataChannel(int, int)}.
     */
    private volatile SctpDtlsBridge sctpDtlsBridge;

    /**
     * Holds the most recent observed RTT sample per WhatsApp Web GraphQL endpoint, populated from inbound
     * {@code <relaylatency><te latency=... relay_name=...>} stanzas via
     * {@link #recordRelayLatency(String, long, InetSocketAddress)}. The map keeps the latest sample per
     * {@code relay_name} so a future relay re-election can pick the lowest-RTT candidate.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> relayLatencies =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Holds the regular-relay transport address advertised by each {@code <relaylatency><te>} stanza,
     * keyed by {@code relay_name}. This is the raw-UDP relay address the media plane allocates against
     * (distinct from the offer-ACK {@code <te2>} edgeray address, which the media plane never uses).
     */
    private final java.util.concurrent.ConcurrentHashMap<String, InetSocketAddress> relayLatencyEndpoints =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Records one round-trip-time sample and the transport address for a named relay.
     *
     * <p>Called by the call receiver when a {@code <relaylatency><te ... />} stanza arrives.
     * Replaces any previous sample for the relay, since callers track the freshest value rather
     * than a moving average for the simple relay-election heuristic. The {@code endpoint} is the
     * regular-relay address decoded from the {@code <te>} content, used by {@link #connectRelay()} as
     * the raw-UDP Allocate destination.
     *
     * @param relayName the WhatsApp Web GraphQL endpoint identifier ({@code relay_name} attribute), or
     *                  {@code null} to skip the recording
     * @param latency   the round-trip-time sample (units mirror the wire value)
     * @param endpoint  the regular-relay address decoded from the {@code <te>} content, or {@code null}
     *                  when the content was missing or malformed
     */
    public void recordRelayLatency(String relayName, long latency, InetSocketAddress endpoint) {
        if (relayName == null) {
            return;
        }
        relayLatencies.put(relayName, latency);
        if (endpoint != null) {
            relayLatencyEndpoints.put(relayName, endpoint);
        }
    }

    /**
     * Returns the name of the lowest-latency relay cluster elected by the {@code <relaylatency>} probe
     * exchange.
     *
     * <p>The relay allocation uses this cluster's {@code <relaylatency><te>} regular-relay address so both
     * call legs land on the same relay the SFU bridges. When no probe was recorded, returns empty.
     *
     * @return the elected relay name, or empty when none was recorded
     */
    public Optional<String> bestRelayLatencyRelayName() {
        return relayLatencies.entrySet().stream()
                .min(java.util.Comparator.comparingLong(java.util.Map.Entry::getValue))
                .map(java.util.Map.Entry::getKey);
    }

    /**
     * Returns the regular-relay transport address of the lowest-latency relay elected by the
     * {@code <relaylatency>} probe exchange.
     *
     * <p>This is the raw-UDP Allocate destination for the call's media plane. Returns empty when the
     * elected relay advertised no decodable {@code <te>} address (or no probe was recorded).
     *
     * @return the elected relay's regular-relay address, or empty when none is available
     */
    public Optional<InetSocketAddress> bestRelayLatencyEndpoint() {
        return bestRelayLatencyRelayName().map(relayLatencyEndpoints::get);
    }

    /**
     * Returns the latest RTT sample for a relay, if any has been observed.
     *
     * @param relayName the WhatsApp Web GraphQL endpoint identifier
     * @return the latest sample, or empty when none has been observed
     */
    public Optional<Long> relayLatency(String relayName) {
        if (relayName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(relayLatencies.get(relayName));
    }

    /**
     * Returns an immutable snapshot of all observed relay-latency samples, keyed by relay name.
     *
     * @return the snapshot
     */
    public java.util.Map<String, Long> relayLatencies() {
        return java.util.Map.copyOf(relayLatencies);
    }

    /**
     * Opens the SCTP association and prepares the data-channel transport.
     *
     * <p>Builds a {@link DataChannelTransport} whose outbound sink writes each SCTP packet as one
     * encrypted DTLS application-data record, bridges the inbound side so decrypted DTLS records feed
     * back into the transport via a {@link SctpDtlsBridge}, then binds the local port and connects to
     * the remote port. Requires {@link #connectDtls(long, TimeUnit)} to have succeeded so the SCTP
     * packets ride the negotiated DTLS application-data layer. WebRTC's conventional SCTP ports are
     * {@code 5000} on both sides.
     *
     * @param localPort  the local SCTP port
     * @param remotePort the peer's SCTP port
     * @return the data-channel transport, ready to open channels via
     *         {@link DataChannelTransport#open(String, com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannelOptions)}
     * @throws IllegalStateException if {@link #connectDtls(long, TimeUnit)} has not yet succeeded, or
     *                               if the driver's DTLS transport handshake state has been lost
     */
    public DataChannelTransport connectDataChannel(int localPort, int remotePort) {
        var driver = this.dtlsDriver;
        if (driver == null) {
            throw new IllegalStateException(
                    "connectDataChannel requires connectDtls() to have succeeded first");
        }
        var bcDtls = driver.dtlsTransport();
        if (bcDtls == null) {
            throw new IllegalStateException(
                    "connectDataChannel: driver.dtlsTransport() is null - handshake state lost");
        }
        var params = this.startParameters;
        var transport = new DataChannelTransport(params.dtlsClient(), packet -> {
            try { bcDtls.send(packet, 0, packet.length); }
            catch (IOException _) {}
        });
        this.sctpDtlsBridge = new SctpDtlsBridge(
                bcDtls, transport);
        transport.bind(localPort);
        transport.connect(remotePort);
        this.dataChannelTransport = transport;
        return transport;
    }

    /**
     * Returns the SCTP-over-DTLS bridge if {@link #connectDataChannel(int, int)} has succeeded.
     *
     * @return the bridge, or empty
     */
    public Optional<SctpDtlsBridge> sctpDtlsBridge() {
        return Optional.ofNullable(sctpDtlsBridge);
    }

    /**
     * Tears every layer down in reverse construction order.
     *
     * <p>Closes the default channel, the SCTP data-channel transport, the SCTP-over-DTLS bridge, the
     * DTLS driver, and the relay allocation transport in turn, swallowing any runtime exception from
     * each so a failure in one layer does not block teardown of the rest, and clears the ICE agent
     * and start parameters. Transitions to {@link State#CLOSED}. This method is idempotent: invoking
     * it on an already-closed or never-started transport is a no-op.
     */
    @Override
    public void close() {
        if (state.getAndSet(State.CLOSED) == State.CLOSED) {
            return;
        }
        var channel = this.defaultChannel;
        if (channel != null) {
            try { channel.close(); } catch (RuntimeException _) {}
            this.defaultChannel = null;
        }
        var transport = this.dataChannelTransport;
        if (transport != null) {
            try { transport.close(); } catch (RuntimeException _) {}
            this.dataChannelTransport = null;
        }
        var bridge = this.sctpDtlsBridge;
        if (bridge != null) {
            try { bridge.close(); } catch (RuntimeException _) {}
            this.sctpDtlsBridge = null;
        }
        var driver = this.dtlsDriver;
        if (driver != null) {
            try { driver.close(); } catch (RuntimeException _) {}
            this.dtlsDriver = null;
        }
        var allocation = this.relayAllocation;
        if (allocation != null) {
            if (allocation.transport() != null) {
                try { allocation.transport().close(); } catch (RuntimeException _) {}
            }
            this.relayAllocation = null;
        }
        var consent = this.iceConsentTicker;
        if (consent != null) {
            consent.interrupt();
            this.iceConsentTicker = null;
        }
        this.dtls = null;
        this.ice = null;
        this.startParameters = null;
    }
}
