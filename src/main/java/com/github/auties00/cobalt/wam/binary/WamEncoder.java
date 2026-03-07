package com.github.auties00.cobalt.wam.binary;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static com.github.auties00.cobalt.wam.binary.WamTags.*;

/**
 * A low-level encoding toolkit for the WhatsApp Metrics (WAM) custom binary
 * protocol.
 *
 * <p>This class provides paired size-calculation and write methods for every
 * primitive value type in the WAM wire format: integers, floats, strings, and
 * nulls. It is designed as pure infrastructure for annotation-processor
 * generated code: each {@code @WamEvent}-annotated interface gets a companion
 * {@code *Impl} class at compile time whose {@code sizeOf} and {@code encode}
 * methods contain hardcoded calls into these primitives with literal field IDs
 * and pre-determined types. No reflection is involved at runtime.
 *
 * <p>The encoding follows the same two-phase pattern used by
 * {@link com.github.auties00.cobalt.node.binary.NodeEncoder NodeEncoder}:
 * <ol>
 *   <li>Compute the exact byte count with the {@code xxxSize} methods
 *   <li>Allocate a {@code byte[]} of exactly that size
 *   <li>Write bytes with the {@code writeXxx} methods, threading the offset
 * </ol>
 *
 * <p>Every write method returns the new offset after the bytes it wrote,
 * enabling chained calls:
 * <pre>{@code
 *     int off = 0;
 *     off = WamEncoder.writeInt(2172, EVENT, -1, buf, off);
 *     off = WamEncoder.writeFloat(1, FIELD, 42.5, buf, off);
 *     off = WamEncoder.writeString(3, FIELD, "pdf", buf, off);
 * }</pre>
 *
 * <p>Multi-byte writes (int16, int32, int64, float64, and string length
 * prefixes) use {@link VarHandle} with little-endian byte order to match
 * the WhatsApp Web {@code Binary} constructor's {@code littleEndian=true}
 * parameter, performing single-instruction stores where the platform
 * supports it and avoiding per-byte shifting.
 *
 * <p>This class is thread-safe as all methods are static and operate on
 * provided parameters without shared mutable state.
 *
 * @see com.github.auties00.cobalt.wam.annotation.WamEvent
 * @see com.github.auties00.cobalt.wam.annotation.WamProperty
 * @see WamTags
 */
