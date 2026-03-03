package com.github.auties00.cobalt.socket.implementation.transport.websocket.frame;

import java.nio.ByteBuffer;

public sealed interface WebSocketDecodedFrame permits WebSocketDecodedFrame.None,
        WebSocketDecodedFrame.Invalid,
        WebSocketDecodedFrame.Data,
        WebSocketDecodedFrame.Control {
    static None none() {
        return None.INSTANCE;
    }

    static Invalid invalid() {
        return Invalid.INSTANCE;
    }

    static Data data(ByteBuffer payload) {
        return new Data(payload);
    }

    static Control control(byte opcode, byte[] payload, int length) {
        return new Control(opcode, payload, length);
    }

    final class None implements WebSocketDecodedFrame {
        private static final None INSTANCE = new None();

        private None() {

        }
    }

    final class Invalid implements WebSocketDecodedFrame {
        private static final Invalid INSTANCE = new Invalid();

        private Invalid() {

        }
    }

    final class Data implements WebSocketDecodedFrame {
        private final ByteBuffer payload;

        private Data(ByteBuffer payload) {
            this.payload = payload;
        }

        public ByteBuffer payload() {
            return payload;
        }
    }

    final class Control implements WebSocketDecodedFrame {
        private final byte opcode;
        private final byte[] payload;
        private final int length;

        private Control(byte opcode, byte[] payload, int length) {
            this.opcode = opcode;
            this.payload = payload;
            this.length = length;
        }

        public byte opcode() {
            return opcode;
        }

        public byte[] payload() {
            return payload;
        }

        public int length() {
            return length;
        }
    }
}
