package com.github.auties00.cobalt.socket.application.websocket.frame.decoder;

import java.nio.ByteBuffer;

/**
 * A decoded WebSocket frame, produced by {@link WebSocketFrameDecoder}.
 */
public sealed interface WebSocketDecodedFrame permits WebSocketDecodedFrame.None,
        WebSocketDecodedFrame.Invalid,
        WebSocketDecodedFrame.Data,
        WebSocketDecodedFrame.Control {
    /**
     * Returns the singleton "no frame available" instance.
     *
     * @return the none instance
     */
    static None none() {
        return None.INSTANCE;
    }

    /**
     * Returns the singleton "invalid frame" instance.
     *
     * @return the invalid instance
     */
    static Invalid invalid() {
        return Invalid.INSTANCE;
    }

    /**
     * Creates a data frame with the given payload.
     *
     * @param payload the frame payload
     * @return a data frame
     */
    static Data data(ByteBuffer payload) {
        return new Data(payload);
    }

    /**
     * Creates a control frame.
     *
     * @param opcode  the control opcode
     * @param payload the control payload bytes
     * @param length  the number of valid bytes in the payload array
     * @return a control frame
     */
    static Control control(byte opcode, byte[] payload, int length) {
        return new Control(opcode, payload, length);
    }

    /**
     * Indicates that no complete frame is available yet.
     */
    final class None implements WebSocketDecodedFrame {
        private static final None INSTANCE = new None();

        private None() {

        }
    }

    /**
     * Indicates that the frame is invalid and the connection should be
     * closed.
     */
    final class Invalid implements WebSocketDecodedFrame {
        private static final Invalid INSTANCE = new Invalid();

        private Invalid() {

        }
    }

    /**
     * A data frame (binary or continuation) with its payload.
     */
    final class Data implements WebSocketDecodedFrame {
        private final ByteBuffer payload;

        private Data(ByteBuffer payload) {
            this.payload = payload;
        }

        /**
         * Returns the frame payload.
         *
         * @return the payload buffer in read mode
         */
        public ByteBuffer payload() {
            return payload;
        }
    }

    /**
     * A control frame (ping, pong, or close) with its payload.
     */
    final class Control implements WebSocketDecodedFrame {
        private final byte opcode;
        private final byte[] payload;
        private final int length;

        private Control(byte opcode, byte[] payload, int length) {
            this.opcode = opcode;
            this.payload = payload;
            this.length = length;
        }

        /**
         * Returns the control opcode.
         *
         * @return the opcode byte
         */
        public byte opcode() {
            return opcode;
        }

        /**
         * Returns the control payload bytes.
         *
         * @return the payload array
         */
        public byte[] payload() {
            return payload;
        }

        /**
         * Returns the number of valid bytes in the payload.
         *
         * @return the payload length
         */
        public int length() {
            return length;
        }
    }
}
