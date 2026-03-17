package com.github.auties00.cobalt.model.business.profile;

import com.github.auties00.cobalt.model.mixin.LocalTimeMinutesMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.LocalTime;

/**
 * A business hours configuration entry that defines the operating schedule for a single day
 * of the week.
 *
 * <p>Each entry specifies the day of the week, an operating mode that determines how the
 * business's availability is interpreted, and optional opening and closing times expressed
 * as a {@link LocalTime} relative to the business's local time zone.
 *
 * <p>The operating mode determines the semantics of the time fields. When the mode is
 * {@link BusinessHoursMode#SPECIFIC_HOURS}, the opening and closing time fields define
 * the time range during which the business is open. When the mode is
 * {@link BusinessHoursMode#OPEN_24H}, the business is considered open for the entire day
 * and the time fields are not meaningful. When the mode is
 * {@link BusinessHoursMode#APPOINTMENT_ONLY}, the business operates by appointment and
 * the time fields are likewise not meaningful.
 *
 * <p>A single day of the week can have multiple entries to represent split operating hours,
 * such as a morning and afternoon session separated by a break. In that case, two entries
 * sharing the same day value and {@link BusinessHoursMode#SPECIFIC_HOURS} mode will be
 * present, each covering a different time range.
 */
@ProtobufMessage
public final class BusinessHoursEntry {
    /**
     * The day of the week for this entry.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    BusinessHoursDay day;

    /**
     * The operating mode for this day.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    BusinessHoursMode mode;

    /**
     * The opening time, stored on the wire as minutes from midnight.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = LocalTimeMinutesMixin.class)
    LocalTime openTime;

    /**
     * The closing time, stored on the wire as minutes from midnight.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = LocalTimeMinutesMixin.class)
    LocalTime closeTime;

    /**
     * Constructs a new business hours entry with the specified day, mode, and time range.
     *
     * @param day       the day of the week
     * @param mode      the operating mode for this day
     * @param openTime  the opening time of day
     * @param closeTime the closing time of day
     */
    BusinessHoursEntry(BusinessHoursDay day, BusinessHoursMode mode, LocalTime openTime, LocalTime closeTime) {
        this.day = day;
        this.mode = mode;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    /**
     * Returns the day of the week for this entry.
     *
     * @return the day of the week
     */
    public BusinessHoursDay day() {
        return day;
    }

    /**
     * Sets the day of the week for this entry.
     *
     * @param day the day of the week
     * @return this instance
     */
    public void setDay(BusinessHoursDay day) {
        this.day = day;
    }

    /**
     * Returns the operating mode for this day.
     *
     * @return the operating mode
     */
    public BusinessHoursMode mode() {
        return mode;
    }

    /**
     * Sets the operating mode for this day.
     *
     * @param mode the operating mode
     * @return this instance
     */
    public void setMode(BusinessHoursMode mode) {
        this.mode = mode;
    }

    /**
     * Returns the opening time as a {@link LocalTime}.
     *
     * <p>This value is only meaningful when the mode is
     * {@link BusinessHoursMode#SPECIFIC_HOURS}. The returned time is relative to the
     * business's local time zone, which is specified by
     * {@link BusinessHours#timeZone()}.
     *
     * @return the opening time of day
     */
    public LocalTime openTime() {
        return openTime;
    }

    /**
     * Sets the opening time.
     *
     * @param openTime the opening time of day
     * @return this instance
     */
    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    /**
     * Returns the closing time as a {@link LocalTime}.
     *
     * <p>This value is only meaningful when the mode is
     * {@link BusinessHoursMode#SPECIFIC_HOURS}. The returned time is relative to the
     * business's local time zone, which is specified by
     * {@link BusinessHours#timeZone()}.
     *
     * @return the closing time of day
     */
    public LocalTime closeTime() {
        return closeTime;
    }

    /**
     * Sets the closing time.
     *
     * @param closeTime the closing time of day
     * @return this instance
     */
    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    /**
     * Returns whether this entry represents a configuration where no specific time range
     * has been set.
     *
     * <p>This is determined by both the opening and closing times being midnight
     * ({@link LocalTime#MIDNIGHT}), which is the default when those attributes are absent
     * from the protocol node. This typically occurs when the mode is
     * {@link BusinessHoursMode#OPEN_24H} or {@link BusinessHoursMode#APPOINTMENT_ONLY},
     * where explicit time boundaries are not applicable.
     *
     * @return {@code true} if both the opening and closing times are midnight,
     *         otherwise {@code false}
     */
    public boolean alwaysOpen() {
        return LocalTime.MIDNIGHT.equals(openTime) && LocalTime.MIDNIGHT.equals(closeTime);
    }
}
