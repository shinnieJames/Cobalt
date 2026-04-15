package com.github.auties00.cobalt.socket.layer.application;

import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.application.websocket.WebSocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.application.whatsapp.WhatsAppSocketClientLayerContext;

public sealed interface SocketClientApplicationLayerContext extends SocketClientLayerContext permits WebSocketClientLayerContext, WhatsAppSocketClientLayerContext {

}
