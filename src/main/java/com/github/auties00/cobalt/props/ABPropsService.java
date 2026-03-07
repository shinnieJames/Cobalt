package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for managing A/B testing properties (AB props) received from WhatsApp servers.
 *
 * <p>AB props are feature flags and configuration values that control client behavior.
 * The service stores synced prop values by their numeric {@code code} and provides
 * type-safe accessors that return non-optional values, falling back to the
 * {@linkplain ABProp#defaultValue() default value} defined by each {@link ABProp} when
 * the server has not provided a value.
 *
 * <p>When querying props before the first sync completes, the query will automatically
 * wait (up to a configurable timeout) for sync to complete before returning. If the
 * timeout expires, the default value is used.
 *
 * <p>This class is thread-safe.
 *
 * @see ABProp
 */
public final class ABPropsService {
    private static final System.Logger LOGGER = System.getLogger(ABPropsService.class.getName());

    /**
     * Default timeout for waiting for initial sync when querying props.
     */
    private static final Duration DEFAULT_SYNC_TIMEOUT = Duration.ofSeconds(30);

    private final WhatsAppClient client;

    /**
     * Thread-safe map storing raw synced AB prop values by their config code.
     * Key: config code (unique identifier)
     * Value: the raw string value received from the server
     */
    private final Map<Integer, String> props;

    /**
     * Current hash of the AB props state, used for delta updates.
     * Null if no props have been synced yet.
     */
    private volatile String currentHash;

    /**
     * Current AB key from the server, used as the WAM {@code abKey2}
     * global attribute (field 4473). Null if no props have been synced
     * yet or the server did not include one.
     */
    private volatile String currentAbKey;

    /**
     * WAM event sampling weight overrides parsed from the AB props
     * response. Each entry maps an event code to a sampling weight.
     * Populated during {@link #process(Node)} from {@code SamplingConfig}
     * entries in the response.
     */
    private final Map<Integer, Integer> samplingConfigs;

    /**
     * Future that completes when sync finishes.
     * Stored in AtomicReference to allow resetting by creating a new future.
     */
    private final AtomicReference<CompletableFuture<Boolean>> syncFuture;

    /**
     * Timeout to wait for initial sync when querying.
     */
    private final Duration syncTimeout;

    /**
     * Creates a new AB props service that will sync props from the server.
     * Uses the default sync timeout {@link #DEFAULT_SYNC_TIMEOUT}.
     *
     * @param client the WhatsApp client instance to use for communication
     */
    public ABPropsService(WhatsAppClient client) {
        this(client, DEFAULT_SYNC_TIMEOUT);
    }

    /**
     * Creates a new AB props service with a custom sync timeout.
     *
     * @param client      the WhatsApp client instance to use for communication
     * @param syncTimeout timeout to wait for sync when querying
     */
    public ABPropsService(WhatsAppClient client, Duration syncTimeout) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.props = new ConcurrentHashMap<>();
        this.samplingConfigs = new ConcurrentHashMap<>();
        this.syncFuture = new AtomicReference<>(new CompletableFuture<>());
        this.syncTimeout = syncTimeout;
    }

    /**
     * Synchronizes AB props from the server.
     *
     * <p>This method sends a sync request to WhatsApp servers and processes the response.
     * On first sync, all props are requested. On subsequent syncs, only the hash is sent
     * to enable delta updates (the server will only send changed props).
     *
     * <p>After sync completes, the sync future is completed, allowing any waiting
     * query operations to proceed.
     *
     * @return {@code true} if sync succeeded, {@code false} otherwise
     */
    public boolean sync() {
        try {
            var request = createSyncRequest();
            var response = client.sendNode(request);
            var success = process(response);
            completeSync(success);
            return success;
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.ERROR, "AB props sync failed: {0}", throwable.getMessage());
            failSync(throwable);
            return false;
        }
    }

    /**
     * Completes the sync future with the given result.
     *
     * <p>This releases all threads waiting in query methods.
     *
     * @param success whether the sync succeeded
     */
    private void completeSync(boolean success) {
        syncFuture.get().complete(success);
    }

    /**
     * Completes the sync future exceptionally.
     *
     * <p>This ensures waiting threads don't hang when sync fails.
     *
     * @param throwable the exception that caused sync to fail
     */
    private void failSync(Throwable throwable) {
        syncFuture.get().completeExceptionally(throwable);
    }

    /**
     * Waits for the initial sync to complete, with a timeout.
     *
     * <p>This is called internally by query methods to ensure props are available
     * before returning results. If the sync hasn't completed yet, this will block
     * until either the sync completes or the timeout expires.
     *
     * @return {@code true} if sync completed successfully before timeout,
     *         {@code false} if timeout occurred or sync failed
     */
    private boolean awaitSync() {
        try {
            var result = syncFuture.get().get(syncTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return result != null && result;
        } catch (TimeoutException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "Timeout waiting for AB props sync");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "Interrupted while waiting for AB props sync");
            return false;
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.WARNING, "Error waiting for AB props sync: {0}", e.getMessage());
            return false;
        }
    }

    /**
     * Creates a WebSocket node request to sync AB props from the server.
     */
    private NodeBuilder createSyncRequest() {
        var propsNode = new NodeBuilder()
                .description("props")
                .attribute("protocol", "1");

        if (currentHash != null) {
            propsNode.attribute("hash", currentHash);
        }

        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "abt")
                .attribute("to", "s.whatsapp.net")
                .attribute("type", "get")
                .content(propsNode.build());
    }

    /**
     * Processes an AB props response received from the server.
     *
     * @param response the server response node
     * @return {@code true} if the response was processed successfully, {@code false} otherwise
     * @throws NullPointerException if {@code response} is {@code null}
     */
    public boolean process(Node response) {
        Objects.requireNonNull(response, "response cannot be null");

        var propsNode = response.getChild("props", null);
        if (propsNode == null) {
            LOGGER.log(System.Logger.Level.WARNING, "AB props response missing <props> node");
            return false;
        }

        // Update hash for future delta updates
        propsNode.getAttributeAsString("hash").ifPresent(hash -> {
            this.currentHash = hash;
            LOGGER.log(System.Logger.Level.DEBUG, "Updated AB props hash: {0}", hash);
        });

        // Update AB key for WAM abKey2 global
        propsNode.getAttributeAsString("ab_key").ifPresent(key -> {
            this.currentAbKey = key;
        });

        // Check if this is a delta update
        var isDelta = propsNode.getAttributeAsBool("delta_update", false);
        if (!isDelta) {
            // Full update - clear existing props
            props.clear();
        }

        // Parse individual prop entries
        var propNodes = propsNode.getChildren("prop");
        var count = 0;
        for (var propNode : propNodes) {
            var configCode = propNode.getAttributeAsInt("config_code");
            if (configCode.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping prop without config_code");
                continue;
            }

            var configValue = propNode.getAttributeAsString("config_value");
            if (configValue.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping prop {0} without config_value", configCode.getAsInt());
                continue;
            }

            props.put(configCode.getAsInt(), configValue.get());
            count++;
        }

        LOGGER.log(System.Logger.Level.INFO, "Synced {0} AB props from server (delta={1})", count, isDelta);

        // Parse SamplingConfig entries for WAM event sampling overrides.
        // In the AB props response, these appear as <prop> nodes with
        // event_code and sampling_weight attributes instead of the
        // standard config_code/config_value pair.
        var samplingNodes = propsNode.getChildren("sampling_config");
        if (!samplingNodes.isEmpty()) {
            samplingConfigs.clear();
            for (var samplingNode : samplingNodes) {
                var eventCode = samplingNode.getAttributeAsInt("event_code");
                var samplingWeight = samplingNode.getAttributeAsInt("sampling_weight");
                if (eventCode.isPresent() && samplingWeight.isPresent()) {
                    samplingConfigs.put(eventCode.getAsInt(), samplingWeight.getAsInt());
                }
            }
        }

        return true;
    }

    /**
     * Returns the current AB key from the server.
     *
     * <p>This value is written as the WAM {@code abKey2} global
     * attribute (field 4473) on the {@code "regular"} channel, unless
     * the {@code wam_disable_abkey_attribute} AB prop is enabled.
     *
     * @return an {@code Optional} containing the AB key string, or
     *         empty if the server did not include one in the response
     */
    public Optional<String> abKey() {
        return Optional.ofNullable(currentAbKey);
    }

    /**
     * Returns the raw string value for the given AB prop.
     *
     * <p>If the server has not provided a value for this prop, the fallback depends on
     * whether the user has joined the WhatsApp Web Beta programme: if so,
     * {@linkplain ABProp#debugDefaultValue() debugDefaultValue} is used; otherwise
     * {@linkplain ABProp#defaultValue() defaultValue} is used. If the initial sync
     * hasn't completed, this method will wait (up to the configured timeout) for the sync
     * to complete before querying.
     *
     * @param prop the AB prop definition
     * @return the string value from the server, or the appropriate default value if not available
     */
    public String getString(ABProp prop) {
        return getString(prop, true);
    }

    /**
     * Returns the raw string value for the given AB prop.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to wait for the A/B props data to arrive from the server
     * @return the string value from the server, or the default value if not available
     */
    public String getString(ABProp prop, boolean waitForSync) {
        if (waitForSync) {
            awaitSync();
        }
        var defaultValue = client.store().externalWebBeta() ? prop.debugDefaultValue() : prop.defaultValue();
        return props.getOrDefault(prop.code(), defaultValue);
    }

    /**
     * Returns the boolean value for the given AB prop.
     *
     * <p>If the server has not provided a value for this prop, the
     * {@linkplain ABProp#defaultValue() default value} is parsed as a boolean.
     * If the initial sync hasn't completed, this method will wait (up to the configured
     * timeout) for the sync to complete before querying.
     *
     * @param prop the AB prop definition
     * @return the boolean value from the server, or the default value if not available
     * @see ABProp#toBoolean(String)
     */
    public boolean getBool(ABProp prop) {
        return getBool(prop, true);
    }

    /**
     * Returns the boolean value for the given AB prop.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to wait for the A/B props data to arrive from the server
     * @return the boolean value from the server, or the default value if not available
     * @see ABProp#toBoolean(String)
     */
    public boolean getBool(ABProp prop, boolean waitForSync) {
        return ABProp.toBoolean(getString(prop, waitForSync));
    }

    /**
     * Returns the integer value for the given AB prop.
     *
     * <p>If the server has not provided a value for this prop, the
     * {@linkplain ABProp#defaultValue() default value} is parsed as an integer.
     * If neither the synced value nor the default value can be parsed as an integer,
     * {@code 0} is returned. If the initial sync hasn't completed, this method will
     * wait (up to the configured timeout) for the sync to complete before querying.
     *
     * @param prop the AB prop definition
     * @return the integer value from the server, or the default value if not available
     * @see ABProp#toInt(String)
     */
    public int getInt(ABProp prop) {
        return getInt(prop, true);
    }

    /**
     * Returns the integer value for the given AB prop.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to wait for the A/B props data to arrive from the server
     * @return the integer value from the server, or the default value if not available
     * @see ABProp#toInt(String)
     */
    public int getInt(ABProp prop, boolean waitForSync) {
        var value = getString(prop, waitForSync);
        var result = ABProp.toInt(value);
        if (result.isPresent()) {
            return result.getAsInt();
        }
        var fallback = ABProp.toInt(prop.defaultValue());
        return fallback.orElse(0);
    }

    /**
     * Returns the long value for the given AB prop.
     *
     * <p>If the server has not provided a value for this prop, the
     * {@linkplain ABProp#defaultValue() default value} is parsed as a long.
     * If neither the synced value nor the default value can be parsed as a long,
     * {@code 0L} is returned. If the initial sync hasn't completed, this method will
     * wait (up to the configured timeout) for the sync to complete before querying.
     *
     * @param prop the AB prop definition
     * @return the long value from the server, or the default value if not available
     * @see ABProp#toLong(String)
     */
    public long getLong(ABProp prop) {
        return getLong(prop, true);
    }

    /**
     * Returns the long value for the given AB prop.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to wait for the A/B props data to arrive from the server
     * @return the long value from the server, or the default value if not available
     * @see ABProp#toLong(String)
     */
    public long getLong(ABProp prop, boolean waitForSync) {
        var value = getString(prop, waitForSync);
        var result = ABProp.toLong(value);
        if (result.isPresent()) {
            return result.getAsLong();
        }
        var fallback = ABProp.toLong(prop.defaultValue());
        return fallback.orElse(0L);
    }

    /**
     * Returns the double (floating-point) value for the given AB prop.
     *
     * <p>If the server has not provided a value for this prop, the
     * {@linkplain ABProp#defaultValue() default value} is parsed as a double.
     * If neither the synced value nor the default value can be parsed as a double,
     * {@code 0.0} is returned. If the initial sync hasn't completed, this method will
     * wait (up to the configured timeout) for the sync to complete before querying.
     *
     * @param prop the AB prop definition
     * @return the double value from the server, or the default value if not available
     * @see ABProp#toDouble(String)
     */
    public double getDouble(ABProp prop) {
        return getDouble(prop, true);
    }

    /**
     * Returns the double (floating-point) value for the given AB prop.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to wait for the A/B props data to arrive from the server
     * @return the double value from the server, or the default value if not available
     * @see ABProp#toDouble(String)
     */
    public double getDouble(ABProp prop, boolean waitForSync) {
        var value = getString(prop, waitForSync);
        var result = ABProp.toDouble(value);
        if (result.isPresent()) {
            return result.getAsDouble();
        }
        var fallback = ABProp.toDouble(prop.defaultValue());
        return fallback.orElse(0.0);
    }

    /**
     * Returns the overridden sampling weight for the given WAM event
     * code, or empty if no override was received from the server.
     *
     * @param eventCode the numeric WAM event identifier
     * @return an {@code OptionalInt} containing the override weight, or
     *         empty if no override exists for this event
     */
    public OptionalInt getSamplingWeight(int eventCode) {
        var weight = samplingConfigs.get(eventCode);
        return weight != null ? OptionalInt.of(weight) : OptionalInt.empty();
    }

    /**
     * Returns an unmodifiable copy of all WAM event sampling weight
     * overrides parsed from the most recent AB props sync.
     *
     * <p>Each entry maps a WAM event code to its overridden sampling
     * weight. Returns an empty map if no overrides were received.
     *
     * @return a defensive copy of the sampling configs map
     */
    public Map<Integer, Integer> samplingConfigs() {
        return Map.copyOf(samplingConfigs);
    }

    /**
     * Returns the number of AB props currently stored.
     *
     * @return the count of synced props
     */
    public int size() {
        return props.size();
    }

    /**
     * Checks if any AB props have been synced.
     *
     * @return {@code true} if props are available, {@code false} if no sync has occurred
     */
    public boolean isEmpty() {
        return props.isEmpty();
    }

    /**
     * Clears all stored AB props and resets the sync future.
     *
     * <p>This is typically called when disconnecting or resetting the session.
     * After calling this method, a new sync must be performed and queries will
     * wait for the new sync to complete.
     *
     * <p>This method is fully resettable — the sync future is replaced with a new instance,
     * allowing subsequent syncs to work correctly.
     */
    public void clear() {
        props.clear();
        samplingConfigs.clear();
        currentHash = null;
        currentAbKey = null;
        syncFuture.set(new CompletableFuture<>());
        LOGGER.log(System.Logger.Level.DEBUG, "Cleared all AB props and reset sync state");
    }
}
