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
 * Verifies that the {@link WhatsAppSslContextFactory} TLS configuration
 * produces the same TLS fingerprint as a real Chrome client.
 */
@Timeout(120)
class WhatsAppSslContextFactoryTest {
    @BeforeAll
    static void ensureSslFactoryLoaded() {
        //noinspection ResultOfMethodCallIgnored
        WhatsAppSslContextFactory.chrome();
    }

    private static final String CHECK_URL = "https://www.howsmyssl.com/a/check";
    private static final String CHECK_HOST = "www.howsmyssl.com";
    private static final int CHECK_PORT = 443;
    private static final byte[] HTTP_REQUEST = (
            "GET /a/check HTTP/1.0\r\n" +
            "Host: www.howsmyssl.com\r\n" +
            "Accept: application/json\r\n" +
            "\r\n"
    ).getBytes(StandardCharsets.US_ASCII);

    @Test
    void testTlsFingerprintMatchesChrome() throws Exception {
        var internalFingerprint = getInternalFingerprint();
        var chromeFingerprint = getChromeFingerprint();

        // Filter pseudo-cipher entries from both sides:
        // - GREASE entries (only Chrome emits these; the JDK cannot produce them)
        // - SCSV signalling entries (only the JDK emits these; not real ciphers)
        // - TLS_RSA_* entries (Chrome still offers them; the JDK pins them on
        //   jdk.tls.disabledAlgorithms and caches that constraint before any
        //   user code can override it, so we drop them on both sides)
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
     * Gets the TLS fingerprint as seen by howsmyssl.com when connecting
     * from a real Chrome browser via Selenium.
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
     * Gets the TLS fingerprint as seen by howsmyssl.com when connecting
     * through an {@link SSLSocket} configured with the same TLS
     * parameters that {@link WhatsAppSslContextFactory#chrome()}
     * produces.
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

    private static List<String> filterPseudoCiphers(JSONArray cipherSuites) {
        return cipherSuites.stream()
                .map(Object::toString)
                .filter(s -> !s.contains("GREASE")
                        && !s.contains("SCSV")
                        && !s.startsWith("TLS_RSA_"))
                .toList();
    }
}
