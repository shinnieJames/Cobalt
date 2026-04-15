package com.github.auties00.cobalt.socket.layer.application;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.application.websocket.WebSocketClientLayer;
import com.github.auties00.cobalt.socket.layer.application.whatsapp.WhatsAppSocketClientLayer;

/**
 * An application layer in the socket client stack.
 *
 * <p>Application layers sit at the top of the layer stack and provide
 * protocol-specific framing (WebSocket frames, WhatsApp int24 datagrams).
 */
public sealed interface SocketClientApplicationLayer<C extends SocketClientApplicationLayerContext>
        extends SocketClientLayer<SocketClientApplicationLayerContext>
        permits WebSocketClientLayer, WhatsAppSocketClientLayer {
}
