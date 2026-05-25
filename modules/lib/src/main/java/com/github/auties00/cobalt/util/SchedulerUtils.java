package com.github.auties00.cobalt.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Fires one-shot tasks on a fresh virtual thread after a configurable delay.
 *
 * <p>A Cobalt subsystem that needs a deferred callback (retry backoff,
 * debounced flush, lifecycle teardown) without standing up a
 * {@link java.util.concurrent.ScheduledExecutorService} schedules it through
 * {@link #scheduleDelayed(Duration, Runnable)}.
 */
public final class SchedulerUtils {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private SchedulerUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Schedules {@code task} to run on a fresh virtual thread after
     * {@code delay} has elapsed.
     *
     * <p>The task runs fire-and-forget on its own virtual thread, so the caller
     * does not share a thread pool. The returned future completes normally when
     * {@code task} returns and completes exceptionally if {@code task} throws.
     *
     * @implNote
     * This implementation composes
     * {@link CompletableFuture#delayedExecutor(long, TimeUnit, java.util.concurrent.Executor)}
     * with {@link Thread#startVirtualThread(Runnable)} so each scheduled task
     * gets its own virtual thread; the delay timer runs on the common scheduler
     * shared with other {@code delayedExecutor} callers.
     *
     * @param delay the amount of time to wait before running the task; resolved
     *              with nanosecond precision
     * @param task  the task to execute
     * @return a future that completes when {@code task} finishes, or completes
     *         exceptionally if {@code task} throws
     */
    public static CompletableFuture<Void> scheduleDelayed(Duration delay, Runnable task) {
        var delayedExecutor = CompletableFuture.delayedExecutor(delay.toNanos(), TimeUnit.NANOSECONDS, Thread::startVirtualThread);
        return CompletableFuture.runAsync(task, delayedExecutor);
    }
}
