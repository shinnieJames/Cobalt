package com.github.auties00.cobalt.socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Produces an {@link SSLContext} and matching {@link SSLParameters} that
 * mimic Chrome's TLS client hello so JA3-fingerprinting endpoints
 * (including WhatsApp) do not reject the connection.
 *
 * <p>The parameters advertise ALPN {@code http/1.1}, enable HTTPS
 * hostname verification, and pin the cipher suite ordering to the
 * Chrome 147 list (minus GREASE, which the JDK cannot produce, and
 * minus the legacy {@code TLS_RSA_*} suites, which the JDK pins on
 * {@code jdk.tls.disabledAlgorithms} too early to override reliably).
 * The remaining 11 ECDHE + TLS 1.3 ciphers in Chrome's exact order
 * cover what WhatsApp actually negotiates in practice.
 */
final class ChromeSslContextFactory implements WhatsAppSslContextFactory {
    /**
     * Cipher suite ordering captured from a live Chrome 147 instance via
     * {@code https://www.howsmyssl.com/a/check}, with the four
     * deprecated {@code TLS_RSA_*} entries dropped.
     *
     * <p>Chrome's actual hello also offers the four legacy {@code
     * TLS_RSA_WITH_AES_*} suites for backward compatibility with old
     * servers. Modern OpenJDK pins {@code TLS_RSA_*} on the
     * {@code jdk.tls.disabledAlgorithms} list and caches that
     * constraint at JSSE class-init time, before any user code can
     * reset the property reliably (especially in IDE runs with a
     * debugger agent loaded earlier). Dropping them here lets the wire
     * and the configuration agree; WhatsApp servers negotiate ECDHE in
     * practice so the legacy entries are never actually used.
     *
     * <p>Servers that JA3-fingerprint the client hello reject any
     * ordering that does not match a known browser, so the order here
     * is load-bearing and must not be sorted.
     */
    private static final String[] CHROME_CIPHER_SUITES = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"
    };

    /**
     * ALPN protocol identifier advertised inside the client hello.
     */
    private static final String[] ALPN_PROTOCOLS = {"http/1.1"};

    /**
     * Endpoint identification algorithm used for hostname verification
     * against the server certificate.
     */
    private static final String ENDPOINT_IDENTIFICATION_ALGORITHM = "HTTPS";

    /**
     * The shared singleton instance.
     */
    static final ChromeSslContextFactory INSTANCE = new ChromeSslContextFactory();

    /**
     * The shared default TLS context, initialised once at construction.
     */
    private final SSLContext sslContext;

    /**
     * Prevents external instantiation; callers obtain the factory through
     * {@link #INSTANCE}.
     *
     * @throws IllegalStateException if the JDK cannot provide a TLS
     *         {@link SSLContext}
     */
    private ChromeSslContextFactory() {
        try {
            var context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            this.sslContext = context;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create SSL context", e);
        }
    }

    /**
     * Returns the shared TLS context.
     *
     * @return the {@link SSLContext}
     */
    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

    /**
     * Returns a fresh {@link SSLParameters} carrying Chrome's cipher
     * suite ordering, ALPN advertisement and HTTPS endpoint
     * identification.
     *
     * @return the parameters
     */
    @Override
    public SSLParameters sslParameters() {
        var params = new SSLParameters();
        params.setCipherSuites(CHROME_CIPHER_SUITES);
        params.setApplicationProtocols(ALPN_PROTOCOLS);
        params.setEndpointIdentificationAlgorithm(ENDPOINT_IDENTIFICATION_ALGORITHM);
        return params;
    }
}
