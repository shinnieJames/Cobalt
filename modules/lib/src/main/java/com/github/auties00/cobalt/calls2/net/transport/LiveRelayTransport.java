package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.calls2.platform.VoipCryptoNative;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.model.call.datachannel.StreamDescriptors;
import com.github.auties00.cobalt.model.call.datachannel.StreamSubscriptions;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The single WhatsApp Web media transport: an {@code RTCPeerConnection}-equivalent that brings the call up
 * over ICE and DTLS, opens one SCTP data channel, and multiplexes every packet over it as SCTP DATA.
 *
 * <p>This transport reproduces the proven web model (see
 * {@code re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md} and
 * {@code re/calls2-spec/web-transport-construction-RE.md}). It gathers no media socket of its own: it drives
 * an {@link IceAgent} over the host UDP socket through an injected {@link Egress} as the controlling agent
 * against a single relay host candidate, then opens a {@link DataChannel} backend that runs DTLS over the
 * selected path and carries the call's whole media and control plane over one SCTP data channel as SCTP
 * DATA: hop-by-hop SRTP audio RTP (keyed by the relay {@code <hbh_key>}), RTCP, application STUN keepalives,
 * WARP control, and the subscription. Media stays hop-by-hop SRTP and is carried as SCTP DATA; it is never
 * DTLS-SRTP.
 *
 * <p>Inbound bytes flow socket {@literal ->} {@link #onInboundDatagram(byte[], SocketAddress)} {@literal ->}
 * a socket-layer {@link InboundPacketDemux} (STUN to the ICE agent, DTLS records to the data channel's DTLS
 * transport via {@link DataChannel#feedDtlsRecord(byte[])}) {@literal ->} SCTP {@literal ->} the data
 * channel, where each inbound SCTP DATA message is leading-byte demultiplexed again into hop-by-hop SRTP
 * media (decrypted to the media sink), RTCP, application STUN, and WARP. Outbound media, RTCP, and
 * standalone WARP are hop-by-hop SRTP-protected (where applicable) and written as SCTP DATA on the data
 * channel.
 *
 * <p>{@link #start()} launches a bring-up thread that paces the RFC 8445 connectivity check against the
 * relay host candidate until a pair is nominated, then runs {@link DataChannel#connect()} (the DTLS
 * handshake, the SCTP connect to port 5000, and the pre-negotiated channel open) and emits
 * {@link TransportEvent#RELAY_CREATE_SUCCESS} once the channel is ready. {@link #tick(long)} is the same
 * connectivity-check scheduler exposed for an external transport-thread driver.
 *
 * @implNote This implementation unifies {@code wa_transport.cc} (the demux and send orchestrator),
 *           {@code wa_transport_p2p.cc} (the ICE/DTLS bring-up and connectivity checks),
 *           {@code wa_hbh_srtp_relay.cc} (hop-by-hop SRTP), and {@code wa_transport_warp.cc} (WARP) from
 *           the wa-voip WASM module {@code ff-tScznZ8P}, restricted to the relay endpoint. The DTLS
 *           handshake, the SCTP connect, and the pre-negotiated data-channel open run inside the
 *           {@link DataChannel} backend ({@link RelayDataChannel}) on the bring-up thread; the relay path
 *           uses no signaled {@code <certificate>} but pins the fixed relay fingerprint
 *           {@link RelayDataChannel#RELAY_CERT_FINGERPRINT_SHA256}, and the remote ICE candidate is the
 *           single relay host candidate the agent is seeded with rather than a signaled candidate list. The
 *           {@code 0x0003} subscription envelope is assembled and shipped through
 *           {@link #sendSubscriptionEnvelope(StreamSubscriptions)} (message-integrity keyed by the relay
 *           {@code <key>}) as the three-attribute form; the live capture's leading {@code 0x4000} WARP
 *           attribute (a control WARP whose body is hop-by-hop SRTP-sealed with a seal that is not
 *           capture-reproducible, see {@link SubscriptionEnvelope}) is an optional piggybacked rate-control
 *           report Cobalt does not emit, and inbound app-data routing is a later capture phase.
 */
public final class LiveRelayTransport implements MediaTransport {
    /**
     * Ships one outbound datagram to a transport address, the host UDP socket sink the ICE checks and the
     * DTLS records leave through.
     *
     * <p>This is the seam between the transport and the host's UDP socket: the transport produces the STUN
     * or DTLS bytes and the egress puts them on the wire, returning the count the socket accepted. It
     * matches the shape of the host datagram-send downcall so an implementation can delegate straight to it.
     */
    @FunctionalInterface
    public interface Egress {
        /**
         * Sends one datagram to a destination as a best-effort packet.
         *
         * @param payload     the datagram bytes to send
         * @param destination the address to send to
         * @return the number of bytes accepted by the transport, or a non-positive value on failure
         */
        int send(byte[] payload, SocketAddress destination);
    }

    /**
     * The one SCTP data channel backend over which the transport multiplexes every media and control
     * message as SCTP DATA.
     *
     * <p>This seam hides the DTLS-wrapped SCTP association and its RFC 8832 DCEP channel from the transport
     * orchestration: the transport hands it outbound SCTP DATA messages through {@link #send(byte[])} and
     * receives each inbound SCTP DATA message through the consumer registered with
     * {@link #onMessage(Consumer)}. {@link #isReady()} reports whether the channel has opened.
     *
     * @implSpec An implementation MUST carry each {@link #send(byte[])} payload as one SCTP DATA message on
     *           the open data channel and MUST deliver each inbound SCTP DATA message to the registered
     *           consumer; it MUST report {@code false} from {@link #isReady()} until the channel is open.
     *           {@link #connect()} MUST run the DTLS handshake and SCTP connect and open the pre-negotiated
     *           channel, blocking until it is ready or throwing.
     */
    public interface DataChannel extends AutoCloseable {
        /**
         * Brings the data channel up: runs the DTLS handshake, connects SCTP, and opens the pre-negotiated
         * channel.
         *
         * <p>The relay path runs DTLS over the ICE-selected path, then one SCTP association on port 5000, and
         * opens a single pre-negotiated channel (so no DCEP open is sent). This method blocks on the calling
         * bring-up thread until the channel is ready or the bring-up fails. It is called once, after ICE has
         * selected the relay path; a second call after the channel is ready is a no-op.
         *
         * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.DataChannel if the handshake,
         *                                                                                the SCTP connect, or
         *                                                                                the channel open
         *                                                                                fails
         */
        void connect();

        /**
         * Feeds one inbound DTLS record from the host socket into the channel's DTLS transport.
         *
         * <p>The transport's socket demultiplex routes a DTLS record here so the channel's DTLS receive path
         * can decrypt it and drive the SCTP stack. A record that arrives before {@link #connect()} or after
         * {@link #close()} is consumed by the handshake or dropped.
         *
         * @param record the inbound DTLS record bytes
         * @throws NullPointerException if {@code record} is {@code null}
         */
        void feedDtlsRecord(byte[] record);

        /**
         * Writes one message as SCTP DATA on the data channel.
         *
         * @param message the message bytes to send
         * @return {@code true} when the channel accepted the message, {@code false} otherwise
         * @throws NullPointerException if {@code message} is {@code null}
         */
        boolean send(byte[] message);

        /**
         * Registers the consumer that receives each inbound SCTP DATA message.
         *
         * @param consumer the inbound-message consumer; never {@code null}
         * @throws NullPointerException if {@code consumer} is {@code null}
         */
        void onMessage(Consumer<byte[]> consumer);

        /**
         * Returns whether the data channel has opened and can carry application data.
         *
         * @return {@code true} once the channel is open
         */
        boolean isReady();

        /**
         * Releases the data channel, its SCTP association, and its DTLS transport.
         */
        @Override
        void close();
    }

    /**
     * Logs the bring-up outcome so a relay-bind failure records which stage failed (ICE nomination versus the
     * DTLS/SCTP data-channel connect) and what the relay returned, rather than only the opaque
     * {@link TransportEvent#RELAY_BINDS_FAILED} event the media session surfaces.
     */
    private static final System.Logger LOGGER = System.getLogger(LiveRelayTransport.class.getName());

    /**
     * Holds the count of binding-request retransmissions before a candidate pair's check is abandoned.
     *
     * @implNote This implementation uses {@code 7}, the RFC 5389 {@code Rc} retransmission count the native
     * connectivity check applies before failing a pair.
     */
    private static final int MAX_CHECK_RETRANSMITS = 7;

    /**
     * Holds the connectivity-check retransmission interval, in nanoseconds.
     *
     * @implNote This implementation uses {@code 500} ms, the RFC 5389 {@code RTO} starting value the native
     * connectivity-check pacing uses between successive binding requests on an in-flight pair.
     */
    private static final long CHECK_RETRANSMIT_INTERVAL_NANOS = 500_000_000L;

    /**
     * Holds the maximum protected-packet size, sized to cover the cleartext packet plus the SRTP tag.
     */
    private static final int MAX_PACKET_SIZE = 1500 + 64;

    /**
     * Holds the bring-up thread's poll interval between connectivity-check passes, in milliseconds.
     *
     * @implNote This implementation polls the connectivity-check scheduler every {@code 20} ms while waiting
     * for the relay to answer, fast enough that a relay that answers within one RTT nominates its pair
     * promptly without busy-spinning the bring-up thread.
     */
    private static final long BRING_UP_POLL_INTERVAL_MILLIS = 20L;

    /**
     * Holds the interval, in nanoseconds, between successive {@code 0x0801} connectivity-keepalive pings on
     * the data channel.
     *
     * @implNote This implementation uses {@code 1000} ms, the captured default of the voip parameter
     * {@code relay_ping_interval} (re/calls2-spec/captures/voip-settings-merged.json:
     * {@code options.relay_ping_interval = 1000}). Once the data channel is open the transport sends a bare
     * {@link StunMessage#TYPE_KEEPALIVE} ping on this cadence as SCTP DATA to keep the leg alive, matching
     * the periodic {@code 0801 0000 2112a442 <txid>} pings the live capture shows on the channel.
     */
    private static final long KEEPALIVE_INTERVAL_NANOS = 1_000_000_000L;

    /**
     * Holds the number of connect-time subscription (re)transmissions sent on the keepalive cadence before the
     * transport falls back to sending the subscription only when its content changes.
     *
     * @implNote This implementation sends the {@code 0x0003} subscription on the first {@code 5} keepalive
     * ticks (about five seconds at the {@link #KEEPALIVE_INTERVAL_NANOS} cadence), reproducing the live
     * capture's connect-time subscription burst before the steady state goes quiet; the data channel is
     * unreliable (no SCTP retransmission), so a short burst guards against a lost single send before the relay
     * has the subscription it needs to forward peer media.
     */
    private static final int SUBSCRIPTION_CONNECT_BURST = 5;

    /**
     * Holds the hop-by-hop SRTP context protecting media carried as SCTP DATA.
     */
    private final HbhSrtpRelay hbhSrtp;

    /**
     * Holds the end-to-end SRTP context protecting outbound media for a one-to-one call, or {@code null}
     * for a group call whose media is hop-by-hop SRTP.
     */
    private final E2eMediaSrtp e2eSend;

    /**
     * Holds the end-to-end SRTP context unprotecting inbound media for a one-to-one call, or {@code null}
     * for a group call whose media is hop-by-hop SRTP.
     */
    private final E2eMediaSrtp e2eRecv;

    /**
     * The number of keepalive ticks between full RTCP sender reports; a compact report is sent on the
     * intervening ticks.
     */
    private static final int RTCP_SR_EVERY_TICKS = 5;

    /**
     * The trailing room, in bytes, reserved after an RTCP report for the hop-by-hop SRTCP index and tag.
     */
    private static final int RTCP_PROTECT_TRAILER = 32;

    /**
     * The RTCP payload type of the compact report.
     */
    private static final int RTCP_PT_COMPACT = 208;

    /**
     * The RTCP payload type of the sender report.
     */
    private static final int RTCP_PT_SENDER_REPORT = 200;

    /**
     * Holds the local media synchronization source read from the first outbound RTP packet, keying the
     * outbound RTCP reports.
     */
    private volatile int localMediaSsrc;

    /**
     * Whether {@link #localMediaSsrc} has been observed from an outbound RTP packet.
     */
    private volatile boolean localMediaSsrcKnown;

    /**
     * Holds the remote media synchronization source read from the first inbound RTP packet, named in the
     * outbound RTCP reports.
     */
    private volatile int remoteMediaSsrc;

    /**
     * Whether {@link #remoteMediaSsrc} has been observed from an inbound RTP packet.
     */
    private volatile boolean remoteMediaSsrcKnown;

    /**
     * Counts the outbound RTP packets sent, reported in the RTCP sender report.
     */
    private volatile long rtpPacketsSent;

    /**
     * Counts the outbound RTP payload octets sent, reported in the RTCP sender report.
     */
    private volatile long rtpOctetsSent;

    /**
     * Holds the RTP timestamp of the last outbound packet, reported in the RTCP sender report.
     */
    private volatile int lastRtpTimestamp;

    /**
     * Counts the RTCP reports sent, selecting the compact-versus-sender-report cadence.
     */
    private int rtcpTick;

    /**
     * Whether the first outbound RTCP report (or its failure) has been logged.
     */
    private boolean firstRtcpLogged;

    /**
     * Holds the derived WARP-auth key for the hop-by-hop WARP message-integrity tag, or {@code null}
     * when the relay negotiated no tag.
     */
    private final byte[] warpAuthKey;

    /**
     * Holds the relay-negotiated WARP message-integrity tag length, or {@code 0} when none.
     */
    private final int warpMiTagLength;

    /**
     * Holds the relay {@code <key>} keying the {@code 0x0003} subscription envelope's HMAC-SHA1
     * message-integrity, used verbatim, or {@code null} when no key was supplied (a degenerate transport that
     * publishes no subscription).
     *
     * <p>The {@code <key>} arrives as ASCII base64 text and is used verbatim both as the ICE password and as
     * the application-STUN {@code 0x0003} subscription message-integrity key (HMAC-SHA1); the relay binds the
     * credential the envelope's leading {@code 0x4000} relay {@code <token>} references and then verifies the
     * integrity with this key. The {@code 0x0801} connectivity keepalive carries no message-integrity, so
     * this key is exercised only by the subscription envelope.
     */
    private final byte[] relayKey;

    /**
     * Holds the relay {@code <token>} bytes carried as the leading {@code 0x4000} RELAY-TOKEN attribute of the
     * {@code 0x0003} subscription envelope, or {@code null} when no token was supplied (a degenerate transport
     * that publishes no subscription).
     *
     * <p>The relay binds the credential this token references before verifying the envelope's message
     * integrity; without it the relay rejects the envelope with {@code "Integrity failure: Hmac mismatch"}
     * even when {@link #relayKey} is correct (zapo-caller reverse engineering of the wasm allocate).
     */
    private final byte[] relayToken;

    /**
     * Holds the selected relay endpoint's reflexive transport address carried as the {@code 0x0016}
     * XOR-MAPPED-ADDRESS attribute of the {@code 0x0003} subscription envelope, or {@code null} when no
     * address was supplied.
     */
    private final InetSocketAddress relayReflexiveAddress;

    /**
     * Holds the sink receiving inbound cleartext media (RTP) bytes.
     */
    private final Consumer<byte[]> mediaSink;

    /**
     * Holds the ICE agent driving connectivity checks and nomination over the host socket, seeded with the
     * single relay host candidate, or {@code null} for a degenerate test transport that runs no bring-up.
     */
    private final IceAgent iceAgent;

    /**
     * Holds the host UDP socket sink the ICE checks and DTLS records leave through.
     */
    private final Egress socketEgress;

    /**
     * Holds the data channel backend, or {@code null} until the DTLS-wrapped SCTP data channel is wired.
     */
    private final DataChannel dataChannel;

    /**
     * Holds the monotonic nanosecond reference this transport's WARP rolling clock counts from, captured
     * once at construction.
     *
     * <p>Every WARP message carries a sixteen-bit sample of a millisecond clock at header offsets two and
     * three; the sample is the number of milliseconds elapsed since this reference, masked to sixteen bits
     * (see {@link #warpTimestamp()}). The reference is captured when the transport is built, the moment the
     * engine captures its own WARP clock reference when the media transport is created.
     *
     * @implNote This implementation captures {@link System#nanoTime()} at construction as the faithful
     *           equivalent of {@code wa_transport_p2p_create} latching the engine's WARP clock reference at
     *           {@code engine + 0x67A00} (func[4947] in the wa-voip WASM module {@code ff-tScznZ8P}; the
     *           field is {@code engine + 0x67B90} in {@code O4cDmmXP6rI}). The engine reads that reference on
     *           every WARP build through its elapsed-millisecond helper {@code (now_ns - ref_ns) / 1e6}
     *           (func[9123]/func[9501]) where both timestamps are process-relative monotonic nanoseconds
     *           from the host clock (func[9271]/func[9653]); {@link System#nanoTime()} is the JVM equivalent
     *           of that monotonic, process-relative timebase. The exact wall-clock origin of the engine's
     *           reference is a host runtime value that is not statically recoverable, so the reference moment
     *           (transport creation) and unit (monotonic milliseconds) are reproduced rather than the
     *           absolute origin. The derivation is recorded in {@code re/calls2-spec/warp-header-layout-RE.md}.
     */
    private final long warpClockReferenceNanos;

    /**
     * Demultiplexes inbound socket datagrams: STUN to the ICE agent, DTLS records to the DTLS/SCTP bridge.
     */
    private final InboundPacketDemux socketDemux;

    /**
     * Demultiplexes each inbound SCTP DATA message: hop-by-hop SRTP media to the sink, RTCP, application
     * STUN, and WARP to their handlers.
     */
    private final InboundPacketDemux channelDemux;

    /**
     * Tracks the in-flight connectivity check per candidate pair by its STUN transaction id, so a binding
     * success response is matched back to the pair that produced it.
     *
     * <p>It is concurrent because the bring-up thread starts and paces checks while the socket reader thread
     * completes them from inbound binding responses.
     */
    private final Map<TransactionKey, PendingCheck> pendingChecks = new ConcurrentHashMap<>();

    /**
     * Holds whether the ICE agent has nominated a candidate pair, set on the socket reader thread when a
     * binding success completes the check and read by the bring-up thread to advance to the DTLS handshake.
     */
    private volatile boolean pairNominated;

    /**
     * Counts inbound STUN datagrams routed to the ICE agent, written by the socket reader thread and read by
     * the bring-up thread for the relay-bind failure diagnostic so a timeout can report whether the relay
     * answered at all.
     */
    private volatile int inboundStunCount;

    /**
     * Counts inbound STUN datagrams the ICE agent could not verify (wrong credential, no MESSAGE-INTEGRITY, or
     * a malformed message), written by the socket reader thread and read by the bring-up thread; a non-zero
     * count on a nomination timeout means the relay replied but the reply was rejected.
     */
    private volatile int stunVerifyFailures;

    /**
     * Counts connectivity-check sends the host egress rejected, written and read by the bring-up thread; a
     * non-zero count on a nomination timeout means the binding requests never left the socket.
     */
    private volatile int checkSendFailures;

    /**
     * Counts inbound media (RTP) messages that failed hop-by-hop SRTP authentication and were dropped, for the
     * media-flow diagnostic; a high count means inbound media arrives but cannot be decrypted (a key or ROC
     * mismatch), which presents to the call as lost media and trips the media-RX reconnect watchdog.
     */
    private volatile int inboundMediaDecryptFailures;

    /**
     * Counts inbound data-channel messages of any class (media, RTCP, application STUN, WARP), for the
     * media-flow diagnostic; the first message is logged once with its leading-byte class so a stalled call
     * can be split into "the relay sends nothing back" versus "the relay sends control but no peer media".
     */
    private volatile int inboundChannelMsgCount;

    /**
     * Records whether the first inbound application-STUN response (leading byte {@code 0x01}, the
     * {@code 0x0101} bind ack or {@code 0x0103} subscription ack) has been logged, for the media-flow
     * diagnostic; a {@code 0x0103} confirms the relay accepted this client's subscription.
     */
    private volatile boolean firstInboundAckLogged;

    /**
     * Records whether the first outbound {@code 0x0003} subscription send has been logged, for the media-flow
     * diagnostic.
     */
    private boolean subscriptionLogged;

    /**
     * Records whether the subscription-not-sent warning has been logged, so a transport that never learns its
     * relay key or reflexive address warns once rather than on every keepalive tick.
     */
    private boolean subscriptionSkipLogged;

    /**
     * Serializes access to the {@link IceAgent} and the {@link #pendingChecks} so the bring-up thread's
     * connectivity-check scheduling never interleaves with the socket reader thread's inbound STUN handling,
     * honouring the agent's single-threaded contract.
     */
    private final Object iceLock = new Object();

    /**
     * Holds the registered transport-event listener, or {@code null} until one is registered.
     */
    private Consumer<TransportEvent> eventListener;

    /**
     * Holds the registered inbound-RTCP-feedback listener, or {@code null} until one is registered.
     */
    private Consumer<RtcpFeedback> rtcpFeedbackListener;

    /**
     * TEMP diagnostic counter gating a one-time log of the first inbound RTCP message's unprotect and parse
     * outcome, to diagnose why the rate-control loop never receives feedback. Remove once confirmed.
     */
    private int inboundRtcpDiagCount;

    /**
     * Holds the registered inbound application-STUN handler, or {@code null} until one is registered.
     *
     * <p>The application STUN messages carried as SCTP DATA (the {@code 0x0801} connectivity ping and the
     * {@code 0x0003} subscription envelope) are surfaced to this handler; it is the seam the subscription
     * and keepalive layers consume in a later phase.
     */
    private Consumer<byte[]> appStunHandler;

    /**
     * Holds whether the data channel has been observed open, so the open event fires exactly once.
     */
    private boolean dataChannelOpen;

    /**
     * Holds the monotonic nanosecond timestamp of the most recent {@code 0x0801} keepalive ping, or
     * {@link Long#MIN_VALUE} before the first ping so the first {@link #tick(long)} after the channel opens
     * sends one immediately.
     */
    private long lastKeepaliveNanos = Long.MIN_VALUE;

    /**
     * Holds the stable per-session connectivity transaction id shared by the {@code 0x0801} keepalive and the
     * {@code 0x0003} subscription envelope.
     *
     * @implNote This implementation generates one transaction id per transport and reuses it for every
     * connectivity-keepalive ping and every subscription envelope, matching the live capture where the
     * keepalive and the subscription carry the same transaction id for the whole call (the {@code 0x0103}
     * subscription ack and the {@code 0x0802} keepalive pong echo it). A fresh per-message id would diverge
     * from the captured shared id and defeat the content-change suppression in
     * {@link #sendSubscriptionEnvelope(StreamSubscriptions)}.
     */
    private final byte[] connectivityTransactionId =
            VoipCryptoNative.randomBytes(StunMessage.TRANSACTION_ID_LENGTH);

    /**
     * Holds the callback that rebuilds and ships the {@code 0x0003} subscription envelope, invoked on the
     * keepalive cadence once the data channel is open, or {@code null} when the transport publishes no
     * subscription.
     *
     * <p>Installed through {@link #onSubscriptionResend(Runnable)}: the media session sets it to the resender
     * that builds the fused {@link StreamSubscriptions} matrix from the live roster and calls
     * {@link #sendSubscriptionEnvelope(StreamSubscriptions)}. The transport owns the cadence (the keepalive
     * loop); the session owns the content.
     */
    private volatile Runnable subscriptionResendHook;

    /**
     * Holds the bytes of the most recently sent subscription envelope so an unchanged subscription is sent
     * once (past the {@link #SUBSCRIPTION_CONNECT_BURST} connect-time retransmissions) rather than on every
     * keepalive tick, or {@code null} before the first subscription is sent.
     */
    private byte[] lastSubscriptionEnvelope;

    /**
     * Holds the number of remaining connect-time subscription retransmissions, counted down by
     * {@link #sendSubscriptionEnvelope(StreamSubscriptions)} as the connect-time burst is sent.
     */
    private int subscriptionConnectBurstRemaining = SUBSCRIPTION_CONNECT_BURST;

    /**
     * Holds whether the transport has been started.
     */
    private boolean started;

    /**
     * Holds whether the transport has been closed.
     */
    private volatile boolean closed;

    /**
     * Holds the bring-up thread that drives ICE connectivity checks to nomination and then opens the data
     * channel, or {@code null} until {@link #start()} launches it.
     */
    private volatile Thread bringUpThread;

    /**
     * Constructs the web media transport over its hop-by-hop SRTP context, ICE agent, host socket egress,
     * and data channel backend.
     *
     * @param hbhSrtp               the hop-by-hop SRTP context protecting media carried as SCTP DATA
     * @param warpAuthKey           the WARP-auth key for the hop-by-hop WARP MI tag, or {@code null} when the
     *                              relay negotiated no tag
     * @param warpMiTagLength       the relay-negotiated WARP MI tag length, or {@code 0} when none
     * @param relayKey              the relay {@code <key>} keying the {@code 0x0003} subscription envelope's
     *                              HMAC-SHA1 message-integrity, used verbatim, or {@code null} when the call
     *                              publishes no subscription
     * @param relayToken            the relay {@code <token>} bytes carried as the leading {@code 0x4000}
     *                              RELAY-TOKEN attribute of the subscription envelope, or {@code null} when
     *                              the call publishes no subscription
     * @param relayReflexiveAddress the selected relay endpoint's reflexive transport address carried as the
     *                              {@code 0x0003} envelope's {@code 0x0016} XOR-MAPPED-ADDRESS, or
     *                              {@code null} when the call publishes no subscription
     * @param mediaSink             the sink receiving inbound cleartext media bytes
     * @param iceAgent              the ICE agent driving connectivity checks over the host socket, or
     *                              {@code null} until the ICE material an inbound transport stanza carries is
     *                              plumbed
     * @param socketEgress          the host UDP socket sink the ICE checks and DTLS records leave through
     * @param dataChannel           the SCTP data channel backend carrying every message as SCTP DATA, or
     *                              {@code null} until the DTLS-wrapped SCTP data channel is wired
     * @param e2eSend               the end-to-end SRTP context protecting outbound media for a one-to-one
     *                              call, or {@code null} for a group call whose media is hop-by-hop SRTP
     * @param e2eRecv               the end-to-end SRTP context unprotecting inbound media for a one-to-one
     *                              call, or {@code null} for a group call whose media is hop-by-hop SRTP
     * @throws NullPointerException     if {@code hbhSrtp}, {@code mediaSink}, or {@code socketEgress} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code warpMiTagLength} is negative, or is positive while
     *                                  {@code warpAuthKey} is {@code null}
     */
    public LiveRelayTransport(HbhSrtpRelay hbhSrtp,
                              byte[] warpAuthKey,
                              int warpMiTagLength,
                              byte[] relayKey,
                              byte[] relayToken,
                              InetSocketAddress relayReflexiveAddress,
                              Consumer<byte[]> mediaSink,
                              IceAgent iceAgent,
                              Egress socketEgress,
                              DataChannel dataChannel,
                              E2eMediaSrtp e2eSend,
                              E2eMediaSrtp e2eRecv) {
        this.hbhSrtp = Objects.requireNonNull(hbhSrtp, "hbhSrtp cannot be null");
        this.mediaSink = Objects.requireNonNull(mediaSink, "mediaSink cannot be null");
        this.iceAgent = iceAgent;
        this.socketEgress = Objects.requireNonNull(socketEgress, "socketEgress cannot be null");
        if (warpMiTagLength < 0) {
            throw new IllegalArgumentException("warpMiTagLength cannot be negative: " + warpMiTagLength);
        }
        if (warpMiTagLength > 0 && warpAuthKey == null) {
            throw new IllegalArgumentException("warpMiTagLength is set but warpAuthKey is null");
        }
        this.warpAuthKey = warpAuthKey == null ? null : warpAuthKey.clone();
        this.warpMiTagLength = warpMiTagLength;
        this.relayKey = relayKey == null ? null : relayKey.clone();
        this.relayToken = relayToken == null ? null : relayToken.clone();
        this.relayReflexiveAddress = relayReflexiveAddress;
        this.dataChannel = dataChannel;
        this.e2eSend = e2eSend;
        this.e2eRecv = e2eRecv;
        this.warpClockReferenceNanos = System.nanoTime();
        this.socketDemux = new InboundPacketDemux(this::onStun, this::onDtlsRecord, null, null);
        this.channelDemux = new InboundPacketDemux(
                this::onChannelStun, null, this::onInboundMedia, this::onInboundRtcpMessage);
        if (dataChannel != null) {
            dataChannel.onMessage(this::onDataChannelMessage);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation launches a dedicated bring-up virtual thread that drives the relay leg
     *           through {@link #runBringUp()}: it paces the RFC 8445 connectivity checks against the single
     *           relay host candidate the {@link IceAgent} was seeded with until a pair is nominated, then
     *           runs {@link DataChannel#connect()} to complete the DTLS handshake, the SCTP connect, and the
     *           pre-negotiated channel open, and finally observes the channel ready and emits
     *           {@link TransportEvent#RELAY_CREATE_SUCCESS}. A bring-up failure emits
     *           {@link TransportEvent#RELAY_BINDS_FAILED}. When no ICE agent or data channel backend is
     *           wired (a degenerate test transport) the bring-up only reports a backend that opens itself,
     *           through {@link #pollDataChannel()}.
     */
    @Override
    public void start() {
        ensureOpen();
        started = true;
        if (iceAgent == null || dataChannel == null) {
            // A degenerate transport (no ICE agent and no data channel backend) cannot run the relay
            // bring-up; it only surfaces a backend that opens itself, matching the test transport shape.
            pollDataChannel();
            return;
        }
        bringUpThread = Thread.ofVirtual()
                .name("calls2-relay-bringup")
                .start(this::runBringUp);
    }

    /**
     * Drives the relay leg bring-up on the bring-up thread: ICE to nomination, then the data-channel
     * connect, then the ready event.
     *
     * <p>The relay block synthesizes a single host candidate at the relay address, so the checklist holds
     * one pair; this paces the connectivity check on it until the relay answers and the controlling agent
     * nominates the pair, bounded by the connectivity-check budget. It then runs {@link DataChannel#connect()}
     * (the DTLS handshake, the SCTP connect, and the pre-negotiated channel open) and emits
     * {@link TransportEvent#RELAY_CREATE_SUCCESS} once the channel reports ready. A bring-up that cannot
     * nominate a pair or that fails the data-channel connect emits {@link TransportEvent#RELAY_BINDS_FAILED}.
     */
    private void runBringUp() {
        try {
            if (!driveIceToNomination()) {
                if (!closed) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "calls2 relay ICE nomination failed against {0}: no verified binding response "
                                    + "({1} inbound STUN datagram(s), {2} rejected, {3} check-send failure(s))",
                            relayReflexiveAddress, inboundStunCount, stunVerifyFailures, checkSendFailures);
                    emit(TransportEvent.RELAY_BINDS_FAILED);
                }
                return;
            }
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay ICE nominated against {0} after {1} inbound STUN datagram(s); connecting data channel",
                    relayReflexiveAddress, inboundStunCount);
            dataChannel.connect();
            pollDataChannel();
            runKeepaliveLoop();
        } catch (RuntimeException exception) {
            if (!closed) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "calls2 relay data-channel bring-up failed against " + relayReflexiveAddress
                                + " after ICE nomination (DTLS handshake or SCTP connect)", exception);
                emit(TransportEvent.RELAY_BINDS_FAILED);
            }
        }
    }

    /**
     * Runs the connectivity-keepalive cadence on the bring-up thread until the transport is closed.
     *
     * <p>Once the data channel is open the transport keeps the leg alive by sending a bare
     * {@link StunMessage#TYPE_KEEPALIVE} ping on the {@link #KEEPALIVE_INTERVAL_NANOS} cadence; the live
     * session wires no external transport-thread tick driver, so the bring-up thread that opened the
     * channel continues into this loop rather than exiting. The same pacing is also exposed through
     * {@link #tick(long)} for a driver that does pump the transport thread, and the two share
     * {@link #driveKeepalive(long)} so a ping is never sent twice in one interval.
     */
    private void runKeepaliveLoop() {
        while (!closed) {
            driveKeepalive(System.nanoTime());
            driveSubscriptionResend();
            driveRtcp();
            try {
                Thread.sleep(KEEPALIVE_INTERVAL_NANOS / 1_000_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Paces the ICE connectivity check against the relay host candidate until a pair is nominated or the
     * check budget is exhausted.
     *
     * <p>This reuses the same connectivity-check scheduler {@link #tick(long)} drives, calling it on a fixed
     * cadence on the bring-up thread (the live session wires no external tick driver) and returning as soon
     * as the agent nominates a pair. It returns {@code false} when the relay never answers within the
     * retransmission budget so the caller can surface a relay-bind failure.
     *
     * @return {@code true} once a candidate pair is nominated, {@code false} when the budget is exhausted or
     * the transport is closed first
     */
    private boolean driveIceToNomination() {
        var deadlineNanos = System.nanoTime()
                + (MAX_CHECK_RETRANSMITS + 1L) * CHECK_RETRANSMIT_INTERVAL_NANOS;
        while (!closed && System.nanoTime() < deadlineNanos) {
            synchronized (iceLock) {
                driveConnectivityChecks(System.nanoTime());
            }
            if (pairNominated) {
                return true;
            }
            try {
                Thread.sleep(BRING_UP_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !closed && pairNominated;
    }

    @Override
    public int sendMedia(byte[] packet, int length) {
        ensureSendable();
        Objects.requireNonNull(packet, "packet cannot be null");
        requireLength(length, packet.length);
        // Only sniff the media SSRC and RTCP sender stats from a real RTP packet (version two, top two bits
        // 10); a stray non-RTP datagram on this path would otherwise mis-key the outbound RTCP report's SSRC.
        var isRtp = length >= 12 && (packet[0] & 0xC0) == 0x80;
        if (isRtp) {
            localMediaSsrc = readUint32(packet, 8);
            localMediaSsrcKnown = true;
            lastRtpTimestamp = readUint32(packet, 4);
            rtpPacketsSent++;
            rtpOctetsSent += length - 12;
        }
        var protectedLength = e2eSend != null
                ? e2eSend.protectRtp(packet, length)
                : hbhSrtp.protectRtp(packet, length);
        var accepted = dataChannel.send(slice(packet, protectedLength));
        if (isRtp) {
            if (rtpPacketsSent == 1) {
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay first outbound RTP: ssrc=0x{0}, payloadLen={1}, onWireLen={2}, e2e={3}, accepted={4}",
                        Integer.toHexString(localMediaSsrc), length, protectedLength, e2eSend != null, accepted);
            } else if (rtpPacketsSent % 250 == 0) {
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay outbound RTP progress: {0} packets / {1} octets sent (last accepted={2})",
                        rtpPacketsSent, rtpOctetsSent, accepted);
            }
        }
        return accepted ? length : 0;
    }

    @Override
    public int sendRtcp(byte[] packet, int length) {
        ensureSendable();
        Objects.requireNonNull(packet, "packet cannot be null");
        requireLength(length, packet.length);
        var protectedLength = hbhSrtp.protectRtcp(packet, length);
        return dataChannel.send(slice(packet, protectedLength)) ? length : 0;
    }

    @Override
    public int sendStandaloneWarp(WarpMessage.Standalone message) {
        ensureSendable();
        Objects.requireNonNull(message, "message cannot be null");
        var encoded = message.encode(warpTimestamp());
        var datagram = warpMiTagLength > 0
                ? WarpMessageIntegrity.appendTag(encoded, warpAuthKey, warpMiTagLength)
                : encoded;
        return dataChannel.send(datagram) ? datagram.length : 0;
    }

    /**
     * Returns the current WARP rolling-clock sample, the number of milliseconds elapsed since this
     * transport's {@link #warpClockReferenceNanos reference}, masked to sixteen bits.
     *
     * <p>This is the value the engine writes big-endian at WARP header offsets two and three. The elapsed
     * nanoseconds are floored to whole milliseconds and reduced modulo {@code 0x10000}, so the clock rolls
     * over roughly every sixty-five seconds, exactly as the engine's sixteen-bit field does.
     *
     * @return the rolling millisecond clock sample in {@code 0..0xFFFF}
     * @implNote This implementation computes {@code ((System.nanoTime() - warpClockReferenceNanos) /
     *           1_000_000) & 0xFFFF}, reproducing the engine's {@code (now_ns - ref_ns) / 1e6} truncated to
     *           an unsigned integer and then sampled to sixteen bits by the WARP serializer's {@code & 0xffff}
     *           (func[9122]/func[9500] feeding the serializer in the wa-voip WASM module). The monotonic
     *           difference is never negative, so the floor division and mask need no clamp.
     */
    private int warpTimestamp() {
        var elapsedMillis = (System.nanoTime() - warpClockReferenceNanos) / 1_000_000L;
        return (int) (elapsedMillis & 0xFFFF);
    }

    /**
     * Writes one application-data message as SCTP DATA on the data channel.
     *
     * <p>This is the seam the application-data and subscription layers ship a serialized payload through:
     * the bytes travel as one SCTP DATA message on the open data channel, exactly as media does. Before the
     * channel is open, or after {@link #close()}, the message is dropped and {@code false} is returned.
     *
     * @param message the message bytes to send; never {@code null}
     * @return {@code true} when the channel accepted the message, {@code false} otherwise
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public boolean sendAppData(byte[] message) {
        Objects.requireNonNull(message, "message cannot be null");
        if (closed || dataChannel == null || !dataChannel.isReady()) {
            return false;
        }
        return dataChannel.send(message);
    }

    /**
     * Builds and ships a {@code 0x0003} subscription envelope carrying the given subscription map over the
     * data channel.
     *
     * <p>The envelope is the message-integrity-protected {@code type 0x0003 + magic + txid +
     * WA_SUBSCRIPTION(0x4024) + WA_XOR_MAPPED_ADDRESS(0x0016) + MESSAGE_INTEGRITY(0x0008)} the
     * {@link SubscriptionEnvelope#subscriptionEnvelope(byte[], StreamSubscriptions, InetSocketAddress, byte[])}
     * assembler builds, keyed by the relay {@code <key>} this transport was constructed with and carrying the
     * selected relay endpoint's reflexive address; it is written as one SCTP DATA message on the open data
     * channel through {@link #sendAppData(byte[])}. The leading {@code 0x4000} WARP attribute the live capture
     * carries is an optional hop-by-hop-SRTP-sealed control report Cobalt does not emit (see
     * {@link SubscriptionEnvelope}). When this transport was given no relay
     * key or reflexive address (a degenerate transport that publishes no subscription), or the channel is not
     * open, the envelope is not sent and {@code false} is returned.
     *
     * @implNote This implementation stamps the envelope with the stable per-session
     * {@link #connectivityTransactionId} (the keepalive and the subscription share one transaction id in the
     * live capture) and re-ships it on every keepalive tick for the call's lifetime, reproducing the engine's
     * roughly one-hertz periodic relay allocate sent on every open relay to keep the relay forwarding the
     * subscribed streams; a one-shot subscription goes stale and the relay stops forwarding the peer's media
     * within a few seconds. It is driven on the single transport (keepalive) thread.
     *
     * @param subscriptions the combined per-stream subscription map to publish; never {@code null}
     * @return {@code true} when the envelope was assembled and accepted by the data channel, {@code false}
     *         otherwise
     * @throws NullPointerException        if {@code subscriptions} is {@code null}
     * @throws WhatsAppCallException.Srtp  if the platform cannot compute the HMAC-SHA1 message integrity
     */
    public boolean sendSubscriptionEnvelope(StreamSubscriptions subscriptions) {
        Objects.requireNonNull(subscriptions, "subscriptions cannot be null");
        if (closed || relayKey == null || relayToken == null || relayReflexiveAddress == null) {
            if (!closed && !subscriptionSkipLogged) {
                subscriptionSkipLogged = true;
                LOGGER.log(System.Logger.Level.WARNING,
                        "calls2 relay 0x0003 subscription NOT sent: relayKey present={0}, relayToken present={1}, reflexiveAddr present={2}",
                        relayKey != null, relayToken != null, relayReflexiveAddress != null);
            }
            return false;
        }
        var envelope = SubscriptionEnvelope.subscriptionEnvelope(
                relayKey, relayToken, subscriptions, relayReflexiveAddress, connectivityTransactionId);
        var accepted = sendAppData(envelope);
        if (!subscriptionLogged) {
            subscriptionLogged = true;
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay first outbound 0x0003 subscription sent ({0} bytes, {1} stream entries, token {3} bytes, key {4} bytes); accepted={2}",
                    envelope.length, subscriptions.entries().size(), accepted, relayToken.length, relayKey.length);
        }
        return accepted;
    }

    /**
     * Builds and ships a {@code 0x0003} subscription envelope carrying the local stream descriptors over the
     * data channel.
     *
     * <p>Identical to {@link #sendSubscriptionEnvelope(StreamSubscriptions)} except the {@code 0x4024}
     * attribute carries the live-capture {@link StreamDescriptors} descriptor list (this client's own send
     * streams: audio plus both simulcast video layers, each a media/FEC/NACK triple) the relay reads to forward
     * this client's media, in place of the per-(participant, stream) subscription map. This is the form the
     * subscription resend ships.
     *
     * @implNote This implementation stamps the envelope with the stable per-session
     * {@link #connectivityTransactionId} and re-ships it on every keepalive tick for the call's lifetime, the
     * same cadence and framing {@link #sendSubscriptionEnvelope(StreamSubscriptions)} uses; it is driven on the
     * single transport (keepalive) thread.
     *
     * @param descriptors the local send-stream descriptors to publish; never {@code null}
     * @return {@code true} when the envelope was assembled and accepted by the data channel, {@code false}
     *         otherwise
     * @throws NullPointerException        if {@code descriptors} is {@code null}
     * @throws WhatsAppCallException.Srtp  if the platform cannot compute the HMAC-SHA1 message integrity
     */
    public boolean sendSubscriptionEnvelope(StreamDescriptors descriptors) {
        Objects.requireNonNull(descriptors, "descriptors cannot be null");
        if (closed || relayKey == null || relayToken == null || relayReflexiveAddress == null) {
            if (!closed && !subscriptionSkipLogged) {
                subscriptionSkipLogged = true;
                LOGGER.log(System.Logger.Level.WARNING,
                        "calls2 relay 0x0003 subscription NOT sent: relayKey present={0}, relayToken present={1}, reflexiveAddr present={2}",
                        relayKey != null, relayToken != null, relayReflexiveAddress != null);
            }
            return false;
        }
        var envelope = SubscriptionEnvelope.subscriptionEnvelope(
                relayKey, relayToken, descriptors, relayReflexiveAddress, connectivityTransactionId);
        var accepted = sendAppData(envelope);
        if (!subscriptionLogged) {
            subscriptionLogged = true;
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay first outbound 0x0003 subscription sent ({0} bytes, {1} stream descriptors, token {3} bytes, key {4} bytes); accepted={2}",
                    envelope.length, descriptors.streamDescriptors().size(), accepted, relayToken.length, relayKey.length);
        }
        return accepted;
    }

    @Override
    public void onInboundDatagram(byte[] datagram, SocketAddress source) {
        Objects.requireNonNull(datagram, "datagram cannot be null");
        if (closed) {
            return;
        }
        socketDemux.accept(datagram, source);
    }

    @Override
    public void onTransportEvent(Consumer<TransportEvent> listener) {
        this.eventListener = Objects.requireNonNull(listener, "listener cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation stores the listener so {@link #onInboundRtcpMessage(byte[])}
     *           SRTCP-unprotects each inbound RTCP data-channel message through the hop-by-hop context,
     *           parses it with {@link RtcpFeedbackParser}, and delivers the feedback. A message that fails
     *           hop-by-hop authentication or carries no recognised feedback is dropped silently.
     */
    @Override
    public void onInboundRtcp(Consumer<RtcpFeedback> listener) {
        this.rtcpFeedbackListener = listener;
    }

    /**
     * Registers the handler that receives each inbound application-STUN message carried as SCTP DATA.
     *
     * <p>The application STUN messages on the data channel are the {@code 0x0801} connectivity ping and the
     * {@code 0x0003} subscription envelope; this handler is the seam the keepalive and subscription layers
     * consume. A second registration replaces the first; passing {@code null} clears the handler.
     *
     * @param handler the application-STUN handler, or {@code null} to clear it
     */
    public void onAppStun(Consumer<byte[]> handler) {
        this.appStunHandler = handler;
    }

    /**
     * Installs the callback that rebuilds and ships the {@code 0x0003} subscription envelope on the keepalive
     * cadence once the data channel is open.
     *
     * <p>The media session passes the resender that builds the fused {@link StreamSubscriptions} matrix from
     * the live roster and ships it through {@link #sendSubscriptionEnvelope(StreamSubscriptions)}. Passing
     * {@code null} publishes no subscription. The hook runs on the transport's keepalive thread, the same
     * thread and cadence as the {@code 0x0801} keepalive, so the connect-time subscription burst and the
     * keepalive interleave as the live capture shows.
     *
     * @param hook the subscription resend callback, or {@code null} to publish no subscription
     */
    public void onSubscriptionResend(Runnable hook) {
        this.subscriptionResendHook = hook;
    }

    /**
     * Runs the installed subscription resend hook once when the data channel is open.
     *
     * <p>Invoked on each keepalive tick alongside {@link #driveKeepalive(long)}; the hook rebuilds the fused
     * subscription from the current roster and ships it through
     * {@link #sendSubscriptionEnvelope(StreamSubscriptions)}, which sends the connect-time burst and then
     * suppresses an unchanged envelope. It is a no-op before the channel is ready or when no hook is
     * installed.
     */
    private void driveSubscriptionResend() {
        var hook = subscriptionResendHook;
        if (hook == null || dataChannel == null || !dataChannel.isReady()) {
            return;
        }
        hook.run();
    }

    /**
     * Drives the RFC 8445 connectivity-check scheduler, the data-channel-open watchdog, and the
     * connectivity-keepalive ping for one transport-thread tick.
     *
     * <p>When started and open, this retransmits or paces the in-flight connectivity check, sends a fresh
     * binding request on the highest-priority not-yet-checked pair when none is in flight, surfaces the
     * data channel opening as {@link TransportEvent#RELAY_CREATE_SUCCESS} the first time it is observed
     * ready, and once the channel is open sends a bare {@link StunMessage#TYPE_KEEPALIVE} ping on the
     * {@link #KEEPALIVE_INTERVAL_NANOS} cadence as SCTP DATA. Before {@link #start()} or after
     * {@link #close()} this is a no-op. It is called by the single transport thread on its tick cadence.
     *
     * @param nowNanos the current time in the transport thread's monotonic nanosecond timebase
     */
    public void tick(long nowNanos) {
        if (closed || !started) {
            return;
        }
        if (iceAgent != null) {
            synchronized (iceLock) {
                driveConnectivityChecks(nowNanos);
            }
        }
        pollDataChannel();
        driveKeepalive(nowNanos);
        driveSubscriptionResend();
        driveRtcp();
    }

    /**
     * Sends a {@code 0x0801} connectivity-keepalive ping when the data channel is open and the keepalive
     * interval has elapsed since the last ping.
     *
     * <p>The ping is a bare {@link StunMessage#TYPE_KEEPALIVE} message with no attributes, built by
     * {@link SubscriptionEnvelope#keepalive()} and written as one SCTP DATA message on the data channel.
     * It is skipped until the channel reports ready and is paced to one ping per
     * {@link #KEEPALIVE_INTERVAL_NANOS}.
     *
     * @param nowNanos the current monotonic nanosecond timestamp
     */
    private void driveKeepalive(long nowNanos) {
        if (dataChannel == null || !dataChannel.isReady()) {
            return;
        }
        if (lastKeepaliveNanos != Long.MIN_VALUE && nowNanos - lastKeepaliveNanos < KEEPALIVE_INTERVAL_NANOS) {
            return;
        }
        lastKeepaliveNanos = nowNanos;
        dataChannel.send(SubscriptionEnvelope.keepalive(connectivityTransactionId));
    }

    /**
     * Builds and ships one periodic RTCP report over the data channel.
     *
     * <p>Sends a compact RTCP report on each keepalive tick and a full sender report every
     * {@value #RTCP_SR_EVERY_TICKS} ticks, hop-by-hop SRTCP protected, once the channel is open and an
     * outbound media packet has established the local synchronization source. The peer's connection-health
     * monitor tears the call down when it receives no RTCP even while RTP keeps flowing.
     */
    private void driveRtcp() {
        if (dataChannel == null || !dataChannel.isReady() || !localMediaSsrcKnown) {
            return;
        }
        rtcpTick++;
        var report = rtcpTick % RTCP_SR_EVERY_TICKS == 0 ? buildSenderReport() : buildCompactRtcp();
        var buffer = new byte[report.length + RTCP_PROTECT_TRAILER];
        System.arraycopy(report, 0, buffer, 0, report.length);
        try {
            var protectedLength = hbhSrtp.protectRtcp(buffer, report.length);
            var accepted = dataChannel.send(slice(buffer, protectedLength));
            if (!firstRtcpLogged) {
                firstRtcpLogged = true;
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay first outbound RTCP sent ({0} bytes, localSsrc=0x{1}, remoteSsrc=0x{2}); accepted={3}",
                        protectedLength, Integer.toHexString(localMediaSsrc), Integer.toHexString(remoteMediaSsrc),
                        accepted);
            }
        } catch (RuntimeException exception) {
            // A failed RTCP protection or send is non-fatal; the next tick retries.
            if (!firstRtcpLogged) {
                firstRtcpLogged = true;
                LOGGER.log(System.Logger.Level.WARNING,
                        "calls2 relay first outbound RTCP protect/send failed (hop-by-hop SRTCP)", exception);
            }
        }
    }

    /**
     * Builds the twelve-byte compact RTCP report carrying the local and remote synchronization sources.
     *
     * @return the compact RTCP report bytes
     */
    private byte[] buildCompactRtcp() {
        var report = new byte[12];
        report[0] = (byte) 0x81;
        report[1] = (byte) RTCP_PT_COMPACT;
        report[3] = 2;
        writeUint32(report, 4, localMediaSsrc);
        writeUint32(report, 8, remoteMediaSsrcKnown ? remoteMediaSsrc : 0);
        return report;
    }

    /**
     * Builds the twenty-eight-byte RTCP sender report carrying the wall-clock and the send counters.
     *
     * @return the sender report bytes
     */
    private byte[] buildSenderReport() {
        var report = new byte[28];
        report[0] = (byte) 0x80;
        report[1] = (byte) RTCP_PT_SENDER_REPORT;
        report[3] = 6;
        writeUint32(report, 4, localMediaSsrc);
        var nowMillis = System.currentTimeMillis();
        writeUint32(report, 8, (int) (nowMillis / 1_000L + 2_208_988_800L));
        writeUint32(report, 12, (int) (nowMillis % 1_000L * 0x1_0000_0000L / 1_000L));
        writeUint32(report, 16, lastRtpTimestamp);
        writeUint32(report, 20, (int) rtpPacketsSent);
        writeUint32(report, 24, (int) rtpOctetsSent);
        return report;
    }

    /**
     * Reads a big-endian unsigned thirty-two-bit integer from a buffer.
     *
     * @param buffer the buffer to read from
     * @param offset the offset of the integer
     * @return the integer value
     */
    private static int readUint32(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 24) | ((buffer[offset + 1] & 0xFF) << 16)
                | ((buffer[offset + 2] & 0xFF) << 8) | (buffer[offset + 3] & 0xFF);
    }

    /**
     * Writes a big-endian thirty-two-bit integer into a buffer.
     *
     * @param buffer the buffer to write to
     * @param offset the offset to write at
     * @param value  the integer value
     */
    private static void writeUint32(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        started = false;
        var thread = bringUpThread;
        if (thread != null) {
            thread.interrupt();
        }
        pendingChecks.clear();
        if (dataChannel != null) {
            try {
                dataChannel.close();
            } catch (RuntimeException _) {
            }
        }
        hbhSrtp.close();
    }

    /**
     * Returns whether this transport carries a hop-by-hop WARP message-integrity tag.
     *
     * @return {@code true} when the relay negotiated a non-zero WARP MI tag length
     */
    public boolean hasWarpMessageIntegrity() {
        return warpMiTagLength > 0;
    }

    /**
     * Returns the ICE agent driving this transport's connectivity, if one was supplied.
     *
     * @return the ICE agent, or {@code null} for a degenerate test transport that runs no bring-up
     */
    public IceAgent iceAgent() {
        return iceAgent;
    }

    /**
     * Drives the connectivity-check scheduler for one tick: retransmits an in-flight check, abandons one
     * that exhausted its retransmits, and starts a fresh check on the next unchecked pair.
     *
     * @param nowNanos the current monotonic nanosecond timestamp
     */
    private void driveConnectivityChecks(long nowNanos) {
        var expired = new ArrayList<TransactionKey>();
        for (var entry : pendingChecks.entrySet()) {
            var pending = entry.getValue();
            if (pending.pair.state() != IceCheckState.IN_PROGRESS) {
                expired.add(entry.getKey());
                continue;
            }
            if (nowNanos - pending.lastSentNanos < CHECK_RETRANSMIT_INTERVAL_NANOS) {
                continue;
            }
            if (pending.retransmits >= MAX_CHECK_RETRANSMITS) {
                pending.pair.setState(IceCheckState.FAILED);
                expired.add(entry.getKey());
                continue;
            }
            pending.retransmits++;
            pending.lastSentNanos = nowNanos;
            sendCheck(pending.pair, pending.transactionId, pending.nominate);
        }
        for (var key : expired) {
            pendingChecks.remove(key);
        }
        startNextCheck(nowNanos);
    }

    /**
     * Starts a connectivity check on the highest-priority pair still {@link IceCheckState#FROZEN} or
     * {@link IceCheckState#WAITING}, when no check is currently in flight.
     *
     * @param nowNanos the current monotonic nanosecond timestamp
     */
    private void startNextCheck(long nowNanos) {
        if (!pendingChecks.isEmpty()) {
            return;
        }
        for (var pair : iceAgent.checklist()) {
            if (pair.state() == IceCheckState.FROZEN || pair.state() == IceCheckState.WAITING) {
                var transactionId = VoipCryptoNative.randomBytes(StunMessage.TRANSACTION_ID_LENGTH);
                var nominate = iceAgent.controlling() && iceAgent.nominatedPair().isEmpty();
                pendingChecks.put(new TransactionKey(transactionId),
                        new PendingCheck(pair, transactionId, nominate, nowNanos));
                sendCheck(pair, transactionId, nominate);
                return;
            }
        }
    }

    /**
     * Builds and ships one connectivity-check binding request for a pair to its remote address.
     *
     * @param pair          the candidate pair to check
     * @param transactionId the STUN transaction id for the request
     * @param nominate      whether to add USE-CANDIDATE to nominate the pair
     */
    private void sendCheck(IceCandidatePair pair, byte[] transactionId, boolean nominate) {
        var request = iceAgent.buildBindingRequest(pair, transactionId, nominate);
        if (socketEgress.send(request, pair.remote().address()) <= 0) {
            checkSendFailures++;
        }
    }

    /**
     * Handles one inbound STUN datagram from the socket: verifies it through the ICE agent and routes it by
     * STUN message type.
     *
     * <p>A verified {@link StunMessage#TYPE_BINDING_REQUEST binding request} is the peer's connectivity
     * check toward this agent and is answered with {@link IceAgent#buildBindingResponse} sent back to the
     * request's source. A verified {@link StunMessage#TYPE_BINDING_SUCCESS_RESPONSE binding success
     * response} completes the check on the pair whose transaction id it echoes, after which a controlling
     * agent nominates it.
     *
     * @param datagram the inbound STUN bytes
     * @param source   the transport address the datagram arrived from
     */
    private void onStun(byte[] datagram, SocketAddress source) {
        if (iceAgent == null) {
            return;
        }
        inboundStunCount++;
        var parsed = iceAgent.parseInbound(datagram);
        if (parsed.isEmpty()) {
            stunVerifyFailures++;
            return;
        }
        var message = parsed.get();
        synchronized (iceLock) {
            switch (message.messageType()) {
                case StunMessage.TYPE_BINDING_REQUEST -> {
                    if (source instanceof InetSocketAddress reflexiveSource) {
                        var response = iceAgent.buildBindingResponse(message.transactionId(), reflexiveSource);
                        socketEgress.send(response, source);
                    }
                }
                case StunMessage.TYPE_BINDING_SUCCESS_RESPONSE -> {
                    var pending = pendingChecks.remove(new TransactionKey(message.transactionId()));
                    if (pending != null) {
                        iceAgent.onCheckSucceeded(pending.pair);
                        if (iceAgent.nominatedPair().isPresent()) {
                            pairNominated = true;
                        }
                    }
                }
                default -> {
                    // A verified STUN message that is neither a binding request nor a binding success
                    // response (for example a binding error response) is not part of the connectivity-check
                    // completion path and is dropped, matching the native agent which acts only on the two.
                }
            }
        }
    }

    /**
     * Feeds one inbound DTLS record into the data channel backend's DTLS transport.
     *
     * <p>The data channel owns the DTLS/SCTP bridge that decrypts the record and drives the SCTP stack, so
     * the socket demux only forwards the record bytes; the bridge's receive pump and SCTP layer turn them
     * into inbound data-channel messages.
     *
     * @param datagram the inbound DTLS record bytes
     */
    private void onDtlsRecord(byte[] datagram) {
        if (dataChannel != null) {
            dataChannel.feedDtlsRecord(datagram);
        }
    }

    /**
     * Demultiplexes one inbound SCTP DATA message from the data channel into media, RTCP, or application
     * STUN by its leading byte.
     *
     * <p>Inbound WARP control rides piggybacked on the tail of an RTP packet (leading byte in the RTP
     * range), so it is reached through the media path and not a separate leading-byte class; a standalone
     * WARP (leading byte {@value WarpMessage#WARP_TYPE}) is an outbound-only control form here and falls in
     * no inbound class.
     *
     * @param message the inbound data-channel message bytes
     */
    private void onDataChannelMessage(byte[] message) {
        if (closed) {
            return;
        }
        if (inboundChannelMsgCount++ == 0) {
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay first inbound data-channel message: class={0}, leadingByte=0x{1}, {2} bytes",
                    InboundPacketDemux.classify(message),
                    String.format("%02X", message.length == 0 ? 0 : message[0] & 0xFF),
                    message.length);
        }
        if (!firstInboundAckLogged && message.length >= 2 && (message[0] & 0xFF) == 0x01) {
            firstInboundAckLogged = true;
            var hex = new StringBuilder(message.length * 2);
            for (var b : message) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            LOGGER.log(System.Logger.Level.INFO,
                    "calls2 relay first inbound app-STUN response type=0x{0} ({1} bytes): {2}",
                    String.format("%02X%02X", message[0] & 0xFF, message[1] & 0xFF),
                    message.length, hex);
        }
        emit(TransportEvent.RX_APP_DATA);
        channelDemux.accept(message, null);
    }

    /**
     * Handles one inbound media (RTP) message carried as SCTP DATA: hop-by-hop decrypts it and feeds the
     * cleartext to the media sink.
     *
     * @param message the inbound protected media bytes
     */
    private void onInboundMedia(byte[] message) {
        if (message.length >= 12) {
            remoteMediaSsrc = readUint32(message, 8);
            remoteMediaSsrcKnown = true;
        }
        var buffer = new byte[Math.max(message.length, MAX_PACKET_SIZE)];
        System.arraycopy(message, 0, buffer, 0, message.length);
        try {
            var cleartextLength = e2eRecv != null
                    ? e2eRecv.unprotectRtp(buffer, message.length)
                    : hbhSrtp.unprotectRtp(buffer, message.length);
            mediaSink.accept(slice(buffer, cleartextLength));
        } catch (WhatsAppCallException.Srtp | IllegalArgumentException _) {
            // A packet that fails authentication or does not parse is dropped, not surfaced: per the engine
            // the leg silently discards an unverifiable media packet.
            if (inboundMediaDecryptFailures++ == 0) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "calls2 relay first inbound media (RTP) decrypt failure ({0} bytes); dropping",
                        message.length);
            }
        }
    }

    /**
     * Handles one inbound RTCP message carried as SCTP DATA: hop-by-hop decrypts it, parses its feedback,
     * and delivers it to the registered listener.
     *
     * @param message the inbound protected RTCP bytes
     */
    private void onInboundRtcpMessage(byte[] message) {
        var listener = rtcpFeedbackListener;
        if (listener == null) {
            return;
        }
        var buffer = new byte[Math.max(message.length, MAX_PACKET_SIZE)];
        System.arraycopy(message, 0, buffer, 0, message.length);
        try {
            var cleartextLength = hbhSrtp.unprotectRtcp(buffer, message.length);
            var feedback = RtcpFeedbackParser.parse(buffer, cleartextLength);
            if (inboundRtcpDiagCount++ == 0) {
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay first inbound RTCP ({0} bytes, {1} cleartext): parsed feedback={2}",
                        message.length, cleartextLength,
                        feedback.map(f -> "loss=" + f.hasLoss() + " rtt=" + f.hasRtt()
                                + " remoteBwe=" + f.hasRemoteBwe()).orElse("NONE"));
            }
            feedback.ifPresent(listener);
        } catch (WhatsAppCallException.Srtp srtpFailure) {
            // A packet that fails hop-by-hop authentication is dropped, not surfaced: per the engine the
            // leg silently discards an unverifiable RTCP packet. The libsrtp status carried in the exception
            // message distinguishes the cause on the first failure (7 auth_fail, 9/10 replay, 13 no_ctx,
            // 2 bad_param), which tells whether the inbound SRTCP key/policy or the stream lookup is wrong.
            if (inboundRtcpDiagCount++ == 0) {
                LOGGER.log(System.Logger.Level.INFO,
                        "calls2 relay first inbound RTCP ({0} bytes) FAILED hop-by-hop unprotect ({1}); dropping",
                        message.length, srtpFailure.getMessage());
                System.out.println("Test");
            }
        }
    }

    /**
     * Handles one inbound application-STUN message carried as SCTP DATA, forwarding it to the registered
     * application-STUN handler.
     *
     * <p>The application STUN on the data channel is connectivity-plane state for the keepalive and
     * subscription layers (the {@code 0x0801} ping and the {@code 0x0003} subscription envelope), not an
     * ICE connectivity check, so it is forwarded verbatim rather than parsed through the ICE agent.
     *
     * @param message the inbound application-STUN bytes
     * @param source  always {@code null} on the data-channel layer; the message did not arrive on a socket
     */
    private void onChannelStun(byte[] message, SocketAddress source) {
        var handler = appStunHandler;
        if (handler != null) {
            handler.accept(message);
        }
    }

    /**
     * Surfaces the data channel opening exactly once as {@link TransportEvent#RELAY_CREATE_SUCCESS}.
     */
    private void pollDataChannel() {
        if (dataChannelOpen || dataChannel == null || !dataChannel.isReady()) {
            return;
        }
        dataChannelOpen = true;
        emit(TransportEvent.RELAY_CREATE_SUCCESS);
    }

    /**
     * Emits a transport event to the registered listener, if any.
     *
     * @param event the event to emit
     */
    private void emit(TransportEvent event) {
        var listener = eventListener;
        if (listener != null) {
            listener.accept(event);
        }
    }

    /**
     * Returns a copy of the first {@code length} bytes of a buffer.
     *
     * @param buffer the source buffer
     * @param length the number of leading bytes to copy
     * @return a new array of the leading {@code length} bytes
     */
    private static byte[] slice(byte[] buffer, int length) {
        var out = new byte[length];
        System.arraycopy(buffer, 0, out, 0, length);
        return out;
    }

    /**
     * Validates a packet length against its buffer.
     *
     * @param length    the declared packet length
     * @param bufferLen the buffer length
     * @throws IllegalArgumentException if {@code length} is negative or exceeds {@code bufferLen}
     */
    private static void requireLength(int length, int bufferLen) {
        if (length < 0 || length > bufferLen) {
            throw new IllegalArgumentException("length " + length + " out of range for buffer " + bufferLen);
        }
    }

    /**
     * Validates that the transport is open.
     *
     * @throws IllegalStateException if the transport has been closed
     */
    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("media transport is closed");
        }
    }

    /**
     * Validates that the transport is started and the data channel is open before a send.
     *
     * @throws IllegalStateException if the transport is not started, is closed, or its data channel is not
     *                               open
     */
    private void ensureSendable() {
        ensureOpen();
        if (!started) {
            throw new IllegalStateException("media transport is not started");
        }
        if (dataChannel == null || !dataChannel.isReady()) {
            throw new IllegalStateException("media transport data channel is not open");
        }
    }

    /**
     * One in-flight connectivity check awaiting its binding success response.
     */
    private static final class PendingCheck {
        /**
         * Holds the candidate pair this check is running on.
         */
        private final IceCandidatePair pair;

        /**
         * Holds the STUN transaction id of the in-flight binding request.
         */
        private final byte[] transactionId;

        /**
         * Holds whether the binding request nominates the pair (USE-CANDIDATE).
         */
        private final boolean nominate;

        /**
         * Holds the monotonic nanosecond timestamp of the most recent binding request.
         */
        private long lastSentNanos;

        /**
         * Holds the number of retransmissions sent so far.
         */
        private int retransmits;

        /**
         * Constructs an in-flight check record.
         *
         * @param pair          the candidate pair being checked
         * @param transactionId the binding-request transaction id
         * @param nominate      whether the request nominates the pair
         * @param sentNanos     the timestamp of the first request
         */
        private PendingCheck(IceCandidatePair pair, byte[] transactionId, boolean nominate, long sentNanos) {
            this.pair = pair;
            this.transactionId = transactionId;
            this.nominate = nominate;
            this.lastSentNanos = sentNanos;
            this.retransmits = 0;
        }
    }

    /**
     * A hashable key wrapping a STUN transaction id so a binding success response can be matched back to
     * its in-flight check.
     *
     * @param transactionId the twelve-byte STUN transaction id
     */
    private record TransactionKey(byte[] transactionId) {
        @Override
        public boolean equals(Object obj) {
            return obj == this
                    || (obj instanceof TransactionKey that && java.util.Arrays.equals(transactionId, that.transactionId));
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(transactionId);
        }
    }
}
