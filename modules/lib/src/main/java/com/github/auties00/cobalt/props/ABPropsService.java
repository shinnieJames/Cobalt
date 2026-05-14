package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Read-write facade over the A/B testing props feature.
 *
 * <p>This is the consumer-facing contract: every caller in the codebase
 * depends on this interface (never on a concrete implementation). The
 * default production implementation is {@link DefaultABPropsService}, wired
 * up by {@link #forClient(WhatsAppClient)}. Tests can supply their own
 * implementation through the {@code props.test} package without rebuilding
 * the live sync machinery.
 *
 * <p>AB props are feature flags and configuration values that control
 * client behaviour. Synced values are stored by their numeric
 * {@code code} and exposed through the typed accessors on this interface,
 * each falling back to {@link ABProp#defaultValue()} when the server has
 * not provided a value. The {@code waitForSync} accessors block the
 * caller until the first sync completes (subject to the implementation's
 * own timeout); the no-argument convenience methods default to
 * {@code waitForSync == true}.
 *
 * <p>Implementations must be thread-safe.
 *
 * @see ABProp
 */
public interface ABPropsService {
    /**
     * Runs a sync round with the default options, retrying up to three
     * times.
     *
     * @return {@code true} when at least one attempt succeeded
     */
    default boolean sync() {
        return sync(null, true);
    }

    /**
     * Runs a sync round with caller-supplied options.
     *
     * @param localRefreshId the refresh-id override used by the emergency
     *                       push branch, or {@code null} for the regular
     *                       {@code propsHash} branch
     * @param shouldSendHash whether the {@code propsHash} branch may
     *                       include the persisted hash on the request
     * @return {@code true} when at least one attempt succeeded
     */
    boolean sync(Long localRefreshId, boolean shouldSendHash);

    /**
     * Performs a single sync round trip.
     *
     * @param localRefreshId the refresh-id override that selects the
     *                       emergency push branch, or {@code null} for the
     *                       regular branch
     * @param shouldSendHash whether the persisted hash is included on the
     *                       regular branch
     * @return {@code true} when the response was processed successfully
     */
    boolean syncABProps(Long localRefreshId, boolean shouldSendHash);

    /**
     * Parses a sync response and applies it to the in-memory caches.
     *
     * @param response the server response node
     * @return {@code true} when the response was parsed successfully
     */
    boolean process(Node response);

    /**
     * Returns the AB key most recently received from the server.
     *
     * @return the AB key, or empty when none has been provided
     */
    Optional<String> abKey();

    /**
     * Returns the {@code hash} attribute most recently received from the
     * server.
     *
     * @return the AB-props hash, or empty when no sync has completed
     */
    Optional<String> hash();

    /**
     * Returns the configured refresh interval in seconds.
     *
     * @return the refresh interval in seconds
     */
    long refresh();

    /**
     * Returns the timestamp of the most recent successful sync.
     *
     * @return the last sync timestamp, or empty when none has been recorded
     */
    Optional<Instant> lastSyncTime();

    /**
     * Returns whether at least one sync has completed since the session
     * was created.
     *
     * @return {@code true} when a previous sync established AB-props state
     */
    boolean isAfterFirstSync();

    /**
     * Persists the {@code ABPROPS} blob attributes returned on the most
     * recent sync.
     *
     * @param abKey          the AB key, or {@code null} to keep the previous value
     * @param hash           the AB-props hash, or {@code null} to keep the previous value
     * @param refreshSeconds the refresh interval in seconds, or {@code null} to keep
     * @param lastSyncTime   the sync completion instant
     */
    void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime);

    /**
     * Returns the AB-props refresh id.
     *
     * @return the AB-props refresh id, or {@code 0} when never set
     */
    long refreshId();

    /**
     * Persists the AB-props refresh id received from the server.
     *
     * @param refreshId the refresh id to persist
     */
    void setRefreshId(long refreshId);

    /**
     * Returns the web-only AB-props refresh id.
     *
     * @return the web AB-props refresh id, or {@code 0} when never set
     */
    long webRefreshId();

    /**
     * Persists the web-only AB-props refresh id received from the server.
     *
     * @param webRefreshId the refresh id to persist
     */
    void setWebRefreshId(long webRefreshId);

    /**
     * Returns the group AB-props refresh id received from the server.
     *
     * @return the group refresh id, or {@code 0} when never set
     */
    long groupAbPropsRefreshId();

    /**
     * Persists the group AB-props refresh id received from the server.
     *
     * @param groupRefreshId the refresh id to persist
     */
    void setGroupAbPropsRefreshId(long groupRefreshId);

    /**
     * Returns the timestamp of the last group AB-props emergency push.
     *
     * @return the last emergency push timestamp, or empty when none has been recorded
     */
    Optional<Instant> groupAbPropsEmergencyPushTimestamp();

    /**
     * Persists the timestamp of the last group AB-props emergency push.
     *
     * @param timestamp the emergency push timestamp
     */
    void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp);

    /**
     * Fetches the per-group AB-props bundle from the relay.
     *
     * @param groupJid  the target group JID
     * @param propsHash the cached group-props hash, or {@code null}
     * @return the projected result on success, empty otherwise
     */
    Optional<GroupAbPropsResult> getGroupAbPropsProtocol(Jid groupJid, String propsHash);

    /**
     * Returns an unmodifiable snapshot of every synced AB prop keyed by
     * its numeric {@code config_code}.
     *
     * @return a defensive copy of the {@code config_code -> config_value}
     *         map
     */
    Map<Integer, String> abPropConfigs();

    /**
     * Flags {@code prop} as having been read by the host application.
     *
     * @param prop the AB prop that was just read
     * @return {@code true} when this is the first access
     */
    boolean setConfigAccessed(ABProp prop);

    /**
     * Returns whether {@code prop} has previously been flagged as accessed.
     *
     * @param prop the AB prop to query
     * @return {@code true} when {@link #setConfigAccessed(ABProp)} has already been called
     */
    boolean isConfigAccessed(ABProp prop);

    /**
     * Returns the raw string value for {@code prop}, blocking until the
     * first sync completes.
     *
     * @param prop the AB prop definition
     * @return the string value, or the appropriate default
     */
    default String getString(ABProp prop) {
        return getString(prop, true);
    }

    /**
     * Returns the raw string value for {@code prop}, optionally skipping
     * the wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync before reading
     * @return the string value, or the appropriate default
     */
    String getString(ABProp prop, boolean waitForSync);

    /**
     * Returns the boolean value for {@code prop}, blocking until the first
     * sync completes.
     *
     * @param prop the AB prop definition
     * @return the parsed boolean, or the default
     */
    default boolean getBool(ABProp prop) {
        return getBool(prop, true);
    }

    /**
     * Returns the boolean value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync before reading
     * @return the parsed boolean, or the default
     */
    boolean getBool(ABProp prop, boolean waitForSync);

    /**
     * Returns the integer value for {@code prop}, blocking until the first
     * sync completes.
     *
     * @param prop the AB prop definition
     * @return the parsed integer, the default, or {@code 0}
     */
    default int getInt(ABProp prop) {
        return getInt(prop, true);
    }

    /**
     * Returns the integer value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync before reading
     * @return the parsed integer, the default, or {@code 0}
     */
    int getInt(ABProp prop, boolean waitForSync);

    /**
     * Returns the long value for {@code prop}, blocking until the first
     * sync completes.
     *
     * @param prop the AB prop definition
     * @return the parsed long, the default, or {@code 0L}
     */
    default long getLong(ABProp prop) {
        return getLong(prop, true);
    }

    /**
     * Returns the long value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync before reading
     * @return the parsed long, the default, or {@code 0L}
     */
    long getLong(ABProp prop, boolean waitForSync);

    /**
     * Returns the double value for {@code prop}, blocking until the first
     * sync completes.
     *
     * @param prop the AB prop definition
     * @return the parsed double, the default, or {@code 0.0}
     */
    default double getDouble(ABProp prop) {
        return getDouble(prop, true);
    }

    /**
     * Returns the double value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync before reading
     * @return the parsed double, the default, or {@code 0.0}
     */
    double getDouble(ABProp prop, boolean waitForSync);

    /**
     * Returns the sampling-weight override for the given WAM event code.
     *
     * @param eventCode the WAM event identifier
     * @return the override weight, or empty when none was synced
     */
    OptionalInt getSamplingWeight(int eventCode);

    /**
     * Returns an unmodifiable snapshot of every WAM event sampling-weight
     * override parsed from the most recent sync.
     *
     * @return a defensive copy of the sampling-config map
     */
    Map<Integer, Integer> samplingConfigs();

    /**
     * Replaces the sampling-config cache with the supplied entries.
     *
     * @param configs the new sampling configs, or {@code null} or empty for a no-op
     * @return {@code true} when the cache was replaced
     */
    boolean updateEventSamplingConfigs(Map<Integer, Integer> configs);

    /**
     * Returns the number of synced AB props currently held in memory.
     *
     * @return the count of synced props
     */
    int size();

    /**
     * Returns whether no AB props have been synced yet.
     *
     * @return {@code true} when no props have been recorded
     */
    boolean isEmpty();

    /**
     * Resets the service for a fresh session.
     */
    void clear();
}
