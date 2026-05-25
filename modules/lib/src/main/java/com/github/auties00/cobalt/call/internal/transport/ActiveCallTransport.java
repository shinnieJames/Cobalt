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
         * {@link com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelState#OPEN}.
         */
        OPEN,
        /**
         * Indicates {@link #close()} has been invoked and every layer has been torn down.
         */
        CLOSED
    }

    /**
     * Carries the negotiated parameters fed into {@link #start(StartParameters)}, filled from the
     * offer and accept exchange.
     *
     * @param credentials           the ICE ufrag and password pair for both sides
     * @param localCert             the self-signed DTLS certificate of the local side
     * @param peerFingerprintSha256 the peer's certificate fingerprint advertised in the offer or accept
     * @param dtlsClient            {@code true} when the local side acts as DTLS client, that is, is the call placer
     * @param offerSpec             the parsed {@link OfferTransportSpec} from the inbound offer, read by
     *                              {@link #connectRelay()} to drive the WA relay handshake; {@code null} on the
     *                              outgoing-call path until the outgoing offer builder fills its own spec
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
     * {@link com.github.auties00.cobalt.call.internal.interaction.CallInteractionEncoder} to produce
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
     * {@link com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelState#OPEN},
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
     * @return the relay allocation result
     * @throws IllegalStateException if no {@link OfferTransportSpec} was supplied at start, or if the
     *                               transport is not in {@link State#STARTED}
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
     * Holds the DTLS-SRTP driver that wraps the relay transport with the TLS state machine and
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
        var driver = new DtlsSrtpDriver(
                allocation.transport(), role,
                params.localCert(), params.peerFingerprintSha256());
        driver.start();
        var srtpEndpoint = driver.awaitHandshake(timeout, unit);
        this.dtlsDriver = driver;
        return srtpEndpoint;
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
     *         {@link DataChannelTransport#open(String, com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelOptions)}
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
            try { allocation.transport().close(); } catch (RuntimeException _) {}
            this.relayAllocation = null;
        }
        this.dtls = null;
        this.ice = null;
        this.startParameters = null;
    }
}
