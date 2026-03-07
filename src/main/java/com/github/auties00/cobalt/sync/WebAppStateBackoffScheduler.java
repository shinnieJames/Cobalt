package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.*;

public final class WebAppStateBackoffScheduler implements Closeable {
    private static final long BASE_DELAY_MS = 1000;
    private static final long MAX_DELAY_MS = 3_600_000;
    private static final int MULTIPLIER = 2;
    private static final long JITTER_MS = 1000;
    private static final long FINITE_FAILURE_EXPIRY_MS = 2 * 24 * 60 * 60 * 1000L;

    private final ConcurrentHashMap<SyncPatchType, CompletableFuture<?>> pendingRetries;

    public WebAppStateBackoffScheduler() {
        this.pendingRetries = new ConcurrentHashMap<>();
    }

    /**
     * Schedules a retry with exponential backoff.
     *
     * @param collectionName        the collection to retry
     * @param firstFailureTimestamp the timestamp of the first failure in this series
     * @param attemptNumber         the current retry attempt number
     * @param serverBackoffMs       the server-suggested backoff in milliseconds, or {@code null}
     * @param retryAction           the action to execute on retry
     * @return {@code true} if the retry was scheduled, {@code false} if the failure window expired
     */
    public boolean scheduleRetry(SyncPatchType collectionName, long firstFailureTimestamp, int attemptNumber, Long serverBackoffMs, Runnable retryAction) {
        // Check if failure window has expired
        var elapsed = System.currentTimeMillis() - firstFailureTimestamp;
        if (elapsed >= FINITE_FAILURE_EXPIRY_MS) {
            return false;
        }

        // Cancel any existing retry for this collection
        cancelRetry(collectionName);

        // Calculate backoff delay, applying server backoff floor per WA Web
        var delayMs = calculateBackoff(attemptNumber);
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
        if (attemptNumber < 0) {
            throw new IllegalArgumentException("Attempt number cannot be negative");
        }

        // Calculate exponential delay
        var delay = (long) (BASE_DELAY_MS * Math.pow(MULTIPLIER, attemptNumber));

        // Cap at maximum delay
        delay = Math.min(delay, MAX_DELAY_MS);

        // Add random jitter to prevent thundering herd
        delay += (long) (Math.random() * JITTER_MS);

        return delay;
    }

    public boolean cancelRetry(SyncPatchType collectionName) {
        var future = pendingRetries.remove(collectionName);
        if (future != null) {
            future.cancel(false);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        for (var future : pendingRetries.values()) {
            future.cancel(true);
        }
        pendingRetries.clear();
    }
}
