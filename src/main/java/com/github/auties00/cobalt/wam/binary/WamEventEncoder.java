package com.github.auties00.cobalt.wam.binary;

import static com.github.auties00.cobalt.wam.binary.WamTags.*;

/**
 * An event-layer encoding facade on top of {@link WamEncoder} that
 * encapsulates the flag logic for event markers and event fields.
 *
 * <p>Generated {@code *Impl} classes call into this class instead of
 * computing {@link WamTags} flag combinations themselves. This keeps
 * the generated code readable and centralizes the flag semantics in
 * one place.
 *
 * <p>Every method pair follows the same two-phase pattern used
 * throughout the WAM encoding stack: a {@code xxxSize} method that
 * returns the exact byte count, and a {@code writeXxx} method that
 * writes into a pre-allocated buffer and returns the new offset.
 *
 * <p>This class is thread-safe as all methods are static and operate
 * on provided parameters without shared mutable state.
 *
 * @see WamEncoder
 * @see WamTags
 */
public final class WamEventEncoder {
    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private WamEventEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the number of bytes required to encode an event marker
     * with the given event identifier and sampling weight.
     *
     * <p>The weight is negated on the wire so the server can apply
     * statistical correction.
     *
     * @param eventId the numeric event identifier
     * @param weight  the sampling weight (written as {@code -weight})
     * @return the encoded size in bytes
     */
    public static int eventMarkerSize(int eventId, int weight) {
        return WamEncoder.intSize(eventId, -weight);
    }

    /**
     * Writes an event marker into the output array.
     *
     * @param eventId   the numeric event identifier
     * @param weight    the sampling weight (written as {@code -weight})
     * @param hasFields {@code true} if at least one field follows
     * @param output    the output byte array
     * @param offset    the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeEventMarker(int eventId, int weight, boolean hasFields, byte[] output, int offset) {
        return WamEncoder.writeInt(eventId, EVENT | (hasFields ? 0 : LAST), -weight, output, offset);
    }

    /**
     * Returns the number of bytes required to encode an integer field.
     *
     * @param fieldId the numeric field identifier
     * @param value   the integer value
     * @return the encoded size in bytes
     */
    public static int intFieldSize(int fieldId, long value) {
        return WamEncoder.intSize(fieldId, value);
    }

    /**
     * Writes an integer field into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param value   the integer value
     * @param hasMore {@code true} if more fields follow in this event
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeIntField(int fieldId, long value, boolean hasMore, byte[] output, int offset) {
        return WamEncoder.writeInt(fieldId, FIELD | (hasMore ? 0 : LAST), value, output, offset);
    }

    /**
     * Returns the number of bytes required to encode a boolean field.
     *
     * <p>Booleans are encoded as integer values {@code 0} or {@code 1},
     * both of which fit in the tag alone with zero payload bytes. The
     * size is computed for the worst case (value {@code 1}).
     *
     * @param fieldId the numeric field identifier
     * @return the encoded size in bytes
     */
    public static int boolFieldSize(int fieldId) {
        return WamEncoder.intSize(fieldId, 1);
    }

    /**
     * Writes a boolean field into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param value   the boolean value
     * @param hasMore {@code true} if more fields follow in this event
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeBoolField(int fieldId, boolean value, boolean hasMore, byte[] output, int offset) {
        return WamEncoder.writeInt(fieldId, FIELD | (hasMore ? 0 : LAST), value ? 1 : 0, output, offset);
    }

    /**
     * Returns the number of bytes required to encode a string field.
     *
     * @param fieldId the numeric field identifier
     * @param value   the string value, must not be {@code null}
     * @return the encoded size in bytes
     */
    public static int stringFieldSize(int fieldId, String value) {
        return WamEncoder.stringSize(fieldId, value);
    }

    /**
     * Writes a string field into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param value   the string value, must not be {@code null}
     * @param hasMore {@code true} if more fields follow in this event
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeStringField(int fieldId, String value, boolean hasMore, byte[] output, int offset) {
        return WamEncoder.writeString(fieldId, FIELD | (hasMore ? 0 : LAST), value, output, offset);
    }

    /**
     * Returns the number of bytes required to encode a float (double)
     * field.
     *
     * @param fieldId the numeric field identifier
     * @return the encoded size in bytes (tag + 8)
     */
    public static int floatFieldSize(int fieldId) {
        return WamEncoder.floatSize(fieldId);
    }

    /**
     * Writes a float (double) field into the output array.
     *
     * @param fieldId the numeric field identifier
     * @param value   the double-precision value
     * @param hasMore {@code true} if more fields follow in this event
     * @param output  the output byte array
     * @param offset  the current offset in the output array
     * @return the new offset after writing
     */
    public static int writeFloatField(int fieldId, double value, boolean hasMore, byte[] output, int offset) {
        return WamEncoder.writeFloat(fieldId, FIELD | (hasMore ? 0 : LAST), value, output, offset);
    }
}
