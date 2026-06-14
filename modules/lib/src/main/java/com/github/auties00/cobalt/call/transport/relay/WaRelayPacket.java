package com.github.auties00.cobalt.call.transport.relay;

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
 * Models a single WhatsApp relay-protocol packet and converts it to and from its on-wire bytes.
 *
 * <p>A packet is the application-data payload the call engine exchanges with the relay over an
 * established SCTP {@code RTCDataChannel}. The wire layout is STUN-shaped (RFC 5389 section 6): a
 * 16-bit message type, a 16-bit attribute-block length, the 32-bit magic cookie
 * {@link #MAGIC_COOKIE}, a {@value #TRANSACTION_ID_LENGTH}-byte transaction id, and then the
 * attribute block.
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
 * <p>Each attribute is encoded as {@code [type:u16, length:u16, value:length bytes, zero padding to a
 * 4-byte boundary]}; the padding is not counted in the attribute length but is included in the
 * packet's {@code msgLength}. {@link #decode(byte[])} and {@link #encode()} round-trip this layout.
 *
 * <p>Two packet shapes are confirmed against captured live traffic (snapshot 1038740778): a 344-byte
 * {@link WaRelayMessageType#ALLOCATE_REQUEST} carrying {@link WaRelayAttributeType#WA_RELAY_TOKEN}
 * (182 bytes), {@link WaRelayAttributeType#WA_CALL_INFO} (95 bytes),
 * {@link WaRelayAttributeType#XOR_RELAYED_ADDRESS} (8 bytes), and
 * {@link WaRelayAttributeType#MESSAGE_INTEGRITY} (20 bytes); and a 20-byte header-only
 * {@link WaRelayMessageType#WA_KEEPALIVE} with no attributes.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipSctpConnectionManager")
public final class WaRelayPacket {
    /**
     * Holds the STUN magic cookie that occupies offset 4 of every packet, {@code 0x2112A442} per
     * RFC 5389 section 6.
     */
    public static final int MAGIC_COOKIE = 0x2112A442;

    /**
     * Holds the byte length of the fixed packet header: the 2-byte message type, 2-byte attribute
     * length, 4-byte magic cookie, and {@value #TRANSACTION_ID_LENGTH}-byte transaction id.
     */
    public static final int HEADER_LENGTH = 20;

    /**
     * Holds the byte length of the transaction-id field.
     */
    public static final int TRANSACTION_ID_LENGTH = 12;

    /**
     * Holds the 16-bit message-type code.
     */
    private final int messageType;

    /**
     * Holds the {@value #TRANSACTION_ID_LENGTH}-byte transaction identifier.
     */
    private final byte[] transactionId;

    /**
     * Holds the packet's attributes in declaration order, possibly empty as for keepalive packets.
     */
    private final List<WaRelayAttribute> attributes;

    /**
     * Constructs a packet from its message type, transaction id, and attributes.
     *
     * <p>The transaction id is defensively copied and the attribute list is copied into an
     * unmodifiable list, so later mutation of the caller's arguments does not affect this packet.
     *
     * @param messageType   the 16-bit message-type code; see {@link WaRelayMessageType#wireValue()}
     * @param transactionId the transaction id; must be non-{@code null} and exactly
     *                      {@value #TRANSACTION_ID_LENGTH} bytes long
     * @param attributes    the attributes; must be non-{@code null}
     * @throws NullPointerException     if {@code transactionId} or {@code attributes} is {@code null}
     * @throws IllegalArgumentException if {@code transactionId} is not exactly
     *                                  {@value #TRANSACTION_ID_LENGTH} bytes long
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
     * Returns a defensive copy of the {@value #TRANSACTION_ID_LENGTH}-byte transaction id.
     *
     * @return the transaction id as a fresh byte array
     */
    public byte[] transactionId() {
        return transactionId.clone();
    }

    /**
     * Returns the unmodifiable attribute list in declaration order.
     *
     * @return the attributes
     */
    public List<WaRelayAttribute> attributes() {
        return attributes;
    }

    /**
     * Decodes a packet from its on-wire bytes.
     *
     * <p>Reads the header big-endian, validates that the magic cookie equals {@link #MAGIC_COOKIE},
     * captures the transaction id, and then walks the attribute block bounded by the header's
     * {@code msgLength}. Each attribute is read as type, length, value, and trailing zero padding to
     * the next 4-byte boundary. The bytes following the attribute block, if any, are not consumed.
     *
     * @param bytes the captured or received packet bytes
     * @return the parsed packet
     * @throws NullPointerException     if {@code bytes} is {@code null}
     * @throws IllegalArgumentException if the input is shorter than {@link #HEADER_LENGTH}, if the
     *                                  magic cookie does not match {@link #MAGIC_COOKIE}, if
     *                                  {@code msgLength} overruns the buffer, or if any attribute
     *                                  overruns the attribute block
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
            var pad = (4 - (aLen & 3)) & 3;
            buf.position(buf.position() + pad);
        }

        return new WaRelayPacket(msgType, txId, attrs);
    }

    /**
     * Encodes this packet to its on-wire bytes.
     *
     * <p>Writes the header big-endian with {@code msgLength} set to the total size of the encoded
     * attribute block including each attribute's 4-byte padding, stamps the magic cookie and
     * transaction id, and then serialises every attribute as type, length, value, and zero padding to
     * the next 4-byte boundary.
     *
     * @return a freshly allocated byte array containing the encoded packet
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
     * Compares this packet with another for message-type, transaction-id, and attribute equality.
     *
     * @param o the object to compare against
     * @return {@code true} when {@code o} is a {@code WaRelayPacket} with the same message type,
     *         transaction id, and attribute list
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof WaRelayPacket p
                && messageType == p.messageType
                && Arrays.equals(transactionId, p.transactionId)
                && attributes.equals(p.attributes);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, combining the message type,
     * transaction id, and attributes.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(messageType, Arrays.hashCode(transactionId), attributes);
    }

    /**
     * Returns a diagnostic string carrying the hex message type, hex transaction id, and attribute
     * list.
     *
     * @return a human-readable representation
     */
    @Override
    public String toString() {
        return "WaRelayPacket[msgType=0x" + Integer.toHexString(messageType)
                + ", txId=" + bytesToHex(transactionId)
                + ", attrs=" + attributes + ']';
    }

    /**
     * Hex-encodes the given bytes in lower case with no separators.
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
