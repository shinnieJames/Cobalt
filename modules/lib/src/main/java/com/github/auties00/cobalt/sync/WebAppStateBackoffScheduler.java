package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Schedules retries of failed syncd patch round-trips with exponential
 * backoff, mirroring the timer state inside WhatsApp Web's
 * {@code WAWebSyncd} module.
 *
 * @apiNote Driven by {@link WebAppStateService} (and indirectly by every
 * outgoing-mutation factory that goes through {@code WAWebSyncdServerSync})
 * after a server round-trip lands a transient error. Most embedders never
 * touch the scheduler directly; it is exposed as a separate class so that
 * tests and integration cycles can drive its observable invariants
 * (finite-failure expiry, sticky server backoff, attempt-counter reset)
 * without booting the rest of the syncd stack.
 *
 * @implNote This implementation matches the exact constants and curve from
 * {@code WAWebSyncd.ne}: {@code min(max(BASE_DELAY * MULTIPLIER^attempt,
 * serverBackoff), MAX_DELAY)} with {@code BASE_DELAY=1s},
 * {@code MULTIPLIER=2}, {@code MAX_DELAY=1h}. Two state variables back the
 * curve: a single global attempt counter ({@link #globalAttemptCounter},
 * WA Web's module-level {@code W}) shared across every collection, and a
 * sticky server-suggested floor ({@link #stickyServerBackoffMs}, WA Web's
 * module-level {@code q}) that survives across retries until it is
 * explicitly overwritten by an {@code ErrorRetry} response carrying a new
 * {@code serverBackoff}. The finite-failure expiry threshold of
 * {@value #FINITE_FAILURE_EXPIRY_MS} ms (2 days) matches
 * {@code WASyncdConst.FINITE_FAILURE_EXPIRY_DURATION}; past that window the
 * collection is considered fatally broken and the scheduler refuses to
 * reschedule, leaving the caller to escalate via
 * {@code WAWebSyncdCollectionsStateMachine.moveCollectionsToFatal}.
 */
public final class WebAppStateBackoffScheduler implements Closeable {
    /**
     * The base delay applied at attempt zero, in milliseconds, matching
     * {@code WASyncdConst.BACKOFF_MIN_TIMEOUT}.
     */
    private static final long BASE_DELAY_MS = 1000;

    /**
     * The hard ceiling for any backoff delay, in milliseconds (one hour),
     * matching {@code WASyncdConst.BACKOFF_MAX_TIMEOUT}.
     */
    private static final long MAX_DELAY_MS = 3_600_000;

    /**
     * The exponential growth factor applied per attempt, matching
     * {@code WASyncdConst.BACKOFF_BASE}.
     */
    private static final int MULTIPLIER = 2;

    /**
     * The cumulative time a collection may stay in the finite-retry state
     * before being rejected as expired, in milliseconds (two days),
     * matching {@code WASyncdConst.FINITE_FAILURE_EXPIRY_DURATION}.
     */
    private static final long FINITE_FAILURE_EXPIRY_MS = 2 * 24 * 60 * 60 * 1000L;

    /**
     * The map of in-flight per-collection retry futures, used to cancel
     * pending retries on {@link #cancelRetry(SyncPatchType)} or
     * {@link #close()}.
     */
    private final ConcurrentHashMap<SyncPatchType, CompletableFuture<?>> pendingRetries;

    /**
     * The global attempt counter shared across every collection, mirroring
     * WA Web's module-level {@code W} variable.
     */
    private final AtomicInteger globalAttemptCounter;

    /**
     * The sticky server-suggested backoff floor in milliseconds, mirroring
     * WA Web's module-level {@code q} variable.
     */
    private final AtomicLong stickyServerBackoffMs;

    /**
     * Builds a scheduler with both the attempt counter and the sticky
     * server backoff initialised to zero.
     *
     * @apiNote Called once by {@link WebAppStateService} during its own
     * construction; the scheduler outlives every individual sync round
     * and is closed when the parent service is closed.
     */
    public WebAppStateBackoffScheduler() {
        this.pendingRetries = new ConcurrentHashMap<>();
        this.globalAttemptCounter = new AtomicInteger(0);
        this.stickyServerBackoffMs = new AtomicLong(0);
    }

    /**
     * Schedules a retry for {@code collectionName}, optionally updating
     * the sticky server-suggested backoff before computing the delay.
     *
     * @apiNote Convenience overload of
     * {@link #scheduleRetry(SyncPatchType, long, Runnable)} for callers
     * that have just received an {@code ErrorRetry} server response and
     * already know the server's suggested backoff value. Pass
     * {@code null} for {@code serverBackoffMs} to leave the existing
     * sticky floor untouched.
     *
     * @param collectionName        the collection whose sync round failed
     * @param firstFailureTimestamp the wall-clock millisecond timestamp at
     *                              which the current finite-retry series
     *                              began, used by the expiry gate
     * @param serverBackoffMs       the server-suggested backoff floor in
     *                              milliseconds, or {@code null} to keep
     *                              the existing sticky value
     * @param retryAction           the action invoked on the timer thread
     *                              once the backoff elapses
     * @return {@code true} when the retry was scheduled, {@code false}
     *         when the finite-failure window had already expired
     */
    public boolean scheduleRetry(SyncPatchType collectionName, long firstFailureTimestamp, Long serverBackoffMs, Runnable retryAction) {
        if (serverBackoffMs != null) {
            stickyServerBackoffMs.set(serverBackoffMs);
        }
        return scheduleRetry(collectionName, firstFailureTimestamp, retryAction);
    }

    /**
     * Schedules a retry for {@code collectionName} using the current
     * sticky server backoff floor.
     *
     * @apiNote Called by the syncd retry loop after every transient
     * failure. The action is fired asynchronously on the
     * {@link SchedulerUtils} virtual-thread scheduler once the computed
     * backoff elapses; cancellation is supported through
     * {@link #cancelRetry(SyncPatchType)}.
     *
     * @implNote This implementation reads-and-increments
     * {@link #globalAttemptCounter} atomically, then computes
     * {@code min(max(BASE * MULTIPLIER^attempt, sticky), MAX)} as the
     * delay. Any pending retry on the same collection is cancelled before
     * scheduling so that the second call wins, mirroring WA Web's
     * {@code clearTimeout(O)} reset in {@code WAWebSyncd.ae}.
     *
     * @param collectionName        the collection whose sync round failed
     * @param firstFailureTimestamp the wall-clock millisecond timestamp at
     *                              which the current finite-retry series
     *                              began
     * @param retryAction           the action invoked on the timer thread
     *                              once the backoff elapses
     * @return {@code true} when the retry was scheduled, {@code false}
     *         when {@code firstFailureTimestamp} is more than
     *         {@value #FINITE_FAILURE_EXPIRY_MS} ms in the past
     */
    public boolean scheduleRetry(SyncPatchType collectionName, long firstFailureTimestamp, Runnable retryAction) {
        var elapsed = System.currentTimeMillis() - firstFailureTimestamp;
        if (elapsed > FINITE_FAILURE_EXPIRY_MS) {
            return false;
        }

        cancelRetry(collectionName);

        var attempt = globalAttemptCounter.getAndIncrement();
        var delayMs = calculateBackoff(attempt, stickyServerBackoffMs.get());

        var future = SchedulerUtils.scheduleDelayed(Duration.ofMillis(delayMs), () -> {
            pendingRetries.remove(collectionName);
            retryAction.run();
        });
        pendingRetries.put(collectionName, future);

        return true;
    }

    /**
     * Stores a new sticky server-backoff floor and resets the global
     * attempt counter to zero.
     *
     * @apiNote Called by the syncd response handler when the server
     * returns an {@code ErrorRetry} verdict carrying a fresh
     * {@code serverBackoff} value, matching WA Web's
     * {@code q = i[0].serverBackoff || 0; W = 0} in
     * {@code WAWebSyncd.ee}. The new floor stays in effect for every
     * subsequent retry until the next {@code ErrorRetry} response or an
     * explicit {@link #close()}.
     *
     * @param serverBackoffMs the server-suggested backoff floor in
     *                        milliseconds; {@code 0} disables the floor
     */
    public void updateServerBackoff(long serverBackoffMs) {
        stickyServerBackoffMs.set(serverBackoffMs);
        globalAttemptCounter.set(0);
    }

    /**
     * Computes the backoff delay for the supplied attempt and floor.
     *
     * @implNote This implementation follows
     * {@code WAWebSyncd.ne(W, q): min(max(pow(BASE, W) * MIN, q), MAX)};
     * the cap at {@link #MAX_DELAY_MS} applies whether the dominant term
     * is the exponential growth or the sticky floor.
     *
     * @param attemptNumber the attempt index (zero-based)
     * @param serverBackoff the sticky server backoff floor in milliseconds
     * @return the resulting delay in milliseconds
     */
    private long calculateBackoff(int attemptNumber, long serverBackoff) {
        var computed = (long) (BASE_DELAY_MS * Math.pow(MULTIPLIER, attemptNumber));
        var delay = Math.max(computed, serverBackoff);
        return Math.min(delay, MAX_DELAY_MS);
    }

    /**
     * Cancels any pending retry for {@code collectionName}.
     *
     * @apiNote Called by {@link #scheduleRetry(SyncPatchType, long, Runnable)}
     * before scheduling a new retry, and by callers that want to drop a
     * pending retry on demand (for example, after a successful sync of
     * the same collection through a different path).
     *
     * @param collectionName the collection whose pending retry, if any,
     *                       should be cancelled
     * @return {@code true} when a pending retry existed and was
     *         cancelled, {@code false} otherwise
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
     * Resets the global attempt counter to zero without touching the
     * sticky server backoff.
     *
     * @apiNote Called by {@link WebAppStateService} after a successful
     * sync round; future retries restart from
     * {@code BASE_DELAY * MULTIPLIER^0} but still honour the last sticky
     * server-suggested floor.
     */
    public void resetAttemptCounter() {
        globalAttemptCounter.set(0);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation force-cancels every pending retry
     * (passing {@code true} so already-running timers are interrupted),
     * empties {@link #pendingRetries}, and resets both
     * {@link #globalAttemptCounter} and {@link #stickyServerBackoffMs} to
     * zero. The method is safe to call repeatedly: subsequent invocations
     * simply find an empty map and idempotent counter assignments.
     */
    @Override
    public void close() {
        for (var future : pendingRetries.values()) {
            future.cancel(true);
        }
        pendingRetries.clear();
        globalAttemptCounter.set(0);
        stickyServerBackoffMs.set(0);
    }
}
