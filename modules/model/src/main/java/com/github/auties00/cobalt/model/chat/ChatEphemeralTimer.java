package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.Duration;
import java.util.Arrays;

/**
 * Represents the available durations for disappearing messages in a WhatsApp chat.
 *
 * <p>When disappearing messages are enabled in a one-to-one or group conversation,
 * every new message sent to the chat is automatically deleted after the configured
 * duration. WhatsApp supports a fixed set of timer values: 24 hours, 7 days, and
 * 90 days, plus a special {@link #OFF} value that disables the feature entirely.
 *
 * <p>Instances are serialized to and from protobuf as an integer representing the
 * duration in seconds. The {@link #of(Integer)} factory method also accepts values
 * expressed in days for backward compatibility.
 */
@WhatsAppWebModule(moduleName = "WAWebEphemeralIsDurationAllowed")
public enum ChatEphemeralTimer {
    /**
     * Disappearing messages are disabled. Messages in the chat are retained
     * indefinitely.
     */
    OFF(Duration.ofDays(0)),

    /**
     * Messages disappear after 24 hours.
     */
    ONE_DAY(Duration.ofDays(1)),

    /**
     * Messages disappear after 7 days.
     */
    ONE_WEEK(Duration.ofDays(7)),

    /**
     * Messages disappear after 90 days.
     */
    THREE_MONTHS(Duration.ofDays(90));

    /**
     * The duration after which messages are automatically deleted.
     */
    private final Duration period;

    /**
     * Constructs a {@code ChatEphemeralTimer} with the given duration.
     *
     * @param period the duration after which messages are deleted
     */
    ChatEphemeralTimer(Duration period) {
        this.period = period;
    }

    /**
     * Returns the duration after which messages are automatically deleted
     * in this timer mode.
     *
     * @return the ephemeral timer duration, never {@code null}
     */
    public Duration period() {
        return period;
    }

    /**
     * Returns the {@code ChatEphemeralTimer} matching the given integer value.
     *
     * <p>The value may be expressed in seconds or in days. If the value is
     * {@code null} or does not match any known timer, {@link #OFF} is returned.
     *
     * @param value the timer value in seconds or days, or {@code null}
     * @return the matching timer, or {@link #OFF} if no match is found
     */
    @ProtobufDeserializer
    public static ChatEphemeralTimer of(Integer value) {
        return value == null ? OFF : Arrays.stream(values())
                .filter(entry -> entry.period().toSeconds() == value || entry.period().toDays() == value)
                .findFirst()
                .orElse(OFF);
    }

    /**
     * Returns this timer's duration expressed in seconds, suitable for
     * protobuf serialization.
     *
     * @return the duration in seconds as an {@link Integer}
     */
    @ProtobufSerializer
    public Integer periodSeconds() {
        return (int) period.toSeconds();
    }

    /**
     * Returns whether the given duration (in seconds) is a valid ephemeral-timer
     * value accepted by WhatsApp.
     *
     * <p>A duration is considered allowed when it is either
     * <ul>
     *   <li>exactly {@code 0}, which disables disappearing messages (the
     *       {@link #OFF} timer), or</li>
     *   <li>one of the fixed positive values {@code 86400} (1 day),
     *       {@code 604800} (7 days) or {@code 7776000} (90 days), matching
     *       {@link #ONE_DAY}, {@link #ONE_WEEK} and {@link #THREE_MONTHS}
     *       respectively.</li>
     * </ul>
     * Any negative value, or any other positive value not in the fixed set, is
     * rejected.
     *
     * @param durationSeconds the candidate duration, in seconds
     * @return {@code true} if {@code durationSeconds} is {@code 0} or matches
     *         one of the defined timers, {@code false} otherwise
     * @implNote ADAPTED: WAWebEphemeralIsDurationAllowed.isEphemeralDurationAllowed.
     * WA Web hard-codes the allowed positive values in a module-level array
     * ({@code [86400, 604800, 7776e3]}) and treats {@code t < 0} as rejected,
     * {@code t === 0} as accepted, and anything else as a membership check
     * against the array. Cobalt derives the same set from this enum's variants
     * so the allowed durations stay in a single source of truth.
     */
    @WhatsAppWebExport(moduleName = "WAWebEphemeralIsDurationAllowed",
            exports = "isEphemeralDurationAllowed",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean isEphemeralDurationAllowed(int durationSeconds) {
        // WAWebEphemeralIsDurationAllowed.isEphemeralDurationAllowed: t < 0 ? false
        if (durationSeconds < 0) {
            return false;
        }
        // WAWebEphemeralIsDurationAllowed.isEphemeralDurationAllowed: t === 0 ? true
        if (durationSeconds == 0) {
            return true;
        }
        // WAWebEphemeralIsDurationAllowed.isEphemeralDurationAllowed: e.includes(t) where e = [86400, 604800, 7776e3]
        for (var timer : values()) {
            if (timer != OFF && timer.period.toSeconds() == durationSeconds) {
                return true;
            }
        }
        return false;
    }
}
