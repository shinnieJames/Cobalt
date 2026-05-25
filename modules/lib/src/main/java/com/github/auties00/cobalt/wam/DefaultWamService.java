package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.props.ABPropsService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides the production {@link WamService} bound to the system UTC clock and
 * to a virtual-thread {@link ScheduledExecutorService} for the periodic
 * serialize and flush ticks.
 *
 * <p>This is the WAM service every embedder wires into the client; it is
 * constructed with the client and AB-props facade, then activated via
 * {@link #initialize()} once the client has authenticated. Tests substitute
 * their own {@link WamService} subclass that overrides {@link #now()},
 * {@link #sleep(long)}, {@link #scheduleRecurring(Runnable, long, long)}, and
 * {@link #cancelAllScheduled()} to drive ticks deterministically.
 *
 * @implNote
 * This implementation backs scheduling with a single-thread
 * {@link Executors#newSingleThreadScheduledExecutor} carried on virtual
 * threads, matching the single-flush threading model documented on
 * {@link WamService}; cancellation drops all outstanding futures but does not
 * shut the executor down so a later
 * {@link #scheduleRecurring(Runnable, long, long)} call can reuse it without
 * re-allocating.
 */
public final class DefaultWamService extends WamService {
    /**
     * Holds the backing executor for
     * {@link #scheduleRecurring(Runnable, long, long)}; created lazily on the
     * first call and reused across {@link #cancelAllScheduled()} cycles.
     */
    private ScheduledExecutorService executor;

    /**
     * Holds the futures returned by
     * {@link #scheduleRecurring(Runnable, long, long)}, tracked so
     * {@link #cancelAllScheduled()} can cancel them all in one pass.
     */
    private final List<ScheduledFuture<?>> futures;

    /**
     * Constructs a production WAM service bound to the given client and
     * AB-props facade, using {@link DefaultWamBeaconingService} for the per-event
     * beacon sequence numbers.
     *
     * <p>The service is dormant until {@link #initialize()} is called after the
     * client has finished its initial handshake; commits made before that point
     * queue into the parent's init buffer and drain on initialize.
     *
     * @param client         the WhatsApp client this service emits events for,
     *                       must not be {@code null}
     * @param abPropsService the AB-props facade used to read the AB key,
     *                       sampling configs, and Falco feature flags, must not
     *                       be {@code null}
     */
    public DefaultWamService(WhatsAppClient client, ABPropsService abPropsService) {
        super(client, abPropsService, new DefaultWamBeaconingService());
        this.futures = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link Instant#now()}, the unmocked UTC clock;
     * identical to {@code WATimeUtils.unixTimeWithoutClockSkewCorrection} apart
     * from the unit (Cobalt callers convert to epoch seconds where the WAM wire
     * format demands it).
     */
    @Override
    protected Instant now() {
        return Instant.now();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to {@link Thread#sleep(long)}, which on a
     * virtual carrier parks the carrier without blocking; it propagates any
     * {@link InterruptedException} so the caller's retry-backoff loop can unwind
     * cleanly.
     */
    @Override
    protected void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation lazily allocates a single-thread executor carried on
     * a virtual thread the first time it is called, then arms
     * {@link ScheduledExecutorService#scheduleWithFixedDelay} so each tick
     * starts {@code periodSeconds} after the previous tick completes (not after
     * it began), keeping ticks from piling up if a flush stalls.
     */
    @Override
    protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        }
        var future = executor.scheduleWithFixedDelay(task, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        futures.add(future);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation cancels each tracked future non-interruptibly (with
     * {@code mayInterruptIfRunning} {@code false}) so an in-flight flush tick is
     * allowed to finish; the executor itself is left running so a later
     * re-initialize sequence can reuse it without paying the thread-factory cost
     * again.
     */
    @Override
    protected void cancelAllScheduled() {
        for (var future : futures) {
            future.cancel(false);
        }
        futures.clear();
    }
}
