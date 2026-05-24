package com.github.auties00.cobalt.call.internal.transport;

import com.github.auties00.cobalt.call.internal.interaction.InteractionStreamState;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpEndpoint;
import com.github.auties00.cobalt.call.internal.transport.ice.IceAgent;
import com.github.auties00.cobalt.call.internal.transport.ice.IceCredentials;
import com.github.auties00.cobalt.call.internal.transport.relay.WaRelayConnector;
import com.github.auties00.cobalt.call.internal.transport.sctp.SctpDtlsBridge;
import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannel;
import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelTransport;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import com.github.auties00.cobalt.call.ActiveCall;

/**
 * Owns the transport-layer state of one {@link ActiveCall}.
 *
 * <p>WhatsApp Web's VoIP transport stack is layered:
 * <pre>{@code
 *   ┌─ DataChannel(s) over ─┐
 *   │  SCTP association     │ ─ peer signaling (interactions, app data)
 *   ├─ DTLS-SRTP over ──────┤
 *   │  WA relay (TURN-like) │ ─ keying material + SRTP
 *   └─ ICE-selected path ───┘
 * }</pre>
 *
 * <p>This class holds one instance of each layer for a single call and
 * sequences their lifecycle:
 * <ul>
 *   <li>{@link #start(StartParameters)} — gathers local candidates,
 *       runs ICE → DTLS → SCTP → DCEP, transitions to {@link #OPEN}
 *       when the default data channel is ready;</li>
 *   <li>{@link #close()} — tears down each layer in reverse order
 *       (DataChannel → SCTP → DTLS → ICE), releasing native handles
 *       and unregistering from {@code SctpEngine}.</li>
 * </ul>
 *
 * <p>Phase 1 scope (this commit): instantiate the orchestrator from
 * {@link ActiveCall}, prove lifecycle
 * symmetry (constructed at call-start → closed at hangup), expose a
 * {@link #dataChannel()} accessor. The actual networking — ICE
 * candidate gathering, the WA-relay handshake, DTLS, SCTP — is
 * **not yet wired**; {@link #start(StartParameters)} stores the
 * negotiated parameters and prepares the objects but does not exchange
 * any wire packets. Phases 2–5 fill that in.
 */
public final class ActiveCallTransport implements AutoCloseable {
    /**
     * Lifecycle state — strictly forward-progressing.
     */
    public enum State {
        /**
         * Constructed, awaiting {@link #start(StartParameters)}.
         */
        IDLE,
        /**
         * {@link #start(StartParameters)} called — parameters stored
         * and underlying objects (ICE agent, DTLS endpoint, SCTP
         * transport) instantiated. The actual handshake chain is
         * pending implementation in later phases.
         */
        STARTED,
        /**
         * Default data channel reached
         * {@link com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelState#OPEN}.
         */
        OPEN,
        /**
         * {@link #close()} invoked — every layer torn down.
         */
        CLOSED
    }

    /**
     * Negotiated parameters fed into {@link #start(StartParameters)}.
     * Filled from the offer/accept exchange.
     *
     * @param credentials  the ICE ufrag/password pair (both sides)
     * @param localCert    the self-signed DTLS certificate
     * @param peerFingerprintSha256 the peer's certificate fingerprint
     *                              advertised in the offer/accept
     * @param dtlsClient   {@code true} when the local side acts as
     *                     DTLS client (i.e. is the call placer)
     * @param offerSpec    the parsed
     *                     {@link OfferTransportSpec} from the inbound
     *                     offer — Phase 3 reads {@code te2Endpoints}
     *                     and {@code tokens} from this to drive the
     *                     WA-relay handshake. {@code null} for the
     *                     outgoing-call path (until the outgoing offer
     *                     builder is extended to fill its own spec).
     */
    public record StartParameters(
            IceCredentials credentials,
            DtlsCertificate localCert,
            byte[] peerFingerprintSha256,
            boolean dtlsClient,
            OfferTransportSpec offerSpec
    ) {
    }

    /**
     * Current lifecycle state. Updated only by {@link #start} and
     * {@link #close}.
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    /**
     * The negotiated parameters, set by {@link #start} and read by
     * the (future) handshake driver. {@code null} while {@link #IDLE}.
     */
    private volatile StartParameters startParameters;

    /**
     * The ICE agent. Constructed in {@link #start}; {@code null} while
     * {@link #IDLE} and after {@link #close}.
     */
    private volatile IceAgent ice;

    /**
     * The DTLS-SRTP endpoint. Built after ICE nominates a pair (Phase
     * 4); {@code null} until then.
     */
    private volatile DtlsSrtpEndpoint dtls;

    /**
     * The SCTP+DCEP data-channel transport. Constructed in
     * {@link #start}; SCTP association is bound/connected only after
     * DTLS handshake completes (Phase 5).
     */
    private volatile DataChannelTransport dataChannelTransport;

    /**
     * The default data channel (label e.g. {@code "data-channel-id"}
     * per WA convention). Opened in Phase 5; {@code null} until then.
     */
    private volatile DataChannel defaultChannel;

