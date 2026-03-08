package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules retries for failed app state sync operations using exponential backoff.
 *
 * <p>Per WhatsApp Web, this scheduler uses a single global attempt counter shared
 * across all collections, rather than per-collection counters. The backoff delay
 * is computed as {@code BASE_DELAY_MS * 2^attempt}, capped at {@code MAX_DELAY_MS}.
 *
 * <p>If the elapsed time since the first failure exceeds the finite failure expiry
 * window ({@value #FINITE_FAILURE_EXPIRY_MS} ms), the retry is rejected.
 */
public final class WebAppStateBackoffScheduler implements Closeable {
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 3_600_000;
    private static final int MULTIPLIER = 2;
    private static final long FINITE_FAILURE_EXPIRY_MS = 2 * 24 * 60 * 60 * 1000L;

    private final ConcurrentHashMap<SyncPatchType, CompletableFuture<?>> pendingRetries;
    private final AtomicInteger globalAttemptCounter;

    /**
     * Constructs a new {@code WebAppStateBackoffScheduler}.
     */
    public WebAppStateBackoffScheduler() {
        this.pendingRetries = new ConcurrentHashMap<>();
        this.globalAttemptCounter = new AtomicInteger(0);
    }

    /**
     * Schedules a retry with exponential backoff using the global attempt counter.
     *
     * <p>The global attempt counter is incremented on each call. If a server-suggested
     * backoff is provided, the actual delay is the maximum of the computed backoff and
     * the server suggestion.
     *
     * @param collectionName       the collection to retry
     * @param firstFailureTimestamp the timestamp of the first failure in this series
     * @param serverBackoffMs      the server-suggested backoff in milliseconds, or {@code null}
     * @param retryAction          the action to execute on retry
     * @return {@code true} if the retry was scheduled, {@code false} if the failure window expired
     */
    public boolean scheduleRetry(SyncPatchType collectionName, long firstFailureTimestamp, Long serverBackoffMs, Runnable retryAction) {
        // Check if failure window has expired
        var elapsed = System.currentTimeMillis() - firstFailureTimestamp;
        if (elapsed >= FINITE_FAILURE_EXPIRY_MS) {
            return false;
        }

        // Cancel any existing retry for this collection
        cancelRetry(collectionName);

        // Per WA Web: increment attempt counter BEFORE computing delay,
        // so the first retry uses attempt=1 giving 2^1 * 1000 = 2000ms
        var attempt = globalAttemptCounter.incrementAndGet();
        var delayMs = calculateBackoff(attempt);
        if (serverBackoffMs != null && serverBackoffMs > 0) {
            delayMs = Math.max(delayMs, serverBackoffMs);
        }

        // Schedule the retry
        var future = SchedulerUtils.scheduleDelayed(Duration.ofMillis(delayMs), () -> {
            pendingRetries.remove(collectionName);
            retryAction.run();
        });
        pendingRetries.put(collectionName, future);

        return true;
    }

    private long calculateBackoff(int attemptNumber) {
        // Calculate exponential delay
        var delay = (long) (BASE_DELAY_MS * Math.pow(MULTIPLIER, attemptNumber));

        // Cap at maximum delay
        delay = Math.min(delay, MAX_DELAY_MS);

        return delay;
    }

    /**
     * Cancels a pending retry for the specified collection.
     *
     * @param collectionName the collection whose retry to cancel
     * @return {@code true} if a pending retry was cancelled
     */
    public boolean cancelRetry(SyncPatchType collectionName) {
        var future = pendingRetries.remove(collectionName);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Resets the global attempt counter.
     *
     * <p>Should be called when a sync succeeds or on reconnect to reset the
     * backoff progression.
     */
    public void resetAttemptCounter() {
        globalAttemptCounter.set(0);
    }

    @Override
    public void close() {
        for (var future : pendingRetries.values()) {
            future.cancel(true);
        }
        pendingRetries.clear();
        globalAttemptCounter.set(0);
    }
}
