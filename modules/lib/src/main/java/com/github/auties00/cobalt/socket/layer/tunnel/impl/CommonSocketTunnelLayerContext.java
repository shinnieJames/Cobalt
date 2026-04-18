package com.github.auties00.cobalt.socket.layer.tunnel.impl;

import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingRead;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.util.DataUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Default implementation of {@link SocketClientTunnelLayerContext} that handles
 * the pre-tunnel and post-tunnel phases of a connection.
 *
 * <p>Before the connection is fully established ({@code tunnelled = false}),
 * this context handles blocking reads posted by handshake threads (proxy
 * handshake, WebSocket upgrade, etc.).  After the connection transitions
 * to asynchronous mode ({@code tunnelled = true}), it becomes a pure
 * passthrough to the next layer above.
 */
final class CommonSocketTunnelLayerContext implements SocketClientTunnelLayerContext {
    /**
     * The next layer context in the chain (above tunnel).
     * Set via {@link #setNextLayer(SocketClientLayerContext)} by auto-chaining.
     */
    private volatile SocketClientLayerContext nextLayer;

    /**
     * The previous layer context in the chain (below tunnel).
     * Set via {@link #setPrevLayer(SocketClientLayerContext)} by auto-chaining.
     */
    private volatile SocketClientLayerContext prevLayer;

    /**
     * Whether the tunnel has been established.
     *
     * <p>Transitions from {@code false} to {@code true} exactly once
     * after the handshake phase succeeds.  Once {@code true}, never
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
    private volatile SocketClientPendingRead pendingBinaryRead;

    /**
     * Creates a tunnel layer context in the pre-tunnel (not yet
     * tunnelled) state.
     */
    public CommonSocketTunnelLayerContext() {
        this.tunnelled = false;
    }

    /**
     * Sets the next layer context in the inbound processing chain.
     *
     * @param next the next layer context
     */
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
     * Returns the inbound target buffer.
     *
     * <p>When this tunnel is currently serving a proxy-handshake pending
     * read, returns that read's buffer directly.  Otherwise delegates to
     * the next layer so post-handshake bytes flow through to the next
     * protocol layer (for example a TLS layer above the tunnel).
     *
     * @return the buffer to read into
     */
    @Override
    public ByteBuffer inboundTarget() {
        var read = pendingBinaryRead;
        if (read != null) {
            return read.buffer;
        }
        var next = nextLayer;
        return next != null ? next.inboundTarget() : DataUtils.EMPTY_BYTE_BUFFER;
    }

    /**
     * Processes inbound bytes.
     *
     * <p>When this tunnel is currently serving a proxy-handshake pending
     * read, updates that read and notifies the waiting handshake thread.
     * Otherwise delegates to the next layer so bytes continue up the chain.
     *
     * @param bytesRead the number of bytes read, or -1 for end-of-stream
     * @return the processing result
     * @throws IOException if layer processing fails
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (pendingBinaryRead != null) {
            return processPreTunnelRead(bytesRead);
        }
        var next = nextLayer;
        if (next == null) {
            return new SocketClientInboundResult.Buffering();
        }
        return next.processInbound(bytesRead);
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
     * <p>Refused if another read is already pending or if this tunnel
     * has already been marked tunnelled (meaning any future handshake
     * reads belong to a higher layer, not to the proxy).
     *
     * @param read the pending read request
     * @return {@code true} if the read was accepted
     */
    @Override
    public boolean setPendingRead(SocketClientPendingRead read) {
        if (tunnelled || pendingBinaryRead != null) {
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
    @Override
    public void markTunnelled() {
        this.tunnelled = true;
    }

    /**
     * Returns whether the tunnel has been established.
     *
     * @return {@code true} if tunnelled
     */
    @Override
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
        var next = nextLayer;
        if (next != null) {
            next.onDisconnect();
        }
    }
}
