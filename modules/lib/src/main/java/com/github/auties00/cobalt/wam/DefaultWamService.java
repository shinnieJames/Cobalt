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
 * Production {@link WamService} implementation that follows
 * WhatsApp Web exactly: a system-UTC clock for commit times,
 * {@link Thread#sleep} for retry backoff, and a virtual-thread
 * single-thread {@link ScheduledExecutorService} for the periodic
 * serialize and flush passes.
 *
 * <p>Tests substitute their own {@link WamService} subclass with
 * deterministic implementations of {@link #now()}, {@link #sleep(long)},
 * {@link #scheduleRecurring(Runnable, long, long)}, and
 * {@link #cancelAllScheduled()}.
 */
public final class DefaultWamService extends WamService {
    /**
     * Underlying executor backing recurring tasks. Created lazily on
     * first call to {@link #scheduleRecurring(Runnable, long, long)}
     * and reused across {@link #cancelAllScheduled()} boundaries.
     */
    private ScheduledExecutorService executor;

    /**
     * Outstanding futures returned by
     * {@link #scheduleRecurring(Runnable, long, long)}, cleared by
     * {@link #cancelAllScheduled()}.
     */
    private final List<ScheduledFuture<?>> futures;

    /**
     * Constructs a new {@code DefaultWamService} bound to the given
     * client.
     *
     * <p>The service is not active until {@link #initialize()} is
     * called after the client has authenticated.
     *
     * @param client         the WhatsApp client instance, must not be
     *                       {@code null}
     * @param abPropsService the AB props service for reading the AB
     *                       key and feature flags, must not be
     *                       {@code null}
     */
    public DefaultWamService(WhatsAppClient client, ABPropsService abPropsService) {
        super(client, abPropsService, new DefaultWamBeaconing());
        this.futures = new ArrayList<>();
    }

    @Override
    protected Instant now() {
        return Instant.now();
    }

    @Override
    protected void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    @Override
    protected void scheduleRecurring(Runnable task, long initialDelaySeconds, long periodSeconds) {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        }
        var future = executor.scheduleWithFixedDelay(task, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
        futures.add(future);
    }

    @Override
    protected void cancelAllScheduled() {
        for (var future : futures) {
            future.cancel(false);
        }
        futures.clear();
    }
}
