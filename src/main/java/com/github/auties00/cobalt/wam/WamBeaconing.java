package com.github.auties00.cobalt.wam;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages daily-sampled sequence numbers for WAM event beaconing.
 *
 * <p>At the start of each calendar day (UTC), there is a 1 % chance that
 * beaconing is activated for this session. If activated, each call to
 * {@link #nextSequenceNumber()} returns a monotonically increasing
 * sequence number that is written as global field {@code 3433}
 * ({@code beaconSessionId}) before each event.
 *
 * <p>If beaconing is not activated for the current day, the method
 * returns an empty {@link OptionalInt} and no beacon global is written.
 *
 * <p>This class is not thread-safe; all calls must be made from the
 * single WAM flush thread.
 *
 * @apiNote WAWebWamBeaconing.maybeGetEventSequenceNumber: determines
 * daily activation at 1 % probability and increments a per-channel
 * sequence counter stored in user preferences.
 */
final class WamBeaconing {
    /**
     * Probability that beaconing is activated on any given day.
     */
    private static final double ACTIVATION_PROBABILITY = 0.01;

    /**
     * The epoch-second value of the start of the day for which the
     * current activation state was determined. A value of {@code -1}
     * means no activation check has been performed yet.
     */
    private long activationDayEpoch;

    /**
     * Whether beaconing is active for the current day.
     */
    private boolean active;

    /**
     * The monotonically increasing sequence counter for the current day.
     */
    private int sequenceNumber;

    /**
     * Constructs a new {@code WamBeaconing} instance with no active
     * beaconing session.
     */
    WamBeaconing() {
        this.activationDayEpoch = -1;
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
     * @return an {@code OptionalInt} containing the sequence number if
     *         beaconing is active, or empty otherwise
     */
    OptionalInt nextSequenceNumber() {
        var currentDayEpoch = Instant.now().truncatedTo(ChronoUnit.DAYS).getEpochSecond();
        if (currentDayEpoch != activationDayEpoch) {
            activationDayEpoch = currentDayEpoch;
            active = ThreadLocalRandom.current().nextDouble() < ACTIVATION_PROBABILITY;
            sequenceNumber = 0;
        }

        if (!active) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(++sequenceNumber);
    }
}   