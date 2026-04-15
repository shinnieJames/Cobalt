package com.github.auties00.cobalt.socket.layer.security.impl.transport.tls;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.TlsSocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.security.impl.TlsSocketClientSecurityLayer;
import com.github.auties00.cobalt.socket.WhatsAppSslEngineFactory;

/**
 * Transport-level TLS security layer implementation.
 */
public final class TransportTlsSecurityLayer extends TlsSocketClientSecurityLayer implements SocketClientTransportSecurityLayer {
    public TransportTlsSecurityLayer(SocketClientLayer<?> innerLayer, WhatsAppSslEngineFactory engineFactory) {
        super(innerLayer, engineFactory);
    }

    @Override
    protected TlsSocketClientLayerContext createLayerContext() {
        return new TransportTlsLayerContext();
    }
}
