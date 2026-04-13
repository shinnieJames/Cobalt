package com.github.auties00.cobalt.device.timestamp;

import com.github.auties00.cobalt.model.device.info.DeviceList;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Utilities for managing expected timestamp logic in device lists.
 *
 * <p>The expected timestamp system detects stale device lists that have not been
 * refreshed recently, even when the device hash (dhash) matches.
 *
 * @implNote WAWebAdvExpectedTsApi: provides the original implementation for
 * expectedTs, expectedTsLastDeviceJobTs, and expectedTsUpdateTs tracking.
 * WAWebAdvDeviceInfoCheckJob: provides staleness and expiration check logic.
 */
public final class DeviceExpectedTsUtils {

    /**
     * Threshold for expected timestamp staleness checks in the ADV scheduler.
     * Corresponds to {@code 25 * HOUR_SECONDS} in WA Web.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob: uses {@code 25 * HOUR_SECONDS} as the
     * threshold constant {@code m} for determining whether expectedTsUpdateTs
     * indicates a stale device list.
     */
    private static final Duration EXPECTED_TIMESTAMP_UPDATE_THRESHOLD = Duration.ofHours(25);

    /**
     * Prevents instantiation of this utility class.
     *
     * @implNote NO_WA_BASIS: Java utility class pattern.
     */
    private DeviceExpectedTsUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Determines whether the expected timestamp should be cleared for a device list.
     *
     * <p>Returns {@code true} when either the server timestamp has caught up to the
     * cached expected timestamp, or when the incoming expected timestamp matches the
     * cached value and a staleness condition is met based on the last ADV job check.
     *
     * @implNote WAWebAdvExpectedTsApi.shouldClearExpectedTs
     * @param incomingTimestamp         the timestamp from server response
     * @param incomingExpectedTimestamp the expected timestamp from server response, or {@code null}
     * @param cachedList                the cached device list, or {@code null}
     * @param lastADVCheckTime          the last ADV device check time, or {@code null}
     * @return {@code true} if expected timestamp should be cleared
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
     * <p>Handles {@code null} values safely: two {@code null} values are considered
     * equal, and a {@code null} compared to a non-{@code null} value is considered changed.
     *
     * @implNote ADAPTED: WAWebAdvExpectedTsApi.computeNewExpectedTs - Java null-safe
     * comparison utility for nullable {@link Instant} values; WA Web performs this
     * comparison inline.
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
     * <p>Extracts current expected timestamp values from the cached device list (if
     * non-deleted) and delegates to {@link #computeNewExpectedTimestamp} for the
     * actual computation.
     *
     * @implNote WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord
     * @param incomingTimestamp the timestamp from the server response
     * @param cachedList        the cached device list, or {@code null}
     * @param lastADVCheckTime  the last ADV device check time, or {@code null}
     * @return the computed expected timestamp fields
     */
    public static ExpectedTimestampResult computeExpectedTimestampForDeviceRecord(
            Instant incomingTimestamp,
            DeviceList cachedList,
            Instant lastADVCheckTime
    ) {
        // WAWebAdvExpectedTsApi.computeExpectedTsForDeviceRecord: var r=t==null?void 0:t.timestamp
        // Returns empty result if cachedList is null OR cachedList.timestamp is null
        if (cachedList == null) {
            return new ExpectedTimestampResult(null, null, null);
        }

        var currentTimestamp = cachedList.timestamp();
        if (currentTimestamp == null) {
            return new ExpectedTimestampResult(null, null, null);
        }
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
     * <p>If the current timestamp or current expected timestamp already meets or
     * exceeds the incoming timestamp, the existing values are preserved. Otherwise,
     * the expected timestamp is set to the incoming timestamp, and the update
     * timestamp is refreshed when the expected timestamp is newly set or the current
     * timestamp has caught up to the previous expected timestamp.
     *
     * @implNote WAWebAdvExpectedTsApi.computeNewExpectedTs
     * @param incomingTimestamp                              the incoming timestamp
     * @param currentTimestamp                               the current timestamp
     * @param lastADVCheckTime                               the last ADV check time, or {@code null}
     * @param currentExpectedTimestamp                       the current expected timestamp, or {@code null}
     * @param currentExpectedTimestampLastDeviceJobTimestamp the current last job timestamp, or {@code null}
     * @param currentExpectedTimestampUpdateTimestamp        the current update timestamp, or {@code null}
     * @return the computed expected timestamp fields
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
     * <p>A device list is considered stale if its timestamp exceeds the expiry
     * threshold, or if its expected timestamp update timestamp was set more than
     * 25 hours ago and the last device job timestamp does not match the last ADV
     * check time.
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.S
     * @param deviceList       the device list to check
     * @param currentTime      current time
     * @param expiryThreshold  threshold for regular expiration
     * @param lastADVCheckTime the last ADV device check time, or {@code null}
     * @return {@code true} if the device list is stale
     */
    public static boolean isDeviceListStale(
            DeviceList deviceList,
            Instant currentTime,
            Duration expiryThreshold,
            Instant lastADVCheckTime
    ) {
        var timestamp = deviceList.timestamp();

        // WAWebAdvDeviceInfoCheckJob.S: e-n.timestamp>=t
        // Device list is stale if timestamp exceeds the expiry threshold
        if (Duration.between(timestamp, currentTime).compareTo(expiryThreshold) >= 0) {
            return true;
        }

        // WAWebAdvDeviceInfoCheckJob.S: n.expectedTsUpdateTs!=null
        var expectedTimestampUpdateTimestamp = deviceList.expectedTimestampUpdateTimestamp();
        if (expectedTimestampUpdateTimestamp == null) {
            return false;
        }

        // WAWebAdvDeviceInfoCheckJob.S: e-n.expectedTsUpdateTs>=m (m=25*HOUR_SECONDS)
        var elapsedSinceUpdate = Duration.between(expectedTimestampUpdateTimestamp, currentTime);
        if (elapsedSinceUpdate.compareTo(EXPECTED_TIMESTAMP_UPDATE_THRESHOLD) < 0) {
            return false;
        }

        // WAWebAdvDeviceInfoCheckJob.S: n.expectedTsLastDeviceJobTs!==r
        // Uses !== (strict inequality) in JS, which means both-null returns false
        var expectedTimestampLastDeviceJobTimestamp = deviceList.expectedTimestampLastDeviceJobTimestamp();
        return !Objects.equals(expectedTimestampLastDeviceJobTimestamp, lastADVCheckTime);
    }

    /**
     * Checks if a device list is close to expiration.
     *
     * <p>Returns {@code true} if the device list's timestamp exceeds the warning
     * threshold, or if the expected timestamp is set and ahead of the current
     * timestamp (indicating a newer device list version exists on the server).
     *
     * @implNote WAWebAdvDeviceInfoCheckJob.R
     * @param deviceList       the device list to check
     * @param currentTime      current time
     * @param warningThreshold threshold for warning
     * @return {@code true} if the device list is close to expiration
     */
    public static boolean isDeviceListCloseToExpiration(
            DeviceList deviceList,
            Instant currentTime,
            Duration warningThreshold
    ) {
        var timestamp = deviceList.timestamp();

        // WAWebAdvDeviceInfoCheckJob.R: e-n.timestamp>=t
        if (Duration.between(timestamp, currentTime).compareTo(warningThreshold) >= 0) {
            return true;
        }

        // WAWebAdvDeviceInfoCheckJob.R: n.expectedTs!=null?n.expectedTs>n.timestamp:!1
        var expectedTimestamp = deviceList.expectedTimestamp();
        return expectedTimestamp != null && expectedTimestamp.isAfter(timestamp);
    }
}
