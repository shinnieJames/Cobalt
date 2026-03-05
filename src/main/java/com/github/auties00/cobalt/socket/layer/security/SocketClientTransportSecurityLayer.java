package com.github.auties00.cobalt.socket.layer.security;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.security.tls.TlsSocketClientSecurityLayer;

import java.util.Objects;

/**
 * A transport-level security layer that provides TLS encryption.
 */
public sealed interface SocketClientTransportSecurityLayer
        extends SocketClientSecurityLayer
        permits TlsSocketClientSecurityLayer {
    /**
     * Creates a TLS transport security layer wrapping the given transport layer.
     *
     * @param transportLayer the layer below TLS
     * @return a new TLS security layer
     */
    static SocketClientTransportSecurityLayer ofTls(SocketClientLayer transportLayer) {
        Objects.requireNonNull(transportLayer, "transportLayer cannot be null");
        return new TlsSocketClientSecurityLayer(transportLayer);
    }
}
