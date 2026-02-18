package com.github.auties00.cobalt.model.mixin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.Duration;

/**
 * A protobuf mixin that converts between {@link Duration} and a {@code Long} representing
 * seconds.
 */
@ProtobufMixin
public final class DurationSecondsMixin {
    /**
     * Converts a {@link Duration} to its seconds representation.
     *
     * @param duration the duration to convert, or {@code null}
     * @return the number of seconds, or {@code null} if {@code duration} is {@code null}
     */
    @ProtobufSerializer
    public static Long toSeconds(Duration duration) {
        return duration == null ? null : duration.toSeconds();
    }

    /**
     * Converts a seconds {@code Long} value to a {@link Duration}.
     *
     * @param value the number of seconds, or {@code null}
     * @return the corresponding {@link Duration}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static Duration fromSeconds(Long value) {
        return value == null ? null : Duration.ofSeconds(value);
    }
}
