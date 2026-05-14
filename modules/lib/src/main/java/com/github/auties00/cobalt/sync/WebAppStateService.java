package com.github.auties00.cobalt.sync;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateFatalExceptionNotificationBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.signal.KeyId;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadAction;
import com.github.auties00.cobalt.model.sync.data.*;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.crypto.MutationIntegrityVerifier;
import com.github.auties00.cobalt.sync.crypto.MutationKeys;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder;
import com.github.auties00.cobalt.sync.exchange.MutationResponseParser;
import com.github.auties00.cobalt.sync.exchange.MutationSyncResponse;
import com.github.auties00.cobalt.sync.exchange.SyncRequest;
import com.github.auties00.cobalt.sync.key.MissingSyncKeyRequestService;
import com.github.auties00.cobalt.sync.key.MissingSyncKeyTimeoutScheduler;
import com.github.auties00.cobalt.sync.key.SyncKeyRotationService;
import com.github.auties00.cobalt.sync.handler.SyncdIndexUtils;
import com.github.auties00.cobalt.util.SchedulerUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdAppStateMessageRangeEventBuilder;
import com.github.auties00.cobalt.wam.event.MdAppStateSyncMutationStatsEventBuilder;
import com.github.auties00.cobalt.wam.event.SyncdKeyCountEventBuilder;
import com.github.auties00.cobalt.wam.event.MdBootstrapAppStateCriticalDataProcessingEventBuilder;
import com.github.auties00.cobalt.wam.event.MdBootstrapAppStateDataDownloadedEventBuilder;
import com.github.auties00.cobalt.wam.event.MdBootstrapDataAppliedEventBuilder;
import com.github.auties00.cobalt.wam.event.MdSyncdMutationEventBuilder;
import com.github.auties00.cobalt.wam.event.MediaDownload2EventBuilder;
import com.github.auties00.cobalt.wam.type.BootstrapAppStateDataStageCode;
import com.github.auties00.cobalt.wam.type.DownloadOriginType;
import com.github.auties00.cobalt.wam.type.MdBootstrapPayloadType;
import com.github.auties00.cobalt.wam.type.MdBootstrapSource;
import com.github.auties00.cobalt.wam.type.MdBootstrapStepResult;
import com.github.auties00.cobalt.wam.type.MediaDownloadModeType;
import com.github.auties00.cobalt.wam.type.MediaDownloadResultType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MutationBundleType;
import com.github.auties00.cobalt.wam.type.MutationCountBucket;
import com.github.auties00.cobalt.wam.type.MutationDirectionType;
import com.github.auties00.cobalt.wam.type.MutationOperationType;
import com.github.auties00.cobalt.wam.type.SyncdCollectionType;
import it.auties.protobuf.stream.ProtobufInputStream;

