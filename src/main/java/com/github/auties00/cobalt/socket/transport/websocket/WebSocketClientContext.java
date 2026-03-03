package com.github.auties00.cobalt.socket.transport.websocket;

import com.github.auties00.cobalt.socket.context.ssl.AbstractSSLSocketClientContext;
import com.github.auties00.cobalt.socket.transport.websocket.frame.WebSocketFrameDecoder;
import com.github.auties00.cobalt.socket.client.webSocket.WebSocketClientListener;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class WebSocketClientContext extends AbstractSSLSocketClientContext {
    private static final int UNEXPECTED_CLOSURE = 1006;

    /**
     * The websocket listener.
     */
    public final WebSocketClientListener webSocketFrameListener;

    /**
     * Stateful websocket parser context. Initialized lazily when websocket
     * framing is enabled for this channel.
     */
    public final WebSocketFrameDecoder webSocketFrameDecoder;

    /**
     * Creates a context for a new connection.
     *
     * @param webSocketFrameListener the websocket listener.
     */
    public WebSocketClientContext(WebSocketClientListener webSocketFrameListener) {
        this.webSocketFrameListener = Objects.requireNonNull(webSocketFrameListener, "webSocketFrameListener cannot be null");
        this.webSocketFrameDecoder = new WebSocketFrameDecoder();
    }

    @Override
    public void onDatagram(ByteBuffer datagram) {
        webSocketFrameListener.onDatagram(datagram);
    }

    @Override
    public void onClose() {
        webSocketFrameListener.onClose(UNEXPECTED_CLOSURE, "");
    }
}
