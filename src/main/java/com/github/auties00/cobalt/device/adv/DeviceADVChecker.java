package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.timestamp.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.exception.WhatsAppAdvCheckException;
import com.github.auties00.cobalt.exception.WhatsAppOwnDeviceListExpiredException;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic ADV device info checks to prevent device list expiration.
 *
 * <p>Runs every 24 hours to check device list timestamps and expectedTs-based staleness.
 * On each run, device lists that have expired are cleaned up (Signal sessions removed,
 * group sender keys rotated, record marked deleted), and users approaching expiration
 * are queued for proactive device sync.
 *
 * @apiNote WAWebAdvDeviceInfoCheckJob: manages automated periodic verification and
 * expiration of device information lists for Advanced Device Verification.
 * @implNote WAWebAdvDeviceInfoCheckJob: the WA Web module uses a recursive
 * {@code setTimeout} pattern with an {@code AdvToSystemBridgeImpl} bridge class;
 * Cobalt inlines the bridge logic and uses {@code ScheduledExecutorService} with
 * virtual threads instead.
 */
public final class DeviceADVChecker implements AutoCloseable {
    /**
     * Logger for ADV device info check operations.
     *
     * @implNote NO_WA_BASIS: Java logging infrastructure.
     */
    private static final System.Logger LOGGER = System.getLogger(DeviceADVChecker.class.getName());

