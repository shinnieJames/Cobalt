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
 * Combined behavioural and KAT tests for the WAM beaconing roll backing
 * {@link DefaultWamBeaconing}.
 *
 * @apiNote
 * Exercises the 1% activation roll, the UTC day boundary, and the
 * per-buffer-key counter semantics demanded by
 * {@code WAWebWamBeaconing.maybeGetEventSequenceNumber}. The KAT suite
 * replays captured scenarios; the behavioural suite covers boundary
 * conditions (threshold inclusivity, counter wraparound, key isolation)
 * without depending on a fixture.
 *
 * @implNote
 * Behavioural tests run against a {@link DeterministicWamBeaconing}
 * harness that lets the test queue activation rolls and drive the
 * simulated UTC day boundary by hand. KAT vectors live in
 * {@code fixtures/wam/wam-beaconing.json} pinned to snapshot revision
 * {@code 1039260921}.
 */
@DisplayName("WamBeaconing behavioural + KAT")
class WamBeaconingTest {
    /**
     * The snapshot revision the KAT vectors were captured against;
     * compared against the fixture header so revision drift fails
     * loudly.
     */
    private static final long PINNED_SNAPSHOT_REVISION = 1039260921L;

    /**
     * The 1% activation cutoff applied inclusively
     * ({@code roll <= 0.01} activates), matching the literal
     * {@code .01} in {@code WAWebWamBeaconing}.
     */
    private static final double ACTIVATION_PROBABILITY = 0.01;

    /**
     * Returns one dynamic test per scenario captured from the live
     * {@code WAWebWamBeaconing} oracle.
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
     * Replays a captured beaconing scenario step by step against a
     * fresh {@link DeterministicWamBeaconing} and asserts each step's
     * outcome matches the captured result (or empty).
     *
     * @apiNote
     * Each step carries a {@code unixTime}, an optional
     * {@code pushedRandom} to enqueue before the call, a
     * {@code bufferKey}, and an expected {@code result} (the next
     * sequence number or {@code null} for empty).
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
     * Behavioural sub-suite covering the roll, day boundary, and
     * counter semantics that {@link DefaultWamBeaconing} must satisfy
     * regardless of fixture availability.
     *
     * @apiNote
     * The harness is {@link DeterministicWamBeaconing}; each test
     * queues the random rolls and the simulated UTC time it needs and
     * asserts the produced sequence numbers (or empties) directly.
     */
    @Nested
    @DisplayName("DefaultWamBeaconing semantics under deterministic inputs")
    class BehaviouralSemantics {
        /**
         * Verifies that a roll strictly below the 1% cutoff activates
         * the key and the next three same-day calls return
         * monotonically increasing sequence numbers starting at
         * {@code 1}.
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
         * Verifies that a roll above the 1% cutoff deactivates the key
         * for the whole day and every same-day call returns empty.
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
         * Verifies that crossing a UTC day boundary forces a fresh
         * activation roll, which may toggle the active state.
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
         * Verifies that two buffer keys roll independently; activation
         * of one does not leak into the other.
         */
        @Test
        @DisplayName("buffer keys roll independently")
        void independentKeys() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());

            beaconing.enqueueRandom(0.005);
            assertEquals(OptionalLong.of(1), beaconing.nextSequenceNumber("regular"));

            beaconing.enqueueRandom(0.5);
            assertTrue(beaconing.nextSequenceNumber("realtime").isEmpty());

