package com.github.auties00.cobalt.node;

import com.github.auties00.cobalt.exception.WhatsAppMalformedJidException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.model.ProtobufString;

import java.util.*;

/**
 * Represents a single attribute value attached to a {@link Node}.
 *
 * @apiNote
 * Stanza attributes carry values in one of three concrete shapes on
 * the wire: a UTF-8 string, a binary blob, or a fully parsed
 * {@link Jid}. This sealed interface enumerates those shapes and
 * exposes a uniform set of conversion helpers ({@code toString},
 * {@code toBytes}, {@code toJid}, {@code toLong}, {@code toInt},
 * {@code toDouble}) so callers reading attributes through
 * {@link Node} do not need to switch on the underlying
 * representation. Attribute values are produced through
 * {@link NodeBuilder} and consumed through the
 * {@code getAttributeAs...} family on {@link Node}.
 *
 * @implNote
 * This implementation surfaces three concrete records to match the
 * three wire shapes produced by {@code WAWap.le} (single-byte or
 * dictionary tokens and packed shapes all decode to strings) and
 * {@code WAWap.te} (raw byte blobs and JIDs). Each variant
 * implements every conversion; conversions that do not apply
 * return {@link Optional#empty()} or the empty {@code OptionalInt}
 * /{@code OptionalLong}/{@code OptionalDouble} sentinels rather
 * than throwing, so callers can compose conversion attempts
 * without a try/catch ladder.
 *
 * @see Node
 * @see NodeBuilder
 * @see Jid
 */
@WhatsAppWebModule(moduleName = "WAWap")
@WhatsAppWebModule(moduleName = "WAXmlNode")
public sealed interface NodeAttribute {
    /**
     * Returns the string representation of this attribute value.
     *
     * @apiNote
     * For {@link TextAttribute} returns the stored string verbatim;
     * for {@link JidAttribute} returns the canonical
     * {@link Jid#toString()} form; for {@link BytesAttribute}
     * decodes the bytes under the platform default charset.
     *
     * @return a non null string view of the value
     */
    String toString();

    /**
     * Returns the byte array representation of this attribute value.
     *
     * @apiNote
     * For {@link TextAttribute} and {@link JidAttribute} returns the
     * UTF-8 encoding of the textual view; for {@link BytesAttribute}
     * returns the stored blob without copying.
     *
     * @return a non null byte view of the value
     */
    byte[] toBytes();

    /**
     * Returns the value parsed as a {@link Jid} when possible.
     *
     * @apiNote
     * Always succeeds for {@link JidAttribute}; for
     * {@link TextAttribute} and {@link BytesAttribute} the parse
     * may fail and the method returns {@link Optional#empty()}.
     *
     * @return an {@link Optional} that holds the parsed JID, or
     *         empty when the value cannot be parsed
     */
    Optional<Jid> toJid();

    /**
     * Returns the value parsed as a {@code long} when possible.
     *
     * @apiNote
     * Parses the textual view through {@link Long#parseLong(String)};
     * {@link JidAttribute} always returns {@link OptionalLong#empty()}
     * because a JID has no integer representation.
     *
     * @return an {@link OptionalLong} that holds the parsed value,
     *         or empty when parsing fails
     */
    OptionalLong toLong();

    /**
     * Returns the value parsed as an {@code int} when possible.
     *
     * @apiNote
     * Parses the textual view through {@link Integer#parseInt(String)};
     * {@link JidAttribute} always returns {@link OptionalInt#empty()}.
     *
     * @return an {@link OptionalInt} that holds the parsed value,
     *         or empty when parsing fails
     */
    OptionalInt toInt();

    /**
     * Returns the value parsed as a {@code double} when possible.
     *
     * @apiNote
     * Parses the textual view through {@link Double#parseDouble(String)};
     * {@link JidAttribute} always returns {@link OptionalDouble#empty()}.
     *
     * @return an {@link OptionalDouble} that holds the parsed value,
     *         or empty when parsing fails
     */
    OptionalDouble toDouble();

