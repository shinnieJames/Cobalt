package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

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
 * <p>Holds a mutable map of {@code ABProp -> value} so individual tests
 * can dial specific feature flags up or down without standing up a real
 * AB-props sync. Every accessor falls back to {@link ABProp#defaultValue()}
 * when the prop has not been explicitly set, so tests that don't care
 * about a particular prop don't need to configure it.
 *
 * <p>The sync, sampling, persistence, and group-props branches are stubbed
 * out: {@link #sync(Long, boolean)} is a no-op that returns {@code true};
 * {@link #process(Node)} returns {@code true}; mutators that would
 * normally write to the store update local fields. This matches what the
 * device-package code actually exercises — the value accessors.
 *
 * <p>Construct via {@link #builder()} for the common case, or pass an
 * empty map for the "defaults for everything" baseline:
 * <pre>{@code
 * var props = TestABPropsService.builder()
 *         .with(ABProp.ADV_ACCEPT_HOSTED_DEVICES, true)
 *         .with(ABProp.USERNAME_CONTACT_DISPLAY, false)
 *         .build();
 * }</pre>
 */
public final class TestABPropsService implements ABPropsService {
    private final Map<ABProp, String> values;
    private final Map<Integer, Integer> samplingConfigs;
    private final Set<Integer> accessedConfigs;
    private String abKey;
    private String hash;
    private Long refreshSeconds;
    private Instant lastSyncTime;
    private long refreshId;
    private long webRefreshId;
    private long groupRefreshId;
    private Instant groupEmergencyPushTimestamp;

    /**
     * Constructs a service holding the given preset values.
     *
     * @param values the preset values, in raw-string form
     */
    public TestABPropsService(Map<ABProp, String> values) {
        this.values = new HashMap<>(Objects.requireNonNull(values, "values"));
        this.samplingConfigs = new HashMap<>();
        this.accessedConfigs = new HashSet<>();
    }

    /**
     * Returns a builder for assembling preset values fluently.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Replaces the preset value for {@code prop}.
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
     * Replaces the preset value for {@code prop} with a boolean.
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
     * @param prop  the AB prop
     * @param value the numeric value
     * @return this service for fluent chaining
     */
    public TestABPropsService set(ABProp prop, long value) {
        values.put(prop, Long.toString(value));
        return this;
    }

    @Override
    public boolean sync(Long localRefreshId, boolean shouldSendHash) {
        return true;
    }

    @Override
    public boolean syncABProps(Long localRefreshId, boolean shouldSendHash) {
        return true;
    }

    @Override
    public boolean process(Node response) {
        return true;
    }

    @Override
    public Optional<String> abKey() {
        return Optional.ofNullable(abKey);
    }

    @Override
    public Optional<String> hash() {
        return Optional.ofNullable(hash);
    }

    @Override
    public long refresh() {
        return refreshSeconds != null ? refreshSeconds : 86_400L;
    }

    @Override
    public Optional<Instant> lastSyncTime() {
        return Optional.ofNullable(lastSyncTime);
    }

    @Override
    public boolean isAfterFirstSync() {
        return lastSyncTime != null || abKey != null || hash != null;
    }

    @Override
    public void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime) {
        if (abKey != null) this.abKey = abKey;
        if (hash != null) this.hash = hash;
        if (refreshSeconds != null) this.refreshSeconds = refreshSeconds;
        this.lastSyncTime = lastSyncTime;
    }

    @Override
    public long refreshId() {
        return refreshId;
    }

    @Override
    public void setRefreshId(long refreshId) {
        this.refreshId = refreshId;
    }

    @Override
    public long webRefreshId() {
        return webRefreshId;
    }

    @Override
    public void setWebRefreshId(long webRefreshId) {
        this.webRefreshId = webRefreshId;
    }

    @Override
    public long groupAbPropsRefreshId() {
        return groupRefreshId;
    }

    @Override
    public void setGroupAbPropsRefreshId(long groupRefreshId) {
        this.groupRefreshId = groupRefreshId;
    }

    @Override
    public Optional<Instant> groupAbPropsEmergencyPushTimestamp() {
        return Optional.ofNullable(groupEmergencyPushTimestamp);
    }

    @Override
    public void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) {
        this.groupEmergencyPushTimestamp = timestamp;
    }

    @Override
    public Optional<GroupAbPropsResult> getGroupAbPropsProtocol(Jid groupJid, String propsHash) {
        return Optional.empty();
    }

    @Override
    public Map<Integer, String> abPropConfigs() {
        var snapshot = new HashMap<Integer, String>();
        values.forEach((prop, value) -> snapshot.put(prop.code(), value));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public boolean setConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop");
        return accessedConfigs.add(prop.code());
    }

    @Override
    public boolean isConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop");
        return accessedConfigs.contains(prop.code());
    }

    @Override
    public String getString(ABProp prop, boolean waitForSync) {
        Objects.requireNonNull(prop, "prop");
        return values.getOrDefault(prop, prop.defaultValue());
    }

    @Override
    public boolean getBool(ABProp prop, boolean waitForSync) {
        return ABProp.toBoolean(getString(prop, waitForSync));
    }

    @Override
    public int getInt(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toInt(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsInt();
        var fallback = ABProp.toInt(prop.defaultValue());
        return fallback.orElse(0);
    }

    @Override
    public long getLong(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toLong(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsLong();
        var fallback = ABProp.toLong(prop.defaultValue());
        return fallback.orElse(0L);
    }

    @Override
    public double getDouble(ABProp prop, boolean waitForSync) {
        var raw = ABProp.toDouble(getString(prop, waitForSync));
        if (raw.isPresent()) return raw.getAsDouble();
        var fallback = ABProp.toDouble(prop.defaultValue());
        return fallback.orElse(0.0);
    }

    @Override
    public OptionalInt getSamplingWeight(int eventCode) {
        var weight = samplingConfigs.get(eventCode);
        return weight != null ? OptionalInt.of(weight) : OptionalInt.empty();
    }

    @Override
    public Map<Integer, Integer> samplingConfigs() {
        return Collections.unmodifiableMap(samplingConfigs);
    }

    @Override
    public boolean updateEventSamplingConfigs(Map<Integer, Integer> configs) {
        if (configs == null || configs.isEmpty()) {
            return false;
        }
        samplingConfigs.clear();
        samplingConfigs.putAll(configs);
        return true;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

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
     */
    public static final class Builder {
        private final Map<ABProp, String> values = new HashMap<>();

        /**
         * Sets a boolean preset.
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
         * Builds the service.
         *
         * @return the configured service
         */
        public TestABPropsService build() {
            return new TestABPropsService(values);
        }
    }
}
