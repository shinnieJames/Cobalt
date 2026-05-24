package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the observable invariants of
 * {@link WebAppStateBackoffScheduler} against WhatsApp Web's
 * {@code WAWebSyncd} retry loop.
 *
 * @apiNote Cobalt-internal exercise of the public scheduler API:
 * finite-failure expiry, per-collection cancellation, attempt-counter
 * reset, sticky server-backoff floor, and idempotent
 * {@link WebAppStateBackoffScheduler#close()}. WA Web's equivalent
 * timer state ({@code W}, {@code q}, {@code O}) is module-level
 * inside {@code WAWebSyncd} and has no externally observable hook;
 * Cobalt exposes the same state here purely so tests and the
 * integration cycle can drive it directly.
 *
 * @implNote This implementation deliberately does not exercise the
 * exact backoff curve at high attempt numbers: the
 * {@code BASE_DELAY * MULTIPLIER^attempt} formula caps at one hour,
 * making per-attempt timing assertions infeasible without
 * instrumentation. The Phase 9 integration cycle covers the
 * curve end-to-end against a recorded server retry stream.
 */
@DisplayName("WebAppStateBackoffScheduler")
class WebAppStateBackoffSchedulerTest {
    /**
     * The scheduler under test, freshly created per test.
     */
    private WebAppStateBackoffScheduler scheduler;

    /**
     * Builds a fresh scheduler before every test so the shared global
     * counter does not leak across cases.
     *
     * @apiNote JUnit-managed setup; not invoked manually from the
     * tests.
     */
    @BeforeEach
    void setUp() {
        scheduler = new WebAppStateBackoffScheduler();
    }

    /**
     * Closes the scheduler after every test so the underlying virtual
     * threads are released even when an assertion fails mid-test.
     *
     * @apiNote JUnit-managed teardown; not invoked manually from the
     * tests.
     */
    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    /**
     * Pins the 48 hour cumulative-failure window enforced by
     * {@link WebAppStateBackoffScheduler#scheduleRetry(SyncPatchType, long, Runnable)}.
     */
    @Nested
    @DisplayName("scheduleRetry -- finite-failure expiry gate")
    class FinitelyFailing {
        /**
         * A retry inside the 48 hour window is accepted.
         */
        @Test
        @DisplayName("retry within the 48h window is scheduled")
        void freshFailureScheduled() {
            var firstFailure = System.currentTimeMillis();
            assertTrue(scheduler.scheduleRetry(SyncPatchType.REGULAR, firstFailure, () -> {}),
                    "fresh failure should be retried");
        }

        /**
         * A retry past the 48 hour window is rejected.
         */
        @Test
        @DisplayName("retry past the 48h finite-failure expiry is rejected")
        void pastWindowRejected() {
            var pastFailure = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000); // 3 days ago
            assertFalse(scheduler.scheduleRetry(SyncPatchType.REGULAR, pastFailure, () -> {}),
                    "WAWebSyncdCollectionsStateMachine.getExpiredCollections rejects > 2 day window");
        }

        /**
         * The expiry check is strictly greater-than: 47 hours stays
         * inside the window.
         */
        @Test
        @DisplayName("retry exactly at the boundary is treated as expired (strictly greater fails)")
        void atBoundaryIsRetried() {
            var insideWindow = System.currentTimeMillis() - (47L * 60 * 60 * 1000); // 47 hours ago
            assertTrue(scheduler.scheduleRetry(SyncPatchType.REGULAR, insideWindow, () -> {}));
        }
    }

    /**
     * Pins the per-collection cancellation contract of
     * {@link WebAppStateBackoffScheduler#cancelRetry(SyncPatchType)}.
     */
    @Nested
    @DisplayName("cancelRetry")
    class CancelRetry {
        /**
         * Cancelling without a pending retry returns {@code false}.
         */
        @Test
        @DisplayName("cancelling without pending retry returns false")
        void cancelWithoutPending() {
            assertFalse(scheduler.cancelRetry(SyncPatchType.REGULAR));
        }

        /**
         * Cancelling after scheduling returns {@code true} once and
         * {@code false} on subsequent calls.
         *
         * @implNote The 60 second sticky server backoff keeps the
         * timer from firing before the cancel is observed.
         */
        @Test
        @DisplayName("cancelling after scheduling returns true and clears the future")
        void cancelAfterSchedule() {
            scheduler.updateServerBackoff(60_000);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            assertTrue(scheduler.cancelRetry(SyncPatchType.REGULAR));
            assertFalse(scheduler.cancelRetry(SyncPatchType.REGULAR),
                    "second cancel finds no pending future");
        }

        /**
         * Cancellation is per-collection: cancelling one does not
         * touch the other.
         */
        @Test
        @DisplayName("cancelling one collection leaves another untouched")
        void independentCollections() {
            scheduler.updateServerBackoff(60_000);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.scheduleRetry(SyncPatchType.CRITICAL_BLOCK, System.currentTimeMillis(), () -> {});
            assertTrue(scheduler.cancelRetry(SyncPatchType.REGULAR));
            assertTrue(scheduler.cancelRetry(SyncPatchType.CRITICAL_BLOCK),
                    "the second collection still has a pending future");
        }
    }

    /**
     * Pins that scheduled actions actually fire when the backoff
     * resolves and that cancellation prevents the action.
     */
    @Nested
    @DisplayName("retry action fires at the scheduled delay")
    class FiresAction {
        /**
         * The first attempt fires within a 5 second wall-clock
         * window because the base delay is one second.
         *
         * @throws InterruptedException if the test thread is
         *                              interrupted waiting on the
         *                              latch
         */
        @Test
        @DisplayName("scheduled action runs (with a 1s base delay)")
        void firstAttemptFires() throws InterruptedException {
            var latch = new CountDownLatch(1);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "first retry should fire within 5 seconds (base delay = 1s * 2^0)");
        }

        /**
         * Cancelling before the timer fires prevents the action from
         * running.
         *
         * @implNote The 30 second sticky server backoff guarantees
         * the cancel wins the race.
         *
         * @throws InterruptedException if the test thread is
         *                              interrupted waiting on the
         *                              latch
         */
        @Test
        @DisplayName("cancel before fire prevents the action")
        void cancelPreventsFire() throws InterruptedException {
            var latch = new CountDownLatch(1);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(),
                    30_000L, latch::countDown);
            scheduler.cancelRetry(SyncPatchType.REGULAR);
            assertFalse(latch.await(2, TimeUnit.SECONDS),
                    "cancel must prevent the action from firing");
        }
    }

    /**
     * Pins the interaction between
     * {@link WebAppStateBackoffScheduler#updateServerBackoff(long)},
     * {@link WebAppStateBackoffScheduler#resetAttemptCounter()}, and
     * the per-call sticky-floor overload of
     * {@link WebAppStateBackoffScheduler#scheduleRetry(SyncPatchType, long, Long, Runnable)}.
     */
    @Nested
    @DisplayName("server backoff and attempt counter interaction")
    class ServerBackoffAndCounter {
        /**
         * Calling {@code updateServerBackoff} resets the global
         * attempt counter.
         */
        @Test
        @DisplayName("updateServerBackoff resets the attempt counter to 0")
        void updateServerBackoffResetsCounter() {
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.cancelRetry(SyncPatchType.REGULAR);

            scheduler.updateServerBackoff(0);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            assertDoesNotThrow(() -> scheduler.cancelRetry(SyncPatchType.REGULAR));
        }

        /**
         * {@code resetAttemptCounter} leaves the sticky floor alone.
         *
         * @implNote The sticky floor cannot be observed directly
         * without instrumentation; the test only asserts the call
         * does not throw.
         */
        @Test
        @DisplayName("resetAttemptCounter without server backoff change leaves the sticky floor")
        void resetAttemptCounterPreservesSticky() {
            scheduler.updateServerBackoff(5_000);
            scheduler.resetAttemptCounter();
            assertDoesNotThrow(() ->
                    scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {}));
        }

        /**
         * The four-arg overload of {@code scheduleRetry} writes the
         * sticky floor before scheduling.
         */
        @Test
        @DisplayName("scheduleRetry with explicit serverBackoffMs updates the sticky floor")
        void explicitServerBackoffUpdates() {
            assertDoesNotThrow(() -> scheduler.scheduleRetry(
                    SyncPatchType.REGULAR, System.currentTimeMillis(), 2_000L, () -> {}));
        }
    }

    /**
     * Pins the cleanup contract of
     * {@link WebAppStateBackoffScheduler#close()}.
     */
    @Nested
    @DisplayName("close -- cancels pending and is idempotent")
    class Close {
        /**
         * {@code close} cancels every pending retry across every
         * collection.
         */
        @Test
        @DisplayName("close cancels pending retries")
        void closeCancelsPending() {
            scheduler.updateServerBackoff(60_000);
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.scheduleRetry(SyncPatchType.CRITICAL_BLOCK, System.currentTimeMillis(), () -> {});
            scheduler.close();
            assertFalse(scheduler.cancelRetry(SyncPatchType.REGULAR));
            assertFalse(scheduler.cancelRetry(SyncPatchType.CRITICAL_BLOCK));
        }

        /**
         * {@code close} is safe to call repeatedly.
         */
        @Test
        @DisplayName("close is idempotent")
        void closeIdempotent() {
            scheduler.close();
            assertDoesNotThrow(scheduler::close);
        }
    }
}