    /**
     * Attribute variant whose value is a UTF-8 string.
     *
     * @apiNote
     * The default variant produced by {@link NodeBuilder}'s text,
     * number, and boolean overloads. Covers the majority of
     * attributes on the wire (identifiers, type discriminators,
     * state flags).
     *
     * @param value the textual value
     */
    record TextAttribute(String value) implements NodeAttribute {
        /**
         * Builds a text attribute, rejecting a {@code null} value.
         *
         * @apiNote
         * Called by {@link NodeBuilder} and by {@link Node}'s decoder
         * path; both ensure {@code value} is non-null before reaching
         * here, so the guard is a defence-in-depth check.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public TextAttribute {
            Objects.requireNonNull(value, "value cannot be null");
        }

        /**
         * Returns the stored string verbatim.
         *
         * @return the non null string value
         */
        @Override
        public String toString() {
            return value;
        }

        /**
         * Returns the UTF-8 encoding of the stored string.
         *
         * @apiNote
         * Allocates a fresh byte array on each call; callers that
         * read the same attribute repeatedly should cache the
         * result.
         *
         * @return a non null byte array holding the UTF-8 encoding
         */
        @Override
        public byte[] toBytes() {
            return value.getBytes();
        }

        /**
         * Parses the stored string as a {@link Jid}.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the string is not a
         * valid JID rather than throwing, so callers can use the
         * fluent {@code getAttributeAsJid} helpers on {@link Node}
         * without wrapping in a try/catch.
         *
         * @return an {@link Optional} that holds the parsed JID, or
         *         empty when the string is not a valid JID
         */
        @Override
        public Optional<Jid> toJid() {
            try {
                var result = Jid.of(value);
                return Optional.of(result);
            }catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        /**
         * Parses the stored string as a {@code long}.
         *
         * @return an {@link OptionalLong} that holds the parsed value,
         *         or empty when the string is not a valid long literal
         */
        @Override
        public OptionalLong toLong() {
            try {
                var result = Long.parseLong(value);
                return OptionalLong.of(result);
            }catch (NumberFormatException exception) {
                return OptionalLong.empty();
            }
        }

        /**
         * Parses the stored string as an {@code int}.
         *
         * @return an {@link OptionalInt} that holds the parsed value,
         *         or empty when the string is not a valid int literal
         */
        @Override
        public OptionalInt toInt() {
            try {
                var result = Integer.parseInt(value);
                return OptionalInt.of(result);
            }catch (NumberFormatException exception) {
                return OptionalInt.empty();
            }
        }

        /**
         * Parses the stored string as a {@code double}.
         *
         * @return an {@link OptionalDouble} that holds the parsed value,
         *         or empty when the string is not a valid double literal
         */
        @Override
        public OptionalDouble toDouble() {
            try {
                var result = Double.parseDouble(value);
                return OptionalDouble.of(result);
            }catch (NumberFormatException exception) {
                return OptionalDouble.empty();
            }
        }
    }

    /**
     * Attribute variant whose value is a fully parsed {@link Jid}.
     *
     * @apiNote
     * Produced by {@link NodeBuilder#attribute(String, com.github.auties00.cobalt.model.jid.JidProvider)}
     * for any attribute referencing a WhatsApp user, group, device,
     * or other addressable entity, and by {@link Node}'s decoder
     * when the wire shape is one of {@link com.github.auties00.cobalt.node.binary.NodeTags#JID_PAIR},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#AD_JID},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#JID_INTEROP},
     * or {@link com.github.auties00.cobalt.node.binary.NodeTags#JID_FB}.
     *
     * @param value the JID value
     * @see Jid
     */
    record JidAttribute(Jid value) implements NodeAttribute {
        /**
         * Builds a JID attribute, rejecting a {@code null} value.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public JidAttribute {
            Objects.requireNonNull(value, "value cannot be null");
        }

        /**
         * Returns the canonical string form of the stored JID.
         *
         * @apiNote
         * Equivalent to {@link Jid#toString()}; used by callers that
         * want the {@code user@server} or {@code user:device@server}
         * shape regardless of the underlying JID variant.
         *
         * @return a non null string view of the JID
         */
        @Override
        public String toString() {
            return value.toString();
        }

        /**
         * Returns the UTF-8 encoding of the canonical JID string.
         *
         * @return a non null byte array holding the UTF-8 encoding
         */
        @Override
        public byte[] toBytes() {
            return value.toString().getBytes();
        }

