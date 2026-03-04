package com.github.auties00.cobalt.socket.application;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.application.websocket.WebSocketClientApplicationLayer;

import java.util.Objects;

public interface SocketClientApplicationLayer extends SocketClientLayer {
    static SocketClientApplicationLayer ofWebSocket(SocketClientLayer innerLayer) {
        Objects.requireNonNull(innerLayer, "innerLayer cannot be null");

        return new WebSocketClientApplicationLayer(innerLayer);
    }
}
