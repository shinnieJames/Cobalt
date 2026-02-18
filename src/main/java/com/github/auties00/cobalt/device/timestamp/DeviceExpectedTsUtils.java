package com.github.auties00.cobalt.device.timestamp;

import com.github.auties00.cobalt.model.device.info.DeviceList;

import java.time.Duration;
import java.time.Instant;

/**
 * Utilities for managing expected timestamp logic in device lists.
 *
 * <p>The expected timestamp system detects stale device lists that have not been
 * refreshed recently, even when the device hash (dhash) matches.
 *
 * @apiNote WAWebAdvExpectedTsApi: provides the original implementation for
 * expectedTs, expectedTsLastDeviceJobTs, and expectedTsUpdateTs tracking.
 */
public final class DeviceExpectedTsUtils {

    /**
     * Threshold for expected timestamp staleness checks in the ADV scheduler.
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob: uses 25 hours as the threshold for
     * determining whether expectedTsUpdateTs indicates a stale device list.
     */
    private static final Duration EXPECTED_TIMESTAMP_UPDATE_THRESHOLD = Duration.ofHours(25);

    private DeviceExpectedTsUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Determines whether the expected timestamp should be cleared for a device list.
     *
     * @param incomingTimestamp         the timestamp from server response
     * @param incomingExpectedTimestamp the expected timestamp from server response, or {@code null}
     * @param cachedList                the cached device list, or {@code null}
     * @param lastADVCheckTime          the last ADV device check time, or {@code null}
     * @return {@code true} if expected timestamp should be cleared
     *
     * @apiNote WAWebAdvExpectedTsApi.shouldClearExpectedTs: clears expectedTs when the
     * server timestamp has caught up, or when a complex staleness condition is met.
     */
    public static boolean shouldClearExpectedTimestamp(
            Instant incomingTimestamp,
            Instant incomingExpectedTimestamp,
            DeviceList cachedList,
            Instant lastADVCheckTime
    ) {
        if (cachedList == null || cachedList.deleted() || cachedList.expectedTimestamp() == null) {
            return false;
        }

        var cachedExpectedTimestamp = cachedList.expectedTimestamp();

        // WAWebAdvExpectedTsApi.shouldClearExpectedTs condition 1:
        // Clear when server timestamp >= cached expectedTs (server has caught up)
        if (!incomingTimestamp.isBefore(cachedExpectedTimestamp)) {
            return true;
        }

        // WAWebAdvExpectedTsApi.shouldClearExpectedTs condition 2:
        // Clear when incoming expectedTs matches cached AND the last ADV job check
        // occurred after the cached job timestamp (staleness detected across job runs)
        if (incomingExpectedTimestamp == null || !incomingExpectedTimestamp.equals(cachedExpectedTimestamp) || lastADVCheckTime == null) {
            return false;
        }

        var cachedLastJobTimestamp = cachedList.expectedTimestampLastDeviceJobTimestamp();
        return cachedLastJobTimestamp == null || lastADVCheckTime.isAfter(cachedLastJobTimestamp);
    }

    /**
     * Determines if expected timestamp has changed between two values.
     *
     * @param oldExpectedTimestamp the old expected timestamp, or {@code null}
     * @param newExpectedTimestamp the new expected timestamp, or {@code null}
     * @return {@code true} if the timestamps differ
     */
    public static boolean hasExpectedTimestampChanged(Instant oldExpectedTimestamp, Instant newExpectedTimestamp) {
        if (oldExpectedTimestamp == null && newExpectedTimestamp == null) {
            return false;
        }
        if (oldExpectedTimestamp == null || newExpectedTimestamp == null) {
            return true;
        }
        return !oldExpectedTimestamp.equals(newExpectedTimestamp);
    }

    /**
     * Computes expected timestamp tracking fields when processing a device list update.
     *
     * @param incomingTimestamp the timestamp from the server response
     * @param cachedList        the cached device list, or {@code null}
     * @param lastADVCheckTime  the last ADV device check time, or {@code null}
     * @return the computed expected timestamp fields
     *
     * @apiNote WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord: calculates
     * updated expected timestamp metadata based on current state and target time.
     */
    public static ExpectedTimestampResult computeExpectedTimestampForDeviceRecord(
            Instant incomingTimestamp,
            DeviceList cachedList,
            Instant lastADVCheckTime
    ) {
        if (cachedList == null) {
            return new ExpectedTimestampResult(null, null, null);
        }

        var currentTimestamp = cachedList.timestamp();
        Instant currentExpectedTimestamp = null;
        Instant currentExpectedTimestampLastDeviceJobTimestamp = null;
        Instant currentExpectedTimestampUpdateTimestamp = null;

        // WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord:
        // Only extract existing values from non-deleted records
        if (!cachedList.deleted()) {
            currentExpectedTimestamp = cachedList.expectedTimestamp();
            currentExpectedTimestampLastDeviceJobTimestamp = cachedList.expectedTimestampLastDeviceJobTimestamp();
            currentExpectedTimestampUpdateTimestamp = cachedList.expectedTimestampUpdateTimestamp();
        }

        return computeNewExpectedTimestamp(
                incomingTimestamp,
                currentTimestamp,
                lastADVCheckTime,
                currentExpectedTimestamp,
                currentExpectedTimestampLastDeviceJobTimestamp,
                currentExpectedTimestampUpdateTimestamp
        );
    }

