package com.github.auties00.cobalt.socket.client.tcp;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.transport.tcp.TcpSocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public final class TcpSocketClient {
    private final TcpSocketClientTransport transport;

    private TcpSocketClient(TcpSocketClientTransport transport) {
        this.transport = transport;
    }

    public static TcpSocketClient newSocketClient(WhatsAppClientProxy proxy) {
        var tunnel = TcpSocketClientTransport.newSocketClientTunnel(proxy);
        return new TcpSocketClient(tunnel);
    }

    public void connect(InetSocketAddress address, TcpSocketClientListener listener) throws IOException, InterruptedException {
        transport.connect(address, listener);
    }

    public void disconnect() throws IOException {
        transport.disconnect();
    }

    public void sendBinary(ByteBuffer... buffers) {
        transport.sendBinary(buffers);
    }

    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return transport.readBinary(buffer, fully);
    }

    public boolean isConnected() {
        return transport.isConnected();
    }
}
