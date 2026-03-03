package com.github.auties00.cobalt.socket.client;

import java.nio.ByteBuffer;

public interface SocketClientListener {
    void onDatagram(ByteBuffer buffer);
}
