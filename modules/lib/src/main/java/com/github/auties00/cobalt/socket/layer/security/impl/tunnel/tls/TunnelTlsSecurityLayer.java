package com.github.auties00.cobalt.socket.layer.security.impl.tunnel.tls;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTunnelSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.TlsSocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.security.impl.TlsSocketClientSecurityLayer;
import com.github.auties00.cobalt.socket.WhatsAppSslEngineFactory;

/**
 * Tunnel-level TLS security layer implementation.
 */
public final class TunnelTlsSecurityLayer extends TlsSocketClientSecurityLayer implements SocketClientTunnelSecurityLayer {
    public TunnelTlsSecurityLayer(SocketClientLayer<?> innerLayer, WhatsAppSslEngineFactory engineFactory) {
        super(innerLayer, engineFactory);
    }

    @Override
    protected TlsSocketClientLayerContext createLayerContext() {
        return new TunnelTlsLayerContext();
    }
}
