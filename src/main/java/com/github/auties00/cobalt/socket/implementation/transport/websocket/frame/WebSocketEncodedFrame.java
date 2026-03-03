package com.github.auties00.cobalt.socket.implementation.transport.websocket.frame;

import java.nio.ByteBuffer;

public record WebSocketEncodedFrame(ByteBuffer header, ByteBuffer payload) {

}
