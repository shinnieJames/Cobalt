package com.github.auties00.cobalt.socket.client.webSocket;

import com.github.auties00.cobalt.socket.client.SocketClientListener;

import java.nio.ByteBuffer;

public interface WebSocketClientListener extends SocketClientListener {
    void onPing(ByteBuffer message);
    void onPong(ByteBuffer message);
    void onClose(int statusCode, String reason);
}
