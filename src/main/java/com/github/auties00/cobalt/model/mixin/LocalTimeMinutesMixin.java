package com.github.auties00.cobalt.model.mixin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.LocalTime;

/**
 * A protobuf mixin that converts between {@link LocalTime} and a {@code Long} representing
 * minutes from midnight.
 */
@ProtobufMixin
public final class LocalTimeMinutesMixin {
    /**
     * Converts a {@link LocalTime} to its minutes-from-midnight representation.
     *
     * @param time the local time, or {@code null}
     * @return the number of minutes from midnight, or {@code null} if {@code time}
     *         is {@code null}
     */
    @ProtobufSerializer
    public static Long toMinutesLong(LocalTime time) {
        return time == null ? null : time.toSecondOfDay() / 60L;
    }

    /**
     * Converts a minutes-from-midnight {@code Long} value to a {@link LocalTime}.
     *
     * @param value the number of minutes from midnight, or {@code null}
     * @return the corresponding {@link LocalTime}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static LocalTime fromMinutesLong(Long value) {
        return value == null ? null : LocalTime.ofSecondOfDay(value * 60);
    }

    /**
     * Converts a {@link LocalTime} to its minutes-from-midnight representation
     * as an {@code Integer}.
     *
     * @param time the local time, or {@code null}
     * @return the number of minutes from midnight, or {@code null} if {@code time}
     *         is {@code null}
     */
    @ProtobufSerializer
    public static Integer toMinutesInt(LocalTime time) {
        return time == null ? null : time.toSecondOfDay() / 60;
    }

    /**
     * Converts a minutes-from-midnight {@code Integer} value to a {@link LocalTime}.
     *
     * @param value the number of minutes from midnight, or {@code null}
     * @return the corresponding {@link LocalTime}, or {@code null} if {@code value}
     *         is {@code null}
     */
    @ProtobufDeserializer
    public static LocalTime fromMinutesInt(Integer value) {
        return value == null ? null : LocalTime.ofSecondOfDay(value * 60L);
    }
}
