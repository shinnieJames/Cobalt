package com.github.auties00.cobalt.socket.security;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.security.tls.TlsSocketClientSecurityLayer;

public sealed interface SocketClientTunnelSecurityLayer
        extends SocketClientSecurityLayer
        permits TlsSocketClientSecurityLayer {

    static SocketClientTunnelSecurityLayer ofTls(SocketClientLayer innerLayer) {
        return new TlsSocketClientSecurityLayer(innerLayer);
    }
}