    /**
     * The per-call RTP-stream state for outgoing in-call
     * interactions (Phase 7). Holds SSRC + sequence + timestamp
     * counters for each of the three logical streams (reaction,
     * control, video-upgrade). Constructed once per call so SSRCs
     * stay stable across all interactions in the call.
     */
    private final InteractionStreamState interactionStreamState =
            new InteractionStreamState();

    /**
     * Returns the per-call interaction-stream state. Used by
     * {@link com.github.auties00.cobalt.call.internal.interaction.CallInteractionEncoder}
     * to produce the next outgoing interaction packet's
     * (SSRC, sequence, timestamp) triple.
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
     * {@link com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelState#OPEN},
     * otherwise {@link Optional#empty()}.
     *
     * @return the default channel, or empty
     */
    public Optional<DataChannel> dataChannel() {
        return Optional.ofNullable(defaultChannel);
    }

    /**
     * Returns the ICE agent if started, or {@link Optional#empty()}
     * otherwise. Exposed mainly for tests and Phase 3 wire-up.
     *
     * @return the agent, or empty
     */
    public Optional<IceAgent> iceAgent() {
        return Optional.ofNullable(ice);
    }

    /**
     * Starts the transport with the negotiated parameters.
     *
     * <p>Phase 1: constructs the ICE agent + DataChannelTransport
     * with the supplied parameters, but does not yet exchange any
     * wire packets. The DTLS-SRTP endpoint is deferred until ICE
     * nominates a pair (Phase 4).
     *
     * @param parameters the negotiated parameters from the offer /
     *                   accept exchange
     * @throws NullPointerException  if {@code parameters} is
     *                               {@code null}
     * @throws IllegalStateException if the transport is not
     *                               {@link State#IDLE}
     */
    public void start(StartParameters parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        if (!state.compareAndSet(State.IDLE, State.STARTED)) {
            throw new IllegalStateException("ActiveCallTransport.start: state=" + state.get());
        }
        this.startParameters = parameters;
        // Outbound STUN sink: discards for now. Phase 3 wires this to
        // the WA-relay tunnel which carries STUN, DTLS, and SCTP
        // packets multiplexed on the same UDP socket.
        IceAgent.OutboundSink stunSink = (packet, destination) -> { /* Phase 3 */ };
        this.ice = new IceAgent(parameters.dtlsClient(), parameters.credentials(), stunSink);
        // Outbound SCTP sink: discards for now. Phase 4 wires this to
        // the DTLS endpoint's send-record method.
        Consumer<byte[]> sctpSink = packet -> { /* Phase 4-5 */ };
        this.dataChannelTransport = new DataChannelTransport(parameters.dtlsClient(), sctpSink);
    }

    /**
     * Returns the negotiated start parameters, present once
     * {@link #start} has been called.
     *
     * @return the parameters, or empty
     */
    public Optional<StartParameters> startParameters() {
        return Optional.ofNullable(startParameters);
    }

    /**
     * Drives the WA-relay Allocate handshake against the first te2
     * endpoint in the offer spec, then stores the resulting
     * {@link WaRelayConnector.Allocation}
     * on the transport for the DTLS layer (Phase 4) to consume.
     *
     * <p>This is intentionally NOT called from {@link #start} — the
     * lifecycle test must construct a transport without firing real
     * UDP I/O. Production paths invoke this explicitly after
     * {@code start(...)}.
     *
     * @return the relay allocation result
     * @throws IllegalStateException     if no {@link OfferTransportSpec}
     *                                   was supplied at start, or if
     *                                   the transport isn't in
     *                                   {@link State#STARTED}
     */
    public WaRelayConnector.Allocation connectRelay() {
        if (state.get() != State.STARTED) {
            throw new IllegalStateException("connectRelay requires state=STARTED, got " + state.get());
        }
        var params = this.startParameters;
        if (params == null || params.offerSpec() == null) {
            throw new IllegalStateException("connectRelay requires StartParameters.offerSpec to be non-null");
        }
        var connector = new WaRelayConnector();
        var allocation = connector.connect(params.offerSpec(), 0);
        this.relayAllocation = allocation;
        return allocation;
    }

    /**
     * The result of the last successful
     * {@link #connectRelay()} call, or {@code null} if relay
     * connection has not yet succeeded for this call.
     */
    private volatile WaRelayConnector.Allocation relayAllocation;

    /**
     * Returns the relay allocation if {@link #connectRelay()} has
     * succeeded for this call.
     *
     * @return the allocation, or empty
     */
    public Optional<WaRelayConnector.Allocation> relayAllocation() {
        return Optional.ofNullable(relayAllocation);
    }

    /**
     * The DTLS-SRTP driver that wraps the relay transport with
     * BouncyCastle's TLS state machine and demultiplexes STUN / DTLS /
     * SRTP byte ranges per RFC 7983. {@code null} until
     * {@link #connectDtls(long, TimeUnit)} runs.
     */
    private volatile DtlsSrtpDriver dtlsDriver;

