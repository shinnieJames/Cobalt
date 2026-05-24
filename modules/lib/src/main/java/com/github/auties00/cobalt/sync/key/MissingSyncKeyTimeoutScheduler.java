package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the wall-clock-driven follow-ups that bound how long Cobalt waits for missing
 * sync keys before declaring the wait fatal.
 *
 * <p>Three concurrent tasks live here: the per-key wait-for-key timeout (driven by
 * {@code syncd_wait_for_key_timeout_days}, default seven days), a short five-second grace
 * period that fires when every asked device has responded without the key, and the periodic
 * six-hour re-broadcast job that re-issues the request to recover from lost peer messages.
 *
 * @apiNote
 * Owned by {@link com.github.auties00.cobalt.sync.WebAppStateService} and not intended for
 * direct embedder use. Cobalt clients that want to observe a fatal sync outcome subscribe
 * via the {@link WhatsAppClient}'s standard error handler; this scheduler raises
 * {@link WhatsAppWebAppStateSyncException.TimeoutWhileWaitingForMissingKey} or
 * {@link WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices} through that channel when
 * the corresponding deadline elapses.
 *
 * @implNote
 * This implementation runs every task on a single-threaded {@link ScheduledExecutorService}
 * with daemon threads so the executor neither serialises with the syncd virtual-thread
 * pipeline nor blocks JVM shutdown. Both the wait-for-key timeout and the
 * all-devices-responded grace check are arming-and-cancelling
 * {@link ScheduledFuture}s rather than persistent timers, mirroring WA Web's
 * {@code clearTimeout(S); S = setTimeout(...)} pattern in
 * {@code _setMissingKeyTimeout}.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestAllSyncdMissingKeysJob")
public final class MissingSyncKeyTimeoutScheduler {
    /**
     * Diagnostic logger for the missing-key timeout flow.
     */
    private static final System.Logger LOGGER = System.getLogger(MissingSyncKeyTimeoutScheduler.class.getName());

    /**
     * The interval, in hours, between two periodic re-broadcasts of the missing key
     * requests, matching WA Web's {@code WAWebTasksDefinitions} configuration of
     * {@code HOUR_SECONDS * 6}.
     */
    private static final long RE_REQUEST_INTERVAL_HOURS = 6;

    /**
     * The injected {@link WhatsAppClient} used to surface the fatal exceptions raised when a
     * missing-key deadline elapses.
     */
    private final WhatsAppClient client;

    /**
     * The shared {@link WhatsAppStore} consulted for the live missing-key tracker and for
     * the resolved app state sync key store (used to confirm that a key never arrived).
     */
    private final WhatsAppStore store;

    /**
     * The {@link ABPropsService} used to read the live
     * {@code syncd_wait_for_key_timeout_days} value.
     */
    private final ABPropsService abPropsService;

    /**
     * The companion {@link MissingSyncKeyRequestService} used by the periodic job to
     * re-broadcast requests for every tracked missing key.
     */
    private final MissingSyncKeyRequestService requestService;

    /**
     * The single-threaded {@link ScheduledExecutorService} that owns every timer in this
     * class.
     *
     * @implNote
     * This implementation uses a daemon-thread factory so the JVM is not held alive by a
     * lingering wait-for-key timer on shutdown.
     */
    private final ScheduledExecutorService scheduler;

    /**
     * The current armed wait-for-key timeout {@link ScheduledFuture}, or {@code null} when
     * none is armed.
     *
     * @implNote
     * This implementation uses a {@code volatile} reference so a concurrent
     * {@link #cancel()} or {@link #scheduleTimeoutCheck()} call observes the latest
     * write without holding the {@link #scheduler} monitor across the {@code schedule}
     * boundary.
     */
    private volatile ScheduledFuture<?> scheduledCheck;

    /**
     * The current armed five-second grace-period {@link ScheduledFuture}, independent of
     * {@link #scheduledCheck}.
     */
    private volatile ScheduledFuture<?> allDevicesCheck;

    /**
     * The handle of the periodic six-hour re-broadcast job, or {@code null} until
     * {@link #startPeriodicReRequestJob()} is called.
     */
    private volatile ScheduledFuture<?> reRequestJob;

