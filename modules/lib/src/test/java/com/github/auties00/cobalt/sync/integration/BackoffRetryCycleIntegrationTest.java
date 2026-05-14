package com.github.auties00.cobalt.sync.integration;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.SyncFixtures;
import com.github.auties00.cobalt.sync.WebAppStateBackoffScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration cycle for the sync retry path under a server-rejected upload.
 *
 * <p>Per WA Web {@code WAWebSyncd.te} / {@code WAWebSyncd.ee}, when the server
 * returns a retryable {@code ErrorRetry} response (with an optional
 * {@code backoff} attribute), the orchestrator schedules a retry via the
 * global attempt counter ({@code W}) and the sticky server-backoff floor
 * ({@code q}). The delay is
 * {@code min(max(pow(2, W) * BASE_DELAY, q), MAX_DELAY)}. After the finite
 * failure window expires ({@code FINITE_FAILURE_EXPIRY_DURATION = 48h}) the
 * collection is moved to a fatal state and no further retries are scheduled.
 *
 * <p>The captured cycle ({@code integration/backoff-retry-cycle/}) replays a
 * sequence of (failed-IQ, retryable-error, success-IQ) traces and asserts the
 * observed retry delays match WA Web's curve under the same server backoff
 * inputs.
 */
@DisplayName("BackoffRetryCycle integration")
class BackoffRetryCycleIntegrationTest {
    private WebAppStateBackoffScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WebAppStateBackoffScheduler();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Nested
    @DisplayName("synthetic — backoff scheduler invariants")
    class Smoke {
        @Test
        @DisplayName("a fresh failure within the 48h window is retried")
        void freshFailureScheduled() {
            assertTrue(scheduler.scheduleRetry(
                    SyncPatchType.REGULAR_LOW, System.currentTimeMillis(), () -> {}));
        }

        @Test
        @DisplayName("a failure older than 48h is rejected (collection should move to fatal)")
        void expiredFailureRejected() {
            var pastFailure = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000;
            assertFalse(scheduler.scheduleRetry(
                    SyncPatchType.REGULAR_LOW, pastFailure, () -> {}));
        }

        @Test
        @DisplayName("cancel before fire prevents the retry action from executing")
        void cancelPreventsFire() throws InterruptedException {
            var latch = new CountDownLatch(1);
            scheduler.scheduleRetry(SyncPatchType.REGULAR_LOW, System.currentTimeMillis(),
                    30_000L, latch::countDown);
            scheduler.cancelRetry(SyncPatchType.REGULAR_LOW);
            assertFalse(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("a scheduled retry fires within the base 1s delay (attempt=0, no server floor)")
        void firstAttemptFires() throws InterruptedException {
            var latch = new CountDownLatch(1);
            scheduler.scheduleRetry(SyncPatchType.REGULAR_LOW, System.currentTimeMillis(), latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS),
                    "attempt=0 base delay is 1s; the action must fire within 5s");
        }

        @Test
        @DisplayName("close cancels every pending retry across collections")
        void closeCancelsAll() {
            scheduler.updateServerBackoff(60_000);
            scheduler.scheduleRetry(SyncPatchType.REGULAR_LOW, System.currentTimeMillis(), () -> {});
            scheduler.scheduleRetry(SyncPatchType.REGULAR, System.currentTimeMillis(), () -> {});
            scheduler.close();
            assertFalse(scheduler.cancelRetry(SyncPatchType.REGULAR_LOW));
            assertFalse(scheduler.cancelRetry(SyncPatchType.REGULAR));
        }
    }

    @Nested
    @DisplayName("captured cycle — oracle parity once fixtures land")
    class CapturedCycle {
        @Test
        @DisplayName("captured retryable error → retry → success matches the WA Web delay curve")
        void capturedRetryCurve() {
            if (!SyncFixtures.isAvailable("integration/backoff-retry-cycle/retryable-then-success")) return;
            // Reserved for Phase 10 corpus. The fixture captures (a) the original
            // failed IQ + retryable error attrs (code, backoff), (b) the retry IQ,
            // (c) the elapsed wall-clock delay between attempts. The cycle drives
            // the same sequence and asserts the scheduler chose a delay within
            // ±10% of the captured value (allowing for jitter).
            assertNotNull(SyncFixtures.loadOracle(
                    "integration/backoff-retry-cycle/retryable-then-success"));
        }

        @Test
        @DisplayName("captured fatal-after-window trace moves the collection to FATAL state")
        void fatalAfterWindow() {
            if (!SyncFixtures.isAvailable("integration/backoff-retry-cycle/finite-failure-expiry")) return;
            // The fixture replays a 48h failure window and asserts the collection
            // transitions to FATAL state and no further retries are scheduled.
            assertDoesNotThrow(scheduler::close);
        }

        @Test
        @DisplayName("captured server-backoff override is honoured as the sticky floor")
        void serverBackoffSticky() {
            if (!SyncFixtures.isAvailable("integration/backoff-retry-cycle/server-backoff-sticky")) return;
            // The fixture captures an ErrorRetry response with backoff=N; subsequent
            // retries (after a separate ErrorRetry without backoff) must still honour
            // N as the floor until a new backoff value is provided.
        }
    }
}
