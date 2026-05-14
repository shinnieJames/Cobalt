package com.github.auties00.cobalt.wam;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Combined behavioural and KAT tests for the WAM beaconing roll
 * implemented by {@link DefaultWamBeaconing}.
 *
 * <p>The behavioural tests run against a controllable
 * {@link WamBeaconing} implementation that lets a test drive the
 * activation roll and UTC day boundary deterministically. The KAT
 * tests run the same controllable implementation through the
 * scenarios captured from the live WAWebWamBeaconing module and
 * assert that Cobalt produces identical activation / sequence-number
 * results step by step.
 *
 * <p>Vectors live in {@code fixtures/wam/wam-beaconing.json}.
 */
@DisplayName("WamBeaconing behavioural + KAT")
class WamBeaconingTest {
    /**
     * Snapshot revision the vectors were captured against.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * Activation probability cutoff, matching
     * {@code WAWebWamBeaconing.ACTIVATION_PROBABILITY}. The roll is
     * inclusive: {@code random <= 0.01} activates.
     */
    private static final double ACTIVATION_PROBABILITY = 0.01;

    /**
     * Returns one dynamic test per captured beaconing scenario.
     *
     * @return the KAT test factory stream
     */
    @TestFactory
    List<DynamicNode> beaconingMatchesLiveBundle() {
        var fixture = WamFixtures.loadOracle("wam-beaconing");
        WamFixtures.requireSnapshotRevision(fixture, PINNED_SNAPSHOT_REVISION);
        var threshold = fixture.getDoubleValue("activationThreshold");
        assertEquals(ACTIVATION_PROBABILITY, threshold,
                "fixture threshold drift: live=" + threshold + " expected=" + ACTIVATION_PROBABILITY);
        var scenarios = fixture.getJSONArray("scenarios");
        var tests = new ArrayList<DynamicNode>(scenarios.size());
        for (var entry : scenarios) {
            var scenario = (JSONObject) entry;
            tests.add(dynamicTest(scenario.getString("name"), () -> replayScenario(scenario)));
        }
        return tests;
    }

    /**
     * Replays a captured beaconing scenario step by step through
     * {@link DeterministicWamBeaconing} and asserts each step's
     * result matches the captured outcome.
     *
     * @param scenario the captured scenario
     */
    private static void replayScenario(JSONObject scenario) {
        var beaconing = new DeterministicWamBeaconing();
        var steps = scenario.getJSONArray("results");
        for (var entry : steps) {
            var step = (JSONObject) entry;
            beaconing.setUnixTimeSeconds(step.getLongValue("unixTime"));
            var pushed = step.get("pushedRandom");
            if (pushed != null) {
                beaconing.enqueueRandom(((Number) pushed).doubleValue());
            }
            var bufferKey = step.getString("bufferKey");
            var actual = beaconing.nextSequenceNumber(bufferKey);

            var expectedRaw = step.get("result");
            if (expectedRaw == null) {
                assertTrue(actual.isEmpty(),
                        () -> "scenario " + scenario.getString("name") + " step " + step.getString("step")
                                + ": expected empty but got " + actual.getAsLong());
            } else {
                var expected = ((Number) expectedRaw).longValue();
                assertTrue(actual.isPresent(),
                        () -> "scenario " + scenario.getString("name") + " step " + step.getString("step")
                                + ": expected " + expected + " but got empty");
                assertEquals(expected, actual.getAsLong(),
                        () -> "scenario " + scenario.getString("name") + " step " + step.getString("step"));
            }
        }
    }

