package com.github.auties00.cobalt.socket.transport.tcp;

import com.github.auties00.cobalt.socket.client.tcp.TcpSocketClientListener;
import com.github.auties00.cobalt.socket.context.ssl.AbstractSSLSocketClientContext;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class TcpSocketClientContext extends AbstractSSLSocketClientContext {
    /**
     * Socket client listener.
     */
    public final TcpSocketClientListener socketClientListener;

    /**
     * Creates a context for a new connection.
     *
     * @param socketClientListener  the callback to receive completed inbound datagrams
     */
    public TcpSocketClientContext(TcpSocketClientListener socketClientListener) {
        this.socketClientListener = Objects.requireNonNull(socketClientListener, "socketClientListener cannot be null");
    }

    @Override
    public void onDatagram(ByteBuffer datagram) {
        socketClientListener.onDatagram(datagram);
    }

    @Override
    public void onClose() {
        socketClientListener.onClose();
    }
}
