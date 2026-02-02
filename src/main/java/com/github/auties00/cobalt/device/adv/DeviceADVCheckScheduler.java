package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.device.util.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for periodic ADV (Authenticated Device Verification) device info checks.
 * Refreshes device lists to ensure ADV data doesn't expire.
 * Uses configurable thresholds from AB props and expectedTs-based staleness detection.
 */
public final class DeviceADVCheckScheduler implements Closeable {
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);

    // Default thresholds (fallback if AB props not available)
    private static final int DEFAULT_EXPIRY_DAYS = 30;
    private static final int DEFAULT_WARNING_DAYS = 7;

    private final WhatsAppClient client;
    private final DeviceService deviceService;
    private final ABPropsService abPropsService;

    private CompletableFuture<Void> scheduledTask;
    private volatile boolean running = false;

    public DeviceADVCheckScheduler(WhatsAppClient client, DeviceService deviceService, ABPropsService abPropsService) {
        this.client = client;
        this.deviceService = deviceService;
        this.abPropsService = abPropsService;
    }

    /**
     * Starts the ADV check scheduler.
     * Schedules checks every 24 hours.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleNextCheck();
    }

    /**
     * Schedules the next ADV check after the configured interval.
     */
    private void scheduleNextCheck() {
        if (!running) {
            return;
        }

        scheduledTask = SchedulerUtils.scheduleDelayed(CHECK_INTERVAL, this::performCheck)
                .thenRun(this::scheduleNextCheck); // Reschedule after completion
    }

    /**
     * Performs the ADV device info check.
     * Checks all cached device lists for expiration and staleness.
     */
    private void performCheck() {
        try {
            var lastCheck = deviceService.lastAdvCheckTime();
            if (lastCheck == null) {
                // First check - just record the time
                deviceService.updateAdvCheckTime();
                return;
            }

            // Get configurable thresholds from AB props
            var expiryDays = abPropsService.getInt(ABProp.NUM_DAYS_KEY_INDEX_LIST_EXPIRATION_AB_PROP_CODE)
                    .orElse(DEFAULT_EXPIRY_DAYS);
            var warningDays = abPropsService.getInt(ABProp.NUM_DAYS_BEFORE_DEVICE_EXPIRY_CHECK_AB_PROP_CODE)
                    .orElse(DEFAULT_WARNING_DAYS);

            var expiryThresholdSeconds = TimeUnit.DAYS.toSeconds(expiryDays);
            var warningThresholdSeconds = expiryThresholdSeconds - TimeUnit.DAYS.toSeconds(warningDays);
            var currentTime = System.currentTimeMillis() / 1000; // Convert to seconds

            // Check all device lists
            var allDeviceLists = client.store().deviceLists();
            var hasExpired = false;
            var hasWarning = false;
            var selfExpired = false;

            // Feature 14: Collect JIDs needing sync (both expired and close-to-expiration)
            var jidsNeedingSync = new java.util.ArrayList<com.github.auties00.cobalt.model.jid.Jid>();

            // Get current user's JID for self-device check
            var myJid = client.store().jid().orElse(null);
            var myUserJid = myJid != null ? myJid.toUserJid() : null;

            for (var deviceList : allDeviceLists) {
                // Feature 12: Skip device lists that are deleted or have only primary device
                // Per WhatsApp Web: primary-only lists don't need ADV expiration checks
                if (deviceList.deleted()) {
                    continue;
                }
                if (isPrimaryOnlyDeviceList(deviceList)) {
                    continue;
                }

                // Check if device list is expired
                if (DeviceExpectedTsUtils.isDeviceListStale(deviceList, currentTime, expiryThresholdSeconds, lastCheck)) {
                    hasExpired = true;

                    // Feature 9: Check if this is the user's own device list
                    if (myUserJid != null && deviceList.userJid().equals(myUserJid)) {
                        selfExpired = true;
                    } else {
                        // Feature 14: Queue for proactive sync (don't sync self)
                        jidsNeedingSync.add(deviceList.userJid());
                    }

                    // Remove expired device list to force refresh on next access
                    client.store().removeDeviceList(deviceList.userJid());
                    continue;
                }

                // Check if device list is close to expiration
                if (DeviceExpectedTsUtils.isDeviceListCloseToExpiration(deviceList, currentTime, warningThresholdSeconds)) {
                    hasWarning = true;
                    // Feature 14: Queue close-to-expiration lists for proactive sync
                    jidsNeedingSync.add(deviceList.userJid());
                }
            }

            // Feature 9: Handle self-device expiration - trigger logout if enabled
            if (selfExpired) {
                var shouldLogout = abPropsService.getBool(ABProp.WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED_AB_PROP_CODE)
                        .orElse(false);
                if (shouldLogout) {
                    System.getLogger(getClass().getName())
                            .log(System.Logger.Level.WARNING, "Own device list expired, triggering logout");
                    // Trigger disconnection - this will require reconnection with fresh device list
                    client.disconnect();
                    return; // Don't continue with normal notification flow
                }
            }

            // Feature 14: Proactively sync expired and close-to-expiration device lists
            if (!jidsNeedingSync.isEmpty()) {
                System.getLogger(getClass().getName())
                        .log(System.Logger.Level.DEBUG, "Proactively syncing {0} device lists", jidsNeedingSync.size());
                // Add to pending device sync queue for async processing
                var pendingSync = new com.github.auties00.cobalt.device.model.PendingDeviceSync(
                        jidsNeedingSync,
                        "adv_expiration",
                        System.currentTimeMillis(),
                        0
                );
                client.store().addPendingDeviceSync(pendingSync);
                // Trigger sync in background
                deviceService.retryPendingSyncs();
            }

            // Update check time
            deviceService.updateAdvCheckTime();

            // Notify listeners if any lists expired or approaching expiration
            if (hasExpired || hasWarning) {
                for (var listener : client.store().listeners()) {
                    Thread.startVirtualThread(() ->
                            listener.onDeviceAdvRefreshed(client)
                    );
                }
            }
        } catch (Exception e) {
            System.getLogger(getClass().getName())
                    .log(System.Logger.Level.ERROR, "ADV check failed", e);
        }
    }

    /**
     * Checks if a device list contains only the primary device.
     * Per WhatsApp Web: primary-only device lists don't need ADV expiration checks.
     *
     * @param deviceList the device list to check
     * @return true if the list has exactly one device which is the primary device
     */
    private boolean isPrimaryOnlyDeviceList(com.github.auties00.cobalt.device.model.DeviceList deviceList) {
        var devices = deviceList.devices();
        return devices.size() == 1 && devices.getFirst().isPrimary();
    }

    /**
     * Stops the ADV check scheduler and cancels any pending checks.
     */
    @Override
    public void close() {
        running = false;
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
    }
}
