package com.github.auties00.cobalt.socket.implementation.tunnel;

import com.github.auties00.cobalt.socket.implementation.SocketClient;
import com.github.auties00.cobalt.socket.implementation.SocketListener;
import com.github.auties00.cobalt.socket.implementation.context.SocketContext;
import com.github.auties00.cobalt.socket.implementation.threading.CentralSelector;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * No tunneling: direct connection to host
 */
public final class DirectSocketClient extends SocketClient {
    @Override
    public void connect(String host, int port, SocketListener listener) throws IOException, InterruptedException {
        connectTunnel(host, port, listener);
        if (!CentralSelector.INSTANCE.markReady(channel)) {
            throw new IOException("Failed to connect: rejected");
        }
    }

    @Override
    public SocketContext connectTunnel(String host, int port, SocketListener listener) throws IOException, InterruptedException {
        var endpoint = new InetSocketAddress(host, port); // Don't resolve this statically
        return super.openConnection(endpoint, listener);
    }
}