    /**
     * Drives the DTLS handshake over the relay-allocated UDP
     * transport via the existing
     * {@link DtlsSrtpDriver}.
     *
     * <p>The driver internally wraps the
     * {@link com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport}
     * in its own BouncyCastle adapter (the BC TLS API is blocking;
     * Cobalt's is listener-driven) and runs the handshake on a
     * dedicated virtual thread.
     *
     * @param timeout the handshake timeout
     * @param unit    the timeout unit
     * @return the negotiated SRTP endpoint
     * @throws IllegalStateException if {@link #connectRelay} has not
     *                               yet succeeded, or if start
     *                               parameters are missing
     * @throws IOException   if the DTLS handshake fails
     * @throws InterruptedException  if the calling thread is
     *                               interrupted while waiting
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
        var driver = new DtlsSrtpDriver(
                allocation.transport(), role,
                params.localCert(), params.peerFingerprintSha256());
        driver.start();
        var srtpEndpoint = driver.awaitHandshake(timeout, unit);
        this.dtlsDriver = driver;
        return srtpEndpoint;
    }

    /**
     * Returns the DTLS driver if {@link #connectDtls} has succeeded.
     *
     * @return the driver, or empty
     */
    public Optional<DtlsSrtpDriver> dtlsDriver() {
        return Optional.ofNullable(dtlsDriver);
    }

    /**
     * Returns the negotiated SRTP endpoint if {@link #connectDtls}
     * has succeeded.
     *
     * @return the SRTP endpoint, or empty
     */
    public Optional<SrtpEndpoint> srtp() {
        return Optional.ofNullable(dtlsDriver).map(d -> d.srtpEndpoint());
    }

    /**
     * The SCTP-over-DTLS inbound bridge produced by
     * {@link #connectDataChannel(int, int)}.
     */
    private volatile SctpDtlsBridge sctpDtlsBridge;

    /**
     * Opens the SCTP association and prepares the data-channel
     * transport. Requires {@link #connectDtls} to have succeeded so
     * the SCTP packets ride the negotiated DTLS application-data
     * layer.
     *
     * <p>WebRTC's standard SCTP ports are 5000 ↔ 5000.
     *
     * @param localPort  the local SCTP port
     * @param remotePort the peer's SCTP port
     * @return the data-channel transport, ready to open channels via
     *         {@link DataChannelTransport#open}
     * @throws IllegalStateException     if {@link #connectDtls} has
     *                                   not yet succeeded
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
                    "connectDataChannel: driver.dtlsTransport() is null — handshake state lost");
        }
        var params = this.startParameters;
        // Build the SCTP transport with an outbound sink that writes
        // straight into the BC DTLS transport — every SCTP packet
        // becomes one encrypted DTLS application-data record.
        var transport = new DataChannelTransport(params.dtlsClient(), packet -> {
            try { bcDtls.send(packet, 0, packet.length); }
            catch (IOException _) { /* swallow — bridge surfaces errors */ }
        });
        // Bridge the inbound side: decrypt DTLS records and feed them
        // into the SCTP transport.
        this.sctpDtlsBridge = new SctpDtlsBridge(
                bcDtls, transport);
        transport.bind(localPort);
        transport.connect(remotePort);
        this.dataChannelTransport = transport;
        return transport;
    }

    /**
     * Returns the SCTP-over-DTLS bridge if
     * {@link #connectDataChannel} has succeeded.
     *
     * @return the bridge, or empty
     */
    public Optional<SctpDtlsBridge> sctpDtlsBridge() {
        return Optional.ofNullable(sctpDtlsBridge);
    }

    /**
     * Tears every layer down in reverse construction order.
     *
     * <p>Idempotent — calling on an already-closed or never-started
     * transport is a no-op.
     */
    @Override
    public void close() {
        if (state.getAndSet(State.CLOSED) == State.CLOSED) {
            return;
        }
        var channel = this.defaultChannel;
        if (channel != null) {
            try { channel.close(); } catch (RuntimeException _) { /* swallow */ }
            this.defaultChannel = null;
        }
        var transport = this.dataChannelTransport;
        if (transport != null) {
            try { transport.close(); } catch (RuntimeException _) { /* swallow */ }
            this.dataChannelTransport = null;
        }
        var bridge = this.sctpDtlsBridge;
        if (bridge != null) {
            try { bridge.close(); } catch (RuntimeException _) { /* swallow */ }
            this.sctpDtlsBridge = null;
        }
        var driver = this.dtlsDriver;
        if (driver != null) {
            try { driver.close(); } catch (RuntimeException _) { /* swallow */ }
            this.dtlsDriver = null;
        }
        var allocation = this.relayAllocation;
        if (allocation != null) {
            try { allocation.transport().close(); } catch (RuntimeException _) { /* swallow */ }
            this.relayAllocation = null;
        }
        this.dtls = null;
        this.ice = null;
        this.startParameters = null;
    }
}