    /**
     * Constructs a new {@code MissingSyncKeyTimeoutScheduler}.
     *
     * @apiNote
     * Invoked once per {@link com.github.auties00.cobalt.sync.WebAppStateService}; the
     * caller must subsequently invoke
     * {@link MissingSyncKeyRequestService#setTimeoutScheduler(MissingSyncKeyTimeoutScheduler)}
     * to close the cyclic-dependency loop. The scheduler does not arm any timer until
     * {@link #scheduleTimeoutCheck()} or {@link #startPeriodicReRequestJob()} is called.
     *
     * @param client the {@link WhatsAppClient} used to surface fatal sync exceptions
     * @param abPropsService the {@link ABPropsService} used to read the timeout configuration
     * @param requestService the {@link MissingSyncKeyRequestService} driven by the periodic job
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
     * Arms (or rearms) the wait-for-key timeout based on the timestamp of the oldest tracked
     * missing key.
     *
     * @apiNote
     * Called inline by
     * {@link MissingSyncKeyRequestService#trackMissingKeys(java.util.Collection, java.util.Set)}
     * whenever a new key is added to the tracker, and again from inside the periodic
     * re-request job. A no-op when the tracker is empty.
     *
     * @implNote
     * This implementation cancels the previously-armed future before scheduling a new one so
     * that successive calls converge on a single live timer (matching WA Web's
     * {@code clearTimeout(S); S = setTimeout(D, a)} preamble). Negative remaining durations
     * are clamped to zero so the deferred check fires on the next scheduler tick rather than
     * being silently swallowed by {@link ScheduledExecutorService#schedule}.
     */
    public synchronized void scheduleTimeoutCheck() {
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
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync keys, timeout check not scheduled");
            return;
        }

        var delayMs = delay.get().toMillis();
        LOGGER.log(System.Logger.Level.DEBUG, "Scheduling missing sync key timeout check in {0}ms", delayMs);

