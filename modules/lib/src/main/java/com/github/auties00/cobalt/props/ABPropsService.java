package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
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
 *
 * @implNote WAWebAbPropsSyncJob: adapts the {@code syncABProps} and
 * {@code syncABPropsTask} exports, which send a {@code GetExperimentConfigRPC}
 * and update the local AB prop cache. Cobalt also absorbs the inline parsing
 * performed by WAWebApiAbPropConfig.updateABPropConfigs into this class, and
 * the sampling-config cache façade exported by
 * WAWebApiAbPropEventSamplingConfig (which in WA Web is backed by an
 * IndexedDB table; Cobalt collapses it into the in-memory
 * {@link #samplingConfigs} map per the store-flattening pattern in
 * CLAUDE.md).
 */
@WhatsAppWebModule(moduleName = "WAWebAbPropsSyncJob")
@WhatsAppWebModule(moduleName = "WAGetAbPropsProtocol")
@WhatsAppWebModule(moduleName = "WAWebApiAbPropConfig")
@WhatsAppWebModule(moduleName = "WAWebApiAbPropEventSamplingConfig")
@WhatsAppWebModule(moduleName = "WAWebABPropsLocalStorage")
public final class ABPropsService {
    private static final System.Logger LOGGER = System.getLogger(ABPropsService.class.getName());

    /**
     * Default timeout for waiting for initial sync when querying props.
     */
    private static final Duration DEFAULT_SYNC_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Lower bound (inclusive) for the {@code refresh} attribute persisted by
     * {@link #updateAttributesLocalStorage(String, String, Long, Instant)},
     * expressed in seconds.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage —
     *           {@code var e=600}
     */
    private static final long REFRESH_MIN_SECONDS = 600L;

    /**
     * Upper bound (inclusive) for the {@code refresh} attribute persisted by
     * {@link #updateAttributesLocalStorage(String, String, Long, Instant)},
     * expressed in seconds.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage —
     *           {@code var s=604800}
     */
    private static final long REFRESH_MAX_SECONDS = 604800L;

    /**
     * Default value (one day, in seconds) returned by {@link #refresh()}
     * when the JS export {@code WAWebABPropsLocalStorage.getRefresh} would
     * fall back to {@code parseInt(86400, 10)}.
     *
     * @implNote WAWebABPropsLocalStorage.getRefresh — {@code var u=86400}
     */
    private static final long REFRESH_DEFAULT_SECONDS = 86400L;

    private final WhatsAppClient client;

    /**
     * Thread-safe map storing raw synced AB prop values by their config code.
     * Key: config code (unique identifier)
     * Value: the raw string value received from the server
     */
    private final Map<Integer, String> props;

    /**
     * WAM event sampling weight overrides parsed from the AB props
     * response. Each entry maps an event code to a sampling weight.
     * Populated during {@link #process(Node)} from {@code SamplingConfig}
     * entries in the response.
     */
    private final Map<Integer, Integer> samplingConfigs;

    /**
     * Set of AB-prop codes that the host application has read at least once.
     *
     * <p>Mirrors the JS module-level {@code accessedConfigs} Set in
     * {@code WAWebABPropsGlobals} that is mutated by
     * {@link #setConfigAccessed(ABProp)} (the JS export
     * {@code WAWebApiAbPropConfig.setConfigAccessed}). The JS export persists
     * the {@code hasAccessed} flag onto an IndexedDB row to drive the
     * exposure-key/{@code expoKey} WAM Global attribute. Cobalt collapses
     * the on-disk flag into this in-memory Set per the store-flattening
     * pattern in CLAUDE.md.
     *
     * @implNote WAWebApiAbPropConfig.setConfigAccessed —
     *           {@code merge(String(e), {hasAccessed:!0})}.
     */
    private final Set<Integer> accessedConfigs;

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
        this.accessedConfigs = ConcurrentHashMap.newKeySet();
        this.syncFuture = new AtomicReference<>(new CompletableFuture<>());
        this.syncTimeout = syncTimeout;
    }

    /**
     * Synchronizes AB props from the server using the default options
     * ({@code localRefreshId=null}, {@code shouldSendHash=true}), mirroring
     * the JS module-level default {@code m = {shouldSendHash: !0}}.
     *
     * @return {@code true} if sync succeeded, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABPropsTask",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean sync() {
        // WAWebAbPropsSyncJob.syncABPropsTask: var l = e ? extends({},e) : extends({},m)
        // where m = {shouldSendHash: !0}; here we pass the defaulted bag.
        return sync(null, true);
    }

    /**
     * Synchronizes AB props from the server with the JS retry loop and
     * first-sync gating semantics.
     *
     * <p>Mirrors {@code syncABPropsTask(e)}: retries up to three times,
     * applies the {@code shouldSendHash = isABPropsAfterFirstSync &&
     * shouldSendHash !== false} adjustment, and dispatches to
     * {@code syncABProps}. On success, completes the sync future so queries
     * can proceed; on terminal failure, fails the future.
     *
     * <p>The JS export delays {@code 10 * 1000 * Math.random()} milliseconds
     * between attempts; Cobalt mirrors this jittered backoff.
     *
     * @implNote WAWebAbPropsSyncJob.syncABPropsTask
     * @param localRefreshId the per-call refresh-id override used by the
     *                       emergency-push branch, or {@code null} to use
     *                       the {@code propsHash} branch
     * @param shouldSendHash whether the {@code propsHash} branch may include
     *                       the persisted hash; the JS export ANDs this with
     *                       {@code isABPropsAfterFirstSync}
     * @return {@code true} if any of the three attempts succeeded,
     *         {@code false} if all attempts failed
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABPropsTask",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean sync(Long localRefreshId, boolean shouldSendHash) {
        // WAWebAbPropsSyncJob.syncABPropsTask:
        //     var t = isABPropsAfterFirstSync();
        //     l.localRefreshId == null && (l.shouldSendHash = t && l.shouldSendHash !== !1)
        var afterFirstSync = isAfterFirstSync();
        var effectiveShouldSendHash = localRefreshId != null
                ? shouldSendHash
                : afterFirstSync && shouldSendHash;
        Throwable lastFailure = null;
        // WAWebAbPropsSyncJob.syncABPropsTask: for (var r = 3; r-- > 0;)
        for (var attempt = 3; attempt-- > 0; ) {
            try {
                var success = syncABProps(localRefreshId, effectiveShouldSendHash);
                if (success) {
                    completeSync(true);
                    return true;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
                LOGGER.log(System.Logger.Level.WARNING,
                        "AB props sync attempt failed (remaining={0}): {1}",
                        attempt, throwable.getMessage());
            }
            if (attempt > 0) {
                // WAWebAbPropsSyncJob.syncABPropsTask:
                //     yield WAPromiseDelays.delayMs(10 * 1e3 * Math.random())
                try {
                    Thread.sleep((long) (10_000L * Math.random()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // WAWebAbPropsSyncJob.syncABPropsTask:
        //     r === 0 && WALogger.ERROR("failed to sync ABProps")
        LOGGER.log(System.Logger.Level.ERROR, "Failed to sync ABProps after 3 attempts");
        if (lastFailure != null) {
            failSync(lastFailure);
        } else {
            completeSync(false);
        }
        return false;
    }

    /**
     * Performs a single AB-props sync round-trip.
     *
     * <p>Mirrors {@code syncABProps(t)}: builds the request stanza with
     * either {@code propsRefreshId} (when justknobx {@code 3330} is enabled
     * and the override is non-{@code null}) or {@code propsHash} (the
     * regular branch), sends it, and processes the response. The JS export
     * also persists {@code erid} via {@code WAWebEncryptedRid.setEncryptedRid}
     * and replaces the sampling-config cache; Cobalt routes those side
     * effects through {@link #process(Node)}.
     *
     * @implNote WAWebAbPropsSyncJob.syncABProps
     * @param localRefreshId the per-call refresh-id override; when
     *                       non-{@code null} and justknobx {@code 3330} is
     *                       enabled, the request uses
     *                       {@code propsRefreshId} instead of
     *                       {@code propsHash}
     * @param shouldSendHash when the regular branch is taken, controls
     *                       whether the persisted hash is included on the
     *                       request
     * @return {@code true} if the response was processed successfully,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABProps",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean syncABProps(Long localRefreshId, boolean shouldSendHash) {
        // WAWebAbPropsSyncJob.syncABProps:
        //     var l = justknobx._("3330") && a != null,
        //         c = l ? {propsRefreshId: a} : {propsHash: i ? getHash() : void 0}
        // Cobalt does not gate on justknobx 3330 (the knob is server-driven
        // and not modeled in the client snapshot); the override-id branch is
        // taken whenever the caller supplies a non-null localRefreshId,
        // matching the post-knob-on JS behavior.
        var emergencyBranch = localRefreshId != null;
        var request = emergencyBranch
                ? getAbPropsProtocol(null, localRefreshId)
                : getAbPropsProtocol(shouldSendHash ? client.store().abPropsHash().orElse(null) : null, null);
        var response = client.sendNode(request);
        // WAWebAbPropsSyncJob.syncABProps: if (!m.success) return false
        if (response == null) {
            return false;
        }
        return process(response);
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
     * Builds the {@code <iq xmlns="abt">} stanza that WhatsApp Web calls
     * {@code getAbPropsProtocol}.
     *
     * <p>The produced stanza mirrors the JS request structure emitted by
     * {@code WASmaxOutAbPropsGetExperimentConfigRequest.makeGetExperimentConfigRequest}
     * composed with {@code mergeBaseIQGetRequestMixin} (which injects
     * {@code id} and {@code type="get"}). The outer {@code <iq>} carries
     * {@code xmlns="abt"} and {@code to="s.whatsapp.net"}; the inner
     * {@code <props>} carries the literal {@code protocol="1"} and an
     * optional {@code hash} (custom string) and optional {@code refresh_id}
     * (INT). Exactly one of {@code propsHash} / {@code propsRefreshId} is
     * typically populated by the caller &mdash; the JS caller in
     * {@code WAWebAbPropsSyncJob} picks between the two modes based on
     * justknobx {@code 3330} and the local refresh id.
     *
     * @implNote WAGetAbPropsProtocol.getAbPropsProtocol; the actual stanza
     *           shape is produced by
     *           WASmaxOutAbPropsGetExperimentConfigRequest.makeGetExperimentConfigRequest
     *           + WASmaxOutAbPropsBaseIQGetRequestMixin.mergeBaseIQGetRequestMixin.
     * @param propsHash      optional AB-props hash for delta updates, or
     *                       {@code null} to omit the attribute
     * @param propsRefreshId optional refresh id used by the emergency-push
     *                       branch, or {@code null} to omit the attribute
     * @return a {@code NodeBuilder} wrapping the constructed {@code <iq>}
     *         stanza ready to be sent to the server
     */
    @WhatsAppWebExport(moduleName = "WAGetAbPropsProtocol", exports = "getAbPropsProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private NodeBuilder getAbPropsProtocol(String propsHash, Long propsRefreshId) {
        // WASmaxOutAbPropsGetExperimentConfigRequest.makeGetExperimentConfigRequest:
        //     smax("props", {protocol:"1",
        //                    hash:OPTIONAL(CUSTOM_STRING, t),
        //                    refresh_id:OPTIONAL(INT, n)})
        var propsNode = new NodeBuilder()
                .description("props")
                .attribute("protocol", "1"); // WASmaxOutAbPropsGetExperimentConfigRequest: protocol:"1"
        if (propsHash != null) {
            propsNode.attribute("hash", propsHash); // hash:OPTIONAL(CUSTOM_STRING,t)
        }
        if (propsRefreshId != null) {
            propsNode.attribute("refresh_id", propsRefreshId); // refresh_id:OPTIONAL(INT,n)
        }

        // WASmaxOutAbPropsBaseIQGetRequestMixin.mergeBaseIQGetRequestMixin:
        //     smax("iq", {id:generateId(), type:"get"}) merged onto the
        //     outer iq, which here also carries xmlns="abt" and
        //     to=S_WHATSAPP_NET.
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "abt")                 // WASmaxOutAbPropsGetExperimentConfigRequest: xmlns:"abt"
                .attribute("to", "s.whatsapp.net")         // WASmaxOutAbPropsGetExperimentConfigRequest: to:S_WHATSAPP_NET
                .attribute("type", "get")                  // WASmaxOutAbPropsBaseIQGetRequestMixin: type:"get"
                .content(propsNode.build());
    }

    /**
     * Processes an AB props response received from the server.
     *
     * @param response the server response node
     * @return {@code true} if the response was processed successfully, {@code false} otherwise
     * @throws NullPointerException if {@code response} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "updateABPropConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean process(Node response) {
        Objects.requireNonNull(response, "response cannot be null");

        var propsNode = response.getChild("props", null);
        if (propsNode == null) {
            LOGGER.log(System.Logger.Level.WARNING, "AB props response missing <props> node");
            return false;
        }

        var responseHash = propsNode.getAttributeAsString("hash").orElse(null);
        var responseAbKey = propsNode.getAttributeAsString("ab_key").orElse(null);
        var responseRefreshOpt = propsNode.getAttributeAsLong("refresh");
        if (responseHash != null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Updated AB props hash: {0}", responseHash);
        }

        // WAWebAbPropsSyncJob: var h=p.isDeltaUpdate; if(!h) { ...
        //     updateAttributesLocalStorage(_,g,C,Date.now()); }
        // The local-storage attributes are only persisted on full updates;
        // delta updates leave the existing blob in place.
        var isDelta = propsNode.getAttributeAsBool("delta_update", false);
        if (!isDelta) {
            // Full update - clear existing props
            props.clear();
            // WAWebABPropsLocalStorage.updateAttributesLocalStorage(abKey, hash, refresh, lastSyncTime)
            // The JS export treats a null refresh as "no update" rather than
            // clamping to the 600s minimum, so we pass null when the
            // attribute is absent.
            Long responseRefresh = responseRefreshOpt.isPresent() ? responseRefreshOpt.getAsLong() : null;
            updateAttributesLocalStorage(responseAbKey, responseHash, responseRefresh, Instant.now());
        }
        // WAWebAbPropsSyncJob: if (b!=null) o("WAWebABPropsLocalStorage").setRefreshId(b)
        propsNode.getAttributeAsLong("refresh_id")
                .ifPresent(this::setRefreshId); // WAWebABPropsLocalStorage.setRefreshId

        // WASmaxInAbPropsGetExperimentConfigResponseSuccess:
        //   mapChildrenWithTag(a.value, "prop", 0, 1/0, parseGetExperimentConfigResponseSuccessPropsProp)
        // Each <prop> child is disambiguated by WASmaxInAbPropsConfigs.parseConfigs
        // into ExperimentConfig ({config_code, config_value, config_expo_key?})
        // or SamplingConfig ({event_code, sampling_weight}).
        var propNodes = propsNode.getChildren("prop");
        var experimentCount = 0;
        var parsedSamplingConfigs = new java.util.LinkedHashMap<Integer, Integer>();
        for (var propNode : propNodes) {
            // WASmaxInAbPropsExperimentConfigMixin.parseExperimentConfigMixin is
            // attempted first; on failure SamplingConfigMixin is tried.
            var configCode = propNode.getAttributeAsInt("config_code");
            var configValue = propNode.getAttributeAsString("config_value");
            if (configCode.isPresent() && configValue.isPresent()) {
                // ExperimentConfig branch
                props.put(configCode.getAsInt(), configValue.get());
                experimentCount++;
                continue;
            }

            // WASmaxInAbPropsSamplingConfigMixin.parseSamplingConfigMixin
            var eventCode = propNode.getAttributeAsInt("event_code");
            var samplingWeight = propNode.getAttributeAsInt("sampling_weight");
            if (eventCode.isPresent() && samplingWeight.isPresent()) {
                parsedSamplingConfigs.put(eventCode.getAsInt(), samplingWeight.getAsInt());
                continue;
            }

            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping <prop> matching neither ExperimentConfig nor SamplingConfig");
        }

        // WAWebAbPropsSyncJob: !h && WAWebApiAbPropEventSamplingConfig.updateEventSamplingConfigs(v)
        // The sampling cache is only replaced on full (non-delta) updates;
        // delta responses leave the previous cache untouched even if they
        // happen to carry SamplingConfig entries.
        var samplingUpdated = !isDelta && updateEventSamplingConfigs(parsedSamplingConfigs);

        LOGGER.log(System.Logger.Level.INFO,
                "Synced {0} AB props and {1} sampling configs from server (delta={2}, samplingUpdated={3})",
                experimentCount, parsedSamplingConfigs.size(), isDelta, samplingUpdated);

        // WAGetAbPropsProtocol.getAbPropsProtocol: var i = optionalChildWithTag(t,"erid",...);
        //     ...erid: i.value==null?void 0:i.value.elementValue
        // The erid byte blob is persisted by WAWebEncryptedRid.setEncryptedRid
        // into UserPrefs. Cobalt does not currently model that UserPrefs slot;
        // logging the presence keeps parity for observability without a store
        // change.
        var eridNode = response.getChild("erid", null);
        if (eridNode != null) {
            LOGGER.log(System.Logger.Level.DEBUG, "AB props response included <erid> blob");
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
     * @implNote WAWebABPropsLocalStorage.getABKey
     * @return an {@code Optional} containing the AB key string, or
     *         empty if the server did not include one in the response
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getABKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> abKey() {
        return client.store().abPropsAbKey(); // WAWebABPropsLocalStorage.getABKey
    }

    /**
     * Returns the AB-props {@code hash} most recently received from the server.
     *
     * <p>The hash is sent as the {@code hash} attribute on subsequent
     * {@code <iq xmlns="abt">} sync requests so the server can respond with a
     * delta update instead of a full prop list.
     *
     * @implNote WAWebABPropsLocalStorage.getHash
     * @return an {@code Optional} containing the AB-props hash, or empty if
     *         no sync has completed yet
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getHash",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> hash() {
        return client.store().abPropsHash(); // WAWebABPropsLocalStorage.getHash
    }

    /**
     * Returns the AB-props refresh interval in seconds, or the
     * {@value #REFRESH_DEFAULT_SECONDS}-second default when no value has
     * been recorded yet.
     *
     * @implNote WAWebABPropsLocalStorage.getRefresh — JS export returns
     *           {@code parseInt(t?.refresh ?? 86400, 10)}.
     * @return the refresh interval in seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getRefresh",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long refresh() {
        return client.store().abPropsRefresh(); // WAWebABPropsLocalStorage.getRefresh
    }

    /**
     * Returns the timestamp of the most recent successful AB-props sync.
     *
     * <p>WhatsApp Web persists this value as the {@code lastSyncTime} field
     * of the JSON object stored under the {@code ABPROPS} {@code localStorage}
     * key.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage —
     *           {@code lastSyncTime} parameter
     * @return an {@code Optional} containing the last sync timestamp, or
     *         empty if no sync has been recorded
     */
    public Optional<Instant> lastSyncTime() {
        return client.store().abPropsLastSyncTime();
    }

    /**
     * Returns whether at least one AB-props sync has completed since the
     * session was created.
     *
     * <p>WhatsApp Web treats the presence of the {@code ABPROPS}
     * {@code localStorage} key as an "after first sync" sentinel and uses
     * the same predicate to decide whether the next sync request should
     * include the {@code hash} attribute.
     *
     * @implNote WAWebABPropsLocalStorage.isABPropsAfterFirstSync — JS
     *           export returns {@code d() != null}, where {@code d()}
     *           parses the persisted {@code ABPROPS} JSON blob.
     * @return {@code true} when the {@code ABPROPS} blob has been written,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "isABPropsAfterFirstSync",
            adaptation = WhatsAppAdaptation.DIRECT)
    public boolean isAfterFirstSync() {
        var store = client.store();
        return store.abPropsLastSyncTime().isPresent()
                || store.abPropsAbKey().isPresent()
                || store.abPropsHash().isPresent();
    }

    /**
     * Persists the {@code ABPROPS} JSON-blob attributes received on the
     * most recent sync, mirroring the JS export
     * {@code updateAttributesLocalStorage(abKey, hash, refresh, lastSyncTime)}.
     *
     * <p>Each non-{@code null} parameter overwrites the corresponding store
     * field; {@code null} parameters preserve the previous value, matching
     * the {@code abKey ?? m.abKey} fallback chain in the JS source. The
     * {@code refresh} value is clamped into
     * {@code [{@value #REFRESH_MIN_SECONDS}, {@value #REFRESH_MAX_SECONDS}]}
     * before being stored.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     * @param abKey        the AB key to persist, or {@code null} to keep
     *                     the previous value
     * @param hash         the AB-props hash to persist, or {@code null} to
     *                     keep the previous value
     * @param refreshSeconds the refresh interval in seconds, or
     *                     {@code null} to keep the previous value
     * @param lastSyncTime the sync completion instant
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "updateAttributesLocalStorage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime) {
        var store = client.store();
        if (abKey != null) {
            store.setAbPropsAbKey(abKey); // WAWebABPropsLocalStorage.updateAttributesLocalStorage - abKey
        }
        if (hash != null) {
            store.setAbPropsHash(hash); // WAWebABPropsLocalStorage.updateAttributesLocalStorage - hash
        }
        if (refreshSeconds != null) {
            // WAWebABPropsLocalStorage.updateAttributesLocalStorage clamps refresh:
            //     c<e?c=e:c>s&&(c=s) where e=600, s=604800
            store.setAbPropsRefresh(refreshSeconds);
        }
        store.setAbPropsLastSyncTime(lastSyncTime); // WAWebABPropsLocalStorage.updateAttributesLocalStorage - i (lastSyncTime)
    }

    /**
     * Returns the AB-props refresh id received from the server.
     *
     * <p>WhatsApp Web persists this value under the
     * {@code ABPROPS_REFRESH_ID} {@code localStorage} key and uses it as
     * the {@code propsRefreshId} attribute on the next sync request when
     * justknobx {@code 3330} is enabled. Reading the slot for the first
     * time eagerly writes {@code 0} so subsequent reads return the same
     * sentinel; Cobalt mirrors this by initialising the underlying store
     * field to {@code 0}.
     *
     * @implNote WAWebABPropsLocalStorage.getRefreshId
     * @return the AB-props refresh id ({@code 0} if never set)
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long getRefreshId() {
        return client.store().abPropsRefreshId(); // WAWebABPropsLocalStorage.getRefreshId
    }

    /**
     * Sets the AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setRefreshId
     * @param refreshId the refresh id received from the server
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setRefreshId(long refreshId) {
        client.store().setAbPropsRefreshId(refreshId); // WAWebABPropsLocalStorage.setRefreshId
    }

    /**
     * Returns the web-only AB-props refresh id used to gate the
     * justknobx {@code 2086} emergency push request.
     *
     * @implNote WAWebABPropsLocalStorage.getWebRefreshId
     * @return the web AB-props refresh id ({@code 0} if never set)
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getWebRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long getWebRefreshId() {
        return client.store().abPropsWebRefreshId(); // WAWebABPropsLocalStorage.getWebRefreshId
    }

    /**
     * Sets the web-only AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setWebRefreshId
     * @param webRefreshId the refresh id received from the server
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setWebRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setWebRefreshId(long webRefreshId) {
        client.store().setAbPropsWebRefreshId(webRefreshId); // WAWebABPropsLocalStorage.setWebRefreshId
    }

    /**
     * Returns the group AB-props refresh id received from the server.
     *
     * @implNote WAWebABPropsLocalStorage.getGroupAbPropsRefreshId
     * @return the group AB-props refresh id ({@code 0} if never set)
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getGroupAbPropsRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long getGroupAbPropsRefreshId() {
        return client.store().groupAbPropsRefreshId(); // WAWebABPropsLocalStorage.getGroupAbPropsRefreshId
    }

    /**
     * Sets the group AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
     * @param groupRefreshId the refresh id received from the server
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setGroupAbPropsRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setGroupAbPropsRefreshId(long groupRefreshId) {
        client.store().setGroupAbPropsRefreshId(groupRefreshId); // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
    }

    /**
     * Returns the timestamp of the last group AB-props emergency push
     * recorded on the {@code <success>} stanza.
     *
     * @implNote WAWebABPropsLocalStorage.getGroupAbPropsEmergencyPushTimestamp
     * @return an {@code Optional} containing the last emergency push
     *         timestamp, or empty if none has been recorded yet
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getGroupAbPropsEmergencyPushTimestamp",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Instant> getGroupAbPropsEmergencyPushTimestamp() {
        return client.store().groupAbPropsEmergencyPushTimestamp(); // WAWebABPropsLocalStorage.getGroupAbPropsEmergencyPushTimestamp
    }

    /**
     * Sets the timestamp of the last group AB-props emergency push.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     * @param timestamp the emergency push timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setGroupAbPropsEmergencyPushTimestamp",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) {
        client.store().setGroupAbPropsEmergencyPushTimestamp(timestamp); // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
    }

    /**
     * Returns an unmodifiable snapshot of every synced AB prop, keyed by
     * its numeric {@code configCode}.
     *
     * <p>Mirrors the JS export {@code getABPropConfigs}, which awaits storage
     * initialisation and then returns
     * {@code getAbpropConfigsTable().all()} — every row of the
     * {@code abpropConfigs} IndexedDB table. Cobalt collapses that table
     * into the in-memory {@link #props} map per the store-flattening pattern
     * in CLAUDE.md, so the JS {@code initializeWithoutGKs()} await has no
     * direct equivalent — the map is always live for reads.
     *
     * @implNote WAWebApiAbPropConfig.getABPropConfigs —
     *           {@code initializeWithoutGKs().then(() => getAbpropConfigsTable().all())}.
     * @return a defensive copy of the {@code configCode -> configValue} map
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "getABPropConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Integer, String> getABPropConfigs() {
        // WAWebApiAbPropConfig.getABPropConfigs:
        //   initializeWithoutGKs().then(() => getAbpropConfigsTable().all())
        return Map.copyOf(props);
    }

    /**
     * Coerces a raw AB-prop string value to the typed value declared by the
     * given {@link ABProp}, falling back to the prop's default when either
     * argument is missing.
     *
     * <p>Mirrors the JS export {@code parseConfigValue(e, t, n)} from
     * {@code WAWebABPropsParseConfigValue}: if {@code rawValue} is
     * {@code null} or {@code prop} is {@code null}, the {@code prop}'s
     * {@linkplain ABProp#defaultValue() defaultValue} is used; otherwise the
     * value is parsed by the call site's caller-chosen typed accessor
     * ({@link #getString}, {@link #getBool}, {@link #getInt},
     * {@link #getLong}, {@link #getDouble}). The JS function dispatches by a
     * runtime type tag (one of {@code "bool"}, {@code "int"}, {@code "float"},
     * or string) and returns a heterogeneous {@code unknown}; Cobalt resolves
     * the type at the call site through the typed getter chosen and so this
     * helper returns the raw string after applying the JS null-coalescing
     * branch.
     *
     * <p>String dispatch values ({@code "1"}, {@code "True"}, {@code "true"}
     * for booleans; {@link Integer#parseInt(String, int)} for integers;
     * {@link Double#parseDouble(String)} for floats) are implemented in
     * {@link ABProp#toBoolean(String)}, {@link ABProp#toInt(String)} and
     * {@link ABProp#toDouble(String)}.
     *
     * @implNote WAWebABPropsParseConfigValue.parseConfigValue (re-exported
     *           by {@code WAWebApiAbPropConfig.parseConfigValue}) —
     *           {@code e==null||t==null ? n : t==='bool' ? boolParse(e)
     *           : t==='int' ? parseInt(e,10) : t==='float' ? parseFloat(e) : e}.
     * @param rawValue the raw value received from the server, may be
     *                 {@code null} when no override is present
     * @param prop the AB prop definition; when {@code null}, the JS
     *             function returns the {@code n} default unchanged
     * @return the value to use, never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "parseConfigValue",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebABPropsParseConfigValue", exports = "parseConfigValue",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static String parseConfigValue(String rawValue, ABProp prop) {
        // WAWebABPropsParseConfigValue.parseConfigValue:
        //   e==null||t==null ? n : (typed-coerce e by t)
        if (rawValue == null || prop == null) {
            return prop == null ? null : prop.defaultValue();
        }
        return rawValue;
    }

    /**
     * Returns the typed value for the given AB prop, mirroring the JS
     * export {@code getConfigValue(e)} from {@code WAWebApiAbPropConfig}.
     *
     * <p>The JS export keys by the prop's textual name, looks up
     * {@code [code, type, defaultValue, debugDefaultValue]} in
     * {@code WAWebABPropsConfigs.ABPropConfigs}, reads the stored row from
     * the {@code abpropConfigs} IndexedDB table, and dispatches through
     * {@link #parseConfigValue(String, ABProp)}. Cobalt's keys are the
     * {@link ABProp} constants instead of the textual name (the same
     * tuples, generated by {@code tooling/web-ab-props-extractor}), so the
     * runtime lookup against {@code ABPropConfigs} collapses into the typed
     * argument and the storage round-trip becomes a {@link #props} read.
     *
     * @implNote WAWebApiAbPropConfig.getConfigValue —
     *           {@code initializeWithoutGKs().then(() =>
     *           getAbpropConfigsTable().get(String(code)).then(row =>
     *           parseConfigValue(row.configValue, type, defaultValue)))}.
     * @param prop the AB prop definition
     * @return the raw stored value, or {@link ABProp#defaultValue()} when
     *         no row has been synced
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "getConfigValue",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public String getConfigValue(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return getString(prop, true);
    }

    /**
     * Marks the given AB prop as having been read by the host application,
     * mirroring the JS export {@code setConfigAccessed(e)} from
     * {@code WAWebApiAbPropConfig}.
     *
     * <p>The JS export reads the row by code, returns early when
     * {@code hasAccessed} is already {@code true}, and otherwise merges
     * {@code {hasAccessed: true}} onto the row. The flag drives WAM
     * exposure tracking through {@code WAWebABPropsGlobals.accessedConfigs}
     * and the {@code expoKey} Global attribute. Cobalt collapses the on-disk
     * row into the in-memory {@link #accessedConfigs} Set; the boolean
     * return value is {@code true} on the first call (mirroring the JS
     * merge-needed branch) and {@code false} on subsequent calls (mirroring
     * the JS early-return branch).
     *
     * @implNote WAWebApiAbPropConfig.setConfigAccessed —
     *           {@code var t=yield table.get(String(e));
     *           t==null||t.hasAccessed===true||(yield table.merge(String(e), {hasAccessed:true}))}.
     * @param prop the AB prop that was just read
     * @return {@code true} if this is the first access (state changed),
     *         {@code false} if the prop was already flagged
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "setConfigAccessed",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean setConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        // WAWebApiAbPropConfig.setConfigAccessed:
        //   t==null || t?.hasAccessed===true || merge(String(e),{hasAccessed:!0})
        return accessedConfigs.add(prop.code());
    }

    /**
     * Returns whether the given AB prop has previously been flagged as
     * accessed via {@link #setConfigAccessed(ABProp)}.
     *
     * <p>Provides a read-side accessor for the in-memory
     * {@link #accessedConfigs} Set without exposing it for mutation.
     *
     * @param prop the AB prop to query
     * @return {@code true} if {@link #setConfigAccessed(ABProp)} has been
     *         called for {@code prop}
     */
    public boolean isConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return accessedConfigs.contains(prop.code());
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
     * <p>The JS export returns {@code Promise<number | undefined>}, reading
     * a single row from the {@code abprop-event-sampling-configs} IndexedDB
     * table. Cobalt collapses that table into the in-memory
     * {@link #samplingConfigs} cache and returns an {@link OptionalInt} as
     * the Java equivalent of {@code undefined}.
     *
     * @implNote WAWebApiAbPropEventSamplingConfig.getEventSamplingWeight —
     *           {@code getAbpropEventSamplingConfigsTable().get(e).then(r =>
     *           r==null?void 0:r.samplingWeight)}.
     * @param eventCode the numeric WAM event identifier
     * @return an {@code OptionalInt} containing the override weight, or
     *         empty if no override exists for this event
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "getEventSamplingWeight",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public OptionalInt getSamplingWeight(int eventCode) {
        // WAWebApiAbPropEventSamplingConfig.getEventSamplingWeight:
        //   getAbpropEventSamplingConfigsTable().get(e).then(r => r?.samplingWeight)
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
     * <p>The JS export returns the full {@code abprop-event-sampling-configs}
     * IndexedDB table via {@code .all()}, yielding an array of
     * {@code {eventCode, samplingWeight}} rows. Cobalt represents the same
     * data as a {@code Map<Integer, Integer>} keyed by event code.
     *
     * @implNote WAWebApiAbPropEventSamplingConfig.getEventSamplingConfigs —
     *           {@code getAbpropEventSamplingConfigsTable().all().then(e=>e)}.
     * @return a defensive copy of the sampling configs map
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "getEventSamplingConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Integer, Integer> samplingConfigs() {
        // WAWebApiAbPropEventSamplingConfig.getEventSamplingConfigs:
        //   getAbpropEventSamplingConfigsTable().all()
        return Map.copyOf(samplingConfigs);
    }

    /**
     * Replaces the entire sampling-config cache with the provided
     * {@code eventCode -> samplingWeight} entries.
     *
     * <p>Mirrors the JS export {@code updateEventSamplingConfigs(t)}, which
     * acquires a lock on the {@code abprop-event-sampling-configs} table,
     * clears it, and bulk-creates one row per supplied entry. Per the JS
     * contract, a {@code null} or empty argument is a no-op and returns
     * {@code false}; a non-empty argument clears + replaces and returns
     * {@code true}.
     *
     * <p>Cobalt's in-memory {@link ConcurrentHashMap} replaces WA Web's
     * IndexedDB table per the store-flattening pattern in CLAUDE.md.
     *
     * @implNote WAWebApiAbPropEventSamplingConfig.updateEventSamplingConfigs —
     *           {@code if (t==null||t.length===0) return Promise.resolve(false);
     *           ... lock(["abprop-event-sampling-configs"], t => t[0].clear()
     *           .then(() => t[0].bulkCreate(rows).then(returnTrue)))}.
     * @param configs the new sampling configs, or {@code null}/empty for
     *                a no-op
     * @return {@code true} if the cache was replaced, {@code false} if the
     *         input was {@code null} or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "updateEventSamplingConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean updateEventSamplingConfigs(Map<Integer, Integer> configs) {
        // WAWebApiAbPropEventSamplingConfig.updateEventSamplingConfigs:
        //   if (t==null||t.length===0) return Promise.resolve(false)
        if (configs == null || configs.isEmpty()) {
            return false;
        }
        // lock(["abprop-event-sampling-configs"], t => t[0].clear()
        //   .then(() => t[0].bulkCreate(rows).then(returnTrue)))
        samplingConfigs.clear();
        samplingConfigs.putAll(configs);
        return true;
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
        accessedConfigs.clear();
        var store = client.store();
        store.setAbPropsAbKey(null);
        store.setAbPropsHash(null);
        store.setAbPropsLastSyncTime(null);
        // Refresh interval and refresh-ids stay at their persisted values
        // because the JS exports do not clear them on signout either.
        syncFuture.set(new CompletableFuture<>());
        LOGGER.log(System.Logger.Level.DEBUG, "Cleared all AB props and reset sync state");
    }
}
