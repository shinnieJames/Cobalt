package com.github.auties00.cobalt.device.timestamp;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.info.DeviceList;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Stateless helpers that implement the expected-timestamp staleness logic of
 * the ADV device-info job.
 *
 * @apiNote
 * Used by {@link com.github.auties00.cobalt.device.DeviceService} when
 * folding a USync response into the local {@link DeviceList} record, and by
 * {@link com.github.auties00.cobalt.device.adv.DeviceADVChecker} when the
 * daily ADV scheduler decides whether to re-query a user's device list. The
 * three "expected-timestamp" fields tracked on each {@link DeviceList} let
 * Cobalt detect a device list whose dhash still matches the server's but is
 * known by the server to be obsolete.
 *
 * @implNote
 * This implementation mirrors the JS predicates in
 * {@code WAWebAdvExpectedTsApi} and the staleness branches inside
 * {@code WAWebAdvDeviceInfoCheckJob.runAdvDeviceInfoCheck}; the 25-hour
 * threshold is duplicated here because Cobalt has no equivalent of the
 * {@code WATimeUtils.HOUR_SECONDS} constant import the JS module reuses.
 */
@WhatsAppWebModule(moduleName = "WAWebAdvExpectedTsApi")
@WhatsAppWebModule(moduleName = "WAWebAdvDeviceInfoCheckJob")
public final class DeviceExpectedTsUtils {

