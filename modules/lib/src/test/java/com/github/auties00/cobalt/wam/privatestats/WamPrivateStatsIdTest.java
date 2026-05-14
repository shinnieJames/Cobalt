package com.github.auties00.cobalt.wam.privatestats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link WamPrivateStatsId}'s rotation
 * machinery. The eight rotation groups match what
 * {@code WAWebWamPrivateStats}'s initialiser sets up; rotation
 * boundaries are driven by an injected
 * {@link java.util.function.LongSupplier} so the period boundary
 * crossings are deterministic.
 */
@DisplayName("WamPrivateStatsId rotation behaviour")
class WamPrivateStatsIdTest {
    /**
     * Number of seconds in a day; cached locally so the test
     * doesn't need to reach into {@link java.time.Duration}
     * arithmetic at each call site.
     */
    private static final long DAY_SECONDS = 86_400L;

    /**
     * A stable starting epoch on a day boundary (relative to UTC),
     * chosen far from epoch zero so any sign-related arithmetic
     * surfaces.
     */
    private static final long T0 = 100 * DAY_SECONDS;

    /**
     * Wire hash integer for the "IdTtlDaily" rotation group; rotates
     * every 1 day.
     */
    private static final int DAILY_HASH = 248614979;

    /**
     * Wire hash integer for the "IdTtlWeekly" group; rotates every
     * 7 days.
     */
    private static final int WEEKLY_HASH = 42196056;

    /**
     * Wire hash integer for the "DefaultPsId" group; never rotates.
     */
    private static final int NEVER_HASH = 113760892;