        /**
         * Returns the stored JID.
         *
         * @return an {@link Optional} that always holds the stored JID
         */
        @Override
        public Optional<Jid> toJid() {
            return Optional.of(value);
        }

        /**
         * Returns {@link OptionalLong#empty()} unconditionally because a
         * JID has no integer parse.
         *
         * @return {@link OptionalLong#empty()}
         */
        @Override
        public OptionalLong toLong() {
            return OptionalLong.empty();
        }

        /**
         * Returns {@link OptionalInt#empty()} unconditionally because a
         * JID has no integer parse.
         *
         * @return {@link OptionalInt#empty()}
         */
        @Override
        public OptionalInt toInt() {
            return OptionalInt.empty();
        }

        /**
         * Returns {@link OptionalDouble#empty()} unconditionally because
         * a JID has no floating-point parse.
         *
         * @return {@link OptionalDouble#empty()}
         */
        @Override
        public OptionalDouble toDouble() {
            return OptionalDouble.empty();
        }
    }

    /**
     * Attribute variant whose value is an opaque binary blob.
     *
     * @apiNote
     * Produced by {@link Node}'s decoder when the wire shape is one
     * of {@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_8},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_20},
     * or {@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_32},
     * and by {@link NodeBuilder#attribute(String, byte[])} for raw
     * payloads (cryptographic hashes, protocol tokens, already
     * encoded blobs). Decoding the blob to a string uses the
     * platform default charset; the resulting text may be
     * non-printable when the bytes are not valid UTF-8.
     *
     * @param value the binary value
     */
    record BytesAttribute(byte[] value) implements NodeAttribute {
        /**
         * Builds a bytes attribute, rejecting a {@code null} value.
         *
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public BytesAttribute {
            Objects.requireNonNull(value, "value cannot be null");
        }

        /**
         * Returns the bytes decoded as a string under the platform
         * default charset.
         *
         * @apiNote
         * Useful when the blob is known to carry textual data but
         * arrived through a binary wire shape; lossy for genuinely
         * binary payloads.
         *
         * @return a non null string decoded from the binary data
         */
        @Override
        public String toString() {
            return new String(value);
        }

        /**
         * Returns the underlying byte array without copying.
         *
         * @apiNote
         * The caller must not mutate the returned array; mutation
         * would corrupt every future read of the same attribute.
         *
         * @return the non null byte array
         */
        @Override
        public byte[] toBytes() {
            return value;
        }

        /**
         * Parses the binary blob as a {@link Jid} through the
         * {@link ProtobufString#lazy(byte[])} fast path.
         *
         * @apiNote
         * The lazy {@link ProtobufString} avoids the intermediate
         * UTF-8 decode that {@link String#String(byte[])} would do;
         * the JID parser walks the underlying bytes directly.
         *
         * @return an {@link Optional} that holds the parsed JID, or
         *         empty when the bytes do not decode to a valid JID
         */
        @Override
        public Optional<Jid> toJid() {
            try {
                var result = Jid.of(ProtobufString.lazy(value));
                return Optional.of(result);
            }catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        /**
         * Parses the bytes as text and then as a {@code long}.
         *
         * @return an {@link OptionalLong} that holds the parsed value,
         *         or empty when the text is not a valid long literal
         */
        @Override
        public OptionalLong toLong() {
            try {
                var result = Long.parseLong(toString());
                return OptionalLong.of(result);
            }catch (NumberFormatException exception) {
                return OptionalLong.empty();
            }
        }

        /**
         * Parses the bytes as text and then as an {@code int}.
         *
         * @return an {@link OptionalInt} that holds the parsed value,
         *         or empty when the text is not a valid int literal
         */
        @Override
        public OptionalInt toInt() {
            try {
                var result = Integer.parseInt(toString());
                return OptionalInt.of(result);
            }catch (NumberFormatException exception) {
                return OptionalInt.empty();
            }
        }

        /**
         * Parses the bytes as text and then as a {@code double}.
         *
         * @return an {@link OptionalDouble} that holds the parsed value,
         *         or empty when the text is not a valid double literal
         */
        @Override
        public OptionalDouble toDouble() {
            try {
                var result = Double.parseDouble(toString());
                return OptionalDouble.of(result);
            }catch (NumberFormatException exception) {
                return OptionalDouble.empty();
            }
        }
    }
}
