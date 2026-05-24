package com.github.auties00.cobalt.socket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import javax.net.ssl.SSLSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@link WhatsAppSslContextFactory} TLS
 * configuration reproduces a real Chrome client's TLS fingerprint as
 * seen by {@code howsmyssl.com}.
 *
 * @apiNote
 * Exercises the JA3-evasion claim of
 * {@link WhatsAppSslContextFactory#chrome()}: every cipher suite,
 * TLS version flag and capability flag returned by
 * {@code howsmyssl.com/a/check} must match between a real Chrome
 * browser and an {@link SSLSocket} configured with the factory's
 * parameters. A divergence means WhatsApp's edge would distinguish
 * Cobalt from a browser and apply a different policy.
 *
 * @implNote
 * This implementation drives a headless Chrome via Selenium to
 * capture the baseline fingerprint, then opens a direct
 * {@link SSLSocket} configured with the factory to capture the
 * Cobalt-side fingerprint, and asserts they match. Pseudo-cipher
 * entries that differ structurally between Chrome and the JDK
 * (GREASE on the Chrome side, SCSV on the JDK side, legacy
 * {@code TLS_RSA_*} suites pinned by {@code jdk.tls.disabledAlgorithms})
 * are filtered out of both sides before comparison.
 */
@Timeout(120)
class WhatsAppSslContextFactoryTest {
    /**
     * Ensures the SSL factory has loaded before the test runs.
     *
     * @apiNote
     * Touching the singleton in a {@code BeforeAll} forces its
     * class-init to run before any test method executes; otherwise
     * an initialisation failure would surface only inside the first
     * test that touched it.
     */
    @BeforeAll
    static void ensureSslFactoryLoaded() {
        //noinspection ResultOfMethodCallIgnored
        WhatsAppSslContextFactory.chrome();
    }

    /**
     * The URL of the howsmyssl.com fingerprinting endpoint.
     */
    private static final String CHECK_URL = "https://www.howsmyssl.com/a/check";

    /**
     * The host of the fingerprinting endpoint, used for direct
     * {@link SSLSocket} construction.
     */
    private static final String CHECK_HOST = "www.howsmyssl.com";

    /**
     * The port of the fingerprinting endpoint, used for direct
     * {@link SSLSocket} construction.
     */
    private static final int CHECK_PORT = 443;

    /**
     * The HTTP/1.0 GET request sent over the direct
     * {@link SSLSocket} so the server returns its JSON fingerprint
     * report.
     */
    private static final byte[] HTTP_REQUEST = (
            "GET /a/check HTTP/1.0\r\n" +
            "Host: www.howsmyssl.com\r\n" +
            "Accept: application/json\r\n" +
            "\r\n"
    ).getBytes(StandardCharsets.US_ASCII);

    /**
     * The internal TLS fingerprint matches Chrome's reported
     * fingerprint on every meaningful field.
     */
    @Test
    void testTlsFingerprintMatchesChrome() throws Exception {
        var internalFingerprint = getInternalFingerprint();
        var chromeFingerprint = getChromeFingerprint();

        var chromeCiphers = filterPseudoCiphers(chromeFingerprint.getJSONArray("given_cipher_suites"));
        var internalCiphers = filterPseudoCiphers(internalFingerprint.getJSONArray("given_cipher_suites"));
        assertEquals(chromeCiphers, internalCiphers, "given_cipher_suites should match Chrome");

        assertEquals(
                chromeFingerprint.getString("tls_version"),
                internalFingerprint.getString("tls_version"),
                "tls_version should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("ephemeral_keys_supported"),
                internalFingerprint.getBoolean("ephemeral_keys_supported"),
                "ephemeral_keys_supported should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("session_ticket_supported"),
                internalFingerprint.getBoolean("session_ticket_supported"),
                "session_ticket_supported should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("tls_compression_supported"),
                internalFingerprint.getBoolean("tls_compression_supported"),
                "tls_compression_supported should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("unknown_cipher_suite_supported"),
                internalFingerprint.getBoolean("unknown_cipher_suite_supported"),
                "unknown_cipher_suite_supported should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("beast_vuln"),
                internalFingerprint.getBoolean("beast_vuln"),
                "beast_vuln should match"
        );
        assertEquals(
                chromeFingerprint.getBoolean("able_to_detect_n_minus_one_splitting"),
                internalFingerprint.getBoolean("able_to_detect_n_minus_one_splitting"),
                "able_to_detect_n_minus_one_splitting should match"
        );
        assertEquals(
                chromeFingerprint.getJSONObject("insecure_cipher_suites"),
                internalFingerprint.getJSONObject("insecure_cipher_suites"),
                "insecure_cipher_suites should match"
        );
        assertEquals(
                chromeFingerprint.getString("rating"),
                internalFingerprint.getString("rating"),
                "rating should match"
        );
    }

    /**
     * Returns the TLS fingerprint as seen by {@code howsmyssl.com}
     * when connecting from a headless Chrome via Selenium.
     *
     * @apiNote
     * Captures the baseline that
     * {@link #getInternalFingerprint()}'s result is compared
     * against; headless mode is sufficient because the fingerprint
     * is determined by the TLS stack, not by the renderer.
     *
     * @return the parsed JSON fingerprint
     */
    private JSONObject getChromeFingerprint() {
        var options = new ChromeOptions();
        options.addArguments("--headless=new");
        var driver = new ChromeDriver(options);
        try {
            driver.get(CHECK_URL);
            var body = driver.findElement(By.tagName("body")).getText();
            return JSON.parseObject(body);
        } finally {
            driver.quit();
        }
    }

    /**
     * Returns the TLS fingerprint as seen by {@code howsmyssl.com}
     * when connecting through an {@link SSLSocket} configured with
     * the same TLS parameters that
     * {@link WhatsAppSslContextFactory#chrome()} produces.
     *
     * @apiNote
     * Drives a direct socket (rather than going through the Cobalt
     * socket stack) so the fingerprint reflects exactly the
     * factory's TLS configuration and is not confounded by any
     * Cobalt-level decorators.
     *
     * @return the parsed JSON fingerprint
     * @throws Exception if the TLS handshake or HTTP round-trip
     *                   fails
     */
    private JSONObject getInternalFingerprint() throws Exception {
        var factory = WhatsAppSslContextFactory.chrome();
        try (var socket = (SSLSocket) factory.sslContext()
                .getSocketFactory()
                .createSocket(CHECK_HOST, CHECK_PORT)) {
            socket.setSSLParameters(factory.sslParameters());
            socket.startHandshake();

            socket.getOutputStream().write(HTTP_REQUEST);
            socket.getOutputStream().flush();

            var responseBytes = socket.getInputStream().readAllBytes();
            var response = new String(responseBytes, StandardCharsets.UTF_8);

            var bodyStart = response.indexOf("\r\n\r\n");
            assertTrue(bodyStart >= 0, "HTTP response should contain header/body separator");
            var jsonBody = response.substring(bodyStart + 4);

            return JSON.parseObject(jsonBody);
        }
    }

    /**
     * Filters the cipher-suite list to drop pseudo-cipher entries
     * that legitimately differ between Chrome and the JDK.
     *
     * @apiNote
     * Three classes of entries are dropped on both sides:
     * <ul>
     *   <li>GREASE values, which only Chrome emits and the JDK
     *       cannot produce.</li>
     *   <li>SCSV signalling entries, which only the JDK emits and
     *       are not real cipher suites.</li>
     *   <li>{@code TLS_RSA_*} entries, which Chrome still offers
     *       but the JDK pins on {@code jdk.tls.disabledAlgorithms}
     *       and caches before user code can override.</li>
     * </ul>
     *
     * @param cipherSuites the cipher suite array returned by
     *                     {@code howsmyssl.com}
     * @return the filtered list, suitable for cross-side
     *         comparison
     */
    private static List<String> filterPseudoCiphers(JSONArray cipherSuites) {
        return cipherSuites.stream()
                .map(Object::toString)
                .filter(s -> !s.contains("GREASE")
                        && !s.contains("SCSV")
                        && !s.startsWith("TLS_RSA_"))
                .toList();
    }
}
