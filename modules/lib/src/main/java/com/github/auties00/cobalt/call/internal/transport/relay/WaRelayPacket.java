package com.github.auties00.cobalt.call.internal.transport.relay;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A single WhatsApp relay-protocol packet — the application-data
 * payload sent by the wasm engine over an established SCTP
 * {@code RTCDataChannel} between the call client and the relay.
 *
 * <p>Wire format (STUN-shaped, RFC 5389 §6):
 *
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |     msgType (16 bits)         |     msgLength (16 bits)       |
 * +-------------------------------+-------------------------------+
 * |                  magic cookie (0x2112A442)                    |
 * +---------------------------------------------------------------+
 * |                                                               |
 * |                  transaction id (12 bytes)                    |
 * |                                                               |
 * +---------------------------------------------------------------+
 * |                  attributes (msgLength bytes)                 |
 * +---------------------------------------------------------------+
 * }</pre>
 *
 * <p>Each attribute is encoded as
 * {@code [type:u16, length:u16, value:length bytes,
 * padding to 4-byte boundary]}.
 *
 * <p>Capture-confirmed packet shapes (snapshot 1038740778):
 *
 * <ul>
 *   <li>344-byte {@link WaRelayMessageType#ALLOCATE_REQUEST} —
 *       {@link WaRelayAttributeType#WA_RELAY_TOKEN} (182 B),
 *       {@link WaRelayAttributeType#WA_CALL_INFO} (95 B),
 *       {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} (8 B),
 *       {@link WaRelayAttributeType#MESSAGE_INTEGRITY} (20 B).</li>
 *   <li>20-byte {@link WaRelayMessageType#WA_KEEPALIVE} — header
 *       only, no attributes.</li>
 * </ul>
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayPacket {
    /**
     * The STUN magic cookie at offset 4 of every packet. Value
     * {@code 0x2112A442} per RFC 5389 §6.
     */
    public static final int MAGIC_COOKIE = 0x2112A442;

    /**
     * Length in bytes of the fixed packet header (msgType +
     * msgLength + magic + txId).
     */
    public static final int HEADER_LENGTH = 20;

    /**
     * Length in bytes of the transaction id field.
     */
    public static final int TRANSACTION_ID_LENGTH = 12;

    /**
     * The 16-bit message-type code.
     */
    private final int messageType;

    /**
     * The 12-byte transaction identifier.
     */
    private final byte[] transactionId;

    /**
     * The list of attributes carried by the packet, in declaration
     * order. May be empty (e.g. for keepalive packets).
     */
    private final List<WaRelayAttribute> attributes;

    /**
     * Constructs a new packet.
     *
     * @param messageType   the 16-bit message-type code; see
     *                      {@link WaRelayMessageType#wireValue()}
     * @param transactionId the 12-byte transaction id; must be
     *                      non-{@code null} and exactly 12 bytes
     * @param attributes    the attributes; must be non-{@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code transactionId} is not
     *                                  exactly 12 bytes long
     */
    public WaRelayPacket(int messageType, byte[] transactionId, List<WaRelayAttribute> attributes) {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(attributes, "attributes cannot be null");
        if (transactionId.length != TRANSACTION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "transactionId must be " + TRANSACTION_ID_LENGTH + " bytes, got " + transactionId.length);
        }
        this.messageType = messageType;
        this.transactionId = transactionId.clone();
        this.attributes = List.copyOf(attributes);
    }

    /**
     * Returns the 16-bit message-type code.
     *
     * @return the wire-level message type
     */
    public int messageType() {
        return messageType;
    }

    /**
     * Returns a defensive copy of the 12-byte transaction id.
     *
     * @return the transaction id as a fresh byte array
     */
    public byte[] transactionId() {
        return transactionId.clone();
    }

    /**
     * Returns the unmodifiable attribute list.
     *
     * @return the attributes in declaration order
     */
    public List<WaRelayAttribute> attributes() {
        return attributes;
    }

    /**
     * Decodes a packet from its on-wire bytes.
     *
     * @param bytes the captured / received packet bytes
     * @return the parsed packet
     * @throws NullPointerException     if {@code bytes} is {@code null}
     * @throws IllegalArgumentException if the input is shorter than
     *                                  {@link #HEADER_LENGTH} or if any
     *                                  attribute overruns the buffer
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    public static WaRelayPacket decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        if (bytes.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("packet too short: " + bytes.length);
        }
        var buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        var msgType = buf.getShort() & 0xFFFF;
        var msgLen = buf.getShort() & 0xFFFF;
        var cookie = buf.getInt();
        if (cookie != MAGIC_COOKIE) {
            throw new IllegalArgumentException(
                    "bad magic cookie: 0x" + Integer.toHexString(cookie));
        }
        var txId = new byte[TRANSACTION_ID_LENGTH];
        buf.get(txId);

        if (HEADER_LENGTH + msgLen > bytes.length) {
            throw new IllegalArgumentException(
                    "msgLength " + msgLen + " overruns buffer (header=" + HEADER_LENGTH + ", total=" + bytes.length + ")");
        }

        var attrs = new ArrayList<WaRelayAttribute>();
        var attrEnd = HEADER_LENGTH + msgLen;
        while (buf.position() < attrEnd) {
            if (buf.position() + 4 > attrEnd) {
                throw new IllegalArgumentException(
                        "trailing " + (attrEnd - buf.position()) + " bytes after last attribute");
            }
            var aType = buf.getShort() & 0xFFFF;
            var aLen = buf.getShort() & 0xFFFF;
            if (buf.position() + aLen > attrEnd) {
                throw new IllegalArgumentException(
                        "attribute 0x" + Integer.toHexString(aType) + " value of length " + aLen + " overruns msgLen");
            }
            var value = new byte[aLen];
            buf.get(value);
            attrs.add(new WaRelayAttribute(aType, value));
            // pad to 4-byte boundary
            var pad = (4 - (aLen & 3)) & 3;
            buf.position(buf.position() + pad);
        }

        return new WaRelayPacket(msgType, txId, attrs);
    }

    /**
     * Encodes this packet to its on-wire bytes.
     *
     * @return a freshly-allocated byte array containing the packet
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipSctpConnectionManager",
            exports = "sendWAWebVoipDataToRelay", adaptation = WhatsAppAdaptation.ADAPTED)
    public byte[] encode() {
        var bodyLen = 0;
        for (var attr : attributes) {
            bodyLen += 4 + attr.value().length;
            bodyLen += (4 - (attr.value().length & 3)) & 3;
        }
        var buf = ByteBuffer.allocate(HEADER_LENGTH + bodyLen).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) messageType);
        buf.putShort((short) bodyLen);
        buf.putInt(MAGIC_COOKIE);
        buf.put(transactionId);
        for (var attr : attributes) {
            buf.putShort((short) attr.type());
            buf.putShort((short) attr.value().length);
            buf.put(attr.value());
            var pad = (4 - (attr.value().length & 3)) & 3;
            for (var i = 0; i < pad; i++) {
                buf.put((byte) 0);
            }
        }
        return buf.array();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayPacket p
                && messageType == p.messageType
                && Arrays.equals(transactionId, p.transactionId)
                && attributes.equals(p.attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(messageType, Arrays.hashCode(transactionId), attributes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "WaRelayPacket[msgType=0x" + Integer.toHexString(messageType)
                + ", txId=" + bytesToHex(transactionId)
                + ", attrs=" + attributes + ']';
    }

    /**
     * Hex-encodes the given bytes in lower-case, no separators.
     *
     * @param b the bytes to encode
     * @return the hex string
     */
    private static String bytesToHex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }
}
