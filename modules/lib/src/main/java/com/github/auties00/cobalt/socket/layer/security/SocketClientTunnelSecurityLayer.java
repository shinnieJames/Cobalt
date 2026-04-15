package com.github.auties00.cobalt.socket.layer.security;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.tunnel.plain.TunnelPlainSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.tunnel.tls.TunnelTlsSecurityLayer;
import com.github.auties00.cobalt.socket.WhatsAppSslEngineFactory;

import java.util.Objects;

/**
 * A tunnel-level security layer that provides optional TLS encryption
 * over an established proxy tunnel.
 */
public non-sealed interface SocketClientTunnelSecurityLayer
        extends SocketClientSecurityLayer<SocketClientTunnelSecurityLayerContext> {

    static SocketClientTunnelSecurityLayer newTlsTunnel(SocketClientLayer<?> innerLayer, WhatsAppSslEngineFactory engineFactory) {
        Objects.requireNonNull(innerLayer, "innerLayer cannot be null");
        Objects.requireNonNull(engineFactory, "engineFactory cannot be null");
        return new TunnelTlsSecurityLayer(innerLayer, engineFactory);
    }

    static SocketClientTunnelSecurityLayer newPlainTunnel(SocketClientLayer<?> innerLayer) {
        Objects.requireNonNull(innerLayer, "innerLayer cannot be null");
        return new TunnelPlainSecurityLayer(innerLayer);
    }
}
