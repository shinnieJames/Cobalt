package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.model.props.ABProp;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * In-memory {@link ABPropsService} implementation for tests.
 *
 * @apiNote
 * Use this in unit tests that need deterministic AB-prop values
 * without standing up
 * {@link DefaultABPropsService}
 * against a live relay. Holds a mutable map of
 * {@code ABProp -> value} so individual tests can dial specific
 * feature flags up or down; every accessor falls back to
 * {@link ABProp#defaultValue()} when the prop has not been
 * explicitly set, so tests that do not care about a particular
 * prop do not need to configure it. Construct via
 * {@link #builder()} for the fluent form, or pass an empty map
 * for the defaults-for-everything baseline:
 * {@snippet :
 *     var props = TestABPropsService.builder()
 *             .with(ABProp.ADV_ACCEPT_HOSTED_DEVICES, true)
 *             .with(ABProp.USERNAME_CONTACT_DISPLAY, false)
 *             .build();
 * }
 *
 * @implNote
 * The sync, sampling-persistence, and group-props branches are
 * stubbed out: {@link #sync(Long, boolean)} is a no-op that
 * returns {@code true}; {@link #process(Node)} returns
 * {@code true}; mutators that would normally write to the store
 * update local fields. The device-package code under test only
 * exercises the value accessors, which is what this stub
 * preserves.
 */
public final class TestABPropsService implements ABPropsService {
    /**
     * Preset {@code prop -> raw-value} map populated by the
     * caller or builder.
     */
    private final Map<ABProp, String> values;

    /**
     * Sampling-weight overrides keyed by event code.
     */
    private final Map<Integer, Integer> samplingConfigs;

    /**
     * Set of AB-prop codes already flagged as accessed.
     */
    private final Set<Integer> accessedConfigs;

    /**
     * Stub of the persisted AB key field.
     */
    private String abKey;

    /**
     * Stub of the persisted AB-props hash field.
     */
    private String hash;

    /**
     * Stub of the persisted refresh-interval field, in seconds.
     */
    private Long refreshSeconds;

    /**
     * Stub of the persisted last-sync timestamp.
     */
    private Instant lastSyncTime;

    /**
     * Stub of the persisted AB-props refresh id.
     */
    private long refreshId;

    /**
     * Stub of the persisted web-only AB-props refresh id.
     */
    private long webRefreshId;

    /**
     * Stub of the persisted group AB-props refresh id.
     */
    private long groupRefreshId;

    /**
     * Stub of the persisted group AB-props emergency-push
     * timestamp.
     */
    private Instant groupEmergencyPushTimestamp;

    /**
     * Constructs a service holding the given preset values.
     *
     * @apiNote
     * Use this from test fixtures that already have a
     * {@code prop -> raw-value} map (loaded from JSON, generated
     * from a parameterised test). For ad-hoc construction prefer
     * {@link #builder()}.
     *
     * @param values the preset values, in raw-string form
     */
    public TestABPropsService(Map<ABProp, String> values) {
        this.values = new HashMap<>(Objects.requireNonNull(values, "values"));
        this.samplingConfigs = new HashMap<>();
        this.accessedConfigs = new HashSet<>();
    }

    /**
     * Returns a fresh {@link Builder} for assembling preset
     * values fluently.
     *
     * @apiNote
     * Use this from tests that want the
     * {@code TestABPropsService.builder().with(...).build()}
     * form.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replaces the preset value for {@code prop} with a raw
     * string.
     *
     * @apiNote
     * Use this from a test that needs to mutate a single prop
     * mid-scenario after the service has been built.
     *
     * @param prop  the AB prop
     * @param value the raw string value to return for it
     * @return this service for fluent chaining
     */
    public TestABPropsService set(ABProp prop, String value) {
        values.put(prop, value);
        return this;
    }

    /**
     * Replaces the preset value for {@code prop} with a
     * boolean.
     *
     * @apiNote
     * Convenience overload of {@link #set(ABProp, String)} that
     * encodes the boolean via {@link Boolean#toString(boolean)}.
     *
     * @param prop  the AB prop
     * @param value the boolean value
     * @return this service for fluent chaining
     */
    public TestABPropsService set(ABProp prop, boolean value) {
        values.put(prop, Boolean.toString(value));
        return this;
    }

    /**
     * Replaces the preset value for {@code prop} with a number.
     *
     * @apiNote
     * Convenience overload of {@link #set(ABProp, String)} that
     * encodes the long via {@link Long#toString(long)}.
     *
     * @param prop  the AB prop
     * @param value the numeric value
     * @return this service for fluent chaining
     */
    public TestABPropsService set(ABProp prop, long value) {
        values.put(prop, Long.toString(value));
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op that always returns
     * {@code true} so tests do not have to stub the relay.
     */
    @Override
    public boolean sync(Long localRefreshId, boolean shouldSendHash) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op that always returns
     * {@code true} so tests do not have to stub the relay.
     */
    @Override
    public boolean syncABProps(Long localRefreshId, boolean shouldSendHash) {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code true} without
     * parsing the node; tests that need to exercise parsing
     * should use the production service instead.
     */
    @Override
    public boolean process(Node response) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> abKey() {
        return Optional.ofNullable(abKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> hash() {
        return Optional.ofNullable(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long refresh() {
        return refreshSeconds != null ? refreshSeconds : 86_400L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> lastSyncTime() {
        return Optional.ofNullable(lastSyncTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAfterFirstSync() {
        return lastSyncTime != null || abKey != null || hash != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime) {
        if (abKey != null) this.abKey = abKey;
        if (hash != null) this.hash = hash;
        if (refreshSeconds != null) this.refreshSeconds = refreshSeconds;
        this.lastSyncTime = lastSyncTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long refreshId() {
        return refreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRefreshId(long refreshId) {
        this.refreshId = refreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long webRefreshId() {
        return webRefreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWebRefreshId(long webRefreshId) {
        this.webRefreshId = webRefreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long groupAbPropsRefreshId() {
        return groupRefreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupAbPropsRefreshId(long groupRefreshId) {
        this.groupRefreshId = groupRefreshId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> groupAbPropsEmergencyPushTimestamp() {
        return Optional.ofNullable(groupEmergencyPushTimestamp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) {
        this.groupEmergencyPushTimestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns empty; tests that need
     * to exercise the group-props branch should stub the
     * production service instead.
     */
    @Override
    public Optional<GroupAbPropsResult> getGroupAbPropsProtocol(Jid groupJid, String propsHash) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation projects {@link #values} into a
     * {@code code -> raw-value} map; the snapshot is wrapped
     * with {@link Collections#unmodifiableMap(Map)}.
     */
    @Override
    public Map<Integer, String> abPropConfigs() {
        var snapshot = new HashMap<Integer, String>();
        values.forEach((prop, value) -> snapshot.put(prop.code(), value));
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean setConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop");
        return accessedConfigs.add(prop.code());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop");
        return accessedConfigs.contains(prop.code());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation ignores {@code waitForSync} because
     * the test stub has no sync future to block on.
     */
    @Override
    public String getString(ABProp prop, boolean waitForSync) {
        Objects.requireNonNull(prop, "prop");
        return values.getOrDefault(prop, prop.defaultValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBool(ABProp prop, boolean waitForSync) {
        return ABProp.toBoolean(getString(prop, waitForSync));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInt(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toInt(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsInt();
        var fallback = ABProp.toInt(prop.defaultValue());
        return fallback.orElse(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLong(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toLong(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsLong();
        var fallback = ABProp.toLong(prop.defaultValue());
        return fallback.orElse(0L);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getDouble(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toDouble(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsDouble();
        var fallback = ABProp.toDouble(prop.defaultValue());
        return fallback.orElse(0.0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OptionalInt getSamplingWeight(int eventCode) {
        var weight = samplingConfigs.get(eventCode);
        return weight != null ? OptionalInt.of(weight) : OptionalInt.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, Integer> samplingConfigs() {
        return Collections.unmodifiableMap(samplingConfigs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateEventSamplingConfigs(Map<Integer, Integer> configs) {
        if (configs == null || configs.isEmpty()) {
            return false;
        }
        samplingConfigs.clear();
        samplingConfigs.putAll(configs);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return values.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation clears the local maps and the stubbed
     * persisted attributes; it leaves the refresh ids in place
     * because production also leaves them.
     */
    @Override
    public void clear() {
        values.clear();
        samplingConfigs.clear();
        accessedConfigs.clear();
        abKey = null;
        hash = null;
        lastSyncTime = null;
    }

    /**
     * Fluent builder for {@link TestABPropsService}.
     *
     * @apiNote
     * Construct via {@link TestABPropsService#builder()} and
     * chain {@code with(...)} calls to seed preset values; call
     * {@link #build()} to produce the configured service.
     *
     * @implNote
     * The builder collects entries in a plain {@link HashMap};
     * order is therefore not preserved across {@code with(...)}
     * calls.
     */
    public static final class Builder {
        /**
         * Accumulator for builder presets.
         */
        private final Map<ABProp, String> values = new HashMap<>();

        /**
         * Sets a boolean preset.
         *
         * @apiNote
         * Encodes the boolean via
         * {@link Boolean#toString(boolean)} before storing it.
         *
         * @param prop  the AB prop
         * @param value the boolean value
         * @return this builder
         */
        public Builder with(ABProp prop, boolean value) {
            values.put(prop, Boolean.toString(value));
            return this;
        }

        /**
         * Sets a numeric preset.
         *
         * @apiNote
         * Encodes the long via {@link Long#toString(long)}
         * before storing it.
         *
         * @param prop  the AB prop
         * @param value the numeric value
         * @return this builder
         */
        public Builder with(ABProp prop, long value) {
            values.put(prop, Long.toString(value));
            return this;
        }

        /**
         * Sets a string preset.
         *
         * @param prop  the AB prop
         * @param value the raw string value
         * @return this builder
         */
        public Builder with(ABProp prop, String value) {
            values.put(prop, value);
            return this;
        }

        /**
         * Builds the configured service.
         *
         * @apiNote
         * Construct one builder per scenario; the returned
         * service holds a copy of the builder's value map, so
         * further {@code with(...)} calls do not mutate the
         * already-built instance.
         *
         * @return the configured service
         */
        public TestABPropsService build() {
            return new TestABPropsService(values);
        }
    }
}