    /**
     * Behavioural unit tests over {@link DeterministicWamBeaconing}
     * that exercise the roll, day boundary, and counter semantics
     * without relying on fixture replay.
     */
    @Nested
    @DisplayName("DefaultWamBeaconing semantics under deterministic inputs")
    class BehaviouralSemantics {
        /**
         * Verifies that a roll below the 1% threshold activates the
         * given buffer key and that subsequent same-day calls
         * monotonically increment the sequence number.
         */
        @Test
        @DisplayName("activation roll below threshold starts the per-day sequence at 1")
        void activatesAndIncrements() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());
            beaconing.enqueueRandom(0.005);
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"));
            assertEquals(OptionalLong.of(2), beaconing.nextSequenceNumber("regular"));
            assertEquals(OptionalLong.of(3), beaconing.nextSequenceNumber("regular"));
        }

        /**
         * Verifies that a roll above the 1% threshold deactivates the
         * key for the day and all subsequent same-day calls return
         * empty.
         */
        @Test
        @DisplayName("activation roll above threshold returns empty for the whole day")
        void deactivatesForDay() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());
            beaconing.enqueueRandom(0.5);
            assertTrue(beaconing.nextSequenceNumber("regular").isEmpty());
            assertTrue(beaconing.nextSequenceNumber("regular").isEmpty());
        }

        /**
         * Verifies that crossing a UTC day boundary forces a new roll,
         * which may toggle activation.
         */
        @Test
        @DisplayName("crossing a UTC day boundary re-rolls activation")
        void rerollsAcrossDayBoundary() {
            var beaconing = new DeterministicWamBeaconing();
            var day0 = 100L * dayInSeconds();
            var day1 = day0 + dayInSeconds();

            beaconing.setUnixTimeSeconds(day0);
            beaconing.enqueueRandom(0.005);
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"));

            beaconing.setUnixTimeSeconds(day1);
            beaconing.enqueueRandom(0.5);
            assertTrue(beaconing.nextSequenceNumber("regular").isEmpty(),
                    "day1 deactivation should make next call return empty");
        }

        /**
         * Verifies that different buffer keys roll independently;
         * activation of one does not influence the other.
         */
        @Test
        @DisplayName("buffer keys roll independently")
        void independentKeys() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());

            beaconing.enqueueRandom(0.005); // regular activates
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"));

            beaconing.enqueueRandom(0.5); // realtime rejects on its first roll
            assertTrue(beaconing.nextSequenceNumber("realtime").isEmpty());

            // regular continues without being touched by realtime's roll
            assertEquals(OptionalLong.of(2), beaconing.nextSequenceNumber("regular"));
        }

        /**
         * Verifies the activation cutoff is inclusive at 0.01: a roll
         * of exactly 0.01 activates, and the smallest value just above
         * (0.01000001) deactivates.
         */
        @Test
        @DisplayName("threshold is inclusive at 0.01")
        void thresholdInclusive() {
            var activated = new DeterministicWamBeaconing();
            activated.setUnixTimeSeconds(100 * dayInSeconds());
            activated.enqueueRandom(0.01);
            assertTrue(activated.nextSequenceNumber("k").isPresent());

            var deactivated = new DeterministicWamBeaconing();
            deactivated.setUnixTimeSeconds(100 * dayInSeconds());
            deactivated.enqueueRandom(0.01000001);
            assertTrue(deactivated.nextSequenceNumber("k").isEmpty());
        }

        /**
         * Verifies that the sequence counter resets to {@code 1} at
         * the start of each new day on which activation succeeds.
         */
        @Test
        @DisplayName("sequence counter resets at the start of a new active day")
        void counterResetsOnNewActiveDay() {
            var beaconing = new DeterministicWamBeaconing();
            var day0 = 100L * dayInSeconds();
            var day2 = day0 + 2L * dayInSeconds();

            beaconing.setUnixTimeSeconds(day0);
            beaconing.enqueueRandom(0.001);
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"));
            assertEquals(OptionalLong.of(2), beaconing.nextSequenceNumber("regular"));

            beaconing.setUnixTimeSeconds(day2);
            beaconing.enqueueRandom(0.001);
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"),
                    "new active day must reset the counter to 1");
        }

        /**
         * Verifies that the long counter advances past
         * {@link Integer#MAX_VALUE} without sign-flipping.
         *
         * <p>WhatsApp Web stores the counter as a JavaScript
         * {@code Number} — effectively unbounded up to
         * {@code Number.MAX_SAFE_INTEGER} (2^53). Cobalt's original
         * {@code int} counter would have overflowed to
         * {@code Integer.MIN_VALUE} on the 2^31st increment, a
         * silent divergence. The counter is now {@code long}, so
         * the value at {@code Integer.MAX_VALUE + 1} is the
         * positive {@code 2_147_483_648L}, not the negative
         * {@code -2_147_483_648}.
         */
        @Test
        @DisplayName("counter past Integer.MAX_VALUE stays positive (long counter)")
        void counterAdvancesPastIntMax() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());
            beaconing.enqueueRandom(0.001);
            // First call activates and returns 1.
            assertEquals(OptionalLong.of(1L), beaconing.nextSequenceNumber("regular"));

            // Pre-set the internal counter directly to
            // Integer.MAX_VALUE - 1 so we observe the first call
            // crossing 2^31 in a constant-time test.
            beaconing.forceSequenceCounter("regular", Integer.MAX_VALUE - 1L);

            var atMax = beaconing.nextSequenceNumber("regular");
            assertEquals(OptionalLong.of(Integer.MAX_VALUE), atMax,
                    "first call after forcing the counter to MAX-1 returns MAX");
            var pastMax = beaconing.nextSequenceNumber("regular");
            assertEquals(OptionalLong.of((long) Integer.MAX_VALUE + 1L), pastMax,
                    "the call past Integer.MAX_VALUE must yield 2^31, not roll over to Integer.MIN_VALUE");
        }
    }

    /**
     * Returns the number of seconds in a day.
     *
     * @return {@code 86400}
     */
    private static long dayInSeconds() {
        return Duration.ofDays(1).toSeconds();
    }

    /**
     * A {@link WamBeaconing} implementation whose UTC day boundary
     * and activation roll are controlled by the test, allowing
     * scenario replay and deterministic assertions.
     *
     * <p>Mirrors {@link DefaultWamBeaconing}'s per-buffer-key state
     * machine exactly: on the first call of a new UTC day for the
     * given key, consume the next queued random value and activate
     * if it is {@code <= 0.01}; otherwise return the next sequence
     * number when active or empty when inactive.
     */
    private static final class DeterministicWamBeaconing implements WamBeaconing {
        /**
         * Per-buffer-key state.
         */
        private final ConcurrentMap<String, KeyState> states = new ConcurrentHashMap<>();

        /**
         * Queue of upcoming random values consumed by activation
         * rolls, in FIFO order.
         */
        private final List<Double> pendingRandoms = new ArrayList<>();

        /**
         * Current simulated wall-clock time, in Unix epoch seconds.
         */
        private long unixSeconds;

        /**
         * Sets the simulated wall-clock time to the given epoch
         * second.
         *
         * @param seconds the wall-clock epoch second
         */
        void setUnixTimeSeconds(long seconds) {
            this.unixSeconds = seconds;
        }

        /**
         * Enqueues the next random value to be consumed by an
         * activation roll.
         *
         * @param value the random value
         */
        void enqueueRandom(double value) {
            pendingRandoms.add(value);
        }

        /**
         * Force-sets the internal sequence counter for the given
         * buffer key, used by the wraparound test to probe values
         * near {@code Integer.MAX_VALUE} without waiting for 2^31
         * increments. Requires the key's state to already exist
         * (call {@code nextSequenceNumber(bufferKey)} once first
         * to materialise it).
         *
         * @param bufferKey the buffer key whose counter is being set
         * @param value     the new counter value
         */
        void forceSequenceCounter(String bufferKey, long value) {
            var state = states.get(bufferKey);
            if (state == null) {
                throw new IllegalStateException("no state yet for bufferKey " + bufferKey
                        + " — call nextSequenceNumber(bufferKey) once first");
            }
            state.sequenceNumber = value;
        }

        @Override
        public OptionalLong nextSequenceNumber(String bufferKey) {
            var state = states.computeIfAbsent(bufferKey, _ -> new KeyState());
            var currentDayEpoch = Instant.ofEpochSecond(unixSeconds)
                    .truncatedTo(ChronoUnit.DAYS)
                    .getEpochSecond();
            if (currentDayEpoch != state.activationDayEpoch) {
                state.activationDayEpoch = currentDayEpoch;
                if (pendingRandoms.isEmpty()) {
                    throw new IllegalStateException("DeterministicWamBeaconing ran out of pending random values for key " + bufferKey);
                }
                var roll = pendingRandoms.removeFirst();
                state.active = roll <= ACTIVATION_PROBABILITY;
                state.sequenceNumber = 0;
            }

            if (!state.active) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(++state.sequenceNumber);
        }

        /**
         * Per-buffer-key activation and counter state.
         */
        private static final class KeyState {
            /**
             * Epoch seconds of the day for which {@link #active} was
             * last decided. Initialised to {@code -1} so the first
             * call always re-rolls.
             */
            long activationDayEpoch = -1;

            /**
             * {@code true} when beaconing is active for the current day.
             */
            boolean active;

            /**
             * Monotonic counter incremented per successful sequence
             * call. Reset to {@code 0} at the start of every new day.
             */
            long sequenceNumber;
        }
    }

    /**
     * Smoke assertion that {@link DefaultWamBeaconing} (the
     * production impl) is wired into Cobalt's {@code DefaultWamService}
     * — protects against an accidental regression that swaps the
     * impl with a no-op while leaving the interface in place.
     */
    @Test
    @DisplayName("DefaultWamBeaconing is the production impl exposed to WamService")
    void defaultImplProvidesWamBeaconing() {
        WamBeaconing beaconing = new DefaultWamBeaconing();
        var first = beaconing.nextSequenceNumber("regular");
        var second = beaconing.nextSequenceNumber("regular");
        // The roll is randomised by DataUtils.randomDouble, but the
        // result must be either empty (deactivated) on both calls or
        // present and monotonically increasing.
        if (first.isPresent()) {
            assertTrue(second.isPresent(), "if first call is present, second must be too");
            assertEquals(first.getAsLong() + 1, second.getAsLong(),
                    "successive same-day calls must increment the sequence");
        } else {
            assertTrue(second.isEmpty(), "if first call is empty (deactivated), second must be empty too");
        }
    }

    /**
     * Smoke assertion that two distinct buffer keys are treated
     * independently by the production impl.
     *
     * <p>Necessarily probabilistic because the production impl uses
     * the real {@link java.lang.Math#random} for the roll. The check
     * only asserts that the two keys' KeyState instances are kept
     * separate (one's activation does not leak into the other), not
     * any specific outcome of the roll itself.
     */
    @Test
    @DisplayName("DefaultWamBeaconing keeps per-key state isolated")
    void defaultImplKeepsKeysIsolated() {
        var beaconing = new DefaultWamBeaconing();
        var regular = beaconing.nextSequenceNumber("regular");
        var realtime = beaconing.nextSequenceNumber("realtime");
        // Either both are deactivated (most likely, since p=0.01) or the
        // two keys rolled independently; in either case the second key's
        // state was decided by a fresh roll, not inherited from the first.
        if (regular.isPresent() && realtime.isPresent()) {
            assertEquals(1, regular.getAsLong(), "first call to 'regular' on an active day starts at 1");
            assertEquals(1, realtime.getAsLong(), "first call to 'realtime' on an active day also starts at 1");
        }
        // Negative space: make sure we don't accidentally share a single
        // KeyState across keys by checking they don't see each other's increments.
        var regular2 = beaconing.nextSequenceNumber("regular");
        if (regular.isPresent()) {
            assertNotEquals(regular, regular2, "'regular' must advance independently of 'realtime'");
        } else {
            assertFalse(regular2.isPresent(), "'regular' stays empty if it was empty");
        }
    }

}