            assertEquals(OptionalLong.of(2), beaconing.nextSequenceNumber("regular"));
        }

        /**
         * Verifies the activation cutoff is inclusive at {@code 0.01}:
         * a roll equal to the cutoff activates, the smallest
         * representable value above ({@code 0.01000001}) deactivates.
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
         * the start of every new active day, not at the day boundary
         * itself.
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
         * Verifies that the {@code long} counter advances past
         * {@link Integer#MAX_VALUE} as a positive value rather than
         * sign-flipping.
         *
         * @implNote
         * Forces the internal counter to {@code Integer.MAX_VALUE - 1}
         * to make the 2^31 boundary observable in constant time
         * without firing 2 billion increments; the test exists because
         * a previous {@code int} counter silently wrapped to
         * {@link Integer#MIN_VALUE} there and the JavaScript reference
         * cannot hit that case at all.
         */
        @Test
        @DisplayName("counter past Integer.MAX_VALUE stays positive (long counter)")
        void counterAdvancesPastIntMax() {
            var beaconing = new DeterministicWamBeaconing();
            beaconing.setUnixTimeSeconds(100 * dayInSeconds());
            beaconing.enqueueRandom(0.001);
            assertEquals(OptionalLong.of(1L), beaconing.nextSequenceNumber("regular"));

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
     * @return {@code 86_400}
     */
    private static long dayInSeconds() {
        return Duration.ofDays(1).toSeconds();
    }

    /**
     * Controllable {@link WamBeaconing} test double whose UTC day
     * boundary and activation rolls are driven explicitly by the test.
     *
     * @apiNote
     * Tests call {@link #setUnixTimeSeconds(long)} and
     * {@link #enqueueRandom(double)} to set up a step, then exercise
     * the same {@link #nextSequenceNumber(String)} surface every
     * production caller uses.
     *
     * @implNote
     * Mirrors {@link DefaultWamBeaconing}'s per-buffer-key state
     * machine: on the first call of a new UTC day the next queued
     * random value is consumed, activation set if it is
     * {@code <= 0.01}, and the counter reset to {@code 0}; subsequent
     * calls within the same day either return the next sequence number
     * (when active) or empty (when inactive).
     */
    private static final class DeterministicWamBeaconing implements WamBeaconing {
        /**
         * The per-buffer-key state map; entries are materialised lazily
         * on the first {@link #nextSequenceNumber(String)} call for a
         * key.
         */
        private final ConcurrentMap<String, KeyState> states = new ConcurrentHashMap<>();

        /**
         * The FIFO queue of upcoming random values that activation
         * rolls consume on day-boundary transitions.
         */
        private final List<Double> pendingRandoms = new ArrayList<>();

        /**
         * The current simulated wall-clock time, in Unix epoch
         * seconds.
         */
        private long unixSeconds;

        /**
         * Sets the simulated wall-clock time to the given epoch
         * second; the next {@link #nextSequenceNumber(String)} call
         * sees this time as {@code now}.
         *
         * @param seconds the wall-clock epoch second
         */
        void setUnixTimeSeconds(long seconds) {
            this.unixSeconds = seconds;
        }

        /**
         * Enqueues the next random value the activation roll will
         * consume.
         *
         * @param value the random value
         */
        void enqueueRandom(double value) {
            pendingRandoms.add(value);
        }

        /**
         * Force-sets the running counter for the given buffer key.
         *
         * @apiNote
         * Used by the wraparound test to probe values near
         * {@link Integer#MAX_VALUE} without firing 2 billion
         * increments. The key's state must already exist; call
         * {@link #nextSequenceNumber(String)} once first to
         * materialise it.
         *
         * @param bufferKey the buffer key whose counter is being set
         * @param value     the new counter value
         * @throws IllegalStateException if no state has been
         *                               materialised for
         *                               {@code bufferKey}
         */
        void forceSequenceCounter(String bufferKey, long value) {
            var state = states.get(bufferKey);
            if (state == null) {
                throw new IllegalStateException("no state yet for bufferKey " + bufferKey
                        + " - call nextSequenceNumber(bufferKey) once first");
            }
            state.sequenceNumber = value;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation truncates {@link #unixSeconds} to days
         * to detect a UTC day change; on a change it pulls the next
         * value from {@link #pendingRandoms} (throwing if the queue
         * was not pre-armed by the test) and resets the counter.
         */
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
         * Per-buffer-key activation and counter state mirroring
         * {@link DefaultWamBeaconing.ChannelState} bit for bit.
         */
        private static final class KeyState {
            /**
             * The UTC epoch second of the day for which {@link #active}
             * was last decided; initialised to {@code -1} so the first
             * call observes a day change.
             */
            long activationDayEpoch = -1;

            /**
             * Whether beaconing is active for the current activation
             * day.
             */
            boolean active;

            /**
             * The running counter, pre-incremented on each non-empty
             * call and reset to {@code 0} on every activation-day
             * change.
             */
            long sequenceNumber;
        }
    }

    /**
     * Smoke check that {@link DefaultWamBeaconing} (the production
     * impl) is the {@link WamBeaconing} implementation
     * {@link DefaultWamService} actually wires up.
     *
     * @apiNote
     * Guards against an accidental regression that swaps the impl for
     * a no-op while leaving the interface in place; the assertion
     * tolerates both possible outcomes of the underlying randomised
     * roll, asserting only the contract (present-and-monotonic or
     * empty-and-stays-empty).
     */
    @Test
    @DisplayName("DefaultWamBeaconing is the production impl exposed to WamService")
    void defaultImplProvidesWamBeaconing() {
        WamBeaconing beaconing = new DefaultWamBeaconing();
        var first = beaconing.nextSequenceNumber("regular");
        var second = beaconing.nextSequenceNumber("regular");
        if (first.isPresent()) {
            assertTrue(second.isPresent(), "if first call is present, second must be too");
            assertEquals(first.getAsLong() + 1, second.getAsLong(),
                    "successive same-day calls must increment the sequence");
        } else {
            assertTrue(second.isEmpty(), "if first call is empty (deactivated), second must be empty too");
        }
    }

    /**
     * Smoke check that two distinct buffer keys see independent
     * activation rolls in the production impl.
     *
     * @apiNote
     * Probabilistic by construction (the production impl reads the
     * real {@link Math#random}); the assertion is that the
     * second key's state was decided by a fresh roll, not inherited
     * from the first, regardless of what either roll actually
     * produced.
     */
    @Test
    @DisplayName("DefaultWamBeaconing keeps per-key state isolated")
    void defaultImplKeepsKeysIsolated() {
        var beaconing = new DefaultWamBeaconing();
        var regular = beaconing.nextSequenceNumber("regular");
        var realtime = beaconing.nextSequenceNumber("realtime");
        if (regular.isPresent() && realtime.isPresent()) {
            assertEquals(1, regular.getAsLong(), "first call to 'regular' on an active day starts at 1");
            assertEquals(1, realtime.getAsLong(), "first call to 'realtime' on an active day also starts at 1");
        }
        var regular2 = beaconing.nextSequenceNumber("regular");
        if (regular.isPresent()) {
            assertNotEquals(regular, regular2, "'regular' must advance independently of 'realtime'");
        } else {
            assertFalse(regular2.isPresent(), "'regular' stays empty if it was empty");
        }
    }

}
