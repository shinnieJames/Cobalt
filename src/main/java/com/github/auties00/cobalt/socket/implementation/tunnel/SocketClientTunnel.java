package com.github.auties00.cobalt.socket.implementation.tunnel;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.transport.SocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public sealed abstract class SocketClientTunnel permits DirectSocketClientTunnel, HttpSocketClientTunnel, SocksSocketClientTunnel {
    final SocketClientTransport transport;

    SocketClientTunnel(SocketClientTransport transport) {
        this.transport = transport;
    }

    public static SocketClientTunnel newSocketClientTunnel(WhatsAppClientProxy proxy) {
        var transport = SocketClientTransport.newSocketClientTransport();
        return switch (proxy) {
            case WhatsAppClientProxy.Http http -> new HttpSocketClientTunnel(transport, http);
            case WhatsAppClientProxy.Socks socks -> new SocksSocketClientTunnel(transport, socks);
            case null -> new DirectSocketClientTunnel(transport);
        };
    }

    public abstract void connect(InetSocketAddress endpoint, SocketClientListener listener) throws IOException, InterruptedException;

    public void disconnect() throws IOException {
        transport.disconnect();
    }

    public void sendBinary(ByteBuffer... buffers) {
        transport.sendBinary(buffers);
    }

    public boolean isConnected() {
        return transport.isConnected();
    }

    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return transport.readBinary(buffer, fully);
    }
}
