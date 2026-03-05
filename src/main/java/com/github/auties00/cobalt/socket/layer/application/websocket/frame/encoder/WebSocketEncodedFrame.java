package com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * An encoded WebSocket frame consisting of a header and payload.
 */
public final class WebSocketEncodedFrame {
    private final ByteBuffer header;
    private final ByteBuffer payload;

    /**
     * Creates an encoded frame with the given header and payload.
     *
     * @param header  the frame header (including masking key)
     * @param payload the masked payload
     */
    WebSocketEncodedFrame(ByteBuffer header, ByteBuffer payload) {
        this.header = header;
        this.payload = payload;
    }

    /**
     * Returns the frame header.
     *
     * @return the header buffer in read mode
     */
    public ByteBuffer header() {
        return header;
    }

    /**
     * Returns the frame payload.
     *
     * @return the payload buffer in read mode
     */
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
