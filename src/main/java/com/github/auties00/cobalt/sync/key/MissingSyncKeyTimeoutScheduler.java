package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Duration;
import java.time.Instant;
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
    private static final System.Logger LOGGER = System.getLogger(MissingSyncKeyTimeoutScheduler.class.getName());

    private static final long RE_REQUEST_INTERVAL_HOURS = 24;

    private final WhatsAppClient client;
    private final WhatsAppStore store;
    private final ABPropsService abPropsService;
    private final MissingSyncKeyRequestService requestService;
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> scheduledCheck;
    private volatile ScheduledFuture<?> reRequestJob;

    /**
     * Creates a new timeout scheduler.
     *
     * @param client         the WhatsApp client
     * @param abPropsService the AB props service for timeout configuration
     * @param requestService the request service for periodic re-requests
     */
    public MissingSyncKeyTimeoutScheduler(WhatsAppClient client, ABPropsService abPropsService, MissingSyncKeyRequestService requestService) {
        this.client = client;
        this.store = client.store();
        this.abPropsService = abPropsService;
        this.requestService = requestService;
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
        var delay = store.missingSyncKeys()
                .stream()
                .map(MissingDeviceSyncKey::timestamp)
                .min(Instant::compareTo)
                .map(earliest -> {
                    var elapsed = Duration.between(earliest, Instant.now());
                    var remaining = timeout.minus(elapsed);
                    return remaining.isNegative() ? Duration.ZERO : remaining;
                });

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
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdStoreMissingKeys._timeoutWhileWaitingForMissingKey}:
     * re-verifies that keys are still missing before triggering a fatal error,
     * since they may have arrived between scheduling and firing. Triggers a
     * single global fatal error rather than per-key errors.
     */
    private void checkForExpiredKeys() {
        // Re-verify: keys may have been received since the timeout was scheduled
        var currentMissingKeys = store.missingSyncKeys();
        if (currentMissingKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync keys remain, timeout check skipped");
            return;
        }

        var timeout = getTimeout();
        var now = Instant.now();
        var expiredMissingSyncKeys = currentMissingKeys
                .stream()
                .filter(key -> Duration.between(key.timestamp(), now).compareTo(timeout) > 0)
                .toList();
        if (expiredMissingSyncKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No expired missing sync keys");
            // Reschedule for any remaining keys
            scheduleTimeoutCheck();
            return;
        }

        // Re-verify each expired key still hasn't been resolved
        var stillMissingKeyIds = expiredMissingSyncKeys.stream()
                .filter(key -> store.findMissingSyncKey(key.keyId()).isPresent())
                .map(MissingDeviceSyncKey::keyId)
                .toList();
        if (stillMissingKeyIds.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "All expired keys were resolved before timeout action");
            scheduleTimeoutCheck();
            return;
        }

        // Trigger a single global fatal error for all expired keys
        LOGGER.log(System.Logger.Level.ERROR, "Fatal sync error: timeout while waiting for {0} missing sync key(s)",
                stillMissingKeyIds.size());
        client.handleFailure(new WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices(stillMissingKeyIds.getFirst()));
    }

    /**
     * Gets the timeout duration from AB props.
     */
    private Duration getTimeout() {
        var days = abPropsService.getInt(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS);
        return Duration.ofDays(days);
    }

    /**
     * Starts a periodic job that re-requests all missing sync keys from
     * companion devices.
     *
     * <p>Per WhatsApp Web {@code requestAllSyncdMissingKeysJob}: periodically
     * re-sends key requests for all tracked missing keys to handle cases
     * where the original request was lost or a new companion device joined.
     */
    public synchronized void startPeriodicReRequestJob() {
        if (reRequestJob != null && !reRequestJob.isDone()) {
            return;
        }

        reRequestJob = scheduler.scheduleAtFixedRate(() -> {
            var missingKeys = store.missingSyncKeys();
            if (missingKeys.isEmpty()) {
                return;
            }

            var keyIds = missingKeys.stream()
                    .map(MissingDeviceSyncKey::keyId)
                    .toList();
            LOGGER.log(System.Logger.Level.INFO, "Periodic re-request for {0} missing sync key(s)", keyIds.size());
            Thread.startVirtualThread(() -> requestService.requestMissingKeys(keyIds));
        }, RE_REQUEST_INTERVAL_HOURS, RE_REQUEST_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Schedules a short-delay check before triggering fatal error when all
     * devices have responded without the key.
     *
     * <p>Per WhatsApp Web behavior, a 5-second grace period is added before
     * declaring a key as missing on all devices. This allows for late-arriving
     * key share responses that may resolve the missing key.
     */
    public synchronized void scheduleAllDevicesRespondedCheck() {
        if (scheduledCheck != null && !scheduledCheck.isDone()) {
            scheduledCheck.cancel(false);
        }

        LOGGER.log(System.Logger.Level.DEBUG, "Scheduling 5-second grace period before missing key fatal");
        scheduledCheck = scheduler.schedule(this::checkForExpiredKeys, 5, TimeUnit.SECONDS);
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
        if (reRequestJob != null && !reRequestJob.isDone()) {
            reRequestJob.cancel(false);
        }
        scheduler.shutdown();
    }
}