        scheduledCheck = scheduler.schedule(this::checkForExpiredKeys, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Re-checks every tracked missing key against the live sync key store and raises a fatal
     * sync exception when any of them have aged past
     * {@code syncd_wait_for_key_timeout_days}.
     *
     * @apiNote
     * The re-verification step is critical: a key may have arrived between the moment the
     * timer was armed and the moment it fires, in which case the tracker entry has already
     * been resolved by {@link SyncKeyRotationService#handleKeyShare(int, java.util.List)}
     * and no fatal is necessary.
     *
     * @implNote
     * This implementation, after finding zero actually-expired keys, defensively reschedules
     * itself rather than relying on the next external trigger; WA Web only reschedules when
     * a new key is added to the tracker, so a torn-down-and-restored Cobalt session that
     * still carries a live tracker entry would otherwise miss the next deadline.
     */
    private void checkForExpiredKeys() {
        var currentMissingKeys = store.missingSyncKeys();
        if (currentMissingKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync keys remain, timeout check skipped");
            return;
        }

        var actuallyMissing = currentMissingKeys.stream()
                .filter(key -> store.findWebAppStateKeyById(key.keyId()).isEmpty())
                .toList();
        if (actuallyMissing.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "All tracked missing keys have been received");
            return;
        }

        var timeout = getTimeout();
        var now = Instant.now();
        var expiredMissingSyncKeys = actuallyMissing
                .stream()
                .filter(key -> Duration.between(key.timestamp(), now).compareTo(timeout) > 0)
                .toList();
        if (expiredMissingSyncKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No expired missing sync keys");
            scheduleTimeoutCheck();
            return;
        }

        LOGGER.log(System.Logger.Level.ERROR, "Fatal sync error: timeout while waiting for {0} missing sync key(s)",
                expiredMissingSyncKeys.size());
        client.handleFailure(new WhatsAppWebAppStateSyncException.TimeoutWhileWaitingForMissingKey(
                expiredMissingSyncKeys.getFirst().keyId()));
    }

    /**
     * Raises a fatal {@link WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices} when at
     * least one tracked missing key has now received a negative response from every asked
     * device.
     *
     * @apiNote
     * Distinct from {@link #checkForExpiredKeys()}: that method is gated on the multi-day
     * wait-for-key timeout, whereas this one is gated only on the boolean
     * "every asked device has answered without the key" predicate, which is a much faster
     * unrecoverable signal.
     *
     * @implNote
     * This implementation is invoked from a five-second grace period
     * ({@link #scheduleAllDevicesRespondedCheck()}) so that a late-arriving positive
     * response can still race in and resolve the key. WA Web encodes the same delay as
     * {@code asyncSleep(5e3)} inline before its check.
     */
    private void checkForAllDevicesRespondedWithoutKey() {
        var currentMissingKeys = store.missingSyncKeys();
        if (currentMissingKeys.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync keys remain, all-devices-responded check skipped");
            return;
        }

        var missingOnAllDevices = currentMissingKeys.stream()
                .filter(key -> store.findMissingSyncKey(key.keyId()).isPresent())
                .filter(MissingDeviceSyncKey::isMissingOnAllDevices)
                .toList();
        if (missingOnAllDevices.isEmpty()) {
            LOGGER.log(System.Logger.Level.DEBUG, "No missing sync key has exhausted all device responses");
            scheduleTimeoutCheck();
            return;
        }

        LOGGER.log(System.Logger.Level.ERROR, "Fatal sync error: all asked devices responded without the requested sync key");
        client.handleFailure(new WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices(
                missingOnAllDevices.getFirst().keyId()
        ));
    }

    /**
     * Reads the current {@code syncd_wait_for_key_timeout_days} value as a {@link Duration}.
     *
     * @return the configured wait-for-key timeout
     */
    private Duration getTimeout() {
        var days = SyncKeyUtils.getSyncdWaitForKeyTimeoutDays(abPropsService);
        return Duration.ofDays(days);
    }

    /**
     * Starts the periodic six-hour re-broadcast job that re-issues key requests for every
     * tracked missing key.
     *
     * @apiNote
     * Idempotent: a second call while the job is still pending is a no-op. WA Web relies on
     * {@code WAWebTasksDefinitions} single-registration to avoid duplicate scheduling;
     * Cobalt has no equivalent task registry so the guard is enforced explicitly.
     *
     * @implNote
     * This implementation runs the actual {@link MissingSyncKeyRequestService#reRequestMissingKeys}
     * call on a freshly spawned virtual thread so the periodic timer does not block on the
     * peer-message fan-out, and follows it with a 20-second deferred reschedule of the
     * wait-for-key timeout, mirroring WA Web's
     * {@code self.setTimeout(setMissingKeyTimeoutInTransaction, 1e3*20)} sequel.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestAllSyncdMissingKeysJob",
            exports = "requestAllSyncdMissingKeysJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleMissingKeys",
            exports = "requestAllMissingKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public synchronized void startPeriodicReRequestJob() {
        if (reRequestJob != null && !reRequestJob.isDone()) {
            return;
        }

        reRequestJob = scheduler.scheduleAtFixedRate(() -> {
            var missingKeys = store.missingSyncKeys();
            var keyIds = missingKeys.stream()
                    .map(MissingDeviceSyncKey::keyId)
                    .toList();
            LOGGER.log(System.Logger.Level.INFO, "syncd: requestAllMissingKeys: missing keys: [{0}]",
                    missingKeys.stream().map(MissingDeviceSyncKey::keyId).map(SyncKeyUtils::syncKeyIdToHex).toList());
            if (keyIds.isEmpty()) {
                return;
            }
            Thread.startVirtualThread(() -> {
                requestService.reRequestMissingKeys(keyIds);
                scheduler.schedule(this::scheduleTimeoutCheck, 20, TimeUnit.SECONDS);
            });
        }, RE_REQUEST_INTERVAL_HOURS, RE_REQUEST_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    /**
     * Schedules the five-second grace period before
     * {@link #checkForAllDevicesRespondedWithoutKey()} fires.
     *
     * @apiNote
     * Called by {@link SyncKeyRotationService#handleKeyShare(int, java.util.List)} when a
     * negative response from a peer brings a tracked key into the
     * "missing on every asked device" state.
     *
     * @implNote
     * This implementation cancels any previously-armed grace period before scheduling a new
     * one so successive negative responses do not stack independent timers.
     */
    public synchronized void scheduleAllDevicesRespondedCheck() {
        if (allDevicesCheck != null && !allDevicesCheck.isDone()) {
            allDevicesCheck.cancel(false);
        }

        LOGGER.log(System.Logger.Level.DEBUG, "Scheduling 5-second grace period before missing key fatal");
        allDevicesCheck = scheduler.schedule(this::checkForAllDevicesRespondedWithoutKey, 5, TimeUnit.SECONDS);
    }

    /**
     * Cancels the currently-armed wait-for-key timeout, if any.
     *
     * @apiNote
     * Used by callers that want to disarm the timer without immediately rearming it; the
     * normal reschedule path goes through {@link #scheduleTimeoutCheck()}.
     */
    public synchronized void cancel() {
        if (scheduledCheck != null && !scheduledCheck.isDone()) {
            scheduledCheck.cancel(false);
        }
    }

    /**
     * Cancels every armed timer and shuts down the underlying executor.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.sync.WebAppStateService} on disconnect.
     * After {@code shutdown()} returns, no further {@link #scheduleTimeoutCheck},
     * {@link #scheduleAllDevicesRespondedCheck}, or {@link #startPeriodicReRequestJob} call
     * should be issued; the executor will refuse the submission with a
     * {@link java.util.concurrent.RejectedExecutionException}.
     */
    public void shutdown() {
        cancel();
        if (allDevicesCheck != null && !allDevicesCheck.isDone()) {
            allDevicesCheck.cancel(false);
        }
        if (reRequestJob != null && !reRequestJob.isDone()) {
            reRequestJob.cancel(false);
        }
        scheduler.shutdown();
    }
}
