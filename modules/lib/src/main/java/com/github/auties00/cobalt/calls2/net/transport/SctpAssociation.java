package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.calls2.net.transport.sctp.bindings.CobaltSctp;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Wraps one usrsctp socket bound to one peer, modelling the SCTP half of a single WebRTC DataChannel
 * association on the Web-P2P call path.
 *
 * <p>SCTP for WebRTC (RFC 8261, RFC 8831) runs over DTLS rather than over IP: usrsctp implements the SCTP
 * state machine but never touches a real socket. Outbound SCTP packets this stack produces are routed
 * through the {@code outboundSink} {@link Consumer} the caller wires to the underlying transport (the
 * {@link SctpDtlsBridge} that DTLS-encrypts and envelopes them), and inbound payloads are pushed back in
 * through {@link #feedInboundPacket(byte[])}, which drives usrsctp's decoder and may synchronously deliver
 * a decoded application message to {@link InboundListener#onMessage(InboundMessage)}.
 *
 * <p>The class is {@link AutoCloseable}; teardown order is deregister, then close socket, then close
 * arena. Higher-level DataChannel framing (the {@link DcepMessage} control messages and the PPID-tagged
 * stream protocol of RFC 8831) lives in the layer above this class, not here.
 *
 * <p>The threading model follows Cobalt's standard virtual-thread, direct-blocking pattern: a single
 * owning virtual thread calls {@link #feedInboundPacket(byte[])} for inbound records and
 * {@link #send(int, int, byte[], boolean)} for outbound DataChannel messages. The {@code outboundSink}
 * {@link Consumer} may be invoked from that same thread (synchronously inside
 * {@link #send(int, int, byte[], boolean)}) or from the engine's tick driver thread, so implementations
 * must be thread-safe.
 *
 * @implNote This implementation drives usrsctp through the portable {@link CobaltSctp} shim, which builds
 * the sockaddr_conn, sctp_sndinfo, sctp_sendv_spa, sctp_initmsg and sctp_event structs C-side and absorbs
 * the by-value union/struct arguments of usrsctp's receive callback behind a C trampoline, so no native
 * struct layout crosses into Java. It pre-applies the WebRTC defaults at construction: {@code SCTP_NODELAY},
 * {@code SCTP_DISABLE_FRAGMENTS}, and an {@code SCTP_INITMSG} requesting {@value #WEBRTC_NUM_STREAMS}
 * inbound and outbound streams as recommended by RFC 8831. The socket is created with {@code AF_CONN} so
 * usrsctp routes its output through the engine's conn-output callback rather than a kernel socket.
 * Teardown follows the order usrsctp documents so that any in-flight conn-output callbacks drain before
 * the conn id segment backing them is freed. It reproduces the data-channel association the WhatsApp Web
 * build offloads to a host-provided SCTP stack ({@code transport/data_channel} in the wa-voip WASM module
 * {@code ff-tScznZ8P}); Cobalt binds usrsctp directly instead.
 */
public final class SctpAssociation implements AutoCloseable {
    /**
     * Holds the RFC 8831 default count of inbound and outbound SCTP streams for a WebRTC DataChannel
     * association.
     *
     * @implNote This implementation requests 1024 streams in each direction via {@code SCTP_INITMSG}, the
     * value RFC 8831 specifies for WebRTC DataChannels.
     */
    private static final int WEBRTC_NUM_STREAMS = 1024;

    /**
     * Holds the {@code sn_type} value of an {@code SCTP_ASSOC_CHANGE} notification.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1 value {@code 0x0001}, read from the
     * notification wire bytes; the shim does not surface it as a constant because it is a stable protocol
     * value rather than an ABI-dependent one.
     */
    private static final int SCTP_ASSOC_CHANGE = 0x0001;

    /**
     * Holds the {@code sac_state} value reported when the SCTP association handshake has completed and the
     * socket is ready for application data.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0001}.
     */
    private static final int SCTP_COMM_UP = 0x0001;

    /**
     * Holds the {@code sac_state} value reported when the association has been torn down by the peer or by
     * a fault.
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
     * Holds the {@code sac_state} value reported when the association could not be started, such as when
     * the peer is unreachable or the INIT was rejected.
     *
     * @implNote This implementation uses the RFC 6458 section 6.1.1 value {@code 0x0005}.
     */
    private static final int SCTP_CANT_STR_ASSOC = 0x0005;

    /**
     * Holds the byte offset of the {@code sac_state} field within a {@code sctp_assoc_change} notification.
     *
     * @implNote This implementation reads {@code sac_state} at offset {@code 8}: the notification layout is
     * {@code uint16_t sac_type; uint16_t sac_flags; uint32_t sac_length; uint16_t sac_state; ...}, so the
     * state word follows the two 16-bit fields and the 32-bit length.
     */
    private static final int SAC_STATE_OFFSET = 8;

    /**
     * Receives one application-data message decoded from the SCTP association.
     *
     * <p>The single callback fires synchronously from {@link #feedInboundPacket(byte[])} when usrsctp
     * decodes a DATA chunk into a complete message.
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
     * @param ppid     the SCTP Payload Protocol Identifier (for example the DCEP PPID
     *                 {@link DcepMessage#PPID_DCEP})
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
     * Holds the per-association arena that owns the {@code conn_id} segment and the receive-callback upcall
     * stub.
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
     * Holds the opaque {@link CobaltSctp} socket handle returned by
     * {@link CobaltSctp#cobalt_sctp_socket_create}, nulled on {@link #close()}.
     */
    private MemorySegment socket;

    /**
     * Holds the receive-callback upcall stub whose lifetime is bounded by {@link #arena}.
     *
     * <p>The stub wraps {@link #onReceive(MemorySegment, long, int, int, int, int, MemorySegment)} with the
     * portable scalar signature the shim's C trampoline calls, so no native struct crosses into Java.
     */
    private final MemorySegment receiveCbStub;

    /**
     * Holds the sink for outbound SCTP packets, which the caller wires to its transport's send method.
     */
    private final Consumer<byte[]> outboundSink;

    /**
     * Holds the listener invoked when usrsctp delivers an inbound application message, typed via
     * {@link InboundMessage}.
     *
     * <p>Notification messages such as association-state changes are handled internally by
     * {@link #handleNotification(MemorySegment, long)} rather than delivered to this listener.
     */
    private final InboundListener inboundListener;

    /**
     * Holds the latch that fires when the association handshake reaches a terminal state.
     *
     * <p>The latch counts down exactly once, when usrsctp delivers an {@code SCTP_ASSOC_CHANGE}
     * notification carrying {@code SCTP_COMM_UP} (handshake complete) or any of the failure states. The
     * blocking {@link #connect(int, long, TimeUnit)} call waits on it.
     */
    private final CountDownLatch connectLatch = new CountDownLatch(1);

    /**
     * Holds whether the handshake completed successfully.
     *
     * <p>Set to {@code true} when the {@code SCTP_ASSOC_CHANGE} notification arrives with {@code sac_state}
     * equal to {@code SCTP_COMM_UP}; {@link #connect(int, long, TimeUnit)} inspects it after the latch
     * fires to distinguish success from a terminal failure state.
     */
    private final AtomicBoolean connected = new AtomicBoolean();

    /**
     * Guards {@link #close()} so the deregister, native-close, and arena-close teardown runs at most once.
     *
     * <p>Flipped from {@code false} to {@code true} by a single {@link AtomicBoolean#compareAndSet} in
     * {@link #close()}; only the winning caller runs the teardown, so two concurrent closes cannot both
     * invoke {@link Arena#close()} and make the second throw on an already-closed arena.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs and configures a new association.
     *
     * <p>The constructor allocates a per-association shared arena, allocates a unique one-byte segment to
     * serve as the {@code conn_id}, builds the receive-callback upcall stub, registers that conn id with
     * the {@link SctpEngine} so the global outbound upcall can route to this instance, creates the usrsctp
     * socket via {@link CobaltSctp#cobalt_sctp_socket_create} (which opens an {@code AF_CONN} SCTP socket
     * bound to the receive trampoline), and applies the WebRTC defaults ({@code SCTP_NODELAY},
     * {@code SCTP_DISABLE_FRAGMENTS}, {@code SCTP_INITMSG}), non-blocking mode, and the
     * {@code SCTP_ASSOC_CHANGE} event subscription. On any failure it deregisters the conn id and closes
     * the arena before rethrowing, so no Java-side native resources leak.
     *
     * @param outboundSink    the sink for SCTP packets the local stack wants to send out the wire,
     *                        typically wired to the {@link SctpDtlsBridge} send method; must be
     *                        thread-safe
     * @param inboundListener the listener invoked synchronously from {@link #feedInboundPacket(byte[])}
     *                        when usrsctp decodes a DATA chunk from the inbound SCTP packet
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
            this.receiveCbStub = installReceiveUpcall();
            engine.register(connId, this);
            registeredConnId = connId;
            var outSock = arena.allocate(CobaltSctp.C_POINTER);
            int rc;
            try {
                rc = CobaltSctp.cobalt_sctp_socket_create(receiveCbStub, connId, outSock);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_socket failed", t);
            }
            if (rc != CobaltSctp.COBALT_SCTP_OK()) {
                throw new WhatsAppCallException.Sctp("usrsctp_socket returned " + rc);
            }
            this.socket = outSock.get(CobaltSctp.C_POINTER, 0);
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
     * Allocates the receive-callback upcall stub bound to this association.
     *
     * <p>Binds {@link #onReceive(MemorySegment, long, int, int, int, int, MemorySegment)} to this instance
     * and wraps it in a native upcall stub with the portable scalar signature the shim's C trampoline
     * calls (data pointer, length, stream id, ppid, flags, notification flag, ulp_info), backed by the
     * per-association {@link #arena}.
     *
     * @return the upcall stub address
     * @throws WhatsAppCallException.Sctp if the {@code onReceive} method handle cannot be located
     */
    private MemorySegment installReceiveUpcall() {
        var descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS);
        MethodHandle target;
        try {
            target = MethodHandles.lookup().findVirtual(
                    SctpAssociation.class,
                    "onReceive",
                    MethodType.methodType(
                            int.class, MemorySegment.class, long.class,
                            int.class, int.class, int.class, int.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new WhatsAppCallException.Sctp("could not locate onReceive method", e);
        }
        target = target.bindTo(this);
        return Linker.nativeLinker().upcallStub(target, descriptor, arena);
    }

    /**
     * Binds the association to a local SCTP port.
     *
     * <p>Must be called before {@link #connect(int)}. RFC 8831 specifies port 5000 for WebRTC
     * DataChannels.
     *
     * @param localPort the local SCTP port
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_bind} fails
     */
    public void bind(int localPort) {
        requireOpen();
        int rc;
        try {
            rc = CobaltSctp.cobalt_sctp_bind(socket, localPort, connId);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_bind failed", t);
        }
        if (rc != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_bind returned " + rc);
        }
    }

    /**
     * Performs the SCTP handshake with the peer and blocks until it completes, fails, or the calling
     * thread is interrupted.
     *
     * <p>Equivalent to {@link #connect(int, long, TimeUnit)} with an effectively unbounded timeout. For
     * WebRTC's simultaneous-open pattern (RFC 4960 section 5.2.4 INIT collision), both peers call this
     * concurrently, typically each on its own thread, so that each side's INIT chunk reaches the other
     * after both sockets have entered {@code COOKIE_WAIT}.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_connect} reports a hard failure or the calling
     *                                    thread is interrupted while waiting
     */
    public void connect(int remotePort) {
        connect(remotePort, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    /**
     * Performs the SCTP handshake and blocks until it reaches a terminal state or the timeout expires.
     *
     * <p>The handshake completes successfully when an {@code SCTP_ASSOC_CHANGE} notification reports
     * {@code SCTP_COMM_UP}, and fails when it reports {@code SCTP_COMM_LOST}, {@code SCTP_SHUTDOWN_COMP},
     * or {@code SCTP_CANT_STR_ASSOC}. Because the socket is non-blocking, the shim reports the start of the
     * handshake as {@link CobaltSctp#COBALT_SCTP_IN_PROGRESS}, with completion signalled through the
     * notification rather than the return value.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @param timeout    the maximum time to wait
     * @param unit       the timeout unit
     * @throws NullPointerException       if {@code unit} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_connect} reports a hard failure, the handshake
     *                                    completes with a non-{@code COMM_UP} state, the wait times out, or
     *                                    the calling thread is interrupted
     */
    public void connect(int remotePort, long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");
        requireOpen();
        int rc;
        try {
            rc = CobaltSctp.cobalt_sctp_connect(socket, remotePort, connId);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_connect failed", t);
        }
        if (rc != CobaltSctp.COBALT_SCTP_OK() && rc != CobaltSctp.COBALT_SCTP_IN_PROGRESS()) {
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
     * Feeds one inbound SCTP packet, decoded from a transport record, into the local SCTP stack.
     *
     * <p>The packet is copied into native memory and handed to {@code usrsctp_conninput}, which drives the
     * SCTP state machine and may produce zero or one synchronous calls into
     * {@link InboundListener#onMessage(InboundMessage)} on the calling thread. An empty packet is ignored.
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
            int rc;
            try {
                rc = CobaltSctp.cobalt_sctp_conninput(connId, seg, packet.length);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_conninput failed", t);
            }
            if (rc != CobaltSctp.COBALT_SCTP_OK()) {
                throw new WhatsAppCallException.Sctp("usrsctp_conninput returned " + rc);
            }
        }
    }

    /**
     * Sends one application-data message on a specific SCTP stream with the given Payload Protocol
     * Identifier.
     *
     * <p>The message is queued through {@code usrsctp_sendv}; usrsctp then emits one or more SCTP packets
     * through the conn-output upcall, which arrive at the caller's {@code outboundSink}. The call fails if
     * usrsctp accepts fewer bytes than the payload length, since the WebRTC defaults disable fragmentation.
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
        try (var scratch = Arena.ofConfined()) {
            var data = stagePayload(payload, scratch);
            long sent;
            try {
                sent = CobaltSctp.cobalt_sctp_send(socket, data, payload.length, streamId, ppid, ordered ? 0 : 1);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv failed", t);
            }
            checkSent(sent, payload.length, "usrsctp_sendv");
        }
    }

    /**
     * Sends a message under a partial-reliability policy.
     *
     * <p>Routes through {@code usrsctp_sendv} with a partial-reliability container the shim builds C-side,
     * so the message is delivered on the chosen stream with the requested PR policy applied by usrsctp's
     * outbound scheduler.
     *
     * @param streamId the SCTP stream index (0 .. 1023)
     * @param ppid     the SCTP PPID
     * @param payload  the message bytes
     * @param ordered  {@code true} for ordered delivery, {@code false} for unordered
     * @param prPolicy one of {@link CobaltSctp#COBALT_SCTP_PR_NONE()}, {@link CobaltSctp#COBALT_SCTP_PR_TTL()},
     *                 or {@link CobaltSctp#COBALT_SCTP_PR_RTX()}
     * @param prValue  the policy operand: max retransmissions for {@code COBALT_SCTP_PR_RTX}, lifetime
     *                 milliseconds for {@code COBALT_SCTP_PR_TTL}; ignored for {@code COBALT_SCTP_PR_NONE}
     * @throws NullPointerException       if {@code payload} is {@code null}
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_sendv} fails or sends fewer bytes than requested
     */
    public void sendWithPolicy(int streamId, int ppid, byte[] payload, boolean ordered,
                               int prPolicy, int prValue) {
        Objects.requireNonNull(payload, "payload cannot be null");
        requireOpen();
        try (var scratch = Arena.ofConfined()) {
            var data = stagePayload(payload, scratch);
            long sent;
            try {
                sent = CobaltSctp.cobalt_sctp_send_pr(socket, data, payload.length, streamId, ppid,
                        ordered ? 0 : 1, prPolicy, prValue);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Sctp("usrsctp_sendv (SPA) failed", t);
            }
            checkSent(sent, payload.length, "usrsctp_sendv (SPA)");
        }
    }

    /**
     * Stages a payload into native memory for a send, returning {@link MemorySegment#NULL} for an empty
     * payload.
     *
     * @param payload the message bytes
     * @param scratch the confined arena owning the staged segment for the duration of the send
     * @return the native segment holding the payload, or {@link MemorySegment#NULL} when empty
     */
    private MemorySegment stagePayload(byte[] payload, Arena scratch) {
        if (payload.length == 0) {
            return MemorySegment.NULL;
        }
        var data = scratch.allocate(payload.length);
        MemorySegment.copy(payload, 0, data, ValueLayout.JAVA_BYTE, 0, payload.length);
        return data;
    }

    /**
     * Validates the byte count returned by a send against the requested length.
     *
     * @param sent      the byte count the shim returned (negative on error)
     * @param expected  the payload length that should have been accepted
     * @param operation the operation name for the error message
     * @throws WhatsAppCallException.Sctp if {@code sent} is negative or not equal to {@code expected}
     */
    private void checkSent(long sent, int expected, String operation) {
        if (sent < 0) {
            throw new WhatsAppCallException.Sctp(operation + " returned " + sent);
        }
        if (sent != expected) {
            throw new WhatsAppCallException.Sctp(operation + " short write: " + sent + " of " + expected);
        }
    }

    /**
     * Forwards an outbound SCTP packet from usrsctp's conn-output upcall to the caller's
     * {@code outboundSink}.
     *
     * <p>Visible to {@link SctpEngine} only; the engine resolves the firing conn id back to this
     * association and invokes this method.
     *
     * @param packet the packet bytes to ship to the peer
     */
    void deliverOutbound(byte[] packet) {
        outboundSink.accept(packet);
    }

    /**
     * Dispatches one usrsctp receive-callback firing, the entry point the shim's C trampoline invokes
     * synchronously inside {@link #feedInboundPacket(byte[])} once an inbound DATA chunk has decoded into a
     * complete message or a notification.
     *
     * <p>Notification messages are routed to {@link #handleNotification(MemorySegment, long)}; null or
     * empty data is ignored; otherwise the native payload is copied into a fresh {@code byte[]} and
     * delivered to the caller's {@link InboundListener} with the stream id and PPID the shim already read
     * from the receive-info struct and normalised to host byte order. Any exception thrown by the listener
     * is swallowed so a single bad message cannot corrupt usrsctp's call stack.
     *
     * @param data           the pointer to the native payload buffer, valid only for the duration of this
     *                       call
     * @param datalen        the length in bytes of the native payload
     * @param streamId       the SCTP stream the message arrived on, already unsigned-extended
     * @param ppid           the SCTP PPID in host byte order
     * @param flags          the raw {@code MSG_*} flags
     * @param isNotification {@code 1} when the message is an SCTP notification rather than application data
     * @param ulpInfo        the ulp_info pointer the socket was created with (this association's
     *                       {@link #connId})
     * @return {@code 0} to signal success; usrsctp treats a non-zero return as a request to drop the
     * message
     */
    private int onReceive(MemorySegment data, long datalen, int streamId, int ppid, int flags,
                          int isNotification, MemorySegment ulpInfo) {
        if (isNotification != 0) {
            handleNotification(data, datalen);
            return 0;
        }
        if (datalen <= 0 || data.equals(MemorySegment.NULL)) {
            return 0;
        }
        try {
            var payload = data.reinterpret(datalen).toArray(ValueLayout.JAVA_BYTE);
            inboundListener.onMessage(new InboundMessage(streamId, ppid, payload, flags));
        } catch (Throwable _) {
        }
        return 0;
    }

    /**
     * Decodes an SCTP notification to surface association state changes.
     *
     * <p>Only {@code SCTP_ASSOC_CHANGE} notifications are acted on. Their {@code sac_state} maps to the
     * {@code SCTP_COMM_UP}, {@code SCTP_COMM_LOST}, {@code SCTP_SHUTDOWN_COMP}, or
     * {@code SCTP_CANT_STR_ASSOC} states; {@code SCTP_COMM_UP} marks success and any of the others marks a
     * terminal failure, and reaching any of them fires {@link #connectLatch} so the blocking
     * {@link #connect(int, long, TimeUnit)} call can return. Notifications shorter than the
     * {@code sac_state} field, null data, and non-assoc-change types are ignored. Notification fields are
     * read in host byte order rather than network byte order.
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
        if (snType != SCTP_ASSOC_CHANGE) {
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
     * <p>Enables {@code SCTP_NODELAY} and {@code SCTP_DISABLE_FRAGMENTS}, then sets {@code SCTP_INITMSG}
     * requesting {@link #WEBRTC_NUM_STREAMS} streams in each direction with the INIT attempt and timeout
     * left at the usrsctp defaults.
     *
     * @throws WhatsAppCallException.Sctp if any of the shim option calls fails
     */
    private void applyWebRtcDefaults() {
        int nodelay;
        int disableFragments;
        int initmsg;
        try {
            nodelay = CobaltSctp.cobalt_sctp_set_nodelay(socket, 1);
            disableFragments = CobaltSctp.cobalt_sctp_set_disable_fragments(socket, 1);
            initmsg = CobaltSctp.cobalt_sctp_set_initmsg(socket, WEBRTC_NUM_STREAMS, WEBRTC_NUM_STREAMS, 0, 0);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt (WebRTC defaults) failed", t);
        }
        if (nodelay != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_NODELAY returned " + nodelay);
        }
        if (disableFragments != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_DISABLE_FRAGMENTS returned " + disableFragments);
        }
        if (initmsg != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_INITMSG returned " + initmsg);
        }
    }

    /**
     * Switches the socket into non-blocking mode.
     *
     * @implNote This implementation requires non-blocking mode so that the single-threaded usrsctp lock
     * model used by {@code usrsctp_init_nothreads} does not deadlock when both peers call
     * {@link #connect(int)} simultaneously, which is the WebRTC simultaneous-open pattern of RFC 4960
     * section 5.2.4.
     *
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_set_non_blocking} fails
     */
    private void applyNonBlocking() {
        int rc;
        try {
            rc = CobaltSctp.cobalt_sctp_set_non_blocking(socket, 1);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_set_non_blocking failed", t);
        }
        if (rc != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_set_non_blocking returned " + rc);
        }
    }

    /**
     * Subscribes the socket to {@code SCTP_ASSOC_CHANGE} notifications via the {@code SCTP_EVENT} socket
     * option.
     *
     * <p>The subscribed notification fires when the handshake reaches {@code SCTP_COMM_UP} or terminates,
     * which is what drives {@link #handleNotification(MemorySegment, long)} and ultimately unblocks
     * {@link #connect(int, long, TimeUnit)}.
     *
     * @throws WhatsAppCallException.Sctp if the shim subscription call fails
     */
    private void subscribeAssocChangeNotifications() {
        int rc;
        try {
            rc = CobaltSctp.cobalt_sctp_subscribe_assoc_change(socket, 1);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_EVENT failed", t);
        }
        if (rc != CobaltSctp.COBALT_SCTP_OK()) {
            throw new WhatsAppCallException.Sctp("usrsctp_setsockopt SCTP_EVENT returned " + rc);
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
     * <p>The method is idempotent: a second call returns immediately. The teardown order is fixed by
     * usrsctp's contract: deregister the conn id first, to drain any in-flight conn-output upcalls routed
     * to it; then {@code usrsctp_close} via the shim to destroy the socket and free its context; then
     * {@link Arena#close()} to free the conn id segment and the upcall stub. The shim close call cannot
     * raise, so the arena is always closed even if it throws.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        engine.unregister(connId);
        try {
            CobaltSctp.cobalt_sctp_close(socket);
        } catch (Throwable _) {
        } finally {
            socket = MemorySegment.NULL;
            arena.close();
        }
    }
}
