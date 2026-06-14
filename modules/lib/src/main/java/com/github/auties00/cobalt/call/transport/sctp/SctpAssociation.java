package com.github.auties00.cobalt.call.transport.sctp;

import com.github.auties00.cobalt.call.transport.sctp.bindings.UsrSctp;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_event;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_initmsg;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_prinfo;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_rcvinfo;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_sendv_spa;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_sndinfo;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sockaddr_conn;
import com.github.auties00.cobalt.call.transport.sctp.bindings.usrsctp_socket$receive_cb;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Wraps one usrsctp socket bound to one peer, modelling the SCTP half of a single WebRTC DataChannel association.
 *
 * <p>SCTP for WebRTC (RFC 8261, RFC 8831) runs over DTLS rather than over IP: usrsctp implements the SCTP state machine
 * but never touches a real socket. Outbound SCTP packets this stack produces are routed through the {@code outboundSink}
 * {@link Consumer} that the caller wires to the underlying DTLS transport, and inbound DTLS payloads are pushed back in
 * through {@link #feedInboundPacket(byte[])}, which drives usrsctp's decoder and may synchronously deliver a decoded
 * application message to {@link InboundListener#onMessage(InboundMessage)}.
 *
 * <p>The class is {@link AutoCloseable}; teardown order is deregister, then close socket, then close arena. Higher-level
 * DataChannel framing (DCEP control messages, the PPID-tagged stream protocol of RFC 8831) lives in the DataChannel
 * transport layered on top of this class, not here.
 *
 * <p>The threading model follows Cobalt's standard virtual-thread, direct-blocking pattern: a single owning virtual
 * thread calls {@link #feedInboundPacket(byte[])} for inbound DTLS records and {@link #send(int, int, byte[], boolean)}
 * for outbound DataChannel messages. The {@code outboundSink} {@link Consumer} may be invoked from that same thread
 * (synchronously inside {@link #send(int, int, byte[], boolean)}) or from the engine's tick driver thread, so
 * implementations must be thread-safe.
 *
 * @implNote This implementation pre-applies the WebRTC defaults at construction: {@code SCTP_NODELAY},
 * {@code SCTP_DISABLE_FRAGMENTS}, and an {@code SCTP_INITMSG} requesting 1024 inbound and 1024 outbound streams as
 * recommended by RFC 8831. Teardown follows the order usrsctp documents so that any in-flight conn_output callbacks
 * drain before the conn id segment backing them is freed.
 */
public final class SctpAssociation implements AutoCloseable {
    /**
     * Holds the RFC 8831 default count of inbound and outbound SCTP streams for a WebRTC DataChannel association.
     *
     * @implNote This implementation requests 1024 streams in each direction via {@code SCTP_INITMSG}, the value RFC 8831
     * specifies for WebRTC DataChannels.
     */
    private static final int WEBRTC_NUM_STREAMS = 1024;

    /**
     * Holds the per-message unordered-delivery flag for the {@code snd_flags} field of {@code sctp_sndinfo}.
     *
     * @implNote This implementation inlines the constant from RFC 4960 section 5.4 / usrsctp.h ({@code 0x0400}) because
     * jextract drops it from the {@code UsrSctp} bindings; the macro group it belongs to is not parseable as a constant
     * expression.
     */
    private static final short SCTP_UNORDERED = 0x0400;

    /**
     * Holds the {@code sac_state} value reported when the SCTP association handshake has completed and the socket is
     * ready for application data.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0001}.
     */
    private static final int SCTP_COMM_UP = 0x0001;

    /**
     * Holds the {@code sac_state} value reported when the association has been torn down by the peer or by a fault.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0002}.
     */
    private static final int SCTP_COMM_LOST = 0x0002;

    /**
     * Holds the {@code sac_state} value reported when a graceful shutdown has completed.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0004}.
     */
    private static final int SCTP_SHUTDOWN_COMP = 0x0004;

    /**
     * Holds the {@code sac_state} value reported when the association could not be started, such as when the peer is
     * unreachable or the INIT was rejected.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0005}.
     */
    private static final int SCTP_CANT_STR_ASSOC = 0x0005;

    /**
     * Holds the byte offset of the {@code sac_state} field within a {@code sctp_assoc_change} notification.
     *
     * @implNote This implementation reads {@code sac_state} at offset {@code 8}: the notification layout is
     * {@code uint16_t sac_type; uint16_t sac_flags; uint32_t sac_length; uint16_t sac_state; ...}, so the state word
     * follows the two 16-bit fields and the 32-bit length.
     */
    private static final int SAC_STATE_OFFSET = 8;

    /**
     * Holds the per-association arena that owns the {@code conn_id} segment, the receive-callback upcall stub, and the
     * scratch buffers reused across calls.
     */
    private final Arena arena;

    /**
     * Holds the engine this association registered with, so {@link #close()} can deregister cleanly.
     */
    private final SctpEngine engine;

    /**
     * Holds the unique opaque pointer this association registered with usrsctp to route outbound packets.
     *
     * <p>The address, not the contents, is what matters; usrsctp treats the pointer as a routing key.
     */
    private final MemorySegment connId;

    /**
     * Holds the pointer to the {@code struct socket *} returned by {@code usrsctp_socket}, nulled on {@link #close()}.
     */
    private MemorySegment socket;

    /**
     * Holds the receive-callback upcall stub whose lifetime is bounded by {@link #arena}.
     */
    private final MemorySegment receiveCbStub;

    /**
     * Holds the sink for outbound SCTP packets, which the caller wires to its DTLS transport's send method.
     */
    private final Consumer<byte[]> outboundSink;

    /**
     * Holds the listener invoked when usrsctp delivers an inbound application message, typed via {@link InboundMessage}.
     *
     * <p>Notification messages such as association-state changes are handled internally by
     * {@link #handleNotification(MemorySegment, long)} rather than delivered to this listener.
     */
    private final InboundListener inboundListener;

    /**
     * Holds the reusable scratch segment for the {@code sctp_sndinfo} struct passed to {@code usrsctp_sendv}.
     */
    private final MemorySegment sndInfoScratch;

    /**
     * Scratch buffer for {@code sctp_sendv_spa} used by
     * {@link #sendWithPolicy(int, int, byte[], boolean, short, int)}. Reused across calls;
     * lazily-allocated on the first partial-reliability send so fully-reliable sends do not pay
     * the allocation cost.
     */
    private volatile MemorySegment spaScratch;

    /**
     * Holds the reusable scratch segment for the local-side {@code sockaddr_conn} struct passed to
     * {@code usrsctp_bind}.
     */
    private final MemorySegment localAddr;

    /**
     * Holds the reusable scratch segment for the remote-side {@code sockaddr_conn} struct passed to
     * {@code usrsctp_connect}.
     */
    private final MemorySegment remoteAddr;

    /**
     * Holds the latch that fires when the association handshake reaches a terminal state.
     *
     * <p>The latch counts down exactly once, when usrsctp delivers an {@code SCTP_ASSOC_CHANGE} notification carrying
     * {@code SCTP_COMM_UP} (handshake complete) or any of the failure states. The blocking
     * {@link #connect(int, long, TimeUnit)} call waits on it.
     */
    private final CountDownLatch connectLatch = new CountDownLatch(1);

    /**
     * Holds whether the handshake completed successfully.
     *
     * <p>Set to {@code true} when the {@code SCTP_ASSOC_CHANGE} notification arrives with {@code sac_state} equal to
     * {@code SCTP_COMM_UP}; {@link #connect(int, long, TimeUnit)} inspects it after the latch fires to distinguish
     * success from a terminal failure state.
     */
    private final AtomicBoolean connected = new AtomicBoolean();

    /**
     * Receives one application-data message decoded from the SCTP association.
     *
     * <p>The single callback fires synchronously from {@link #feedInboundPacket(byte[])} when usrsctp decodes a DATA
     * chunk into a complete message.
     */
    @FunctionalInterface
    public interface InboundListener {
        /**
         * Receives one application-data message from the SCTP association.
         *
         * @param msg the inbound message
         */
        void onMessage(InboundMessage msg);
    }

    /**
     * Carries one decoded application-data message delivered to {@link InboundListener}.
     *
     * @param streamId the SCTP stream the message arrived on
     * @param ppid     the SCTP Payload Protocol Identifier (for example the WebRTC DataChannel-binary PPID 53)
     * @param payload  the raw payload bytes
     * @param flags    the raw {@code msg_flags} from {@code usrsctp_recvv}
     */
    public record InboundMessage(int streamId, int ppid, byte[] payload, int flags) {
        /**
         * Validates that the payload is non-null.
         *
         * @throws NullPointerException if {@code payload} is {@code null}
         */
        public InboundMessage {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs and configures a new association.
     *
     * <p>The constructor allocates a per-association shared arena, allocates a unique one-byte segment to serve as the
     * {@code conn_id}, registers that conn id with the {@link SctpEngine} so the global outbound upcall can route to
     * this instance, builds the receive-callback upcall stub, creates the usrsctp socket via
     * {@code usrsctp_socket(AF_CONN, SOCK_STREAM, IPPROTO_SCTP, recv_cb, NULL, 0, conn_id)}, and applies the WebRTC
     * defaults ({@code SCTP_NODELAY}, {@code SCTP_DISABLE_FRAGMENTS}, {@code SCTP_INITMSG}), non-blocking mode, and the
     * {@code SCTP_ASSOC_CHANGE} event subscription. On any failure it deregisters the conn id and closes the arena
     * before rethrowing, so no native resources leak.
     *
     * @param outboundSink    the sink for SCTP packets the local stack wants to send out the wire, typically wired to
     *                        the DTLS transport's send method; must be thread-safe
     * @param inboundListener the listener invoked synchronously from {@link #feedInboundPacket(byte[])} when usrsctp
     *                        decodes a DATA chunk from the inbound SCTP packet
     * @throws NullPointerException       if any argument is {@code null}
     * @throws WhatsAppCallException.Sctp if the socket cannot be created or configured
     */
    public SctpAssociation(Consumer<byte[]> outboundSink, InboundListener inboundListener) {
        this.outboundSink = Objects.requireNonNull(outboundSink, "outboundSink cannot be null");
        this.inboundListener = Objects.requireNonNull(inboundListener, "inboundListener cannot be null");
        this.engine = SctpEngine.instance();
        this.arena = Arena.ofShared();
        MemorySegment registeredConnId = null;
        try {
            this.connId = arena.allocate(1);
            this.sndInfoScratch = sctp_sndinfo.allocate(arena);
            this.localAddr = sockaddr_conn.allocate(arena);
            this.remoteAddr = sockaddr_conn.allocate(arena);
            this.receiveCbStub = usrsctp_socket$receive_cb.allocate(this::onReceive, arena);
            engine.register(connId, this);
            registeredConnId = connId;
            try {
                this.socket = UsrSctp.usrsctp_socket(
                        UsrSctp.AF_CONN(),
                        UsrSctp.SOCK_STREAM(),
                        UsrSctp.IPPROTO_SCTP(),
                        receiveCbStub,
                        MemorySegment.NULL,
                        0,
                        connId);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_socket failed", t);
            }
            if (socket.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.Sctp("usrsctp_socket returned NULL");
            }
            applyWebRtcDefaults();
            applyNonBlocking();
            subscribeAssocChangeNotifications();
        } catch (RuntimeException e) {
            if (registeredConnId != null) {
                engine.unregister(registeredConnId);
            }
            arena.close();
            throw e;
        }
    }

    /**
     * Binds the association to a local SCTP port.
     *
     * <p>Must be called before {@link #connect(int)}. RFC 8831 specifies port 5000 for WebRTC DataChannels.
     *
     * @param localPort the local SCTP port
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_bind} fails
     */
    public void bind(int localPort) {
        requireOpen();
        sockaddr_conn.sconn_family(localAddr, (short) UsrSctp.AF_CONN());
        sockaddr_conn.sconn_port(localAddr, htons(localPort));
        sockaddr_conn.sconn_addr(localAddr, connId);
        int rc;
        try {
            rc = UsrSctp.usrsctp_bind(socket, localAddr, (int) sockaddr_conn.layout().byteSize());
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_bind failed", t);
        }
        if (rc < 0) {
            throw new WhatsAppCallException.Sctp("usrsctp_bind returned " + rc);
        }
    }

    /**
     * Performs the SCTP handshake with the peer and blocks until it completes, fails, or the calling thread is
     * interrupted.
     *
     * <p>Equivalent to {@link #connect(int, long, TimeUnit)} with an effectively unbounded timeout. For WebRTC's
     * simultaneous-open pattern (RFC 4960 section 5.2.4 INIT collision), both peers call this concurrently, typically
     * each on its own thread, so that each side's INIT chunk reaches the other after both sockets have entered
     * {@code COOKIE_WAIT}.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_connect} reports a hard failure or the calling thread is
     *                                    interrupted while waiting
     */
    public void connect(int remotePort) {
        connect(remotePort, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    /**
     * Performs the SCTP handshake and blocks until it reaches a terminal state or the timeout expires.
     *
     * <p>The handshake completes successfully when an {@code SCTP_ASSOC_CHANGE} notification reports
     * {@code SCTP_COMM_UP}, and fails when it reports {@code SCTP_COMM_LOST}, {@code SCTP_SHUTDOWN_COMP}, or
     * {@code SCTP_CANT_STR_ASSOC}. Because the socket is non-blocking, {@code usrsctp_connect} returns {@code -1} with
     * {@code EINPROGRESS} for the success case; this is treated as the start of the handshake, with completion
     * signalled through the notification rather than the return value.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @param timeout    the maximum time to wait
     * @param unit       the timeout unit
     * @throws NullPointerException       if {@code unit} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_connect} reports a hard failure, the handshake completes with
     *                                    a non-{@code COMM_UP} state, the wait times out, or the calling thread is
     *                                    interrupted
     */
    public void connect(int remotePort, long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        requireOpen();
        sockaddr_conn.sconn_family(remoteAddr, (short) UsrSctp.AF_CONN());
        sockaddr_conn.sconn_port(remoteAddr, htons(remotePort));
        sockaddr_conn.sconn_addr(remoteAddr, connId);
        int rc;
        try {
            rc = UsrSctp.usrsctp_connect(socket, remoteAddr, (int) sockaddr_conn.layout().byteSize());
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_connect failed", t);
        }
        if (rc != 0 && rc != -1) {
            throw new WhatsAppCallException.Sctp("usrsctp_connect returned " + rc);
        }
        boolean signaled;
        try {
            signaled = connectLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppCallException.Sctp("interrupted while waiting for SCTP_COMM_UP", e);
        }
        if (!signaled) {
            throw new WhatsAppCallException.Sctp("SCTP handshake timed out after " + timeout + " " + unit);
        }
        if (!connected.get()) {
            throw new WhatsAppCallException.Sctp("SCTP handshake failed (COMM_LOST/SHUTDOWN/CANT_STR_ASSOC)");
        }
    }

    /**
     * Feeds one inbound SCTP packet, decoded from a DTLS record, into the local SCTP stack.
     *
     * <p>The packet is copied into native memory and handed to {@code usrsctp_conninput}, which drives the SCTP state
     * machine and may produce zero or one synchronous calls into {@link InboundListener#onMessage(InboundMessage)} on
     * the calling thread. An empty packet is ignored.
     *
     * @param packet the SCTP packet bytes
     * @throws NullPointerException       if {@code packet} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_conninput} fails
     */
    public void feedInboundPacket(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        requireOpen();
        if (packet.length == 0) {
            return;
        }
        try (var scratch = Arena.ofConfined()) {
            var seg = scratch.allocate(packet.length);
            MemorySegment.copy(packet, 0, seg, ValueLayout.JAVA_BYTE, 0, packet.length);
            try {
                UsrSctp.usrsctp_conninput(connId, seg, packet.length, (byte) 0);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_conninput failed", t);
            }
        }
    }

    /**
     * Sends one application-data message on a specific SCTP stream with the given Payload Protocol Identifier.
     *
     * <p>The message is queued through {@code usrsctp_sendv}; usrsctp then emits one or more SCTP packets through the
     * conn_output upcall, which arrive at the caller's {@code outboundSink}. The call fails if usrsctp accepts fewer
     * bytes than the payload length, since the WebRTC defaults disable fragmentation.
     *
     * @param streamId the SCTP stream index (0 .. 1023)
     * @param ppid     the SCTP PPID (WebRTC uses 50, 51, 53, 56, and 57)
     * @param payload  the message bytes
     * @param ordered  {@code true} for ordered delivery, {@code false} for unordered
     * @throws NullPointerException       if {@code payload} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_sendv} fails or sends fewer bytes than requested
     */
    public void send(int streamId, int ppid, byte[] payload, boolean ordered) {
        Objects.requireNonNull(payload, "payload cannot be null");
        requireOpen();
        sctp_sndinfo.snd_sid(sndInfoScratch, (short) streamId);
        sctp_sndinfo.snd_flags(sndInfoScratch, ordered ? (short) 0 : SCTP_UNORDERED);
        sctp_sndinfo.snd_ppid(sndInfoScratch, htonl(ppid));
        sctp_sndinfo.snd_context(sndInfoScratch, 0);
        sctp_sndinfo.snd_assoc_id(sndInfoScratch, 0);
        try (var scratch = Arena.ofConfined()) {
            MemorySegment data;
            if (payload.length == 0) {
                data = MemorySegment.NULL;
            } else {
                data = scratch.allocate(payload.length);
                MemorySegment.copy(payload, 0, data, ValueLayout.JAVA_BYTE, 0, payload.length);
            }
            long sent;
            try {
                sent = UsrSctp.usrsctp_sendv(
                        socket,
                        data,
                        payload.length,
                        MemorySegment.NULL,
                        0,
                        sndInfoScratch,
                        (int) sctp_sndinfo.layout().byteSize(),
                        UsrSctp.SCTP_SENDV_SNDINFO(),
                        0);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv failed", t);
            }
            if (sent < 0) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv returned " + sent);
            }
            if (sent != payload.length) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv short write: " + sent + " of " + payload.length);
            }
        }
    }

    /**
     * Sends a message under a partial-reliability policy.
     *
     * <p>Routes through {@code usrsctp_sendv} with a {@link sctp_sendv_spa} container whose
     * {@code sendv_flags} carry both {@link UsrSctp#SCTP_SEND_SNDINFO_VALID() SCTP_SEND_SNDINFO_VALID}
     * and {@link UsrSctp#SCTP_SEND_PRINFO_VALID() SCTP_SEND_PRINFO_VALID}, so the message is delivered
     * on the chosen stream with the requested PR policy applied by usrsctp's outbound scheduler.
     *
     * @param streamId the SCTP stream index (0 .. 1023)
     * @param ppid     the SCTP PPID
     * @param payload  the message bytes
     * @param ordered  {@code true} for ordered delivery, {@code false} for unordered
     * @param prPolicy one of {@link UsrSctp#SCTP_PR_SCTP_NONE()},
     *                 {@link UsrSctp#SCTP_PR_SCTP_TTL()}, or
     *                 {@link UsrSctp#SCTP_PR_SCTP_RTX()}
     * @param prValue  the policy operand: max retransmissions for {@code SCTP_PR_SCTP_RTX},
     *                 lifetime milliseconds for {@code SCTP_PR_SCTP_TTL}; ignored for
     *                 {@code SCTP_PR_SCTP_NONE}
     * @throws NullPointerException       if {@code payload} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_sendv} fails or sends fewer bytes than requested
     */
    public void sendWithPolicy(int streamId, int ppid, byte[] payload, boolean ordered,
                               short prPolicy, int prValue) {
        Objects.requireNonNull(payload, "payload cannot be null");
        requireOpen();
        var spa = spaScratch;
        if (spa == null) {
            synchronized (this) {
                spa = spaScratch;
                if (spa == null) {
                    spa = sctp_sendv_spa.allocate(arena);
                    spaScratch = spa;
                }
            }
        }
        sctp_sendv_spa.sendv_flags(spa,
                UsrSctp.SCTP_SEND_SNDINFO_VALID() | UsrSctp.SCTP_SEND_PRINFO_VALID());
        var sndInfo = sctp_sendv_spa.sendv_sndinfo(spa);
        sctp_sndinfo.snd_sid(sndInfo, (short) streamId);
        sctp_sndinfo.snd_flags(sndInfo, ordered ? (short) 0 : SCTP_UNORDERED);
        sctp_sndinfo.snd_ppid(sndInfo, htonl(ppid));
        sctp_sndinfo.snd_context(sndInfo, 0);
        sctp_sndinfo.snd_assoc_id(sndInfo, 0);
        var prInfo = sctp_sendv_spa.sendv_prinfo(spa);
        sctp_prinfo.pr_policy(prInfo, prPolicy);
        sctp_prinfo.pr_value(prInfo, prValue);
        try (var scratch = Arena.ofConfined()) {
            MemorySegment data;
            if (payload.length == 0) {
                data = MemorySegment.NULL;
            } else {
                data = scratch.allocate(payload.length);
                MemorySegment.copy(payload, 0, data, ValueLayout.JAVA_BYTE, 0, payload.length);
            }
            long sent;
            try {
                sent = UsrSctp.usrsctp_sendv(
                        socket,
                        data,
                        payload.length,
                        MemorySegment.NULL,
                        0,
                        spa,
                        (int) sctp_sendv_spa.layout().byteSize(),
                        UsrSctp.SCTP_SENDV_SPA(),
                        0);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv (SPA) failed", t);
            }
            if (sent < 0) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv (SPA) returned " + sent);
            }
            if (sent != payload.length) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv (SPA) short write: " + sent + " of " + payload.length);
            }
        }
    }

    /**
     * Forwards an outbound SCTP packet from usrsctp's conn_output upcall to the caller's {@code outboundSink}.
     *
     * <p>Visible to {@link SctpEngine} only; the engine resolves the firing conn id back to this association and invokes
     * this method.
     *
     * @param packet the packet bytes to ship to the peer
     */
    void deliverOutbound(byte[] packet) {
        outboundSink.accept(packet);
    }

    /**
     * Dispatches one usrsctp receive-callback firing, the entry point usrsctp invokes synchronously inside
     * {@link #feedInboundPacket(byte[])} once an inbound DATA chunk has decoded into a complete message.
     *
     * <p>Notification messages are routed to {@link #handleNotification(MemorySegment, long)}; null or empty data is
     * ignored; otherwise the native payload is copied into a fresh {@code byte[]}, the stream id and PPID are read from
     * the receive-info struct (the PPID converted from network to host byte order), and the result is delivered to the
     * caller's {@link InboundListener}. Any exception thrown by the listener is swallowed so a single bad message
     * cannot corrupt usrsctp's call stack.
     *
     * @param sock    the firing socket pointer (unused, since there is only one)
     * @param addr    the peer's {@code sockaddr_conn} by value (unused for {@code AF_CONN}, where the conn id alone
     *                identifies the peer)
     * @param data    the pointer to the native payload buffer, which usrsctp frees after the callback returns
     * @param datalen the length in bytes of the native payload
     * @param rcvinfo the receive-metadata struct carrying stream, PPID, and flags
     * @param flags   the raw {@code MSG_*} flags
     * @param ulpInfo the ulp_info pointer the socket was created with (always this association's {@link #connId})
     * @return {@code 0} to signal success; a non-zero return is treated by usrsctp as a request to drop the message
     */
    private int onReceive(MemorySegment sock, MemorySegment addr, MemorySegment data, long datalen,
                          MemorySegment rcvinfo, int flags, MemorySegment ulpInfo) {
        if ((flags & UsrSctp.MSG_NOTIFICATION()) != 0) {
            handleNotification(data, datalen);
            return 0;
        }
        if (datalen <= 0 || data.equals(MemorySegment.NULL)) {
            return 0;
        }
        try {
            var payload = data.reinterpret(datalen).toArray(ValueLayout.JAVA_BYTE);
            var streamId = Short.toUnsignedInt(sctp_rcvinfo.rcv_sid(rcvinfo));
            var ppid = ntohl(sctp_rcvinfo.rcv_ppid(rcvinfo));
            inboundListener.onMessage(new InboundMessage(streamId, ppid, payload, flags));
        } catch (Throwable _) {
        }
        return 0;
    }

    /**
     * Decodes an SCTP notification to surface association state changes.
     *
     * <p>Only {@code SCTP_ASSOC_CHANGE} notifications are acted on. Their {@code sac_state} maps to the
     * {@code SCTP_COMM_UP}, {@code SCTP_COMM_LOST}, {@code SCTP_SHUTDOWN_COMP}, or {@code SCTP_CANT_STR_ASSOC} states;
     * {@code SCTP_COMM_UP} marks success and any of the others marks a terminal failure, and reaching any of them fires
     * {@link #connectLatch} so the blocking {@link #connect(int, long, TimeUnit)} call can return. Notifications shorter
     * than the {@code sac_state} field, null data, and non-assoc-change types are ignored. Notification fields are read
     * in host byte order rather than network byte order.
     *
     * @param data    the pointer to the notification payload, a {@code union sctp_notification}
     * @param datalen the length of the payload in bytes
     */
    private void handleNotification(MemorySegment data, long datalen) {
        if (datalen < SAC_STATE_OFFSET + Short.BYTES || data.equals(MemorySegment.NULL)) {
            return;
        }
        var view = data.reinterpret(datalen);
        var snType = Short.toUnsignedInt(view.get(ValueLayout.JAVA_SHORT, 0));
        if (snType != UsrSctp.SCTP_ASSOC_CHANGE()) {
            return;
        }
        var sacState = Short.toUnsignedInt(view.get(ValueLayout.JAVA_SHORT, SAC_STATE_OFFSET));
        switch (sacState) {
            case SCTP_COMM_UP -> {
                connected.set(true);
                connectLatch.countDown();
            }
            case SCTP_COMM_LOST, SCTP_SHUTDOWN_COMP, SCTP_CANT_STR_ASSOC -> connectLatch.countDown();
            default -> {
            }
        }
    }

    /**
     * Applies the standard WebRTC SCTP socket configuration to the freshly created socket.
     *
     * <p>Enables {@code SCTP_NODELAY} and {@code SCTP_DISABLE_FRAGMENTS}, then sets {@code SCTP_INITMSG} requesting
     * {@link #WEBRTC_NUM_STREAMS} streams in each direction with the INIT attempt and timeout left at the usrsctp
     * defaults.
     *
     * @throws WhatsAppCallException.Sctp if any of the {@code usrsctp_setsockopt} calls fails
     */
    private void applyWebRtcDefaults() {
        setIntSockopt(UsrSctp.SCTP_NODELAY(), 1);
        setIntSockopt(UsrSctp.SCTP_DISABLE_FRAGMENTS(), 1);
        try (var scratch = Arena.ofConfined()) {
            var initMsg = sctp_initmsg.allocate(scratch);
            sctp_initmsg.sinit_num_ostreams(initMsg, (short) WEBRTC_NUM_STREAMS);
            sctp_initmsg.sinit_max_instreams(initMsg, (short) WEBRTC_NUM_STREAMS);
            sctp_initmsg.sinit_max_attempts(initMsg, (short) 0);
            sctp_initmsg.sinit_max_init_timeo(initMsg, (short) 0);
            int rc;
            try {
                rc = UsrSctp.usrsctp_setsockopt(
                        socket,
                        UsrSctp.IPPROTO_SCTP(),
                        UsrSctp.SCTP_INITMSG(),
                        initMsg,
                        (int) sctp_initmsg.layout().byteSize());
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_INITMSG failed", t);
            }
            if (rc < 0) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_INITMSG returned " + rc);
            }
        }
    }

    /**
     * Switches the socket into non-blocking mode.
     *
     * @implNote This implementation requires non-blocking mode so that the single-threaded usrsctp lock model used by
     * {@code usrsctp_init_nothreads} does not deadlock when both peers call {@link #connect(int)} simultaneously, which
     * is the WebRTC simultaneous-open pattern of RFC 4960 section 5.2.4.
     *
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_set_non_blocking} fails
     */
    private void applyNonBlocking() {
        int rc;
        try {
            rc = UsrSctp.usrsctp_set_non_blocking(socket, 1);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_set_non_blocking failed", t);
        }
        if (rc < 0) {
            throw new WhatsAppCallException.Sctp("usrsctp_set_non_blocking returned " + rc);
        }
    }

    /**
     * Subscribes the socket to {@code SCTP_ASSOC_CHANGE} notifications via the {@code SCTP_EVENT} socket option.
     *
     * <p>The subscribed notification fires when the handshake reaches {@code SCTP_COMM_UP} or terminates, which is what
     * drives {@link #handleNotification(MemorySegment, long)} and ultimately unblocks
     * {@link #connect(int, long, TimeUnit)}.
     *
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_setsockopt} fails
     */
    private void subscribeAssocChangeNotifications() {
        try (var scratch = Arena.ofConfined()) {
            var ev = sctp_event.allocate(scratch);
            sctp_event.se_assoc_id(ev, 0);
            sctp_event.se_type(ev, (short) UsrSctp.SCTP_ASSOC_CHANGE());
            sctp_event.se_on(ev, (byte) 1);
            int rc;
            try {
                rc = UsrSctp.usrsctp_setsockopt(
                        socket,
                        UsrSctp.IPPROTO_SCTP(),
                        UsrSctp.SCTP_EVENT(),
                        ev,
                        (int) sctp_event.layout().byteSize());
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_EVENT failed", t);
            }
            if (rc < 0) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_EVENT returned " + rc);
            }
        }
    }

    /**
     * Sets a single integer-valued SCTP-level socket option.
     *
     * @param optionName the {@code SCTP_*} option code
     * @param value      the integer payload
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_setsockopt} fails
     */
    private void setIntSockopt(int optionName, int value) {
        try (var scratch = Arena.ofConfined()) {
            var holder = scratch.allocate(ValueLayout.JAVA_INT);
            holder.set(ValueLayout.JAVA_INT, 0, value);
            int rc;
            try {
                rc = UsrSctp.usrsctp_setsockopt(
                        socket,
                        UsrSctp.IPPROTO_SCTP(),
                        optionName,
                        holder,
                        (int) ValueLayout.JAVA_INT.byteSize());
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt option=" + optionName + " failed", t);
            }
            if (rc < 0) {
                throw new WhatsAppCallException.Sctp("usrsctp_setsockopt option=" + optionName + " returned " + rc);
            }
        }
    }

    /**
     * Verifies that the socket is still open.
     *
     * @throws IllegalStateException if the socket has been closed
     */
    private void requireOpen() {
        if (socket == null || socket.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("SctpAssociation is closed");
        }
    }

    /**
     * Tears down the association, releasing the usrsctp socket and the native memory backing it.
     *
     * <p>The method is idempotent: a second call returns immediately. The teardown order is fixed by usrsctp's
     * contract: {@code usrsctp_deregister_address} first, to drain any in-flight conn_output upcalls routed to this
     * conn id; then {@code usrsctp_close} to destroy the socket; then {@link Arena#close()} to free the conn id segment,
     * the upcall stub, and all scratch buffers. The {@code usrsctp_close} call cannot raise, so the arena is always
     * closed even if it throws.
     */
    @Override
    public void close() {
        if (socket == null || socket.equals(MemorySegment.NULL)) {
            return;
        }
        engine.unregister(connId);
        try {
            UsrSctp.usrsctp_close(socket);
        } catch (Throwable _) {
        } finally {
            socket = MemorySegment.NULL;
            arena.close();
        }
    }

    /**
     * Converts a 16-bit value from host byte order to network byte order.
     *
     * <p>Applied when populating the {@code sconn_port} field of a {@code sockaddr_conn}, which the BSD socket API
     * requires in network byte order.
     *
     * @param value the host-order short
     * @return the network-order short
     */
    private static short htons(int value) {
        return Short.reverseBytes((short) value);
    }

    /**
     * Converts a 32-bit value from host byte order to network byte order.
     *
     * <p>Applied to the SCTP PPID written into the {@code snd_ppid} field of {@code sctp_sndinfo}.
     *
     * @param value the host-order int
     * @return the network-order int
     */
    private static int htonl(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Converts a 32-bit value from network byte order to host byte order, the inverse of {@link #htonl(int)}.
     *
     * @param value the network-order int
     * @return the host-order int
     */
    private static int ntohl(int value) {
        return Integer.reverseBytes(value);
    }
}
