package com.github.auties00.cobalt.socket.implementation.context;

import com.github.auties00.cobalt.socket.implementation.SocketListener;
import com.github.auties00.cobalt.socket.implementation.websocket.WebSocketState;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-connection state attached to a {@link SelectionKey}
 * by the central selector.
 *
 * <p> A {@code SocketContext} is created when a connection is opened and
 * remains associated with the channel for its entire lifetime.  It is
 * attached to the key via
 * {@link SelectionKey#attach(Object) SelectionKey.attach}
 * at registration time and retrieved with
 * {@link SelectionKey#attachment() SelectionKey.attachment}
 * on every selection cycle.
 *
 * <p> The context operates in two phases, governed by the {@link #tunnelled}
 * flag:
 *
 * <ol>
 *   <li><em>Pre-tunnel</em> ({@code tunnelled == false}) -- active during a
 *       proxy handshake (HTTP CONNECT or SOCKS5 negotiation).  The proxy
 *       client issues explicit reads through {@link #pendingBinaryRead} and
 *       writes through {@link #pendingWrites}.  For direct (non-proxied)
 *       connections this phase is skipped entirely: the context is constructed
 *       with {@code tunnelled = true}.</li>
 *   <li><em>Post-tunnel</em> ({@code tunnelled == true}) -- the WhatsApp
 *       datagram protocol is active.  Incoming bytes are framed as a 3-byte
 *       big-endian length prefix followed by a payload of that length.
 *       The selector reads the prefix into {@link #datagramLengthBuffer},
 *       allocates {@link #datagramBuffer} for the payload, and delivers the
 *       completed datagram to {@link #listener}.  Outgoing buffers are still
 *       enqueued through {@link #pendingWrites}.</li>
 * </ol>
 *
 * <p> <em>Thread safety:</em> fields in this class are read by the selector
 * thread during event processing, but may be modified by an arbitrary number
 * of threads (any thread that sends a message, initiates a proxy handshake
 * read, or disconnects).  The specific access pattern for each field is
 * documented on the field itself.  No general-purpose locking is used;
 * thread safety relies on the selector's single-threaded read path, volatile
 * semantics where needed, and the lock-free guarantees of
 * {@link SocketPendingWrites}.
 *
 * @see SocketPendingWrites
 * @see SocketPendingRead
 */
public final class SocketContext {
    private static final int INT24_BYTE_SIZE = 3;
    private static final int WRITES_CHUNK_CAPACITY = 64;

    public enum FramingMode {
        DATAGRAM,
        WEBSOCKET
    }

    /**
     * Whether the underlying channel is connected and registered with the
     * selector.
     *
     * <p> Set to {@code true} by the connecting thread after the handshake
     * completes; set to {@code false} by the selector thread on disconnect
     * or I/O failure.  Read by any thread via
     * {@code SocketClient.isConnected()}.
     */
    public final AtomicBoolean connected = new AtomicBoolean();

    /**
     * Monitor used to block the connecting thread until the selector
     * completes the non-blocking {@link SocketChannel#connect
     * connect} operation.
     *
     * <p> The connecting thread calls {@code connectionLock.wait()} after
     * initiating a non-blocking connect.  The selector thread calls
     * {@code connectionLock.notifyAll()} when
     * {@link SocketChannel#finishConnect finishConnect}
     * succeeds.
     */
    public final Object connectionLock;

    /**
     * Whether the connection has completed any proxy negotiation and is
     * ready for the WhatsApp datagram protocol.
     *
     * <p> For direct connections this is {@code true} from construction.
     * For proxied connections it transitions from {@code false} to
     * {@code true} exactly once, after the proxy handshake succeeds.
     * Once {@code true} it never reverts.
     *
     * <p> Read and written exclusively by the selector thread.
     */
    public boolean tunnelled;

    /**
     * The framing mode used after the tunnel/authentication stage completes.
     * Defaults to datagram framing.
     */
    public FramingMode framingMode;

    /**
     * The pending binary read request, or {@code null} if no read is
     * in progress.
     *
     * <p> Used only during the pre-tunnel phase.  The proxy handshake is
     * sequential -- each request is sent and its response fully read before
     * the next request -- so at most one read is outstanding at any time.
     * A nullable field is therefore sufficient; no collection is needed.
     *
     * <p> Written by the thread calling {@code SocketClient.readBinary};
     * read and cleared by the selector thread.
     */
    public volatile SocketPendingRead pendingBinaryRead;

    /**
     * Lock-free MPSC queue of outbound buffers waiting to be written to
     * the channel.
     *
     * <p> Producers (any thread calling {@code SocketClient.sendBinary})
     * enqueue buffers via {@link SocketPendingWrites#offer offer}.  The
     * selector thread drains the queue via
     * {@link SocketPendingWrites#claim() claim} and
     * {@link SocketPendingWrites#release(int) release}, passing the
     * backing array directly to
     * {@link GatheringByteChannel#write(ByteBuffer[], int, int)
     * GatheringByteChannel.write}.
     *
     * <p> Used in both phases.
     */
    public final SocketPendingWrites pendingWrites;

    /**
     * Buffer for reading the 3-byte length prefix of the current inbound
     * WhatsApp datagram.
     *
     * <p> Used only during the post-tunnel phase.  Accessed exclusively
     * by the selector thread.  Cleared and reused after each complete
     * datagram delivery.
     */
    public final ByteBuffer datagramLengthBuffer;

    /**
     * Buffer for accumulating the payload of the current inbound WhatsApp
     * datagram, or {@code null} if the length prefix has not yet been
     * fully read.
     *
     * <p> Allocated by the selector thread once the 3-byte length prefix
     * is complete, sized to the decoded length.  Set back to {@code null}
     * after the completed datagram is handed off to {@link #listener}.
     * Accessed exclusively by the selector thread.
     */
    public ByteBuffer datagramBuffer;

    /**
     * Stateful websocket parser context. Initialized lazily when websocket
     * framing is enabled for this channel.
     */
    public WebSocketState webSocketState;

    /**
     * Callback invoked when a complete inbound datagram has been
     * reassembled.
     *
     * <p> The selector thread dispatches each datagram to the listener
     * on a virtual thread to avoid blocking the selection loop.
     */
    public final SocketListener listener;

    /**
     * The {@link SSLEngine} for this connection, or {@code null} if TLS
     * is not active.  Set once via {@link #initSsl} before the selector
     * thread begins driving the handshake; never changed after that.
     */
    public SSLEngine sslEngine;

    /**
     * {@code true} while the TLS handshake is in progress.  Set to
     * {@code false} once the handshake completes.  Read and written
     * exclusively by the selector thread after the initial set in
     * {@link #initSsl}.
     */
    public boolean sslHandshaking;

    /**
     * {@code true} while SSL delegated tasks are running on a background
     * thread. The selector thread skips handshake processing while this
     * flag is set. Written by the selector thread (set) and the task
     * thread (clear + wakeup).
     */
    public volatile boolean sslTasksPending;

    /**
     * Set to {@code true} when the TLS handshake completes successfully.
     * Read by the thread waiting on {@link #sslHandshakeLock} after
     * being notified.
     */
    public volatile boolean sslHandshakeComplete;

    /**
     * Monitor used to block the thread that initiates TLS until the
     * selector thread completes the handshake.
     */
    public final Object sslHandshakeLock;

    /**
     * Buffer for encrypted inbound data read from the channel.
     * Allocated as a direct buffer (sized to
     * {@code SSLSession.getPacketBufferSize()}) so that
     * {@code channel.read()} avoids the JVM's internal heap-to-direct copy.
     */
    public ByteBuffer netInBuffer;

    /**
     * Buffer for encrypted outbound data to write to the channel.
     * Allocated as a direct buffer for the same reason as
     * {@link #netInBuffer}.
     */
    public ByteBuffer netOutBuffer;

    /**
     * Buffer for decrypted inbound application data produced by
     * {@code SSLEngine.unwrap()}.  Heap-allocated because it is
     * consumed by Java code and never passed to channel I/O directly.
     * Used as overflow storage when {@code unwrap()} produces more
     * bytes than the current target buffer can accept.
     */
    public ByteBuffer appInBuffer;

    /**
     * The virtual executor for listener events
     */
    public volatile ExecutorService listenerVirtualExecutor;

    /**
     * The lock to create/destroy the virtual executor for listener events
     */
    private final Object listenerVirtualExecutorLock;

    /**
     * Creates a context for a new connection.
     *
     * @param listener  the callback to receive completed inbound datagrams
     */
    public SocketContext(SocketListener listener) {
        this.connectionLock = new Object();
        this.sslHandshakeLock = new Object();
        this.listener = listener;
        this.pendingWrites = new SocketPendingWrites(WRITES_CHUNK_CAPACITY);
        this.datagramLengthBuffer = ByteBuffer.allocate(INT24_BYTE_SIZE);
        this.framingMode = FramingMode.DATAGRAM;
        this.listenerVirtualExecutorLock = new Object();
    }

    /**
     * Starts the virtual listener executor if one doesn't already exist
     */
    public void startListenerExecutor() {
        if(listenerVirtualExecutor == null || listenerVirtualExecutor.isShutdown()) {
            synchronized (listenerVirtualExecutorLock) {
                if(listenerVirtualExecutor == null || listenerVirtualExecutor.isShutdown()) {
                    listenerVirtualExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
                }
            }
        }
    }

    /**
     * Stops the virtual listener executor if one exists
     */
    public void stopListenerExecutor() {
        if(listenerVirtualExecutor != null && !listenerVirtualExecutor.isShutdown()) {
            synchronized (listenerVirtualExecutorLock) {
                if(listenerVirtualExecutor != null && !listenerVirtualExecutor.isShutdown()) {
                    listenerVirtualExecutor.shutdownNow();
                    listenerVirtualExecutor = null;
                }
            }
        }
    }

    /**
     * Initializes TLS state for this connection.  Must be called before
     * the selector thread begins driving the handshake.
     *
     * @param engine the configured {@link SSLEngine} (client mode, with
     *        SNI and hostname verification set)
     */
    public void initSsl(SSLEngine engine) {
        this.sslEngine = engine;
        var session = engine.getSession();
        this.netInBuffer = ByteBuffer.allocateDirect(session.getPacketBufferSize());
        this.netOutBuffer = ByteBuffer.allocateDirect(session.getPacketBufferSize());
        this.appInBuffer = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.sslHandshaking = true;
        this.sslHandshakeComplete = false;
    }
}
