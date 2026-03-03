package com.github.auties00.cobalt.model.mixin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.Instant;

/**
 * A protobuf mixin that converts between {@link Instant} and a {@code Long} representing
 * milliseconds since the epoch of 1970-01-01T00:00:00Z.
 */
@ProtobufMixin
public final class InstantMillisMixin {
    /**
     * Converts an {@link Instant} to its epoch milliseconds representation.
     *
     * @param instant the instant to convert, or {@code null}
     * @return the number of milliseconds since the epoch, or {@code null} if
     *         {@code instant} is {@code null}
     */
    @ProtobufSerializer
    public static Long toMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * Converts an epoch milliseconds {@code Long} value to an {@link Instant}.
     *
     * @param value the number of milliseconds since the epoch, or {@code null}
     * @return the corresponding {@link Instant}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static Instant fromMillis(Long value) {
        return value == null ? null : Instant.ofEpochMilli(value);
    }
}
