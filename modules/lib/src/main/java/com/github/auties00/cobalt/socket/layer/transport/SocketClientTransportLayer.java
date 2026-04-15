package com.github.auties00.cobalt.socket.layer.transport;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.transport.impl.tcp.TcpSocketClientTransportLayer;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

public sealed interface SocketClientTransportLayer
        extends SocketClientLayer<SocketClientLayerContext>
        permits TcpSocketClientTransportLayer {
    static SocketClientTransportLayer newTcpTransport() {
        return new TcpSocketClientTransportLayer();
    }
}
