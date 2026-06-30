package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.ScheduledTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides the production {@link WamService} bound to the system UTC clock and
 * to virtual-thread {@link ScheduledTask} recurrences for the periodic
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
 * This implementation backs each recurring tick with one
 * {@link ScheduledTask#schedule(Duration, Duration, Runnable)} handle running on
 * its own virtual thread with fixed-delay semantics, matching the single-flush
 * threading model documented on {@link WamService}; cancellation cancels every
 * outstanding handle and clears the tracking list so a later
 * {@link #scheduleRecurring(Runnable, long, long)} call starts fresh.
 */
public final class LiveWamService extends WamService {
    /**
     * Holds the handles returned by
     * {@link #scheduleRecurring(Runnable, long, long)}, tracked so
     * {@link #cancelAllScheduled()} can cancel them all in one pass.
     */
    private final List<ScheduledTask> futures;

    /**
     * Constructs a production WAM service bound to the given client and
     * AB-props facade, using {@link LiveWamBeaconingService} for the per-event
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
    public LiveWamService(LinkedWhatsAppClient client, ABPropsService abPropsService) {
        super(client, abPropsService, new LiveWamBeaconingService());
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
     * This implementation schedules the tick through
     * {@link ScheduledTask#schedule(Duration, Duration, Runnable)} with
     * fixed-delay semantics, so each tick starts {@code periodSeconds} after the
     * previous tick completes (not after it began), keeping ticks from piling up
     * if a flush stalls.
     */
    @Override
    protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
        var handle = ScheduledTask.schedule(Duration.ofSeconds(initialDelaySeconds), Duration.ofSeconds(periodSeconds), task);
        futures.add(handle);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation cancels each tracked handle, which wakes a pending
     * tick so it never fires and interrupts one already running, then clears the
     * list so a later re-initialize sequence schedules fresh handles.
     */
    @Override
    protected void cancelAllScheduled() {
        for (var handle : futures) {
            handle.cancel();
        }
        futures.clear();
    }
}