    /**
     * Interval between ADV checks.
     *
     * <p>Corresponds to the 24-hour recurring schedule in WA Web's recursive
     * {@code setTimeout} pattern, where each callback re-invokes
     * {@code scheduleAdvDeviceInfoCheck} after recording the current timestamp.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck:
     * WA Web uses {@code DAY_SECONDS} as the base interval for recursive
     * scheduling; Cobalt uses {@code scheduleWithFixedDelay} with 24 hours.
     */
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);

    /**
     * The WhatsApp client for accessing the store and error handling.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob: WA Web accesses stores and
     * services via module-level imports; Cobalt uses constructor-injected client.
     */
    private final WhatsAppClient client;

    /**
     * The device service for ADV check time tracking and pending sync operations.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob: WA Web uses
     * {@code WAWebLastADVCheckTimeApi} and {@code WAWebApiPendingDeviceSync}
     * as separate modules; Cobalt consolidates into {@link DeviceService}.
     */
    private final DeviceService deviceService;

    /**
     * The AB props service for reading expiration thresholds.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl:
     * WA Web uses {@code WAWebABProps.getABPropConfigValue}; Cobalt uses
     * constructor-injected {@link ABPropsService}.
     */
    private final ABPropsService abPropsService;

    /**
     * The scheduled executor for periodic ADV checks, or {@code null} if not started.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck:
     * WA Web uses a module-level timeout variable {@code g} with recursive
     * {@code setTimeout}; Cobalt uses a {@link ScheduledExecutorService}.
     */
    private volatile ScheduledExecutorService scheduler;

    /**
     * Creates a new ADV check scheduler.
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob: WA Web creates a singleton
     * runner lazily in {@code runAdvDeviceInfoCheck} using {@code new p(new _)};
     * Cobalt receives dependencies via constructor injection.
     * @param client        the WhatsApp client
     * @param deviceService the device service for sync operations
     * @param abPropsService the AB props service for thresholds
     */
    public DeviceADVChecker(WhatsAppClient client, DeviceService deviceService, ABPropsService abPropsService) {
        this.client = client;
        this.deviceService = deviceService;
        this.abPropsService = abPropsService;
    }

    /**
     * Starts the ADV check scheduler.
     *
     * <p>Computes the initial delay based on the time elapsed since the last check
     * to avoid waiting a full 24 hours if a check was performed recently. If no
     * previous check exists, runs immediately but only records the timestamp
     * without performing the actual device info check.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: clears any
     * existing timeout, computes initial delay as
     * {@code max(DAY_SECONDS - (now - lastCheck), 0)}, and schedules the callback
     * which runs the check, records the timestamp, and recursively reschedules.
     */
    public void start() {
        if (scheduler == null || scheduler.isShutdown()) {
            synchronized (this) {
                if (scheduler == null || scheduler.isShutdown()) {
                    var factory = Thread.ofVirtual()
                            .name("adv-check-", 0)
                            .factory();
                    scheduler = Executors.newSingleThreadScheduledExecutor(factory);

                    // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: compute initial delay
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
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck:
     * {@code t != null ? Math.max(DAY_SECONDS - (e - t), 0) : 0} where
     * {@code e} is current time and {@code t} is last check time. When
     * {@code t == null}, delay is 0 and action is a no-op.
     * @return the computed initial delay
     */
    private Duration computeInitialDelay() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: no previous check - run immediately
            // The actual job will be skipped in performCheck() when lastCheck is empty
            return Duration.ZERO;
        }

        var now = Instant.now();
        var timeSinceLastCheck = Duration.between(lastCheck.get(), now);

        // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: Math.max(DAY_SECONDS-(e-t), 0)
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
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.runAdvDeviceInfoCheck: creates a
     * singleton {@code p} runner with an {@code AdvToSystemBridgeImpl} bridge
     * and calls {@code run(unixTimeWithoutClockSkewCorrection())}. The runner
     * calls {@code getUsersForExpiration}, {@code removeCompanions},
     * {@code sendADVStoredTimestampExpiredEvents} (WAM, skipped),
     * and {@code sendOrQueueDeviceUsyncQuery}.
     */
    private void performCheck() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: first run just records timestamp
            // In WA Web, when lastCheck is null the action is a no-op (Promise.resolve()),
            // and the timestamp is recorded after the action completes.
            deviceService.updateAdvCheckTime();
            return;
        }

        try {
            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
            // get expiry thresholds from AB props
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

            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.removeCompanions:
            // if own device list expired AND AB prop is true, trigger logout and
            // return resolved Promise (skip clearDeviceRecord for ALL entries)
            if (result.selfExpired() && shouldLogoutOnSelfExpired()) {
                LOGGER.log(Level.WARNING, "Own device list expired, triggering logout");
                client.handleFailure(new WhatsAppOwnDeviceListExpiredException());
                return;
            }

            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.removeCompanions:
            // when not logging out, calls clearDeviceRecord(userJid, devices) for
            // each expired entry via Promise.all
            for (var expiredList : result.expiredLists()) {
                clearExpiredDeviceRecord(expiredList);
            }

            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.sendOrQueueDeviceUsyncQuery:
            // adds all users (expired + close-to-expiration) to pending device sync
            // and triggers doPendingDeviceSync. canRemoveUserDevices always returns
            // false so no filtering occurs.
            if (!result.jidsNeedingSync().isEmpty()) {
                scheduleProactiveSync(result.jidsNeedingSync());
            }
        } catch (Exception e) {
            // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: try{yield a()}catch(e){ERROR(...)}
            client.handleFailure(new WhatsAppAdvCheckException(e));
        } finally {
            // WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: timestamp recording
            // runs after the try/catch in WA Web (not in a finally), but Cobalt uses
            // finally for robustness. Semantically equivalent.
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
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
     * iterates all device lists, skipping deleted and primary-only entries
     * (function {@code v}), then classifies using functions {@code S} (expired)
     * and {@code R} (close to expiration). Returns
     * {@code {usersExpired, usersCloseToExpiration}} maps.
     * @param deviceLists      the device lists to analyze
     * @param now              the current time
     * @param expiryThreshold  the threshold for full expiration
     * @param warningThreshold the threshold for close-to-expiration warning
     * @param lastCheck        the last ADV device info check time
     * @param myUserJid        the current user's JID, or {@code null}
     * @return the analysis result containing expired lists and JIDs needing sync
     */
    private AnalysisResult analyzeDeviceLists(
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
            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
            // n.deleted || v(n) -> skip
            if (deviceList.deleted() || isPrimaryOnly(deviceList)) {
                continue;
            }

            // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
            // S(e, a, n, r) -> expired; R(e, warningThreshold, n) -> close to expiration
            if (DeviceExpectedTsUtils.isDeviceListStale(deviceList, now, expiryThreshold, lastCheck)) {
                expiredLists.add(deviceList);
                if (deviceList.userJid().equals(myUserJid)) {
                    selfExpired = true;
                }
                // WAWebAdvDeviceInfoCheckJob: expired users are added to the sync list
                // alongside close-to-expiration users
                jidsNeedingSync.add(deviceList.userJid());
            } else if (DeviceExpectedTsUtils.isDeviceListCloseToExpiration(deviceList, now, warningThreshold)) {
                // WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
                // R(e, a - warningDays*DAY_SECONDS, n) -> add to usersCloseToExpiration
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
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.v:
     * {@code e.devices.length === 1 && e.devices[0].id === DEFAULT_DEVICE_ID}
     * @param deviceList the device list to check
     * @return {@code true} if the list contains only the primary device
     */
    private boolean isPrimaryOnly(DeviceList deviceList) {
        var devices = deviceList.devices();
        return devices.size() == 1 && devices.getFirst().isPrimary();
    }

    /**
     * Checks whether the client should log out when its own device list has expired.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.removeCompanions:
     * checks {@code WAWebABProps.getABPropConfigValue("web_adv_logout_on_self_device_list_expired")}
     * @return {@code true} if the AB prop indicates logout on self device list expiration
     */
    private boolean shouldLogoutOnSelfExpired() {
        return abPropsService.getBool(ABProp.WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED);
    }

    /**
     * Clears an expired device record by removing Signal sessions for all
     * companion devices, rotating group sender keys, and marking the device
     * list as deleted.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.removeCompanions:
     * calls {@code WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord(wid, devices)}
     * with only the user JID and devices array (no account type parameters),
     * so {@code deletedChangedToHost} is never set in this code path.
     * @param deviceList the expired device list to clear
     */
    private void clearExpiredDeviceRecord(DeviceList deviceList) {
        var userJid = deviceList.userJid();

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clean up Signal sessions
        // for all non-primary devices: deleteRemoteInfo + deleteDeviceSenderKey
        deviceList.devices()
                .stream()
                .filter(device -> !device.isPrimary())
                .map(device -> device.toDeviceJid(userJid.user(), userJid.server()))
                .forEach(client.store()::cleanupSignalSessions);

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord ->
        // WAWebAdvUpdateParticipantApi.updateGroupParticipantsInTransaction:
        // when devices are removed, mark sender keys for rotation in all groups
        // containing this user
        client.store().markKeyRotation(userJid);

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: create {id:..., deleted:true}
        // Note: removeCompanions calls clearDeviceRecord(wid, devices) without account
        // type parameters, so _(undefined, undefined) returns null and
        // deletedChangedToHost is never set in this path.
        var deletedList = new DeviceListBuilder()
                .userJid(userJid)
                .deleted(true)
                .build();
        client.store().addDeviceList(deletedList);

        LOGGER.log(Level.DEBUG, "Marked device list as deleted for {0}, cleaned up {1} companion devices",
                userJid, deviceList.devices().size() - 1);
    }

    /**
     * Schedules proactive sync for device lists that are expired or approaching
     * expiration.
     *
     * <p>Adds all specified JIDs to the pending device sync queue and triggers
     * the pending sync process.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.sendOrQueueDeviceUsyncQuery:
     * calls {@code WAWebApiPendingDeviceSync.addUserToPendingDeviceSync(jids.map(toString))}
     * followed by {@code WAWebApiPendingDeviceSync.doPendingDeviceSync()}.
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
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck:
     * WA Web clears the timeout via {@code clearTimeout(g); g = null}; Cobalt
     * shuts down the {@link ScheduledExecutorService}.
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
     *
     * @implNote ADAPTED: WAWebAdvDeviceInfoCheckJob.AdvToSystemBridgeImpl.getUsersForExpiration:
     * WA Web returns {@code {usersExpired: Map, usersCloseToExpiration: Map}} and
     * separately checks self-expiration in {@code removeCompanions}; Cobalt
     * consolidates into a single result record.
     * @param selfExpired    {@code true} if the current user's device list is expired
     * @param expiredLists   the list of expired device lists
     * @param jidsNeedingSync the JIDs needing proactive device sync (expired + close-to-expiration)
     */
    private record AnalysisResult(
            boolean selfExpired,
            List<DeviceList> expiredLists,
            List<Jid> jidsNeedingSync
    ) {

    }
}
