package com.github.auties00.cobalt.socket.layer.security.tls;

import com.github.auties00.cobalt.socket.layer.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A layer context that provides TLS encryption and decryption.
 *
 * <p>This context is reusable for both tunnel-level TLS (HTTPS proxy)
 * and transport-level TLS (secure WebSocket).  Each instance owns its
 * own {@link SSLEngine} and associated buffers.
 *
 * <p>The inbound read path uses a zero-copy fast path: encrypted data
 * is read into {@link #netInBuffer}, then unwrapped directly into the
 * next layer's {@link SocketClientLayerContext#inboundTarget()}, avoiding
 * an intermediate application buffer copy.  A slow path through
 * {@link #appInBuffer} handles the rare {@code BUFFER_OVERFLOW} case
 * where the next layer's target is too small for a full TLS record.
 *
 * <p>The outbound write path coalesces multiple application buffers
 * into a single TLS record via
 * {@link SSLEngine#wrap(ByteBuffer[], int, int, ByteBuffer)}, minimizing
 * TLS record overhead and system calls.
 *
 * <p>TLS handshake is driven by the selector calling
 * {@link #processInbound(int)} while {@link #sslHandshaking} is true.
 * The handshake state machine handles {@code NEED_WRAP},
 * {@code NEED_UNWRAP}, and {@code NEED_TASK} transitions, returning
 * appropriate {@link SocketClientInboundResult} variants to the selector.
 */
public final class TlsLayerContext implements SocketClientLayerContext {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    /**
     * The next layer context in the chain (the layer above TLS).
     */
    private final SocketClientLayerContext nextLayer;

    /**
     * The TLS engine for this connection.
     */
    private SSLEngine sslEngine;

    /**
     * Buffer for encrypted data read from the channel.
     * Direct-allocated for efficient channel I/O.
     */
    private ByteBuffer netInBuffer;

    /**
     * Buffer for encrypted data to write to the channel.
     * Direct-allocated for efficient channel I/O.
     */
    private ByteBuffer netOutBuffer;

    /**
     * Buffer for decrypted data overflow.  Used only on the slow path
     * when the next layer's inbound target is too small for a full TLS
     * record.  Heap-allocated for efficient Java consumption.
     */
    private ByteBuffer appInBuffer;

    /**
     * Whether the TLS handshake is currently in progress.
     */
    private boolean sslHandshaking;

    /**
     * Whether delegated TLS tasks are currently running on virtual threads.
     */
    private volatile boolean sslTasksPending;

    /**
     * Whether the TLS handshake has completed successfully.
     */
    private volatile boolean sslHandshakeComplete;

    /**
     * Monitor used to block the thread initiating the TLS handshake
     * until the selector-driven handshake completes.
     */
    private final Object sslHandshakeLock;

    /**
     * Creates a TLS layer context that delegates decoded data to the
     * given next layer.
     *
     * @param nextLayer the layer above TLS in the read pipeline
     */
    public TlsLayerContext(SocketClientLayerContext nextLayer) {
        this.nextLayer = nextLayer;
        this.sslHandshakeLock = new Object();
    }

    /**
     * Initializes the TLS engine and allocates initial buffers.
     *
     * <p>Must be called before registering interest ops for the
     * handshake.  Buffer sizes are based on the engine's session
     * parameters and are resized after handshake completion.
     *
     * @param engine the configured SSL engine
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
     * Resizes SSL buffers after handshake completion.
     *
     * <p>The initial buffer sizes are based on the session's pre-handshake
     * defaults.  After handshake, the negotiated parameters may require
     * different sizes.
     */
    private void resizeSslBuffers() {
        var session = sslEngine.getSession();
        var packetSize = session.getPacketBufferSize();
        var appSize = session.getApplicationBufferSize();

        if (netInBuffer.capacity() < packetSize) {
            var old = netInBuffer;
            netInBuffer = ByteBuffer.allocateDirect(packetSize);
            old.flip();
            netInBuffer.put(old);
        }
        if (netOutBuffer.capacity() < packetSize) {
            netOutBuffer = ByteBuffer.allocateDirect(packetSize);
        }
        if (appInBuffer.capacity() < appSize) {
            appInBuffer = ByteBuffer.allocate(appSize);
        }
    }

    /**
     * Returns the encrypted-data input buffer.
     *
     * <p>The selector reads raw bytes from the channel into this buffer.
     * The TLS layer then unwraps from this buffer into the next layer's
     * target.
     *
     * @return the network input buffer, in write mode
     */
    @Override
    public ByteBuffer inboundTarget() {
        return netInBuffer;
    }

    /**
     * Processes inbound encrypted bytes.
     *
     * <p>During handshake, this method is not used — the selector calls
     * {@link #driveHandshake(SocketChannel)} directly.  After handshake,
     * unwraps encrypted data and delegates decoded bytes to the next layer.
     *
     * @param bytesRead the number of bytes read into the net input buffer,
     *                  or -1 for end-of-stream
     * @return the processing result
     * @throws IOException if a TLS error occurs
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }

        return processDataSsl();
    }

    /**
     * Drives the TLS handshake state machine with direct channel I/O.
     *
     * <p>This method is called by the selector when the TLS handshake is
     * in progress.  Unlike the post-handshake data path which uses the
     * layer context's {@code inboundTarget()} / {@code processInbound()}
     * protocol, the handshake performs channel reads and writes directly
     * to avoid a chicken-and-egg problem: the selector's normal write
     * processing block is unreachable while the handshake interception
     * is active, and the normal read path does not feed
     * {@code netInBuffer} during handshake.
     *
     * <p>Handles all four handshake states:
     * <ul>
     * <li>{@code NEED_WRAP}: wraps handshake data and writes directly to
     *     the channel.  If the channel cannot accept all bytes, returns
     *     {@link SocketClientInboundResult.Buffering} so the selector
     *     re-enables write interest and retries.
     * <li>{@code NEED_UNWRAP}: reads encrypted data from the channel into
     *     {@code netInBuffer}, then unwraps.  On {@code BUFFER_UNDERFLOW},
     *     returns {@link SocketClientInboundResult.Buffering} to wait for
     *     more data.
     * <li>{@code NEED_TASK}: returns
     *     {@link SocketClientInboundResult.Suspended} so the selector
     *     can run delegated tasks on virtual threads.
     * <li>{@code FINISHED} / {@code NOT_HANDSHAKING}: finalizes the
     *     handshake, resizes buffers, and notifies the waiting thread.
     * </ul>
     *
     * @param channel the socket channel for direct I/O
     * @return the result indicating what the selector should do next
     * @throws IOException if a TLS error occurs
     */
    public SocketClientInboundResult driveHandshake(SocketChannel channel) throws IOException {
        // Flush residual encrypted data from a previous partial write
        if (netOutBuffer.position() > 0) {
            netOutBuffer.flip();
            while (netOutBuffer.hasRemaining()) {
                if (channel.write(netOutBuffer) == 0) {
                    netOutBuffer.compact();
                    return new SocketClientInboundResult.Buffering();
                }
            }
            netOutBuffer.compact();
        }

        while (true) {
            switch (sslEngine.getHandshakeStatus()) {
                case NEED_WRAP -> {
                    netOutBuffer.clear();
                    SSLEngineResult result;
                    try {
                        result = sslEngine.wrap(EMPTY_BUFFER, netOutBuffer);
                    } catch (SSLException e) {
                        throw new IOException("TLS handshake wrap failed", e);
                    }
                    netOutBuffer.flip();

                    if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        return new SocketClientInboundResult.Close();
                    }

                    while (netOutBuffer.hasRemaining()) {
                        if (channel.write(netOutBuffer) == 0) {
                            netOutBuffer.compact();
                            return new SocketClientInboundResult.Buffering();
                        }
                    }
                    netOutBuffer.compact();
                }

                case NEED_UNWRAP -> {
                    if (channel.read(netInBuffer) == -1) {
                        return new SocketClientInboundResult.Close();
                    }

                    netInBuffer.flip();
                    SSLEngineResult result;
                    try {
                        result = sslEngine.unwrap(netInBuffer, appInBuffer);
                    } catch (SSLException e) {
                        netInBuffer.compact();
                        throw new IOException("TLS handshake unwrap failed", e);
                    }
                    netInBuffer.compact();

                    switch (result.getStatus()) {
                        case BUFFER_UNDERFLOW -> {
                            return new SocketClientInboundResult.Buffering();
                        }
                        case CLOSED -> {
                            return new SocketClientInboundResult.Close();
                        }
                        default -> {
                            // OK or BUFFER_OVERFLOW (unreachable during handshake)
                        }
                    }
                }

                case NEED_TASK -> {
                    sslTasksPending = true;
                    return new SocketClientInboundResult.Suspended();
                }

                case FINISHED, NOT_HANDSHAKING -> {
                    sslHandshaking = false;
                    sslHandshakeComplete = true;
                    appInBuffer.clear();
                    resizeSslBuffers();
                    synchronized (sslHandshakeLock) {
                        sslHandshakeLock.notifyAll();
                    }
                    return new SocketClientInboundResult.Continue();
                }
            }
        }
    }

    /**
     * Processes inbound TLS data after handshake completion.
     *
     * <p>Unwraps all available TLS records from the network input buffer.
     * Uses the zero-copy fast path when possible, unwrapping directly
     * into the next layer's inbound target.  Falls back to the app-in
     * buffer on {@code BUFFER_OVERFLOW}.
     *
     * @return the result of processing the decoded data through the
     *         next layer
     * @throws IOException if a TLS or layer processing error occurs
     */
    private SocketClientInboundResult processDataSsl() throws IOException {
        // Drain leftover decrypted data from previous unwrap (slow path)
        appInBuffer.flip();
        if (appInBuffer.hasRemaining()) {
            var result = feedNextLayer(appInBuffer);
            appInBuffer.compact();
            if (!(result instanceof SocketClientInboundResult.Continue)) {
                return result;
            }
        } else {
            appInBuffer.compact();
        }

        // Unwrap all available TLS records
        netInBuffer.flip();
        while (netInBuffer.hasRemaining()) {
            // Fast path: unwrap directly into next layer's target
            var target = nextLayer.inboundTarget();
            SSLEngineResult result;
            try {
                result = sslEngine.unwrap(netInBuffer, target);
            } catch (SSLException e) {
                netInBuffer.compact();
                throw new IOException("SSL unwrap failed", e);
            }

            switch (result.getStatus()) {
                case OK -> {
                    if (result.bytesProduced() > 0) {
                        var layerResult = nextLayer.processInbound(result.bytesProduced());
                        if (!(layerResult instanceof SocketClientInboundResult.Continue)) {
                            netInBuffer.compact();
                            return layerResult;
                        }
                    }
                }
                case BUFFER_UNDERFLOW -> {
                    netInBuffer.compact();
                    return new SocketClientInboundResult.Buffering();
                }
                case BUFFER_OVERFLOW -> {
                    // Slow path: target too small, unwrap into appInBuffer
                    try {
                        result = sslEngine.unwrap(netInBuffer, appInBuffer);
                    } catch (SSLException e) {
                        netInBuffer.compact();
                        throw new IOException("SSL unwrap failed", e);
                    }

                    if (result.getStatus() == SSLEngineResult.Status.OK && result.bytesProduced() > 0) {
                        appInBuffer.flip();
                        var layerResult = feedNextLayer(appInBuffer);
                        appInBuffer.compact();
                        if (!(layerResult instanceof SocketClientInboundResult.Continue)) {
                            netInBuffer.compact();
                            return layerResult;
                        }
                    } else if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        netInBuffer.compact();
                        throw new IOException("SSL appInBuffer overflow: buffer sized incorrectly after handshake");
                    }
                }
                case CLOSED -> {
                    netInBuffer.compact();
                    return new SocketClientInboundResult.Close();
                }
            }
        }
        netInBuffer.compact();
        return new SocketClientInboundResult.Continue();
    }

    /**
     * Feeds decoded bytes from a source buffer into the next layer.
     *
     * <p>Delegates to the next layer's
     * {@link SocketClientLayerContext#feedFromSource(ByteBuffer)
     * feedFromSource} method, which handles the copy-and-process loop
     * generically or uses an optimized override (for example,
     * {@code ApplicationLayerContext} avoids intermediate copies by
     * writing directly into its datagram reassembly buffers).
     *
     * @param source the buffer containing decoded bytes, in read mode
     * @return the result of processing
     * @throws IOException if layer processing fails
     */
    private SocketClientInboundResult feedNextLayer(ByteBuffer source) throws IOException {
        return nextLayer.feedFromSource(source);
    }

    /**
     * Runs delegated TLS tasks on virtual threads and resumes the
     * handshake.
     *
     * <p>Called by the selector when it needs to execute pending
     * delegated tasks.  After all tasks complete, the selector should
     * re-enable interest ops and wake up.
     *
     * @param onComplete callback to run after tasks finish (typically
     *                   re-registers interest ops and wakes selector)
     */
    public void runDelegatedTasks(Runnable onComplete) {
        Thread.startVirtualThread(() -> {
            Runnable task;
            while ((task = sslEngine.getDelegatedTask()) != null) {
                task.run();
            }
            sslTasksPending = false;
            onComplete.run();
        });
    }

    /**
     * Wraps outbound application data into TLS records and writes them
     * to the channel.
     *
     * <p>Called by the selector during {@code OP_WRITE} processing.
     * Coalesces multiple application buffers into a single TLS record
     * to minimize overhead.
     *
     * @param channel the socket channel to write to
     * @param buffers the application data buffers
     * @param offset  the offset into the buffers array
     * @param count   the number of buffers to wrap
     * @return {@code true} if all data was written, {@code false} if a
     *         partial write occurred
     * @throws IOException if a TLS or I/O error occurs
     */
    public boolean wrapAndWrite(SocketChannel channel, ByteBuffer[] buffers, int offset, int count) throws IOException {
        // Flush residual encrypted data from a previous partial write
        if (netOutBuffer.position() > 0) {
            netOutBuffer.flip();
            channel.write(netOutBuffer);
            if (netOutBuffer.hasRemaining()) {
                netOutBuffer.compact();
                return false;
            }
            netOutBuffer.compact();
        }

        // Coalesced wrap: pack multiple app buffers into one TLS record
        netOutBuffer.clear();
        SSLEngineResult result;
        try {
            result = sslEngine.wrap(buffers, offset, count, netOutBuffer);
        } catch (SSLException e) {
            throw new IOException("SSL wrap failed", e);
        }

        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw new IOException("SSL netOutBuffer overflow: buffer sized incorrectly after handshake");
        }

        netOutBuffer.flip();
        while (netOutBuffer.hasRemaining()) {
            if (channel.write(netOutBuffer) == 0) {
                netOutBuffer.compact();
                return false;
            }
        }
        netOutBuffer.compact();
        return true;
    }

    /**
     * Returns whether there is encrypted TLS data still buffered for write.
     *
     * @return {@code true} if pending network output exists
     */
    public boolean hasPendingEncryptedOutput() {
        return netOutBuffer != null && netOutBuffer.position() > 0;
    }

    /**
     * Returns whether the TLS handshake is currently in progress.
     *
     * @return {@code true} if handshaking
     */
    public boolean isHandshaking() {
        return sslHandshaking;
    }

    /**
     * Returns whether delegated TLS tasks are pending.
     *
     * @return {@code true} if tasks are running
     */
    public boolean isTasksPending() {
        return sslTasksPending;
    }

    /**
     * Returns the handshake lock monitor.
     *
     * <p>The thread initiating the TLS handshake blocks on this lock
     * until the selector-driven handshake completes.
     *
     * @return the handshake lock object
     */
    public Object handshakeLock() {
        return sslHandshakeLock;
    }

    /**
     * Returns whether the handshake has completed successfully.
     *
     * @return {@code true} if the handshake completed
     */
    public boolean isHandshakeComplete() {
        return sslHandshakeComplete;
    }

    /**
     * Begins the TLS handshake.
     *
     * @throws IOException if the handshake cannot be initiated
     */
    public void beginHandshake() throws IOException {
        sslEngine.beginHandshake();
    }

    /**
     * Drains any leftover decrypted data from the application input buffer
     * into the next layer.
     *
     * <p>After a sequence of synchronous reads through the TLS layer
     * (such as during a WebSocket upgrade handshake), the
     * {@link #appInBuffer} may still contain decrypted bytes that were
     * part of a TLS record but not consumed by the pending read.  This
     * method feeds those bytes into the next layer so they are not lost.
     *
     * @return {@code true} if draining succeeded or there was nothing to
     *         drain, {@code false} if the next layer signalled close
     * @throws IOException if layer processing fails
     */
    public boolean drainToNextLayer() throws IOException {
        if (sslEngine == null || appInBuffer == null) {
            return true;
        }

        appInBuffer.flip();
        if (!appInBuffer.hasRemaining()) {
            appInBuffer.compact();
            return true;
        }

        var result = feedNextLayer(appInBuffer);
        appInBuffer.compact();
        return !(result instanceof SocketClientInboundResult.Close);
    }

    @Override
    public void onDisconnect() {
        if (sslEngine != null) {
            sslEngine.closeOutbound();
        }
        synchronized (sslHandshakeLock) {
            sslHandshakeLock.notifyAll();
        }
        nextLayer.onDisconnect();
    }
}
