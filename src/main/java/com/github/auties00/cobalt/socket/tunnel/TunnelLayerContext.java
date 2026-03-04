package com.github.auties00.cobalt.socket.tunnel;

import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayerContext;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A layer context that handles the pre-tunnel and post-tunnel phases of
 * a proxied connection.
 *
 * <p>Before the proxy handshake completes ({@code tunnelled = false}),
 * this context handles blocking reads posted by the proxy handshake
 * thread.  After the handshake ({@code tunnelled = true}), it becomes
 * a pure passthrough to the next layer above.
 *
 * <p>For direct connections (no proxy), this context starts with
 * {@code tunnelled = true} and always delegates to the next layer.
 */
public final class TunnelLayerContext implements SocketClientLayerContext {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    /**
     * The next layer context in the chain (above tunnel).
     */
    private final SocketClientLayerContext nextLayer;

    /**
     * Whether the tunnel has been established.
     *
     * <p>Transitions from {@code false} to {@code true} exactly once
     * after the proxy handshake succeeds.  Once {@code true}, never
     * reverts.  Read and written exclusively by the selector thread.
     */
    private boolean tunnelled;

    /**
     * The pending binary read request, or {@code null} if no read is
     * in progress.
     *
     * <p>Used only during the pre-tunnel phase.  Written by the thread
     * calling {@code readBinary}; read and cleared by the selector thread.
     */
    private volatile SocketClientTransportLayerContext.PendingRead pendingBinaryRead;

    /**
     * Creates a tunnel layer context.
     *
     * @param nextLayer the layer above in the read pipeline
     * @param directConnection {@code true} if no proxy is used (tunnel
     *                         is immediately established)
     */
    public TunnelLayerContext(SocketClientLayerContext nextLayer, boolean directConnection) {
        this.nextLayer = nextLayer;
        this.tunnelled = directConnection;
    }

    /**
     * Returns the appropriate inbound target based on the tunnel phase.
     *
     * <p>Pre-tunnel: returns the pending read's destination buffer.
     * Post-tunnel: delegates to the next layer's inbound target.
     *
     * @return the buffer to read into
     */
    @Override
    public ByteBuffer inboundTarget() {
        if (tunnelled) {
            return nextLayer.inboundTarget();
        } else {
            var read = pendingBinaryRead;
            if (read != null) {
                return read.buffer;
            } else {
                return EMPTY_BUFFER;
            }
        }
    }

    /**
     * Processes inbound bytes based on the tunnel phase.
     *
     * <p>Pre-tunnel: updates the pending read request and notifies the
     * waiting proxy handshake thread.
     * Post-tunnel: delegates to the next layer.
     *
     * @param bytesRead the number of bytes read, or -1 for end-of-stream
     * @return the processing result
     * @throws IOException if layer processing fails
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (!tunnelled) {
            return processPreTunnelRead(bytesRead);
        }
        return nextLayer.processInbound(bytesRead);
    }

    /**
     * Processes a pre-tunnel read completion.
     *
     * @param bytesRead the number of bytes read, or -1 for EOS
     * @return the processing result
     */
    private SocketClientInboundResult processPreTunnelRead(int bytesRead) {
        var pendingRead = pendingBinaryRead;
        if (pendingRead == null) {
            return new SocketClientInboundResult.Buffering();
        }

        if (bytesRead == -1) {
            pendingRead.length = -1;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
            return new SocketClientInboundResult.Close();
        }

        if (bytesRead == 0) {
            return new SocketClientInboundResult.Buffering();
        }

        if (pendingRead.length == -1) {
            pendingRead.length = 0;
        }
        pendingRead.length += bytesRead;

        if (!pendingRead.fullRead || !pendingRead.buffer.hasRemaining()) {
            pendingBinaryRead = null;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
        }

        return new SocketClientInboundResult.Continue();
    }

    /**
     * Sets the pending binary read request.
     *
     * <p>Called by the proxy handshake thread to post a read request
     * that the selector will fulfill.
     *
     * @param read the pending read request
     * @return {@code true} if the read was posted, {@code false} if
     *         another read is already pending
     */
    public boolean setPendingRead(SocketClientTransportLayerContext.PendingRead read) {
        if (pendingBinaryRead != null) {
            return false;
        }
        pendingBinaryRead = read;
        return true;
    }

    /**
     * Marks the tunnel as established.
     *
     * <p>After this call, this context becomes a pure passthrough to
     * the next layer.
     */
    public void markTunnelled() {
        this.tunnelled = true;
    }

    /**
     * Returns whether the tunnel has been established.
     *
     * @return {@code true} if tunnelled
     */
    public boolean isTunnelled() {
        return tunnelled;
    }

    @Override
    public void onDisconnect() {
        var pendingRead = pendingBinaryRead;
        if (pendingRead != null) {
            pendingRead.length = -1;
            pendingBinaryRead = null;
            synchronized (pendingRead.lock) {
                pendingRead.lock.notifyAll();
            }
        }
        nextLayer.onDisconnect();
    }
}
