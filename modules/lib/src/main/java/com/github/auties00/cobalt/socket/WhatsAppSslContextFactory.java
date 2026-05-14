package com.github.auties00.cobalt.socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Produces the {@link SSLContext} and {@link SSLParameters} that the
 * Cobalt socket stack and the JDK {@link java.net.http.HttpClient} use
 * for outbound WhatsApp connections.
 *
 * <p>The factory abstraction lets the socket layer pick a Chrome-like
 * context for endpoints behind JA3 fingerprinting and a stock JDK
 * context for ordinary peers without leaking the choice into the caller.
 * The split into two methods mirrors the JDK contracts: the
 * {@link SSLContext} is reusable across hosts, while
 * {@link SSLParameters} is applied per-call to either
 * {@link javax.net.ssl.SSLSocket#setSSLParameters(SSLParameters)} or
 * {@link java.net.http.HttpClient.Builder#sslParameters(SSLParameters)}.
 */
public interface WhatsAppSslContextFactory {
    /**
     * Returns the {@link SSLContext} backing this factory.
     *
     * <p>The same context may be returned across invocations; callers
     * must not mutate it.
     *
     * @return a configured {@link SSLContext}
     */
    SSLContext sslContext();

    /**
     * Returns the {@link SSLParameters} to apply to an outbound TLS
     * session.
     *
     * <p>A fresh instance is returned on every invocation so callers may
     * mutate it (typically to set SNI server names) without affecting
     * other connections.
     *
     * @return the parameters carrying the cipher suite ordering, ALPN
     *         protocols and endpoint identification algorithm
     */
    SSLParameters sslParameters();

    /**
     * Returns the default Chrome-like factory that reproduces the cipher
     * suite ordering Chrome advertises so JA3-fingerprinting endpoints
     * accept the connection.
     *
     * @return the singleton Chrome-style factory
     */
    static WhatsAppSslContextFactory chrome() {
        return ChromeSslContextFactory.INSTANCE;
    }
}
