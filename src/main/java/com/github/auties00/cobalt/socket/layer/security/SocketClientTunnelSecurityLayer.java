package com.github.auties00.cobalt.socket.layer.security;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.tls.TlsSocketClientSecurityLayer;

public sealed interface SocketClientTunnelSecurityLayer
        extends SocketClientSecurityLayer
        permits TlsSocketClientSecurityLayer {

    static SocketClientTunnelSecurityLayer ofTls(SocketClientLayer innerLayer) {
        return new TlsSocketClientSecurityLayer(innerLayer);
    }
}
