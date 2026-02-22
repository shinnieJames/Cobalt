package com.github.auties00.cobalt.socket.implementation.websocket;

import java.nio.ByteBuffer;

/**
 * Stateful websocket frame parser context.
 */
public final class WebSocketState {
    public static final int READ_HEADER_FIRST = 0;
    public static final int READ_HEADER_SECOND = 1;
    public static final int READ_EXTENDED_LENGTH = 2;
    public static final int READ_MASK = 3;
    public static final int READ_PAYLOAD = 4;

    private static final int CONTROL_PAYLOAD_MAX_LENGTH = 125;
    private static final int UNMASK_CHUNK_SIZE = 8192;

    public int readState = READ_HEADER_FIRST;
    public byte firstByte;
    public boolean finalFragment;
    public byte opcode;
    public boolean masked;
    public int extendedLengthBytesRemaining;
    public long extendedLengthValue;
    public long payloadLength;
    public long payloadRemaining;
    public int maskingKey;
    public int maskingKeyBytesRead;
    public int maskOffset;
    public int controlPayloadLength;
    public int controlPayloadRead;

    public final byte[] controlPayload;
    public final ByteBuffer unmaskBuffer;

    public WebSocketState() {
        this.controlPayload = new byte[CONTROL_PAYLOAD_MAX_LENGTH];
        this.unmaskBuffer = ByteBuffer.allocate(UNMASK_CHUNK_SIZE);
    }

    public void startFrame(byte firstByte, byte secondByte) {
        this.firstByte = firstByte;
        this.finalFragment = (firstByte & 0x80) != 0;
        this.opcode = (byte) (firstByte & 0x0F);
        this.masked = (secondByte & 0x80) != 0;
        this.extendedLengthBytesRemaining = 0;
        this.extendedLengthValue = 0;
        this.payloadLength = 0;
        this.payloadRemaining = 0;
        this.maskingKey = 0;
        this.maskingKeyBytesRead = 0;
        this.maskOffset = 0;
        this.controlPayloadLength = 0;
        this.controlPayloadRead = 0;
    }

    public void resetFrame() {
        this.readState = READ_HEADER_FIRST;
        this.firstByte = 0;
        this.finalFragment = false;
        this.opcode = 0;
        this.masked = false;
        this.extendedLengthBytesRemaining = 0;
        this.extendedLengthValue = 0;
        this.payloadLength = 0;
        this.payloadRemaining = 0;
        this.maskingKey = 0;
        this.maskingKeyBytesRead = 0;
        this.maskOffset = 0;
        this.controlPayloadLength = 0;
        this.controlPayloadRead = 0;
    }
}
