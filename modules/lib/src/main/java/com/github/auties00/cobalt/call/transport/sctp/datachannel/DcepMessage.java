package com.github.auties00.cobalt.call.transport.sctp.datachannel;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Sealed model of the two RFC 8832 DataChannel Establishment Protocol
 * messages — {@link Open DATA_CHANNEL_OPEN} and {@link Ack
 * DATA_CHANNEL_ACK} — together with their on-the-wire codec.
 *
 * <p>DCEP messages travel over the SCTP association on the same
 * stream as application data, distinguished by SCTP Payload Protocol
 * Identifier {@value #PPID_DCEP} (RFC 8831 §8). The {@link #encode()}
 * helper returns the bytes to ship; {@link #decode(byte[])} parses
 * the payload of an inbound chunk back into a typed message.
 *
 * <p>The wire layout of {@code DATA_CHANNEL_OPEN} (big-endian, RFC
 * 8832 §5.1):
 *
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Message Type |  Channel Type |             Priority          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Reliability Parameter                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Label Length          |       Protocol Length         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * /                             Label                             /
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * /                            Protocol                           /
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * <p>{@code DATA_CHANNEL_ACK} is a single byte: {@value #MSG_ACK}.
 */
public sealed interface DcepMessage {
    /**
     * SCTP Payload Protocol Identifier carrying DCEP messages
     * (RFC 8831 §8 — "WebRTC DCEP").
     */
    int PPID_DCEP = 50;

    /**
     * RFC 8832 §5.1 — message type for {@code DATA_CHANNEL_OPEN}.
     */
    byte MSG_OPEN = (byte) 0x03;

    /**
     * RFC 8832 §5.1 — message type for {@code DATA_CHANNEL_ACK}.
     */
    byte MSG_ACK = (byte) 0x02;

    /**
     * RFC 8832 §5.1 — fully reliable, ordered.
     */
    byte CHANNEL_RELIABLE = (byte) 0x00;

    /**
     * RFC 8832 §5.1 — partially reliable by retransmit count, ordered.
     */
    byte CHANNEL_PARTIAL_RELIABLE_REXMIT = (byte) 0x01;

    /**
     * RFC 8832 §5.1 — partially reliable by lifetime, ordered.
     */
    byte CHANNEL_PARTIAL_RELIABLE_TIMED = (byte) 0x02;

    /**
     * RFC 8832 §5.1 — fully reliable, unordered.
     */
    byte CHANNEL_RELIABLE_UNORDERED = (byte) 0x80;

    /**
     * RFC 8832 §5.1 — partially reliable by retransmit count, unordered.
     */
    byte CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED = (byte) 0x81;

    /**
     * RFC 8832 §5.1 — partially reliable by lifetime, unordered.
     */
    byte CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED = (byte) 0x82;

    /**
     * Encodes this message to its on-the-wire byte representation.
     *
     * @return the bytes ready to ship over PPID
     *         {@value #PPID_DCEP}
     */
    byte[] encode();

    /**
     * Decodes an inbound DCEP chunk payload into the right
     * {@code DcepMessage} subtype.
     *
     * @param bytes the inbound bytes (typically the full payload of a
     *              SCTP DATA chunk arriving on PPID
     *              {@value #PPID_DCEP})
     * @return the parsed message
     * @throws WhatsAppCallException.DataChannel if the payload is empty, has an
     *                              unknown message type, or is
     *                              truncated relative to the declared
     *                              field lengths
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    static DcepMessage decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        if (bytes.length == 0) {
            throw new WhatsAppCallException.DataChannel("DCEP payload is empty");
        }
        return switch (bytes[0]) {
            case MSG_ACK -> Ack.INSTANCE;
            case MSG_OPEN -> Open.decodeOpen(bytes);
            default -> throw new WhatsAppCallException.DataChannel(
                    "unknown DCEP message type: 0x" + Integer.toHexString(Byte.toUnsignedInt(bytes[0])));
        };
    }

    /**
     * Returns the {@code Channel Type} byte that encodes the
     * given {@link DataChannelOptions}'s reliability + ordering
     * combination per RFC 8832 §5.1.
     *
     * @param options the channel options
     * @return the wire byte
     */
    static byte channelType(DataChannelOptions options) {
        boolean unordered = !options.ordered();
        if (options.maxRetransmits().isPresent()) {
            return unordered ? CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED
                    : CHANNEL_PARTIAL_RELIABLE_REXMIT;
        }
        if (options.maxLifetimeMs().isPresent()) {
            return unordered ? CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED
                    : CHANNEL_PARTIAL_RELIABLE_TIMED;
        }
        return unordered ? CHANNEL_RELIABLE_UNORDERED : CHANNEL_RELIABLE;
    }

    /**
     * Returns whether the given {@code Channel Type} byte denotes
     * unordered delivery (high bit set per RFC 8832 §5.1).
     *
     * @param channelType the wire byte
     * @return {@code true} iff the high bit is set
     */
    static boolean isUnordered(byte channelType) {
        return (channelType & 0x80) != 0;
    }

    /**
     * The {@code DATA_CHANNEL_OPEN} variant.
     *
     * @param channelType          the channel-type byte (one of the
     *                             {@code CHANNEL_*} constants)
     * @param priority             the channel priority (0..65535,
     *                             RFC 8831 §6.4)
     * @param reliabilityParameter the {@code Reliability Parameter}
     *                             field — interpretation depends on
     *                             {@code channelType}: 0 for
     *                             reliable, max retransmits for
     *                             rexmit channels, max lifetime ms
     *                             for timed channels
     * @param label                the channel label (UTF-8)
     * @param protocol             the application-level subprotocol
     *                             (UTF-8, possibly empty)
     */
    record Open(
            byte channelType,
            int priority,
            long reliabilityParameter,
            String label,
            String protocol
    ) implements DcepMessage {
        /**
         * Compact constructor — null-checks strings and validates
         * numeric ranges.
         */
        public Open {
            Objects.requireNonNull(label, "label cannot be null");
            Objects.requireNonNull(protocol, "protocol cannot be null");
            if (priority < 0 || priority > 0xFFFF) {
                throw new IllegalArgumentException(
                        "priority out of range [0, 65535]: " + priority);
            }
            if (reliabilityParameter < 0 || reliabilityParameter > 0xFFFFFFFFL) {
                throw new IllegalArgumentException(
                        "reliabilityParameter out of range [0, 2^32): " + reliabilityParameter);
            }
            byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
            byte[] protocolBytes = protocol.getBytes(StandardCharsets.UTF_8);
            if (labelBytes.length > 0xFFFF) {
                throw new IllegalArgumentException(
                        "label exceeds 65535 UTF-8 bytes");
            }
            if (protocolBytes.length > 0xFFFF) {
                throw new IllegalArgumentException(
                        "protocol exceeds 65535 UTF-8 bytes");
            }
        }

        /**
         * Builds an {@code Open} from a {@link DataChannelOptions} +
         * label, choosing the correct channel-type byte and
         * reliability-parameter encoding per RFC 8832 §5.1.
         *
         * @param label   the channel label
         * @param options the channel options
         * @return the encoded {@code Open}
         */
        public static Open from(String label, DataChannelOptions options) {
            byte channelType = DcepMessage.channelType(options);
            long reliability = options.maxRetransmits().orElseGet(() ->
                    options.maxLifetimeMs().orElse(0));
            return new Open(channelType, options.priority(), reliability, label, options.protocol());
        }

        /**
         * Returns whether this open message asks for unordered
         * delivery.
         *
         * @return {@code true} iff the channel type's high bit is set
         */
        public boolean unordered() {
            return DcepMessage.isUnordered(channelType);
        }

        /**
         * Returns the max-retransmits hint encoded in this open, or
         * empty when the channel-type is not rexmit-based.
         *
         * @return the max retransmits, or empty
         */
        public OptionalInt maxRetransmits() {
            return switch (channelType) {
                case CHANNEL_PARTIAL_RELIABLE_REXMIT,
                     CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED ->
                        OptionalInt.of((int) reliabilityParameter);
                default -> OptionalInt.empty();
            };
        }

        /**
         * Returns the max-lifetime hint (ms) encoded in this open, or
         * empty when the channel-type is not timed.
         *
         * @return the max lifetime ms, or empty
         */
        public OptionalInt maxLifetimeMs() {
            return switch (channelType) {
                case CHANNEL_PARTIAL_RELIABLE_TIMED,
                     CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED ->
                        OptionalInt.of((int) reliabilityParameter);
                default -> OptionalInt.empty();
            };
        }

        @Override
        public byte[] encode() {
            byte[] labelBytes = label.getBytes(StandardCharsets.UTF_8);
            byte[] protocolBytes = protocol.getBytes(StandardCharsets.UTF_8);
            int size = 12 + labelBytes.length + protocolBytes.length;
            ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
            buf.put(MSG_OPEN);
            buf.put(channelType);
            buf.putShort((short) priority);
            buf.putInt((int) reliabilityParameter);
            buf.putShort((short) labelBytes.length);
            buf.putShort((short) protocolBytes.length);
            buf.put(labelBytes);
            buf.put(protocolBytes);
            return buf.array();
        }

        /**
         * Parses the {@code DATA_CHANNEL_OPEN} body — caller has
         * already verified the leading message-type byte.
         *
         * @param bytes the full payload, with byte 0 = {@link #MSG_OPEN}
         * @return the parsed {@code Open}
         */
        private static Open decodeOpen(byte[] bytes) {
            if (bytes.length < 12) {
                throw new WhatsAppCallException.DataChannel(
                        "DATA_CHANNEL_OPEN truncated (need 12 header bytes, have " + bytes.length + ")");
            }
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            buf.get();
            byte channelType = buf.get();
            int priority = Short.toUnsignedInt(buf.getShort());
            long reliability = Integer.toUnsignedLong(buf.getInt());
            int labelLen = Short.toUnsignedInt(buf.getShort());
            int protocolLen = Short.toUnsignedInt(buf.getShort());
            int expected = 12 + labelLen + protocolLen;
            if (bytes.length < expected) {
                throw new WhatsAppCallException.DataChannel(
                        "DATA_CHANNEL_OPEN truncated: declared " + labelLen + "+" + protocolLen
                                + " bytes of label/protocol but payload is " + bytes.length);
            }
            byte[] labelBytes = new byte[labelLen];
            buf.get(labelBytes);
            byte[] protocolBytes = new byte[protocolLen];
            buf.get(protocolBytes);
            return new Open(channelType, priority, reliability,
                    new String(labelBytes, StandardCharsets.UTF_8),
                    new String(protocolBytes, StandardCharsets.UTF_8));
        }
    }

    /**
     * The {@code DATA_CHANNEL_ACK} variant — carries no payload other
     * than the leading message-type byte. Use the cached
     * {@link #INSTANCE} rather than allocating a fresh value.
     */
    record Ack() implements DcepMessage {
        /**
         * The single-byte wire form
         */
        private static final byte[] WIRE = {MSG_ACK};

        /**
         * Canonical singleton — every {@code Ack} compares equal to
         * this instance, so callers can compare with {@code ==} or
         * {@code equals}.
         */
        public static final Ack INSTANCE = new Ack();

        @Override
        public byte[] encode() {
            return WIRE.clone();
        }
    }
}
