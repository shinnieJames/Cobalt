package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.timestamp.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.exception.WhatsAppAdvCheckException;
import com.github.auties00.cobalt.exception.WhatsAppOwnDeviceListExpiredException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.AdvStoredTimestampExpiredEventBuilder;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Recurring job that keeps cached device lists fresh by detecting expiration and
 * triggering proactive resyncs.
 *
 * <p>The checker is started once per session by {@link DeviceService#startAdvCheckScheduler()}
 * and wakes up every 24 hours. On each tick it walks every cached device list,
 * classifies entries as expired (timestamp older than the
 * {@code num_days_key_index_list_expiration} AB prop) or close to expiration
 * (within the {@code num_days_before_device_expiry_check} warning window), tears
 * down Signal sessions for expired companion devices, rotates group sender keys
 * for the affected users, queues a proactive USync for every classified user, and
 * logs the local user out when its own device list expired and the
 * {@code web_adv_logout_on_self_device_list_expired} AB prop is on.
 *
 * @apiNote
 * Embedders that drive Cobalt's full multi-device lifecycle leave the scheduler
 * on via {@link DeviceService#startAdvCheckScheduler()}; embedders that only use
 * Cobalt for short-lived single-message sessions can leave it off and accept
 * stale device lists.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvDeviceInfoCheckJob")
public final class DeviceADVChecker implements AutoCloseable {
    /**
     * Logger for ADV check diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceADVChecker.class.getName());

    /**
     * Recurrence interval for the ADV check.
     *
     * @apiNote
     * Pins the WA Web cadence of one tick per 24 hours; not configurable.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);

    /**
     * The WhatsApp client used for store access and failure reporting.
     */
    private final WhatsAppClient client;

    /**
     * The device service consulted for the last-check time and for queueing
     * pending syncs.
     */
    private final DeviceService deviceService;

    /**
     * The AB props service used to read expiration thresholds.
     */
    private final ABPropsService abPropsService;

    /**
     * The WAM telemetry service used to commit ADV check events.
     */
    private final WamService wamService;

    /**
     * The scheduled executor running the periodic check, or {@code null} when
     * the scheduler is stopped.
     */
    private volatile ScheduledExecutorService scheduler;

    /**
     * Constructs a new ADV check scheduler.
     *
     * @apiNote
     * Wired up by the device-service construction graph; embedders do not
     * usually call this directly. The constructor only captures collaborators
     * and does not schedule anything until {@link #start()} is invoked.
     *
     * @param client         the WhatsApp client
     * @param deviceService  the device service
     * @param abPropsService the AB props service
     * @param wamService     the WAM telemetry service
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceADVChecker(WhatsAppClient client, DeviceService deviceService, ABPropsService abPropsService, WamService wamService) {
        this.client = client;
        this.deviceService = deviceService;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
    }

    /**
     * Starts the ADV check scheduler.
     *
     * @apiNote
     * Called once after the socket is online to begin the 24-hour cadence.
     * Idempotent: a second call while the scheduler is already running is a
     * no-op. The first tick fires immediately when no previous check time is
     * stored, but only records the timestamp without running the actual job
     * (mirroring WA Web's first-run no-op {@code Promise.resolve()}).
     *
     * @implNote
     * This implementation runs the scheduler on a single virtual-thread executor
     * named {@code adv-check-N}. {@link #computeInitialDelay()} subtracts the
     * elapsed time since the last check so a session that reconnects within the
     * 24-hour window does not get a fresh full delay.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void start() {
        if (scheduler == null || scheduler.isShutdown()) {
            synchronized (this) {
                if (scheduler == null || scheduler.isShutdown()) {
                    var factory = Thread.ofVirtual()
                            .name("adv-check-", 0)
                            .factory();
                    scheduler = Executors.newSingleThreadScheduledExecutor(factory);

                    var initialDelay = computeInitialDelay();
                    LOGGER.log(Level.DEBUG, "ADV check scheduler starting with initial delay of {0} seconds",
                            initialDelay.toSeconds());

                    scheduler.scheduleWithFixedDelay(this::performCheck,
                            initialDelay.toSeconds(), CHECK_INTERVAL.toSeconds(), TimeUnit.SECONDS);
                }
            }
        }
    }

    /**
     * Computes the initial delay before the first scheduled tick.
     *
     * @apiNote
     * Called from {@link #start()} to honour any check that already ran during
     * a previous session: a session reconnecting after twelve hours waits the
     * remaining twelve, not a full twenty-four.
     *
     * @return the computed initial delay; zero when no previous check exists or
     *         when the previous check is at least {@link #CHECK_INTERVAL} old
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Duration computeInitialDelay() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            return Duration.ZERO;
        }

        var now = Instant.now();
        var timeSinceLastCheck = Duration.between(lastCheck.get(), now);

        if (timeSinceLastCheck.compareTo(CHECK_INTERVAL) >= 0) {
            return Duration.ZERO;
        }

        return CHECK_INTERVAL.minus(timeSinceLastCheck);
    }

    /**
     * Performs one ADV device info check tick.
     *
     * @apiNote
     * The body of every scheduled tick. On the first ever invocation (no
     * previous check time) records the timestamp and returns immediately,
     * mirroring WA Web's no-op first run. Subsequent ticks read the two
     * expiration AB props, classify cached lists via
     * {@link #analyzeDeviceLists}, emit one
     * {@link com.github.auties00.cobalt.wam.event.AdvStoredTimestampExpiredEvent}
     * per expired list, log the local user out when self-expired and the AB prop
     * agrees, clear expired records, and queue proactive USyncs for the rest.
     *
     * @implNote
     * This implementation surfaces unexpected exceptions through
     * {@link WhatsAppClient#handleFailure} as a
     * {@link WhatsAppAdvCheckException} so the error pipeline can route them
     * the same way as any other ADV failure. The WAM events fire before the
     * self-expired logout so telemetry is not lost on the logout path.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void performCheck() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            deviceService.updateAdvCheckTime();
            return;
        }

        try {
            var expiryDays = abPropsService.getInt(ABProp.NUM_DAYS_KEY_INDEX_LIST_EXPIRATION);
            var warningDays = abPropsService.getInt(ABProp.NUM_DAYS_BEFORE_DEVICE_EXPIRY_CHECK);
            var expiryThreshold = Duration.ofDays(expiryDays);
            var warningThreshold = Duration.ofDays(expiryDays - warningDays);

            var now = Instant.now();
            var myUserJid = client.store().jid()
                    .map(Jid::toUserJid)
                    .orElse(null);

            var result = analyzeDeviceLists(
                    client.store().deviceLists(),
                    now,
                    expiryThreshold,
                    warningThreshold,
                    lastCheck.get(),
                    myUserJid
            );

            sendAdvStoredTimestampExpiredEvents(result.expiredLists(), now, expiryThreshold);

            if (result.selfExpired() && shouldLogoutOnSelfExpired()) {
                LOGGER.log(Level.WARNING, "Own device list expired, triggering logout");
                client.handleFailure(new WhatsAppOwnDeviceListExpiredException());
                return;
            }

            for (var expiredList : result.expiredLists()) {
                clearExpiredDeviceRecord(expiredList);
            }

            if (!result.jidsNeedingSync().isEmpty()) {
                scheduleProactiveSync(result.jidsNeedingSync());
            }
        } catch (Exception e) {
            client.handleFailure(new WhatsAppAdvCheckException(e));
        } finally {
            deviceService.updateAdvCheckTime();
        }
    }

    /**
     * Classifies cached device lists as expired or close to expiration.
     *
     * @apiNote
     * Extracted as a package-private method so unit tests can pass a synthetic
     * {@code now} and {@code lastCheck} without driving the real scheduler.
     * Skips deleted lists and primary-only lists (no companion to chase). For
     * each remaining list, consults
     * {@link DeviceExpectedTsUtils#isDeviceListStale} for expiration and
     * {@link DeviceExpectedTsUtils#isDeviceListCloseToExpiration} for the
     * warning window; expired lists are added to the result and also to the
     * sync queue; close-to-expiration lists are only queued.
     *
     * @param deviceLists      the device lists to analyze
     * @param now              the reference instant for "now"
     * @param expiryThreshold  the full-expiration threshold (from the
     *                         {@code num_days_key_index_list_expiration} AB
     *                         prop)
     * @param warningThreshold the close-to-expiration warning window
     * @param lastCheck        the time the last ADV check ran
     * @param myUserJid        the local user's JID, or {@code null} when no
     *                         self JID is set
     * @return the analysis result
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    AnalysisResult analyzeDeviceLists(
            Iterable<DeviceList> deviceLists,
            Instant now,
            Duration expiryThreshold,
            Duration warningThreshold,
            Instant lastCheck,
            Jid myUserJid
    ) {
        var selfExpired = false;
        var expiredLists = new ArrayList<DeviceList>();
        var jidsNeedingSync = new ArrayList<Jid>();

        for (var deviceList : deviceLists) {
            if (deviceList.deleted() || isPrimaryOnly(deviceList)) {
                continue;
            }

            if (DeviceExpectedTsUtils.isDeviceListStale(deviceList, now, expiryThreshold, lastCheck)) {
                expiredLists.add(deviceList);
                if (deviceList.userJid().equals(myUserJid)) {
                    selfExpired = true;
                }
                jidsNeedingSync.add(deviceList.userJid());
            } else if (DeviceExpectedTsUtils.isDeviceListCloseToExpiration(deviceList, now, warningThreshold)) {
                jidsNeedingSync.add(deviceList.userJid());
            }
        }

        return new AnalysisResult(selfExpired, expiredLists, jidsNeedingSync);
    }

    /**
     * Returns whether a device list contains only the primary device.
     *
     * @apiNote
     * Mirrors the WA Web {@code v(n)} predicate that exempts primary-only
     * users from the staleness check: there is no companion device to refresh
     * for them.
     *
     * @param deviceList the device list to check
     * @return {@code true} when the list has exactly one device and that
     *         device is primary
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isPrimaryOnly(DeviceList deviceList) {
        var devices = deviceList.devices();
        return devices.size() == 1 && devices.getFirst().isPrimary();
    }

    /**
     * Returns whether the local user should be logged out when its own device
     * list has expired.
     *
     * @apiNote
     * Consulted by {@link #performCheck()} immediately after the
     * {@code selfExpired} flag is set; the AB prop
     * {@code web_adv_logout_on_self_device_list_expired} acts as a server-side
     * kill switch.
     *
     * @return {@code true} when the AB prop is on
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldLogoutOnSelfExpired() {
        return abPropsService.getBool(ABProp.WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED);
    }

    /**
     * Clears an expired device record by tearing down Signal sessions for every
     * companion device, rotating group sender keys, and marking the list as
     * deleted.
     *
     * @apiNote
     * Mirrors WA Web's {@code clearDeviceRecord} side-effect bundle invoked from
     * {@code removeCompanions}. The {@code deletedChangedToHost} marker stays
     * unset on this path because the call site does not carry account-type
     * arguments; it only fires on the coexistence transition path.
     *
     * @param deviceList the expired device list to clear
     */
    private void clearExpiredDeviceRecord(DeviceList deviceList) {
        var userJid = deviceList.userJid();

        deviceList.devices()
                .stream()
                .filter(device -> !device.isPrimary())
                .map(device -> device.toDeviceJid(userJid.user(), userJid.server()))
                .forEach(client.store()::cleanupSignalSessions);

        client.store().markKeyRotation(userJid);

        var deletedList = new DeviceListBuilder()
                .userJid(userJid)
                .deleted(true)
                .build();
        client.store().addDeviceList(deletedList);

        LOGGER.log(Level.DEBUG, "Marked device list as deleted for {0}, cleaned up {1} companion devices",
                userJid, deviceList.devices().size() - 1);
    }

    /**
     * Emits one {@code AdvStoredTimestampExpired} WAM event per expired device
     * list, reporting overshoot in hours.
     *
     * @apiNote
     * Drives the {@code advExpireTimeInHours} server-side dashboard. For every
     * expired list the overshoot is computed as
     * {@code now - (timestamp + expiryThreshold)}; lists whose overshoot is
     * negative are skipped (not yet past the cutoff), matching the WA Web inline
     * {@code if (!(r < 0))} guard.
     *
     * @implNote
     * This implementation rounds overshoot seconds to the nearest hour via
     * {@link Math#round(double)} and forwards via {@link WamService#commit} so
     * the broader WAM batching policy decides when the events ship.
     *
     * @param expiredLists    the device lists classified as expired
     * @param now             the reference instant used to compute overshoot
     * @param expiryThreshold the configured key-index-list expiration threshold
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void sendAdvStoredTimestampExpiredEvents(List<DeviceList> expiredLists, Instant now, Duration expiryThreshold) {
        if (expiredLists.isEmpty()) {
            return;
        }
        for (var expiredList : expiredLists) {
            var overshoot = Duration.between(expiredList.timestamp().plus(expiryThreshold), now);
            if (overshoot.isNegative()) {
                continue;
            }
            var hours = Math.toIntExact(Math.round(overshoot.toSeconds() / 3600.0));
            wamService.commit(new AdvStoredTimestampExpiredEventBuilder()
                    .advExpireTimeInHours(hours)
                    .build());
        }
    }

    /**
     * Queues a proactive USync for the given user JIDs through the pending
     * device sync pipeline.
     *
     * @apiNote
     * Called at the end of every tick with the union of expired and
     * close-to-expiration user JIDs; the pipeline coalesces and flushes them on
     * the next eligible event-loop window.
     *
     * @param jids the JIDs to enqueue
     */
    private void scheduleProactiveSync(List<Jid> jids) {
        LOGGER.log(Level.DEBUG, "Proactively syncing {0} device lists", jids.size());
        var pendingSync = PendingDeviceSync.of(jids, "adv_expiration");
        client.store().addPendingDeviceSync(pendingSync);
        deviceService.retryPendingSyncs();
    }

    /**
     * Stops the ADV check scheduler and cancels pending checks.
     *
     * @apiNote
     * Called on client teardown and idempotent: a second call once the
     * scheduler is already shut down is a no-op. Never call from inside a
     * scheduler tick; the executor it shuts down is the one running the tick.
     */
    @Override
    public void close() {
        if (scheduler != null && !scheduler.isShutdown()) {
            synchronized (this) {
                if (scheduler != null) {
                    scheduler.shutdownNow();
                    scheduler = null;
                }
            }
        }
    }

    /**
     * Output of {@link #analyzeDeviceLists} bundling the self-expiration flag,
     * the expired lists, and the union of user JIDs needing a proactive sync.
     *
     * @apiNote
     * Package-private container that lets the staleness analysis stay pure
     * (input in, output out) so unit tests can assert against the classification
     * without driving the scheduler's side effects.
     *
     * @param selfExpired     {@code true} when the local user's own device list
     *                        is expired
     * @param expiredLists    the device lists classified as expired
     * @param jidsNeedingSync the JIDs needing proactive device sync (expired
     *                        plus close-to-expiration)
     */
    record AnalysisResult(
            boolean selfExpired,
            List<DeviceList> expiredLists,
            List<Jid> jidsNeedingSync
    ) {

    }
}
