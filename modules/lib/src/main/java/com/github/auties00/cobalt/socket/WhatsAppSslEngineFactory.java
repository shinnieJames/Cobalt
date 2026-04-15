package com.github.auties00.cobalt.socket;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;

/**
 * Factory for creating {@link SSLEngine} instances for WhatsApp
 * socket connections.
 */
@FunctionalInterface
public interface WhatsAppSslEngineFactory {
    /**
     * Creates an {@link SSLEngine} for the given peer address.
     *
     * @param address the peer address for hostname verification
     * @return a configured {@link SSLEngine} in client mode
     */
    SSLEngine createSSLEngine(InetSocketAddress address);

    /**
     * Returns the default Chrome-like factory that matches WhatsApp's
     * JA3 TLS fingerprinting.
     *
     * @return the singleton web SSL engine factory
     */
    static WhatsAppSslEngineFactory chrome() {
        return ChromeSslEngineFactory.INSTANCE;
    }
}
