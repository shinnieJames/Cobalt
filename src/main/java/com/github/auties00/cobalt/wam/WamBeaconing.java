package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.util.FastRandomUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages daily-sampled sequence numbers for WAM event beaconing.
 *
 * <p>At the start of each calendar day (UTC), there is a 1 % chance that
 * beaconing is activated for this session. If activated, each call to
 * {@link #nextSequenceNumber(String)} returns a monotonically increasing
 * sequence number that is written as global field {@code 3433}
 * ({@code beaconSessionId}) before each event.
 *
 * <p>If beaconing is not activated for the current day, the method
 * returns an empty {@link OptionalInt} and no beacon global is written.
 *
 * <p>Beaconing state is tracked independently per buffer key. WhatsApp
 * Web defines 10 buffer keys: {@code "regular"}, {@code "realtime"},
 * and 8 private-stats ID key names (e.g. {@code "DefaultPsId"},
 * {@code "IdTtlDaily"}).
 *
 * <p>This class is not thread-safe; all calls must be made from the
 * single WAM flush thread.
 *
 * @apiNote WAWebWamBeaconing.maybeGetEventSequenceNumber: determines
 * daily activation at 1 % probability and increments a per-buffer-key
 * sequence counter stored in user preferences.
 */
final class WamBeaconing {
    /**
     * Probability that beaconing is activated on any given day.
     */
    private static final double ACTIVATION_PROBABILITY = 0.01;

    private final Map<String, ChannelState> states;

    /**
     * Constructs a new {@code WamBeaconing} instance with no active
     * beaconing session.
     */
    WamBeaconing() {
        this.states = new ConcurrentHashMap<>();
    }

    /**
     * Returns the next beaconing sequence number if beaconing is active
     * for the current day, otherwise returns an empty value.
     *
     * <p>On the first call of a new calendar day, a random check
     * determines whether beaconing is activated. If activated the
     * sequence counter resets to 1 and increments on each subsequent
     * call within the same day.
     *
     * @param bufferKey the buffer key identifying the beaconing track
     *                  (e.g. {@code "regular"}, {@code "realtime"}, or
     *                  a private-stats ID key name)
     * @return an {@code OptionalInt} containing the sequence number if
     *         beaconing is active, or empty otherwise
     */
    OptionalInt nextSequenceNumber(String bufferKey) {
        var state = states.computeIfAbsent(bufferKey, _ -> new ChannelState());
        var currentDayEpoch = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        if (currentDayEpoch != state.activationDayEpoch) {
            state.activationDayEpoch = currentDayEpoch;
            state.active = FastRandomUtils.randomDouble() <= ACTIVATION_PROBABILITY;
            state.sequenceNumber = 0;
        }

        if (!state.active) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(++state.sequenceNumber);
    }

    private static final class ChannelState {
        long activationDayEpoch = -1;
        boolean active = false;
        int sequenceNumber = 0;
    }
}