    /**
     * Computes new expected timestamp tracking fields based on incoming and current values.
     *
     * @param incomingTimestamp                              the incoming timestamp
     * @param currentTimestamp                               the current timestamp
     * @param lastADVCheckTime                               the last ADV check time, or {@code null}
     * @param currentExpectedTimestamp                       the current expected timestamp, or {@code null}
     * @param currentExpectedTimestampLastDeviceJobTimestamp the current last job timestamp, or {@code null}
     * @param currentExpectedTimestampUpdateTimestamp        the current update timestamp, or {@code null}
     * @return the computed expected timestamp fields
     *
     * @apiNote WAWebAdvExpectedTsApi.computeNewExpectedTs: contains the core logic
     * to determine if expectedTs should be updated based on timestamp comparisons.
     */
    public static ExpectedTimestampResult computeNewExpectedTimestamp(
            Instant incomingTimestamp,
            Instant currentTimestamp,
            Instant lastADVCheckTime,
            Instant currentExpectedTimestamp,
            Instant currentExpectedTimestampLastDeviceJobTimestamp,
            Instant currentExpectedTimestampUpdateTimestamp
    ) {
        var result = new ExpectedTimestampResult(
                currentExpectedTimestamp,
                currentExpectedTimestampLastDeviceJobTimestamp,
                currentExpectedTimestampUpdateTimestamp
        );

        // WAWebAdvExpectedTsApi.computeNewExpectedTs:
        // If current timestamp is already >= incoming, no update needed
        if (!currentTimestamp.isBefore(incomingTimestamp)) {
            return result;
        }

        // WAWebAdvExpectedTsApi.computeNewExpectedTs:
        // If current expectedTs is already >= incoming, no update needed
        if (currentExpectedTimestamp != null && !currentExpectedTimestamp.isBefore(incomingTimestamp)) {
            return result;
        }

        // WAWebAdvExpectedTsApi.computeNewExpectedTs:
        // Update expectedTsUpdateTs only when setting a new expectedTs target
        // (i.e., when there's no existing expectedTs or current has caught up to it)
        var newExpectedTimestampUpdateTimestamp = currentExpectedTimestampUpdateTimestamp;
        if (currentExpectedTimestamp == null || !currentTimestamp.isBefore(currentExpectedTimestamp)) {
            newExpectedTimestampUpdateTimestamp = Instant.now();
        }

        return new ExpectedTimestampResult(incomingTimestamp, lastADVCheckTime, newExpectedTimestampUpdateTimestamp);
    }

    /**
     * Checks if a device list is stale (expired) based on timestamp and expected timestamp.
     *
     * @param deviceList       the device list to check
     * @param currentTime      current time
     * @param expiryThreshold  threshold for regular expiration
     * @param lastADVCheckTime the last ADV device check time, or {@code null}
     * @return {@code true} if the device list is stale
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob: checks both regular expiration based on
     * timestamp and expectedTs-based staleness using the 25-hour threshold.
     */
    public static boolean isDeviceListStale(
            DeviceList deviceList,
            Instant currentTime,
            Duration expiryThreshold,
            Instant lastADVCheckTime
    ) {
        var timestamp = deviceList.timestamp();

        // WAWebAdvDeviceInfoCheckJob condition 1:
        // Device list is stale if timestamp exceeds the expiry threshold (default 30 days)
        if (Duration.between(timestamp, currentTime).compareTo(expiryThreshold) >= 0) {
            return true;
        }

        // WAWebAdvDeviceInfoCheckJob condition 2:
        // Device list is stale if expectedTsUpdateTs was set >25 hours ago AND
        // the last ADV job timestamp doesn't match (indicating the job ran but didn't clear it)
        var expectedTimestampUpdateTimestamp = deviceList.expectedTimestampUpdateTimestamp();
        if (expectedTimestampUpdateTimestamp == null) {
            return false;
        }

        var elapsedSinceUpdate = Duration.between(expectedTimestampUpdateTimestamp, currentTime);
        if (elapsedSinceUpdate.compareTo(EXPECTED_TIMESTAMP_UPDATE_THRESHOLD) < 0) {
            return false;
        }

        var expectedTimestampLastDeviceJobTimestamp = deviceList.expectedTimestampLastDeviceJobTimestamp();
        return expectedTimestampLastDeviceJobTimestamp == null || !expectedTimestampLastDeviceJobTimestamp.equals(lastADVCheckTime);
    }

    /**
     * Checks if a device list is close to expiration.
     *
     * @param deviceList       the device list to check
     * @param currentTime      current time
     * @param warningThreshold threshold for warning
     * @return {@code true} if the device list is close to expiration
     *
     * @apiNote WAWebAdvDeviceInfoCheckJob: triggers proactive sync for device lists
     * approaching expiration to prevent ADV failures.
     */
    public static boolean isDeviceListCloseToExpiration(
            DeviceList deviceList,
            Instant currentTime,
            Duration warningThreshold
    ) {
        var timestamp = deviceList.timestamp();

        // WAWebAdvDeviceInfoCheckJob:
        // Warn if approaching the regular expiration threshold
        if (Duration.between(timestamp, currentTime).compareTo(warningThreshold) >= 0) {
            return true;
        }

        // WAWebAdvDeviceInfoCheckJob:
        // Warn if expectedTs is set and ahead of current timestamp
        // (indicates server reported a newer timestamp we haven't synced yet)
        var expectedTimestamp = deviceList.expectedTimestamp();
        return expectedTimestamp != null && expectedTimestamp.isAfter(timestamp);
    }
}
