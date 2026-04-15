package com.github.auties00.cobalt.socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A {@link WhatsAppSslEngineFactory} that creates Chrome-like
 * {@link SSLEngine} instances with JA3 fingerprint matching.
 *
 * <p>The engine is configured with ALPN ({@code http/1.1}), HTTPS
 * hostname verification, and Chrome's cipher suite ordering.
 */
final class ChromeSslEngineFactory implements WhatsAppSslEngineFactory {
    /**
     * Chrome cipher suite ordering extracted from a live Chrome 136 instance.
     *
     * <p>Servers that perform JA3 TLS fingerprinting (including WhatsApp's
     * infrastructure) reject connections whose cipher suite ordering does
     * not match a known browser.  This list reproduces Chrome's exact
     * ordering, omitting the {@code TLS_GREASE} entries that the JDK
     * does not support.
     *
     * @implNote Extracted via {@code https://www.howsmyssl.com/a/check}.
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
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA"
    };

    static final ChromeSslEngineFactory INSTANCE = new ChromeSslEngineFactory();

    static {
        // Java 25 disables TLS_RSA_* cipher suites by default (no forward secrecy).
        // Chrome still offers them, so we must re-enable them to match Chrome's JA3 fingerprint.
        var disabled = Security.getProperty("jdk.tls.disabledAlgorithms");
        if (disabled != null && disabled.contains("TLS_RSA_*")) {
            var updated = Arrays.stream(disabled.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.equals("TLS_RSA_*"))
                    .collect(Collectors.joining(", "));
            Security.setProperty("jdk.tls.disabledAlgorithms", updated);
        }

    }

    private ChromeSslEngineFactory() {

    }

    @Override
    public SSLEngine createSSLEngine(InetSocketAddress address) {
        try {
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            var engine = sslContext.createSSLEngine(address.getHostString(), address.getPort());
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            params.setApplicationProtocols(new String[]{"http/1.1"});
            params.setCipherSuites(CHROME_CIPHER_SUITES);
            engine.setSSLParameters(params);
            return engine;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("Failed to create SSL context", e);
        }
    }
}
