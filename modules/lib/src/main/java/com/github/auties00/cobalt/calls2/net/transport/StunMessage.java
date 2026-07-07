package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Encodes and decodes a STUN message, the framing both the RFC-compliant Web-P2P ICE path and the
 * WhatsApp relay/SFU path use to carry connectivity checks, reflexive-address reports, and the SFU
 * publish/subscribe protobufs.
 *
 * <p>A STUN message is a twenty-byte header followed by a sequence of type-length-value attributes.
 * The header is the sixteen-bit {@linkplain #messageType() message type}, the sixteen-bit length of
 * the attribute section, the thirty-two-bit {@linkplain #magicCookie() magic cookie}, and the
 * ninety-six-bit {@linkplain #transactionId() transaction id}. Each attribute is a sixteen-bit type, a
 * sixteen-bit value length, the value bytes, then zero padding to the next four-byte boundary; the
 * length field counts the value bytes only, not the padding. {@link #attributes()} preserves the
 * attribute order, which matters because the two integrity attributes must come last and in the fixed
 * order {@link StunAttributeType#MESSAGE_INTEGRITY} then {@link StunAttributeType#FINGERPRINT}.
 *
 * <p>This record carries only the parsed structure; the integrity attributes are computed by
 * {@link StunIntegrity}. The {@link #encode()} method serializes whatever attributes the record holds
 * verbatim, so a caller that wants an integrity-protected message uses
 * {@link #finalizeWithIntegrity(byte[])}, which appends the {@link StunAttributeType#MESSAGE_INTEGRITY}
 * and {@link StunAttributeType#FINGERPRINT} attributes over the length-adjusted prefixes STUN
 * mandates. {@link #decode(byte[])} parses a received message, keeping an attribute whose type is not a
 * known {@link StunAttributeType} as an {@link Attribute} with a {@code null} {@linkplain Attribute#type()
 * type} so unknown comprehension-optional attributes round-trip rather than fail.
 *
 * @implNote This implementation reproduces the STUN header and TLV layout built by
 * {@code wa_stun_build_req_hdr} (fn4836) and parsed by {@code parse_stun_attrs} (fn4847) of
 * {@code wa_stun_msg.cc} in the wa-voip WASM module {@code ff-tScznZ8P}, and the four-byte attribute
 * padding the {@code wa_stun_add_*_attr} writers apply ({@code wa_stun_add_binary_data_attr} fn4838
 * pads each value to a four-byte boundary). The magic cookie {@value #MAGIC_COOKIE_HEX} is the RFC 8489
 * constant on the Web-P2P path; the relay path's exact header is an open question
 * (re/calls2-spec/captures/transport.json: the raw relay datagrams were not capturable from the page
 * socket), so the magic cookie is a record component rather than a baked-in constant, letting a relay
 * binding carry a WhatsApp-private value if a future datagram capture shows one. The standard
 * binding-message-type constants ({@value #TYPE_BINDING_REQUEST_HEX} request,
 * {@value #TYPE_BINDING_SUCCESS_HEX} success response, {@value #TYPE_BINDING_ERROR_HEX} error response,
 * {@value #TYPE_BINDING_INDICATION_HEX} indication) follow RFC 8489 method/class encoding.
 *
 * @param messageType   the sixteen-bit STUN message type (method and class)
 * @param magicCookie   the thirty-two-bit magic cookie, {@link #MAGIC_COOKIE} on the RFC path
 * @param transactionId the {@value #TRANSACTION_ID_LENGTH}-byte transaction id; never {@code null}
 * @param attributes    the ordered attribute list; never {@code null}
 */
public record StunMessage(int messageType, int magicCookie, byte[] transactionId, List<Attribute> attributes) {
    /**
     * The RFC 8489 magic cookie, present on the Web-P2P ICE path.
     */
    public static final int MAGIC_COOKIE = 0x2112A442;

    /**
     * The {@value #MAGIC_COOKIE_HEX} magic cookie rendered as a hex string for documentation
     * cross-reference.
     */
    private static final String MAGIC_COOKIE_HEX = "0x2112A442";

    /**
     * The length, in bytes, of a STUN transaction id.
     */
    public static final int TRANSACTION_ID_LENGTH = 12;

    /**
     * The length, in bytes, of the fixed STUN message header that precedes the attribute section.
     */
    public static final int HEADER_LENGTH = 20;

    /**
     * The {@code Binding Request} message type ({@value #TYPE_BINDING_REQUEST_HEX}).
     */
    public static final int TYPE_BINDING_REQUEST = 0x0001;

    /**
     * The {@code Binding Indication} message type ({@value #TYPE_BINDING_INDICATION_HEX}).
     */
    public static final int TYPE_BINDING_INDICATION = 0x0011;

    /**
     * The {@code Binding Success Response} message type ({@value #TYPE_BINDING_SUCCESS_HEX}).
     */
    public static final int TYPE_BINDING_SUCCESS_RESPONSE = 0x0101;

    /**
     * The {@code Binding Error Response} message type ({@value #TYPE_BINDING_ERROR_HEX}).
     */
    public static final int TYPE_BINDING_ERROR_RESPONSE = 0x0111;

    /**
     * The WhatsApp Web subscription-envelope message type ({@value #TYPE_SUBSCRIPTION_HEX}).
     *
     * <p>A {@code 0x0003} STUN-magic message carries the SFU publish/subscribe state over the SCTP data
     * channel: the {@link StunAttributeType#WA_WARP_MESSAGE} WARP frame, the
     * {@link StunAttributeType#WA_SUBSCRIPTION} protobuf, the {@link StunAttributeType#WA_XOR_MAPPED_ADDRESS}
     * relay reflexive address, and a trailing {@link StunAttributeType#MESSAGE_INTEGRITY} (with no
     * {@link StunAttributeType#FINGERPRINT}). It is observed in
     * re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md.
     */
    public static final int TYPE_SUBSCRIPTION = 0x0003;

    /**
     * The WhatsApp Web connectivity-keepalive message type ({@value #TYPE_KEEPALIVE_HEX}).
     *
     * <p>A {@code 0x0801} STUN-magic message with a zero-length attribute section is the bare connectivity
     * ping the client sends on the data channel to keep the leg alive: header only, no attributes. It is
     * observed in re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md.
     */
    public static final int TYPE_KEEPALIVE = 0x0801;

    /**
     * The {@value #TYPE_BINDING_REQUEST_HEX} request type rendered as hex for documentation.
     */
    private static final String TYPE_BINDING_REQUEST_HEX = "0x0001";

    /**
     * The {@value #TYPE_BINDING_SUCCESS_HEX} success-response type rendered as hex for documentation.
     */
    private static final String TYPE_BINDING_SUCCESS_HEX = "0x0101";

    /**
     * The {@value #TYPE_BINDING_ERROR_HEX} error-response type rendered as hex for documentation.
     */
    private static final String TYPE_BINDING_ERROR_HEX = "0x0111";

    /**
     * The {@value #TYPE_BINDING_INDICATION_HEX} indication type rendered as hex for documentation.
     */
    private static final String TYPE_BINDING_INDICATION_HEX = "0x0011";

    /**
     * The {@value #TYPE_SUBSCRIPTION_HEX} subscription-envelope type rendered as hex for documentation.
     */
    private static final String TYPE_SUBSCRIPTION_HEX = "0x0003";

    /**
     * The {@value #TYPE_KEEPALIVE_HEX} keepalive type rendered as hex for documentation.
     */
    private static final String TYPE_KEEPALIVE_HEX = "0x0801";

    /**
     * The byte offset of the sixteen-bit attribute-section length field in the STUN header.
     */
    private static final int LENGTH_FIELD_OFFSET = 2;

    /**
     * The byte alignment every STUN attribute value is zero-padded to.
     */
    private static final int ATTRIBUTE_PADDING = 4;

    /**
     * The IPv4 family code in a STUN MAPPED-ADDRESS-style attribute value.
     */
    public static final int ADDRESS_FAMILY_IPV4 = 0x01;

    /**
     * The IPv6 family code in a STUN MAPPED-ADDRESS-style attribute value.
     */
    public static final int ADDRESS_FAMILY_IPV6 = 0x02;

    /**
     * Canonicalizes the record components, validating the transaction id length and copying the mutable
     * fields defensively.
     *
     * @throws NullPointerException     if {@code transactionId} or {@code attributes} is {@code null}
     * @throws IllegalArgumentException if {@code transactionId} is not {@value #TRANSACTION_ID_LENGTH}
     *                                  bytes long
     */
    public StunMessage {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(attributes, "attributes cannot be null");
        if (transactionId.length != TRANSACTION_ID_LENGTH) {
            throw new IllegalArgumentException("transactionId must be " + TRANSACTION_ID_LENGTH
                    + " bytes, got " + transactionId.length);
        }
        transactionId = transactionId.clone();
        attributes = List.copyOf(attributes);
    }

    /**
     * Returns a defensive copy of the transaction id.
     *
     * @return a copy of the {@value #TRANSACTION_ID_LENGTH}-byte transaction id
     */
    @Override
    public byte[] transactionId() {
        return transactionId.clone();
    }

    /**
     * Returns the first attribute of a given type, if present.
     *
     * @param type the attribute type to find
     * @return an {@link Optional} holding the first matching attribute, or empty when none is present
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public Optional<Attribute> attribute(StunAttributeType type) {
        Objects.requireNonNull(type, "type cannot be null");
        for (var attribute : attributes) {
            if (attribute.type() == type) {
                return Optional.of(attribute);
            }
        }
        return Optional.empty();
    }

    /**
     * Serializes this message to its wire bytes exactly as its attributes stand.
     *
     * <p>The header is written first, then each attribute as a type-length-value triple padded with
     * zero bytes to the next four-byte boundary, and finally the header's length field is patched to
     * the total attribute-section length, which counts attribute values and their padding but not the
     * header. This method does not add or recompute any integrity attribute; use
     * {@link #finalizeWithIntegrity(byte[])} to produce an integrity-protected message.
     *
     * @return the serialized STUN message bytes
     */
    // TODO: add package-private ownership-transfer seams to size the final buffer once and skip the
    //  redundant clones on the single-transport-thread encode/decode path: Attribute.ofOwned(type,
    //  ownedValue) (no defensive clone of a caller-owned value), an encodedLength()/encodeInto(dest,off)
    //  pair here so encode() and finalizeWithIntegrity() write straight into one right-sized buffer
    //  instead of building a List<byte[]> and re-copying, and a StunIntegrity.computeMessageIntegrity(
    //  buffer, prefixLen) overload so finalizeWithIntegrity does not re-encode the prefix. The public
    //  ctors and value()/transactionId() keep cloning for external callers. Deferred: correctness rests on
    //  the "IceAgent/CallTransportController drive this from a single transport thread" invariant and on
    //  every same-package writer honoring the no-alias contract, which is not locally provable here.
    public byte[] encode() {
        var body = new ArrayList<byte[]>(attributes.size());
        var attributeSectionLength = 0;
        for (var attribute : attributes) {
            var encoded = attribute.encode();
            body.add(encoded);
            attributeSectionLength += encoded.length;
        }
        var out = new byte[HEADER_LENGTH + attributeSectionLength];
        writeHeader(out, attributeSectionLength);
        var cursor = HEADER_LENGTH;
        for (var encoded : body) {
            System.arraycopy(encoded, 0, out, cursor, encoded.length);
            cursor += encoded.length;
        }
        return out;
    }

    /**
     * Serializes this message and appends the {@code MESSAGE-INTEGRITY} and {@code FINGERPRINT}
     * attributes, returning the fully finalized wire bytes.
     *
     * <p>The message is first encoded with its existing attributes, then the twenty-byte HMAC-SHA1 is
     * computed over that prefix with the length field adjusted to span the integrity attribute and
     * appended as a {@link StunAttributeType#MESSAGE_INTEGRITY} attribute, then the four-byte CRC32 is
     * computed over the new prefix with the length field adjusted to span the fingerprint attribute and
     * appended as a {@link StunAttributeType#FINGERPRINT} attribute. The two integrity attributes must
     * not already be present; both are twenty bytes and eight bytes on the wire respectively and need no
     * value padding.
     *
     * @param password the ICE password keying the {@code MESSAGE-INTEGRITY} HMAC, in raw bytes
     * @return the finalized STUN message bytes including the integrity attributes
     * @throws NullPointerException       if {@code password} is {@code null}
     * @throws IllegalStateException      if this message already carries a
     *                                    {@link StunAttributeType#MESSAGE_INTEGRITY} or
     *                                    {@link StunAttributeType#FINGERPRINT} attribute
     * @throws WhatsAppCallException.Srtp if the platform cannot compute the integrity primitives
     */
    public byte[] finalizeWithIntegrity(byte[] password) {
        Objects.requireNonNull(password, "password cannot be null");
        if (attribute(StunAttributeType.MESSAGE_INTEGRITY).isPresent()
                || attribute(StunAttributeType.FINGERPRINT).isPresent()) {
            throw new IllegalStateException(
                    "message already carries a MESSAGE-INTEGRITY or FINGERPRINT attribute");
        }
        var prefix = encode();
        var integrity = StunIntegrity.computeMessageIntegrity(prefix, password);
        var withIntegrity = appendAttribute(
                prefix, StunAttributeType.MESSAGE_INTEGRITY.value(), integrity);
        var fingerprint = StunIntegrity.computeFingerprint(withIntegrity);
        return appendAttribute(withIntegrity, StunAttributeType.FINGERPRINT.value(), fingerprint);
    }

    /**
     * Parses a received STUN message into its structured form.
     *
     * <p>The header is read first; the magic cookie and transaction id are kept verbatim. Each
     * attribute is then read as a type-length-value triple, with the cursor advanced past the value's
     * four-byte padding. An attribute whose type is not a known {@link StunAttributeType} is kept with a
     * {@code null} {@linkplain Attribute#type() type} and its raw type value, so unknown attributes do
     * not abort the parse.
     *
     * @param message the received STUN message bytes
     * @return the parsed {@link StunMessage}
     * @throws NullPointerException        if {@code message} is {@code null}
     * @throws WhatsAppCallException.Rtp   if the message is shorter than the header, declares an
     *                                     attribute-section length past the buffer, or carries a
     *                                     truncated attribute
     */
    public static StunMessage decode(byte[] message) {
        Objects.requireNonNull(message, "message cannot be null");
        if (message.length < HEADER_LENGTH) {
            throw new WhatsAppCallException.Rtp(
                    "STUN message of " + message.length + " bytes is shorter than the " + HEADER_LENGTH
                            + "-byte header");
        }
        var messageType = readUnsignedShort(message, 0);
        var attributeSectionLength = readUnsignedShort(message, LENGTH_FIELD_OFFSET);
        var magicCookie = readInt(message, 4);
        var transactionId = Arrays.copyOfRange(message, 8, HEADER_LENGTH);
        var end = HEADER_LENGTH + attributeSectionLength;
        if (end > message.length) {
            throw new WhatsAppCallException.Rtp("STUN attribute section length " + attributeSectionLength
                    + " exceeds the " + (message.length - HEADER_LENGTH) + " available bytes");
        }
        var attributes = new ArrayList<Attribute>(attributeSectionLength / ATTRIBUTE_PADDING);
        var cursor = HEADER_LENGTH;
        while (cursor + ATTRIBUTE_PADDING <= end) {
            var typeValue = readUnsignedShort(message, cursor);
            var valueLength = readUnsignedShort(message, cursor + 2);
            var valueStart = cursor + ATTRIBUTE_PADDING;
            if (valueStart + valueLength > end) {
                throw new WhatsAppCallException.Rtp("STUN attribute value of " + valueLength
                        + " bytes at offset " + valueStart + " runs past the attribute section");
            }
            var value = Arrays.copyOfRange(message, valueStart, valueStart + valueLength);
            attributes.add(new Attribute(typeValue, value));
            cursor = valueStart + paddedLength(valueLength);
        }
        return new StunMessage(messageType, magicCookie, transactionId, attributes);
    }

    /**
     * Writes the twenty-byte STUN header into {@code out} with the given attribute-section length.
     *
     * @param out                    the destination buffer, at least {@value #HEADER_LENGTH} bytes
     * @param attributeSectionLength the length of the attribute section, in bytes
     */
    private void writeHeader(byte[] out, int attributeSectionLength) {
        out[0] = (byte) (messageType >>> 8);
        out[1] = (byte) messageType;
        out[LENGTH_FIELD_OFFSET] = (byte) (attributeSectionLength >>> 8);
        out[LENGTH_FIELD_OFFSET + 1] = (byte) attributeSectionLength;
        out[4] = (byte) (magicCookie >>> 24);
        out[5] = (byte) (magicCookie >>> 16);
        out[6] = (byte) (magicCookie >>> 8);
        out[7] = (byte) magicCookie;
        System.arraycopy(transactionId, 0, out, 8, TRANSACTION_ID_LENGTH);
    }

    /**
     * Appends one attribute to an already-serialized message and patches the header length field.
     *
     * <p>The value is assumed to need no padding; the two integrity attributes are both multiples of
     * four bytes. The returned buffer is the message plus the four-byte attribute header and the value,
     * with the header length field rewritten to include the new attribute.
     *
     * @param message   the already-serialized message bytes
     * @param typeValue the sixteen-bit attribute type value
     * @param value     the attribute value bytes
     * @return the message with the attribute appended and the length field patched
     */
    private static byte[] appendAttribute(byte[] message, int typeValue, byte[] value) {
        var out = Arrays.copyOf(message, message.length + ATTRIBUTE_PADDING + value.length);
        var cursor = message.length;
        out[cursor] = (byte) (typeValue >>> 8);
        out[cursor + 1] = (byte) typeValue;
        out[cursor + 2] = (byte) (value.length >>> 8);
        out[cursor + 3] = (byte) value.length;
        System.arraycopy(value, 0, out, cursor + ATTRIBUTE_PADDING, value.length);
        var attributeSectionLength = out.length - HEADER_LENGTH;
        out[LENGTH_FIELD_OFFSET] = (byte) (attributeSectionLength >>> 8);
        out[LENGTH_FIELD_OFFSET + 1] = (byte) attributeSectionLength;
        return out;
    }

    /**
     * Returns the four-byte-aligned length of an attribute value.
     *
     * @param valueLength the attribute value length, in bytes
     * @return {@code valueLength} rounded up to the next multiple of {@value #ATTRIBUTE_PADDING}
     */
    private static int paddedLength(int valueLength) {
        return (valueLength + ATTRIBUTE_PADDING - 1) & ~(ATTRIBUTE_PADDING - 1);
    }

    /**
     * Encodes a transport address into the STUN XOR-MAPPED-ADDRESS attribute value.
     *
     * <p>The value is the RFC 8489 section 14.2 form: a zero reserved byte, the address family
     * ({@value #ADDRESS_FAMILY_IPV4} for IPv4, {@value #ADDRESS_FAMILY_IPV6} for IPv6), the port XORed with
     * the high sixteen bits of the {@link #MAGIC_COOKIE magic cookie}, and the address XORed with the magic
     * cookie (IPv4) or the magic cookie followed by the transaction id (IPv6). The same form is what the
     * WhatsApp relay path carries under its private attribute type and what the RFC ICE path carries under
     * the standard type, so this single encoder serves both; only the attribute type the value is wrapped in
     * differs.
     *
     * {@snippet :
     *   value[0]      = 0x00                                    // reserved
     *   value[1]      = family                                  // 0x01 IPv4, 0x02 IPv6
     *   value[2..4]   = port  ^ (MAGIC_COOKIE >>> 16)           // big-endian
     *   value[4..]    = addr  ^ (MAGIC_COOKIE || transactionId) // cookie for the first 4 bytes, then txid
     * }
     *
     * @param address       the transport address to encode
     * @param transactionId the {@value #TRANSACTION_ID_LENGTH}-byte transaction id of the carrying message,
     *                      used to XOR the trailing twelve bytes of an IPv6 address
     * @return the XOR-MAPPED-ADDRESS attribute value bytes (eight bytes for IPv4, twenty for IPv6)
     * @throws NullPointerException     if {@code address} or {@code transactionId} is {@code null}
     * @throws IllegalArgumentException if {@code transactionId} is not {@value #TRANSACTION_ID_LENGTH} bytes
     * @implNote This implementation reproduces the IPv4 XOR-MAPPED-ADDRESS writer of {@code fn4842}
     *           ({@code attr type 0x16}, length 8, family byte 1, {@code port ^ 0x2112},
     *           {@code addr ^ 0x2112a442}) and its IPv6 length-{@code 0x14} branch from the wa-voip WASM
     *           module {@code ff-tScznZ8P}; the IPv4 address XOR uses the magic cookie alone (no transaction
     *           id), so the captured relay value {@code 00 01 2c 84 3e 1f f2 7d} decodes to family 1, port
     *           {@code 0x2c84 ^ 0x2112 = 3478}, address {@code 0x3e1ff27d ^ 0x2112a442 = 31.13.86.63}.
     */
    public static byte[] encodeXorMappedAddress(InetSocketAddress address, byte[] transactionId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        if (transactionId.length != TRANSACTION_ID_LENGTH) {
            throw new IllegalArgumentException("transactionId must be " + TRANSACTION_ID_LENGTH
                    + " bytes, got " + transactionId.length);
        }
        var rawAddress = address.getAddress().getAddress();
        var isIpv6 = address.getAddress() instanceof Inet6Address;
        var value = new byte[ATTRIBUTE_PADDING + rawAddress.length];
        value[0] = 0;
        value[1] = (byte) (isIpv6 ? ADDRESS_FAMILY_IPV6 : ADDRESS_FAMILY_IPV4);
        var xorPort = address.getPort() ^ (MAGIC_COOKIE >>> 16);
        value[2] = (byte) (xorPort >>> 8);
        value[3] = (byte) xorPort;
        var cookieBytes = new byte[]{
                (byte) (MAGIC_COOKIE >>> 24),
                (byte) (MAGIC_COOKIE >>> 16),
                (byte) (MAGIC_COOKIE >>> 8),
                (byte) MAGIC_COOKIE
        };
        for (var index = 0; index < rawAddress.length; index++) {
            var mask = index < 4 ? cookieBytes[index] : transactionId[index - 4];
            value[ATTRIBUTE_PADDING + index] = (byte) (rawAddress[index] ^ mask);
        }
        return value;
    }

    /**
     * Reads a sixteen-bit big-endian unsigned integer from {@code buffer} at {@code offset}.
     *
     * @param buffer the source buffer
     * @param offset the index of the high byte
     * @return the unsigned sixteen-bit value, in {@code 0..65535}
     */
    private static int readUnsignedShort(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 8) | (buffer[offset + 1] & 0xFF);
    }

    /**
     * Reads a thirty-two-bit big-endian integer from {@code buffer} at {@code offset}.
     *
     * @param buffer the source buffer
     * @param offset the index of the most significant byte
     * @return the thirty-two-bit value
     */
    private static int readInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 24)
                | ((buffer[offset + 1] & 0xFF) << 16)
                | ((buffer[offset + 2] & 0xFF) << 8)
                | (buffer[offset + 3] & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof StunMessage that
                && this.messageType == that.messageType
                && this.magicCookie == that.magicCookie
                && Arrays.equals(this.transactionId, that.transactionId)
                && this.attributes.equals(that.attributes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, magicCookie, Arrays.hashCode(transactionId), attributes);
    }

    @Override
    public String toString() {
        return "StunMessage[type=0x" + Integer.toHexString(messageType)
                + ", cookie=0x" + Integer.toHexString(magicCookie)
                + ", attributes=" + attributes.size() + ']';
    }

    /**
     * Represents one STUN attribute as its type and raw value, the type-length-value triple before
     * four-byte padding.
     *
     * <p>The {@link #type()} is the known {@link StunAttributeType}, or {@code null} for an attribute
     * whose sixteen-bit type the transport does not model; {@link #typeValue()} always holds the raw
     * sixteen-bit type so an unknown attribute round-trips. {@link #value()} is the value bytes without
     * the trailing padding the wire form adds; {@link #encode()} re-adds the four-byte padding.
     *
     * @param type      the known attribute type, or {@code null} for an unmodeled type
     * @param typeValue the raw sixteen-bit attribute type value
     * @param value     the attribute value bytes, without trailing padding; never {@code null}
     */
    public record Attribute(StunAttributeType type, int typeValue, byte[] value) {
        /**
         * Canonicalizes the attribute, copying the value defensively.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Attribute {
            Objects.requireNonNull(value, "value cannot be null");
            value = value.clone();
        }

        /**
         * Constructs an attribute from a raw sixteen-bit type and a value, resolving the known
         * {@link StunAttributeType} from the type value.
         *
         * @param typeValue the raw sixteen-bit attribute type value
         * @param value     the attribute value bytes, without trailing padding
         */
        public Attribute(int typeValue, byte[] value) {
            this(StunAttributeType.ofValue(typeValue), typeValue, value);
        }

        /**
         * Constructs an attribute from a known {@link StunAttributeType} and a value.
         *
         * @param type  the known attribute type
         * @param value the attribute value bytes, without trailing padding
         * @throws NullPointerException if {@code type} is {@code null}
         */
        public Attribute(StunAttributeType type, byte[] value) {
            this(Objects.requireNonNull(type, "type cannot be null"), type.value(), value);
        }

        /**
         * Returns a defensive copy of the attribute value bytes.
         *
         * @return a copy of the value bytes, without trailing padding
         */
        @Override
        public byte[] value() {
            return value.clone();
        }

        /**
         * Serializes this attribute to its wire bytes, the type-length-value triple followed by zero
         * padding to the next four-byte boundary.
         *
         * @return the padded attribute bytes
         */
        public byte[] encode() {
            var padded = (value.length + ATTRIBUTE_PADDING - 1) & ~(ATTRIBUTE_PADDING - 1);
            var out = new byte[ATTRIBUTE_PADDING + padded];
            out[0] = (byte) (typeValue >>> 8);
            out[1] = (byte) typeValue;
            out[2] = (byte) (value.length >>> 8);
            out[3] = (byte) value.length;
            System.arraycopy(value, 0, out, ATTRIBUTE_PADDING, value.length);
            return out;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || (obj instanceof Attribute that
                    && this.typeValue == that.typeValue
                    && Arrays.equals(this.value, that.value));
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeValue, Arrays.hashCode(value));
        }

        @Override
        public String toString() {
            return "Attribute[type=" + (type == null ? "0x" + Integer.toHexString(typeValue) : type)
                    + ", length=" + value.length + ']';
        }
    }
}