    /**
     * Verifies the eight rotation groups are present in
     * {@link WamPrivateStatsId#snapshotAll()} with the documented
     * rotation periods.
     */
    @Test
    @DisplayName("snapshotAll returns eight groups with the documented rotation periods")
    void snapshotAllReturnsEightGroups() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);
        var groups = psId.snapshotAll();
        assertEquals(8, groups.size(),
                "WAWebWamPrivateStats defines exactly eight rotation groups");

        // Spot-check a few groups against the table in the javadoc.
        var dailyDescriptor = groups.stream().filter(g -> g.keyHashInt() == DAILY_HASH).findFirst();
        assertTrue(dailyDescriptor.isPresent());
        assertEquals(1, dailyDescriptor.orElseThrow().rotationDays(),
                "IdTtlDaily rotates every 1 day");
        var weeklyDescriptor = groups.stream().filter(g -> g.keyHashInt() == WEEKLY_HASH).findFirst();
        assertTrue(weeklyDescriptor.isPresent());
        assertEquals(7, weeklyDescriptor.orElseThrow().rotationDays());
        var neverDescriptor = groups.stream().filter(g -> g.keyHashInt() == NEVER_HASH).findFirst();
        assertTrue(neverDescriptor.isPresent());
        assertEquals(-1, neverDescriptor.orElseThrow().rotationDays(),
                "DefaultPsId is the canonical never-rotate group");
    }

    /**
     * Verifies that calling {@link WamPrivateStatsId#rotateAndReportChanges}
     * before any period has elapsed produces no rotations.
     */
    @Test
    @DisplayName("no time has elapsed → no rotations")
    void noTimeElapsedNoRotations() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);
        var rotated = psId.rotateAndReportChanges();
        assertTrue(rotated.isEmpty(),
                "rotation when no period has elapsed should report no changes");
    }

    /**
     * Verifies that {@code rotateAndReportChanges} fires for the
     * daily group exactly once when the clock crosses a day
     * boundary after the entries were created.
     */
    @Test
    @DisplayName("crossing a day boundary rotates IdTtlDaily exactly once")
    void crossingDayBoundaryRotatesDaily() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);
        var originalDailyValue = psId.getValueForHash(DAILY_HASH);

        // Advance the clock by 2 days so the next period boundary
        // has definitely passed.
        time.set(T0 + 2 * DAY_SECONDS);
        var rotated = psId.rotateAndReportChanges();

        // Every entry whose period <= 2 days has rotated. Daily and
        // every multi-day group whose creation epoch is before the
        // current period start. IdTtlDaily must be among them.
        var dailyRotated = rotated.stream().anyMatch(r -> r.keyHashInt() == DAILY_HASH);
        assertTrue(dailyRotated, "IdTtlDaily must have rotated after 2 elapsed days");

        // The daily value has been regenerated.
        assertNotEquals(originalDailyValue, psId.getValueForHash(DAILY_HASH),
                "daily value should be a fresh hex after the rotation");

        // The never-rotate group's value is unchanged.
        var neverDescriptor = rotated.stream().anyMatch(r -> r.keyHashInt() == NEVER_HASH);
        assertFalse(neverDescriptor, "DefaultPsId must never rotate, regardless of elapsed time");
    }

    /**
     * Verifies that re-calling {@link WamPrivateStatsId#rotateAndReportChanges}
     * within the same period produces no further rotations.
     */
    @Test
    @DisplayName("subsequent rotation within the same period does nothing")
    void noDoubleRotationWithinPeriod() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);

        // Cross into day 2: rotations fire.
        time.set(T0 + 2 * DAY_SECONDS);
        var first = psId.rotateAndReportChanges();
        assertFalse(first.isEmpty(),
                "first rotation across the boundary should rotate at least the daily group");

        // Stay in day 2: no new rotations.
        var second = psId.rotateAndReportChanges();
        assertTrue(second.isEmpty(),
                "second rotation within the same period must report no changes");
    }

    /**
     * Verifies that the weekly group does not rotate when only 2
     * days have elapsed, but does rotate after 8 days.
     */
    @Test
    @DisplayName("weekly group rotates after 7 days, not after 2")
    void weeklyRotationBoundary() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);

        time.set(T0 + 2 * DAY_SECONDS);
        var afterTwoDays = psId.rotateAndReportChanges();
        assertFalse(afterTwoDays.stream().anyMatch(r -> r.keyHashInt() == WEEKLY_HASH),
                "weekly group must not rotate after only 2 days");

        time.set(T0 + 8 * DAY_SECONDS);
        var afterEightDays = psId.rotateAndReportChanges();
        assertTrue(afterEightDays.stream().anyMatch(r -> r.keyHashInt() == WEEKLY_HASH),
                "weekly group must rotate after 8 elapsed days (next period start crossed)");
    }

    /**
     * Verifies that the regenerated identifier is a fresh hex
     * string of the documented 32-character length.
     */
    @Test
    @DisplayName("rotated identifier is a 32-char hex string")
    void rotatedValueIsFreshHex() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);
        var before = psId.getValueForHash(DAILY_HASH);
        time.set(T0 + 2 * DAY_SECONDS);
        psId.rotateAndReportChanges();
        var after = psId.getValueForHash(DAILY_HASH);
        assertEquals(32, after.length(),
                "WARandomHex.randomHex(16) produces a 32-char uppercase hex string");
        assertTrue(after.matches("[0-9A-Fa-f]+"),
                "rotated value must be hex");
        assertNotEquals(before, after,
                "rotation must regenerate the identifier (probability of collision is 2^-128)");
    }

    /**
     * Verifies that every entry has a unique hash key and a
     * distinct initial value.
     */
    @Test
    @DisplayName("eight groups have distinct hash keys and distinct initial values")
    void groupsAreDistinct() {
        var time = new AtomicLong(T0);
        var psId = new WamPrivateStatsId(time::get);
        var hashKeys = new HashSet<Integer>();
        var values = new HashSet<String>();
        for (var info : psId.snapshotAll()) {
            assertTrue(hashKeys.add(info.keyHashInt()),
                    () -> "duplicate hash key: " + info.keyHashInt());
            values.add(psId.getValueForHash(info.keyHashInt()));
        }
        assertEquals(8, values.size(),
                "every group must have a distinct initial identifier (random hex collisions are astronomical)");
    }

    /**
     * Verifies that {@link WamPrivateStatsId#getKeyNameForHash}
     * returns the documented {@code "none"} sentinel for zero and
     * {@code "unknown_<hash>"} for any other unknown hash integer.
     */
    @Test
    @DisplayName("getKeyNameForHash sentinels match the documented contract")
    void getKeyNameForHashSentinels() {
        var psId = new WamPrivateStatsId();
        assertEquals("none", psId.getKeyNameForHash(0),
                "zero hash maps to the 'none' sentinel");
        assertEquals("unknown_42", psId.getKeyNameForHash(42),
                "unknown non-zero hash maps to 'unknown_<hash>'");
        assertEquals("DefaultPsId", psId.getKeyNameForHash(NEVER_HASH),
                "known hash maps to its key name");
    }

    /**
     * Verifies that {@link WamPrivateStatsId#getValueForHash}
     * returns the {@code "none"} sentinel for any unknown hash
     * integer.
     */
    @Test
    @DisplayName("getValueForHash returns 'none' for unknown hashes")
    void getValueForHashSentinel() {
        var psId = new WamPrivateStatsId();
        assertEquals("none", psId.getValueForHash(0));
        assertEquals("none", psId.getValueForHash(42));
        assertEquals(32, psId.getValueForHash(NEVER_HASH).length(),
                "known hash returns the live 32-char identifier");
    }
}
