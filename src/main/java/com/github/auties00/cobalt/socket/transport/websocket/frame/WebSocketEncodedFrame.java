package com.github.auties00.cobalt.socket.transport.websocket.frame;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class WebSocketEncodedFrame {
    private final ByteBuffer header;
    private final ByteBuffer payload;

    WebSocketEncodedFrame(ByteBuffer header, ByteBuffer payload) {
        this.header = header;
        this.payload = payload;
    }

    public ByteBuffer header() {
        return header;
    }

    public ByteBuffer payload() {
        return payload;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (WebSocketEncodedFrame) obj;
        return Objects.equals(this.header, that.header) &&
               Objects.equals(this.payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, payload);
    }

    @Override
    public String toString() {
        return "WebSocketEncodedFrame[" +
               "header=" + header + ", " +
               "payload=" + payload + ']';
    }
}
