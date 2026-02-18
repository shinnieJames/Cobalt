package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.timestamp.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.exception.WhatsAppAdvCheckException;
import com.github.auties00.cobalt.exception.WhatsAppOwnDeviceListExpiredException;
import com.github.auties00.cobalt.model.auth.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.DeviceListBuilder;
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
 *
 * @apiNote WAWebAdvDeviceInfoCheckJob: manages automated periodic verification and
 * expiration of device information lists for Advanced Device Verification.
 */
public final class DeviceADVChecker implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DeviceADVChecker.class.getName());

    /**
     * Interval between ADV checks.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: uses 24-hour interval.
     */
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);


    private final WhatsAppClient client;
    private final DeviceService deviceService;
    private final ABPropsService abPropsService;
    private volatile ScheduledExecutorService scheduler;

    /**
     * Creates a new ADV check scheduler.
     *
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
     * @apiNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: computes initial delay
     * based on time since last check to avoid waiting a full 24 hours if a check was recent.
     */
    public void start() {
        if (scheduler == null || scheduler.isShutdown()) {
            synchronized (this) {
                if (scheduler == null || scheduler.isShutdown()) {
                    var factory = Thread.ofVirtual()
                            .name("adv-check-", 0)
                            .factory();
                    scheduler = Executors.newSingleThreadScheduledExecutor(factory);

                    // WAWebAdvDeviceInfoCheckJob: compute initial delay based on time since last check
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
     * @apiNote WAWebAdvDeviceInfoCheckJob.scheduleAdvDeviceInfoCheck: if no previous check exists,
     * runs immediately but skips the actual job (just records timestamp). If a check was performed
     * recently, waits only the remaining time until the next 24-hour interval.
     */
    private Duration computeInitialDelay() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // WAWebAdvDeviceInfoCheckJob: no previous check - run immediately to record timestamp
            // The actual job will be skipped in performCheck() when lastCheck is empty
            return Duration.ZERO;
        }

        var now = Instant.now();
        var timeSinceLastCheck = Duration.between(lastCheck.get(), now);

        // WAWebAdvDeviceInfoCheckJob: if more than 24 hours passed, run immediately
        if (timeSinceLastCheck.compareTo(CHECK_INTERVAL) >= 0) {
            return Duration.ZERO;
        }

        return CHECK_INTERVAL.minus(timeSinceLastCheck);
    }

    /**
     * Performs the ADV device info check.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob.runAdvDeviceInfoCheck: checks all cached device lists
     * for expiration and expectedTs-based staleness, triggers sync for approaching expiration.
     */
    private void performCheck() {
        var lastCheck = deviceService.lastAdvCheckTime();
        if (lastCheck.isEmpty()) {
            // WAWebAdvDeviceInfoCheckJob: first run just records timestamp
            deviceService.updateAdvCheckTime();
            return;
        }

        try {
            // WAWebAdvDeviceInfoCheckJob: get expiry thresholds from AB props
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

            // WAWebAdvDeviceInfoCheckJob.removeCompanions: if own device list expired AND AB prop is true,
            // trigger logout/reconnect and skip clearing records
            if (result.selfExpired() && shouldLogoutOnSelfExpired()) {
                LOGGER.log(Level.WARNING, "Own device list expired, triggering logout");
                client.handleFailure(new WhatsAppOwnDeviceListExpiredException());
                return;
            }

            // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clear records for all expired users
            // (including self if AB prop is false)
            for (var expiredList : result.expiredLists()) {
                clearExpiredDeviceRecord(expiredList);
            }

            // WAWebAdvDeviceInfoCheckJob: schedule proactive sync for approaching expiration
            if (!result.jidsNeedingSync().isEmpty()) {
                scheduleProactiveSync(result.jidsNeedingSync());
            }
        } catch (Exception e) {
            client.handleFailure(new WhatsAppAdvCheckException(e));
        } finally {
            // WAWebAdvDeviceInfoCheckJob: always record timestamp after job runs,
            // even on error, to prevent retry loops
            deviceService.updateAdvCheckTime();
        }
    }

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
            // WAWebAdvDeviceInfoCheckJob: skip deleted and primary-only lists
            if (deviceList.deleted() || isPrimaryOnly(deviceList)) {
                continue;
            }

            // WAWebAdvDeviceInfoCheckJob: check both timestamp expiry and expectedTs staleness
            if (DeviceExpectedTsUtils.isDeviceListStale(deviceList, now, expiryThreshold, lastCheck)) {
                expiredLists.add(deviceList);
                if (deviceList.userJid().equals(myUserJid)) {
                    selfExpired = true;
                }
                // WAWebAdvDeviceInfoCheckJob: add ALL expired users to sync queue
                // (if self is expired with AB prop true, early return prevents sync)
                jidsNeedingSync.add(deviceList.userJid());
            } else if (DeviceExpectedTsUtils.isDeviceListCloseToExpiration(deviceList, now, warningThreshold)) {
                // WAWebAdvDeviceInfoCheckJob: proactive sync for approaching expiration
                jidsNeedingSync.add(deviceList.userJid());
            }
        }

        return new AnalysisResult(selfExpired, expiredLists, jidsNeedingSync);
    }

    private boolean isPrimaryOnly(DeviceList deviceList) {
        var devices = deviceList.devices();
        return devices.size() == 1 && devices.getFirst().isPrimary();
    }

    private boolean shouldLogoutOnSelfExpired() {
        return abPropsService.getBool(ABProp.WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED);
    }

    /**
     * Clears an expired device record by marking it as deleted.
     *
     * @apiNote WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clears Signal sessions for
     * all non-primary devices, updates group participant sender keys, and marks the device list as deleted.
     */
    private void clearExpiredDeviceRecord(DeviceList deviceList) {
        var userJid = deviceList.userJid();

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: clean up Signal sessions for all non-primary devices
        deviceList.devices()
                .stream()
                .filter(device -> !device.isPrimary())
                .map(device -> device.toDeviceJid(userJid.user(), userJid.server()))
                .forEach(client.store()::cleanupSignalSessionsForDevice);

        // WAWebAdvUpdateParticipantApi.updateGroupParticipantsInTransaction: when devices are removed,
        // mark sender keys for rotation in all groups containing this user. This ensures removed devices
        // can't decrypt future group messages.
        client.store().markUserNeedsSenderKeyRotation(userJid);

        // WAWebIdentityUpdateDeviceTableApi.clearDeviceRecord: mark device list as deleted
        // For business coex: set deletedChangedToHost if the account type is HOSTED
        var isHostedAccount = deviceList.advAccountType() == ADVEncryptionType.HOSTED
                && isBizHostedDevicesEnabled();
        var deletedList = new DeviceListBuilder()
                .userJid(userJid)
                .deleted(true)
                .deletedChangedToHost(isHostedAccount)
                .build();
        client.store().addDeviceList(deletedList);

        LOGGER.log(Level.DEBUG, "Marked device list as deleted for {0}, cleaned up {1} companion devices",
                userJid, deviceList.devices().size() - 1);
    }

    /**
     * Checks if business hosted devices feature is enabled.
     *
     * @apiNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled: checks the adv_accept_hosted_devices AB prop.
     */
    private boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }

    /**
     * Schedules proactive sync for device lists approaching expiration.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob: triggers device sync to refresh before expiration.
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

    private record AnalysisResult(
            boolean selfExpired,
            List<DeviceList> expiredLists,
            List<Jid> jidsNeedingSync
    ) {

    }
}
