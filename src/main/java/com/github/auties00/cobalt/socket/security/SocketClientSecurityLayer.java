package com.github.auties00.cobalt.socket.security;

import com.github.auties00.cobalt.socket.SocketClientLayer;

import java.io.IOException;

public sealed interface SocketClientSecurityLayer extends SocketClientLayer
        permits SocketClientTransportSecurityLayer, SocketClientTunnelSecurityLayer {
    void startHandshake() throws IOException;
}
