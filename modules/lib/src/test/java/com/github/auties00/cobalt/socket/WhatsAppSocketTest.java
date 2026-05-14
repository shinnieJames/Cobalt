package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDeviceBuilder;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.infra.ProxyServer;
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

import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class WhatsAppSocketTest {
    private WhatsAppSocketClient client;
    private ProxyServer proxyServer;

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

    @Test
    void testWebNoProxy() throws Exception {
        var store = createWebStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertInstanceOf(WhatsAppSocketClient.Web.class, client);
        assertHandshakeSucceeds();
    }

    @Test
    void testWebHttpProxy() throws Exception {
        proxyServer = ProxyServer.http();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testWebHttpsProxy() throws Exception {
        proxyServer = ProxyServer.https();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store, trustAllSslContextFactory());
        assertHandshakeSucceeds();
    }

    @Test
    void testMobileNoProxy() throws Exception {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertInstanceOf(WhatsAppSocketClient.Mobile.class, client);
        assertHandshakeSucceeds();
    }

    @Test
    void testMobileHttpProxy() throws Exception {
        proxyServer = ProxyServer.http();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testMobileHttpsProxy() throws Exception {
        proxyServer = ProxyServer.https();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store, trustAllSslContextFactory());
        assertHandshakeSucceeds();
    }

    @Test
    void testWebSocks4Proxy() throws Exception {
        proxyServer = ProxyServer.socks4();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testWebSocks5Proxy() throws Exception {
        proxyServer = ProxyServer.socks5();
        var store = createWebStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testMobileSocks4Proxy() throws Exception {
        proxyServer = ProxyServer.socks4();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testMobileSocks5Proxy() throws Exception {
        proxyServer = ProxyServer.socks5();
        var store = createMobileStore();
        store.setProxy(proxyServer.toProxy());
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertHandshakeSucceeds();
    }

    @Test
    void testDisconnectCleansUp() throws Exception {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        client.connect(new CapturingListener());
        assertTrue(client.isConnected());
        client.disconnect();
        assertFalse(client.isConnected());
    }

    @Test
    void testConnectRejectsNullListener() {
        var store = createMobileStore();
        client = WhatsAppSocketClient.newCipheredSocketClient(store);
        assertThrows(NullPointerException.class, () -> client.connect(null));
    }

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

    private void assertHandshakeSucceeds() throws Exception {
        var listener = new CapturingListener();
        client.connect(listener);
        assertTrue(client.isConnected(), "Should be connected after handshake");
        assertNotNull(client.writeKey(), "Write key should be derived");
        assertNotNull(client.readKey(), "Read key should be derived");
    }

    private static WhatsAppStore createMobileStore() {
        try {
            return WhatsAppStoreFactory.temporary()
                    .create(WhatsAppClientType.MOBILE, 15551234567L);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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

    private static class CapturingListener implements WhatsAppSocketListener {
        private final List<Node> nodes;
        private final List<WhatsAppException> errors;
        private final CountDownLatch firstNode;
        private final CountDownLatch closed;

        private CapturingListener() {
            this.nodes = new CopyOnWriteArrayList<>();
            this.errors = new CopyOnWriteArrayList<>();
            this.firstNode = new CountDownLatch(1);
            this.closed = new CountDownLatch(1);
        }

        @Override
        public void onNode(Node node) {
            nodes.add(node);
            firstNode.countDown();
        }

        @Override
        public void onError(WhatsAppException exception) {
            errors.add(exception);
        }

        @Override
        public void onClose() {
            closed.countDown();
        }
    }
}
