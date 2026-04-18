package com.github.auties00.cobalt.socket.layer.security.impl;

import com.github.auties00.cobalt.socket.layer.security.SocketClientSecurityLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.util.DataUtils;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

/**
 * A layer context that provides TLS encryption and decryption.
 *
 * <p>This context owns an {@link SSLEngine} and the associated net/app
 * buffers. It is positionally polymorphic — the same implementation serves
 * both tunnel-level TLS (client-to-proxy) and transport-level TLS
 * (client-to-target).  The composition of the stack determines which role
 * a particular instance fulfils.
 *
 * <p>The inbound read path uses a zero-copy fast path: encrypted data is
 * read into {@link #netInBuffer}, then unwrapped directly into the next
 * layer's {@link SocketClientLayerContext#inboundTarget()}, avoiding an
 * intermediate application buffer copy.  A slow path through
 * {@link #appInBuffer} handles the rare {@code BUFFER_OVERFLOW} case.
 *
 * <p>The outbound write path coalesces multiple application buffers into
 * a single TLS record via
 * {@link SSLEngine#wrap(ByteBuffer[], int, int, ByteBuffer)}.
 *
 * <p>TLS handshake is driven by the selector calling
 * {@link #driveHandshake(SocketChannel)} while {@link #sslHandshaking} is
 * true.
 */
final class TlsLayerContext implements SocketClientSecurityLayerContext {
    private volatile SocketClientLayerContext nextLayer;
    private volatile SocketClientLayerContext prevLayer;
    private SSLEngine sslEngine;
    private ByteBuffer netInBuffer;
    private ByteBuffer netOutBuffer;
    private ByteBuffer appInBuffer;
    private volatile boolean sslHandshaking;
    private volatile boolean sslTasksPending;
    private volatile boolean sslHandshakeComplete;
    private final Object sslHandshakeLock;
    private volatile SocketChannel channel;

    /**
     * Creates a TLS layer context.
     */
    public TlsLayerContext() {
        this.sslHandshakeLock = new Object();
    }

    @Override
    public void setNextLayer(SocketClientLayerContext next) {
        this.nextLayer = next;
    }

    @Override
    public void setPrevLayer(SocketClientLayerContext prev) {
        this.prevLayer = prev;
    }

    @Override
    public SocketClientLayerContext prevLayer() {
        return prevLayer;
    }

