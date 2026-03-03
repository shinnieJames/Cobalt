package com.github.auties00.cobalt.socket.transport;

import com.github.auties00.cobalt.socket.client.SocketClientListener;
import com.github.auties00.cobalt.socket.context.AbstractSocketClientContext;
import com.github.auties00.cobalt.socket.transport.tcp.TcpSocketClientTransport;
import com.github.auties00.cobalt.socket.transport.websocket.WebSocketClientTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public sealed interface SocketClientTransport<LISTENER extends SocketClientListener> permits TcpSocketClientTransport, WebSocketClientTransport {
    AbstractSocketClientContext connect(InetSocketAddress endpoint, LISTENER listener) throws IOException, InterruptedException;
    void disconnect() throws IOException;
    void sendBinary(ByteBuffer... buffers);
    boolean isConnected();
    int readBinary(ByteBuffer buffer, boolean fully) throws IOException;
}
