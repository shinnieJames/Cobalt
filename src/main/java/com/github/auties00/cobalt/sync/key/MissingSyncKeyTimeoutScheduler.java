package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules timeout checks for missing sync keys.
 * <p>
 * Per WhatsApp Web WAWebSyncdStoreMissingKeys: when sync keys are missing,
 * a timeout is scheduled. If the keys are still missing after the timeout,
 * a fatal sync error is triggered.
 */
public final class MissingSyncKeyTimeoutScheduler {
    private static final System.Logger LOGGER = System.getLogger("MissingSyncKeyTimeoutScheduler");

    private final WhatsAppClient client;
    private final WhatsAppStore store;
    private final ABPropsService abPropsService;
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledCheck;

    public MissingSyncKeyTimeoutScheduler(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = client;
        this.store = client.store();
        this.abPropsService = abPropsService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "MissingSyncKeyTimeoutScheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Schedules or reschedules the timeout check for missing sync keys.
     * <p>
     * Per WhatsApp Web: calculates timeout based on earliest missing key timestamp
     * and schedules a check when that timeout expires.
     */
    public synchronized void scheduleTimeoutCheck() {
        // Cancel any existing scheduled check
        if (scheduledCheck != null && !scheduledCheck.isDone()) {
            scheduledCheck.cancel(false);
        }

        var timeout = getTimeout();
        var delay = store.calculateMissingSyncKeyTimeoutDelay(timeout);

        if (delay.isEmpty()) {
            // No missing keys, nothing to schedule
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync keys, timeout check not scheduled");
            return;
        }

        var delayMs = delay.get().toMillis();
        LOGGER.log(System.Logger.Level.DEBUG, "Scheduling missing sync key timeout check in {0}ms", delayMs);

        scheduledCheck = scheduler.schedule(this::checkForExpiredKeys, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Checks for expired missing keys and triggers fatal error if any are found.
     * <p>
     * Per WhatsApp Web WAWebSyncdStoreMissingKeys._timeoutWhileWaitingForMissingKey:
     * If keys have expired, report fatal error and trigger handleSyncdFatal.
     */
    private void checkForExpiredKeys() {
        var timeout = getTimeout();
        var expiredMissingSyncKeys = store.findExpiredMissingSyncKeys(timeout);
        if (expiredMissingSyncKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No expired missing sync keys");
            // Reschedule for any remaining keys
            scheduleTimeoutCheck();
            return;
        }

        LOGGER.log(System.Logger.Level.ERROR, "Fatal sync error: timeout while waiting for missing sync key");
        for(var expiredMissingSyncKey : expiredMissingSyncKeys) {
            client.handleFailure(new WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices(expiredMissingSyncKey.keyId()));
        }
    }

    /**
     * Gets the timeout duration from AB props.
     */
    private Duration getTimeout() {
        var days = abPropsService.getInt(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS);
        return Duration.ofDays(days);
    }

    /**
     * Cancels any pending timeout check.
     */
    public synchronized void cancel() {
        if (scheduledCheck != null && !scheduledCheck.isDone()) {
            scheduledCheck.cancel(false);
        }
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        cancel();
        scheduler.shutdown();
    }
}
