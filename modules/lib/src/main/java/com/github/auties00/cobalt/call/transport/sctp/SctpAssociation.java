package com.github.auties00.cobalt.call.transport.sctp;

import com.github.auties00.cobalt.call.transport.sctp.bindings.UsrSctp;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_event;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_initmsg;
import com.github.auties00.cobalt.call.transport.sctp.bindings.sctp_rcvinfo;
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
 * One usrsctp socket bound to one peer — the SCTP half of a single
 * WebRTC DataChannel association. Outbound SCTP packets are routed
 * through {@code outboundSink} (which the caller wires to the
 * underlying DTLS transport); inbound DTLS payloads are pushed in via
 * {@link #feedInboundPacket} and decoded by usrsctp's state machine.
 *
 * <p>The class is {@link AutoCloseable}; teardown order is
 * deregister → close socket → close arena, which is the order
 * usrsctp itself documents to drain any in-flight conn_output
 * callbacks before the address segment is freed.
 *
 * <p>Threading model follows Cobalt's standard virtual-thread,
 * direct-blocking pattern: this class is intended to be driven from a
 * single owning virtual thread that does {@link #feedInboundPacket}
 * for inbound DTLS records and {@link #send} for outbound DataChannel
 * messages. The {@code outboundSink} {@link Consumer} may be invoked
 * from that same thread (synchronously inside {@code send}) or from
 * the engine's tick driver thread; implementations must be
 * thread-safe.
 *
 * <p>WebRTC defaults are pre-applied at construction:
 * {@code SCTP_NODELAY}, {@code SCTP_DISABLE_FRAGMENTS}, and an
 * {@code SCTP_INITMSG} with 1024 in/out streams as recommended by
 * RFC 8831. Higher-level DataChannel framing (PPIDs, RFC 8831 DCEP
 * messages) lives in the SCTP DataChannel transport (#60) layered on
 * top of this class.
 */
public final class SctpAssociation implements AutoCloseable {
    /**
     * RFC 8831 default number of inbound and outbound SCTP streams
     * for a WebRTC DataChannel association.
     */
    private static final int WEBRTC_NUM_STREAMS = 1024;

    /**
     * RFC 4960 §5.4 / usrsctp.h — bit set in
     * {@link sctp_sndinfo#snd_flags} to request unordered delivery
     * for a single message. Inlined here because jextract drops it
     * from the {@link UsrSctp} bindings (the macro group it sits in
     * is not parseable as a constant expression).
     */
    private static final short SCTP_UNORDERED = 0x0400;

    /**
     * RFC 6458 §6.1.1 — {@code sac_state} value indicating the SCTP
     * association handshake has completed and the socket is ready
     * for application data.
     */
    private static final int SCTP_COMM_UP = 0x0001;

    /**
     * RFC 6458 §6.1.1 — {@code sac_state} value indicating the
     * association has been torn down by the peer or by a fault.
     */
    private static final int SCTP_COMM_LOST = 0x0002;

    /**
     * RFC 6458 §6.1.1 — {@code sac_state} value indicating a
     * graceful shutdown has completed.
     */
    private static final int SCTP_SHUTDOWN_COMP = 0x0004;

    /**
     * RFC 6458 §6.1.1 — {@code sac_state} value indicating the
     * association could not be started (peer unreachable, INIT
     * rejected, etc.).
     */
    private static final int SCTP_CANT_STR_ASSOC = 0x0005;

    /**
     * Byte offset of the {@code sac_state} field within a
     * {@code sctp_assoc_change} notification: layout is
     * {@code uint16_t sac_type; uint16_t sac_flags; uint32_t
     * sac_length; uint16_t sac_state; ...}.
     */
    private static final int SAC_STATE_OFFSET = 8;

    /**
     * Per-association arena owning the {@code conn_id} segment, the
     * receive-callback upcall stub, and any scratch buffers reused
     * across calls.
     */
    private final Arena arena;

    /**
     * Engine the association registered with — held so {@link #close}
     * can deregister cleanly.
     */
    private final SctpEngine engine;

    /**
     * Unique opaque pointer this association registered with usrsctp
     * to route outbound packets. The address (not the contents) is
     * what matters; usrsctp treats it as a key.
     */
    private final MemorySegment connId;

    /**
     * Pointer to the {@code struct socket *} returned by
     * {@code usrsctp_socket}; nulled on {@link #close}.
     */
    private MemorySegment socket;

    /**
     * Receive-callback upcall stub. Lifetime is bounded by
     * {@link #arena}.
     */
    private final MemorySegment receiveCbStub;

    /**
     * Sink for outbound SCTP packets; the caller wires this to its
     * DTLS transport's send method.
     */
    private final Consumer<byte[]> outboundSink;

    /**
     * Listener invoked when usrsctp delivers an inbound application
     * message — typed via {@link InboundMessage}. Notification
     * messages (assoc state changes etc.) are surfaced separately
     * via {@link #notificationListener} once the DataChannel
     * transport (#60) needs them.
     */
    private final InboundListener inboundListener;

    /**
     * Reusable scratch segment for the {@link sctp_sndinfo} struct
     * passed to {@code usrsctp_sendv}.
     */
    private final MemorySegment sndInfoScratch;

    /**
     * Reusable scratch segment for the local-side
     * {@link sockaddr_conn} struct passed to {@code usrsctp_bind}.
     */
    private final MemorySegment localAddr;

    /**
     * Reusable scratch segment for the remote-side
     * {@link sockaddr_conn} struct passed to {@code usrsctp_connect}.
     */
    private final MemorySegment remoteAddr;

    /**
     * Latch that fires when usrsctp delivers an
     * {@code SCTP_ASSOC_CHANGE} notification with {@code sac_state ==
     * SCTP_COMM_UP} (handshake complete) or any of the failure
     * states. {@link #awaitConnected} blocks on this; the latch fires
     * exactly once per association.
     */
    private final CountDownLatch connectLatch = new CountDownLatch(1);

    /**
     * Set to {@code true} when the {@code SCTP_ASSOC_CHANGE}
     * notification arrived with {@code sac_state == SCTP_COMM_UP}.
     * {@link #awaitConnected} returns its final value.
     */
    private final AtomicBoolean connected = new AtomicBoolean();

    /**
     * Functional interface invoked when usrsctp delivers a DATA
     * chunk to the receive callback. The payload is a fresh
     * {@code byte[]} owned by the caller.
     */
    @FunctionalInterface
    public interface InboundListener {
        /**
         * Receives one application-data message from the SCTP
         * association.
         *
         * @param msg the inbound message
         */
        void onMessage(InboundMessage msg);
    }

    /**
     * Decoded application-data message delivered to
     * {@link InboundListener}.
     *
     * @param streamId the SCTP stream the message arrived on
     * @param ppid     the SCTP Payload Protocol Identifier (e.g.
     *                 the WebRTC DataChannel-binary PPID 53)
     * @param payload  the raw payload bytes
     * @param flags    raw {@code msg_flags} from
     *                 {@code usrsctp_recvv}
     */
    public record InboundMessage(int streamId, int ppid, byte[] payload, int flags) {
        /**
         * Compact constructor — null-checks the payload.
         */
        public InboundMessage {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs and configures a new association.
     *
     * <p>Steps performed:
     *
     * <ol>
     *   <li>Allocates a per-association {@link Arena#ofShared() shared arena}.</li>
     *   <li>Allocates a unique 1-byte segment as the {@code conn_id}.</li>
     *   <li>Registers the conn id with the {@link SctpEngine} so the
     *       global outbound upcall can route to this instance.</li>
     *   <li>Builds the receive-callback upcall stub.</li>
     *   <li>Calls {@code usrsctp_socket(AF_CONN, SOCK_STREAM,
     *       IPPROTO_SCTP, recv_cb, NULL, 0, conn_id)}.</li>
     *   <li>Applies the WebRTC defaults: {@code SCTP_NODELAY},
     *       {@code SCTP_DISABLE_FRAGMENTS}, {@code SCTP_INITMSG}.</li>
     * </ol>
     *
     * @param outboundSink    sink for SCTP packets the local stack
     *                        wants to send out the wire — typically
     *                        wired to the DTLS transport's send
     *                        method; thread-safe
     * @param inboundListener invoked synchronously from
     *                        {@link #feedInboundPacket} when usrsctp
     *                        decodes a DATA chunk from the inbound
     *                        SCTP packet
     * @throws NullPointerException if any argument is {@code null}
     * @throws WhatsAppCallException.Sctp        if the socket cannot be created
     *                              or configured
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
     * Binds the association to a local SCTP port. Must be called
     * before {@link #connect}.
     *
     * @param localPort the local SCTP port (RFC 8831 specifies 5000
     *                  for WebRTC DataChannels)
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
     * Performs the SCTP handshake with the peer and blocks until
     * {@code SCTP_COMM_UP} fires (or a failure / interrupt is
     * observed). Equivalent to
     * {@link #connect(int, long, TimeUnit)} with no timeout.
     *
     * <p>For WebRTC's "simultaneous open" pattern (RFC 4960 §5.2.4
     * INIT-collision), both peers must call {@code connect}
     * concurrently — typically each on its own thread — so that
     * each side's INIT chunk reaches the other side after both
     * sockets have entered {@code COOKIE_WAIT}.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_connect} reports a
     *                       hard failure or the calling thread is
     *                       interrupted while waiting
     */
    public void connect(int remotePort) {
        connect(remotePort, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    /**
     * Performs the SCTP handshake and blocks until
     * {@code SCTP_COMM_UP} fires, the handshake fails
     * ({@code SCTP_COMM_LOST} / {@code SCTP_CANT_STR_ASSOC} /
     * {@code SCTP_SHUTDOWN_COMP}), or the timeout expires.
     *
     * <p>The underlying socket is non-blocking, so {@code usrsctp_connect}
     * returns {@code -1} with {@code EINPROGRESS} for the success
     * case; that's treated as the start of the handshake here, with
     * completion signalled via the {@code SCTP_ASSOC_CHANGE}
     * notification.
     *
     * @param remotePort the peer's SCTP port (5000 in WebRTC)
     * @param timeout    the maximum time to wait
     * @param unit       the timeout unit
     * @throws WhatsAppCallException.Sctp        if {@code usrsctp_connect}
     *                              reports a hard failure, the
     *                              handshake completes with a
     *                              non-{@code COMM_UP} state, the
     *                              wait times out, or the calling
     *                              thread is interrupted
     * @throws NullPointerException if {@code unit} is {@code null}
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
     * Feeds one inbound SCTP packet (decoded from a DTLS record)
     * into the local SCTP stack. The packet may produce zero or one
     * synchronous calls into {@link InboundListener#onMessage} on
     * the calling thread.
     *
     * @param packet the SCTP packet bytes; never {@code null}
     * @throws NullPointerException if {@code packet} is {@code null}
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
     * Sends one application-data message on a specific SCTP stream
     * with the given Payload Protocol Identifier.
     *
     * @param streamId the SCTP stream index (0 .. 1023)
     * @param ppid     the SCTP PPID (WebRTC uses 50/51/53/56/57)
     * @param payload  the message bytes
     * @param ordered  {@code true} for ordered delivery,
     *                 {@code false} for unordered
     * @throws WhatsAppCallException.Sctp if {@code usrsctp_sendv} fails or sends
     *                       fewer bytes than requested
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
     * Forwards an outbound SCTP packet from usrsctp's conn_output
     * upcall to the caller's {@code outboundSink}. Visible to
     * {@link SctpEngine} only.
     *
     * @param packet the packet bytes to ship to the peer
     */
    void deliverOutbound(byte[] packet) {
        outboundSink.accept(packet);
    }

    /**
     * usrsctp receive callback — fires synchronously inside
     * {@link #feedInboundPacket} when an inbound DATA chunk is
     * decoded into a complete application message. Forwards the
     * message to the caller's {@link InboundListener} after copying
     * the native payload into a fresh {@code byte[]}.
     *
     * <p>Notification messages (state changes, peer-addr events) are
     * silently dropped here — the SCTP DataChannel transport (#60)
     * will subscribe to those once needed.
     *
     * @param sock     the firing socket pointer (unused — we have
     *                 only one)
     * @param addr     the peer's {@link sockaddr_conn} by value
     *                 (unused for AF_CONN — the conn id alone
     *                 identifies the peer)
     * @param data     pointer to the native payload buffer; usrsctp
     *                 frees this after the callback returns
     * @param datalen  length in bytes of the native payload
     * @param rcvinfo  receive metadata struct (stream, ppid, flags)
     * @param flags    raw {@code MSG_*} flags
     * @param ulpInfo  ulp_info pointer the socket was created with
     *                 (always our {@link #connId})
     * @return 0 to signal success — non-zero is treated by usrsctp
     *         as "drop this message"
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
            int streamId = Short.toUnsignedInt(sctp_rcvinfo.rcv_sid(rcvinfo));
            int ppid = ntohl(sctp_rcvinfo.rcv_ppid(rcvinfo));
            inboundListener.onMessage(new InboundMessage(streamId, ppid, payload, flags));
        } catch (Throwable _) {
        }
        return 0;
    }

    /**
     * Decodes the leading bytes of an SCTP notification to surface
     * association state changes. The {@code SCTP_ASSOC_CHANGE}
     * notification carries an {@code sac_state} that maps to the
     * {@code SCTP_COMM_UP} / {@code SCTP_COMM_LOST} /
     * {@code SCTP_SHUTDOWN_COMP} / {@code SCTP_CANT_STR_ASSOC}
     * states; reaching any of these fires {@link #connectLatch} so
     * {@link #awaitConnected} can return.
     *
     * <p>Notification fields are in host byte order (not network).
     *
     * @param data    pointer to the notification payload (a
     *                {@code union sctp_notification})
     * @param datalen length of the payload in bytes
     */
    private void handleNotification(MemorySegment data, long datalen) {
        if (datalen < SAC_STATE_OFFSET + Short.BYTES || data.equals(MemorySegment.NULL)) {
            return;
        }
        var view = data.reinterpret(datalen);
        int snType = Short.toUnsignedInt(view.get(ValueLayout.JAVA_SHORT, 0));
        if (snType != UsrSctp.SCTP_ASSOC_CHANGE()) {
            return;
        }
        int sacState = Short.toUnsignedInt(view.get(ValueLayout.JAVA_SHORT, SAC_STATE_OFFSET));
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
     * Applies the standard WebRTC SCTP socket configuration —
     * {@code SCTP_NODELAY}, {@code SCTP_DISABLE_FRAGMENTS},
     * {@code SCTP_INITMSG} with 1024 streams each direction.
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
     * Switches the socket into non-blocking mode. Required so the
     * single-threaded usrsctp lock model used by
     * {@code usrsctp_init_nothreads} does not deadlock when both
     * peers call {@link #connect} simultaneously (the WebRTC pattern
     * — see RFC 4960 §5.2.4).
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
     * Subscribes the socket to {@code SCTP_ASSOC_CHANGE}
     * notifications via the {@code SCTP_EVENT} setsockopt. The
     * notification fires when the handshake reaches
     * {@code SCTP_COMM_UP} or terminates, driving
     * {@link #awaitConnected}.
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
     * Throws if the socket has been closed.
     */
    private void requireOpen() {
        if (socket == null || socket.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("SctpAssociation is closed");
        }
    }

    /**
     * Tears down the association. Idempotent. Order is fixed by
     * usrsctp's contract:
     *
     * <ol>
     *   <li>{@code usrsctp_deregister_address} — drains any
     *       in-flight conn_output upcalls routed to this conn id.</li>
     *   <li>{@code usrsctp_close} — destroys the socket.</li>
     *   <li>{@link Arena#close()} — frees the conn id segment, the
     *       upcall stub, and all scratch buffers.</li>
     * </ol>
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
     * Host-to-network byte-order conversion for a 16-bit value, used
     * when populating the {@code sin_port}/{@code sconn_port} field
     * of a sockaddr (which the BSD API requires in network byte
     * order).
     *
     * @param value the host-order short
     * @return the network-order short
     */
    private static short htons(int value) {
        return Short.reverseBytes((short) value);
    }

    /**
     * Host-to-network byte-order conversion for a 32-bit value,
     * applied to SCTP PPIDs in {@link sctp_sndinfo#snd_ppid}.
     *
     * @param value the host-order int
     * @return the network-order int
     */
    private static int htonl(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Inverse of {@link #htonl}.
     *
     * @param value the network-order int
     * @return the host-order int
     */
    private static int ntohl(int value) {
        return Integer.reverseBytes(value);
    }
}
