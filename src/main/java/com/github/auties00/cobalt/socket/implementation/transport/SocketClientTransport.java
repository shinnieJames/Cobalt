package com.github.auties00.cobalt.socket.implementation.transport;

import com.github.auties00.cobalt.socket.implementation.SocketClientListener;
import com.github.auties00.cobalt.socket.implementation.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.implementation.transport.tcp.TCPSocketClientTransport;
import com.github.auties00.cobalt.socket.implementation.transport.websocket.WebSocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public sealed interface SocketClientTransport permits TCPSocketClientTransport, WebSocketClientTransport {
    static SocketClientTransport newSocketClientTransport() {
        // We don't really need UDP support right now
        return new TCPSocketClientTransport();
    }

    AbstractSocketClientContext connect(InetSocketAddress endpoint, SocketClientListener listener) throws IOException, InterruptedException;
    void disconnect() throws IOException;
    void sendBinary(ByteBuffer... buffers);
    boolean isConnected();
    int readBinary(ByteBuffer buffer, boolean fully) throws IOException;
}
