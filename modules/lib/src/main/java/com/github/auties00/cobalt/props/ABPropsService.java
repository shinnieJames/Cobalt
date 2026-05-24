package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Read-write facade over the AB-props feature flag and configuration
 * service.
 *
 * @apiNote
 * Inject this through the client and consume it wherever Cobalt
 * needs a per-account feature flag, server-pushed configuration
 * value, or WAM event sampling weight. {@link DefaultABPropsService}
 * is the production wiring; tests can supply alternative
 * implementations (the {@code props.test} package ships one). Every
 * concrete caller depends on this interface, never on a concrete
 * implementation. Synced values are stored by their numeric
 * {@code code} and exposed through the typed accessors, each
 * falling back to
 * {@link ABProp#defaultValue()}
 * when the server has not provided a value. The {@code waitForSync}
 * accessors block the caller until the first sync completes (subject
 * to the implementation's own timeout); the no-argument convenience
 * overloads default to {@code waitForSync == true}.
 *
 * @implSpec
 * Implementations must be thread-safe.
 *
 * @see ABProp
 */
public interface ABPropsService {
    /**
     * Runs a sync round with default options and a three-attempt
     * retry budget.
     *
     * @apiNote
     * Use this for the normal periodic resync. Equivalent to
     * {@code sync(null, true)} which selects the regular
     * {@code propsHash} branch and lets the implementation include
     * the persisted hash on the wire.
     *
     * @return {@code true} when at least one attempt succeeded
     */
    default boolean sync() {
        return sync(null, true);
    }

    /**
     * Runs a sync round with caller-supplied options.
     *
     * @apiNote
     * Use this when the caller drives the {@code propsHash}-vs
     * -emergency-push branch directly: pass a non-{@code null}
     * {@code localRefreshId} to take the emergency push branch (the
     * server pushes the new refresh id on the {@code <success>}
     * stanza when a global AB-prop update needs immediate
     * propagation); pass {@code null} to take the regular branch.
     *
     * @param localRefreshId the refresh-id override used by the
     *                       emergency push branch, or {@code null}
     *                       for the regular {@code propsHash}
     *                       branch
     * @param shouldSendHash whether the {@code propsHash} branch
     *                       may include the persisted hash on the
     *                       request
     * @return {@code true} when at least one attempt succeeded
     */
    boolean sync(Long localRefreshId, boolean shouldSendHash);

    /**
     * Performs a single sync round trip without the retry loop.
     *
     * @apiNote
     * Use this when an external retry policy already drives the
     * outer loop; {@link #sync(Long, boolean)} is the usual entry
     * point.
     *
     * @param localRefreshId the refresh-id override that selects
     *                       the emergency push branch, or
     *                       {@code null} for the regular branch
     * @param shouldSendHash whether the persisted hash is included
     *                       on the regular branch
     * @return {@code true} when the response was processed
     *         successfully
     */
    boolean syncABProps(Long localRefreshId, boolean shouldSendHash);

    /**
     * Parses a sync response and applies it to the in-memory
     * caches.
     *
     * @apiNote
     * Use this to feed a response into the service when the caller
     * obtained the {@code <iq xmlns="abt">} response through a
     * non-default path (replay tests, fixture-driven scenarios).
     *
     * @param response the server response node
     * @return {@code true} when the response was parsed
     *         successfully
     */
    boolean process(Node response);

    /**
     * Returns the AB key most recently received from the server.
     *
     * @apiNote
     * The AB key is the WAM {@code abKey2} global attribute, used
     * by Cobalt embedders that mirror WA Web's telemetry surface.
     *
     * @return the AB key, or empty when none has been provided
     */
    Optional<String> abKey();

    /**
     * Returns the {@code hash} attribute most recently received
     * from the server.
     *
     * @apiNote
     * Sent on the next sync request so the server can reply with
     * a delta update instead of the full prop list.
     *
     * @return the AB-props hash, or empty when no sync has
     *         completed
     */
    Optional<String> hash();

    /**
     * Returns the configured refresh interval in seconds.
     *
     * @apiNote
     * Drives the periodic sync schedule; defaults to one day
     * ({@code 86400}) when no value has been persisted.
     *
     * @return the refresh interval in seconds
     */
    long refresh();

    /**
     * Returns the timestamp of the most recent successful sync.
     *
     * @apiNote
     * Used as the {@code lastSyncTime} field on the persisted
     * AB-props blob; embedders may surface it to gate
     * stale-data UI.
     *
     * @return the last sync timestamp, or empty when none has
     *         been recorded
     */
    Optional<Instant> lastSyncTime();

    /**
     * Returns whether at least one sync has completed since the
     * session was created.
     *
     * @apiNote
     * Use this to gate code paths that depend on the AB-props
     * cache being populated (sampling weight reads, default-value
     * vs synced-value comparisons).
     *
     * @return {@code true} when a previous sync established
     *         AB-props state
     */
    boolean isAfterFirstSync();

    /**
     * Persists the {@code ABPROPS} blob attributes returned on
     * the most recent sync.
     *
     * @apiNote
     * Each non-{@code null} parameter overwrites the corresponding
     * persisted field; {@code null} parameters keep the previous
     * value. Use this to forward the response attributes from
     * {@link #process(Node)} into the persistent store.
     *
     * @param abKey          the AB key, or {@code null} to keep
     *                       the previous value
     * @param hash           the AB-props hash, or {@code null} to
     *                       keep the previous value
     * @param refreshSeconds the refresh interval in seconds, or
     *                       {@code null} to keep the previous
     *                       value
     * @param lastSyncTime   the sync completion instant
     */
    void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime);

    /**
     * Returns the AB-props refresh id.
     *
     * @apiNote
     * Used as the {@code propsRefreshId} attribute on the next
     * sync request when the relay's emergency push branch is
     * active.
     *
     * @return the AB-props refresh id, or {@code 0} when never
     *         set
     */
    long refreshId();

    /**
     * Persists the AB-props refresh id received from the server.
     *
     * @apiNote
     * Called from {@link #process(Node)} when the response carries
     * a {@code refresh_id} attribute.
     *
     * @param refreshId the refresh id to persist
     */
    void setRefreshId(long refreshId);

    /**
     * Returns the web-only AB-props refresh id.
     *
     * @apiNote
     * Drives the web-tier emergency push request; distinct from
     * the cross-platform {@link #refreshId()} so the relay can
     * push web-specific AB-prop updates without disturbing
     * mobile-tier sessions.
     *
     * @return the web AB-props refresh id, or {@code 0} when
     *         never set
     */
    long webRefreshId();

    /**
     * Persists the web-only AB-props refresh id received from
     * the server.
     *
     * @param webRefreshId the refresh id to persist
     */
    void setWebRefreshId(long webRefreshId);

    /**
     * Returns the group AB-props refresh id received from the
     * server.
     *
     * @apiNote
     * Used by the group-AB-props sync job to gate per-group
     * refreshes.
     *
     * @return the group refresh id, or {@code 0} when never set
     */
    long groupAbPropsRefreshId();

    /**
     * Persists the group AB-props refresh id received from the
     * server.
     *
     * @param groupRefreshId the refresh id to persist
     */
    void setGroupAbPropsRefreshId(long groupRefreshId);

    /**
     * Returns the timestamp of the last group AB-props emergency
     * push.
     *
     * @apiNote
     * Used by the group AB-props sync job to decide whether the
     * locally cached group props are stale relative to the latest
     * server-pushed emergency notification.
     *
     * @return the last emergency push timestamp, or empty when
     *         none has been recorded
     */
    Optional<Instant> groupAbPropsEmergencyPushTimestamp();

    /**
     * Persists the timestamp of the last group AB-props
     * emergency push.
     *
     * @param timestamp the emergency push timestamp
     */
    void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp);

    /**
     * Fetches the per-group AB-props bundle from the relay.
     *
     * @apiNote
     * Use this to drive per-group experiment overrides for group
     * surfaces (group-call admin controls, premium group
     * features). Returns empty on any failure variant.
     *
     * @param groupJid  the target group JID
     * @param propsHash the cached group-props hash, or
     *                  {@code null} for an unconditional fetch
     * @return the projected result on success, empty otherwise
     */
    Optional<GroupAbPropsResult> getGroupAbPropsProtocol(Jid groupJid, String propsHash);

    /**
     * Returns an unmodifiable snapshot of every synced AB prop
     * keyed by its numeric {@code config_code}.
     *
     * @apiNote
     * Use this for diagnostics or to export the synced state for
     * a debug-overlay UI.
     *
     * @return a defensive copy of the
     *         {@code config_code -> config_value} map
     */
    Map<Integer, String> abPropConfigs();

    /**
     * Flags {@code prop} as having been read by the host
     * application.
     *
     * @apiNote
     * Use this to drive the WAM exposure-key attribute: WA Web
     * marks each AB-prop as accessed on first read so the WAM
     * server can attribute downstream events to the right
     * experiment cell.
     *
     * @param prop the AB prop that was just read
     * @return {@code true} when this is the first access
     */
    boolean setConfigAccessed(ABProp prop);

    /**
     * Returns whether {@code prop} has previously been flagged
     * as accessed.
     *
     * @apiNote
     * Used by code paths that need to know whether the WAM
     * exposure attribute has already been emitted for an
     * AB-prop.
     *
     * @param prop the AB prop to query
     * @return {@code true} when
     *         {@link #setConfigAccessed(ABProp)}
     *         has already been called
     */
    boolean isConfigAccessed(ABProp prop);

    /**
     * Returns the raw string value for {@code prop}, blocking
     * until the first sync completes.
     *
     * @apiNote
     * Equivalent to {@code getString(prop, true)}; use the
     * two-argument overload when the caller wants to opt out of
     * the first-sync wait.
     *
     * @param prop the AB prop definition
     * @return the string value, or the appropriate default
     */
    default String getString(ABProp prop) {
        return getString(prop, true);
    }

    /**
     * Returns the raw string value for {@code prop}, optionally
     * skipping the wait for the first sync.
     *
     * @apiNote
     * Pass {@code waitForSync = false} from hot paths where
     * blocking on the first sync would stall an unrelated
     * pipeline; the caller then accepts the prop's default on
     * cold cache.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync
     *                    before reading
     * @return the string value, or the appropriate default
     */
    String getString(ABProp prop, boolean waitForSync);

    /**
     * Returns the boolean value for {@code prop}, blocking until
     * the first sync completes.
     *
     * @apiNote
     * Equivalent to {@code getBool(prop, true)}; use the
     * two-argument overload to opt out of the first-sync wait.
     *
     * @param prop the AB prop definition
     * @return the parsed boolean, or the default
     */
    default boolean getBool(ABProp prop) {
        return getBool(prop, true);
    }

    /**
     * Returns the boolean value for {@code prop}, optionally
     * skipping the wait for the first sync.
     *
     * @apiNote
     * Pass {@code waitForSync = false} from hot paths where
     * blocking on the first sync would stall an unrelated
     * pipeline.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync
     *                    before reading
     * @return the parsed boolean, or the default
     */
    boolean getBool(ABProp prop, boolean waitForSync);

    /**
     * Returns the integer value for {@code prop}, blocking until
     * the first sync completes.
     *
     * @apiNote
     * Equivalent to {@code getInt(prop, true)}; returns
     * {@code 0} when neither the synced value nor the default
     * parses as an integer.
     *
     * @param prop the AB prop definition
     * @return the parsed integer, the default, or {@code 0}
     */
    default int getInt(ABProp prop) {
        return getInt(prop, true);
    }

    /**
     * Returns the integer value for {@code prop}, optionally
     * skipping the wait for the first sync.
     *
     * @apiNote
     * Pass {@code waitForSync = false} from hot paths where
     * blocking on the first sync would stall an unrelated
     * pipeline.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync
     *                    before reading
     * @return the parsed integer, the default, or {@code 0}
     */
    int getInt(ABProp prop, boolean waitForSync);

    /**
     * Returns the long value for {@code prop}, blocking until
     * the first sync completes.
     *
     * @apiNote
     * Equivalent to {@code getLong(prop, true)}; returns
     * {@code 0L} when neither the synced value nor the default
     * parses as a long.
     *
     * @param prop the AB prop definition
     * @return the parsed long, the default, or {@code 0L}
     */
    default long getLong(ABProp prop) {
        return getLong(prop, true);
    }

    /**
     * Returns the long value for {@code prop}, optionally
     * skipping the wait for the first sync.
     *
     * @apiNote
     * Pass {@code waitForSync = false} from hot paths where
     * blocking on the first sync would stall an unrelated
     * pipeline.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync
     *                    before reading
     * @return the parsed long, the default, or {@code 0L}
     */
    long getLong(ABProp prop, boolean waitForSync);

    /**
     * Returns the double value for {@code prop}, blocking until
     * the first sync completes.
     *
     * @apiNote
     * Equivalent to {@code getDouble(prop, true)}; returns
     * {@code 0.0} when neither the synced value nor the default
     * parses as a double.
     *
     * @param prop the AB prop definition
     * @return the parsed double, the default, or {@code 0.0}
     */
    default double getDouble(ABProp prop) {
        return getDouble(prop, true);
    }

    /**
     * Returns the double value for {@code prop}, optionally
     * skipping the wait for the first sync.
     *
     * @apiNote
     * Pass {@code waitForSync = false} from hot paths where
     * blocking on the first sync would stall an unrelated
     * pipeline.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on the first sync
     *                    before reading
     * @return the parsed double, the default, or {@code 0.0}
     */
    double getDouble(ABProp prop, boolean waitForSync);

    /**
     * Returns the sampling-weight override for the given WAM
     * event code.
     *
     * @apiNote
     * Use this when the WAM event commit site needs to override
     * the WAM-property-defined sampling weight; the relay can
     * push per-event sampling adjustments through the AB-props
     * channel.
     *
     * @param eventCode the WAM event identifier
     * @return the override weight, or empty when none was synced
     */
    OptionalInt getSamplingWeight(int eventCode);

    /**
     * Returns an unmodifiable snapshot of every WAM event
     * sampling-weight override parsed from the most recent sync.
     *
     * @apiNote
     * Use this for diagnostics or to export the sampling state
     * for a debug-overlay UI.
     *
     * @return a defensive copy of the sampling-config map
     */
    Map<Integer, Integer> samplingConfigs();

    /**
     * Replaces the sampling-config cache with the supplied
     * entries.
     *
     * @apiNote
     * Called from {@link #process(Node)} when the response is a
     * full (non-delta) update carrying sampling configs. A
     * {@code null} or empty argument is a no-op and returns
     * {@code false}; a non-empty argument clears and replaces
     * the cache and returns {@code true}.
     *
     * @param configs the new sampling configs, or {@code null}
     *                or empty for a no-op
     * @return {@code true} when the cache was replaced
     */
    boolean updateEventSamplingConfigs(Map<Integer, Integer> configs);

    /**
     * Returns the number of synced AB props currently held in
     * memory.
     *
     * @apiNote
     * Use this for diagnostics; for individual prop reads use
     * the typed accessors.
     *
     * @return the count of synced props
     */
    int size();

    /**
     * Returns whether no AB props have been synced yet.
     *
     * @apiNote
     * Use this for diagnostics; for individual prop reads use
     * the typed accessors.
     *
     * @return {@code true} when no props have been recorded
     */
    boolean isEmpty();

    /**
     * Resets the service for a fresh session.
     *
     * @apiNote
     * Call this on logout to drop the synced state and reset the
     * sync future so subsequent queries block on the next sync.
     * Refresh interval and refresh ids stay at their persisted
     * values because the relay also leaves them in place across
     * sign-outs.
     */
    void clear();
}
