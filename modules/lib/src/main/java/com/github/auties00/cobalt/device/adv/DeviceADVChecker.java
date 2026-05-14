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
import com.github.auties00.cobalt.props.ABProp;
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
 * Schedules the recurring ADV (Account Device Verification) device info check that
 * keeps cached device lists fresh.
 *
 * <p>Once every 24 hours this checker walks all cached device lists and decides
 * which are expired (based on their timestamp, the expected-timestamp tracking
 * fields, and the age of the last check) and which are merely approaching
 * expiration. Expired records have their Signal sessions torn down, group sender
 * keys rotated, and the record itself marked deleted; both expired and
 * close-to-expiration records are queued for a proactive device sync so that
 * message sends made after them use an up-to-date view of the recipient's
 * companion devices. If the user's own device list expires and the corresponding
 * AB prop is on, the client logs out to avoid sending with a stale view of its
 * own devices.
 *
 * <p>Started by {@link DeviceService#startAdvCheckScheduler()} and stopped via
 * {@link DeviceService#stopAdvCheckScheduler()}.
 *
 * @apiNote WAWebAdvDeviceInfoCheckJob: manages automated periodic verification and
 * expiration of device information lists for Advanced Device Verification.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvDeviceInfoCheckJob")
public final class DeviceADVChecker implements AutoCloseable {
    /**
     * Logger for ADV check diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceADVChecker.class.getName());

    /**
     * Recurrence interval for the ADV check, mirroring WA Web's 24-hour cadence.
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
     * The scheduled executor running the periodic check, or {@code null} when the
     * scheduler is stopped.
     */
    private volatile ScheduledExecutorService scheduler;

    /**
     * Constructs a new ADV check scheduler.
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
     * <p>Computes the initial delay based on the time elapsed since the last check
     * to avoid waiting a full 24 hours if a check was performed recently. If no
     * previous check exists, runs immediately but only records the timestamp
     * without performing the actual device info check.
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
     * Computes the initial delay for the first ADV check.
     *
     * <p>If no previous check exists, returns zero so the scheduler runs
     * immediately (the actual job is skipped on first run, only recording the
     * timestamp). If a check was performed within the last 24 hours, returns
     * the remaining time until the next interval.
     * @return the computed initial delay
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "scheduleAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private Duration computeInitialDelay() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // No previous check, so run immediately. The actual job is skipped in
            // performCheck for the first run.
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
     * Performs the ADV device info check.
     *
     * <p>On the first invocation (when no previous check time exists), only records
     * the current timestamp without running the actual check. On subsequent
     * invocations, retrieves all cached device lists, classifies them as expired
     * or close-to-expiration, handles companion removal (including logout on own
     * device expiration), and queues proactive device syncs.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void performCheck() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // The first run only records the timestamp, mirroring WA Web's no-op
            // Promise.resolve() path.
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

            // The expired-timestamp WAM events fire before the self-expired logout so
            // telemetry is not lost on the logout path.
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
     * Analyzes all cached device lists and classifies them as expired or
     * close-to-expiration.
     *
     * <p>Skips deleted device lists and lists containing only the primary device.
     * For each remaining list, checks timestamp-based expiration via
     * {@link DeviceExpectedTsUtils#isDeviceListStale} and proximity-to-expiration
     * via {@link DeviceExpectedTsUtils#isDeviceListCloseToExpiration}.
     * @param deviceLists      the device lists to analyze
     * @param now              the current time
     * @param expiryThreshold  the threshold for full expiration
     * @param warningThreshold the threshold for close-to-expiration warning
     * @param lastCheck        the last ADV device info check time
     * @param myUserJid        the current user's JID, or {@code null}
     * @return the analysis result containing expired lists and JIDs needing sync
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
     * Checks whether a device list contains only the primary device.
     *
     * <p>Returns {@code true} when the list has exactly one device and that
     * device's ID equals the default (primary) device ID.
     * @param deviceList the device list to check
     * @return {@code true} if the list contains only the primary device
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean isPrimaryOnly(DeviceList deviceList) {
        var devices = deviceList.devices();
        return devices.size() == 1 && devices.getFirst().isPrimary();
    }

    /**
     * Checks whether the client should log out when its own device list has expired.
     * @return {@code true} if the AB prop indicates logout on self device list expiration
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private boolean shouldLogoutOnSelfExpired() {
        return abPropsService.getBool(ABProp.WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED);
    }

    /**
     * Clears an expired device record by removing Signal sessions for all
     * companion devices, rotating group sender keys, and marking the device
     * list as deleted.
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

        // removeCompanions calls clearDeviceRecord without account type parameters,
        // so deletedChangedToHost is never set in this path.
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
     * list reporting, in hours, how far past its expiration the stored
     * timestamp is.
     *
     * <p>For every expired device list the overshoot is computed as
     * {@code now - (timestamp + expiryThreshold)}. When non-negative the
     * overshoot is converted to hours (rounded to the nearest hour) and a
     * {@link com.github.auties00.cobalt.wam.event.AdvStoredTimestampExpiredEvent}
     * is committed through the client's {@code WamService}. Lists whose
     * overshoot is negative (not yet past the expiration cutoff) are skipped,
     * matching WA Web's inline {@code if (!(r < 0))} guard.
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
     * Schedules proactive sync for device lists that are expired or approaching
     * expiration.
     *
     * <p>Adds all specified JIDs to the pending device sync queue and triggers
     * the pending sync process.
     * @param jids the JIDs of users needing device sync
     */
    private void scheduleProactiveSync(List<Jid> jids) {
        LOGGER.log(Level.DEBUG, "Proactively syncing {0} device lists", jids.size());
        var pendingSync = PendingDeviceSync.of(jids, "adv_expiration");
        client.store().addPendingDeviceSync(pendingSync);
        deviceService.retryPendingSyncs();
    }

    /**
     * Stops the ADV check scheduler and cancels pending checks.
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
     * Result of analyzing cached device lists for expiration and staleness.
     * @param selfExpired    {@code true} if the current user's device list is expired
     * @param expiredLists   the list of expired device lists
     * @param jidsNeedingSync the JIDs needing proactive device sync (expired + close-to-expiration)
     */
    record AnalysisResult(
            boolean selfExpired,
            List<DeviceList> expiredLists,
            List<Jid> jidsNeedingSync
    ) {

    }
}
