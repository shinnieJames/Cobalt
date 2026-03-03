package com.github.auties00.cobalt.socket.implementation.tunnel;

import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.transport.SocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * No tunneling: direct connection to host
 */
final class DirectSocketClientTunnel extends SocketClientTunnel {
    DirectSocketClientTunnel(SocketClientTransport transport) {
        super(transport);
    }

    @Override
    public void connect(InetSocketAddress endpoint, SocketClientListener listener) throws IOException, InterruptedException {
        transport.connect(endpoint, listener);
    }
}
