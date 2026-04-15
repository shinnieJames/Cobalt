package com.github.auties00.cobalt.socket.layer.security;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.transport.plain.TransportPlainSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.impl.transport.tls.TransportTlsSecurityLayer;
import com.github.auties00.cobalt.socket.WhatsAppSslEngineFactory;

import java.util.Objects;

/**
 * A transport-level security layer that provides optional TLS encryption.
 */
public non-sealed interface SocketClientTransportSecurityLayer
        extends SocketClientSecurityLayer<SocketClientTransportSecurityLayerContext> {

    static SocketClientTransportSecurityLayer newTlsTransport(SocketClientLayer<?> transportLayer, WhatsAppSslEngineFactory engineFactory) {
        Objects.requireNonNull(transportLayer, "transportLayer cannot be null");
        Objects.requireNonNull(engineFactory, "engineFactory cannot be null");
        return new TransportTlsSecurityLayer(transportLayer, engineFactory);
    }

    static SocketClientTransportSecurityLayer newPlainTransport(SocketClientLayer<?> transportLayer) {
        Objects.requireNonNull(transportLayer, "transportLayer cannot be null");
        return new TransportPlainSecurityLayer(transportLayer);
    }
}
