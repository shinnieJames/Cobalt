package com.github.auties00.cobalt.model.mixin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.Instant;

/**
 * A protobuf mixin that converts between {@link Instant} and a {@code Long} representing
 * seconds since the epoch of 1970-01-01T00:00:00Z.
 */
@ProtobufMixin
public final class InstantSecondsMixin {
    /**
     * Converts an {@link Instant} to its epoch seconds representation.
     *
     * @param instant the instant to convert, or {@code null}
     * @return the number of seconds since the epoch, or {@code null} if
     *         {@code instant} is {@code null}
     */
    @ProtobufSerializer
    public static Long toSecondsLong(Instant instant) {
        return instant == null ? null : instant.getEpochSecond();
    }

    /**
     * Converts an epoch seconds {@code Long} value to an {@link Instant}.
     *
     * @param value the number of seconds since the epoch, or {@code null}
     * @return the corresponding {@link Instant}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static Instant fromSecondsLong(Long value) {
        return value == null ? null : Instant.ofEpochSecond(value);
    }

    /**
     * Converts an {@link Instant} to its epoch seconds representation
     * as an {@code Integer}.
     *
     * @param instant the instant to convert, or {@code null}
     * @return the number of seconds since the epoch, or {@code null} if
     *         {@code instant} is {@code null}
     */
    @ProtobufSerializer
    public static Integer toSecondsInt(Instant instant) {
        return instant == null ? null : (int) instant.getEpochSecond();
    }

    /**
     * Converts an epoch seconds {@code Integer} value to an {@link Instant}.
     *
     * @param value the number of seconds since the epoch, or {@code null}
     * @return the corresponding {@link Instant}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static Instant fromSecondsInt(Integer value) {
        return value == null ? null : Instant.ofEpochSecond(value);
    }
}
