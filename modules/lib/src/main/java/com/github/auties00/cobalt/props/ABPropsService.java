package com.github.auties00.cobalt.props;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and applies A/B testing properties from WhatsApp servers.
 *
 * <p>AB props are feature flags and configuration values that control client behavior.
 * Synced values are stored by their numeric {@code code} and exposed through typed
 * accessors that fall back to {@link ABProp#defaultValue()} when the server has not
 * provided a value. Queries issued before the first sync completes block (up to a
 * configurable timeout) so callers observe a consistent view, then return the default
 * if the timeout elapses.
 *
 * <p>The service also absorbs the parsing performed by {@code updateABPropConfigs}
 * and the sampling-config cache exposed by {@code WAWebApiAbPropEventSamplingConfig}
 * (backed by an IndexedDB table in WA Web, collapsed into in-memory maps here per the
 * store-flattening pattern documented in {@code CLAUDE.md}).
 *
 * <p>Instances are thread-safe.
 *
 * @see ABProp
 */
@WhatsAppWebModule(moduleName = "WAWebAbPropsSyncJob")
@WhatsAppWebModule(moduleName = "WAGetAbPropsProtocol")
@WhatsAppWebModule(moduleName = "WAGetGroupAbPropsProtocol")
@WhatsAppWebModule(moduleName = "WAWebApiAbPropConfig")
@WhatsAppWebModule(moduleName = "WAWebApiAbPropEventSamplingConfig")
@WhatsAppWebModule(moduleName = "WAWebABPropsLocalStorage")
@WhatsAppWebModule(moduleName = "WAWebABPropsParseConfigValue")
@WhatsAppWebModule(moduleName = "WASmaxInAbPropsSamplingConfigMixin")
@WhatsAppWebModule(moduleName = "WASmaxInAbPropsConfigs")
@WhatsAppWebModule(moduleName = "WASmaxInAbPropsExperimentOrSamplingConfigMixinGroup")
@WhatsAppWebModule(moduleName = "WASmaxInAbPropsEnums")
public final class ABPropsService {
    /**
     * Logger used for sync-cycle warnings, errors, and informational diagnostics.
     */
    private static final System.Logger LOGGER = System.getLogger(ABPropsService.class.getName());

    /**
     * Default timeout that query methods wait for the first sync to complete
     * before falling back to the prop's default value.
     */
    private static final Duration DEFAULT_SYNC_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Lower bound, inclusive, for the {@code refresh} attribute persisted by
     * {@link #updateAttributesLocalStorage(String, String, Long, Instant)},
     * expressed in seconds.
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "updateAttributesLocalStorage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final long REFRESH_MIN_SECONDS = 600L;

    /**
     * Upper bound, inclusive, for the {@code refresh} attribute persisted by
     * {@link #updateAttributesLocalStorage(String, String, Long, Instant)},
     * expressed in seconds.
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "updateAttributesLocalStorage",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final long REFRESH_MAX_SECONDS = 604800L;

    /**
     * Default refresh interval of one day, in seconds, returned by
     * {@link #refresh()} when no value has been recorded yet.
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getRefresh",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final long REFRESH_DEFAULT_SECONDS = 86400L;

    /**
     * Inclusive lower bound, in events, accepted for the {@code event_code}
     * attribute on {@code SamplingConfig} {@code <prop>} children.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsSamplingConfigMixin",
            exports = "parseSamplingConfigMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SAMPLING_EVENT_CODE_MIN = 1;

    /**
     * Inclusive lower bound accepted for the {@code sampling_weight}
     * attribute on {@code SamplingConfig} {@code <prop>} children.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsSamplingConfigMixin",
            exports = "parseSamplingConfigMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SAMPLING_WEIGHT_MIN = -10_000;

    /**
     * Inclusive upper bound accepted for the {@code sampling_weight}
     * attribute on {@code SamplingConfig} {@code <prop>} children.
     */
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsSamplingConfigMixin",
            exports = "parseSamplingConfigMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int SAMPLING_WEIGHT_MAX = 10_000;

    /**
     * Client used to issue the {@code <iq xmlns="abt">} stanza and to read or
     * mutate the AB-props slots on the shared store.
     */
    private final WhatsAppClient client;

    /**
     * Synced AB-prop values keyed by their numeric {@code config_code}.
     */
    private final Map<Integer, String> props;

    /**
     * WAM event sampling-weight overrides keyed by event code, populated
     * from the {@code SamplingConfig} entries returned alongside the prop
     * list during {@link #process(Node)}.
     */
    private final Map<Integer, Integer> samplingConfigs;

    /**
     * Codes of AB props that the host application has read at least once,
     * tracked for the WAM exposure-key attribute.
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "setConfigAccessed",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final Set<Integer> accessedConfigs;

    /**
     * Future that completes once a sync round finishes, releasing threads
     * blocked in query methods. Held in an {@link AtomicReference} so that
     * {@link #clear()} can swap in a fresh future for the next session.
     */
    private final AtomicReference<CompletableFuture<Boolean>> syncFuture;

    /**
     * Timeout that query methods wait for the first sync to complete.
     */
    private final Duration syncTimeout;

    /**
     * Constructs a service bound to {@code client} using the
     * {@link #DEFAULT_SYNC_TIMEOUT} query timeout.
     *
     * @param client the WhatsApp client used to issue sync requests
     */
    public ABPropsService(WhatsAppClient client) {
        this(client, DEFAULT_SYNC_TIMEOUT);
    }

    /**
     * Constructs a service bound to {@code client} with a caller-supplied
     * query timeout.
     *
     * @param client      the WhatsApp client used to issue sync requests
     * @param syncTimeout the timeout query methods wait for the first sync
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
     * Runs a sync round with the default options of
     * {@code localRefreshId=null} and {@code shouldSendHash=true}, matching
     * the JS module-level default object {@code m = {shouldSendHash: !0}}.
     *
     * @return {@code true} when at least one of the three attempts
     *         succeeded, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABPropsTask",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean sync() {
        return sync(null, true);
    }

    /**
     * Runs a sync round with caller-supplied options, retrying up to three
     * times with the JS jittered backoff and applying the first-sync gating
     * rule on {@code shouldSendHash}.
     *
     * <p>On the {@code propsHash} branch, {@code shouldSendHash} is ANDed
     * with {@link #isAfterFirstSync()} so the persisted hash is only sent
     * once a previous sync established it. On success the sync future is
     * completed so blocked queries can proceed; on terminal failure the
     * future is failed exceptionally.
     * @param localRefreshId the refresh-id override used by the emergency
     *                       push branch, or {@code null} to take the
     *                       regular {@code propsHash} branch
     * @param shouldSendHash whether the {@code propsHash} branch may
     *                       include the persisted hash on the request
     * @return {@code true} when at least one attempt succeeded,
     *         {@code false} when all attempts failed
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABPropsTask",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean sync(Long localRefreshId, boolean shouldSendHash) {
        var afterFirstSync = isAfterFirstSync();
        var effectiveShouldSendHash = localRefreshId != null
                ? shouldSendHash
                : afterFirstSync && shouldSendHash;
        Throwable lastFailure = null;
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
                try {
                    Thread.sleep((long) (10_000L * Math.random()));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOGGER.log(System.Logger.Level.ERROR, "Failed to sync ABProps after 3 attempts");
        if (lastFailure != null) {
            failSync(lastFailure);
        } else {
            completeSync(false);
        }
        return false;
    }

    /**
     * Performs a single sync round trip, choosing between the emergency
     * push branch (when {@code localRefreshId} is non-{@code null}) and the
     * regular {@code propsHash} branch.
     * @param localRefreshId the refresh-id override that selects the
     *                       emergency push branch, or {@code null} to take
     *                       the regular branch
     * @param shouldSendHash whether the persisted hash is included on the
     *                       regular branch
     * @return {@code true} when the response was processed successfully,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebAbPropsSyncJob", exports = "syncABProps",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean syncABProps(Long localRefreshId, boolean shouldSendHash) {
        var emergencyBranch = localRefreshId != null;
        var request = emergencyBranch
                ? getAbPropsProtocol(null, localRefreshId)
                : getAbPropsProtocol(shouldSendHash ? client.store().abPropsHash().orElse(null) : null, null);
        var response = client.sendNode(request);
        if (response == null) {
            return false;
        }
        return process(response);
    }

    /**
     * Completes the sync future with {@code success}, releasing every
     * thread blocked in {@link #awaitSync()}.
     *
     * @param success the result delivered to waiters
     */
    private void completeSync(boolean success) {
        syncFuture.get().complete(success);
    }

    /**
     * Completes the sync future exceptionally so waiting threads do not
     * hang when every sync attempt fails.
     *
     * @param throwable the failure observed on the last attempt
     */
    private void failSync(Throwable throwable) {
        syncFuture.get().completeExceptionally(throwable);
    }

    /**
     * Blocks until the next sync completes or {@link #syncTimeout} elapses.
     * Used by typed query methods to defer reads until props are actually
     * available.
     *
     * @return {@code true} when the sync completed successfully within the
     *         timeout, {@code false} on timeout, interruption, or failure
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
     * Builds the {@code <iq xmlns="abt" type="get">} stanza that requests
     * the experiment-config blob from {@code s.whatsapp.net}.
     *
     * <p>The inner {@code <props>} child carries the literal
     * {@code protocol="1"} together with an optional {@code hash} (used by
     * the regular delta-update branch) or an optional {@code refresh_id}
     * (used by the emergency push branch). Callers normally populate
     * exactly one of the two depending on which branch they take.
     * @param propsHash      the AB-props hash for delta updates, or
     *                       {@code null} to omit the attribute
     * @param propsRefreshId the refresh id used by the emergency push
     *                       branch, or {@code null} to omit the attribute
     * @return a {@link NodeBuilder} wrapping the constructed stanza
     */
    @WhatsAppWebExport(moduleName = "WAGetAbPropsProtocol", exports = "getAbPropsProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private NodeBuilder getAbPropsProtocol(String propsHash, Long propsRefreshId) {
        var propsNode = new NodeBuilder()
                .description("props")
                .attribute("protocol", "1");
        if (propsHash != null) {
            propsNode.attribute("hash", propsHash);
        }
        if (propsRefreshId != null) {
            propsNode.attribute("refresh_id", propsRefreshId);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "abt")
                .attribute("to", "s.whatsapp.net")
                .attribute("type", "get")
                .content(propsNode.build());
    }

    /**
     * Parses a sync response, applies it to the in-memory caches, and
     * persists the {@code ABPROPS} local-storage attributes.
     *
     * <p>The {@code <props>} child carries the {@code hash}, {@code ab_key},
     * {@code refresh}, {@code refresh_id}, and {@code delta_update}
     * attributes plus a list of {@code <prop>} children. Each child is
     * either an {@code ExperimentConfig} (carrying {@code config_code} and
     * {@code config_value}) or a {@code SamplingConfig} (carrying
     * {@code event_code} and {@code sampling_weight}).
     *
     * <p>Local-storage attributes and the sampling-config cache are only
     * replaced on full (non-delta) updates. Delta responses leave both in
     * place even if they happen to carry sampling entries, matching the JS
     * gating on {@code !isDeltaUpdate}.
     *
     * <p>{@code SamplingConfig} entries are accepted only when
     * {@code event_code >= }{@value #SAMPLING_EVENT_CODE_MIN} and
     * {@code sampling_weight} lies in
     * [{@value #SAMPLING_WEIGHT_MIN}, {@value #SAMPLING_WEIGHT_MAX}], matching
     * the validation in
     * {@code WASmaxInAbPropsSamplingConfigMixin.parseSamplingConfigMixin}.
     *
     * <p>Each {@code <prop>} child is dispatched through the disjunction
     * defined in {@code WASmaxInAbPropsConfigs.parseConfigs} and the
     * underlying {@code WASmaxInAbPropsExperimentOrSamplingConfigMixinGroup
     * .parseExperimentOrSamplingConfigMixinGroup} wrapper: try
     * {@code parseExperimentConfigMixin} first, fall back to
     * {@code parseSamplingConfigMixin}, and log a warning equivalent to
     * {@code WASmaxParseUtils.errorMixinDisjunction} when neither shape
     * matches.
     * @param response the server response node
     * @return {@code true} when the response was parsed successfully,
     *         {@code false} when the {@code <props>} child was missing
     * @throws NullPointerException when {@code response} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "updateABPropConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsSamplingConfigMixin",
            exports = "parseSamplingConfigMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsConfigs", exports = "parseConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInAbPropsExperimentOrSamplingConfigMixinGroup",
            exports = "parseExperimentOrSamplingConfigMixinGroup",
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

        var isDelta = propsNode.getAttributeAsBool("delta_update", false);
        if (!isDelta) {
            props.clear();
            var responseRefresh = responseRefreshOpt.isPresent() ? responseRefreshOpt.getAsLong() : null;
            updateAttributesLocalStorage(responseAbKey, responseHash, responseRefresh, Instant.now());
        }
        propsNode.getAttributeAsLong("refresh_id")
                .ifPresent(this::setRefreshId);

        var propNodes = propsNode.getChildren("prop");
        var experimentCount = 0;
        var parsedSamplingConfigs = new LinkedHashMap<Integer, Integer>();
        for (var propNode : propNodes) {
            var configCode = propNode.getAttributeAsInt("config_code");
            var configValue = propNode.getAttributeAsString("config_value");
            if (configCode.isPresent() && configValue.isPresent()) {
                props.put(configCode.getAsInt(), configValue.get());
                experimentCount++;
                continue;
            }

            var eventCode = propNode.getAttributeAsInt("event_code");
            var samplingWeight = propNode.getAttributeAsInt("sampling_weight");
            if (eventCode.isPresent() && samplingWeight.isPresent()) {
                var eventCodeValue = eventCode.getAsInt();
                var samplingWeightValue = samplingWeight.getAsInt();
                if (eventCodeValue < SAMPLING_EVENT_CODE_MIN) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Skipping SamplingConfig <prop>: event_code={0} below minimum {1}",
                            eventCodeValue, SAMPLING_EVENT_CODE_MIN);
                    continue;
                }
                if (samplingWeightValue < SAMPLING_WEIGHT_MIN
                        || samplingWeightValue > SAMPLING_WEIGHT_MAX) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Skipping SamplingConfig <prop>: sampling_weight={0} outside [{1}, {2}]",
                            samplingWeightValue, SAMPLING_WEIGHT_MIN, SAMPLING_WEIGHT_MAX);
                    continue;
                }
                parsedSamplingConfigs.put(eventCodeValue, samplingWeightValue);
                continue;
            }

            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping <prop> matching neither ExperimentConfig nor SamplingConfig");
        }

        var samplingUpdated = !isDelta && updateEventSamplingConfigs(parsedSamplingConfigs);

        LOGGER.log(System.Logger.Level.INFO,
                "Synced {0} AB props and {1} sampling configs from server (delta={2}, samplingUpdated={3})",
                experimentCount, parsedSamplingConfigs.size(), isDelta, samplingUpdated);

        var eridNode = response.getChild("erid", null);
        if (eridNode != null) {
            LOGGER.log(System.Logger.Level.DEBUG, "AB props response included <erid> blob");
        }

        return true;
    }

    /**
     * Returns the AB key most recently received from the server, written
     * as the WAM {@code abKey2} global attribute (field 4473) on the
     * {@code regular} channel unless the {@code wam_disable_abkey_attribute}
     * prop is enabled.
     *
     * @return the AB key, or empty when the server has not provided one
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getABKey",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> abKey() {
        return client.store().abPropsAbKey();
    }

    /**
     * Returns the {@code hash} attribute most recently received from the
     * server, sent on subsequent sync requests so the server can reply
     * with a delta update instead of the full prop list.
     *
     * @return the AB-props hash, or empty when no sync has completed
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getHash",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<String> hash() {
        return client.store().abPropsHash();
    }

    /**
     * Returns the configured refresh interval in seconds, falling back to
     * {@value #REFRESH_DEFAULT_SECONDS} when no value has been recorded.
     *
     * @return the refresh interval in seconds
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getRefresh",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long refresh() {
        return client.store()
                .abPropsRefresh()
                .orElse(REFRESH_DEFAULT_SECONDS);
    }

    /**
     * Returns the timestamp of the most recent successful sync, persisted
     * as the {@code lastSyncTime} field of the {@code ABPROPS} JSON blob
     * in WA Web.
     *
     * @return the last sync timestamp, or empty when none has been recorded
     */
    public Optional<Instant> lastSyncTime() {
        return client.store()
                .abPropsLastSyncTime();
    }

    /**
     * Returns whether at least one sync has completed since the session was
     * created. WA Web tests this by parsing the persisted {@code ABPROPS}
     * JSON blob; Cobalt approximates the same predicate by checking
     * whether any of the stored attributes have been populated.
     *
     * @return {@code true} when a previous sync established AB-props state,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "isABPropsAfterFirstSync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean isAfterFirstSync() {
        var store = client.store();
        return store.abPropsLastSyncTime().isPresent()
                || store.abPropsAbKey().isPresent()
                || store.abPropsHash().isPresent();
    }

    /**
     * Persists the {@code ABPROPS} blob attributes returned on the most
     * recent sync. Each non-{@code null} parameter overwrites the
     * corresponding store field; {@code null} parameters keep the previous
     * value, matching the {@code abKey ?? m.abKey} fallback chain in the
     * JS source.
     * @param abKey          the AB key to persist, or {@code null} to keep
     *                       the previous value
     * @param hash           the AB-props hash to persist, or {@code null}
     *                       to keep the previous value
     * @param refreshSeconds the refresh interval in seconds, or
     *                       {@code null} to keep the previous value
     * @param lastSyncTime   the sync completion instant
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "updateAttributesLocalStorage",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void updateAttributesLocalStorage(String abKey, String hash, Long refreshSeconds, Instant lastSyncTime) {
        var store = client.store();
        if (abKey != null) {
            store.setAbPropsAbKey(abKey);
        }
        if (hash != null) {
            store.setAbPropsHash(hash);
        }
        if (refreshSeconds != null) {
            long clamped;
            if (refreshSeconds < REFRESH_MIN_SECONDS) {
                clamped = REFRESH_MIN_SECONDS;
            } else if (refreshSeconds > REFRESH_MAX_SECONDS) {
                clamped = REFRESH_MAX_SECONDS;
            } else {
                clamped = refreshSeconds;
            }
            store.setAbPropsRefresh(clamped);
        }
        store.setAbPropsLastSyncTime(lastSyncTime);
    }

    /**
     * Returns the AB-props refresh id, used as the {@code propsRefreshId}
     * attribute on the next sync request when justknobx {@code 3330} is
     * enabled.
     * @return the AB-props refresh id, or {@code 0} when never set
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long refreshId() {
        return client.store().abPropsRefreshId();
    }

    /**
     * Persists the AB-props refresh id received from the server.
     *
     * @param refreshId the refresh id to persist
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setRefreshId(long refreshId) {
        client.store().setAbPropsRefreshId(refreshId);
    }

    /**
     * Returns the web-only AB-props refresh id, used to gate the
     * justknobx {@code 2086} emergency push request.
     *
     * @return the web AB-props refresh id, or {@code 0} when never set
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getWebRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long webRefreshId() {
        return client.store().abPropsWebRefreshId();
    }

    /**
     * Persists the web-only AB-props refresh id received from the server.
     *
     * @param webRefreshId the refresh id to persist
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setWebRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setWebRefreshId(long webRefreshId) {
        client.store().setAbPropsWebRefreshId(webRefreshId);
    }

    /**
     * Returns the group AB-props refresh id received from the server.
     *
     * @return the group refresh id, or {@code 0} when never set
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getGroupAbPropsRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public long groupAbPropsRefreshId() {
        return client.store().groupAbPropsRefreshId();
    }

    /**
     * Persists the group AB-props refresh id received from the server.
     *
     * @param groupRefreshId the refresh id to persist
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setGroupAbPropsRefreshId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setGroupAbPropsRefreshId(long groupRefreshId) {
        client.store().setGroupAbPropsRefreshId(groupRefreshId);
    }

    /**
     * Returns the timestamp of the last group AB-props emergency push
     * recorded on the {@code <success>} stanza.
     *
     * @return the last emergency push timestamp, or empty when none has
     *         been recorded
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "getGroupAbPropsEmergencyPushTimestamp",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Instant> groupAbPropsEmergencyPushTimestamp() {
        return client.store().groupAbPropsEmergencyPushTimestamp();
    }

    /**
     * Persists the timestamp of the last group AB-props emergency push.
     *
     * @param timestamp the emergency push timestamp
     */
    @WhatsAppWebExport(moduleName = "WAWebABPropsLocalStorage", exports = "setGroupAbPropsEmergencyPushTimestamp",
            adaptation = WhatsAppAdaptation.DIRECT)
    public void setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) {
        client.store().setGroupAbPropsEmergencyPushTimestamp(timestamp);
    }

    /**
     * Fetches the per-group AB-props bundle from the relay and projects
     * the response into a typed {@link GroupAbPropsResult}.
     *
     * @param groupJid  the target group JID. Never {@code null}
     * @param propsHash the cached group-props hash, or {@code null} for
     *                  an unconditional fetch
     * @return an {@link Optional} carrying the projected result on
     *         success, empty on any failure variant
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAGetGroupAbPropsProtocol", exports = "getGroupAbPropsProtocol",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<GroupAbPropsResult> getGroupAbPropsProtocol(Jid groupJid, String propsHash) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        try {
            var bundle = client.queryGroupExperimentConfig(groupJid, propsHash).orElse(null);
            if (bundle == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "getGroupAbPropsProtocol failed: response did not parse as Success");
                return Optional.empty();
            }

            var entries = new ArrayList<GroupAbPropsResult.Entry>(bundle.experiments().size());
            for (var experiment : bundle.experiments().entrySet()) {
                // The exposure key is dropped at the bundle layer; consumers
                // observed via abPropConfigs() only key on configCode/configValue.
                entries.add(new GroupAbPropsResult.Entry(experiment.getKey(), experiment.getValue(), null));
            }
            var result = new GroupAbPropsResult(
                    groupJid,
                    bundle.hash().orElse(null),
                    bundle.refresh().orElse(null),
                    bundle.refreshId().orElse(null),
                    entries);
            return Optional.of(result);
        } catch (WhatsAppServerRuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "getGroupAbPropsProtocol failed: {0}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns an unmodifiable snapshot of every synced AB prop keyed by
     * its numeric {@code config_code}.
     * @return a defensive copy of the {@code config_code -> config_value}
     *         map
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "getABPropConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Integer, String> abPropConfigs() {
        return Map.copyOf(props);
    }

    /**
     * Flags {@code prop} as having been read by the host application,
     * driving the WAM exposure-key attribute.
     * @param prop the AB prop that was just read
     * @return {@code true} when this is the first access, {@code false}
     *         when the prop was already flagged
     * @throws NullPointerException when {@code prop} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropConfig", exports = "setConfigAccessed",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean setConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return accessedConfigs.add(prop.code());
    }

    /**
     * Returns whether {@code prop} has previously been flagged as accessed
     * via {@link #setConfigAccessed(ABProp)}.
     *
     * @param prop the AB prop to query
     * @return {@code true} when {@link #setConfigAccessed(ABProp)} has
     *         already been called for {@code prop}
     * @throws NullPointerException when {@code prop} is {@code null}
     */
    public boolean isConfigAccessed(ABProp prop) {
        Objects.requireNonNull(prop, "prop cannot be null");
        return accessedConfigs.contains(prop.code());
    }

    /**
     * Returns the raw string value for {@code prop}, blocking until the
     * first sync completes. The fallback is
     * {@link ABProp#debugDefaultValue()} when the WhatsApp Web Beta flag
     * is set on the store, otherwise {@link ABProp#defaultValue()}.
     *
     * @param prop the AB prop definition
     * @return the string value, or the appropriate default
     */
    public String getString(ABProp prop) {
        return getString(prop, true);
    }

    /**
     * Returns the raw string value for {@code prop}, optionally skipping
     * the wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on {@link #awaitSync()} before
     *                    reading
     * @return the string value, or the appropriate default
     */
    public String getString(ABProp prop, boolean waitForSync) {
        if (waitForSync) {
            awaitSync();
        }
        var defaultValue = client.store().externalWebBeta() ? prop.debugDefaultValue() : prop.defaultValue();
        return props.getOrDefault(prop.code(), defaultValue);
    }

    /**
     * Returns the boolean value for {@code prop}, blocking until the first
     * sync completes and falling back to the default when the synced value
     * is unparseable.
     *
     * @param prop the AB prop definition
     * @return the parsed boolean, or the default
     * @see ABProp#toBoolean(String)
     */
    public boolean getBool(ABProp prop) {
        return getBool(prop, true);
    }

    /**
     * Returns the boolean value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on {@link #awaitSync()} before
     *                    reading
     * @return the parsed boolean, or the default
     * @see ABProp#toBoolean(String)
     */
    public boolean getBool(ABProp prop, boolean waitForSync) {
        return ABProp.toBoolean(getString(prop, waitForSync));
    }

    /**
     * Returns the integer value for {@code prop}, blocking until the first
     * sync completes. Returns {@code 0} when neither the synced value nor
     * the default parses as an integer.
     *
     * @param prop the AB prop definition
     * @return the parsed integer, the default, or {@code 0}
     * @see ABProp#toInt(String)
     */
    public int getInt(ABProp prop) {
        return getInt(prop, true);
    }

    /**
     * Returns the integer value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on {@link #awaitSync()} before
     *                    reading
     * @return the parsed integer, the default, or {@code 0}
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
     * Returns the long value for {@code prop}, blocking until the first
     * sync completes. Returns {@code 0L} when neither the synced value nor
     * the default parses as a long.
     *
     * @param prop the AB prop definition
     * @return the parsed long, the default, or {@code 0L}
     * @see ABProp#toLong(String)
     */
    public long getLong(ABProp prop) {
        return getLong(prop, true);
    }

    /**
     * Returns the long value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on {@link #awaitSync()} before
     *                    reading
     * @return the parsed long, the default, or {@code 0L}
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
     * Returns the double value for {@code prop}, blocking until the first
     * sync completes. Returns {@code 0.0} when neither the synced value
     * nor the default parses as a double.
     *
     * @param prop the AB prop definition
     * @return the parsed double, the default, or {@code 0.0}
     * @see ABProp#toDouble(String)
     */
    public double getDouble(ABProp prop) {
        return getDouble(prop, true);
    }

    /**
     * Returns the double value for {@code prop}, optionally skipping the
     * wait for the first sync.
     *
     * @param prop        the AB prop definition
     * @param waitForSync whether to block on {@link #awaitSync()} before
     *                    reading
     * @return the parsed double, the default, or {@code 0.0}
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
     * Returns the sampling-weight override for the given WAM event code.
     * @param eventCode the WAM event identifier
     * @return the override weight, or empty when none was synced for this
     *         event
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "getEventSamplingWeight",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public OptionalInt getSamplingWeight(int eventCode) {
        var weight = samplingConfigs.get(eventCode);
        return weight != null ? OptionalInt.of(weight) : OptionalInt.empty();
    }

    /**
     * Returns an unmodifiable snapshot of every WAM event sampling-weight
     * override parsed from the most recent sync, keyed by event code.
     *
     * @return a defensive copy of the sampling-config map
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "getEventSamplingConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Map<Integer, Integer> samplingConfigs() {
        return Map.copyOf(samplingConfigs);
    }

    /**
     * Replaces the sampling-config cache with the supplied entries. A
     * {@code null} or empty argument is a no-op and returns {@code false};
     * a non-empty argument clears and replaces the cache and returns
     * {@code true}, matching the JS export contract.
     *
     * @param configs the new sampling configs, or {@code null} or empty
     *                for a no-op
     * @return {@code true} when the cache was replaced, {@code false}
     *         when the argument was {@code null} or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebApiAbPropEventSamplingConfig",
            exports = "updateEventSamplingConfigs",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean updateEventSamplingConfigs(Map<Integer, Integer> configs) {
        if (configs == null || configs.isEmpty()) {
            return false;
        }
        samplingConfigs.clear();
        samplingConfigs.putAll(configs);
        return true;
    }

    /**
     * Returns the number of synced AB props currently held in memory.
     *
     * @return the count of synced props
     */
    public int size() {
        return props.size();
    }

    /**
     * Returns whether no AB props have been synced yet.
     *
     * @return {@code true} when {@link #props} is empty, {@code false}
     *         otherwise
     */
    public boolean isEmpty() {
        return props.isEmpty();
    }

    /**
     * Resets the service for a fresh session by clearing every in-memory
     * cache, dropping the persisted local-storage attributes that depend
     * on the current session, and replacing the sync future so subsequent
     * queries block on the next sync.
     *
     * <p>Refresh interval and refresh ids stay at their persisted values
     * because the JS exports also leave them in place across sign-outs.
     */
    public void clear() {
        props.clear();
        samplingConfigs.clear();
        accessedConfigs.clear();
        var store = client.store();
        store.setAbPropsAbKey(null);
        store.setAbPropsHash(null);
        store.setAbPropsLastSyncTime(null);
        syncFuture.set(new CompletableFuture<>());
        LOGGER.log(System.Logger.Level.DEBUG, "Cleared all AB props and reset sync state");
    }
}
