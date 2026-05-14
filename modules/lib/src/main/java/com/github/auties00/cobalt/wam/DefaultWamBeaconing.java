package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.util.DataUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Production {@link WamBeaconing} implementation that follows
 * WhatsApp Web's {@code WAWebWamBeaconing} exactly: a one percent
 * activation roll at the start of each UTC calendar day, with a
 * monotonically increasing per-buffer-key sequence number while
 * activated.
 *
 * <p>This class is not thread-safe. All calls must be made from the
 * single WAM flush thread.
 */
@WhatsAppWebModule(moduleName = "WAWebWamBeaconing")
final class DefaultWamBeaconing implements WamBeaconing {
    /**
     * Probability that beaconing is activated on any given day.
     */
    private static final double ACTIVATION_PROBABILITY = 0.01;

    /**
     * Per-buffer-key beaconing state, keyed by the buffer key string.
     */
    private final ConcurrentMap<String, ChannelState> states;

    /**
     * Constructs a new {@code DefaultWamBeaconing} with no active
     * beaconing session.
     */
    DefaultWamBeaconing() {
        this.states = new ConcurrentHashMap<>();
    }

    @Override
    public OptionalLong nextSequenceNumber(String bufferKey) {
        var state = states.computeIfAbsent(bufferKey, _ -> new ChannelState());
        var currentDayEpoch = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        if (currentDayEpoch != state.activationDayEpoch) {
            state.activationDayEpoch = currentDayEpoch;
            state.active = DataUtils.randomDouble() <= ACTIVATION_PROBABILITY;
            state.sequenceNumber = 0L;
        }

        if (!state.active) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(++state.sequenceNumber);
    }

    /**
     * Per-buffer-key beaconing state, holding the activation day, the
     * activation flag, and the running sequence counter.
     *
     * <p>The {@code sequenceNumber} counter is a {@code long} (not an
     * {@code int}) to match WAWebWamBeaconing's effectively-unbounded
     * JavaScript {@code Number} counter — Cobalt could otherwise wrap
     * past {@code Integer.MAX_VALUE} into negative territory after
     * ~2 billion increments, while WA Web would not.
     */
    private static final class ChannelState {
        /**
         * Epoch seconds of the calendar day for which {@link #active}
         * was last decided. Initialised to {@code -1} so the first
         * call always re-rolls activation.
         */
        long activationDayEpoch = -1;

        /**
         * {@code true} when beaconing is active for the current day.
         */
        boolean active = false;

        /**
         * Monotonic counter incremented on each successful call. Reset
         * to {@code 0} at the start of every new day.
         */
        long sequenceNumber = 0L;
    }
}