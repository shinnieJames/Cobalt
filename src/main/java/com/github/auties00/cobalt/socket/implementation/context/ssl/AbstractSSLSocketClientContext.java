package com.github.auties00.cobalt.socket.implementation.context.ssl;

import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketClientContext;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

public abstract class AbstractSSLSocketClientContext extends AbstractSocketClientContext {
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
     * Creates a context for a new connection.
     */
    public AbstractSSLSocketClientContext() {
        super();
        this.sslHandshakeLock = new Object();
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

    /**
     * Resizes the TLS buffers, if necessary
     */
    public void resizeSslBuffers() {
        var session = sslEngine.getSession();

        var packetSize = session.getPacketBufferSize();
        if (netInBuffer.capacity() < packetSize) {
            netInBuffer = ByteBuffer.allocateDirect(packetSize);
        }
        if (netOutBuffer.capacity() < packetSize) {
            netOutBuffer = ByteBuffer.allocateDirect(packetSize);
        }

        var appSize = session.getApplicationBufferSize();
        if (appInBuffer.capacity() < appSize) {
            appInBuffer = ByteBuffer.allocate(appSize);
        }
    }
}
