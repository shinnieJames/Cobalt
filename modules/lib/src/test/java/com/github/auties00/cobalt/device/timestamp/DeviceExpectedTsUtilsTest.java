package com.github.auties00.cobalt.device.timestamp;

import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DeviceExpectedTsUtils}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WAWebAdvExpectedTsApi.shouldClearExpectedTs},
 * {@code .computeExpectedTsForDeviceRecord}, and
 * {@code .computeNewExpectedTs}, plus the staleness predicates inside
 * {@code WAWebAdvDeviceInfoCheckJob.runAdvDeviceInfoCheck}.
 *
 * @implNote
 * This implementation drives the helpers with synthetic {@link Instant}
 * inputs because the underlying logic is a pure pipeline over four
 * timestamps; the WA Web oracle adds no information that synthetic cases
 * cannot exercise, so no captured fixtures are required.
 */
@DisplayName("DeviceExpectedTsUtils")
class DeviceExpectedTsUtilsTest {
    /**
     * Synthetic user JID used as the {@link DeviceList} primary key.
     */
    private static final Jid USER_JID = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Builds a {@link DeviceList} carrying the five tracked timestamp fields.
     *
     * @apiNote
     * Helper for every nested suite; lets each test express its inputs as
     * a handful of {@link Instant} parameters instead of building the full
     * {@link DeviceListBuilder} chain.
     *
     * @param timestamp the {@code timestamp} field
     * @param expected  the {@code expectedTimestamp} field, or {@code null}
     * @param lastJob   the {@code expectedTimestampLastDeviceJobTimestamp}
     *                  field, or {@code null}
     * @param updated   the {@code expectedTimestampUpdateTimestamp} field,
     *                  or {@code null}
     * @param deleted   the {@code deleted} flag
     * @return the populated {@link DeviceList}
     */
    private static DeviceList list(Instant timestamp, Instant expected, Instant lastJob, Instant updated, boolean deleted) {
        return new DeviceListBuilder()
                .userJid(USER_JID)
                .devices(List.of())
                .timestamp(timestamp)
                .deleted(deleted)
                .expectedTimestamp(expected)
                .expectedTimestampLastDeviceJobTimestamp(lastJob)
                .expectedTimestampUpdateTimestamp(updated)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
    }

    /**
     * Truth-table coverage for
     * {@link DeviceExpectedTsUtils#shouldClearExpectedTimestamp(Instant, Instant, DeviceList, Instant)}.
     */
    @Nested
    @DisplayName("shouldClearExpectedTimestamp")
    class ShouldClearExpectedTimestamp {
        /**
         * Anchor for the synthetic timestamp axis.
         */
        private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        /**
         * {@link #t0} plus five minutes; lets a test assert "incoming
         * caught up to expected" without committing to a specific date.
         */
        private final Instant t1 = t0.plus(Duration.ofMinutes(5));

        /**
         * {@link #t0} plus ten minutes; lets a test express "incoming
         * overtook expected".
         */
        private final Instant t2 = t0.plus(Duration.ofMinutes(10));