    /**
     * Initializes the TLS engine and allocates initial buffers.
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

    @Override
    public ByteBuffer inboundTarget() {
        return netInBuffer;
    }

    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }
        if (sslHandshaking) {
            // During the handshake bytes are left accumulated in
            // netInBuffer for driveHandshake() to unwrap.  We must not
            // call processDataSsl here because the engine will refuse to
            // unwrap application data while still handshaking, and
            // because nextLayer may not even be registered yet.
            return new SocketClientInboundResult.Buffering();
        }
        return processDataSsl();
    }

    @Override
    public SocketClientInboundResult driveHandshake(SocketChannel channel) throws IOException {
        // Flush any leftover bytes from a previous handshake iteration.
        if (netOutBuffer.position() > 0) {
            netOutBuffer.flip();
            if (!writeHandshakeBytes(channel, netOutBuffer)) {
                netOutBuffer.compact();
                return new SocketClientInboundResult.Buffering();
            }
            netOutBuffer.compact();
        }

        while (true) {
            switch (sslEngine.getHandshakeStatus()) {
                case NEED_WRAP -> {
                    netOutBuffer.clear();
                    SSLEngineResult result;
                    try {
                        result = sslEngine.wrap(DataUtils.EMPTY_BYTE_BUFFER, netOutBuffer);
                    } catch (SSLException e) {
                        throw new IOException("TLS handshake wrap failed", e);
                    }
                    netOutBuffer.flip();

                    if (result.getStatus() == SSLEngineResult.Status.CLOSED) {
                        return new SocketClientInboundResult.Close();
                    }

                    if (!writeHandshakeBytes(channel, netOutBuffer)) {
                        netOutBuffer.compact();
                        return new SocketClientInboundResult.Buffering();
                    }
                    netOutBuffer.compact();
                }

                case NEED_UNWRAP -> {
                    // Bytes are placed into netInBuffer by the normal
                    // inbound chain (selector processRead → outer layers'
                    // unwraps end up here).  If nothing arrived yet, wait.
                    netInBuffer.flip();
                    if (!netInBuffer.hasRemaining()) {
                        netInBuffer.compact();
                        return new SocketClientInboundResult.Buffering();
                    }
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
                    this.channel = channel;
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

    private SocketClientInboundResult processDataSsl() throws IOException {
        if (nextLayer == null) {
            return new SocketClientInboundResult.Buffering();
        }

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

        netInBuffer.flip();
        while (netInBuffer.hasRemaining()) {
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

    private SocketClientInboundResult feedNextLayer(ByteBuffer source) throws IOException {
        return nextLayer.feedFromSource(source);
    }

    /**
     * Writes {@code bytes} (a read-mode view of {@link #netOutBuffer}) to
     * the channel, routing through {@link #prevLayer} so any outer TLS
     * layer below this one also wraps them.  If this is the outermost TLS
     * layer ({@code prevLayer == null}) the bytes go straight to the
     * channel.
     *
     * @return {@code true} if all bytes were written; {@code false} if
     *         the write was partial (caller should buffer and retry)
     */
    private boolean writeHandshakeBytes(SocketChannel channel, ByteBuffer bytes) throws IOException {
        var prev = prevLayer;
        if (prev != null) {
            return prev.processOutbound(channel, new ByteBuffer[]{bytes}, 0, 1);
        }
        while (bytes.hasRemaining()) {
            if (channel.write(bytes) == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
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
     * @param channel the socket channel to write to
     * @param buffers the application data buffers
     * @param offset  the offset into the buffers array
     * @param count   the number of buffers to wrap
     * @return {@code true} if all data was written
     * @throws IOException if a TLS or I/O error occurs
     */
    public boolean wrapAndWrite(SocketChannel channel, ByteBuffer[] buffers, int offset, int count) throws IOException {
        if (netOutBuffer.position() > 0) {
            netOutBuffer.flip();
            channel.write(netOutBuffer);
            if (netOutBuffer.hasRemaining()) {
                netOutBuffer.compact();
                return false;
            }
            netOutBuffer.compact();
        }

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
     * Wraps outbound application data into a TLS record without writing
     * to a channel.
     *
     * @param buffers the application data buffers
     * @param offset  the offset into the buffers array
     * @param count   the number of buffers to wrap
     * @return the encrypted buffer in read mode, or {@code null} if
     *         nothing was produced
     * @throws IOException if a TLS error occurs
     */
    public ByteBuffer wrapToBuffer(ByteBuffer[] buffers, int offset, int count) throws IOException {
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
        return netOutBuffer.hasRemaining() ? netOutBuffer.asReadOnlyBuffer() : null;
    }

    @Override
    public boolean hasPendingOutput() {
        return netOutBuffer != null && netOutBuffer.position() > 0;
    }

    @Override
    public boolean isHandshaking() {
        return sslHandshaking;
    }

    @Override
    public boolean isTasksPending() {
        return sslTasksPending;
    }

    @Override
    public Object handshakeLock() {
        return sslHandshakeLock;
    }

    @Override
    public boolean isHandshakeComplete() {
        return sslHandshakeComplete;
    }

    @Override
    public void beginHandshake() throws IOException {
        sslEngine.beginHandshake();
    }

    @Override
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
    public boolean processOutbound(SocketChannel channel, ByteBuffer[] buffers, int offset, int count) throws IOException {
        var prev = prevLayer;
        if (prev != null) {
            var wrapped = wrapToBuffer(buffers, offset, count);
            if (wrapped == null) {
                return false;
            }
            return prev.processOutbound(channel, new ByteBuffer[]{wrapped}, 0, 1);
        }
        return wrapAndWrite(channel, buffers, offset, count);
    }

    @Override
    public void onDisconnect() {
        if (sslEngine != null) {
            sslEngine.closeOutbound();
            sendCloseNotify();
        }
        synchronized (sslHandshakeLock) {
            sslHandshakeLock.notifyAll();
        }
        if (nextLayer != null) {
            nextLayer.onDisconnect();
        }
    }

    private void sendCloseNotify() {
        var ch = this.channel;
        if (ch == null || !ch.isOpen()) {
            return;
        }
        try {
            netOutBuffer.clear();
            sslEngine.wrap(DataUtils.EMPTY_BYTE_BUFFER, netOutBuffer);
            netOutBuffer.flip();
            while (netOutBuffer.hasRemaining()) {
                if (ch.write(netOutBuffer) == 0) {
                    break;
                }
            }
        } catch (ClosedChannelException _) {
        } catch (IOException _) {
        }
    }
}
