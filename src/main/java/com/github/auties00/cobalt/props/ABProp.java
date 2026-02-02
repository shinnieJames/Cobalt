package com.github.auties00.cobalt.props;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents an A/B testing property (AB prop) configuration value received from WhatsApp servers.
 * <p>
 * AB props are feature flags and configuration values that WhatsApp uses to control client behavior,
 * enable/disable features, and conduct A/B testing experiments. Each prop consists of:
 * <ul>
 *   <li>A numeric {@code code} that uniquely identifies the property</li>
 *   <li>A string {@code value} containing the actual value (may represent bool, int, float, or string)</li>
 *   <li>An optional {@code exposureKey} used for experiment tracking</li>
 * </ul>
 * <p>
 * This record is immutable and thread-safe.
 *
 * @param code   the unique numeric identifier for this configuration property
 * @param value  the string representation of the property value
 * @param exposureKey  optional key used for tracking experiment exposure, may be null
 */
public record ABProp(int code, String value, Long exposureKey) {
    /**
     * Controls whether LID migration is enabled.
     */
    public static final int LID_STATUS_SEND_ENABLED_AB_PROP_CODE = 6791;

    /**
     * Controls whether web client supports AI group open features.
     * Must be true for Meta AI bot features to work.
     */
    public static final int WEB_AI_GROUP_OPEN_SUPPORT_AB_PROP_CODE = 23530;

    /**
     * Controls whether AI group participation is enabled.
     * Must be true (along with WEB_AI_GROUP_OPEN_SUPPORT) for Meta AI bot to be included in phash.
     */
    public static final int AI_GROUP_PARTICIPATION_ENABLED_AB_PROP_CODE = 22171;

    /**
     * Number of days after which key index lists expire.
     * Device lists older than this threshold are considered fully expired.
     */
    public static final int NUM_DAYS_KEY_INDEX_LIST_EXPIRATION_AB_PROP_CODE = 0; // TODO: Find actual code

    /**
     * Number of days before device expiry to trigger pre-expiration check.
     * Device lists within this threshold of expiration should be proactively refreshed.
     */
    public static final int NUM_DAYS_BEFORE_DEVICE_EXPIRY_CHECK_AB_PROP_CODE = 0; // TODO: Find actual code

    /**
     * Controls whether to trigger logout when the user's own device list expires.
     * Per WhatsApp Web: when true, ADV expiration of own device list triggers logout.
     */
    public static final int WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED_AB_PROP_CODE = 0; // TODO: Find actual code

    public ABProp {
        Objects.requireNonNull(value, "value cannot be null");
    }

    /**
     * Converts this property's value to a boolean.
     *
     * @return the boolean representation of this property's value
     */
    public boolean asBoolean() {
        return "1".equals(value)
            || "True".equals(value)
            || "true".equals(value);
    }

    /**
     * Attempts to convert this property's value to an integer.
     *
     * @return an {@link OptionalInt} containing the integer value if parsing succeeds, or empty if it fails
     */
    public OptionalInt asInt() {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /**
     * Attempts to convert this property's value to a long.
     *
     * @return an {@link OptionalLong} containing the long value if parsing succeeds, or empty if it fails
     */
    public OptionalLong asLong() {
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /**
     * Attempts to convert this property's value to a double (floating-point).
     *
     * @return an {@link OptionalDouble} containing the double value if parsing succeeds, or empty if it fails
     */
    public OptionalDouble asDouble() {
        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Returns this property's value as a string.
     *
     * @return the string representation of this property's value
     */
    public String asString() {
        return value;
    }

    @Override
    public String toString() {
        return "ABProp[code=%d, value='%s', exposureKey=%s]"
                .formatted(code, value, exposureKey);
    }
}
