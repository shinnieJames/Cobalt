package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDeviceBuilder;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.ProxyServer;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link WhatsAppSocketClient} end-to-end against the live
 * WhatsApp servers for every supported transport, platform shape and
 * proxy configuration.
 *
 * @apiNote
 * Each test opens a real connection to {@code g.whatsapp.net} or
 * {@code web.whatsapp.com} and asserts that the Noise XX handshake
 * derives both read and write keys; the goal is integration coverage
 * of transport selection (TCP vs WebSocket), handshake shape (web vs
 * mobile) and proxy tunnelling (HTTP plain, HTTPS, SOCKS4, SOCKS5)
 * rather than unit-level coverage of any one method. Reconnect tests
 * exercise the two-shot handshake to confirm key destruction and
 * re-derivation do not leak state across sessions.
 *
 * @implNote
 * This implementation spins up a local {@link ProxyServer} per
 * proxy-flavour test (its lifecycle is owned by
 * {@link #tearDown()}), a fresh {@link WhatsAppStore} per scenario
 * via {@link WhatsAppStoreFactory#temporary()}, and a
 * {@link CapturingListener} that latches on the first node or close
 * event so {@link #assertHandshakeSucceeds()} can verify the
 * handshake landed without blocking on the full connect/auth flow.
 * HTTPS-proxy tests use the {@link #trustAllSslContextFactory()}
 * since the local proxy presents a locally-signed certificate.
 */
@Timeout(60)
class WhatsAppSocketTest {
    /**
     * The socket client under test; owned by each test method and
     * torn down by {@link #tearDown()}.
     */
    private WhatsAppSocketClient client;

    /**
     * The local proxy server (if any) backing the current
     * proxy-flavour test; null when the scenario does not need a
     * proxy.
     */
    private ProxyServer proxyServer;

    /**
     * Tears down the per-test socket client and proxy server, if any
     * were created.
     *
     * @apiNote
     * Always invoked even when the test threw, so the next test
     * starts from a clean baseline; both resources are nulled out so
     * a teardown failure does not double-close.
     *
     * @throws IOException if the proxy server cannot be closed
     */
    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception _) {
            }
            client = null;
        }
        if (proxyServer != null) {
            proxyServer.close();
            proxyServer = null;
        }
    }

    /**
     * Web sessions can disconnect and reconnect with a fresh store.
     */
    @Test
    void testWebReconnect() throws Exception {
        var store1 = createWebStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store1);
        assertHandshakeSucceeds();
        client.disconnect();
        client = null;

        var store2 = createWebStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store2);
        assertHandshakeSucceeds();
    }

    /**
     * Web stores produce the WebSocket transport and complete the
     * Noise handshake with no proxy in front.
     */
    @Test
    void testWebNoProxy() throws Exception {
        var store = createWebStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertInstanceOf(WhatsAppSocketClient.WebSocket.class, client);
        assertHandshakeSucceeds();
    }

    /**
     * The WebSocket transport tunnels through a plain HTTP proxy.
     */
    @Test
    void testWebHttpProxy() throws Exception {
        proxyServer = ProxyServer.http();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The WebSocket transport tunnels through an HTTPS proxy.
     *
     * @implNote
     * Uses {@link #trustAllSslContextFactory()} because the local
     * proxy presents a self-signed certificate.
     */
    @Test
    void testWebHttpsProxy() throws Exception {
        proxyServer = ProxyServer.https();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store, trustAllSslContextFactory());
        assertHandshakeSucceeds();
    }

    /**
     * Mobile stores produce the TCP transport and complete the
     * Noise handshake with no proxy in front.
     */
    @Test
    void testMobileNoProxy() throws Exception {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertInstanceOf(WhatsAppSocketClient.Tcp.class, client);
        assertHandshakeSucceeds();
    }

    /**
     * The TCP transport tunnels through a plain HTTP proxy on the
     * mobile path.
     */
    @Test
    void testMobileHttpProxy() throws Exception {
        proxyServer = ProxyServer.http();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The TCP transport tunnels through an HTTPS proxy on the
     * mobile path.
     */
    @Test
    void testMobileHttpsProxy() throws Exception {
        proxyServer = ProxyServer.https();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store, trustAllSslContextFactory());
        assertHandshakeSucceeds();
    }

    /**
     * The WebSocket transport tunnels through a SOCKS4 proxy.
     */
    @Test
    void testWebSocks4Proxy() throws Exception {
        proxyServer = ProxyServer.socks4();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The WebSocket transport tunnels through a SOCKS5 proxy.
     */
    @Test
    void testWebSocks5Proxy() throws Exception {
        proxyServer = ProxyServer.socks5();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The macOS native (Mac Catalyst) desktop path can disconnect and
     * reconnect on the TCP transport with the web handshake shape.
     */
    @Test
    void testDesktopReconnect() throws Exception {
        var store1 = createDesktopStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store1);
        assertHandshakeSucceeds();
        client.disconnect();
        client = null;

        var store2 = createDesktopStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store2);
        assertHandshakeSucceeds();
    }

    /**
     * The macOS desktop platform routes to the TCP transport and
     * completes the handshake with no proxy in front.
     */
    @Test
    void testDesktopNoProxy() throws Exception {
        var store = createDesktopStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertInstanceOf(WhatsAppSocketClient.Tcp.class, client);
        assertHandshakeSucceeds();
    }

    /**
     * The macOS desktop TCP transport tunnels through a plain HTTP
     * proxy.
     */
    @Test
    void testDesktopHttpProxy() throws Exception {
        proxyServer = ProxyServer.http();
        var store = createDesktopStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The macOS desktop TCP transport tunnels through an HTTPS
     * proxy.
     */
    @Test
    void testDesktopHttpsProxy() throws Exception {
        proxyServer = ProxyServer.https();
        var store = createDesktopStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store, trustAllSslContextFactory());
        assertHandshakeSucceeds();
    }

    /**
     * The macOS desktop TCP transport tunnels through a SOCKS4
     * proxy.
     */
    @Test
    void testDesktopSocks4Proxy() throws Exception {
        proxyServer = ProxyServer.socks4();
        var store = createDesktopStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The macOS desktop TCP transport tunnels through a SOCKS5
     * proxy.
     */
    @Test
    void testDesktopSocks5Proxy() throws Exception {
        proxyServer = ProxyServer.socks5();
        var store = createDesktopStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The mobile TCP transport tunnels through a SOCKS4 proxy.
     */
    @Test
    void testMobileSocks4Proxy() throws Exception {
        proxyServer = ProxyServer.socks4();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * The mobile TCP transport tunnels through a SOCKS5 proxy.
     */
    @Test
    void testMobileSocks5Proxy() throws Exception {
        proxyServer = ProxyServer.socks5();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    /**
     * Disconnecting transitions {@code isConnected} to {@code false}
     * and releases the reader thread.
     */
    @Test
    void testDisconnectCleansUp() throws Exception {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        var listener = new CapturingListener();
        client.connect(listener);
        assertTrue(client.isConnected());
        client.disconnect();
        assertFalse(client.isConnected());
        listener.await();
    }

    /**
     * Passing {@code null} as the listener raises
     * {@link NullPointerException} at the {@code connect} entry
     * point rather than racing the reader thread.
     */
    @Test
    void testConnectRejectsNullListener() {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertThrows(NullPointerException.class, () -> client.connect(null));
    }

    /**
     * Builds a {@link WhatsAppSslContextFactory} that accepts any
     * server certificate, for use with the locally-signed HTTPS
     * proxy.
     *
     * @apiNote
     * Only safe in tests; the trust-all manager removes all
     * certificate validation and would expose production traffic to
     * MITM if used outside fixtures.
     *
     * @return a trust-all factory
     */
    private static WhatsAppSslContextFactory trustAllSslContextFactory() {
        return new WhatsAppSslContextFactory() {
            @Override
            public SSLContext sslContext() {
                try {
                    var ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, new TrustManager[]{
                            new X509TrustManager() {
                                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                                }

                                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                                }

                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                            }
                    }, null);
                    return ctx;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public SSLParameters sslParameters() {
                var params = new SSLParameters();
                params.setApplicationProtocols(new String[]{"http/1.1"});
                return params;
            }
        };
    }

    /**
     * Drives the {@code connect} flow against the current
     * {@link #client} and asserts the handshake completed by
     * checking both AES keys are present.
     *
     * @apiNote
     * Used by every scenario test; the listener's {@code await}
     * blocks until the first node, error or close event arrives so
     * the test does not race the reader thread.
     *
     * @throws Exception if connect or await fails
     */
    private void assertHandshakeSucceeds() throws Exception {
        var listener = new CapturingListener();
        client.connect(listener);
        assertTrue(client.isConnected(), "Should be connected after handshake");
        assertNotNull(client.writeKey(), "Write key should be derived");
        assertNotNull(client.readKey(), "Read key should be derived");
        listener.await();
    }

    /**
     * Builds a temporary mobile-platform store seeded with a fixed
     * phone number.
     *
     * @return the store
     */
    private static WhatsAppStore createMobileStore() {
        try {
            return WhatsAppStoreFactory.temporary()
                    .create(WhatsAppClientType.MOBILE, 15551234567L);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a temporary web-platform store with a Windows-style
     * device, suitable for exercising the {@link WebSocket}
     * transport.
     *
     * @return the store
     */
    private static WhatsAppStore createWebStore() {
        try {
            var store = WhatsAppStoreFactory.temporary()
                    .create(WhatsAppClientType.WEB, UUID.randomUUID());
            store.setDevice(new WhatsAppDeviceBuilder()
                    .model("Surface Pro 4")
                    .manufacturer("Microsoft")
                    .platform(ClientPlatformType.WEB)
                    .clientType(WhatsAppClientType.WEB)
                    .build());
            return store;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds a temporary desktop-platform store with a macOS device,
     * suitable for exercising the macOS Mac Catalyst path (TCP
     * transport, web handshake shape).
     *
     * @return the store
     */
    private static WhatsAppStore createDesktopStore() {
        try {
            var store = WhatsAppStoreFactory.temporary()
                    .create(WhatsAppClientType.WEB, UUID.randomUUID());
            store.setDevice(new WhatsAppDeviceBuilder()
                    .model("MacBook Pro")
                    .manufacturer("Apple")
                    .platform(ClientPlatformType.MACOS)
                    .osDeviceAppVersion(ClientAppVersion.of("14.5"))
                    .clientType(WhatsAppClientType.WEB)
                    .build());
            return store;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A {@link WhatsAppSocketListener} that records every callback
     * and exposes an {@link #await} primitive so a test can park
     * until the first inbound event arrives.
     *
     * @apiNote
     * The latch counts down on the first {@link #onNode(Node)} or
     * {@link #onClose()} call so a scenario either sees a real
     * inbound node or sees the connection close orderly; collected
     * errors are surfaced through {@link #await()} as test failures.
     */
    private static class CapturingListener implements WhatsAppSocketListener {
        /**
         * The inbound nodes captured during the test.
         */
        private final List<Node> nodes;

        /**
         * The errors captured during the test.
         */
        private final List<WhatsAppException> errors;

        /**
         * The latch that releases on the first inbound node or
         * close event.
         */
        private final CountDownLatch latch;

        /**
         * Constructs an empty capturing listener.
         */
        public CapturingListener() {
            this.nodes = new CopyOnWriteArrayList<>();
            this.errors = new CopyOnWriteArrayList<>();
            this.latch = new CountDownLatch(1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onNode(Node node) {
            nodes.add(node);
            latch.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onError(WhatsAppException exception) {
            errors.add(exception);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClose() {
            latch.countDown();
        }

        /**
         * Parks for up to 30 seconds for the first inbound event,
         * failing the test if the timeout elapses or any errors
         * were captured.
         *
         * @throws InterruptedException if the wait is interrupted
         */
        public void await() throws InterruptedException {
            if(!latch.await(30, TimeUnit.SECONDS)) {
                fail("Timed out");
            }

            if(!errors.isEmpty()) {
                fail("Unexpected errors: " + errors);
            }
        }
    }
}