public final class WamEncoder {
    private static final VarHandle SHORT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private WamEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the number of bytes required to encode a tag with the given
     * field identifier.
     *
     * <p>Field identifiers below 256 use a one-byte encoding (2 bytes total:
     * flags + uint8 id). Identifiers 256 and above set the {@link WamTags#WIDE_ID}
     * flag and use a two-byte encoding (3 bytes total: flags + uint16 id).
     *
     * @param fieldId the numeric field or event identifier
     * @return {@code 2} if {@code fieldId < 256}, otherwise {@code 3}
     */
    public static int tagSize(int fieldId) {
        return fieldId < 256 ? 2 : 3;
    }

    /**
     * Writes a tag (flags byte + field identifier) into the output array.
     *
     * @param fieldId the numeric field or event identifier
     * @param flags   the pre-computed flags byte (role + value-type + LAST)
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeTag(int fieldId, int flags, byte[] output, int offset) {
        if (fieldId < 256) {
            output[offset++] = (byte) flags;
            output[offset++] = (byte) fieldId;
        } else {
            output[offset++] = (byte) (flags | WIDE_ID);
            SHORT_HANDLE.set(output, offset, (short) fieldId);
            offset += 2;
        }
        return offset;
    }

    /**
     * Returns the number of bytes required to encode a null value with the
     * given field identifier.
     *
     * <p>A null value is encoded as a tag-only entry with value-type
     * {@code 0x00} and no payload bytes.
     *
     * @param fieldId the numeric field identifier
     * @return the number of bytes required
     */
    public static int nullSize(int fieldId) {
        return tagSize(fieldId);
    }

    /**
     * Writes a null value entry into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param flags   the role and continuation flags (e.g. {@code GLOBAL})
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeNull(int fieldId, int flags, byte[] output, int offset) {
        return writeTag(fieldId, flags | VALUE_NULL, output, offset);
    }

    /**
     * Returns the number of bytes required to encode an integer value with
     * the given field identifier.
     *
     * <p>The encoding uses the minimum number of bytes for the value:
     * <ul>
     *   <li>Values {@code 0} and {@code 1} are encoded in the tag alone
     *       (zero payload bytes)
     *   <li>Values in {@code [-128, 127]} use 1 payload byte (int8)
     *   <li>Values in {@code [-32768, 32767]} use 2 payload bytes (int16)
     *   <li>Values in {@code [-2^31, 2^31-1]} use 4 payload bytes (int32)
     *   <li>All other values use 8 payload bytes (int64)
     * </ul>
     *
     * @param fieldId the numeric field identifier
     * @param value   the integer value to encode
     * @return the number of bytes required
     */
    public static int intSize(int fieldId, long value) {
        return tagSize(fieldId) + intPayloadSize(value);
    }

    /**
     * Returns the number of payload bytes for an integer value (excluding
     * the tag).
     *
     * @param value the integer value
     * @return the payload byte count (0, 1, 2, 4, or 8)
     */
    private static int intPayloadSize(long value) {
        if (value == 0 || value == 1) {
            return 0;
        } else if (value >= -128 && value < 128) {
            return 1;
        } else if (value >= -32768 && value < 32768) {
            return 2;
        } else if (value >= -2147483648L && value < 2147483648L) {
            return 4;
        } else {
            return 8;
        }
    }

    /**
     * Writes an integer value entry into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param flags   the role and continuation flags
     * @param value   the integer value to encode
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeInt(int fieldId, int flags, long value, byte[] output, int offset) {
        if (value == 0) {
            return writeTag(fieldId, flags | VALUE_INT_0, output, offset);
        } else if (value == 1) {
            return writeTag(fieldId, flags | VALUE_INT_1, output, offset);
        } else if (value >= -128 && value < 128) {
            offset = writeTag(fieldId, flags | VALUE_INT8, output, offset);
            output[offset++] = (byte) value;
            return offset;
        } else if (value >= -32768 && value < 32768) {
            offset = writeTag(fieldId, flags | VALUE_INT16, output, offset);
            SHORT_HANDLE.set(output, offset, (short) value);
            return offset + 2;
        } else if (value >= -2147483648L && value < 2147483648L) {
            offset = writeTag(fieldId, flags | VALUE_INT32, output, offset);
            INT_HANDLE.set(output, offset, (int) value);
            return offset + 4;
        } else {
            return writeFloat(fieldId, flags, (double) value, output, offset);
        }
    }

    /**
     * Returns the number of bytes required to encode a float (double) value
     * with the given field identifier.
     *
     * <p>Floats are always encoded as 8-byte IEEE 754 double-precision
     * values in little-endian byte order.
     *
     * @param fieldId the numeric field identifier
     * @return the number of bytes required (tag + 8)
     */
    public static int floatSize(int fieldId) {
        return tagSize(fieldId) + 8;
    }

    /**
     * Writes a float (double) value entry into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param flags   the role and continuation flags
     * @param value   the double-precision value to encode
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeFloat(int fieldId, int flags, double value, byte[] output, int offset) {
        offset = writeTag(fieldId, flags | VALUE_FLOAT64, output, offset);
        LONG_HANDLE.set(output, offset, Double.doubleToRawLongBits(value));
        return offset + 8;
    }

    /**
     * Returns the number of bytes required to encode a string value with
     * the given field identifier.
     *
     * <p>The encoding consists of a tag, a variable-width length prefix
     * (uint8, uint16, or uint32 depending on the UTF-8 byte count), and
     * the raw UTF-8 bytes.
     *
     * @param fieldId the numeric field identifier
     * @param value   the string to encode, must not be {@code null}
     * @return the number of bytes required
     */
    public static int stringSize(int fieldId, String value) {
        var utf8len = utf8Length(value);
        return tagSize(fieldId) + stringLengthPrefixSize(utf8len) + utf8len;
    }

    /**
     * Returns the number of bytes required for the string length prefix.
     *
     * @param utf8len the UTF-8 byte count of the string
     * @return {@code 1} if less than 256, {@code 2} if less than 65536,
     *         otherwise {@code 4}
     */
    private static int stringLengthPrefixSize(int utf8len) {
        if (utf8len < 256) {
            return 1;
        } else if (utf8len < 65536) {
            return 2;
        } else {
            return 4;
        }
    }

    /**
     * Writes a string value entry into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param flags   the role and continuation flags
     * @param value   the string to encode, must not be {@code null}
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeString(int fieldId, int flags, String value, byte[] output, int offset) {
        var encoded = value.getBytes(StandardCharsets.UTF_8);
        var encodedLength = encoded.length;
        if (encodedLength < 256) {
            offset = writeTag(fieldId, flags | VALUE_STR8, output, offset);
            output[offset++] = (byte) encodedLength;
        } else if (encodedLength < 65536) {
            offset = writeTag(fieldId, flags | VALUE_STR16, output, offset);
            SHORT_HANDLE.set(output, offset, (short) encodedLength);
            offset += 2;
        } else {
            offset = writeTag(fieldId, flags | VALUE_STR32, output, offset);
            INT_HANDLE.set(output, offset, encodedLength);
            offset += 4;
        }
        System.arraycopy(encoded, 0, output, offset, encodedLength);
        return offset + encodedLength;
    }

    /**
     * Calculates the number of bytes required to represent a string in
     * UTF-8 encoding.
     *
     * <p>This method iterates character-by-character, correctly handling
     * surrogate pairs (4 bytes), BMP characters above U+007F, and ASCII.
     *
     * @param input the string to measure
     * @return the UTF-8 byte count
     */
    public static int utf8Length(String input) {
        var length = 0;
        var len = input.length();
        for (var i = 0; i < len; i++) {
            var ch = input.charAt(i);
            if (ch <= 0x7F) {
                length++;
            } else if (ch <= 0x7FF) {
                length += 2;
            } else if (Character.isHighSurrogate(ch)) {
                length += 4;
                i++;
            } else {
                length += 3;
            }
        }
        return length;
    }
}
