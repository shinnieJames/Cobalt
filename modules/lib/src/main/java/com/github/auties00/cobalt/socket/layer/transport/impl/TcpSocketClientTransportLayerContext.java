package com.github.auties00.cobalt.socket.layer.transport.impl;

import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * TCP implementation of {@link SocketClientTransportLayerContext}.
 *
 * <p>The context only owns the 16 KiB inbound buffer and its chain link
 * to the next layer.  Per-connection state (pending writes, connection
 * lock, connected flag) lives on the selector's {@code AttachmentData}.
 */
final class TcpSocketClientTransportLayerContext implements SocketClientTransportLayerContext {
    /**
     * The buffer into which raw bytes are read from the channel.
     *
     * <p>This is the very first buffer in the inbound processing chain.
     * The selector reads channel data into this buffer and then
     * {@link #processInbound(int)} propagates the data to the next layer.
     */
    private final ByteBuffer inboundBuffer = ByteBuffer.allocateDirect(16384);

    /**
     * The next layer context in the inbound processing chain.
     *
     * <p>Set by {@link #setNextLayer(SocketClientLayerContext)} when the
     * chain is extended.  May be {@code null} during early connection
     * setup before any upper layer has been registered.
     */
    private volatile SocketClientLayerContext nextLayer;

    /**
     * Creates a transport layer context for a new connection.
     */
    TcpSocketClientTransportLayerContext() {
    }

    @Override
    public ByteBuffer inboundTarget() {
        return inboundBuffer;
    }

    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }
        if (nextLayer == null) {
            return new SocketClientInboundResult.Buffering();
        }
        inboundBuffer.flip();
        var result = nextLayer.feedFromSource(inboundBuffer);
        inboundBuffer.compact();
        return result;
    }

    @Override
    public void setNextLayer(SocketClientLayerContext next) {
        this.nextLayer = next;
    }

    @Override
    public void onDisconnect() {
        if (nextLayer != null) {
            nextLayer.onDisconnect();
        }
    }
}