import javax.crypto.Mac;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Main coordinator for WhatsApp Web App State synchronization.
 *
 * <p>This class manages bidirectional synchronization of application state
 * across multiple devices using end-to-end encryption and LT-Hash verification.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncd")
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionHandlerTypesConverter")
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdDbCallbacksApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdMMSDownload")
@WhatsAppWebModule(moduleName = "WAWebSyncdNetCallbacksApi")
@WhatsAppWebModule(moduleName = "WAWebGetCollectionVersion")
@WhatsAppWebModule(moduleName = "WAWebGetSyncAction")
@WhatsAppWebModule(moduleName = "WAWebSyncdBridgeApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionsStateMachine")
@WhatsAppWebModule(moduleName = "WAWebSyncdDisabled")
@WhatsAppWebModule(moduleName = "WAWebSyncdFatal")
@WhatsAppWebModule(moduleName = "WAWebSyncdFatalExceptionNotificationApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
@WhatsAppWebModule(moduleName = "WAWebSyncdReportSyncdStatJob")
@WhatsAppWebModule(moduleName = "WAWebSyncdWamUtils")
public final class WebAppStateService {
    private static final Logger LOGGER = Logger.getLogger(WebAppStateService.class.getName());

    /**
     * The WhatsApp client used for store access and for sending nodes.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Cached reference to {@link WhatsAppClient#store()}.
     */
    private final WhatsAppStore store;

    /**
     * Builds outgoing sync IQ nodes and encrypts pending mutations.
     */
    private final MutationRequestBuilder requestBuilder;

    /**
     * Parses incoming sync IQ responses into {@link MutationSyncResponse} records.
     */
    private final MutationResponseParser responseParser;

    /**
     * Verifies snapshot and patch MACs against the local key state.
     */
    private final MutationIntegrityVerifier integrityVerifier;

    /**
     * Lookup table mapping action names to their registered handlers.
     */
    private final WebAppStateHandlerRegistry handlerRegistry;

    /**
     * Schedules retries for failed sync rounds with exponential backoff.
     */
    private final WebAppStateBackoffScheduler retryScheduler;

    /**
     * Schedules timeout checks for missing sync keys.
     */
    private final MissingSyncKeyTimeoutScheduler missingSyncKeyTimeoutScheduler;

    /**
     * Sends key request peer messages for missing sync keys.
     */
    private final MissingSyncKeyRequestService missingSyncKeyRequestService;

    /**
     * Manages sync key rotation and key share handling.
     */
    private final SyncKeyRotationService syncKeyRotationService;

    /**
     * Source of A/B-tested configuration values.
     */
    private final ABPropsService abPropsService;

    /**
     * Drives snapshot recovery when a snapshot MAC validation fails.
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * The WAM telemetry service used to commit app-state sync events.
     */
    private final WamService wamService;

    /**
     * Handle of the currently scheduled periodic sync job, or {@code null}
     * when none is scheduled.
     */
    private volatile CompletableFuture<?> periodicSyncJob;
    /**
     * Handle of the currently scheduled daily syncd stats reporting job, or
     * {@code null} when no job is scheduled.
     */
    private volatile CompletableFuture<?> periodicReportSyncdStatsJob;

    /**
     * Handle of the currently scheduled daily syncd key stats reporting job,
     * or {@code null} when no job is scheduled.
     */
    private volatile CompletableFuture<?> periodicReportSyncdKeyStatsJob;

    /**
     * Creates a new {@code WebAppStateService} instance.
     *
     * @param whatsapp                the WhatsApp client instance for store access and node sending
     * @param abPropsService          the A/B props service for configuration values
     * @param lidMigrationService     the LID migration service injected
     *                                into the handler registry so the
     *                                device-capabilities handler can
     *                                observe LID 1:1 migration progress
     * @param snapshotRecoveryService the snapshot recovery service for peer recovery
     * @param wamService              the WAM telemetry service for committing sync events
     */
    public WebAppStateService(WhatsAppClient whatsapp, ABPropsService abPropsService, LidMigrationService lidMigrationService, SnapshotRecoveryService snapshotRecoveryService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.store = whatsapp.store();
        this.abPropsService = abPropsService;
        this.wamService = wamService;
        this.requestBuilder = new MutationRequestBuilder(whatsapp, abPropsService, wamService);
        this.responseParser = new MutationResponseParser();
        this.handlerRegistry = new WebAppStateHandlerRegistry(abPropsService, lidMigrationService, wamService);
        this.integrityVerifier = new MutationIntegrityVerifier(store);
        this.retryScheduler = new WebAppStateBackoffScheduler();
        this.missingSyncKeyRequestService = new MissingSyncKeyRequestService(whatsapp, wamService);
        this.missingSyncKeyTimeoutScheduler = new MissingSyncKeyTimeoutScheduler(whatsapp, abPropsService, missingSyncKeyRequestService);
        this.missingSyncKeyRequestService.setTimeoutScheduler(missingSyncKeyTimeoutScheduler);
        this.syncKeyRotationService = new SyncKeyRotationService(whatsapp, this, abPropsService, wamService);
        this.snapshotRecoveryService = snapshotRecoveryService;
    }

    /**
     * Pushes local patches to the server.
     * Called from Whatsapp.pushWebAppState().
     * @param patchType the collection type to sync
     * @param patches the patches to push
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "markCollectionsForSync", adaptation = WhatsAppAdaptation.ADAPTED)
    public void pushPatches(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        syncKeyRotationService.ensureActiveKey(true); // WAWebSyncdKeyManagement.getActiveKey

        store.markWebAppStateDirty(patchType); // WAWebSyncd.V: moveCollectionsToDirty([e])

        whatsapp.store().addPendingMutations(patchType, patches); // ADAPTED: WAWebSyncd.V caller stores pending before calling markCollectionsForSync

        syncCollection(patchType); // WAWebSyncd.V: Z() -> ee() -> scheduleSyncCollections
    }

    /**
     * Pulls patches from the server.
     * Called from Whatsapp.pullWebAppState().
     *
     * <p>Returns whether any of the synced collections contributed actual state changes, i.e.
     * at least one collection response carried patches or a snapshot. The return value is the
     * Cobalt equivalent of WA Web's {@code onceAppStateSyncCompleted} callback argument which
     * is an array of per-collection results with {@code patches} and {@code snapshot} fields;
     * {@code WAWebHandleDirtyBits.p} inspects it as
     * {@code !e.some(r => r.patches?.length > 0 || r.snapshot != null)} to detect false-positive
     * dirty bits.
     * @param patchTypes the collection types to sync
     * @return {@code true} if any synced collection had patches or a snapshot; {@code false}
     *         when every collection sync completed without applying any state changes, or when
     *         {@code patchTypes} is empty
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "markCollectionsForSync", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean pullPatches(SyncPatchType... patchTypes) {
        var allCollections = new LinkedHashSet<SyncPatchType>(); // ADAPTED: WAWebSyncd.V: n = t != null ? yield H(e,t) : e (no server version filter in pull path)
        Collections.addAll(allCollections, patchTypes); // WAWebSyncd.V: collections list
        if (!allCollections.isEmpty()) { // WAWebSyncd.V: n.forEach(...)
            return syncCollectionsBatched(allCollections); // WAWebSyncd.V: Z() -> ee() -> scheduleSyncCollections
        }
        return false;
    }

    /**
     * Returns the internal {@link SyncKeyRotationService} instance owned by this service.
     *
     * <p>Exposed so collaborators that need direct access to key share/rotation primitives
     * (e.g., the protocol message handler dispatching incoming
     * {@code AppStateSyncKeyShare} messages) can call into the service without having
     * a separately injected instance, since the service is currently constructed
     * internally by {@link WebAppStateService}.
     *
     * @return the sync key rotation service
     */
    public SyncKeyRotationService syncKeyRotationService() {
        return syncKeyRotationService;
    }

    /**
     * Syncs all collections currently in {@code BLOCKED} state.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.syncBlockedCollections} (J): retrieves all
     * collections in the {@code Blocked} state from the state machine, transitions them
     * to {@code Dirty}, and triggers a sync round. This is called after missing sync
     * keys are received (via {@code WAWebSyncdHandleKeyShare.handleKeyShare}) to resume
     * syncing collections that were waiting for key material.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "syncBlockedCollections", adaptation = WhatsAppAdaptation.DIRECT)
    public void syncBlockedCollections() {
        var blockedCollections = new ArrayList<SyncPatchType>(); // WAWebSyncd.J: var t = getCollectionsInStateBlocked()
        for (var patchType : SyncPatchType.values()) { // WAWebSyncd.J: iterates blocked collections
            var metadata = store.findWebAppState(patchType);
            if (metadata.state() == SyncCollectionState.BLOCKED) { // WAWebSyncd.J: getCollectionsInStateBlocked
                store.markWebAppStateDirty(patchType); // WAWebSyncd.J: moveCollectionsToDirty(t)
                blockedCollections.add(patchType);
            }
        }
        if (!blockedCollections.isEmpty()) { // WAWebSyncd.J: Z() — schedule sync
            pullPatches(blockedCollections.toArray(SyncPatchType[]::new)); // WAWebSyncd.J: Z() -> ee() -> serverSync
        }
    }

    /**
     * Returns the set of collections currently being synced (in-flight).
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.getInFlightCollections} (me): returns the
     * module-level set {@code A} tracking collections with an active server sync request.
     * In Cobalt, this is derived from the store's {@code IN_FLIGHT} state.
     * @return an unmodifiable set of in-flight collection types
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "getInFlightCollections", adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<SyncPatchType> getInFlightCollections() {
        var result = new LinkedHashSet<SyncPatchType>(); // WAWebSyncd.me: return A
        for (var patchType : SyncPatchType.values()) { // ADAPTED: WAWebSyncd uses module-level Set A; Cobalt derives from store state
            if (store.findWebAppState(patchType).state() == SyncCollectionState.IN_FLIGHT) {
                result.add(patchType);
            }
        }
        return Collections.unmodifiableSet(result); // WAWebSyncd.me: return A (Set)
    }

    /**
     * Returns the set of collections pending sync.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.getPendingCollections} (pe): returns the
     * module-level set {@code F} tracking collections that were re-marked dirty while
     * already in-flight, requiring another sync round after the current one completes.
     * In Cobalt, this is derived from the store's {@code PENDING} state.
     * @return an unmodifiable set of pending collection types
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "getPendingCollections", adaptation = WhatsAppAdaptation.ADAPTED)
    public Set<SyncPatchType> getPendingCollections() {
        var result = new LinkedHashSet<SyncPatchType>(); // WAWebSyncd.pe: return F
        for (var patchType : SyncPatchType.values()) { // ADAPTED: WAWebSyncd uses module-level Set F; Cobalt derives from store state
            if (store.findWebAppState(patchType).state() == SyncCollectionState.PENDING) {
                result.add(patchType);
            }
        }
        return Collections.unmodifiableSet(result); // WAWebSyncd.pe: return F (Set)
    }

    /**
     * Reports the current syncd telemetry counters into the WAM aggregator.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.reportWam} (z/j): queries
     * {@code WAWebGetSyncAction.countSyncActionsInTransaction} and feeds the
     * {@code WAWebSyncdWamAppState} aggregator with stored mutation count, invalid
     * action count (Malformed state), unsupported action count, and missing key
     * count. The aggregator is later flushed by {@code WAWebSyncdReportSyncdStatJob}
     * which commits the {@code MdAppStateSyncMutationStats} and {@code SyncdKeyCount}
     * WAM events.
     *
     * <p>In Cobalt, the per-bucket {@code MdAppStateSyncMutationStats} reporting is
     * driven by {@link #reportSyncdStatsJob()} (which directly queries the store
     * and commits one event per mutation name) and the {@code SyncdKeyCount} reporting
     * is driven by {@link #reportSyncdKeyStatsJob()}. The {@code WAWebSyncdWamAppState}
     * intermediate aggregator is collapsed away because Cobalt's event pipeline does
     * not require the aggregate-then-flush pattern WA Web uses. This method exists to
     * keep the export surface complete and is a no-op pass-through that simply forwards
     * to the existing recurring job entry points.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "reportWam", adaptation = WhatsAppAdaptation.ADAPTED)
    public void reportWam() {
        reportSyncdStats(); // WAWebSyncd.j: setStoredMutationCount + setInvalidActionCount + setUnsupportedActionCount (commits MdAppStateSyncMutationStats per mutation name)
        reportSyncdKeyStats(); // WAWebSyncd.j: setMissingKeyCount (commits SyncdKeyCount)
    }

    /**
     * Logs syncd key information to the WhatsApp Web internal-only logging channel.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.logKeysInfoInIntern} (X/Y): the JS body
     * is an empty async generator ({@code function*(){}}) — the export exists as a
     * tree-shaken stub for the {@code intern}-build logging pipeline. There is no
     * runtime behavior in production WA Web bundles.
     *
     * <p>Cobalt has no separate internal-build channel, so this is a no-op.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "logKeysInfoInIntern", adaptation = WhatsAppAdaptation.DIRECT)
    public void logKeysInfoInIntern() {
        // WAWebSyncd.X: function*(){} — no-op
    }

    /**
     * Syncs multiple non-critical collections using a single batched IQ request.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdServerSync}: all dirty collections are
     * batched into a single IQ with one {@code <sync>} node containing multiple
     * {@code <collection>} children. This reduces round-trips compared to sending
     * one IQ per collection.
     *
     * <p>After the initial batched round, collections that need further pagination
     * (i.e. those with {@code has_more_patches}) fall back to individual sync loops.
     *
     * <p>Returns whether any collection in the batch contributed real state changes. The
     * return value is used by {@link #pullPatches} to surface a dirty-bit false-positive
     * signal to callers (notably {@code InfoBulletinStreamHandler} handling a
     * {@code syncd_app_state} dirty-bit notification).
     * @param patchTypes the non-critical collection types to sync
     * @return {@code true} if any response in the batch carried at least one patch or a
     *         snapshot reference; {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync", exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean syncCollectionsBatched(Set<SyncPatchType> patchTypes) {
        // WAWebSyncdServerSync.L: collectionsToSkip + collectionWithPendingMutationsIds
        var collectionPatches = new LinkedHashMap<SyncPatchType, SequencedCollection<SyncPendingMutation>>();
        var skippedUploads = new LinkedHashSet<SyncPatchType>(); // WAWebSyncdServerSync.L: collectionsToSkip
        for (var patchType : patchTypes) {
            var pending = whatsapp.store().findPendingMutations(patchType);
            if (!pending.isEmpty() && !store.findWebAppState(patchType).bootstrapped()) {
                skippedUploads.add(patchType); // WAWebSyncdServerSync.L: collectionsToSkip
                pending = List.of();
            }
            collectionPatches.put(patchType, pending);
        }

        // Mirror of the array passed to WAWebBackendEventBus.onceAppStateSyncCompleted in
        // WAWebSyncd; WAWebHandleDirtyBits.p inspects it via
        // `e.some(r => r.patches?.length > 0 || r.snapshot != null)`.
        var hasAppStateChanges = false;

        try {
            for (var patchType : patchTypes) {
                store.markWebAppStateInFlight(patchType); // ADAPTED: WAWebSyncd.ee: A = A.union(t)
            }

            // WAWebSyncdServerSync.L: build + send batched IQ
            var batchedRequest = requestBuilder.buildBatchedSyncRequest(collectionPatches);
            // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(REQUEST_BUILT) right before the IQ is dispatched.
            logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.REQUEST_BUILT);
            var responseNode = whatsapp.sendNode(batchedRequest.node()); // WAWebSyncdServerSync.L: deprecatedSendIq
            // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(RESPONSE_RECEIVED) after the IQ response is received (m.success).
            logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.RESPONSE_RECEIVED);
            var responses = responseParser.parseBatchedSyncResponse(responseNode); // WAWebSyncdServerSync.L: syncResponseParser
            // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(RESPONSE_PARSED_VALID) after syncResponseParser succeeds.
            logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.RESPONSE_PARSED_VALID);

            // WAWebSyncdServerSync.S: pre-filter ErrorRetry/ErrorFatal/Blocked -> done
            // Per WA Web WAWebSyncdResponseParser.h: collection-level errors are captured
            // on the response object rather than thrown, so each collection is handled independently.
            // Per WA Web WAWebSyncdServerSync.R: Conflict/ConflictHasMore are NOT in the
            // ErrorRetry/ErrorFatal/Blocked switch — they go through applyAppStateSyncResponse
            // (as empty responses) and then through the post-apply routing logic.
            for (var response : responses) {
                // WAWebSyncdServerSync.S: collections with error states go directly to done
                if (response.collectionError().isPresent()) {
                    var collectionError = response.collectionError().get();
                    // WAWebSyncdServerSync.R: Conflict/ConflictHasMore fall through to default
                    // and are handled in the post-apply routing, not as generic errors
                    if (collectionError instanceof WhatsAppWebAppStateSyncException.Conflict conflict) {
                        // WAWebSyncdServerSync.R: post-apply routing for Conflict
                        if (conflict.hasMorePatches()) {
                            // WAWebSyncdServerSync.R: ConflictHasMore -> always refetch
                            store.markWebAppStateDirty(response.collectionName()); // WAWebSyncdServerSync.R: refetchCollections
                        } else {
                            // WAWebSyncdServerSync.R: Conflict -> check pending
                            var hasPending = !whatsapp.store().findPendingMutations(response.collectionName()).isEmpty();
                            if (hasPending) {
                                store.markWebAppStateDirty(response.collectionName()); // WAWebSyncdServerSync.R: refetchCollections
                            } else {
                                store.markWebAppStateUpToDate(response.collectionName()); // WAWebSyncdServerSync.R: Conflict + !pending -> Success
                            }
                        }
                    } else {
                        handleSyncError(collectionError, response.collectionName()); // WAWebSyncdServerSync.S
                    }
                    continue;
                }

                // WAWebHandleDirtyBits.p: the onceAppStateSyncCompleted callback observes
                // `r.patches?.length > 0 || r.snapshot != null` per collection result.
                if (!response.patches().isEmpty() || response.snapshotReference().isPresent()) {
                    hasAppStateChanges = true;
                }

                try {
                    handleSyncResponse(response); // WAWebSyncdServerSync.S: applyAppStateSyncResponse
                    var uploadInfo = batchedRequest.uploadInfos().get(response.collectionName());
                    if (uploadInfo != null) {
                        // WAWebSyncdServerSync.S: skip upload processing for error/blocked states
                        var state = store.findWebAppState(response.collectionName()).state();
                        if (state != SyncCollectionState.ERROR_FATAL
                                && state != SyncCollectionState.ERROR_RETRY
                                && state != SyncCollectionState.BLOCKED) {
                            processUploadSuccess(uploadInfo);
                        }
                    }
                    retryScheduler.resetAttemptCounter();
                } catch (Throwable throwable) {
                    handleSyncError(throwable, response.collectionName()); // WAWebSyncdServerSync.S: catch
                }
            }
        } catch (Throwable throwable) {
            // WAWebSyncdServerSync.S: catch — error affects all collections in the batch
            for (var patchType : patchTypes) {
                handleSyncError(throwable, patchType);
            }
            return hasAppStateChanges;
        }

        // WAWebSyncdServerSync.S: post-apply routing — collections needing refetch
        for (var patchType : patchTypes) {
            // WAWebSyncdServerSync.S: Success && collectionsToUpload.some(t === e.name) -> refetch
            if (skippedUploads.contains(patchType)
                    && store.findWebAppState(patchType).bootstrapped()
                    && !whatsapp.store().findPendingMutations(patchType).isEmpty()) {
                store.markWebAppStateDirty(patchType);
            }
            // WAWebSyncdServerSync.serverSync: refetchCollections -> next iteration
            var state = store.findWebAppState(patchType).state();
            if (state == SyncCollectionState.PENDING || state == SyncCollectionState.DIRTY) {
                syncCollection(patchType); // WAWebSyncdServerSync.serverSync: individual retry loop
            }
        }
        return hasAppStateChanges;
    }

    /**
     * Retries orphan mutations across all collections.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.applyAllOrphansAndUnsupported}:
     * all orphan mutations are retried across every collection. This is used on
     * app resume to resolve any orphans accumulated while the app was suspended.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "applyAllOrphansAndUnsupported", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByActionStatesInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    public void retryAllOrphanMutations() {
        for (var patchType : SyncPatchType.values()) { // WAWebSyncdOrphan.applyAllOrphansAndUnsupported: getSyncActionsByActionStatesInTransaction([Orphan, Unsupported])
            retryOrphanMutations(patchType); // WAWebSyncdOrphan.applyAllOrphansAndUnsupported -> applyIndividualMutations (orphan portion)
        }
    }

    /**
     * Retries orphan mutations matching the specified message, chat, and thread identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanMutations}: when new messages
     * or chats become available (e.g. from history sync or incoming messages), orphans matching
     * those specific entity IDs are retried. Delegates to {@link #checkOrphanMessages},
     * {@link #checkOrphanChats}, account-level orphan retry, and {@link #checkOrphanThreads}
     * (when {@code threadIds} is non-empty) in parallel.
     *
     * <p>This method is also exposed as {@code SyncdBridgeApi.checkOrphanMutations} via the
     * {@code WAWebSyncdBridgeApi} facade, which destructures a {@code {chatIds, msgIds, threadIds}}
     * descriptor before delegating to {@code WAWebSyncdOrphan.checkOrphanMutations}.
     * @param msgIds    the message identifiers to match for {@code Msg}-type orphans
     * @param chatIds   the chat identifiers to match for {@code Chat}-type and {@code Account}-type orphans
     * @param threadIds the thread identifiers to match for {@code Thread}-type orphans; {@code null} or empty skips threads
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanMutations", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdBridgeApi", exports = "SyncdBridgeApi", adaptation = WhatsAppAdaptation.ADAPTED)
    public void checkOrphanMutations(Collection<String> msgIds, Collection<String> chatIds, Collection<String> threadIds) {
        checkOrphanMessages(msgIds); // WAWebSyncdOrphan.checkOrphanMutations: C(e)
        checkOrphanChats(chatIds); // WAWebSyncdOrphan.checkOrphanMutations: v(t)
        retryOrphanMutationsByModelIds(chatIds, "Account"); // WAWebSyncdOrphan.checkOrphanMutations: bulkGetAccountLid(t).then(R) — ADAPTED: Cobalt uses chatIds directly, WA Web resolves LID accounts first
        if (threadIds != null && !threadIds.isEmpty()) { // WAWebSyncdOrphan.checkOrphanMutations: r != null && r.length > 0 ? D(r) : void 0
            checkOrphanThreads(threadIds); // WAWebSyncdOrphan.checkOrphanMutations: D(r)
        }
    }

    /**
     * Retries orphan mutations of model type {@code Msg} matching the specified message identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanMessages}: enriches the key list
     * with additional LID message keys and (if forced history LID chat is enabled) history chat
     * ID message keys, then delegates to the private helper {@code E} with {@code SyncModelType.Msg}.
     * @param msgIds the message identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanMessages", adaptation = WhatsAppAdaptation.ADAPTED)
    public void checkOrphanMessages(Collection<String> msgIds) {
        if (msgIds == null || msgIds.isEmpty()) { // ADAPTED: defensive null check
            return;
        }
        retryOrphanMutationsByModelIds(msgIds, "Msg"); // WAWebSyncdOrphan.checkOrphanMessages -> E(keys, SyncModelType.Msg) — ADAPTED: Cobalt skips getAdditionalLidMsgKeys/getAdditionalHistoryChatIdMsgKeys enrichment (LID resolution handled by store)
    }

    /**
     * Retries orphan mutations of model type {@code Chat} matching the specified chat identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanChats}: enriches the key list
     * with additional history chat IDs (if forced history LID chat is enabled), then delegates
     * to the private helper {@code E} with {@code SyncModelType.Chat}.
     * @param chatIds the chat identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanChats", adaptation = WhatsAppAdaptation.ADAPTED)
    public void checkOrphanChats(Collection<String> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) { // ADAPTED: defensive null check
            return;
        }
        retryOrphanMutationsByModelIds(chatIds, "Chat"); // WAWebSyncdOrphan.checkOrphanChats -> E(keys, SyncModelType.Chat) — ADAPTED: Cobalt skips getAdditionalHistoryChatIds enrichment (LID resolution handled by store)
    }

    /**
     * Retries orphan mutations of model type {@code Thread} matching the specified thread identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanThreads}: delegates directly to the
     * private helper {@code E} with {@code SyncModelType.Thread}.
     * @param threadIds the thread identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanThreads", adaptation = WhatsAppAdaptation.DIRECT)
    public void checkOrphanThreads(Collection<String> threadIds) {
        if (threadIds == null || threadIds.isEmpty()) { // ADAPTED: defensive null check
            return;
        }
        retryOrphanMutationsByModelIds(threadIds, "Thread"); // WAWebSyncdOrphan.checkOrphanThreads -> E(e, SyncModelType.Thread)
    }

    /**
     * Retries orphan mutations of model type {@code Agent} matching the specified agent identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanAgents}: delegates directly to the
     * private helper {@code E} with {@code SyncModelType.Agent}.
     * @param agentIds the agent identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanAgents", adaptation = WhatsAppAdaptation.DIRECT)
    public void checkOrphanAgents(Collection<String> agentIds) {
        retryOrphanMutationsByModelIds(agentIds, "Agent"); // WAWebSyncdOrphan.checkOrphanAgents -> E(e, SyncModelType.Agent)
    }

    /**
     * Retries orphan mutations of model type {@code ChatAssignment} matching the specified assignment identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanChatAssignments}: delegates directly
     * to the private helper {@code E} with {@code SyncModelType.ChatAssignment}.
     * @param assignmentIds the chat assignment identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanChatAssignments", adaptation = WhatsAppAdaptation.DIRECT)
    public void checkOrphanChatAssignments(Collection<String> assignmentIds) {
        retryOrphanMutationsByModelIds(assignmentIds, "ChatAssignment"); // WAWebSyncdOrphan.checkOrphanChatAssignments -> E(e, SyncModelType.ChatAssignment)
    }

    /**
     * Retries orphan mutations of model type {@code UserStatusMute} matching the specified contact identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanUserStatusMutes}: delegates directly
     * to the private helper {@code E} with {@code SyncModelType.UserStatusMute}.
     * @param contactIds the contact identifiers to check
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanUserStatusMutes", adaptation = WhatsAppAdaptation.DIRECT)
    public void checkOrphanUserStatusMutes(Collection<String> contactIds) {
        retryOrphanMutationsByModelIds(contactIds, "UserStatusMute"); // WAWebSyncdOrphan.checkOrphanUserStatusMutes -> E(e, SyncModelType.UserStatusMute)
    }

    /**
     * Retries orphan mutations matching the specified model IDs and model type.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.E}: queries the orphan store for entries
     * matching both the entity key and the model type, then applies them via individual mutation
     * retry. This is the core helper that all type-specific orphan check methods delegate to.
     * @param modelIds  the entity identifiers to match
     * @param modelType the model type to filter by (e.g. {@code "Msg"}, {@code "Chat"})
     */
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByModelInfosInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryOrphanMutationsByModelIds(Collection<String> modelIds, String modelType) {
        if (modelIds == null || modelIds.isEmpty()) { // ADAPTED: defensive null check
            return;
        }

        var modelIdSet = modelIds instanceof Set ? (Set<String>) modelIds : new HashSet<>(modelIds); // ADAPTED: optimization for set lookup
        for (var patchType : SyncPatchType.values()) { // WAWebSyncdOrphan.E: getSyncActionsByModelInfosInTransaction — ADAPTED: Cobalt scans all patch types
            var orphans = store.findOrphanMutations(patchType); // WAWebSyncdOrphan.E: getSyncActionsByModelInfosInTransaction
            if (orphans.isEmpty()) {
                continue;
            }

            var matching = orphans.stream()
                    .filter(o -> modelType.equals(o.modelType()) && o.modelId() != null && modelIdSet.contains(o.modelId())) // WAWebSyncdOrphan.E: match by (key, modelType, Orphan)
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }

            store.removeOrphanMutations(patchType, matching); // ADAPTED: Cobalt removes before retrying
            retryOrphanEntries(patchType, matching); // WAWebSyncdOrphan.E -> applyIndividualMutations
        }
    }

    /**
     * Retries orphaned favorite sticker mutations if the favorite stickers feature is enabled.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanFavoriteStickers}: first checks
     * whether the {@code favorite_sticker} primary feature is enabled. If not, returns early.
     * Then delegates to the model-type-based orphan retry, gated by the
     * {@code favorite_sticker_sync_after_pairing_enabled_web} AB prop.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdOrphan", exports = "checkOrphanFavoriteStickers", adaptation = WhatsAppAdaptation.DIRECT)
    public void checkOrphanFavoriteStickers() {
        if (!store.primaryFeatures().contains("favorite_sticker")) { // WAWebSyncdOrphan.checkOrphanFavoriteStickers: isFavoriteStickersEnabled
            return; // WAWebSyncdOrphan.checkOrphanFavoriteStickers: not enabled, early return
        }
        retryOrphanMutationsByModelType("FavoriteSticker", () -> // WAWebSyncdOrphan.checkOrphanFavoriteStickers -> w(SyncModelType.FavoriteSticker, conditionFn)
                abPropsService.getBool(ABProp.FAVORITE_STICKER_SYNC_AFTER_PAIRING_ENABLED_WEB) // WAWebSyncdOrphan.checkOrphanFavoriteStickers: isFavoriteStickerSyncAfterPairingEnabled
        );
    }

    /**
     * Retries orphaned mutations matching a specific model type, optionally gated by a condition.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.w}: queries all orphan entries matching
     * the specified {@code modelType}, and if the optional {@code condition} evaluates to
     * {@code true} (or is {@code null}), applies them via individual mutation retry.
     * @param modelType the model type string to filter orphans by (e.g. {@code "favoriteSticker"})
     * @param condition an optional gate; if non-null and returns {@code false}, mutations are not applied
     */
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getOrphanSyncActionsByModelTypeInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryOrphanMutationsByModelType(String modelType, BooleanSupplier condition) {
        var allMatching = new ArrayList<Map.Entry<SyncPatchType, List<OrphanMutationEntry>>>(); // WAWebSyncdOrphan.w: getOrphanSyncActionsByModelTypeInTransaction
        for (var patchType : SyncPatchType.values()) {
            var orphans = store.findOrphanMutations(patchType);
            if (orphans.isEmpty()) {
                continue;
            }
            var matching = orphans.stream()
                    .filter(o -> modelType.equals(o.modelType()))
                    .toList();
            if (!matching.isEmpty()) {
                allMatching.add(Map.entry(patchType, matching));
            }
        }

        if (allMatching.isEmpty()) { // WAWebSyncdOrphan.w: r.length === 0 early return
            return;
        }

        if (condition != null && !condition.getAsBoolean()) { // WAWebSyncdOrphan.w: t != null && !t()
            return;
        }

        for (var entry : allMatching) { // WAWebSyncdOrphan.w: applyIndividualMutations(r)
            store.removeOrphanMutations(entry.getKey(), entry.getValue());
            retryOrphanEntries(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Resumes interrupted sync operations after an application restart.
     *
     * <p>Per WhatsApp Web behavior, the sync state machine must handle
     * restart recovery for collections that were mid-sync when the process
     * was interrupted:
     * <ul>
     *   <li>{@code IN_FLIGHT} — the request was lost; transition back to
     *       {@code DIRTY} so the next sync round re-sends it
     *   <li>{@code PENDING} — more data was available; mark as {@code DIRTY}
     *       to resume fetching
     *   <li>{@code BLOCKED} — still waiting for missing keys; re-schedule
     *       the timeout check
     *   <li>{@code ERROR_RETRY} — a retryable error occurred; mark as
     *       {@code DIRTY} to retry
     * </ul>
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "initializeStateMachine", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncd", exports = "processOnAppResume", adaptation = WhatsAppAdaptation.ADAPTED)
    public void resumeAfterRestart() {
        // ADAPTED: WAWebSyncd.ue: loadStatesFromDb() -> Cobalt stores state in-memory
        var collectionsToSync = new LinkedHashSet<SyncPatchType>();
        for (var patchType : SyncPatchType.values()) { // WAWebSyncd.ae: getCollectionsInStateDirty/Retry/Fatal
            var metadata = store.findWebAppState(patchType);
            switch (metadata.state()) {
                case IN_FLIGHT, PENDING, ERROR_RETRY -> { // WAWebSyncd.ae: dirty/retry state handling
                    store.markWebAppStateDirty(patchType); // WAWebSyncd.ae: moveCollectionsToDirty
                    collectionsToSync.add(patchType);
                }
                case BLOCKED -> { // WAWebSyncd.se: moveCollectionsToDirty(blocked)
                    missingSyncKeyTimeoutScheduler.scheduleTimeoutCheck(); // WAWebSyncdHandleMissingKeys
                    missingSyncKeyTimeoutScheduler.startPeriodicReRequestJob(); // WAWebSyncdHandleMissingKeys
                    store.markWebAppStateDirty(patchType); // WAWebSyncd.se: moveCollectionsToDirty(t)
                    collectionsToSync.add(patchType);
                }
                case DIRTY -> collectionsToSync.add(patchType); // WAWebSyncd.ae: getCollectionsInStateDirty -> Z()
                default -> {
                    // UP_TO_DATE, ERROR_FATAL: no action needed
                    // WAWebSyncd.ae: fatal -> handleSyncdFatal (Cobalt: fatal is terminal, no callback)
                }
            }

            // WAWebSyncd.se: getAllSyncPendingMutationsInTransaction -> combine with blocked
            if (!whatsapp.store().findPendingMutations(patchType).isEmpty()
                    && store.findWebAppState(patchType).state() != SyncCollectionState.ERROR_FATAL) {
                if (store.findWebAppState(patchType).state() == SyncCollectionState.UP_TO_DATE) {
                    store.markWebAppStateDirty(patchType); // WAWebSyncd.V: UpToDate -> moveCollectionsToDirty
                }
                collectionsToSync.add(patchType);
            }
        }

        retryAllOrphanMutations(); // WAWebSyncd.de: WAWebSyncdOrphan.applyAllOrphansAndUnsupported
        retryUnsupportedMutations(); // WAWebSyncd.de: WAWebSyncdOrphan.applyAllOrphansAndUnsupported (unsupported portion)

        if (!collectionsToSync.isEmpty()) { // WAWebSyncd.se: n.length > 0 && U(n)
            pullPatches(collectionsToSync.toArray(SyncPatchType[]::new)); // WAWebSyncd.V -> Z() -> ee()
        }
    }

    /**
     * Maximum number of sync iterations before giving up.
     */
    private static final int MAX_SYNC_ITERATIONS = 500; // WAWebSyncdServerSync.serverSync: C=500

    /**
     * Result of a single sync round, bundling the parsed response, optional upload
     * metadata, and whether the pending upload was skipped due to an unbootstrapped collection.
     * @param response            the parsed sync response
     * @param uploadInfo          the upload metadata, or {@code null} if no upload was included
     * @param skippedPendingUpload whether pending mutations were skipped due to unbootstrapped state
     */
    private record SyncRoundResult(
            MutationSyncResponse response,
            SyncRequest.UploadedPatchInfo uploadInfo,
            boolean skippedPendingUpload
    ) {
    }

    /**
     * Represents a pending sync action entry update (either put or remove) to be applied
     * after the version guard passes.
     * @param indexMac the index MAC identifying the entry
     * @param entry    the entry to store, or {@code null} for remove operations
     * @param remove   whether this is a remove operation
     */
    private record SyncActionEntryUpdate(
            byte[] indexMac,
            SyncActionEntry entry,
            boolean remove
    ) {
    }

    /**
     * Result of an LT-Hash computation, bundling the new hash and the sync action
     * entry updates that should be persisted if the version guard passes.
     * @param newHash the computed LT-Hash
     * @param updates the sync action entry updates
     */
    private record LtHashComputation(
            byte[] newHash,
            List<SyncActionEntryUpdate> updates
    ) {
    }

    /**
     * Syncs a single collection in a loop until it reaches {@code UP_TO_DATE} state
     * or an unrecoverable error occurs.
     * @param patchType the collection type to sync
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync", exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
    private void syncCollection(SyncPatchType patchType) {
        var iterations = 0; // WAWebSyncdServerSync.serverSync: l (single iteration counter, caps at C=500)
        while(store.findWebAppState(patchType).state() != SyncCollectionState.UP_TO_DATE) {
            // WAWebSyncdServerSync.serverSync: l < C (500) — total iteration cap
            if (++iterations > MAX_SYNC_ITERATIONS) {
                LOGGER.warning("Iteration cap reached for collection " + patchType + " after " + MAX_SYNC_ITERATIONS + " iterations");
                // WAWebSyncdServerSync.serverSync: max iterations -> ErrorRetry
                store.markWebAppStateErrorRetry(patchType);
                break;
            }

            try {
                // WAWebSyncdServerSync.S: yield L(i, r) — build and send IQ
                var syncResult = sendSyncRequestOrThrow(patchType);

                // WAWebSyncdServerSync.S: yield applyAppStateSyncResponse(e, ...)
                handleSyncResponse(syncResult.response());

                // WAWebSyncdServerSync.S: collectionsToUpload — upload success handling
                if (syncResult.uploadInfo() != null) {
                    processUploadSuccess(syncResult.uploadInfo());
                }

                // WAWebSyncdServerSync.S: Success && collectionsToUpload.some(t === e.name) -> refetch
                if (syncResult.skippedPendingUpload()
                        && store.findWebAppState(patchType).bootstrapped()
                        && !whatsapp.store().findPendingMutations(patchType).isEmpty()) {
                    store.markWebAppStateDirty(patchType);
                }

                retryScheduler.resetAttemptCounter(); // WAWebSyncdServerSync.S: success path
            } catch (WhatsAppWebAppStateSyncException.Conflict conflict) {
                // WAWebSyncdServerSync.S: ConflictHasMore -> always refetch (no pending check)
                // WAWebSyncdServerSync.S: Conflict + no pending -> Success (done)
                if (!conflict.hasMorePatches()) { // WAWebSyncdServerSync.S: only plain Conflict checks pending
                    var hasPending = !whatsapp.store().findPendingMutations(patchType).isEmpty();
                    if (!hasPending) {
                        store.markWebAppStateUpToDate(patchType); // WAWebSyncdServerSync.S: Conflict + !pending -> Success
                        break;
                    }
                }
                // WAWebSyncdServerSync.S: Conflict + pending / ConflictHasMore -> refetch (next iteration)
            } catch (Throwable throwable) {
                // WAWebSyncdServerSync.S: catch — fatal or retryable error handling
                handleSyncError(throwable, patchType);
                break;
            }
        }
    }

    /**
     * Builds and sends a sync IQ request for a single collection, returning
     * the parsed response together with upload metadata.
     * @param patchType the collection to sync
     * @return the sync round result
     */
    private SyncRoundResult sendSyncRequestOrThrow(SyncPatchType patchType) {
        // Get pending mutations
        var pending = whatsapp.store()
                .findPendingMutations(patchType); // WAWebSyncdServerSync.L: collectionWithPendingMutationsIds
        var skippedPendingUpload = false;

        // WAWebSyncdServerSync.L: collectionsToSkip — skip pending for unbootstrapped collections
        if (!pending.isEmpty() && !store.findWebAppState(patchType).bootstrapped()) {
            LOGGER.fine("Skipping pending mutations for unbootstrapped collection " + patchType);
            skippedPendingUpload = true;
            pending = List.of();
        }

        // WAWebSyncdServerSync.L: yield WAWebSyncdRequestBuilder(e, t)
        var syncRequest = requestBuilder.buildSyncRequest(patchType, pending);

        // ADAPTED: WAWebSyncd tracks in-flight collections in a Set (A); Cobalt uses state machine
        store.markWebAppStateInFlight(patchType);

        // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(REQUEST_BUILT) right before the IQ is dispatched.
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.REQUEST_BUILT);
        // WAWebSyncdServerSync.L: yield deprecatedSendIq(u, syncResponseParser) — blocking on virtual thread
        var response = whatsapp.sendNode(syncRequest.node()); // WAWebSyncdServerSync.L
        // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(RESPONSE_RECEIVED) after the IQ response is received (m.success).
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.RESPONSE_RECEIVED);

        // WAWebSyncdServerSync.L: syncResponseParser parses the IQ response
        var parsedResponse = responseParser.parseSyncResponse(response); // WAWebSyncdServerSync.L
        // WAWebSyncdServerSync.L: logCriticalBootstrapStageIfNecessary(RESPONSE_PARSED_VALID) after syncResponseParser succeeds.
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.RESPONSE_PARSED_VALID);
        return new SyncRoundResult(parsedResponse, syncRequest.uploadInfo(), skippedPendingUpload);
    }

    /**
     * Processes a sync response for a single collection, applying snapshots,
     * patches, and state transitions.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler.applyAppStateSyncResponse}:
     * first processes any snapshot (download, decrypt, verify MAC, apply mutations),
     * then processes patches in version order (download externals, decrypt, verify
     * integrity, compute LT-Hash, apply mutations). Finally transitions the collection
     * to the appropriate state based on the response's {@code has_more_patches} flag.
     * @param syncResponse the parsed sync response for a single collection
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandler", exports = "applyAppStateSyncResponse", adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleSyncResponse(MutationSyncResponse syncResponse) {
        var collectionName = syncResponse.collectionName();
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: capture pre-sync state
        // for missing patches check (WA Web's `n` parameter is the local version BEFORE sync)
        var wasBootstrapped = store.findWebAppState(collectionName).bootstrapped(); // WAWebSyncdCollectionUtils.isBootstrap(n): n != null means bootstrapped
        var localVersionBeforeSync = getCurrentVersion(collectionName); // WAWebSyncdCollectionHandler.Ie: n (localVersion parameter)

        // WAWebSyncdCollectionHandler.Fe (downloadExternalSyncData): capture download
        // start timestamp and total external blob size before any CDN download, so that
        // reportSyncdBootstrapAppStateDownloadMetric can be emitted after Promise.all
        // completes (success) or in the outer catch (failure). Only tracked for the
        // bootstrap branch — WA Web gates the emission on isBootstrap(existingVersion).
        var bootstrapDownload = wasBootstrapped ? null : new BootstrapDownloadTracker(collectionName); // WAWebSyncdCollectionHandler.Fe: u = unixTimeMs(), s = 0
        if (bootstrapDownload != null) {
            // WAWebSyncdCollectionHandler.Fe: s += WALongInt.numberOrThrowIfTooLarge(snapshotRef.fileSizeBytes ?? 0)
            if (syncResponse.snapshotReference().isPresent()) {
                var snapshotSize = syncResponse.snapshotReference().get().fileSizeBytes();
                if (snapshotSize.isPresent()) {
                    bootstrapDownload.addBytes(snapshotSize.getAsLong());
                }
            }
            // WAWebSyncdCollectionHandler.Fe: patches.forEach(p => { if (p.externalMutations) s += ... })
            for (var patch : syncResponse.patches()) {
                if (patch.externalMutations().isPresent()) {
                    var patchSize = patch.externalMutations().get().fileSizeBytes();
                    if (patchSize.isPresent()) {
                        bootstrapDownload.addBytes(patchSize.getAsLong());
                    }
                }
            }
        }

        try {
            handleSyncResponseInternal(syncResponse, collectionName, wasBootstrapped, localVersionBeforeSync);
        } catch (Throwable throwable) {
            // WAWebSyncdCollectionHandler.Fe: try { await Promise.all([l,m]); ...success }
            //   catch { reportSyncdBootstrapAppStateDownloadMetric({..., isSuccess: "failure"}); throw }
            if (bootstrapDownload != null) {
                emitBootstrapAppStateDataDownloaded(bootstrapDownload, MdBootstrapStepResult.FAILURE, throwable);
            }
            throw throwable;
        }

        // WAWebSyncdCollectionHandler.Fe: reportSyncdBootstrapAppStateDownloadMetric({..., isSuccess: "success"})
        // after Promise.all([downloadSnapshot, downloadPatches]) resolves.
        if (bootstrapDownload != null) {
            emitBootstrapAppStateDataDownloaded(bootstrapDownload, MdBootstrapStepResult.SUCCESS, null);
        }
    }

    /**
     * Performs the snapshot and patch processing for a single collection sync
     * response, without the bootstrap download telemetry tracking.
     *
     * <p>Extracted from {@link #handleSyncResponse} so that the bootstrap
     * download metric can wrap this body in a single try/catch that mirrors
     * WA Web's {@code downloadExternalSyncData} (Fe) emission pattern:
     * success after the downloads complete, failure in the outer catch.
     * @param syncResponse          the parsed sync response for a single collection
     * @param collectionName        the collection being processed
     * @param wasBootstrapped       {@code true} if the collection was already bootstrapped
     * @param localVersionBeforeSync the local collection version before this sync round
     */
    private void handleSyncResponseInternal(
            MutationSyncResponse syncResponse,
            SyncPatchType collectionName,
            boolean wasBootstrapped,
            long localVersionBeforeSync
    ) {
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: h = performance.now() captured
        // right before the snapshot/patch apply phase, used to compute the step duration
        // reported via WAWebSyncdMetrics.reportSyncdBootstrapDataApplied.
        var applyStartTs = System.currentTimeMillis();
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: f = snapshot object (truthy
        // when a snapshot was sent by the server). Snapshot-recovered rounds also set
        // recoveredFromSnapshot=true below; usedSnapshot is reported for either path to
        // mirror WA Web's emission which only gates on whether the server payload carried
        // a snapshot, not on which apply branch was taken.
        var snapshotAppliedFromServer = syncResponse.snapshotReference().isPresent();
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: g = patches array returned
        // from Ae() (downloadExternalSyncData). Truthy in WA Web when patches were sent,
        // regardless of whether any mutations were materially applied.
        var patchesPresent = !syncResponse.patches().isEmpty();
        // Phase A: Process snapshot if present
        var recoveredFromSnapshot = false;
        if (syncResponse.snapshotReference().isPresent()) {
            if (syncResponse.version() <= 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Snapshot missing required version in " + collectionName, null);
            }

            store.clearSyncActionEntries(collectionName);

            var snapshot = downloadAndDecodeSnapshot(syncResponse.snapshotReference().get());
            // Per WA Web WAWebSyncdCollectionHandler: after decoding the snapshot, the
            // SyncdSnapshot.version.version field is independently validated with
            // `new SyncdFatalError("missing snapshot version")`. The XML collection
            // version checked above is separate from this protobuf-level check.
            var snapshotVersionEntry = snapshot.version().orElse(null); // WAWebSyncdCollectionHandler: SyncdSnapshot.version
            var snapshotProtoVersion = snapshotVersionEntry == null
                    ? 0L
                    : snapshotVersionEntry.version().orElse(0L); // WAWebSyncdCollectionHandler: SyncdSnapshot.version.version
            if (snapshotProtoVersion <= 0L) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("missing snapshot version", null); // WAWebSyncdCollectionHandler — SyncdFatalError
            }
            var snapshotMutations = getMutationsFromSnapshot(snapshot);
            var untrusted = snapshotMutations.isEmpty()
                    ? List.<DecryptedMutation.Untrusted>of()
                    : decryptMutations(snapshotMutations);

            // WAWebSyncdCollectionHandler._applySnapshotAndPatches: after decrypting snapshot mutations,
            // iterate and emit per-mutation MdSyncdMutationWamEvent via
            // WAWebSyncdWamReportingUtils.syncReportMutationToWam (called with isPatch=false, incoming=true).
            emitSyncdMutationWamEvents(
                    collectionName,
                    syncResponse.version(),
                    MutationDirectionType.INCOMING,
                    MutationBundleType.SNAPSHOT,
                    untrusted,
                    null); // snapshot has no patchMac

            // WAWebSyncdCollectionHandler._applySnapshotAndPatches: after decrypting snapshot mutations,
            // reportSyncdDecryptedMutations logs per-chat message range sizes via WAM.
            reportDecryptedMutationMessageRanges(untrusted);

            if (!untrusted.isEmpty()) {
                validateNoDuplicateIndices(collectionName, untrusted, false);
            }

            var newHash = computeNewLTHash(collectionName, MutationLTHash.EMPTY_HASH, untrusted);

            try {
                integrityVerifier.verifySnapshotMac(collectionName, syncResponse.version(), snapshot, newHash.newHash());

                var versionApplied = updateCollectionState(collectionName, syncResponse.version(), newHash.newHash()); // WAWebSyncdCollectionHandler.ot
                if (versionApplied) { // WAWebSyncdCollectionHandler.ot: only persist if version guard passes
                    applySyncActionEntryUpdates(collectionName, newHash.updates());
                }
                if (!untrusted.isEmpty()) {
                    var snapshotTrusted = new ArrayList<DecryptedMutation.Trusted>(untrusted.size());
                    for (var entry : untrusted) {
                        snapshotTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
                    }
                    applyMutations(collectionName, snapshotTrusted); // WAWebSyncdCollectionHandler.Xe: handlers run regardless of version guard
                }
            } catch (WhatsAppWebAppStateSyncException e) {
                if (!e.isFatal() || !snapshotRecoveryService.shouldAttemptRecovery(collectionName, snapshotMutations.size())) {
                    throw e;
                }

                var recoveredSnapshot = snapshotRecoveryService.requestRecovery(collectionName);
                if (recoveredSnapshot == null) {
                    throw e;
                }

                // Per WAWebNonMessageDataRequestHandler.m, the decode is performed once
                // by the protocol message handler before resolving the recovery promise;
                // requestRecovery now returns the already-decoded snapshot to match.
                var recoveredName = recoveredSnapshot.collectionName()
                        .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                                "Recovery response missing collection name for " + collectionName,
                                null
                        ));
                if (!recoveredName.equals(collectionName.toString())) {
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery response collection mismatch: expected " + collectionName + " but got " + recoveredName, null);
                }

                store.clearSyncActionEntries(collectionName);

                var recoveredTrusted = processRecoveredSnapshot(collectionName, recoveredSnapshot);
                if (!recoveredTrusted.isEmpty()) {
                    applyMutations(collectionName, recoveredTrusted);
                }
                recoveredFromSnapshot = true;
            }
        }

        if (recoveredFromSnapshot) {
            // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: reportSyncdBootstrapDataApplied
            // is emitted on every bootstrap round of a non-critical collection that received a
            // server payload, regardless of which apply branch was taken. The recovered-snapshot
            // branch counts as usedSnapshot=true because the server did send a snapshot.
            emitBootstrapDataAppliedIfNeeded(
                    collectionName,
                    wasBootstrapped,
                    snapshotAppliedFromServer,
                    patchesPresent,
                    true,
                    applyStartTs);
            store.markWebAppStateUpToDate(collectionName);
            return;
        }

        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse Case 4: no snapshot, no patches, no upload
        // Only initialize bootstrap (version 0, empty LT-Hash) when there is truly nothing to process.
        // WA Web: `else { isBootstrap(n) && updateCollectionVersionAndLtHashInTransaction(i, Re, Le) }`
        if (syncResponse.snapshotReference().isEmpty()
                && syncResponse.patches().isEmpty()
                && !wasBootstrapped) { // WAWebSyncdCollectionUtils.isBootstrap(n): n == null
            updateCollectionState(collectionName, 0L, MutationLTHash.EMPTY_HASH); // WAWebSyncdCollectionHandler.Ie: updateCollectionVersionAndLtHashInTransaction(i, Re=0, Le=new ArrayBuffer(128))
        }

        var sortedPatches = new ArrayList<>(syncResponse.patches());
        sortedPatches.sort(Comparator.comparingLong(patch -> patch.version()
                .map(version -> version.version().orElse(0L))
                .orElse(0L)));

        validateNoDuplicatePatchVersions(collectionName, sortedPatches);

        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: missing patches detection
        // WA Web: `var $ = n != null && x > n + 1 && g.length > 0`
        // `n != null` maps to `wasBootstrapped` (collection has been synced before)
        if (!sortedPatches.isEmpty()) {
            long minPatchVersion = sortedPatches.getFirst().version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L);
            if (wasBootstrapped && minPatchVersion > localVersionBeforeSync + 1) { // WAWebSyncdCollectionHandler.Ie: n != null && x > n + 1
                throw new WhatsAppWebAppStateSyncException.MissingPatches(collectionName, localVersionBeforeSync, minPatchVersion);
            }
        }

        for (var patch : sortedPatches) {
            if (patch.exitCode().isPresent()) {
                throw new WhatsAppWebAppStateSyncException.TerminalPatch(collectionName, patch.exitCode().get());
            }

            var patchVer = patch.version()
                    .map(v -> v.version().orElse(0L))
                    .orElse(0L);
            if (patchVer <= 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Patch missing required version field in " + collectionName, null);
            }
            if (patch.patchMac().isEmpty() || patch.patchMac().get().length == 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Patch missing required patchMac field in " + collectionName + " at version " + patchVer, null);
            }
            if (patch.snapshotMac().isEmpty() || patch.snapshotMac().get().length == 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Patch missing required snapshotMac field in " + collectionName + " at version " + patchVer, null);
            }

            var patchMutations = getMutationsFromPatch(patch);
            if (patchMutations.isEmpty()) {
                continue;
            }

            var untrusted = decryptMutations(patchMutations);
            // WAWebSyncdCollectionHandler._applyPatch: after decrypting patch mutations,
            // iterate and emit per-mutation MdSyncdMutationWamEvent via
            // WAWebSyncdWamReportingUtils.syncReportMutationToWam (isPatch=true, incoming=true).
            emitSyncdMutationWamEvents(
                    collectionName,
                    patch.version().map(v -> v.version().orElse(0L)).orElse(0L),
                    MutationDirectionType.INCOMING,
                    MutationBundleType.PATCH,
                    untrusted,
                    patch.patchMac().orElse(null));

            // WAWebSyncdCollectionHandler._applyPatch: after decrypting patch mutations,
            // reportSyncdDecryptedMutations logs per-chat message range sizes via WAM.
            reportDecryptedMutationMessageRanges(untrusted);
            validateNoDuplicateIndices(collectionName, untrusted, true);

            var currentHash = getCurrentLTHash(collectionName);
            long patchVersion = patch.version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L);
            if (patchVersion > 1 && Arrays.equals(currentHash, MutationLTHash.EMPTY_HASH)) {
                throw new WhatsAppWebAppStateSyncException.RetryableServerError(
                        "Empty LT-Hash for non-bootstrap patch version " + patchVersion + " in " + collectionName);
            }

            var newHash = computeNewLTHash(collectionName, currentHash, untrusted);

            // @implNote WAWebSyncdAntiTampering.validateSnapshotMac — hoisted guard:
            // when the collection is already in mac-mismatch state, the snapshot MAC validation
            // is skipped entirely (matches the `isCollectionInMacMismatchFatal` early-return in
            // function G). Hoisting the check here avoids the per-patch valueMacs list allocation
            // and the HMAC setup performed inside verifyPatchIntegrity. The internal guard inside
            // MutationIntegrityVerifier.verifyPatchIntegrity is retained as defense in depth.
            if (!store.isCollectionInMacMismatchFatal(collectionName)) { // WAWebGetCollectionVersion.getIsCollectionInMacMismatchFatalInTransaction: n.get(e).then(e => e?.isCollectionInMacMismatchFatal)
                var patchValueMacs = untrusted.stream()
                        .map(DecryptedMutation.Untrusted::valueMac)
                        .toList();
                var snapshotMacValid = integrityVerifier.verifyPatchIntegrity(collectionName, patch, newHash.newHash(), patchValueMacs);
                if (!snapshotMacValid) {
                    store.markWebAppStateMacMismatch(collectionName); // WAWebGetCollectionVersion.updateIsCollectionInMacMismatchFatalInTransaction: n.update(e, {isCollectionInMacMismatchFatal: !0})
                    LOGGER.warning("Patch snapshot MAC mismatch for " + collectionName + " at version " + patchVersion + ", marking mac-mismatch");
                }
            }

            var patchVersionApplied = updateCollectionState(collectionName, patchVersion, newHash.newHash()); // WAWebSyncdCollectionHandler.ot
            if (patchVersionApplied) { // WAWebSyncdCollectionHandler.ot: only persist if version guard passes
                applySyncActionEntryUpdates(collectionName, newHash.updates());
            }

            var ordered = deduplicateAndOrder(untrusted);
            var patchTrusted = new ArrayList<DecryptedMutation.Trusted>(ordered.size());
            for (var entry : ordered) {
                patchTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
            }
            if (!patchTrusted.isEmpty()) {
                applyMutations(collectionName, patchTrusted); // WAWebSyncdCollectionHandler.Xe: handlers run regardless of version guard
            }
        }

        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse:
        //   if ((g||f) && isBootstrap(n) && !isCriticalCollection(i)) {
        //     reportSyncdBootstrapDataApplied(i, f != null ? SNAPSHOT_USED : SNAPSHOT_NOT_USED, O);
        //   }
        // Emitted after all snapshot/patch application for the non-critical collections that
        // were bootstrapped this round. Critical collections fire their own equivalent via
        // WAWebSyncBootstrap.setSyncDCriticalDataSyncCompleted, which has no Cobalt counterpart.
        emitBootstrapDataAppliedIfNeeded(
                collectionName,
                wasBootstrapped,
                snapshotAppliedFromServer,
                patchesPresent,
                snapshotAppliedFromServer,
                applyStartTs);

        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: state transition
        // WA Web returns the response to the caller which checks hasMorePatches for routing.
        // The hasMore flag must always be checked, even when no mutations were received,
        // because the server may send an empty batch with hasMore=true indicating more data.
        if (syncResponse.hasMore()) { // WAWebSyncdServerSync.R: hasMorePatches -> Pending
            store.markWebAppStatePending(collectionName);
        } else { // WAWebSyncdServerSync.R: Success -> UP_TO_DATE
            store.markWebAppStateUpToDate(collectionName);
        }
    }

    /**
     * Emits a {@code MdBootstrapDataAppliedEvent} for the app-state branch when
     * a non-critical collection's first-time bootstrap round finishes applying.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdCollectionHandler.applyAppStateSyncResponse}: at the end
     * of the function, guarded by
     * {@code (g || f) && isBootstrap(n) && !isCriticalCollection(i)}, the
     * handler invokes
     * {@code WAWebSyncdMetrics.reportSyncdBootstrapDataApplied(i, f != null
     * ? SNAPSHOT_USED : SNAPSHOT_NOT_USED, O)} which delegates to
     * {@code WAWebCollectionHandlerWamMutation.logMetricsForDataApplied},
     * constructing and committing a {@code MdBootstrapDataAppliedWamEvent}
     * with {@code mdBootstrapPayloadType=NON_CRITICAL},
     * {@code mdBootstrapSource=APP_STATE},
     * {@code collection=collectionNameToMetric(e)},
     * {@code mdBootstrapStepDuration=n},
     * {@code usedSnapshot=(t===SNAPSHOT_USED)},
     * {@code mdSessionId=MdSyncFieldStatsMeta.getMdSessionId()} and
     * {@code mdTimestamp=unixTimeMs()}.
     *
     * <p>Only fires on bootstrap rounds (the first time a collection is synced)
     * of non-critical collections ({@code REGULAR}, {@code REGULAR_LOW},
     * {@code REGULAR_HIGH}). Critical collections fire
     * {@code MdBootstrapDataAppliedEvent} with
     * {@code mdBootstrapPayloadType=CRITICAL} through
     * {@code WAWebSyncBootstrap.setSyncDCriticalDataSyncCompleted}, for which
     * Cobalt has no equivalent global flag (bootstrap tracking lives at the
     * per-collection {@code SyncCollectionMetadata.bootstrapped} level).
     * @param collectionName          the collection being bootstrapped
     * @param wasBootstrapped         {@code true} if the collection was already
     *                                bootstrapped before this round (emission
     *                                is skipped)
     * @param snapshotAppliedFromServer {@code true} if the server payload
     *                                  carried a snapshot reference for this
     *                                  round
     * @param patchesPresent          {@code true} if the server payload carried
     *                                any patches for this round
     * @param usedSnapshot            value of the {@code usedSnapshot} property
     *                                ({@code true} when a snapshot was applied,
     *                                including the recovered-snapshot path)
     * @param applyStartTs            the millisecond timestamp captured at the
     *                                start of the apply phase, used to compute
     *                                {@code mdBootstrapStepDuration}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandler", exports = "applyAppStateSyncResponse", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetrics", exports = "reportSyncdBootstrapDataApplied", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCollectionHandlerWamMutation", exports = "logMetricsForDataApplied", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitBootstrapDataAppliedIfNeeded(
            SyncPatchType collectionName,
            boolean wasBootstrapped,
            boolean snapshotAppliedFromServer,
            boolean patchesPresent,
            boolean usedSnapshot,
            long applyStartTs
    ) {
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: (g||f)
        if (!snapshotAppliedFromServer && !patchesPresent) {
            return;
        }
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: isBootstrap(n)
        // In WA Web `isBootstrap(n)` is `n == null`, i.e. the collection has never
        // been bootstrapped before this round — equivalent to !wasBootstrapped here.
        if (wasBootstrapped) {
            return;
        }
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: !isCriticalCollection(i)
        if (collectionName.isCritical()) {
            return;
        }
        var now = System.currentTimeMillis();
        // WAWebSyncdCollectionHandler.applyAppStateSyncResponse: O = floor(performance.now() - h)
        var duration = (int) (now - applyStartTs);
        wamService.commit(new MdBootstrapDataAppliedEventBuilder()
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   mdBootstrapPayloadType: MD_BOOTSTRAP_PAYLOAD_TYPE.NON_CRITICAL
                .mdBootstrapPayloadType(MdBootstrapPayloadType.NON_CRITICAL)
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   mdBootstrapSource: MD_BOOTSTRAP_SOURCE.APP_STATE
                .mdBootstrapSource(MdBootstrapSource.APP_STATE)
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   collection: WAWebSyncdMetrics.collectionNameToMetric(e)
                .collection(mapCollection(collectionName))
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   mdBootstrapStepDuration: n (the O value passed from applyAppStateSyncResponse)
                .mdBootstrapStepDuration(duration)
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   usedSnapshot: t === SyncdBootstrapDataAppliedSnapshotUsed.SNAPSHOT_USED
                .usedSnapshot(usedSnapshot)
                // WAWebCollectionHandlerWamMutation.logMetricsForDataApplied:
                //   mdTimestamp: WATimeUtils.unixTimeMs()
                .mdTimestamp((int) now)
                // NO_WA_BASIS: WA Web populates mdSessionId via
                // MdSyncFieldStatsMeta.getMdSessionId() which hashes primary +
                // companion identity keys; Cobalt has no equivalent derivation
                // so mdSessionId is omitted, mirroring MdBootstrapAppStateDataDownloadedEvent
                // and MdBootstrapAppStateCriticalDataProcessingEvent.
                .build());
    }

    /**
     * Maps a {@link SyncPatchType} collection to the corresponding
     * {@link com.github.auties00.cobalt.wam.type.Collection} WAM enum constant
     * used by
     * {@link MdBootstrapDataAppliedEventBuilder#collection(com.github.auties00.cobalt.wam.type.Collection)}.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdMetrics.collectionNameToMetric}: the exhaustive match
     * throws on unknown collection names. Cobalt's enum encodes the closed
     * domain, so the wildcard branch is unreachable.
     * @param collectionName the non-{@code null} collection to map
     * @return the matching WAM {@link com.github.auties00.cobalt.wam.type.Collection}
     *         enum constant
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetrics", exports = "collectionNameToMetric", adaptation = WhatsAppAdaptation.DIRECT)
    private static com.github.auties00.cobalt.wam.type.Collection mapCollection(SyncPatchType collectionName) {
        return switch (collectionName) {
            // WAWebSyncdMetrics.collectionNameToMetric: CriticalBlock -> CRITICAL_BLOCK
            case CRITICAL_BLOCK -> com.github.auties00.cobalt.wam.type.Collection.CRITICAL_BLOCK;
            // WAWebSyncdMetrics.collectionNameToMetric: CriticalUnblockLow -> CRITICAL_UNBLOCK_LOW
            case CRITICAL_UNBLOCK_LOW -> com.github.auties00.cobalt.wam.type.Collection.CRITICAL_UNBLOCK_LOW;
            // WAWebSyncdMetrics.collectionNameToMetric: Regular -> REGULAR
            case REGULAR -> com.github.auties00.cobalt.wam.type.Collection.REGULAR;
            // WAWebSyncdMetrics.collectionNameToMetric: RegularHigh -> REGULAR_HIGH
            case REGULAR_HIGH -> com.github.auties00.cobalt.wam.type.Collection.REGULAR_HIGH;
            // WAWebSyncdMetrics.collectionNameToMetric: RegularLow -> REGULAR_LOW
            case REGULAR_LOW -> com.github.auties00.cobalt.wam.type.Collection.REGULAR_LOW;
        };
    }

    /**
     * Maps a {@link SyncPatchType} collection to the {@link SyncdCollectionType}
     * WAM enum constant used by {@code MdSyncdMutationEvent} and siblings.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdWamReportingUtils.h} (unnamed local mapping function),
     * the mapping is one-to-one between {@code WASyncdConst.CollectionName.*} and
     * {@code WAWebWamEnumSyncdCollectionType.SYNCD_COLLECTION_TYPE.*}.
     * @param collectionName the non-{@code null} collection to map
     * @return the matching {@link SyncdCollectionType} enum constant
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamReportingUtils", exports = "h", adaptation = WhatsAppAdaptation.DIRECT)
    private static SyncdCollectionType mapSyncdCollectionType(SyncPatchType collectionName) {
        return switch (collectionName) {
            case CRITICAL_BLOCK -> SyncdCollectionType.CRITICAL_BLOCK;
            case CRITICAL_UNBLOCK_LOW -> SyncdCollectionType.CRITICAL_UNBLOCK_LOW;
            case REGULAR -> SyncdCollectionType.REGULAR;
            case REGULAR_HIGH -> SyncdCollectionType.REGULAR_HIGH;
            case REGULAR_LOW -> SyncdCollectionType.REGULAR_LOW;
        };
    }

    /**
     * Emits one {@code MdSyncdMutationEvent} per decrypted mutation for an
     * incoming snapshot or patch.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdCollectionHandler._applySnapshotAndPatches} (snapshot
     * branch) and {@code _applyPatch} (patch branch), after decrypting the
     * mutations the handler iterates them and calls
     * {@code WAWebSyncdWamReportingUtils.syncReportMutationToWam(collection,
     * version, /*incoming*&#47;true, base64(indexMac), mutationName, isRemove,
     * /*isPatch*&#47;bundleIsPatch, mdSessionId, base64(patchMac))}. The reporter
     * constructs and commits a {@code MdSyncdMutationWamEvent} per mutation
     * when the {@code syncd_mutation_and_bundle_logging} AB prop allowlist
     * includes the collection.
     *
     * <p>Cobalt omits the AB allowlist gate (matches Cobalt's project-wide WAM
     * emission policy: always emit; filtering is handled upstream) and derives
     * each property from the decrypted mutation directly.
     * @param collectionName      the collection the mutations belong to
     * @param seqNumber           the patch/snapshot seq number (version)
     * @param direction           {@link MutationDirectionType#INCOMING} for
     *                            snapshot/patch apply,
     *                            {@link MutationDirectionType#OUTGOING} for
     *                            upload ack
     * @param bundle              {@link MutationBundleType#SNAPSHOT} for the
     *                            snapshot branch,
     *                            {@link MutationBundleType#PATCH} for the
     *                            patch branch
     * @param mutations           the decrypted mutations (post-verify but
     *                            pre-apply, matching the WA Web call ordering)
     * @param patchMac            the wire patch MAC for PATCH bundles, or
     *                            {@code null} for SNAPSHOT bundles. Base64-url
     *                            encoded on emission to match WA Web's
     *                            {@code WABase64.encodeB64UrlSafe} formatting
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamReportingUtils", exports = "syncReportMutationToWam", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitSyncdMutationWamEvents(
            SyncPatchType collectionName,
            long seqNumber,
            MutationDirectionType direction,
            MutationBundleType bundle,
            SequencedCollection<DecryptedMutation.Untrusted> mutations,
            byte[] patchMac) {
        if (mutations.isEmpty()) {
            return;
        }
        var syncdCollection = mapSyncdCollectionType(collectionName);
        var seqNumberInt = (int) seqNumber; // WAWebSyncdWamReportingUtils.syncReportMutationToWam: seqNumber: t (JS number)
        var patchMacStr = patchMac == null || patchMac.length == 0
                ? ""
                : Base64.getUrlEncoder().withoutPadding().encodeToString(patchMac); // WABase64.encodeB64UrlSafe
        for (var mutation : mutations) {
            // WAWebSyncdActionUtils.getMutationNameFromIndex: parseIndex(e, t)?.[0]
            var mutationName = SyncdIndexUtils
                    .getMutationNameFromIndex(null, mutation.index());
            if (mutationName == null || mutationName.isBlank()) {
                mutationName = "no-mutation-name"; // WAWebSyncdWamReportingUtils.syncReportMutationToWam: a != null ? a : "no-mutation-name"
            }
            // WAWebSyncdWamReportingUtils.syncReportMutationToWam: mutationMac: WABase64.encodeB64UrlSafe(indexMac)
            var mutationMac = Base64.getUrlEncoder().withoutPadding().encodeToString(mutation.indexMac());
            // WAWebSyncdWamReportingUtils.syncReportMutationToWam:
            //   mutationOperation: i ? REMOVE : SET (i = operation === REMOVE)
            var mutationOperation = mutation.operation() == SyncdOperation.REMOVE
                    ? MutationOperationType.REMOVE
                    : MutationOperationType.SET;
            wamService.commit(new MdSyncdMutationEventBuilder() // WAWebMdSyncdMutationWamEvent.MdSyncdMutationWamEvent
                    .contentLength(0) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: contentLength:0 (literal)
                    .isInBootstrap(false) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: isInBootstrap:!1 (literal)
                    .isUsingLid(false) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: isUsingLid:!1 (literal)
                    .mutationBundle(bundle) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: l ? PATCH : SNAPSHOT
                    .mutationDirection(direction) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: n ? INCOMING : OUTGOING
                    .mutationMac(mutationMac) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: mutationMac: r (b64url(indexMac))
                    .mutationName(mutationName) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: mutationName: a != null ? a : "no-mutation-name"
                    .mutationOperation(mutationOperation)
                    .seqNumber(seqNumberInt) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: seqNumber: t
                    .syncdCollection(syncdCollection) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: syncdCollection: h(e)
                    .syncdKeyhash("") // WAWebSyncdWamReportingUtils.syncReportMutationToWam: syncdKeyhash:"" (literal)
                    .syncdKeyid("") // WAWebSyncdWamReportingUtils.syncReportMutationToWam: syncdKeyid:"" (literal)
                    .patchMac(patchMacStr) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: patchMac: c != null ? c : ""
                    // NO_WA_BASIS: appSessionId — WA Web calls getSharedSessionId(); Cobalt has no equivalent derivation (same omission as MdBootstrapDataAppliedEvent in this file)
                    // NO_WA_BASIS: companionSessionIds — WA Web uses mdSessionId from identity-key hashing; Cobalt has no equivalent
                    .build());
        }
    }

    /**
     * Emits one {@code MdSyncdMutationEvent} per uploaded mutation after a
     * successful outgoing patch push.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler.$e}
     * ({@code _uploadSuccessful}): the post-upload callback iterates the
     * acknowledged mutations and calls
     * {@code WAWebSyncdWamReportingUtils.syncReportMutationToWam(e, d,
     * /*incoming*&#47;false, base64(indexMac), mutationName, isRemove,
     * /*isPatch*&#47;true, mdSessionId, base64(uploadedPatchMac))}. The reporter
     * then constructs a {@code MdSyncdMutationWamEvent} per mutation.
     *
     * <p>Cobalt's upload success path lives in {@link #processUploadSuccess};
     * the uploaded mutations are represented as
     * {@link SyncRequest.UploadedMutationInfo}. WA Web always emits
     * {@code isInBootstrap:false} and {@code mutationBundle:PATCH} for this
     * site (uploads are by definition outgoing patches post-bootstrap); Cobalt
     * mirrors that.
     * @param collectionName the collection that was uploaded
     * @param seqNumber      the new patch version acknowledged by the server
     * @param mutations      the acknowledged mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandler", exports = "$e", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitSyncdMutationWamEventsForUpload(
            SyncPatchType collectionName,
            long seqNumber,
            List<SyncRequest.UploadedMutationInfo> mutations) {
        if (mutations.isEmpty()) {
            return;
        }
        var syncdCollection = mapSyncdCollectionType(collectionName);
        var seqNumberInt = (int) seqNumber;
        for (var mutation : mutations) {
            var mutationName = SyncdIndexUtils
                    .getMutationNameFromIndex(null, mutation.actionIndex());
            if (mutationName == null || mutationName.isBlank()) {
                mutationName = "no-mutation-name"; // WAWebSyncdWamReportingUtils.syncReportMutationToWam: a != null ? a : "no-mutation-name"
            }
            var mutationMac = Base64.getUrlEncoder().withoutPadding().encodeToString(mutation.indexMac()); // WABase64.encodeB64UrlSafe
            var mutationOperation = mutation.operation() == SyncdOperation.REMOVE
                    ? MutationOperationType.REMOVE
                    : MutationOperationType.SET;
            wamService.commit(new MdSyncdMutationEventBuilder() // WAWebMdSyncdMutationWamEvent.MdSyncdMutationWamEvent
                    .contentLength(0) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: contentLength:0 (literal)
                    .isInBootstrap(false) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: isInBootstrap:!1 (literal)
                    .isUsingLid(false) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: isUsingLid:!1 (literal)
                    .mutationBundle(MutationBundleType.PATCH) // WAWebSyncdCollectionHandler.$e call: l=!0 -> PATCH
                    .mutationDirection(MutationDirectionType.OUTGOING) // WAWebSyncdCollectionHandler.$e call: n=false -> OUTGOING
                    .mutationMac(mutationMac) // WAWebSyncdWamReportingUtils.syncReportMutationToWam: mutationMac: base64(indexMac)
                    .mutationName(mutationName)
                    .mutationOperation(mutationOperation)
                    .seqNumber(seqNumberInt) // WAWebSyncdCollectionHandler.$e call: t = newVersion d
                    .syncdCollection(syncdCollection)
                    .syncdKeyhash("") // WAWebSyncdWamReportingUtils.syncReportMutationToWam: literal ""
                    .syncdKeyid("") // WAWebSyncdWamReportingUtils.syncReportMutationToWam: literal ""
                    // NO_WA_BASIS: patchMac — WA Web passes base64(e.patchMac) per-mutation, but Cobalt's UploadedMutationInfo does not carry the wire patch MAC; omitted (WA Web also falls back to "" when null)
                    // NO_WA_BASIS: appSessionId / companionSessionIds — same omission rationale as incoming path
                    .build());
        }
    }

    /**
     * Processes a successful mutation upload by persisting sync action entries,
     * updating the collection version and LT-Hash, and clearing pending mutations.
     *
     * <p>Per WhatsApp Web {@code _uploadSuccessful}: after the server acknowledges
     * a push, the client verifies the expected version, converts uploaded SET
     * mutations to sync actions, removes REMOVE mutation entries, and atomically
     * updates the collection state.
     * @param uploadInfo the upload metadata captured during request building
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandlerTypesConverter", exports = "encryptedUploadMutationsToSyncActions", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandlerTypesConverter", exports = "setMutationToSyncAction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandlerTypesConverter", exports = "syncActionToSyncData", adaptation = WhatsAppAdaptation.ADAPTED)
    private void processUploadSuccess(SyncRequest.UploadedPatchInfo uploadInfo) {
        var patchType = uploadInfo.patchType();
        var expectedVersion = uploadInfo.newVersion();

        // Per WA Web: verify server_version == local_version + 1
        var currentVersion = getCurrentVersion(patchType);
        if (expectedVersion != currentVersion + 1) {
            LOGGER.warning("Unexpected version after upload for " + patchType
                    + ": expected " + (currentVersion + 1) + " but computed " + expectedVersion);
            return;
        }

        // Per WA Web $e: update collection version and LT-Hash, gated by version guard
        var uploadApplied = updateCollectionState(patchType, expectedVersion, uploadInfo.newLtHash()); // WAWebSyncdCollectionHandler.ot
        if (!uploadApplied) { // WAWebSyncdCollectionHandler.ot: skip entire transaction if stale
            return;
        }

        // Per WA Web $e: persist sync action entries for SET mutations and
        // remove prior entries for acknowledged REMOVE mutations.
        // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions —
        // t.map(t => {index, action, binarySyncData: syncActionToSyncData(t.binarySyncAction),
        //   actionState: n, version, keyId, indexMac, valueMac, collection, timestamp})
        for (var mutation : uploadInfo.mutations()) {
            if (mutation.operation() == SyncdOperation.SET) {
                // WAWebSyncdCollectionHandlerTypesConverter.setMutationToSyncAction —
                // (e, t=Success, n=action, r=modelId, o=modelType) => {...e, action: n, actionState: t,
                //   modelId: r, modelType: o ?? void 0}. Inlined here as builder population.
                store.putSyncActionEntry(patchType, mutation.indexMac(), new SyncActionEntryBuilder()
                        .indexMac(mutation.indexMac()) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: indexMac: t.indexMac
                        .valueMac(mutation.valueMac()) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: valueMac: t.valueMac
                        .keyId(mutation.keyId()) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: keyId: t.keyId
                        .actionIndex(mutation.actionIndex()) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: index: t.index
                        .actionValue(mutation.actionValue()) // ADAPTED: WAWebSyncdCollectionHandlerTypesConverter.syncActionToSyncData — WA Web re-serializes to binarySyncData bytes; Cobalt keeps the decoded SyncActionValue object
                        .actionVersion(mutation.actionVersion()) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: version: t.version
                        .actionState(SyncActionState.SUCCESS) // WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions: actionState: n (caller passes CollectionState.Success)
                        .modelType(resolveActionNameSafe(new DecryptedMutation.Trusted( // WAWebSyncdCollectionHandler.$e: getMutationNameFromIndex (safe); WAWebSyncdCollectionHandlerTypesConverter.setMutationToSyncAction: modelType: o
                                mutation.actionIndex(),
                                mutation.actionValue(),
                                mutation.operation(),
                                mutation.actionValue().timestamp().orElse(Instant.EPOCH),
                                mutation.actionVersion()
                        )))
                        .modelId(extractModelId(mutation.actionIndex())) // WAWebSyncdCollectionHandlerTypesConverter.setMutationToSyncAction: modelId: r
                        // NO_WA_BASIS: collection field omitted — stored implicitly via the patchType map key in the Cobalt store
                        // NO_WA_BASIS: timestamp field omitted — Cobalt reads timestamp from actionValue.timestamp() on demand
                        // NO_WA_BASIS: action field omitted — Cobalt derives the action object from actionValue at read sites, not stored separately
                        .build());
            } else {
                // ADAPTED: WAWebSyncdCollectionHandlerTypesConverter.encryptedUploadMutationsToSyncActions —
                // WA Web converts REMOVE mutations to SyncAction entries the same way as SET (differing only
                // by the action payload), but WAWebSyncdCollectionHandler.$e's REMOVE branch explicitly
                // deletes the prior entry via removeSyncActionInTransaction instead of writing one back,
                // so Cobalt mirrors the $e behaviour here rather than the converter's .map(...) shape.
                store.removeSyncActionEntry(patchType, mutation.indexMac());
            }
        }

        // WAWebSyncdCollectionHandler.$e (_uploadSuccessful): after persisting sync
        // action entries, iterate the uploaded mutations and emit one
        // MdSyncdMutationWamEvent per mutation via
        // WAWebSyncdWamReportingUtils.syncReportMutationToWam (called with
        // incoming=false, isPatch=true, isInBootstrap=false).
        emitSyncdMutationWamEventsForUpload(patchType, expectedVersion, uploadInfo.mutations());

        // Remove only the pending mutations that participated in this upload.
        var uploadedPendingMutationIds = new HashSet<>(uploadInfo.uploadedPendingMutationIds());
        if (uploadedPendingMutationIds.isEmpty()) {
            return;
        }
        whatsapp.store().removePendingMutations(patchType, uploadedPendingMutationIds);
    }

    /**
     * Deduplicates and orders mutations within a single patch.
     *
     * <p>Per WhatsApp Web behavior:
     * <ul>
     *   <li>If both SET and REMOVE exist for the same index, the REMOVE is dropped (SET wins)</li>
     *   <li>REMOVE mutations are ordered before SET mutations</li>
     * </ul>
     * @param mutations the decrypted mutations from a single patch
     * @return the deduplicated and ordered mutations
     */
    private SequencedCollection<DecryptedMutation.Untrusted> deduplicateAndOrder(SequencedCollection<DecryptedMutation.Untrusted> mutations) {
        // Collect SET indices
        var setIndices = new HashSet<String>();
        for (var mutation : mutations) {
            if (mutation.operation() == SyncdOperation.SET) {
                setIndices.add(mutation.index());
            }
        }

        // Partition: REMOVE first (excluding those with a matching SET), then all SETs
        var removes = new ArrayList<DecryptedMutation.Untrusted>();
        var sets = new ArrayList<DecryptedMutation.Untrusted>();
        for (var mutation : mutations) {
            if (mutation.operation() == SyncdOperation.SET) {
                sets.add(mutation);
            } else if (!setIndices.contains(mutation.index())) {
                removes.add(mutation);
            }
        }

        var result = new ArrayList<DecryptedMutation.Untrusted>(removes.size() + sets.size());
        result.addAll(removes);
        result.addAll(sets);
        return result;
    }

    /**
     * Validates that no two mutations of the same operation type share the same index.
     *
     * <p>Per WhatsApp Web {@code validateNoSameIndexForMultipleMutations}:
     * <ul>
     *   <li>For patches ({@code fatal = true}): throws {@link WhatsAppWebAppStateSyncException.DuplicateIndexInPatch}</li>
     *   <li>For snapshots ({@code fatal = false}): logs a warning but continues</li>
     * </ul>
     * @param collectionName the collection being processed
     * @param mutations the decrypted mutations to validate
     * @param fatal whether to throw on duplicate (patches) or just log (snapshots)
     */
    private void validateNoDuplicateIndices(
            SyncPatchType collectionName,
            SequencedCollection<DecryptedMutation.Untrusted> mutations,
            boolean fatal
    ) {
        var setIndices = new HashSet<String>(); // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — var r = new Set
        var removeIndices = new HashSet<String>(); // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — var a = new Set
        for (var mutation : mutations) { // ADAPTED: WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — t.forEach
            var index = mutation.index(); // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — i.index
            var isDuplicate = mutation.operation() == SyncdOperation.SET // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — i.operation === SET
                    ? !setIndices.add(index) // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — r.has(i.index) / r.add(i.index)
                    : !removeIndices.add(index); // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — a.has(i.index) / a.add(i.index)
            if (isDuplicate) { // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — if (l)
                if (fatal) { // ADAPTED: WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — n === SyncDataType.Patch
                    throw new WhatsAppWebAppStateSyncException.DuplicateIndexInPatch(collectionName); // WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — new SyncdFatalError("same index for multiple mutations in patch")
                } else { // ADAPTED: WAWebSyncdValidateMutations.validateNoSameIndexForMultipleMutations — n === SyncDataType.Snapshot (reportSyncdFatalError + y(t) diagnostic logging simplified)
                    LOGGER.warning("Duplicate index in snapshot for collection " + collectionName + ": " + index);
                }
            }
        }
    }

    /**
     * Validates that no two patches in the response share the same version.
     *
     * <p>Per WhatsApp Web {@code validateNoDuplicatePatchVersionInCollection}:
     * duplicate patch versions within the same collection response indicate
     * server corruption and trigger a fatal sync error.
     * @param collectionName the collection being processed
     * @param patches        the patches to validate
     */
    private void validateNoDuplicatePatchVersions(SyncPatchType collectionName, List<SyncdPatch> patches) {
        var seenVersions = new HashSet<Long>(); // WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection — var n = new Set
        for (var patch : patches) { // ADAPTED: WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection — t.forEach
            var version = patch.version() // ADAPTED: WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection — t.version.version (Optional wrapping)
                    .map(v -> v.version().orElse(0L))
                    .orElse(0L);
            if (!seenVersions.add(version)) { // WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection — n.has(r)
                throw new WhatsAppWebAppStateSyncException.DuplicatePatchVersion(collectionName, version); // WAWebSyncdValidateMutations.validateNoDuplicatePatchVersionInCollection — new SyncdFatalError("duplicate patch version in collection")
            }
        }
    }

    /**
     * Returns the current version for the specified collection.
     *
     * <p>ADAPTED: WA Web exposes {@code getCollectionVersionInTransaction} (single) and
     * {@code bulkGetCollectionVersionsInTransaction} / {@code getAllCollectionVersionsInTransaction}
     * (batched). Cobalt flattens the per-collection IDB store into the in-memory
     * {@code webAppStateCollections} map on the store,
     * so bulk lookups are inlined as per-item calls to {@link WhatsAppStore#findWebAppState}
     * by the consumers (e.g. {@link #resumeAfterRestart} iterates {@code SyncPatchType.values()}).
     * @param patchType the collection to query
     * @return the current version number
     */
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "getCollectionVersionInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "bulkGetCollectionVersionsInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "getAllCollectionVersionsInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private long getCurrentVersion(SyncPatchType patchType) {
        return store.findWebAppState(patchType).version();
    }

    /**
     * Returns the current LT-Hash for the specified collection.
     *
     * <p>Falls back to {@link MutationLTHash#EMPTY_HASH} if no hash state exists, matching
     * WA Web's {@code getCollectionVersionLtHashInTransaction} which substitutes an empty
     * {@code ArrayBuffer(KEY_LENGTH_BYTES)} when the stored entry is missing or has a
     * {@code null} {@code ltHash} field.
     * @param patchType the collection to query
     * @return the current LT-Hash bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "getCollectionVersionLtHashInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private byte[] getCurrentLTHash(SyncPatchType patchType) {
        var currentHashState = whatsapp.store().findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));
        return currentHashState.hash() != null ? currentHashState.hash() : MutationLTHash.EMPTY_HASH;
    }

    /**
     * Downloads and decodes a snapshot from an external blob reference.
     *
     * <p>Validates the blob reference fields, downloads the encrypted blob via
     * the media connection, and decodes the protobuf into a {@link SyncdSnapshot}.
     * Snapshot field validation (version, mac, keyId, records) is performed by
     * the caller and {@link MutationIntegrityVerifier#verifySnapshotMac}.
     * @param snapshotRef the external blob reference for the snapshot
     * @return the decoded snapshot
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if the blob reference is invalid or decoding fails
     * @throws WhatsAppWebAppStateSyncException.ExternalDownloadFailed if the download fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSDownload", exports = "downloadSnapshot", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdNetCallbacksApi", exports = "downloadSyncBlob", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdDecode", exports = "decodeSyncdSnapshot", adaptation = WhatsAppAdaptation.ADAPTED)
    private SyncdSnapshot downloadAndDecodeSnapshot(ExternalBlobReference snapshotRef) {
        validateExternalBlobReference(snapshotRef); // ADAPTED: WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — validated pre-download

        var downloadStart = Instant.now();
        try {
            var downloadedData = whatsapp.store()
                    .awaitMediaConnection()
                    .download(snapshotRef, abPropsService); // WAWebSyncdNetCallbacksApi.downloadSyncBlob(blobRef, "snapshot", collectionName)
            try (var protobufStream = ProtobufInputStream.fromStream(downloadedData)) {
                var decoded = SyncdSnapshotSpec.decode(protobufStream); // WAWebSyncdDecode.decodeSyncdSnapshot
                commitMediaDownload2Success(downloadStart); // WAWebCreateMediaDownloadMetrics.handleDownloadAndDecryptSuccess
                return decoded;
            } catch (Throwable throwable) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("Failed to decode snapshot", throwable); // WAWebSyncdDecode.decodeSyncdSnapshot — SyncdFatalError
            }
        } catch (Throwable throwable) {
            if (throwable instanceof WhatsAppWebAppStateSyncException exception) {
                throw exception;
            }
            commitMediaDownload2Failure(downloadStart, throwable); // WAWebCreateMediaDownloadMetrics.handleDownloadError
            // Per WA Web WAWebSyncdNetCallbacksApi.downloadSyncBlob: MediaNotFoundError
            // (HTTP 404 / CDN blob expired) is re-thrown as
            // `new SyncdFatalError("external patch expired")` — a fatal error that
            // stops the collection rather than scheduling a retry. Non-404 failures
            // remain retryable ExternalDownloadFailed.
            if (isExternalBlobNotFound(throwable)) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("external patch expired", throwable); // WAWebSyncdNetCallbacksApi.downloadSyncBlob — MediaNotFoundError → SyncdFatalError
            }
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(throwable); // WAWebSyncdNetCallbacksApi.downloadSyncBlob — non-404 download failures are retryable
        }
    }

    /**
     * Extracts mutations from a decoded snapshot, wrapping each record as a SET mutation.
     *
     * <p>Per WhatsApp Web snapshot processing: all snapshot records are treated as
     * SET operations since the snapshot represents the full state at a given version.
     * @param snapshot the decoded snapshot
     * @return the mutations derived from the snapshot records
     */
    private SequencedCollection<SyncdMutation> getMutationsFromSnapshot(SyncdSnapshot snapshot) {
        var result = new ArrayList<SyncdMutation>();
        for (var record : snapshot.records()) {
            var sync = new SyncdMutationBuilder()
                    .operation(SyncdOperation.SET)
                    .record(record)
                    .build();
            result.add(sync);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Processes a recovered snapshot from the primary device, converting plaintext
     * records into trusted mutations and populating the sync action entry store.
     *
     * <p>Per WhatsApp Web {@code convertSyncdSnapshotRecoveryResponseToSnapshot}:
     * the recovery data contains already-decrypted {@code SyncActionData} records.
     * For each record, the companion re-derives the index MAC using the referenced
     * sync key, then uses the primary's value MAC and LT-Hash directly.
     * @param collectionName    the collection being recovered
     * @param recoveredSnapshot the decoded recovery snapshot from the primary device
     * @return the trusted mutations extracted from the recovery data
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandlerTypesConverter", exports = "syncActionsToDecryptedMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    private SequencedCollection<DecryptedMutation.Trusted> processRecoveredSnapshot(
            SyncPatchType collectionName,
            SyncdSnapshotRecovery recoveredSnapshot
    ) {
        var records = recoveredSnapshot.mutationRecords();
        var trusted = new ArrayList<DecryptedMutation.Trusted>(records.size());

        for (var record : records) {
            var actionData = record.value()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery record missing SyncActionData", null));
            var keyId = record.keyId()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery record missing key ID", null));
            var valueMac = record.mac()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery record missing value MAC", null));
            var indexBytes = actionData.index()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery record missing index", null));
            var actionValue = actionData.value()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Recovery record missing action value", null));
            var actionVersion = actionData.version()
                    .orElse(0);
            if(actionVersion <= 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("Recovery record missing action version", null);
            }

            var timestamp = actionValue.timestamp()
                    .orElseThrow(WhatsAppWebAppStateSyncException.MissingActionTimestamp::new);

            // Re-derive indexMac using the sync key's index key
            var syncKeyData = whatsapp.store()
                    .findWebAppStateKeyById(keyId)
                    .flatMap(AppStateSyncKey::keyData)
                    .flatMap(AppStateSyncKeyData::keyData)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.MissingKey(keyId));

            try (var keys = MutationKeys.ofSyncKey(syncKeyData)) {
                var indexMac = Mac.getInstance("HmacSHA256");
                indexMac.init(keys.indexKey());
                var indexMacResult = indexMac.doFinal(indexBytes);

                // Populate sync action entry store for future LT-Hash computations
                var indexString = new String(indexBytes, StandardCharsets.UTF_8);
                store.putSyncActionEntry(collectionName, indexMacResult, new SyncActionEntryBuilder()
                        .indexMac(indexMacResult)
                        .valueMac(valueMac)
                        .keyId(keyId)
                        .actionIndex(indexString)
                        .actionValue(actionValue)
                        .actionVersion(actionVersion)
                        .build());

                // WAWebSyncdCollectionHandlerTypesConverter.syncActionsToDecryptedMutation —
                // e.map(e => {collection, index, indexMac, keyId, operation: SET, binarySyncData, valueMac, version})
                // In Cobalt: collection is implicit (collectionName), indexMac/keyId/valueMac are
                // persisted into the store above rather than copied onto the mutation, binarySyncData
                // is replaced by the decoded SyncActionValue (actionValue).
                trusted.add(new DecryptedMutation.Trusted(
                        indexString, // WAWebSyncdCollectionHandlerTypesConverter.syncActionsToDecryptedMutation: index: e.index
                        actionValue, // ADAPTED: WAWebSyncdCollectionHandlerTypesConverter.syncActionsToDecryptedMutation: binarySyncData — Cobalt carries the decoded SyncActionValue
                        SyncdOperation.SET, // WAWebSyncdCollectionHandlerTypesConverter.syncActionsToDecryptedMutation: operation: SyncdMutation$SyncdOperation.SET
                        timestamp, // NO_WA_BASIS: Cobalt extracts timestamp eagerly for the Trusted record; WA Web keeps it inside binarySyncData
                        actionVersion // WAWebSyncdCollectionHandlerTypesConverter.syncActionsToDecryptedMutation: version: e.version
                ));
            } catch (GeneralSecurityException e) {
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed(e);
            }
        }

        // Use recovery's LT-Hash and version directly
        var recoveryVersion = recoveredSnapshot.version()
                .map(entry -> entry.version().orElse(0))
                .filter(version -> version > 0)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Recovery response missing version for " + collectionName,
                        null
                ));
        var recoveryLtHash = recoveredSnapshot.collectionLthash()
                .filter(hash -> hash.length == MutationLTHash.EMPTY_HASH.length)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Recovery response missing LT-Hash for " + collectionName,
                        null
                ));
        updateCollectionState(collectionName, recoveryVersion, recoveryLtHash);

        return trusted;
    }

    /**
     * Extracts mutations from a patch, downloading external mutations if needed.
     *
     * <p>A patch contains either inline mutations or an external blob reference
     * (never both). When external, the blob is downloaded, decoded as
     * {@link SyncdMutations}, and the contained mutations are returned.
     * Per-mutation field validation is deferred to {@link #decryptMutations}.
     * @param patch the decoded patch
     * @return the mutations from this patch (inline or external)
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if the patch has both inline and external mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMMSDownload", exports = "downloadExternalPatch", adaptation = WhatsAppAdaptation.ADAPTED)
    private SequencedCollection<SyncdMutation> getMutationsFromPatch(SyncdPatch patch) {
        var hasInline = patch.mutations() != null && !patch.mutations().isEmpty(); // WAWebSyncdValidateServerSyncProtobuf.validatePatchProtobuf — u && u.length > 0
        var hasExternal = patch.externalMutations().isPresent(); // WAWebSyncdValidateServerSyncProtobuf.validatePatchProtobuf — l (externalMutations)

        // WAWebSyncdValidateServerSyncProtobuf.validatePatchProtobuf — u && u.length > 0 && l → SyncdFatalError("patch with both inline and external mutations")
        if (hasInline && hasExternal) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Patch contains both inline and external mutations", null);
        }

        if (hasExternal) { // WAWebSyncdMMSDownload.downloadExternalPatch path
            var downloadedData = downloadExternalMutation(patch.externalMutations().get()); // WAWebSyncdNetCallbacksApi.downloadSyncBlob(blobRef, "patch", collectionName)
            var externalMutations = decodeExternalMutation(downloadedData); // WAWebSyncdDecode.decodeSyncdMutations
            return externalMutations.mutations() != null // WAWebSyncdDecode.decodeSyncdMutations — .mutations
                    ? Collections.unmodifiableList(externalMutations.mutations())
                    : List.of();
        }

        return hasInline
                ? Collections.unmodifiableList(patch.mutations())
                : List.of();
    }

    /**
     * Downloads an external mutation blob from the media connection.
     * @param externalRef the external blob reference
     * @return the downloaded data stream
     * @throws WhatsAppWebAppStateSyncException.ExternalDownloadFailed if the download fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdNetCallbacksApi", exports = "downloadSyncBlob", adaptation = WhatsAppAdaptation.ADAPTED)
    private InputStream downloadExternalMutation(ExternalBlobReference externalRef) {
        validateExternalBlobReference(externalRef); // ADAPTED: WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — validated pre-download

        var downloadStart = Instant.now();
        try {
            var downloaded = whatsapp.store()
                    .awaitMediaConnection()
                    .download(externalRef, abPropsService); // WAWebSyncdNetCallbacksApi.downloadSyncBlob(blobRef, "patch", collectionName)
            commitMediaDownload2Success(downloadStart); // WAWebCreateMediaDownloadMetrics.handleDownloadAndDecryptSuccess
            return downloaded;
        } catch (Throwable throwable) {
            commitMediaDownload2Failure(downloadStart, throwable); // WAWebCreateMediaDownloadMetrics.handleDownloadError
            // Per WA Web WAWebSyncdNetCallbacksApi.downloadSyncBlob: when the CDN returns
            // MediaNotFoundError (HTTP 404 / expired blob) for an external patch, WA Web
            // throws `new SyncdFatalError("external patch expired")` — fatal, not retryable.
            if (isExternalBlobNotFound(throwable)) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("external patch expired", throwable); // WAWebSyncdNetCallbacksApi.downloadSyncBlob — MediaNotFoundError → SyncdFatalError
            }
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(throwable); // WAWebSyncdNetCallbacksApi.downloadSyncBlob — non-404 download failures are retryable
        }
    }

    /**
     * Commits a successful {@code MediaDownload2Event} for an app-state CDN
     * blob download.
     * @param downloadStart the instant at which the download attempt began
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaDownloadMetrics",
            exports = "createMediaDownloadMetrics",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaDownload2Success(Instant downloadStart) {
        var overallT = Instant.ofEpochMilli(Duration.between(downloadStart, Instant.now()).toMillis());
        wamService.commit(new MediaDownload2EventBuilder()
                .overallMediaType(MediaType.MD_APP_STATE)
                .overallMmsVersion(4)
                .overallDownloadOrigin(DownloadOriginType.MESSAGE_HISTORY_SYNC)
                .overallDownloadMode(MediaDownloadModeType.FULL)
                .overallDownloadResult(MediaDownloadResultType.OK)
                .overallIsFinal(Boolean.TRUE)
                .downloadHttpCode(200)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0)
                .build());
    }

    /**
     * Commits a failing {@code MediaDownload2Event} for an app-state CDN
     * blob download.
     * @param downloadStart the instant at which the download attempt began
     * @param throwable     the error that aborted the download
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaDownloadMetrics",
            exports = "createMediaDownloadMetrics",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaDownload2Failure(Instant downloadStart, Throwable throwable) {
        var overallT = Instant.ofEpochMilli(Duration.between(downloadStart, Instant.now()).toMillis());
        var builder = new MediaDownload2EventBuilder()
                .overallMediaType(MediaType.MD_APP_STATE)
                .overallMmsVersion(4)
                .overallDownloadOrigin(DownloadOriginType.MESSAGE_HISTORY_SYNC)
                .overallDownloadMode(MediaDownloadModeType.FULL)
                .overallDownloadResult(classifyMediaDownloadError(throwable))
                .overallIsFinal(Boolean.TRUE)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0);
        var statusCode = extractHttpStatusCode(throwable);
        if (statusCode != null) {
            builder.downloadHttpCode(statusCode);
        }
        wamService.commit(builder.build());
    }

    /**
     * Maps a download failure to the appropriate
     * {@link MediaDownloadResultType} using the same rules as WA Web's
     * {@code WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType}.
     * @param throwable the error raised by the download path
     * @return the mapped result type
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaDownloadResultType classifyMediaDownloadError(Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download download) {
                var optStatus = download.httpStatusCode();
                if (optStatus.isEmpty()) {
                    // HttpNetworkError -> ERROR_NETWORK
                    return MediaDownloadResultType.ERROR_NETWORK;
                }
                var status = optStatus.getAsInt();
                return switch (status) {
                    case 404, 410 -> MediaDownloadResultType.ERROR_TOO_OLD;
                    case 416 -> MediaDownloadResultType.ERROR_CANNOT_RESUME;
                    case 401 -> MediaDownloadResultType.ERROR_INVALID_URL;
                    case 429, 507 -> MediaDownloadResultType.ERROR_THROTTLE;
                    default -> MediaDownloadResultType.ERROR_UNKNOWN;
                };
            }
        }
        return MediaDownloadResultType.ERROR_UNKNOWN;
    }

    /**
     * Extracts the HTTP status code embedded in a download failure, if any.
     * @param throwable the error raised by the download path
     * @return the status code, or {@code null} if unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getStatusCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Integer extractHttpStatusCode(Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download download) {
                var optStatus = download.httpStatusCode();
                return optStatus.isPresent() ? optStatus.getAsInt() : null;
            }
        }
        return null;
    }

    /**
     * Returns whether the given throwable indicates the external blob could not be found
     * (typically HTTP 404 / CDN-expired), equivalent to WA Web's {@code MediaNotFoundError}.
     *
     * <p>WA Web's {@code downloadSyncBlob} wraps such failures as
     * {@code new SyncdFatalError("external patch expired")}. In Cobalt, the media download
     * path surfaces not-found failures as {@link WhatsAppMediaException.Download} with a
     * message containing the HTTP status code (e.g. {@code "status code 404"}).
     * @param throwable the exception thrown by the media download path
     * @return {@code true} if the throwable indicates a 404 / not-found blob, {@code false} otherwise
     */
    private boolean isExternalBlobNotFound(Throwable throwable) {
        // Walk the cause chain so a wrapped IOException still gets classified correctly.
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download) {
                var message = current.getMessage();
                if (message != null && (message.contains("status code 404") || message.contains("404") && message.toLowerCase().contains("not found"))) {
                    return true;
                }
            }
            var message = current.getMessage();
            if (message != null) {
                var lower = message.toLowerCase();
                if (lower.contains("404") || lower.contains("not found") || lower.contains("notfound")) {
                    return true;
                }
            }
            // Avoid infinite loops in case of self-referencing causes.
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    /**
     * Validates that an external blob reference has all required fields for download.
     * @param ref the external blob reference to validate
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if any required field is missing
     */
    private void validateExternalBlobReference(ExternalBlobReference ref) {
        // Per WA Web WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference,
        // the validation order is: mediaKey → directPath → fileSha256 → fileEncSha256.
        // Matching the order keeps the error surfaced by the first missing field
        // consistent with WA Web, which is important for parity with its error metrics.
        if (ref.mediaKey().isEmpty() || ref.mediaKey().get().length == 0) { // WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — !s → SyncdFatalError("missing external blob reference media key")
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing mediaKey",
                    null
            );
        }
        if (ref.mediaDirectPath().isEmpty() || ref.mediaDirectPath().get().isEmpty()) { // WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — e == null → SyncdFatalError("missing external blob reference direct path")
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing directPath",
                    null
            );
        }
        if (ref.fileSha256().isEmpty()) { // WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — !a → SyncdFatalError("missing external blob reference file SHA256")
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing fileSha256",
                    null
            );
        }
        if (ref.fileEncSha256().isEmpty()) { // WAWebSyncdValidateServerSyncProtobuf.validateExternalBlobReference — !r → SyncdFatalError("missing external blob reference file enc SHA256")
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing fileEncSha256",
                    null
            );
        }
    }

    /**
     * Decodes downloaded external mutation data into a {@link SyncdMutations} object.
     * @param downloadedData the downloaded data stream
     * @return the decoded mutations container
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if protobuf decoding fails
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdDecode", exports = "decodeSyncdMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private SyncdMutations decodeExternalMutation(InputStream downloadedData) {
        try (var protobufStream = ProtobufInputStream.fromStream(downloadedData)) {
            return SyncdMutationsSpec.decode(protobufStream); // WAWebSyncdDecode.decodeSyncdMutations
        } catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError("Failed to decode external mutations", throwable); // WAWebSyncdDecode.decodeSyncdMutations — SyncdFatalError
        }
    }

    /**
     * Decrypts a sequence of raw mutations, performing missing key detection before decryption.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdDecryptMutationsWrapper}: first scans all mutations
     * for missing keys, sending key share requests for any that are absent. If a missing key
     * is found, throws {@link WhatsAppWebAppStateSyncException.MissingKey} to transition the
     * collection to {@code BLOCKED} state. For REMOVE mutations, the handling depends on the
     * {@code web_request_missing_keys_for_removes} AB prop.
     * @param mutations the raw mutations to decrypt
     * @return the decrypted untrusted mutations
     * @throws WhatsAppWebAppStateSyncException.MissingKey if a required sync key is not available
     * @throws WhatsAppWebAppStateSyncException.DecryptionFailed if decryption fails
     */
    private SequencedCollection<DecryptedMutation.Untrusted> decryptMutations(SequencedCollection<SyncdMutation> mutations) {
        // Per WA Web WAWebSyncdDecryptMutationsWrapper: proactively scan all key IDs
        // before decrypting to detect missing keys. For REMOVE mutations, the behavior
        // depends on the web_request_missing_keys_for_removes AB prop:
        // - If true: treat REMOVE missing keys the same as SET (MissingKey -> Blocked)
        // - If false: REMOVE missing keys are fatal (SyncdFatalError)
        var requestMissingKeysForRemoves = abPropsService.getBool(ABProp.WEB_REQUEST_MISSING_KEYS_FOR_REMOVES); // WAWebSyncdDecryptMutationsWrapper.y
        // Per WA Web handleMissingKeysInSnapshot/handleMissingKeysInPatches: collect ALL
        // missing key IDs into a Set first, then invoke handleMissingKeys ONCE with the
        // full batch. The Set deduplicates keys that appear in multiple mutations and
        // avoids one key-share request per mutation.
        var missingKeyIds = new LinkedHashMap<String, byte[]>(); // WAWebSyncdHandleMissingKeys.handleMissingKeysInSnapshot/handleMissingKeysInPatches
        byte[] firstMissingKeyId = null;
        for (var mutation : mutations) {
            var keyId = mutation.record()
                    .flatMap(SyncdRecord::keyId)
                    .flatMap(KeyId::id)
                    .orElse(null);
            if (keyId != null && whatsapp.store().findWebAppStateKeyById(keyId).isEmpty()) {
                var operation = mutation.operation().orElse(null); // WAWebSyncdDecryptMutationsWrapper.y
                if (operation == SyncdOperation.REMOVE && !requestMissingKeysForRemoves) { // WAWebSyncdDecryptMutationsWrapper.y
                    // AB prop web_request_missing_keys_for_removes == false:
                    // WA Web throws `new SyncdFatalError("no key data for remove mutations")`.
                    // Cobalt uses UnexpectedError (fatal, isFatal() == true) with the matching
                    // message to preserve the fatal routing behavior.
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError( // WAWebSyncdDecryptMutationsWrapper.y
                            "no key data for remove mutations", null);
                }
                if (firstMissingKeyId == null) {
                    firstMissingKeyId = keyId;
                }
                // Deduplicate using a key derived from the raw bytes so we only queue one
                // request per distinct key ID across the whole mutation batch.
                missingKeyIds.putIfAbsent(HexFormat.of().formatHex(keyId), keyId);
            }
        }
        if (!missingKeyIds.isEmpty()) {
            // WAWebSyncdHandleMissingKeys.handleMissingKeysInSnapshot/handleMissingKeysInPatches:
            // single batched call with the full set of missing key IDs.
            missingSyncKeyRequestService.requestMissingKeys(missingKeyIds.values());
        }
        if (firstMissingKeyId != null) {
            throw new WhatsAppWebAppStateSyncException.MissingKey(firstMissingKeyId);
        }

        var decrypted = new ArrayList<DecryptedMutation.Untrusted>(mutations.size());

        for (var mutation : mutations) {
            var record = mutation.record()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing record in mutation", null));

            var operation = mutation.operation()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing operation in mutation", null));

            var recordValueBlob = record.value()
                    .flatMap(SyncdValue::blob)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing value blob in mutation record", null));

            var recordIndexBlob = record.index()
                    .flatMap(SyncdIndex::blob)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing index blob in mutation record", null));

            var keyId = record.keyId()
                    .flatMap(KeyId::id)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError("Missing key ID in mutation record", null));
            if (keyId.length == 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("Empty key ID in mutation record", null);
            }

            // Get encryption key
            var syncKey = whatsapp.store()
                    .findWebAppStateKeyById(keyId)
                    .flatMap(AppStateSyncKey::keyData)
                    .flatMap(AppStateSyncKeyData::keyData)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.MissingKey(keyId));

            // Derive keys and decrypt
            try (var keys = MutationKeys.ofSyncKey(syncKey)) {
                var decryptedMutation = DecryptedMutation.Untrusted.of(
                        recordValueBlob,
                        recordIndexBlob,
                        keys,
                        operation,
                        keyId
                );
                decrypted.add(decryptedMutation);
            } catch (Exception e) {
                // Per WA Web WAWebSyncdDecryptMutation.decryptMutation: the catch block logs
                // and re-throws the original exception (ValueMacMismatch, IndexMacMismatch, etc.)
                // so the outer handler can distinguish fatal from retryable sync errors.
                // Only wrap non-sync exceptions so their original subtype (and isFatal()) is preserved.
                if (e instanceof WhatsAppWebAppStateSyncException syncEx) {
                    throw syncEx;
                }
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed(e);
            }
        }

        return Collections.unmodifiableSequencedCollection(decrypted);
    }

    /**
     * Reports the size of any per-chat message range snapshot carried by
     * decrypted chat action mutations to the WAM pipeline.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdMetricCriticalBootstrapStage.reportSyncdDecryptedMutations}:
     * iterates over each decoded {@code SyncActionData.value}; when the payload is an
     * {@code archiveChatAction}, {@code markChatAsReadAction}, {@code clearChatAction},
     * or {@code deleteChatAction} and carries a non-null {@code messageRange}, invokes
     * {@code WAWebCollectionHandlerWamMutation.logMetricsForMutationLength(a.messages.length)}
     * which in turn constructs and commits a {@code MdAppStateMessageRangeWamEvent} with
     * {@code additionalMessagesCount} set to {@code messageRange.messages.length}.
     *
     * <p>This method is invoked after {@link #decryptMutations} returns for both the
     * snapshot and patch application paths, matching the two call sites in
     * {@code WAWebSyncdCollectionHandler._applySnapshotAndPatches} and
     * {@code WAWebSyncdCollectionHandler._applyPatch}.
     * @param untrusted the decrypted mutations to inspect for chat action message ranges
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetricCriticalBootstrapStage", exports = "reportSyncdDecryptedMutations", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebCollectionHandlerWamMutation", exports = "logMetricsForMutationLength", adaptation = WhatsAppAdaptation.DIRECT)
    private void reportDecryptedMutationMessageRanges(SequencedCollection<DecryptedMutation.Untrusted> untrusted) {
        // WAWebSyncdMetricCriticalBootstrapStage.reportSyncdDecryptedMutations:
        //   logCriticalBootstrapStageIfNecessary(MUTATIONS_DECRYPTED) is the first step of this helper.
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.MUTATIONS_DECRYPTED);
        for (var mutation : untrusted) {
            // WAWebSyncdMetricCriticalBootstrapStage.reportSyncdDecryptedMutations:
            //   r.archiveChatAction ? r.archiveChatAction.messageRange
            // : r.markChatAsReadAction ? r.markChatAsReadAction.messageRange
            // : r.clearChatAction ? r.clearChatAction.messageRange
            // : r.deleteChatAction ? r.deleteChatAction.messageRange : null
            var messageRange = mutation.value().action()
                    .flatMap(action -> switch (action) {
                        case ArchiveChatAction a -> a.messageRange();
                        case MarkChatAsReadAction a -> a.messageRange();
                        case ClearChatAction a -> a.messageRange();
                        case DeleteChatAction a -> a.messageRange();
                        default -> Optional.<SyncActionMessageRange>empty();
                    })
                    .orElse(null);
            if (messageRange == null) { // WAWebSyncdMetricCriticalBootstrapStage: a != null guard
                continue;
            }
            // WAWebCollectionHandlerWamMutation.logMetricsForMutationLength: new MdAppStateMessageRangeWamEvent({additionalMessagesCount: e}).commit()
            wamService.commit(new MdAppStateMessageRangeEventBuilder()
                    .additionalMessagesCount(messageRange.messages().size())
                    .build());
        }
    }

    /**
     * Emits a {@code MdBootstrapAppStateCriticalDataProcessingEvent} when a
     * critical-bootstrap stage is reached during the initial app-state sync.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdCriticalBootstrapProcessingApi.logCriticalBootstrapStageIfNecessary}:
     * the event is only emitted while
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()} returns
     * {@code true} &mdash; i.e. the first-run critical data sync has not yet
     * completed. In Cobalt this is approximated by checking whether the
     * {@link SyncPatchType#CRITICAL_BLOCK} collection has been bootstrapped,
     * matching {@link com.github.auties00.cobalt.stream.notification.device.NotificationSyncStreamHandler#isCriticalDataSyncInProcess}.
     *
     * <p>WA Web populates three properties on every emission:
     * {@code bootstrapAppStateDataStage} (the current stage),
     * {@code mdSessionId} (the current session id derived from primary +
     * companion identity keys by {@code WAWebSyncdMdSession.genCurrentSessionId}),
     * and {@code mdTimestamp} (current unix time in milliseconds truncated to
     * int32). Cobalt does not compute an equivalent of {@code mdSessionId}, so
     * that property is left unset; the other two are mirrored exactly.
     * @param stage the bootstrap stage reached; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCriticalBootstrapProcessingApi", exports = "logCriticalBootstrapStageIfNecessary", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode stage) {
        // WAWebSyncdCriticalBootstrapProcessingApi.logCriticalBootstrapStageIfNecessary:
        //   if (WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()) { emit(); }
        // ADAPTED: WA Web tracks a global syncdCritical state machine; Cobalt approximates
        // it by checking whether the critical_block collection has been bootstrapped yet.
        if (store.findWebAppState(SyncPatchType.CRITICAL_BLOCK).bootstrapped()) {
            return;
        }
        // WAWebSyncdCriticalBootstrapProcessingApi.logCriticalBootstrapStageIfNecessary:
        //   new MdBootstrapAppStateCriticalDataProcessingWamEvent({
        //       bootstrapAppStateDataStage: e,
        //       mdSessionId: yield MdSyncFieldStatsMeta.getMdSessionId(),
        //       mdTimestamp: unixTimeMs()
        //   }).commit()
        // ADAPTED: Cobalt has no equivalent of genCurrentSessionId (which hashes
        // primary + companion identity keys), so mdSessionId is omitted.
        wamService.commit(new MdBootstrapAppStateCriticalDataProcessingEventBuilder()
                .bootstrapAppStateDataStage(stage) // WAWebSyncdCriticalBootstrapProcessingApi: bootstrapAppStateDataStage: e
                .mdTimestamp((int) System.currentTimeMillis()) // WAWebSyncdCriticalBootstrapProcessingApi: mdTimestamp: unixTimeMs()
                .build());
    }

    /**
     * Emits {@code MdBootstrapAppStateDataDownloadedEvent} after the external
     * snapshot and patch downloads complete for a collection being bootstrapped
     * for the first time.
     *
     * <p>Per WhatsApp Web {@code WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric}:
     * the event is built with the collection's payload type (critical vs.
     * non-critical), the elapsed download duration since
     * {@code BootstrapDownloadTracker.startTs()}, the accumulated payload
     * size in bytes (clamped to int32), the step result (success/failure),
     * the current unix timestamp, and the md session id. Storage quota
     * estimation fields are populated from the browser's
     * {@code navigator.storage.estimate()} API when available.
     *
     * <p>WA Web emits this unconditionally for bootstrap rounds; Cobalt
     * mirrors that by only invoking this helper when the collection was
     * not previously bootstrapped. Cobalt has no browser storage API so
     * {@code mdStorageQuotaBytes} and {@code mdStorageQuotaUsedBytes} are
     * omitted; Cobalt also has no equivalent of {@code genCurrentSessionId}
     * (which hashes primary + companion identity keys) so {@code mdSessionId}
     * is omitted.
     * @param tracker the download tracker carrying start timestamp and accumulated size
     * @param result  the step result, {@link MdBootstrapStepResult#SUCCESS} when the
     *                downloads completed or {@link MdBootstrapStepResult#FAILURE}
     *                otherwise
     * @param failure the throwable that caused the failure, or {@code null} on success;
     *                used to populate {@code mdSyncFailureReason}
     */
    @WhatsAppWebExport(moduleName = "WAWebCollectionHandlerWamSyncUtil", exports = "commitBootstrapAppStateDownloadMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdMetrics", exports = "reportSyncdBootstrapAppStateDownloadMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitBootstrapAppStateDataDownloaded(
            BootstrapDownloadTracker tracker,
            MdBootstrapStepResult result,
            Throwable failure
    ) {
        var now = System.currentTimeMillis();
        var duration = now - tracker.startTs();
        var builder = new MdBootstrapAppStateDataDownloadedEventBuilder()
                // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric:
                //   mdBootstrapPayloadType: e.includes(t) ? CRITICAL : NON_CRITICAL
                .mdBootstrapPayloadType(tracker.collectionName().isCritical()
                        ? MdBootstrapPayloadType.CRITICAL
                        : MdBootstrapPayloadType.NON_CRITICAL)
                // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric:
                //   mdTimestamp: unixTimeMs()
                .mdTimestamp((int) now)
                // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric:
                //   mdBootstrapStepDuration: unixTimeMs() - n
                .mdBootstrapStepDuration((int) duration)
                // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric:
                //   mdBootstrapStepResult: a==="success" ? SUCCESS : FAILURE
                .mdBootstrapStepResult(result);
        // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric:
        //   try { var s = WALongInt.maybeNumberOrThrowIfTooLarge(r); if (s != null) i.mdBootstrapPayloadSize = s; } catch {}
        if (tracker.totalBytes() > 0 && tracker.totalBytes() <= Integer.MAX_VALUE) {
            builder.mdBootstrapPayloadSize((int) tracker.totalBytes());
        }
        // NO_WA_BASIS: WA Web reads mdStorageQuotaBytes/mdStorageQuotaUsedBytes from
        // navigator.storage.estimate(); Cobalt runs on the JVM and has no equivalent
        // browser quota API, so both storage quota fields are omitted.
        // NO_WA_BASIS: WA Web populates mdSessionId via MdSyncFieldStatsMeta.getMdSessionId()
        // which hashes primary + companion identity keys; Cobalt has no equivalent session id
        // derivation, so mdSessionId is omitted (same as MdBootstrapAppStateCriticalDataProcessingEvent).
        // NO_WA_BASIS: WA Web's commitBootstrapAppStateDownloadMetric does not populate
        // mdSyncFailureReason (index 17) — the spec defines the field but neither the
        // primary emitter nor the KmpWamLogger variant ever writes to it. The `failure`
        // throwable is accepted here for symmetry with WA Web's isSuccess==="failure"
        // branch (so the on-failure emission carries the same shape) and kept available
        // in case a future WA Web revision starts populating mdSyncFailureReason at
        // this callsite.
        assert failure == null || result == MdBootstrapStepResult.FAILURE;
        wamService.commit(builder.build()); // WAWebCollectionHandlerWamSyncUtil.commitBootstrapAppStateDownloadMetric: i.commit()
    }

    /**
     * Mutable accumulator for the bootstrap app state download metric
     * tracked across the snapshot and patch-external download phase of a
     * single sync response.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler.Fe}, the
     * download metric is computed from the collection name, a start
     * timestamp captured before any CDN download, and a running sum of
     * the {@code fileSizeBytes} of each external blob reference
     * downloaded. The start timestamp is captured eagerly at construction
     * so it matches WA Web's {@code u = unixTimeMs()} at the top of the
     * download function; per-blob sizes are added via {@link #addBytes}.
     */
    private static final class BootstrapDownloadTracker {
        /**
         * Collection being downloaded; fed into {@code mdBootstrapPayloadType}
         * via {@link SyncPatchType#isCritical()}.
         */
        private final SyncPatchType collectionName;

        /**
         * Unix timestamp (ms) captured at the start of the download phase,
         * used to compute {@code mdBootstrapStepDuration}.
         */
        private final long startTs;

        /**
         * Running sum of the plaintext {@code fileSizeBytes} of each
         * external blob downloaded so far.
         */
        private long totalBytes;

        /**
         * Creates a new tracker with a start timestamp of {@code System.currentTimeMillis()}
         * and a zero byte accumulator.
         *
         * @param collectionName the collection being downloaded
         */
        private BootstrapDownloadTracker(SyncPatchType collectionName) {
            this.collectionName = collectionName;
            this.startTs = System.currentTimeMillis();
        }

        /**
         * Returns the collection being downloaded.
         *
         * @return the collection name
         */
        private SyncPatchType collectionName() {
            return collectionName;
        }

        /**
         * Returns the unix timestamp captured at construction.
         *
         * @return the start timestamp in milliseconds
         */
        private long startTs() {
            return startTs;
        }

        /**
         * Returns the running sum of external blob sizes.
         *
         * @return the total downloaded bytes
         */
        private long totalBytes() {
            return totalBytes;
        }

        /**
         * Adds a single blob's plaintext size to the running total.
         *
         * @param bytes the {@code fileSizeBytes} value from an external blob reference
         */
        private void addBytes(long bytes) {
            if (bytes > 0) {
                totalBytes += bytes;
            }
        }
    }

    /**
     * Applies a batch of trusted mutations to the store via the handler registry.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler._applySetMutations} (Xe):
     * resolves conflicts with pending local mutations, groups by action name, applies
     * each group through its handler, records mutation state (success/failed/orphan/unsupported),
     * and retries orphan mutations that may now succeed.
     * @param collectionName  the collection these mutations belong to
     * @param remoteMutations the trusted mutations to apply
     */
    private void applyMutations(SyncPatchType collectionName, SequencedCollection<DecryptedMutation.Trusted> remoteMutations) {
        // WAWebSyncdCollectionHandler._applySetMutations: logCriticalBootstrapStageIfNecessary(ABOUT_TO_APPLY_MUTATIONS) at entry.
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.ABOUT_TO_APPLY_MUTATIONS);
        // Step 1: Resolve conflicts with pending local mutations
        var mutationsToApply = resolveConflicts(remoteMutations, collectionName);

        // Step 2: Group mutations by action type
        var mutationsByAction = new HashMap<String, List<DecryptedMutation.Trusted>>();

        for (var mutation : mutationsToApply) {
            var actionName = resolveActionName(mutation);
            // Per WA Web nt/et: null action name (invalid index) -> unsupported
            if (actionName == null) {
                recordMutationState(collectionName, mutation, null, MutationApplicationResult.unsupported());
                continue;
            }
            mutationsByAction
                    .computeIfAbsent(actionName, _ -> new ArrayList<>())
                    .add(mutation);
        }

        // Step 3: Apply each action group via its handler
        // Per WA Web: handlers receive the full batch via applyMutations(mutations, options)
        for (var entry : mutationsByAction.entrySet()) {
            var handler = handlerRegistry.findHandler(entry.getKey());
            if (handler.isEmpty()) {
                for (var mutation : entry.getValue()) {
                    recordMutationState(collectionName, mutation, entry.getKey(), MutationApplicationResult.unsupported());
                }
                continue;
            }

            // Per WA Web: skip mutations with version higher than handler supports
            var maxVersion = handler.get().version();
            var versionGated = new ArrayList<DecryptedMutation.Trusted>(entry.getValue().size());
            for (var mutation : entry.getValue()) {
                if (mutation.actionVersion() > maxVersion) {
                    recordMutationState(collectionName, mutation, entry.getKey(), MutationApplicationResult.unsupported());
                    continue;
                }
                versionGated.add(mutation);
            }
            if (versionGated.isEmpty()) {
                continue;
            }

            // Per WA Web _applySetMutations (Xe): on error, if SyncdFatalError or
            // CriticalBlock collection, rethrow; otherwise mark batch as Failed.
            List<MutationApplicationResult> batchResults = null; // WAWebSyncdCollectionHandler.Xe
            var handlerFailed = false; // WAWebSyncdCollectionHandler.Xe: k flag
            try {
                batchResults = handler.get().applyMutationBatch(whatsapp, versionGated); // WAWebSyncdCollectionHandler.Xe
            } catch (WhatsAppWebAppStateSyncException exception) { // WAWebSyncdCollectionHandler.Xe
                if (exception.isFatal() || collectionName == SyncPatchType.CRITICAL_BLOCK) { // WAWebSyncdCollectionHandler.Xe
                    throw exception;
                }
                handlerFailed = true; // WAWebSyncdCollectionHandler.Xe
                LOGGER.warning("Error during _applySetMutations for " + entry.getKey() + " in " + collectionName + ": " + exception.getMessage());
            } catch (Throwable throwable) { // WAWebSyncdCollectionHandler.Xe
                if (collectionName == SyncPatchType.CRITICAL_BLOCK) { // WAWebSyncdCollectionHandler.Xe
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError(throwable);
                }
                handlerFailed = true; // WAWebSyncdCollectionHandler.Xe
                LOGGER.warning("Error during _applySetMutations for " + entry.getKey() + " in " + collectionName + ": " + throwable.getMessage());
            }
            for (var i = 0; i < versionGated.size(); i++) { // WAWebSyncdCollectionHandler.Xe
                var mutation = versionGated.get(i);
                var result = handlerFailed // WAWebSyncdCollectionHandler.Xe: k ? Failed : E[T].actionState
                        ? MutationApplicationResult.failed()
                        : batchResults.get(i);
                recordMutationState(collectionName, mutation, entry.getKey(), result); // WAWebSyncdCollectionHandler.Xe
                if (!handlerFailed && result.isOrphan()) { // WAWebSyncdCollectionHandler.Xe
                    store.addOrphanMutation(
                            collectionName,
                            buildOrphanEntry(
                                    mutation,
                                    result.modelType() != null ? result.modelType() : entry.getKey(),
                                    result.modelId()
                            )
                    );
                }
            }
        }

        // WAWebSyncdCollectionHandler._applySetMutations: logCriticalBootstrapStageIfNecessary(APPLIED_MUTATIONS) right before the return/log block at the end of the function.
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.APPLIED_MUTATIONS);

        // Step 4: Retry orphan mutations that may now succeed
        retryOrphanMutations(collectionName);
    }

    /**
     * Retries orphan mutations that previously failed because their referenced
     * entity did not exist yet.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan}: after each sync round,
     * orphaned mutations are retried since the sync may have brought in the
     * missing entities. Mutations that still fail remain in the orphan list
     * for subsequent retries.
     * @param collectionName the collection type whose orphans to retry
     */
    private void retryOrphanMutations(SyncPatchType collectionName) {
        var orphans = store.findOrphanMutations(collectionName);
        if (orphans.isEmpty()) {
            return;
        }

        store.removeOrphanMutations(collectionName);
        retryOrphanEntries(collectionName, orphans);
    }

    /**
     * Retries the given orphan mutation entries, re-adding any that still cannot
     * be applied.
     * @param collectionName the collection these orphans belong to
     * @param orphans        the orphan entries to retry
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCollectionHandler", exports = "applyIndividualMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryOrphanEntries(SyncPatchType collectionName, List<OrphanMutationEntry> orphans) {
        // WAWebSyncdCollectionHandler.applyIndividualMutations (Ee/ke):
        //   var r = e.sort(function(e,t){return e.timestamp-t.timestamp})
        // Apply oldest-first so later orphans targeting the same index overwrite earlier ones.
        var sortedOrphans = new ArrayList<>(orphans);
        sortedOrphans.sort(Comparator.comparing(OrphanMutationEntry::timestamp));
        for (var orphan : sortedOrphans) {
            var mutation = new DecryptedMutation.Trusted(
                    orphan.index(),
                    orphan.value(),
                    orphan.operation(),
                    orphan.timestamp(),
                    orphan.actionVersion()
            );
            var actionName = resolveActionNameSafe(mutation); // WAWebSyncdCollectionHandler.Ee: individual mutation retry uses safe path
            if (actionName == null) {
                store.addOrphanMutation(collectionName, orphan);
                continue;
            }
            var handler = handlerRegistry.findHandler(actionName);
            if (handler.isEmpty()) {
                store.addOrphanMutation(collectionName, orphan);
                continue;
            }

            try {
                var result = handler.get().applyMutation(whatsapp, mutation);
                if (result.isOrphan()) {
                    store.addOrphanMutation(collectionName, orphan);
                } else {
                    recordMutationState(collectionName, mutation, actionName, result);
                }
            } catch (Throwable throwable) {
                LOGGER.warning("Failed to retry orphan mutation: " + throwable.getMessage());
            }
        }
    }

    /**
     * Builds an {@link OrphanMutationEntry} from a trusted mutation, extracting
     * the model type (action name) and model ID (primary entity JID) from the
     * mutation index for targeted orphan lookups.
     * @param mutation        the trusted mutation to persist as an orphan
     * @param modelType       the action name extracted from the mutation grouping
     * @param modelIdOverride explicit model ID override, or {@code null} to extract from index
     * @return the constructed orphan entry
     */
    private static OrphanMutationEntry buildOrphanEntry(
            DecryptedMutation.Trusted mutation,
            String modelType,
            String modelIdOverride
    ) {
        var modelId = modelIdOverride;
        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (modelId == null && indexArray != null && indexArray.size() >= 2) {
                modelId = indexArray.getString(1);
            }
        } catch (Throwable _) {
            // Index is not valid JSON or too short — leave modelId null
        }
        return new OrphanMutationEntryBuilder()
                .index(mutation.index())
                .value(mutation.value())
                .operation(mutation.operation())
                .timestamp(mutation.timestamp())
                .actionVersion(mutation.actionVersion())
                .modelType(modelType)
                .modelId(modelId)
                .build();
    }

    /**
     * Builds an {@link OrphanMutationEntry} without a model ID override.
     * @param mutation  the trusted mutation to persist as an orphan
     * @param modelType the action name extracted from the mutation grouping
     * @return the constructed orphan entry
     */
    private static OrphanMutationEntry buildOrphanEntry(DecryptedMutation.Trusted mutation, String modelType) {
        return buildOrphanEntry(mutation, modelType, null);
    }

    /**
     * Parses the action index and returns the action name, throwing on invalid indices.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler.it}: wraps
     * {@code WAWebSyncdActionUtils.parseIndex} and throws {@code SyncdFatalError}
     * if the index is unparseable or empty. This is used in the main sync
     * processing path ({@code nt/et} categorization) where invalid indices are fatal.
     *
     * <p>If the index parses successfully but the action name at position 0 is
     * unknown, this returns {@code null} (unsupported, not fatal) — matching
     * {@code WASyncdConst.Actions.cast} returning null.
     * @param mutation the trusted mutation whose index to parse
     * @return the action name, or {@code null} if the action name is unknown
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if the index is unparseable
     */
    private String resolveActionName(DecryptedMutation.Trusted mutation) {
        // WAWebSyncdCollectionHandler.it: var n = parseIndex(e, t); if (n == null) throw SyncdFatalError
        JSONArray indexArray; // WAWebSyncdActionUtils.parseIndex
        try {
            indexArray = JSON.parseArray(mutation.index()); // WAWebSyncdActionUtils.parseIndex: JSON.parse(n)
        } catch (Throwable throwable) {
            // WAWebSyncdCollectionHandler.it: parseIndex returns null on catch -> it() throws
            throw new WhatsAppWebAppStateSyncException.UnexpectedError( // WAWebSyncdCollectionHandler.it
                    "invalid action index: " + mutation.index(), throwable);
        }
        if (indexArray == null || indexArray.isEmpty()) {
            // WAWebSyncdCollectionHandler.it: parseIndex returns null for empty -> it() throws
            throw new WhatsAppWebAppStateSyncException.UnexpectedError( // WAWebSyncdCollectionHandler.it
                    "invalid action index: " + mutation.index(), null);
        }
        var actionName = indexArray.getString(0); // WAWebSyncdActionUtils.getMutationNameFromIndex
        if (actionName == null || actionName.isBlank()) {
            // WAWebSyncdCollectionHandler.nt: Actions.cast(l[0]) returns null -> unsupported
            return null;
        }
        return actionName; // WAWebSyncdCollectionHandler.nt: valid action name
    }

    /**
     * Resolves the action name from a mutation index string without throwing.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdActionUtils.getMutationNameFromIndex}:
     * returns the action name at index 0, or {@code null} if parsing fails.
     * Used in non-fatal paths like orphan retry, logging, and model ID extraction.
     *
     * <p>Delegates to
     * {@link SyncdIndexUtils#getMutationNameFromIndex(String, String)};
     * additionally treats a blank action name as {@code null} (the JS check
     * {@code n == null} collapses to {@code undefined} in WA Web since a
     * zero-element array cannot reach this branch; Cobalt keeps the blank
     * guard as defensive behavior).
     * @param mutation the trusted mutation whose index to parse
     * @return the action name, or {@code null} if the index is invalid or action unknown
     */
    private String resolveActionNameSafe(DecryptedMutation.Trusted mutation) {
        // WAWebSyncdActionUtils.getMutationNameFromIndex: var n = parseIndex(e, t); return n == null ? void 0 : n[0]
        var actionName = SyncdIndexUtils
                .getMutationNameFromIndex(null, mutation.index());
        if (actionName == null || actionName.isBlank()) { // ADAPTED: blank guard preserves existing Cobalt behavior
            return null; // WAWebSyncdActionUtils.getMutationNameFromIndex: n == null -> return undefined
        }
        return actionName;
    }

    /**
     * Extracts the model ID from a mutation index string.
     *
     * <p>The mutation index is a JSON array where element 0 is the action name
     * and element 1 (if present) is the primary entity identifier (e.g. JID).
     * @param mutationIndex the raw index string
     * @return the model ID, or {@code null} if the index is invalid or too short
     */
    private String extractModelId(String mutationIndex) {
        // WAWebSyncdActionUtils.parseIndex: JSON.parse(n); element 1 is the model ID
        var indexArray = SyncdIndexUtils
                .parseIndex(null, mutationIndex);
        if (indexArray != null && indexArray.size() >= 2) {
            return indexArray.getString(1);
        }
        return null;
    }

    /**
     * Records the application result of a mutation in the sync action entry store.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCollectionHandler._applySetMutations} (Xe):
     * after each mutation is applied (or fails), its action state, model type, and model
     * ID are updated on the corresponding sync action entry. Only SET mutations are
     * recorded; REMOVE mutations have no entry to update.
     * @param collectionName the collection the mutation belongs to
     * @param mutation       the trusted mutation that was applied
     * @param actionName     the resolved action name, or {@code null} if unknown
     * @param result         the application result from the handler
     */
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByCollectionsInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private void recordMutationState(
            SyncPatchType collectionName,
            DecryptedMutation.Trusted mutation,
            String actionName,
            MutationApplicationResult result
    ) {
        if (mutation.operation() != SyncdOperation.SET) {
            return;
        }

        var fallbackModelId = extractModelId(mutation.index());
        for (var entry : store.getSyncActionEntries(collectionName)) {
            if (!Objects.equals(entry.actionIndex(), mutation.index())
                    || entry.actionVersion() != mutation.actionVersion()) {
                continue;
            }

            entry.setActionState(result.actionState());
            entry.setModelType(result.modelType() != null ? result.modelType() : actionName);
            entry.setModelId(result.modelId() != null ? result.modelId() : fallbackModelId);
        }
    }

    /**
     * Retries all unsupported mutations across every collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.applyAllOrphansAndUnsupported}:
     * entries with {@code Unsupported} state are re-applied alongside orphans
     * on app resume, in case a handler has since been registered for the action.
     */
    private void retryUnsupportedMutations() {
        for (var patchType : SyncPatchType.values()) { // WAWebSyncdOrphan.applyAllOrphansAndUnsupported: getSyncActionsByActionStatesInTransaction([Unsupported])
            retryUnsupportedMutations(patchType); // WAWebSyncdOrphan.applyAllOrphansAndUnsupported -> applyIndividualMutations (unsupported portion)
        }
    }

    /**
     * Retries unsupported mutations for a single collection.
     *
     * <p>Scans the sync action entry store for entries with
     * {@link SyncActionState#UNSUPPORTED} state and re-applies them
     * if a matching handler is now available.
     * @param collectionName the collection whose unsupported entries to retry
     */
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByActionStatesInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByCollectionsInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryUnsupportedMutations(SyncPatchType collectionName) {
        var unsupportedEntries = store.getSyncActionEntries(collectionName).stream()
                .filter(entry -> entry.actionState() == SyncActionState.UNSUPPORTED)
                .toList();
        for (var entry : unsupportedEntries) {
            if (entry.actionIndex() == null || entry.actionValue() == null) {
                continue;
            }

            var mutation = new DecryptedMutation.Trusted(
                    entry.actionIndex(),
                    entry.actionValue(),
                    SyncdOperation.SET,
                    entry.actionValue().timestamp().orElse(Instant.EPOCH),
                    entry.actionVersion()
            );
            var actionName = resolveActionNameSafe(mutation); // WAWebSyncdOrphan: retry path uses safe resolution
            if (actionName == null) {
                continue;
            }
            var handler = handlerRegistry.findHandler(actionName);
            if (handler.isEmpty() || mutation.actionVersion() > handler.get().version()) {
                continue;
            }

            try {
                var result = handler.get().applyMutation(whatsapp, mutation);
                recordMutationState(collectionName, mutation, actionName, result);
                if (result.isOrphan()) {
                    store.addOrphanMutation(collectionName, buildOrphanEntry(
                            mutation,
                            result.modelType() != null ? result.modelType() : actionName,
                            result.modelId()
                    ));
                }
            } catch (Throwable throwable) {
                LOGGER.warning("Failed to retry unsupported mutation: " + throwable.getMessage());
            }
        }
    }

    /**
     * Resolves conflicts between incoming remote mutations and local pending mutations.
     *
     * <p>For each remote mutation, checks if a pending local mutation with the same
     * index exists. If so, delegates to the action handler's conflict resolution.
     * After resolution, runs a cross-index conflict check to drop mutations that
     * are obsoleted by pending mutations at different indices.
     * @param remoteMutations the incoming remote mutations to resolve
     * @param collectionName  the sync collection being processed
     * @return the filtered list of remote mutations to apply
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdResolveConflict", exports = "resolveConflict", adaptation = WhatsAppAdaptation.ADAPTED)
    private SequencedCollection<DecryptedMutation.Trusted> resolveConflicts(SequencedCollection<DecryptedMutation.Trusted> remoteMutations, SyncPatchType collectionName) {
        // WAWebSyncdResolveConflict.resolveConflict: getSyncPendingMutationsByCollectionInTransaction(e)
        var pendingMutations = whatsapp.store().findPendingMutations(collectionName);
        // WAWebSyncdResolveConflict.resolveConflict: l = new Map(i.map(e => [e.index, e]))
        var pendingByIndex = new LinkedHashMap<String, DecryptedMutation.Trusted>();
        for (var pendingMutation : pendingMutations) {
            pendingByIndex.put(pendingMutation.mutation().index(), pendingMutation.mutation());
        }

        // WAWebSyncdResolveConflict.resolveConflict: n = [], a = []
        var results = new ArrayList<DecryptedMutation.Trusted>(remoteMutations.size());
        var pendingToDrop = new HashSet<String>();
        var mergedPendingToAdd = new ArrayList<SyncPendingMutation>();
        // WAWebSyncdResolveConflict.resolveConflict: u = yield s(t, l) then t.forEach(...)
        for (var remoteMutation : remoteMutations) {
            var localMutation = pendingByIndex.get(remoteMutation.index()); // WAWebSyncdResolveConflict.resolveConflict: u.get(e.index)
            if (localMutation == null) {
                results.add(remoteMutation); // WAWebSyncdResolveConflict.resolveConflict: no conflict, n.push(e)
                continue;
            }

            // ADAPTED: WAWebSyncdResolveConflict uses e.actionHandler from remote mutation; Cobalt resolves via registry
            var actionName = resolveActionNameSafe(remoteMutation);
            var handler = actionName != null ? handlerRegistry.findHandler(actionName).orElse(null) : null;
            // WAWebSyncdResolveConflict: s() calls n.resolveConflicts(t, e) — local first, remote second
            var resolution = handler != null
                    ? handler.resolveConflicts(localMutation, remoteMutation)
                    : ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);

            switch (resolution.state()) {
                case APPLY_REMOTE_DROP_LOCAL -> {
                    results.add(remoteMutation); // WAWebSyncdResolveConflict.resolveConflict: n.push(e)
                    pendingToDrop.add(remoteMutation.index()); // WAWebSyncdResolveConflict.resolveConflict: a = a.concat(i.filter(...))
                }
                case SKIP_REMOTE -> {
                    // WAWebSyncdResolveConflict.resolveConflict: break e (no-op)
                }
                case SKIP_REMOTE_DROP_LOCAL -> {
                    pendingToDrop.add(remoteMutation.index()); // WAWebSyncdResolveConflict.resolveConflict: a = a.concat(i.filter(...))
                    // ADAPTED: WA Web handlers apply merged mutation via lockForMessageRangeSync inside resolveConflicts;
                    // Cobalt defers the merged mutation via ConflictResolution.mergedMutation()
                    if (resolution.mergedMutation() != null) {
                        results.add(resolution.mergedMutation());
                        mergedPendingToAdd.add(new SyncPendingMutation(resolution.mergedMutation(), 0));
                    }
                }
            }
        }

        // WAWebSyncdResolveConflict.resolveConflict: cross-index conflict check loop
        var filteredResults = new ArrayList<DecryptedMutation.Trusted>(results.size());
        for (var remoteMutation : results) {
            // ADAPTED: WAWebSyncdResolveConflict uses n[d].actionHandler; Cobalt resolves via registry
            var actionName = resolveActionNameSafe(remoteMutation);
            var handler = actionName != null ? handlerRegistry.findHandler(actionName).orElse(null) : null;
            // WAWebSyncdResolveConflict.resolveConflict: p = yield m.dropMutationDueToCrossIndexConflict(n[d], l)
            if (handler != null && handler.dropMutationDueToCrossIndexConflict(remoteMutation, pendingByIndex)) {
                continue; // WAWebSyncdResolveConflict.resolveConflict: p is true, skip
            }
            filteredResults.add(remoteMutation); // WAWebSyncdResolveConflict.resolveConflict: c.push(n[d])
        }

        // ADAPTED: WA Web returns {remoteMutationsToApply, pendingSetMutationsToDrop} for caller to handle;
        // Cobalt performs the store cleanup here (same net effect)
        if (!pendingToDrop.isEmpty() || !mergedPendingToAdd.isEmpty()) {
            // WAWebSyncdResolveConflict.resolveConflict: _ = compactMap(a, e => e.id)
            var pendingMutationIdsToDrop = whatsapp.store()
                    .findPendingMutations(collectionName)
                    .stream()
                    .filter(pm -> pendingToDrop.contains(pm.mutation().index()))
                    .map(SyncPendingMutation::mutationId)
                    .toList();
            if (!pendingMutationIdsToDrop.isEmpty()) {
                whatsapp.store().removePendingMutations(collectionName, pendingMutationIdsToDrop);
            }
            if (!mergedPendingToAdd.isEmpty()) {
                whatsapp.store().addPendingMutations(collectionName, mergedPendingToAdd);
            }
        }

        return Collections.unmodifiableSequencedCollection(filteredResults);
    }

    /**
     * Computes a new LT-Hash from a base hash and a sequence of mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdAntiTampering.computeLtHash} (Y/J): for SET
     * mutations, removes the previous value MAC (if any) and adds the new one. For REMOVE
     * mutations, removes the existing value MAC. Returns both the new hash and the sync
     * action entry updates to be applied if the version guard passes.
     * @param patchType the collection being processed
     * @param baseHash  the starting LT-Hash
     * @param mutations the mutations to apply to the hash
     * @return the computed hash and the sync action entry updates
     */
    @WhatsAppWebExport(moduleName = "WAWebGetSyncAction", exports = "getSyncActionsByIndexMacsInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private LtHashComputation computeNewLTHash(SyncPatchType patchType, byte[] baseHash, SequencedCollection<DecryptedMutation.Untrusted> mutations) {
        var currentHash = baseHash != null ? baseHash : MutationLTHash.EMPTY_HASH;
        var toAdd = new ArrayList<byte[]>();
        var toRemove = new ArrayList<byte[]>();
        var updates = new ArrayList<SyncActionEntryUpdate>(mutations.size());

        for (var mutation : mutations) {
            var indexMac = mutation.indexMac();
            var valueMac = mutation.valueMac();

            if (mutation.operation() == SyncdOperation.SET) {
                store.findSyncActionEntry(patchType, indexMac)
                        .ifPresent(existing -> toRemove.add(existing.valueMac()));
                toAdd.add(valueMac);
                updates.add(new SyncActionEntryUpdate(
                        indexMac,
                        new SyncActionEntryBuilder()
                                .indexMac(indexMac)
                                .valueMac(valueMac)
                                .keyId(mutation.keyId())
                                .actionIndex(mutation.index())
                                .actionValue(mutation.value())
                                .actionVersion(mutation.actionVersion())
                                .build(),
                        false
                ));
            } else {
                // WAWebSyncdAntiTampering.computeLtHash (Y/J): REMOVE branch
                var removedEntry = store.findSyncActionEntry(patchType, indexMac);
                if (removedEntry.isPresent()) {
                    toRemove.add(removedEntry.get().valueMac()); // WAWebSyncdAntiTampering.computeLtHash: p.set(hex, existing.valueMac)
                    updates.add(new SyncActionEntryUpdate(indexMac, null, true));
                } else {
                    // WAWebSyncdAntiTampering.computeLtHash: missing REMOVE entry is non-fatal.
                    // WA Web sets hasMissingRemove=true and continues — the REMOVE is skipped
                    // from the LT-Hash computation. This can happen legitimately during
                    // split-brain scenarios or when patches arrive out of order.
                    LOGGER.warning("REMOVE mutation has no local entry for indexMac in " + patchType
                            + ", skipping from LT-Hash computation");
                }
            }
        }

        return new LtHashComputation(
                MutationLTHash.subtractThenAdd(currentHash, toAdd, toRemove).ltHash(),
                updates
        );
    }

    /**
     * Persists sync action entry updates computed during LT-Hash computation.
     *
     * <p>Applies SET and REMOVE updates to the sync action entry store, called only
     * when the version guard passes ({@link #updateCollectionState} returns {@code true}).
     * @param patchType the collection whose entries to update
     * @param updates   the entry updates to apply
     */
    private void applySyncActionEntryUpdates(SyncPatchType patchType, List<SyncActionEntryUpdate> updates) {
        for (var update : updates) {
            if (update.remove()) {
                store.removeSyncActionEntry(patchType, update.indexMac());
            } else {
                store.putSyncActionEntry(patchType, update.indexMac(), update.entry());
            }
        }
    }

    /**
     * Updates the collection version and LT-Hash, returning whether the update was applied.
     *
     * <p>Per WhatsApp Web {@code ot/at} (version guard): if the incoming version is not
     * newer than the currently persisted version, the entire update (including sync action
     * entry writes) is skipped.
     *
     * <p>ADAPTED: WA Web also exposes {@code bulkUpdateCollectionVersionInTransaction} used by
     * {@code WAWebSyncdCollectionsStateMachine.persistToDb} to flush the state-machine snapshot.
     * Cobalt's state machine persists per-collection via {@code store.mark*} calls on
     * the store, so bulk updates are
     * inlined as repeated per-item calls and no dedicated bulk API is introduced.
     * @param collectionName the collection to update
     * @param version        the new version
     * @param ltHash         the new LT-Hash
     * @return {@code true} if the update was applied, {@code false} if skipped (stale version)
     */
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "updateCollectionVersionAndLtHashInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebGetCollectionVersion", exports = "bulkUpdateCollectionVersionInTransaction", adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean updateCollectionState(SyncPatchType collectionName, long version, byte[] ltHash) {
        // Per WA Web ot: guard against applying state updates with an older version
        var currentVersion = getCurrentVersion(collectionName);
        if (version > 0 && currentVersion > 0 && version <= currentVersion) { // WAWebSyncdCollectionHandler.ot
            LOGGER.warning("Skipping state update for " + collectionName + ": version " + version + " is not newer than current " + currentVersion);
            return false; // WAWebSyncdCollectionHandler.ot: skip entire transaction
        }

        // Per WA Web: update both hash state and collection metadata atomically
        // to prevent inconsistent state on crash between writes.
        // The store's updateWebAppStateVersion updates both maps in one call.
        store.updateWebAppStateVersion(collectionName, version, ltHash);
        return true;
    }

    /**
     * Handles a sync error by transitioning the collection to the appropriate error state.
     * @param error          the error that occurred during sync
     * @param collectionName the collection that failed
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdFatal", exports = "handleFatalError", adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleSyncError(Throwable error, SyncPatchType collectionName) {
        if (collectionName == null) {
            return;
        }

        var metadata = store.findWebAppState(collectionName); // WAWebSyncdServerSync.S
        if (error instanceof WhatsAppWebAppStateSyncException.MissingKey missingKeyEx) {
            store.markWebAppStateBlocked(collectionName); // WAWebSyncdServerSync.S: Blocked state
            var keyId = missingKeyEx.keyId(); // WAWebSyncdServerSync.S

            // WAWebSyncdHandleMissingKeys._handleMissingKeys -> WAWebSyncdStoreMissingKeys.addMissingKeys
            // schedules the timeout check inline at the end of trackMissingKeys, so no separate
            // scheduleTimeoutCheck() call is needed here (matches WA Web's call flow).
            missingSyncKeyRequestService.requestMissingKey(keyId); // WAWebSyncdHandleMissingKeys._handleMissingKeys
            missingSyncKeyTimeoutScheduler.startPeriodicReRequestJob(); // WAWebSyncdStoreMissingKeys._startPeriodicReRequestJob
        } else if (error instanceof WhatsAppWebAppStateSyncException syncEx && syncEx.isFatal()) {
            store.markWebAppStateErrorFatal(collectionName); // WAWebSyncdServerSync.S: catch (SyncdFatalError) -> ErrorFatal

            // WAWebSyncdFatal.handleFatalError: var n = t != null ? t.map(e => String(e)) : []
            var collectionNames = List.of(String.valueOf(collectionName));

            try { // WAWebSyncdFatal.handleFatalError: yield asyncSleep(5000)
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            try { // WAWebSyncdFatal.handleFatalError: try { yield sendAppStateFatalExceptionNotification(n) }
                sendAppStateFatalExceptionNotification(collectionNames); // WAWebSyncdFatal.handleFatalError: yield sendAppStateFatalExceptionNotification(n)
            } catch (Throwable notifyError) { // WAWebSyncdFatal.handleFatalError: catch(e) ERROR("syncd: error when sending fatal message to primary").sendLogs("syncd: could not send fatal to primary")
                LOGGER.log(Level.SEVERE, "syncd: error when sending fatal message to primary", notifyError);
            }
            // ADAPTED: WAWebSyncdFatal.handleFatalError calls socketLogout(LogoutReason.SyncdFailure);
            // Cobalt routes through pluggable error handler instead of hardcoded logout
            whatsapp.handleFailure(syncEx);
        } else {
            // WAWebSyncdServerSync.S: catch (retryable) -> ErrorRetry with serverBackoff
            var firstFailureTimestamp = metadata.lastErrorTimestamp()
                    .map(Instant::toEpochMilli)
                    .orElseGet(System::currentTimeMillis);
            // WAWebSyncd.ee: i.length > 0 && (q = i[0].serverBackoff || 0, W = 0)
            // When server returns ErrorRetry with backoff, reset the global attempt counter
            var serverBackoffMs = error instanceof WhatsAppWebAppStateSyncException.RetryableServerError retryable
                    ? retryable.serverBackoffMs() // WAWebSyncdServerSync.S: serverBackoff: n.errorBackoff
                    : null;
            if (serverBackoffMs != null) {
                retryScheduler.resetAttemptCounter(); // WAWebSyncd.ee: W = 0 on ErrorRetry results
            }
            var result = retryScheduler.scheduleRetry( // WAWebSyncd.te/ne: backoff scheduling
                    collectionName,
                    firstFailureTimestamp,
                    serverBackoffMs,
                    () -> syncCollection(collectionName)
            );
            if (result) {
                store.markWebAppStateErrorRetry(collectionName); // WAWebSyncdServerSync.S: ErrorRetry
                // WAWebSyncd.ie: if (e.state === CollectionState.ErrorRetry) logCriticalBootstrapStageIfNecessary(ENTERED_RETRY_MODE)
                logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.ENTERED_RETRY_MODE);
            } else {
                store.markWebAppStateErrorFatal(collectionName); // WAWebSyncd.oe: expired -> Fatal
            }
        }
    }

    /**
     * Sends a fatal exception notification to the primary device as a peer message.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdFatalExceptionNotificationApi.sendAppStateFatalExceptionNotification}:
     * constructs an {@code AppStateFatalExceptionNotification} protobuf with the
     * affected collection names and the current timestamp (milliseconds), wraps it in a
     * {@code ProtocolMessage} with type {@code APP_STATE_FATAL_EXCEPTION_NOTIFICATION},
     * and sends it to device 0 (primary) as a peer message via
     * {@code encryptAndSendKeyMsg}.
     * @param collectionNames the collections that triggered the fatal error
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdFatalExceptionNotificationApi", exports = "sendAppStateFatalExceptionNotification", adaptation = WhatsAppAdaptation.ADAPTED)
    private void sendAppStateFatalExceptionNotification(List<String> collectionNames) {
        var myJid = whatsapp.store().jid().orElse(null); // ADAPTED: WAWebSyncdFatalExceptionNotificationApi: getMePnUserOrThrow() / getMeDevicePnOrThrow()
        if (myJid == null) {
            LOGGER.warning("Cannot send fatal exception notification: own JID not available");
            return;
        }

        var notification = new AppStateFatalExceptionNotificationBuilder() // WAWebSyncdFatalExceptionNotificationApi: {collectionNames: e, timestamp: unixTimeMs()}
                .collectionNames(collectionNames) // WAWebSyncdFatalExceptionNotificationApi: collectionNames: e
                .timestamp(Instant.now()) // WAWebSyncdFatalExceptionNotificationApi: timestamp: unixTimeMs()
                .build();

        var protocolMessage = new ProtocolMessageBuilder() // WAWebSyncdFatalExceptionNotificationApi: appStateFatalExceptionNotification: t
                .type(ProtocolMessage.Type.APP_STATE_FATAL_EXCEPTION_NOTIFICATION)
                .appStateFatalExceptionNotification(notification)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        // WAWebSyncdFatalExceptionNotificationApi: createDeviceWidFromUserAndDevice(..., 0)
        // builds a device JID with agent == 0 and device == 0; it does NOT preserve the
        // calling JID's agent. Use the 4-arg Jid.of form to construct `user:0@server`
        // explicitly so the agent is not carried over from `myJid`.
        var primaryDeviceJid = Jid.of(myJid.user(), myJid.server(), 0, 0); // WAWebSyncdFatalExceptionNotificationApi: createDeviceWidFromUserAndDevice(..., 0)
        var messageKey = new MessageKeyBuilder() // WAWebSyncdFatalExceptionNotificationApi: new WAWebMsgKey({fromMe: true, remote: getMePnUserOrThrow(), id: ...})
                .id(MessageIdGenerator.generate(MessageIdVersion.V2, myJid)) // WAWebSyncdFatalExceptionNotificationApi: yield WAWebMsgKey.newId()
                .parentJid(myJid.toUserJid()) // WAWebSyncdFatalExceptionNotificationApi: remote: getMePnUserOrThrow() — strip device/agent
                .fromMe(true) // WAWebSyncdFatalExceptionNotificationApi: fromMe: true
                .senderJid(myJid) // ADAPTED: Cobalt peer message pattern
                .build();
        var messageInfo = new ChatMessageInfoBuilder() // WAWebSyncdFatalExceptionNotificationApi: {id: n, to: ..., type: "protocol", ...}
                .key(messageKey)
                .message(messageContainer)
                .build();

        // ADAPTED: WA Web calls storePeerMessages([a]) then encryptAndSendKeyMsg({msg: a})
        // Cobalt's sendPeerMessage handles both storage and sending
        whatsapp.sendPeerMessage(primaryDeviceJid, messageInfo); // WAWebSyncdFatalExceptionNotificationApi: encryptAndSendKeyMsg({msg: a})
    }

    /**
     * Schedules the all-devices-responded check for missing key timeout.
     *
     * <p>Per WhatsApp Web: when a device responds to a key share request
     * without providing the requested key, this method should be called to
     * schedule a grace period check. If all devices have responded negatively,
     * the missing key is marked as fatal after the grace period.
     */
    public void scheduleAllDevicesRespondedCheck() {
        missingSyncKeyTimeoutScheduler.scheduleAllDevicesRespondedCheck();
    }

    /**
     * Reschedules the missing sync key timeout check.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdStoreMissingKeys._setMissingKeyTimeout}:
     * after keys are resolved (removed from the missing key store), the timeout
     * must be rescheduled because the earliest missing key may have changed
     * or there may be no missing keys left.
     */
    public void rescheduleMissingSyncKeyTimeout() {
        missingSyncKeyTimeoutScheduler.scheduleTimeoutCheck();
    }

    /**
     * Flushes all dirty collections that have pending mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSocketModel.sendLogout}: before
     * disconnecting, pending sentinel mutations are flushed to the server
     * so that key expiration is propagated to other devices.
     */
    public void flushDirtyCollections() {
        for (var patchType : SyncPatchType.values()) {
            var metadata = store.findWebAppState(patchType);
            if (metadata.state() == SyncCollectionState.DIRTY) {
                try {
                    syncCollection(patchType);
                } catch (Exception e) {
                    LOGGER.warning("Failed to flush dirty collection " + patchType + " on disconnect: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Starts the periodic sync job that syncs all collections periodically.
     *
     * <p>Per WhatsApp Web {@code syncdSyncAllCollectionsJob}: a background job
     * runs periodically to catch missed notifications by syncing all collections.
     * This ensures eventual consistency even if push notifications are lost.
     */
    public void startPeriodicSyncJob() {
        stopPeriodicSyncJob();
        scheduleNextPeriodicSync();

        // Per WA Web WAWebTasksDefinitions: start the periodic key rotation
        // background job (every 27 days) independently of mutation push
        syncKeyRotationService.startPeriodicRotationJob();

        // Per WA Web WAWebTasksDefinitions.REPORT_SYNCD_ACTION_STAT:
        // start the daily syncd stats reporting job which iterates all sync
        // action entries, groups by mutation name, buckets per-state counts,
        // and commits one MdAppStateSyncMutationStats WAM event per mutation.
        startPeriodicReportSyncdStatsJob();

        // Per WA Web WAWebTasksDefinitions.REPORT_SYNCD_KEY_STATS:
        // start the daily syncd key stats reporting job which derives the
        // per-key usage histogram and commits one SyncdKeyCount WAM event.
        startPeriodicReportSyncdKeyStatsJob();
    }

    /**
     * Schedules the next periodic sync after the configured delay.
     *
     * <p>Per WhatsApp Web {@code syncdSyncAllCollectionsJob}: the delay is determined
     * by the {@code syncd_periodic_sync_days} AB prop. Reschedules itself after
     * each execution to create a recurring job.
     */
    private void scheduleNextPeriodicSync() {
        var days = abPropsService.getInt(ABProp.SYNCD_PERIODIC_SYNC_DAYS);
        if (days <= 0) {
            return;
        }

        periodicSyncJob = SchedulerUtils.scheduleDelayed(
                Duration.ofDays(days),
                () -> {
                    try {
                        pullPatches(SyncPatchType.values());
                    } catch (Exception e) {
                        LOGGER.warning("Periodic sync job failed: " + e.getMessage());
                    } finally {
                        scheduleNextPeriodicSync();
                    }
                }
        );
    }

    /**
     * Stops the periodic sync job.
     */
    public void stopPeriodicSyncJob() {
        var job = periodicSyncJob;
        if (job != null) {
            job.cancel(false);
            periodicSyncJob = null;
        }
    }

    /**
     * Starts the daily syncd stats reporting job.
     *
     * <p>Per WhatsApp Web {@code WAWebTasksDefinitions.REPORT_SYNCD_ACTION_STAT}:
     * schedules a recurring task that every {@code DAY_SECONDS} invokes
     * {@link #reportSyncdStats()} to walk all stored sync action entries,
     * bucket their per-state counts, and commit one
     * {@code MdAppStateSyncMutationStats} WAM event per mutation name.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdReportSyncdStatJob", exports = "reportSyncdStatsJob", adaptation = WhatsAppAdaptation.ADAPTED)
    private void startPeriodicReportSyncdStatsJob() {
        stopPeriodicReportSyncdStatsJob(); // WAWebTasksDefinitions: ensure no duplicate task is scheduled
        scheduleNextPeriodicReportSyncdStats();
    }

    /**
     * Schedules the next execution of the daily syncd stats reporting job.
     *
     * <p>Per WhatsApp Web {@code WAWebTasksDefinitions}: the task body returns
     * {@code DAY_SECONDS} after each run, so the runtime reschedules the task
     * one day later. Cobalt mirrors this by self-rescheduling from within the
     * {@code finally} block of the task lambda.
     */
    private void scheduleNextPeriodicReportSyncdStats() {
        periodicReportSyncdStatsJob = SchedulerUtils.scheduleDelayed(
                Duration.ofDays(1), // WAWebTasksDefinitions.REPORT_SYNCD_ACTION_STAT: return o("WATimeUtils").DAY_SECONDS
                () -> {
                    try {
                        reportSyncdStats(); // WAWebSyncdReportSyncdStatJob.reportSyncdStatsJob body
                    } catch (Exception e) {
                        LOGGER.warning("Periodic syncd stats reporting job failed: " + e.getMessage()); // ADAPTED: WA Web job orchestrator logs per-task failures
                    } finally {
                        scheduleNextPeriodicReportSyncdStats(); // WAWebTasksDefinitions: return DAY_SECONDS -> reschedule
                    }
                }
        );
    }

    /**
     * Walks every stored sync action entry, buckets per-state counts by
     * mutation name, and commits one {@code MdAppStateSyncMutationStats} WAM
     * event per distinct mutation.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdReportSyncdStatJob.reportSyncdStatsJob}
     * plus {@code WAWebSyncdWamUtils.generateActionStatCounts}: fetches every row of
     * the sync actions table across all collections, resolves the mutation name via
     * {@code getMutationNameFromIndex} (falling back to {@code "no-mutation-name"}
     * when the index cannot be parsed), tallies each row under its mutation name
     * by {@code actionState} ({@code Success}/{@code Skipped} increment
     * {@code applied}; {@code Malformed} increments {@code invalid}; {@code Orphan}
     * increments {@code orphan}; {@code Unsupported} increments {@code unsupported};
     * {@code Failed} increments {@code failed}), converts each count to a
     * {@link MutationCountBucket} via {@link #convertToBucket(int)}, and emits
     * one event per mutation name with the bucketed fields.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdReportSyncdStatJob", exports = "reportSyncdStatsJob", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamUtils", exports = "generateActionStatCounts", adaptation = WhatsAppAdaptation.ADAPTED)
    void reportSyncdStats() {
        // WAWebSyncdWamUtils.generateActionStatCounts: var e = new Map, t = yield getSyncActionsTable().all()
        var counts = new LinkedHashMap<String, ActionStatCounts>();
        for (var patchType : SyncPatchType.values()) { // ADAPTED: WA Web reads the flat sync actions table; Cobalt iterates per-collection sync-action views
            for (var entry : store.getSyncActionEntries(patchType)) { // WAWebSyncdWamUtils.generateActionStatCounts: t.forEach(function(t) { ... })
                // WAWebSyncdWamUtils.generateActionStatCounts: var r = getMutationNameFromIndex(t.collection, t.index) || "no-mutation-name"
                var actionName = SyncdIndexUtils.getMutationNameFromIndex(patchType.toString(), entry.actionIndex());
                if (actionName == null || actionName.isBlank()) {
                    actionName = "no-mutation-name"; // WAWebSyncdWamUtils.generateActionStatCounts: || "no-mutation-name"
                }
                var bucket = counts.computeIfAbsent(actionName, name -> new ActionStatCounts()); // WAWebSyncdWamUtils.generateActionStatCounts: var a = e.get(r) ?? { action: r, applied: 0, invalid: 0, orphan: 0, unsupported: 0, failed: 0 }
                var actionState = entry.actionState();
                if (actionState == null) {
                    // ADAPTED: WA Web throws on unknown actionState via `throw Error("Match: No case...")`;
                    // Cobalt tolerates a null state so the reporting job never aborts mid-collection.
                    continue;
                }
                switch (actionState) { // WAWebSyncdWamUtils.generateActionStatCounts: exhaustive switch on t.actionState
                    case SUCCESS, SKIPPED -> bucket.applied++; // WAWebSyncdWamUtils.generateActionStatCounts: Success || Skipped -> a.applied++
                    case MALFORMED -> bucket.invalid++;         // WAWebSyncdWamUtils.generateActionStatCounts: Malformed  -> a.invalid++
                    case ORPHAN -> bucket.orphan++;             // WAWebSyncdWamUtils.generateActionStatCounts: Orphan     -> a.orphan++
                    case UNSUPPORTED -> bucket.unsupported++;   // WAWebSyncdWamUtils.generateActionStatCounts: Unsupported-> a.unsupported++
                    case FAILED -> bucket.failed++;             // WAWebSyncdWamUtils.generateActionStatCounts: Failed     -> a.failed++
                }
            }
        }

        // WAWebSyncdReportSyncdStatJob.reportSyncdStatsJob: for (var t of e.values()) { new MdAppStateSyncMutationStatsWamEvent({...}).commit() }
        for (var entry : counts.entrySet()) {
            var actionName = entry.getKey();
            var stats = entry.getValue();
            wamService.commit(new MdAppStateSyncMutationStatsEventBuilder()
                    .syncdAction(actionName)                           // WAWebSyncdReportSyncdStatJob: syncdAction: t.action
                    .applied(convertToBucket(stats.applied))           // WAWebSyncdReportSyncdStatJob: applied: n.convertToBucket(t.applied)
                    .invalid(convertToBucket(stats.invalid))           // WAWebSyncdReportSyncdStatJob: invalid: n.convertToBucket(t.invalid)
                    .orphan(convertToBucket(stats.orphan))             // WAWebSyncdReportSyncdStatJob: orphan: n.convertToBucket(t.orphan)
                    .unsupported(convertToBucket(stats.unsupported))   // WAWebSyncdReportSyncdStatJob: unsupported: n.convertToBucket(t.unsupported)
                    .failed(convertToBucket(stats.failed))             // WAWebSyncdReportSyncdStatJob: failed: n.convertToBucket(t.failed)
                    .build());
        }
    }

    /**
     * Converts a non-negative mutation count to its corresponding
     * {@link MutationCountBucket}.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdWamUtils.convertToBucket}: maps
     * {@code 0 -> ZERO}, {@code 1 -> ONE}, {@code <10 -> LT10},
     * {@code <100 -> LT100}, {@code <500 -> LT500}, {@code <1000 -> LT1K},
     * {@code <5000 -> LT5K}, otherwise {@code GTE5K}. Negative inputs throw
     * in WA Web via {@code err("cannot convert negative number to a bucket")}.
     * @param count the non-negative mutation count
     * @return the bucket constant for the given count
     * @throws IllegalArgumentException when {@code count} is negative
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamUtils", exports = "convertToBucket", adaptation = WhatsAppAdaptation.DIRECT)
    private static MutationCountBucket convertToBucket(int count) {
        // WAWebSyncdWamUtils.convertToBucket: if (e<0) throw err("cannot convert negative number to a bucket")
        if (count < 0) {
            throw new IllegalArgumentException("cannot convert negative number to a bucket");
        }
        if (count == 0) return MutationCountBucket.ZERO;    // WAWebSyncdWamUtils.convertToBucket: e===0 -> ZERO
        if (count == 1) return MutationCountBucket.ONE;     // WAWebSyncdWamUtils.convertToBucket: e===1 -> ONE
        if (count < 10) return MutationCountBucket.LT10;    // WAWebSyncdWamUtils.convertToBucket: e<10  -> LT10
        if (count < 100) return MutationCountBucket.LT100;  // WAWebSyncdWamUtils.convertToBucket: e<100 -> LT100
        if (count < 500) return MutationCountBucket.LT500;  // WAWebSyncdWamUtils.convertToBucket: e<500 -> LT500
        if (count < 1_000) return MutationCountBucket.LT1K; // WAWebSyncdWamUtils.convertToBucket: e<1e3 -> LT1K
        if (count < 5_000) return MutationCountBucket.LT5K; // WAWebSyncdWamUtils.convertToBucket: e<5e3 -> LT5K
        return MutationCountBucket.GTE5K;                   // WAWebSyncdWamUtils.convertToBucket: else  -> GTE5K
    }

    /**
     * Starts the daily syncd key stats reporting job.
     *
     * <p>Per WhatsApp Web {@code WAWebTasksDefinitions.REPORT_SYNCD_KEY_STATS}:
     * schedules a recurring task that every {@code DAY_SECONDS} invokes
     * {@link #reportSyncdKeyStats()} to derive the per-app-state-sync-key
     * usage histogram and commit one {@code SyncdKeyCount} WAM event.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdReportKeyStatsJob", exports = "reportSyncdKeyStatsJob", adaptation = WhatsAppAdaptation.ADAPTED)
    private void startPeriodicReportSyncdKeyStatsJob() {
        stopPeriodicReportSyncdKeyStatsJob(); // WAWebTasksDefinitions: ensure no duplicate task is scheduled
        scheduleNextPeriodicReportSyncdKeyStats();
    }

    /**
     * Schedules the next execution of the daily syncd key stats reporting job.
     *
     * <p>Per WhatsApp Web {@code WAWebTasksDefinitions}: the task body returns
     * {@code DAY_SECONDS} when the {@code gkx 26258} kill switch is OFF and
     * {@code DAY_SECONDS * 3} when it is ON. Cobalt does not consult the
     * gatekeeper and uses the un-gated daily cadence.
     */
    private void scheduleNextPeriodicReportSyncdKeyStats() {
        periodicReportSyncdKeyStatsJob = SchedulerUtils.scheduleDelayed(
                Duration.ofDays(1), // WAWebTasksDefinitions.REPORT_SYNCD_KEY_STATS: gkx("26258") ? DAY_SECONDS*3 : DAY_SECONDS — Cobalt always uses DAY_SECONDS
                () -> {
                    try {
                        reportSyncdKeyStats(); // WAWebSyncdReportKeyStatsJob.reportSyncdKeyStatsJob body
                    } catch (Exception e) {
                        LOGGER.warning("Periodic syncd key stats reporting job failed: " + e.getMessage()); // ADAPTED: WA Web job orchestrator logs per-task failures
                    } finally {
                        scheduleNextPeriodicReportSyncdKeyStats(); // WAWebTasksDefinitions: return DAY_SECONDS -> reschedule
                    }
                }
        );
    }

    /**
     * Stops the daily syncd key stats reporting job.
     */
    public void stopPeriodicReportSyncdKeyStatsJob() {
        var job = periodicReportSyncdKeyStatsJob;
        if (job != null) {
            job.cancel(false);
            periodicReportSyncdKeyStatsJob = null;
        }
    }

    /**
     * Computes per-app-state-sync-key usage statistics from the local store
     * and emits one {@code SyncdKeyCount} WAM event with the bucketed
     * percentile counts.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdReportKeyStatsJob.reportSyncdKeyStatsJob}:
     * delegates to {@link #getKeyStats()} and commits a {@code SyncdKeyCountWamEvent}
     * with {@code keysUsedInSnapshotCount}, {@code p80MuationsPerKey},
     * {@code p95MuationsPerKey}, {@code totalKeyCount}, and (when defined)
     * {@code syncdSessionLengthDays}.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdReportKeyStatsJob", exports = "reportSyncdKeyStatsJob", adaptation = WhatsAppAdaptation.ADAPTED)
    void reportSyncdKeyStats() {
        // WAWebSyncdReportKeyStatsJob.reportSyncdKeyStatsJob: if (!gkx("26258")) — Cobalt does not consult the kill-switch gatekeeper, so the job always runs
        var stats = getKeyStats(); // WAWebSyncdReportKeyStatsJob.reportSyncdKeyStatsJob: var e = yield WAWebSyncdWamUtils.getKeyStats()
        var event = new SyncdKeyCountEventBuilder()
                .keysUsedInSnapshotCount(stats.keysUsedInSnapshotCount())   // WAWebSyncdReportKeyStatsJob: keysUsedInSnapshotCount: e.keysUsedInSnapshotCount
                .p80MuationsPerKey(stats.p80MutationsPerKey())              // WAWebSyncdReportKeyStatsJob: p80MuationsPerKey: e.p80MuationsPerKey
                .p95MuationsPerKey(stats.p95MutationsPerKey())              // WAWebSyncdReportKeyStatsJob: p95MuationsPerKey: e.p95MuationsPerKey
                .totalKeyCount(stats.totalKeyCount());                      // WAWebSyncdReportKeyStatsJob: totalKeyCount: e.totalKeyCount
        var sessionLengthDays = stats.syncdSessionLengthDays();
        if (sessionLengthDays != null) { // WAWebSyncdReportKeyStatsJob.reportSyncdKeyStatsJob: e.syncdSessionLengthDays != null && (t.syncdSessionLengthDays = e.syncdSessionLengthDays)
            event.syncdSessionLengthDays(sessionLengthDays);
        }
        wamService.commit(event.build()); // WAWebSyncdReportKeyStatsJob: new SyncdKeyCountWamEvent(t).commit()
    }

    /**
     * Computes per-app-state-sync-key usage statistics across the entire local
     * sync action store.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdWamUtils.getKeyStats}: collects all
     * known app state sync keys via {@code getAllSyncKeysInTransaction}, all
     * stored sync action entries across every collection, and the
     * {@code session_start} primary version timestamp; computes the syncd
     * session length in days as
     * {@code Math.round((unixTimeMs() - sessionStart) / (1000 * 3600 * 24))};
     * and delegates the bucket math to {@link #getKeyStatsInternal(Collection,
     * Collection, Integer)}.
     * @return the aggregated key statistics
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamUtils", exports = "getKeyStats", adaptation = WhatsAppAdaptation.ADAPTED)
    KeyStats getKeyStats() {
        // WAWebSyncdWamUtils.getKeyStats: var e = yield o("WAWebGetSyncKey").getAllSyncKeysInTransaction()
        var keys = whatsapp.store().appStateKeys();
        // WAWebSyncdWamUtils.getKeyStats: var t = yield o("WAWebSchemaSyncActions").getSyncActionsTable().all()
        var entries = new ArrayList<SyncActionEntry>();
        for (var patchType : SyncPatchType.values()) { // ADAPTED: WA Web reads the flat sync actions table; Cobalt iterates per-collection sync-action views
            entries.addAll(whatsapp.store().getSyncActionEntries(patchType));
        }
        // WAWebSyncdWamUtils.getKeyStats: var n = yield c() — c() reads the session_start primary_version timestamp
        var sessionStart = getSyncdSessionStartTimestamp();
        // WAWebSyncdWamUtils.getKeyStats: var r = n == null ? void 0 : Math.round((WATimeUtils.unixTimeMs() - n) / (1000 * 3600 * 24))
        Integer sessionLengthDays = null;
        if (sessionStart != null) {
            var deltaMs = System.currentTimeMillis() - sessionStart.toEpochMilli();
            sessionLengthDays = (int) Math.round(deltaMs / (1000.0 * 3600.0 * 24.0));
        }
        // WAWebSyncdWamUtils.getKeyStats: return _(e, t, r)
        return getKeyStatsInternal(keys, entries, sessionLengthDays);
    }

    /**
     * Reads the {@code session_start} primary version timestamp from the
     * stored sync action entries.
     *
     * <p>Per WhatsApp Web inner helper {@code c} of {@code WAWebSyncdWamUtils}:
     * {@code yield getSyncActionsTable().get('["primary_version","session_start"]')}
     * and returns {@code e?.timestamp}. Cobalt indexes sync action entries by
     * their HMAC index instead of the JSON-encoded plaintext index, so this
     * helper scans the {@code REGULAR_LOW} collection for an entry whose
     * {@code actionIndex} equals the canonical {@code session_start} index.
     * @return the {@code session_start} timestamp, or {@code null} when no
     *         {@code session_start} entry has been recorded yet
     */
    private Instant getSyncdSessionStartTimestamp() {
        // WAWebSyncdWamUtils.c: var e = yield getSyncActionsTable().get('["primary_version","session_start"]')
        for (var entry : whatsapp.store().getSyncActionEntries(SyncPatchType.REGULAR_LOW)) {
            var actionIndex = entry.actionIndex();
            if (actionIndex == null) {
                continue;
            }
            // ADAPTED: WA Web does a primary-key lookup; Cobalt scans because the table is HMAC-keyed.
            // The canonical index is the JSON array `["primary_version","session_start"]`.
            if (!actionIndex.contains("primary_version") || !actionIndex.contains("session_start")) {
                continue;
            }
            var actionValue = entry.actionValue();
            if (actionValue == null) {
                continue;
            }
            return actionValue.timestamp().orElse(null); // WAWebSyncdWamUtils.c: return e == null ? void 0 : e.timestamp
        }
        return null; // WAWebSyncdWamUtils.c: undefined when no row is found
    }

    /**
     * Computes the {@code SyncdKeyCount} payload from the supplied keys,
     * sync action entries, and optional session length.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdWamUtils.getKeyStatsInternal}: maps
     * every entry's {@code keyId} to its base64 encoding, deduplicates the
     * result for {@code keysUsedInSnapshotCount}, builds the per-key usage
     * histogram, sorts the per-key counts ascending, and reports the
     * {@code floor(N * 0.8) - 1} and {@code floor(N * 0.95) - 1} percentiles.
     * The {@code totalKeyCount} field is the size of the supplied key
     * collection (not the histogram size).
     * @param keys the collection of all known app state sync keys
     * @param entries the collection of all stored sync action entries
     * @param syncdSessionLengthDays the syncd session length in days, or
     *                               {@code null} when no {@code session_start}
     *                               timestamp has been recorded
     * @return the aggregated key statistics
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdWamUtils", exports = "getKeyStatsInternal", adaptation = WhatsAppAdaptation.DIRECT)
    KeyStats getKeyStatsInternal(Collection<AppStateSyncKey> keys, Collection<SyncActionEntry> entries, Integer syncdSessionLengthDays) {
        // WAWebSyncdWamUtils.getKeyStatsInternal: var r = t.map(function(e) { return WABase64.encodeB64(e.keyId) })
        var encodedKeyIds = new ArrayList<String>(entries.size());
        for (var entry : entries) {
            var keyId = entry.keyId();
            if (keyId == null) {
                continue; // ADAPTED: WA Web assumes every row has a keyId; tolerate nulls because Cobalt's column is nullable
            }
            encodedKeyIds.add(Base64.getEncoder().encodeToString(keyId)); // WAWebSyncdWamUtils.getKeyStatsInternal: WABase64.encodeB64(e.keyId)
        }
        // WAWebSyncdWamUtils.getKeyStatsInternal: var a = Array.from(new Set(r))
        var distinctKeyIds = new HashSet<>(encodedKeyIds);
        // WAWebSyncdWamUtils.getKeyStatsInternal: var i = new Map; for (var l of r) i.set(l, (i.get(l) || 0) + 1)
        var perKeyCounts = new HashMap<String, Integer>();
        for (var encoded : encodedKeyIds) {
            perKeyCounts.merge(encoded, 1, Integer::sum);
        }
        // WAWebSyncdWamUtils.getKeyStatsInternal: var s = Array.from(i.values()).sort()
        // WA Web's `.sort()` with no comparator sorts as strings; for the small positive ints stored here
        // numeric and lexicographic order coincide on inputs <10. Cobalt sorts numerically since the
        // values are mutation counts, which is the intent of the percentile computation.
        var sortedCounts = new ArrayList<>(perKeyCounts.values());
        Collections.sort(sortedCounts);
        // WAWebSyncdWamUtils.getKeyStatsInternal: var u = s.length, c = Math.floor(u * .8) - 1, d = Math.floor(u * .95) - 1
        var size = sortedCounts.size();
        var p80Index = (int) Math.floor(size * 0.8) - 1;
        var p95Index = (int) Math.floor(size * 0.95) - 1;
        // WAWebSyncdWamUtils.getKeyStatsInternal: return { totalKeyCount: e.length, keysUsedInSnapshotCount: a.length,
        //                                                 p80MuationsPerKey: s[c], p95MuationsPerKey: s[d],
        //                                                 syncdSessionLengthDays: n }
        // Out-of-bounds indices map to undefined in JS; Cobalt represents that as null (the WAM event field is OptionalInt).
        Integer p80 = (p80Index >= 0 && p80Index < size) ? sortedCounts.get(p80Index) : null; // WAWebSyncdWamUtils.getKeyStatsInternal: s[c]
        Integer p95 = (p95Index >= 0 && p95Index < size) ? sortedCounts.get(p95Index) : null; // WAWebSyncdWamUtils.getKeyStatsInternal: s[d]
        return new KeyStats(
                keys.size(),                 // WAWebSyncdWamUtils.getKeyStatsInternal: totalKeyCount: e.length
                distinctKeyIds.size(),       // WAWebSyncdWamUtils.getKeyStatsInternal: keysUsedInSnapshotCount: a.length
                p80,                         // WAWebSyncdWamUtils.getKeyStatsInternal: p80MuationsPerKey: s[c]
                p95,                         // WAWebSyncdWamUtils.getKeyStatsInternal: p95MuationsPerKey: s[d]
                syncdSessionLengthDays       // WAWebSyncdWamUtils.getKeyStatsInternal: syncdSessionLengthDays: n
        );
    }

    /**
     * Aggregated per-app-state-sync-key statistics returned by
     * {@link #getKeyStats()} and {@link #getKeyStatsInternal(Collection,
     * Collection, Integer)}.
     * @param totalKeyCount total number of app state sync keys present in the
     *                      local store
     * @param keysUsedInSnapshotCount number of distinct keys that have at
     *                                least one stored sync action entry
     * @param p80MutationsPerKey mutation count at the 80th percentile of the
     *                           per-key histogram, or {@code null} when the
     *                           histogram is empty
     * @param p95MutationsPerKey mutation count at the 95th percentile of the
     *                           per-key histogram, or {@code null} when the
     *                           histogram is empty
     * @param syncdSessionLengthDays days since the {@code session_start}
     *                               primary version timestamp, or {@code null}
     *                               when no {@code session_start} entry has
     *                               been recorded
     */
    record KeyStats(
            int totalKeyCount,
            int keysUsedInSnapshotCount,
            Integer p80MutationsPerKey,
            Integer p95MutationsPerKey,
            Integer syncdSessionLengthDays
    ) {
    }

    /**
     * Stops the daily syncd stats reporting job.
     */
    public void stopPeriodicReportSyncdStatsJob() {
        var job = periodicReportSyncdStatsJob;
        if (job != null) {
            job.cancel(false);
            periodicReportSyncdStatsJob = null;
        }
    }

    /**
     * Mutable accumulator used to tally per-action-state counts for a single
     * mutation name while walking the sync actions table.
     */
    private static final class ActionStatCounts {
        /**
         * Count of mutations in the {@code SUCCESS} or {@code SKIPPED} state.
         */
        int applied;

        /**
         * Count of mutations in the {@code MALFORMED} state.
         */
        int invalid;

        /**
         * Count of mutations in the {@code ORPHAN} state.
         */
        int orphan;

        /**
         * Count of mutations in the {@code UNSUPPORTED} state.
         */
        int unsupported;

        /**
         * Count of mutations in the {@code FAILED} state.
         */
        int failed;
    }

    /**
     * Resets all background jobs and schedulers.
     *
     * <p>Stops the periodic sync job, key rotation job, daily syncd stats
     * reporting job, backoff scheduler, and missing key timeout scheduler.
     * Called during disconnect or logout.
     */
    public void reset() {
        stopPeriodicSyncJob();
        stopPeriodicReportSyncdStatsJob();
        stopPeriodicReportSyncdKeyStatsJob();
        syncKeyRotationService.stopPeriodicRotationJob();
        retryScheduler.close();
        missingSyncKeyTimeoutScheduler.shutdown();
    }
}