        /**
         * Null cached list short-circuits to {@code false}.
         */
        @Test
        @DisplayName("returns false when the cached list is null")
        void nullCachedList() {
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t1, t2, null, t0));
        }

        /**
         * Deleted cached list short-circuits to {@code false}.
         */
        @Test
        @DisplayName("returns false when the cached list is deleted")
        void deletedCachedList() {
            var cached = list(t0, t1, null, null, true);
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t1, t2, cached, t0));
        }

        /**
         * Cached list with no expected timestamp short-circuits to
         * {@code false}.
         */
        @Test
        @DisplayName("returns false when the cached list has no expected timestamp")
        void noCachedExpected() {
            var cached = list(t0, null, null, null, false);
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t1, t2, cached, t0));
        }

        /**
         * Incoming timestamp equal to cached expectation triggers a clear.
         */
        @Test
        @DisplayName("returns true when the server timestamp has caught up to the cached expectation")
        void caughtUp() {
            var cached = list(t0, t1, null, null, false);
            assertTrue(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t1, null, cached, t0));
        }

        /**
         * Incoming timestamp past cached expectation triggers a clear.
         */
        @Test
        @DisplayName("returns true when the server timestamp has overtaken the cached expectation")
        void overtaken() {
            var cached = list(t0, t1, null, null, false);
            assertTrue(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t2, null, cached, t0));
        }

        /**
         * A changed incoming expectation prevents the clear.
         */
        @Test
        @DisplayName("returns false when the incoming expectation differs from the cached one")
        void expectationChanged() {
            var cached = list(t0, t1, null, null, false);
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t0, t2, cached, t1));
        }

        /**
         * Matching incoming expectation but no ADV check time prevents the
         * clear.
         */
        @Test
        @DisplayName("returns false when the incoming expectation matches but no ADV check time is given")
        void noAdvCheck() {
            var cached = list(t0, t1, null, null, false);
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t0, t1, cached, null));
        }

        /**
         * Matching incoming expectation with a newer ADV job triggers a
         * clear.
         */
        @Test
        @DisplayName("returns true when the incoming expectation matches and a newer ADV job ran")
        void newerAdvCheck() {
            var cached = list(t0, t1, t0, null, false);
            assertTrue(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t0, t1, cached, t1));
        }

        /**
         * Matching incoming expectation with the same ADV job leaves the
         * cache alone.
         */
        @Test
        @DisplayName("returns false when the incoming expectation matches but the ADV job has not advanced")
        void sameAdvCheck() {
            var cached = list(t0, t1, t1, null, false);
            assertFalse(DeviceExpectedTsUtils.shouldClearExpectedTimestamp(t0, t1, cached, t1));
        }
    }

    /**
     * Truth-table coverage for
     * {@link DeviceExpectedTsUtils#hasExpectedTimestampChanged(Instant, Instant)}.
     */
    @Nested
    @DisplayName("hasExpectedTimestampChanged")
    class HasExpectedTimestampChanged {
        /**
         * Anchor instant.
         */
        private final Instant a = Instant.parse("2026-01-01T00:00:00Z");

        /**
         * Distinct anchor used to assert "changed" detection.
         */
        private final Instant b = Instant.parse("2026-01-02T00:00:00Z");

        /**
         * Both inputs {@code null} reports unchanged.
         */
        @Test
        @DisplayName("both null counts as unchanged")
        void bothNull() {
            assertFalse(DeviceExpectedTsUtils.hasExpectedTimestampChanged(null, null));
        }

        /**
         * Exactly one input {@code null} reports changed in both
         * directions.
         */
        @Test
        @DisplayName("null vs non-null counts as changed")
        void asymmetricNull() {
            assertTrue(DeviceExpectedTsUtils.hasExpectedTimestampChanged(null, a));
            assertTrue(DeviceExpectedTsUtils.hasExpectedTimestampChanged(a, null));
        }

        /**
         * Equal-valued {@link Instant}s report unchanged regardless of
         * identity.
         */
        @Test
        @DisplayName("equal instants count as unchanged")
        void equalInstants() {
            assertFalse(DeviceExpectedTsUtils.hasExpectedTimestampChanged(a, Instant.ofEpochSecond(a.getEpochSecond())));
        }

        /**
         * Distinct {@link Instant}s report changed.
         */
        @Test
        @DisplayName("different instants count as changed")
        void differentInstants() {
            assertTrue(DeviceExpectedTsUtils.hasExpectedTimestampChanged(a, b));
        }
    }

    /**
     * Coverage for
     * {@link DeviceExpectedTsUtils#computeExpectedTimestampForDeviceRecord(Instant, DeviceList, Instant)}
     * focused on the null and deleted-list branches.
     */
    @Nested
    @DisplayName("computeExpectedTimestampForDeviceRecord")
    class ComputeExpectedTimestampForDeviceRecord {
        /**
         * Anchor for the synthetic timestamp axis.
         */
        private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        /**
         * {@link #t0} plus five minutes for "incoming after current"
         * scenarios.
         */
        private final Instant t1 = t0.plus(Duration.ofMinutes(5));

        /**
         * Null cached list produces a blank tuple.
         */
        @Test
        @DisplayName("returns blank result when the cached list is null")
        void nullCachedList() {
            var result = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(t1, null, t0);
            assertTrue(result.expectedTimestamp().isEmpty());
            assertTrue(result.expectedTimestampLastDeviceJobTimestamp().isEmpty());
            assertTrue(result.expectedTimestampUpdateTimestamp().isEmpty());
        }

        /**
         * Deleted cached list does not propagate its existing
         * expected-timestamp fields into the result.
         */
        @Test
        @DisplayName("does not propagate fields from a deleted cached list")
        void deletedCachedList() {
            var cached = list(t0, t1, t0, t0, true);
            var result = DeviceExpectedTsUtils.computeExpectedTimestampForDeviceRecord(t1, cached, t0);
            assertEquals(t1, result.expectedTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampLastDeviceJobTimestamp().orElseThrow());
            assertTrue(result.expectedTimestampUpdateTimestamp().isPresent());
        }
    }

    /**
     * Coverage for the core arithmetic in
     * {@link DeviceExpectedTsUtils#computeNewExpectedTimestamp(Instant, Instant, Instant, Instant, Instant, Instant)}.
     */
    @Nested
    @DisplayName("computeNewExpectedTimestamp")
    class ComputeNewExpectedTimestamp {
        /**
         * Anchor for the synthetic timestamp axis.
         */
        private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        /**
         * {@link #t0} plus five minutes for "current already met incoming"
         * scenarios.
         */
        private final Instant t1 = t0.plus(Duration.ofMinutes(5));

        /**
         * The current timestamp already meeting the incoming one preserves
         * the existing tuple.
         */
        @Test
        @DisplayName("preserves current values when the current timestamp already meets the incoming one")
        void currentMeetsIncoming() {
            var result = DeviceExpectedTsUtils.computeNewExpectedTimestamp(t0, t1, t0, t1, t0, t0);
            assertEquals(t1, result.expectedTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampLastDeviceJobTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampUpdateTimestamp().orElseThrow());
        }

        /**
         * The current expectation already meeting the incoming one
         * preserves the existing tuple.
         */
        @Test
        @DisplayName("preserves current values when the current expected already meets the incoming one")
        void currentExpectedMeetsIncoming() {
            var result = DeviceExpectedTsUtils.computeNewExpectedTimestamp(t1, t0, t0, t1, t0, t0);
            assertEquals(t1, result.expectedTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampLastDeviceJobTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampUpdateTimestamp().orElseThrow());
        }

        /**
         * No current expectation triggers a fresh expectation with an
         * updated {@code expectedTsUpdateTs}.
         */
        @Test
        @DisplayName("sets a new expectation when no current one exists")
        void freshExpectation() {
            var result = DeviceExpectedTsUtils.computeNewExpectedTimestamp(t1, t0, t0, null, null, null);
            assertEquals(t1, result.expectedTimestamp().orElseThrow());
            assertEquals(t0, result.expectedTimestampLastDeviceJobTimestamp().orElseThrow());
            assertTrue(result.expectedTimestampUpdateTimestamp().isPresent());
        }
    }

    /**
     * Coverage for the four branches of
     * {@link DeviceExpectedTsUtils#isDeviceListStale(DeviceList, Instant, Duration, Instant)}.
     */
    @Nested
    @DisplayName("isDeviceListStale")
    class IsDeviceListStale {
        /**
         * Wall-clock anchor.
         */
        private final Instant now = Instant.parse("2026-01-02T00:00:00Z");

        /**
         * Synthetic two-hour expiry budget.
         */
        private final Duration twoHours = Duration.ofHours(2);

        /**
         * A list older than {@link #twoHours} reports stale.
         */
        @Test
        @DisplayName("returns true when the list is older than the expiry threshold")
        void olderThanExpiry() {
            var stamp = now.minus(Duration.ofHours(3));
            var dl = list(stamp, null, null, null, false);
            assertTrue(DeviceExpectedTsUtils.isDeviceListStale(dl, now, twoHours, null));
        }

        /**
         * A list younger than {@link #twoHours} with no expected-update
         * reports non-stale.
         */
        @Test
        @DisplayName("returns false when the list is within the expiry threshold and has no expected-update")
        void withinExpiry() {
            var stamp = now.minus(Duration.ofMinutes(30));
            var dl = list(stamp, null, null, null, false);
            assertFalse(DeviceExpectedTsUtils.isDeviceListStale(dl, now, twoHours, null));
        }

        /**
         * A list with a fresh expected-update inside the 25-hour grace
         * window reports non-stale.
         */
        @Test
        @DisplayName("returns false when the expected-update is fresh (< 25 hours)")
        void freshExpectedUpdate() {
            var stamp = now.minus(Duration.ofMinutes(30));
            var lastUpdate = now.minus(Duration.ofHours(1));
            var dl = list(stamp, stamp.plus(Duration.ofMinutes(1)), null, lastUpdate, false);
            assertFalse(DeviceExpectedTsUtils.isDeviceListStale(dl, now, twoHours, null));
        }

        /**
         * An old expected-update combined with a diverging last-job
         * timestamp reports stale.
         */
        @Test
        @DisplayName("returns true when expected-update is old AND the last-job timestamp differs from the ADV check time")
        void staleExpectedUpdate() {
            var stamp = now.minus(Duration.ofMinutes(30));
            var lastUpdate = now.minus(Duration.ofHours(26));
            var lastJob = Instant.parse("2025-12-30T00:00:00Z");
            var dl = list(stamp, stamp.plus(Duration.ofMinutes(1)), lastJob, lastUpdate, false);
            var advCheckTime = Instant.parse("2026-01-01T12:00:00Z");
            assertTrue(DeviceExpectedTsUtils.isDeviceListStale(dl, now, twoHours, advCheckTime));
        }

        /**
         * An old expected-update with a matching last-job timestamp reports
         * non-stale.
         */
        @Test
        @DisplayName("returns false when expected-update is old but the last-job timestamp matches the ADV check time")
        void staleExpectedUpdateMatchingJob() {
            var stamp = now.minus(Duration.ofMinutes(30));
            var lastUpdate = now.minus(Duration.ofHours(26));
            var advCheckTime = Instant.parse("2026-01-01T12:00:00Z");
            var dl = list(stamp, stamp.plus(Duration.ofMinutes(1)), advCheckTime, lastUpdate, false);
            assertFalse(DeviceExpectedTsUtils.isDeviceListStale(dl, now, twoHours, advCheckTime));
        }
    }

    /**
     * Coverage for the three branches of
     * {@link DeviceExpectedTsUtils#isDeviceListCloseToExpiration(DeviceList, Instant, Duration)}.
     */
    @Nested
    @DisplayName("isDeviceListCloseToExpiration")
    class IsDeviceListCloseToExpiration {
        /**
         * Wall-clock anchor.
         */
        private final Instant now = Instant.parse("2026-01-02T00:00:00Z");

        /**
         * Synthetic one-hour warning budget.
         */
        private final Duration oneHour = Duration.ofHours(1);

        /**
         * Raw-age beyond {@link #oneHour} reports close-to-expiration.
         */
        @Test
        @DisplayName("returns true when the list is older than the warning threshold")
        void olderThanWarning() {
            var stamp = now.minus(Duration.ofHours(2));
            var dl = list(stamp, null, null, null, false);
            assertTrue(DeviceExpectedTsUtils.isDeviceListCloseToExpiration(dl, now, oneHour));
        }

        /**
         * An expected timestamp ahead of the cached timestamp reports
         * close-to-expiration even when the raw age is small.
         */
        @Test
        @DisplayName("returns true when an expected timestamp is set ahead of the list timestamp")
        void hasFutureExpectation() {
            var stamp = now.minus(Duration.ofMinutes(15));
            var dl = list(stamp, stamp.plus(Duration.ofMinutes(1)), null, null, false);
            assertTrue(DeviceExpectedTsUtils.isDeviceListCloseToExpiration(dl, now, oneHour));
        }

        /**
         * Neither branch fires reports not close-to-expiration.
         */
        @Test
        @DisplayName("returns false when neither condition holds")
        void neitherCondition() {
            var stamp = now.minus(Duration.ofMinutes(15));
            var dl = list(stamp, null, null, null, false);
            assertFalse(DeviceExpectedTsUtils.isDeviceListCloseToExpiration(dl, now, oneHour));
        }
    }
}
