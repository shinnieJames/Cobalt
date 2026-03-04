package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.socket.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.application.websocket.WebSocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.security.SocketClientTunnelSecurityLayer;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayer;
import com.github.auties00.cobalt.socket.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class SocketClient {
    private static final InetSocketAddress SOCKET_ENDPOINT = InetSocketAddress.createUnresolved("g.whatsapp.net", 443);
    private static final URI WEB_SOCKET_ENDPOINT = URI.create("wss://web.whatsapp.com/ws/chat");

    public static SocketClient newSocketClient(WhatsAppStore store) {
        Objects.requireNonNull(store, "store cannot be null");

        var secureTransport = createSecureTransport(store);
        var secureTunnel = createSecureTunnel(secureTransport, store);
        var application = createApplication(secureTunnel, store);

        return new SocketClient(application);
    }

    private static SocketClientLayer createSecureTransport(WhatsAppStore store) {
        var transport = SocketClientTransportLayer.ofTcp();
        if(store.device().platform() == ClientPlatformType.WEB) {
            var tlsSecuredTransport = SocketClientTransportSecurityLayer.ofTls(transport); // WSS needs TLS
            return SocketClientTransportSecurityLayer.ofWhatsApp(tlsSecuredTransport, store);
        } else {
            return SocketClientTransportSecurityLayer.ofWhatsApp(transport, store);
        }
    }

    private static SocketClientLayer createSecureTunnel(SocketClientLayer secureTransport, WhatsAppStore store) {
        return switch (store.proxy().orElse(null)) {
            case WhatsAppClientProxy.Http http -> {
                var httpTunnel = SocketClientTunnelLayer.ofHttp(http, secureTransport);
                yield switch (http) {
                    case WhatsAppClientProxy.Http.Plain _ -> httpTunnel;
                    case WhatsAppClientProxy.Http.Secure secure -> SocketClientTunnelSecurityLayer.ofTls(httpTunnel);
                };
            }
            case WhatsAppClientProxy.Socks socks -> SocketClientTunnelLayer.ofSocks(socks, secureTransport);
            case null -> secureTransport;
        };
    }

    private static SocketClientLayer createApplication(SocketClientLayer secureTunnel, WhatsAppStore store) {
        if(store.device().platform() == ClientPlatformType.WEB) {
            return SocketClientApplicationLayer.ofWebSocket(secureTunnel);
        } else {
            return secureTunnel;
        }
    }

    private final SocketClientLayer upmostLayer;

    private SocketClient(SocketClientLayer upmostLayer) {
        this.upmostLayer = upmostLayer;
    }

    public void connect(SocketClientLayerListener listener) throws IOException {
        if(upmostLayer instanceof WebSocketClientApplicationLayer webSocketClientApplicationLayer) {
            webSocketClientApplicationLayer.connect(WEB_SOCKET_ENDPOINT, listener);
        } else {
            upmostLayer.connect(SOCKET_ENDPOINT, listener);
        }
    }

    public void disconnect() {
        upmostLayer.disconnect();
    }

    public boolean isConnected() {
        return upmostLayer.isConnected();
    }

    /**
     * Sends one logical binary payload represented by the provided buffers.
     *
     * <p>Buffers may be written asynchronously and may be transformed
     * in-place by the socket stack. Callers should not mutate the supplied
     * buffers after this call.
     *
     * @param buffers payload buffers in send order
     */
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        upmostLayer.sendBinary(buffers);
    }

    /**
     * Reads binary bytes into {@code buffer}.
     *
     * <p>The destination buffer stays in write mode after the call; callers
     * that need to read from it should invoke {@link ByteBuffer#flip()}
     * explicitly.
     *
     * @param buffer the destination buffer, in write mode
     * @param fully  {@code true} to fill the buffer, {@code false} to return
     *               after the first successful read
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return upmostLayer.readBinary(buffer, fully);
    }
}
