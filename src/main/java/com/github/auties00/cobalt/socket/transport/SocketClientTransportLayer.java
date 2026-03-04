package com.github.auties00.cobalt.socket.transport;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.transport.tcp.TcpSocketClientTransportLayer;

public sealed interface SocketClientTransportLayer
        extends SocketClientLayer
        permits TcpSocketClientTransportLayer {
    static SocketClientTransportLayer ofTcp() {
        return new TcpSocketClientTransportLayer();
    }
}