    /**
     * Twenty-five hour grace window that the daily ADV job allows before it
     * treats an expected-timestamp record as stale.
     *
     * @apiNote
     * Read by {@link #isDeviceListStale(DeviceList, Instant, Duration, Instant)};
     * matches WA Web's {@code m = 25 * HOUR_SECONDS} constant in
     * {@code WAWebAdvDeviceInfoCheckJob}. One hour of slack is added on top
     * of the nominal 24-hour scheduler tick so a slightly delayed job run
     * does not flag every record as stale.
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Duration EXPECTED_TIMESTAMP_UPDATE_THRESHOLD = Duration.ofHours(25);

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DeviceExpectedTsUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Decides whether to clear the cached expected-timestamp triple after
     * folding in a USync response.
     *
     * @apiNote
     * Called from the USync handling path to keep the locally cached
     * {@link DeviceList#expectedTimestamp()} consistent with the latest
     * server signal. Returns {@code true} either when the new server
     * timestamp has caught up to the cached expectation (so the expectation
     * is no longer interesting), or when the incoming expectation matches
     * the cached one but a newer ADV job has run since the cached observation
     * (so the expectation is now redundant). Returns {@code false} otherwise.
     *
     * @implNote
     * This implementation mirrors the three-branch predicate of WA Web's
     * {@code WAWebAdvExpectedTsApi.shouldClearExpectedTs}: a {@code null}
     * or deleted cached list short-circuits to {@code false}, and the
     * ADV-check comparison treats a {@code null}
     * {@code expectedTsLastDeviceJobTs} as "older than any timestamp"
     * matching the JS {@code ==null || r > n.expectedTsLastDeviceJobTs}
     * check.
     *
     * @param incomingTimestamp         the {@code ts} attribute from the
     *                                  server response
     * @param incomingExpectedTimestamp the {@code expected_ts} attribute from
     *                                  the response, or {@code null}
     * @param cachedList                the currently cached device list, or
     *                                  {@code null}
     * @param lastADVCheckTime          the timestamp of the most recent ADV
     *                                  device-info job run, or {@code null}
     * @return {@code true} if the cached expected-timestamp triple should be
     *         cleared
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvExpectedTsApi",
            exports = "shouldClearExpectedTs",
            adaptation = WhatsAppAdaptation.DIRECT)
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

        if (!incomingTimestamp.isBefore(cachedExpectedTimestamp)) {
            return true;
        }

        if (incomingExpectedTimestamp == null || !incomingExpectedTimestamp.equals(cachedExpectedTimestamp) || lastADVCheckTime == null) {
            return false;
        }

        var cachedLastJobTimestamp = cachedList.expectedTimestampLastDeviceJobTimestamp();
        return cachedLastJobTimestamp == null || lastADVCheckTime.isAfter(cachedLastJobTimestamp);
    }

    /**
     * Compares two expected-timestamp {@link Instant} values with
     * {@code null}-tolerant equality.
     *
     * @apiNote
     * Convenience helper for call sites that need to know whether two
     * snapshots of an expected timestamp would round-trip the same value;
     * both {@code null} counts as unchanged, a {@code null} paired with a
     * non-{@code null} counts as changed.
     *
     * @param oldExpectedTimestamp the previous expected timestamp, or
     *                             {@code null}
     * @param newExpectedTimestamp the new expected timestamp, or {@code null}
     * @return {@code true} if the two values differ
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvExpectedTsApi",
            exports = "computeNewExpectedTs",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Drives {@link #computeNewExpectedTimestamp} from a cached {@link DeviceList}.
     *
     * @apiNote
     * Reads the current expected-timestamp triple from the cached list (when
     * non-deleted) and forwards it to
     * {@link #computeNewExpectedTimestamp(Instant, Instant, Instant, Instant, Instant, Instant)}.
     * Returned to callers that fold a fresh USync response into the cache
     * before persisting the device record.
     *
     * @implNote
     * This implementation reproduces WA Web's
     * {@code computeExpectedTsForDeviceRecord}: a {@code null} cached list
     * or a cached list missing a {@code timestamp} short-circuits to a blank
     * tuple, and a deleted cached record contributes nothing to the carry
     * forward.
     *
     * @param incomingTimestamp the {@code ts} attribute from the server
     *                          response
     * @param cachedList        the currently cached device list, or
     *                          {@code null}
     * @param lastADVCheckTime  the timestamp of the most recent ADV
     *                          device-info job run, or {@code null}
     * @return the updated expected-timestamp triple
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvExpectedTsApi",
            exports = "computeExpectedTsForDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static ExpectedTimestampResult computeExpectedTimestampForDeviceRecord(
            Instant incomingTimestamp,
            DeviceList cachedList,
            Instant lastADVCheckTime
    ) {
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
     * Core staleness arithmetic over the six expected-timestamp inputs.
     *
     * @apiNote
     * Public for direct testing and reuse; production callers normally go
     * through
     * {@link #computeExpectedTimestampForDeviceRecord(Instant, DeviceList, Instant)}
     * which marshals the inputs from a cached {@link DeviceList}.
     *
     * @implNote
     * This implementation mirrors the JS {@code computeNewExpectedTs}: when
     * neither the current timestamp nor the current expectation has fallen
     * behind the incoming timestamp the cached triple is returned unchanged;
     * otherwise the expectation moves to the incoming timestamp, the
     * last-job timestamp moves to {@code lastADVCheckTime}, and the
     * update-instant refreshes to {@link Instant#now()} only when a new
     * expectation target is set (not when the existing target is
     * reaffirmed).
     *
     * @param incomingTimestamp                              the {@code ts}
     *                                                       attribute from
     *                                                       the response
     * @param currentTimestamp                               the cached
     *                                                       record's
     *                                                       {@code timestamp}
     * @param lastADVCheckTime                               the most recent
     *                                                       ADV check time,
     *                                                       or {@code null}
     * @param currentExpectedTimestamp                       the currently
     *                                                       cached
     *                                                       expectation, or
     *                                                       {@code null}
     * @param currentExpectedTimestampLastDeviceJobTimestamp the currently
     *                                                       cached last-job
     *                                                       timestamp, or
     *                                                       {@code null}
     * @param currentExpectedTimestampUpdateTimestamp        the currently
     *                                                       cached
     *                                                       update-instant,
     *                                                       or {@code null}
     * @return the updated expected-timestamp triple
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvExpectedTsApi",
            exports = "computeNewExpectedTs",
            adaptation = WhatsAppAdaptation.DIRECT)
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

        if (!currentTimestamp.isBefore(incomingTimestamp)) {
            return result;
        }

        if (currentExpectedTimestamp != null && !currentExpectedTimestamp.isBefore(incomingTimestamp)) {
            return result;
        }

        var newExpectedTimestampUpdateTimestamp = currentExpectedTimestampUpdateTimestamp;
        if (currentExpectedTimestamp == null || !currentTimestamp.isBefore(currentExpectedTimestamp)) {
            newExpectedTimestampUpdateTimestamp = Instant.now();
        }

        return new ExpectedTimestampResult(incomingTimestamp, lastADVCheckTime, newExpectedTimestampUpdateTimestamp);
    }

    /**
     * Tests whether a cached {@link DeviceList} has aged out and must be
     * re-queried.
     *
     * @apiNote
     * Invoked by the daily ADV device-info scheduler to identify users whose
     * device lists are considered expired. The first branch matches the JS
     * {@code S(e, t, n, r)} predicate (raw age beats the configured
     * {@code num_days_key_index_list_expiration} threshold). The second
     * branch matches the {@code expectedTsUpdateTs}-based fallback that
     * triggers a re-query when the expected-timestamp tracking record is
     * over 25 hours old and a new ADV check has run in between.
     *
     * @implNote
     * This implementation collapses the JS {@code !=r} job-comparison into
     * {@link Objects#equals(Object, Object)} so a {@code null}
     * {@code lastADVCheckTime} compared against a {@code null}
     * {@code expectedTsLastDeviceJobTs} reports "matching" rather than
     * "diverging", matching the JS truthiness semantics of {@code !==}.
     *
     * @param deviceList       the device list to check
     * @param currentTime      the current wall-clock time
     * @param expiryThreshold  the {@code num_days_key_index_list_expiration}
     *                         budget converted to a {@link Duration}
     * @param lastADVCheckTime the most recent ADV check time, or
     *                         {@code null}
     * @return {@code true} when the list should be re-queried
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean isDeviceListStale(
            DeviceList deviceList,
            Instant currentTime,
            Duration expiryThreshold,
            Instant lastADVCheckTime
    ) {
        var timestamp = deviceList.timestamp();

        if (Duration.between(timestamp, currentTime).compareTo(expiryThreshold) >= 0) {
            return true;
        }

        var expectedTimestampUpdateTimestamp = deviceList.expectedTimestampUpdateTimestamp();
        if (expectedTimestampUpdateTimestamp == null) {
            return false;
        }

        var elapsedSinceUpdate = Duration.between(expectedTimestampUpdateTimestamp, currentTime);
        if (elapsedSinceUpdate.compareTo(EXPECTED_TIMESTAMP_UPDATE_THRESHOLD) < 0) {
            return false;
        }

        var expectedTimestampLastDeviceJobTimestamp = deviceList.expectedTimestampLastDeviceJobTimestamp();
        return !Objects.equals(expectedTimestampLastDeviceJobTimestamp, lastADVCheckTime);
    }

    /**
     * Tests whether a cached {@link DeviceList} is close enough to expiring
     * to warrant a pre-emptive refresh.
     *
     * @apiNote
     * Companion to {@link #isDeviceListStale(DeviceList, Instant, Duration, Instant)}
     * used by the daily ADV scheduler to populate the
     * {@code usersCloseToExpiration} bucket. Returns {@code true} when the
     * raw age exceeds the warning budget, or when the server has signalled
     * a newer device list exists via an {@code expectedTs} that sits ahead
     * of the cached {@code ts}.
     *
     * @implNote
     * This implementation mirrors WA Web's {@code R(e, t, n)} predicate;
     * the warning budget is supplied by callers (Cobalt currently passes
     * {@code expiryThreshold - num_days_before_device_expiry_check}).
     *
     * @param deviceList       the device list to check
     * @param currentTime      the current wall-clock time
     * @param warningThreshold the budget after which a warning should fire
     * @return {@code true} when the device list is close to expiration
     */
    @WhatsAppWebExport(moduleName = "WAWebAdvDeviceInfoCheckJob",
            exports = "runAdvDeviceInfoCheck",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean isDeviceListCloseToExpiration(
            DeviceList deviceList,
            Instant currentTime,
            Duration warningThreshold
    ) {
        var timestamp = deviceList.timestamp();

        if (Duration.between(timestamp, currentTime).compareTo(warningThreshold) >= 0) {
            return true;
        }

        var expectedTimestamp = deviceList.expectedTimestamp();
        return expectedTimestamp != null && expectedTimestamp.isAfter(timestamp);
    }
}
