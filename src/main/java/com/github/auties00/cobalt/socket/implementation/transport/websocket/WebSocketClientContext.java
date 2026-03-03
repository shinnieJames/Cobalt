package com.github.auties00.cobalt.socket.implementation.transport.websocket;

import com.github.auties00.cobalt.socket.implementation.context.ssl.AbstractSSLSocketClientContext;
import com.github.auties00.cobalt.socket.implementation.transport.websocket.frame.WebSocketFrameDecoder;
import com.github.auties00.cobalt.socket.implementation.websocket.WebSocketClientListener;

public final class WebSocketClientContext extends AbstractSSLSocketClientContext {
    /**
     * Stateful websocket parser context. Initialized lazily when websocket
     * framing is enabled for this channel.
     */
    public final WebSocketFrameDecoder webSocketFrameDecoder;

    /**
     * Callback invoked when a complete inbound datagram has been
     * reassembled.
     *
     * <p> The selector thread dispatches each datagram to the listener
     * on a virtual thread to avoid blocking the selection loop.
     */
    public final WebSocketClientListener listener;

    /**
     * Creates a context for a new connection.
     *
     * @param listener  the callback to receive completed inbound datagrams
     */
    public WebSocketClientContext(WebSocketClientListener listener) {
        this.webSocketFrameDecoder = new WebSocketFrameDecoder();
        this.listener = listener;
    }
}
