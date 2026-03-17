package com.github.auties00.cobalt.sync;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
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
import com.github.auties00.cobalt.util.SchedulerUtils;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;


/**
 * Main coordinator for WhatsApp Web App State synchronization.
 *
 * <p>This class manages bidirectional synchronization of application state
 * across multiple devices using end-to-end encryption and LT-Hash verification.
 */
public final class WebAppStateService {
    private static final Logger LOGGER = Logger.getLogger(WebAppStateService.class.getName());

    private final WhatsAppClient whatsapp;
    private final WhatsAppStore store;
    private final MutationRequestBuilder requestBuilder;
    private final MutationResponseParser responseParser;
    private final MutationIntegrityVerifier integrityVerifier;
    private final WebAppStateHandlerRegistry handlerRegistry;
    private final WebAppStateBackoffScheduler retryScheduler;
    private final MissingSyncKeyTimeoutScheduler missingSyncKeyTimeoutScheduler;
    private final MissingSyncKeyRequestService missingSyncKeyRequestService;
    private final SyncKeyRotationService syncKeyRotationService;
    private final ABPropsService abPropsService;
    private final SnapshotRecoveryService snapshotRecoveryService;
    private volatile CompletableFuture<?> periodicSyncJob;

    /**
     * Creates a new WebAppStateManager instance.
     *
     * @param whatsapp               the Whatsapp instance to use for store access and node sending
     * @param abPropsService         the AB props service for configuration values
     * @param snapshotRecoveryService the snapshot recovery service for peer recovery
     */
    public WebAppStateService(WhatsAppClient whatsapp, ABPropsService abPropsService, SnapshotRecoveryService snapshotRecoveryService) {
        this.whatsapp = whatsapp;
        this.store = whatsapp.store();
        this.abPropsService = abPropsService;
        this.requestBuilder = new MutationRequestBuilder(whatsapp, abPropsService);
        this.responseParser = new MutationResponseParser();
        this.handlerRegistry = new WebAppStateHandlerRegistry();
        this.integrityVerifier = new MutationIntegrityVerifier(store);
        this.retryScheduler = new WebAppStateBackoffScheduler();
        this.missingSyncKeyRequestService = new MissingSyncKeyRequestService(whatsapp);
        this.missingSyncKeyTimeoutScheduler = new MissingSyncKeyTimeoutScheduler(whatsapp, abPropsService, missingSyncKeyRequestService);
        this.syncKeyRotationService = new SyncKeyRotationService(whatsapp, abPropsService);
        this.snapshotRecoveryService = snapshotRecoveryService;
    }

    /**
     * Pushes local patches to the server.
     * Called from Whatsapp.pushWebAppState().
     *
     * @param patchType the collection type to sync
     * @param patches the patches to push
     */
    public void pushPatches(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // Per WA Web WAWebSyncdKeyManagement.getActiveKey: check if the
        // current key needs rotation before pushing mutations
        syncKeyRotationService.ensureActiveKey(true);

        // Mark collection as dirty
        store.markWebAppStateDirty(patchType);

        // Store patches as pending mutations
        whatsapp.store().addPendingMutations(patchType, patches);

        // Trigger sync
        syncCollection(patchType);
    }

    /**
     * Pulls patches from the server.
     * Called from Whatsapp.pullWebAppState().
     *
     * @param patchTypes the collection types to sync
     */
    public void pullPatches(SyncPatchType... patchTypes) {
        // Per WA Web: batch all dirty collections (including critical) into a
        // single IQ to reduce round-trips during bootstrap
        var allCollections = new LinkedHashSet<SyncPatchType>();
        Collections.addAll(allCollections, patchTypes);
        if (!allCollections.isEmpty()) {
            syncCollectionsBatched(allCollections);
        }
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
     * @param patchTypes the non-critical collection types to sync
     */
    private void syncCollectionsBatched(Set<SyncPatchType> patchTypes) {
        var collectionPatches = new LinkedHashMap<SyncPatchType, SequencedCollection<SyncPendingMutation>>();
        var skippedUploads = new LinkedHashSet<SyncPatchType>();
        for (var patchType : patchTypes) {
            var pending = whatsapp.store().findPendingMutations(patchType);
            if (!pending.isEmpty() && !store.findWebAppState(patchType).bootstrapped()) {
                skippedUploads.add(patchType);
                pending = List.of();
            }
            collectionPatches.put(patchType, pending);
        }

        try {
            for (var patchType : patchTypes) {
                store.markWebAppStateInFlight(patchType);
            }

            var batchedRequest = requestBuilder.buildBatchedSyncRequest(collectionPatches);
            var responseNode = whatsapp.sendNode(batchedRequest.node());
            var responses = responseParser.parseBatchedSyncResponse(responseNode);

            for (var response : responses) {
                try {
                    handleSyncResponse(response);
                    var uploadInfo = batchedRequest.uploadInfos().get(response.collectionName());
                    if (uploadInfo != null) {
                        var state = store.findWebAppState(response.collectionName()).state();
                        if (state != SyncCollectionState.ERROR_FATAL
                                && state != SyncCollectionState.ERROR_RETRY
                                && state != SyncCollectionState.BLOCKED) {
                            processUploadSuccess(uploadInfo);
                        }
                    }
                    retryScheduler.resetAttemptCounter();
                } catch (Throwable throwable) {
                    handleSyncError(throwable, response.collectionName());
                }
            }
        } catch (Throwable throwable) {
            for (var patchType : patchTypes) {
                handleSyncError(throwable, patchType);
            }
            return;
        }

        // Per WA Web: collections that still need pagination fall back to individual sync
        for (var patchType : patchTypes) {
            if (skippedUploads.contains(patchType)
                    && store.findWebAppState(patchType).bootstrapped()
                    && !whatsapp.store().findPendingMutations(patchType).isEmpty()) {
                store.markWebAppStateDirty(patchType);
            }
            var state = store.findWebAppState(patchType).state();
            if (state == SyncCollectionState.PENDING || state == SyncCollectionState.DIRTY) {
                syncCollection(patchType);
            }
        }
    }

    /**
     * Retries orphan mutations across all collections.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.applyAllOrphansAndUnsupported}:
     * all orphan mutations are retried across every collection. This is used on
     * app resume to resolve any orphans accumulated while the app was suspended.
     */
    public void retryAllOrphanMutations() {
        for (var patchType : SyncPatchType.values()) {
            retryOrphanMutations(patchType);
        }
    }

    /**
     * Retries orphan mutations that match the specified entity identifiers.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan.checkOrphanMutations}: when
     * new entities become available (e.g., messages from history sync or new
     * contacts), only orphans matching those specific entity IDs are retried
     * instead of scanning all orphans across all collections.
     *
     * @param modelIds the entity identifiers to match (e.g. message IDs or JIDs)
     */
    public void retryOrphanMutationsForEntities(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }

        var modelIdSet = modelIds instanceof Set ? (Set<String>) modelIds : new HashSet<>(modelIds);
        for (var patchType : SyncPatchType.values()) {
            var orphans = store.findOrphanMutations(patchType);
            if (orphans.isEmpty()) {
                continue;
            }

            var matching = orphans.stream()
                    .filter(o -> o.modelId() != null && modelIdSet.contains(o.modelId()))
                    .toList();
            if (matching.isEmpty()) {
                continue;
            }

            store.removeOrphanMutations(patchType, matching);
            retryOrphanEntries(patchType, matching);
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
    public void resumeAfterRestart() {
        var collectionsToSync = new LinkedHashSet<SyncPatchType>();
        for (var patchType : SyncPatchType.values()) {
            var metadata = store.findWebAppState(patchType);
            switch (metadata.state()) {
                case IN_FLIGHT, PENDING, ERROR_RETRY -> {
                    store.markWebAppStateDirty(patchType);
                    collectionsToSync.add(patchType);
                }
                case BLOCKED -> {
                    missingSyncKeyTimeoutScheduler.scheduleTimeoutCheck();
                    missingSyncKeyTimeoutScheduler.startPeriodicReRequestJob();
                    store.markWebAppStateDirty(patchType);
                    collectionsToSync.add(patchType);
                }
                case DIRTY -> collectionsToSync.add(patchType);
                default -> {
                    // UP_TO_DATE, DIRTY, ERROR_FATAL: no action needed
                }
            }

            if (!whatsapp.store().findPendingMutations(patchType).isEmpty()
                    && store.findWebAppState(patchType).state() != SyncCollectionState.ERROR_FATAL) {
                if (store.findWebAppState(patchType).state() == SyncCollectionState.UP_TO_DATE) {
                    store.markWebAppStateDirty(patchType);
                }
                collectionsToSync.add(patchType);
            }
        }

        // Per WA Web processOnAppResume: retry orphan mutations on resume
        retryAllOrphanMutations();
        retryUnsupportedMutations();

        if (!collectionsToSync.isEmpty()) {
            pullPatches(collectionsToSync.toArray(SyncPatchType[]::new));
        }
    }

    private static final int MAX_CONFLICT_RETRIES = 5;
    private static final int MAX_CONFLICT_RETRIES_HAS_MORE = 500;
    private static final int MAX_PAGINATION_ITERATIONS = 500;

    private record SyncRoundResult(
            MutationSyncResponse response,
            SyncRequest.UploadedPatchInfo uploadInfo,
            boolean skippedPendingUpload
    ) {
    }

    private record SyncActionEntryUpdate(
            byte[] indexMac,
            SyncActionEntry entry,
            boolean remove
    ) {
    }

    private record LtHashComputation(
            byte[] newHash,
            List<SyncActionEntryUpdate> updates
    ) {
    }

    private void syncCollection(SyncPatchType patchType) {
        var conflictRetries = 0;
        var paginationIterations = 0;
        while(store.findWebAppState(patchType).state() != SyncCollectionState.UP_TO_DATE) {
            // Per WA Web: cap overall pagination iterations to prevent infinite loops
            if (++paginationIterations > MAX_PAGINATION_ITERATIONS) {
                LOGGER.warning("Pagination cap reached for collection " + patchType + " after " + MAX_PAGINATION_ITERATIONS + " iterations");
                // Per WA Web: pagination cap exceeded is retryable, not fatal
                store.markWebAppStateErrorRetry(patchType);
                break;
            }

            try {
                // Get the sync response
                var syncResult = sendSyncRequestOrThrow(patchType);

                // Process the result — per WA Web, mutations are applied per-patch inside
                handleSyncResponse(syncResult.response());

                // Per WA Web _uploadSuccessful: if we pushed mutations,
                // persist sync action entries, update version/LT-Hash,
                // and clear uploaded pending mutations
                if (syncResult.uploadInfo() != null) {
                    processUploadSuccess(syncResult.uploadInfo());
                }

                if (syncResult.skippedPendingUpload()
                        && store.findWebAppState(patchType).bootstrapped()
                        && !whatsapp.store().findPendingMutations(patchType).isEmpty()) {
                    store.markWebAppStateDirty(patchType);
                }

                // Reset conflict counter on success
                conflictRetries = 0;
                retryScheduler.resetAttemptCounter();
            } catch (WhatsAppWebAppStateSyncException.Conflict conflict) {
                // Per WA Web: conflict without pending mutations is treated as success
                var hasPending = !whatsapp.store().findPendingMutations(patchType).isEmpty();
                if (!hasPending) {
                    store.markWebAppStateUpToDate(patchType);
                    break;
                }

                var limit = conflict.hasMorePatches() ? MAX_CONFLICT_RETRIES_HAS_MORE : MAX_CONFLICT_RETRIES;
                if (++conflictRetries >= limit) {
                    handleSyncError(conflict, patchType);
                    break;
                }
                // Immediate retry without backoff
            } catch (Throwable throwable) {
                handleSyncError(throwable, patchType);
                break;
            }
        }
    }

    private SyncRoundResult sendSyncRequestOrThrow(SyncPatchType patchType) {
        // Get pending mutations
        var pending = whatsapp.store()
                .findPendingMutations(patchType);
        var skippedPendingUpload = false;

        // Per WA Web collectionsToSkip: skip pending mutations for unbootstrapped collections
        if (!pending.isEmpty() && !store.findWebAppState(patchType).bootstrapped()) {
            LOGGER.fine("Skipping pending mutations for unbootstrapped collection " + patchType);
            skippedPendingUpload = true;
            pending = List.of();
        }

        // Build request
        var syncRequest = requestBuilder.buildSyncRequest(patchType, pending);

        // Mark as in-flight
        store.markWebAppStateInFlight(patchType);

        // Send a request and get a response (synchronous)
        var response = whatsapp.sendNode(syncRequest.node());

        // Handle response
        var parsedResponse = responseParser.parseSyncResponse(response);
        return new SyncRoundResult(parsedResponse, syncRequest.uploadInfo(), skippedPendingUpload);
    }

    private void handleSyncResponse(MutationSyncResponse syncResponse) {
        var collectionName = syncResponse.collectionName();
        var receivedMutations = false;

        // Phase A: Process snapshot if present
        var recoveredFromSnapshot = false;
        if (syncResponse.snapshotReference().isPresent()) {
            if (syncResponse.version() <= 0) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Snapshot missing required version in " + collectionName, null);
            }

            store.clearSyncActionEntries(collectionName);

            var snapshot = downloadAndDecodeSnapshot(syncResponse.snapshotReference().get());
            var snapshotMutations = getMutationsFromSnapshot(snapshot);
            var untrusted = snapshotMutations.isEmpty()
                    ? List.<DecryptedMutation.Untrusted>of()
                    : decryptMutations(snapshotMutations);

            if (!untrusted.isEmpty()) {
                validateNoDuplicateIndices(collectionName, untrusted, false);
            }

            var newHash = computeNewLTHash(collectionName, MutationLTHash.EMPTY_HASH, untrusted);

            try {
                integrityVerifier.verifySnapshotMac(collectionName, syncResponse.version(), snapshot, newHash.newHash());

                updateCollectionState(collectionName, syncResponse.version(), newHash.newHash());
                applySyncActionEntryUpdates(collectionName, newHash.updates());
                if (!untrusted.isEmpty()) {
                    var snapshotTrusted = new ArrayList<DecryptedMutation.Trusted>(untrusted.size());
                    for (var entry : untrusted) {
                        snapshotTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
                    }
                    applyMutations(collectionName, snapshotTrusted);
                    receivedMutations = true;
                }
            } catch (WhatsAppWebAppStateSyncException e) {
                if (!e.isFatal() || !snapshotRecoveryService.shouldAttemptRecovery(collectionName, snapshotMutations.size())) {
                    throw e;
                }

                var recoveryResponse = snapshotRecoveryService.requestRecovery(collectionName);
                if (recoveryResponse == null) {
                    throw e;
                }

                var recoveredSnapshot = snapshotRecoveryService.decodeRecoverySnapshot(recoveryResponse);
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
                receivedMutations = !recoveredTrusted.isEmpty();
            }
        }

        if (recoveredFromSnapshot) {
            store.markWebAppStateUpToDate(collectionName);
            return;
        }

        if (syncResponse.snapshotReference().isEmpty() && !store.findWebAppState(collectionName).bootstrapped()) {
            updateCollectionState(collectionName, 0L, MutationLTHash.EMPTY_HASH);
        }

        var sortedPatches = new ArrayList<>(syncResponse.patches());
        sortedPatches.sort(Comparator.comparingLong(patch -> patch.version()
                .map(version -> version.version().orElse(0L))
                .orElse(0L)));

        validateNoDuplicatePatchVersions(collectionName, sortedPatches);

        if (!sortedPatches.isEmpty()) {
            var localVersion = getCurrentVersion(collectionName);
            long minPatchVersion = sortedPatches.getFirst().version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L);
            if (localVersion > 0 && minPatchVersion > localVersion + 1) {
                throw new WhatsAppWebAppStateSyncException.MissingPatches(collectionName, localVersion, minPatchVersion);
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
            var patchValueMacs = untrusted.stream()
                    .map(DecryptedMutation.Untrusted::valueMac)
                    .toList();
            var snapshotMacValid = integrityVerifier.verifyPatchIntegrity(collectionName, patch, newHash.newHash(), patchValueMacs);
            if (!snapshotMacValid) {
                store.markWebAppStateMacMismatch(collectionName);
                LOGGER.warning("Patch snapshot MAC mismatch for " + collectionName + " at version " + patchVersion + ", marking mac-mismatch");
            }

            updateCollectionState(collectionName, patchVersion, newHash.newHash());
            applySyncActionEntryUpdates(collectionName, newHash.updates());

            var ordered = deduplicateAndOrder(untrusted);
            var patchTrusted = new ArrayList<DecryptedMutation.Trusted>(ordered.size());
            for (var entry : ordered) {
                patchTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
            }
            if (!patchTrusted.isEmpty()) {
                applyMutations(collectionName, patchTrusted);
                receivedMutations = true;
            }
        }

        if (!receivedMutations) {
            store.markWebAppStateUpToDate(collectionName);
            return;
        }

        if (syncResponse.hasMore()) {
            store.markWebAppStatePending(collectionName);
        } else {
            store.markWebAppStateUpToDate(collectionName);
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
     *
     * @param uploadInfo the upload metadata captured during request building
     */
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

        // Per WA Web: persist sync action entries for SET mutations and
        // remove prior entries for acknowledged REMOVE mutations.
        for (var mutation : uploadInfo.mutations()) {
            if (mutation.operation() == SyncdOperation.SET) {
                store.putSyncActionEntry(patchType, mutation.indexMac(), new SyncActionEntryBuilder()
                        .indexMac(mutation.indexMac())
                        .valueMac(mutation.valueMac())
                        .keyId(mutation.keyId())
                        .actionIndex(mutation.actionIndex())
                        .actionValue(mutation.actionValue())
                        .actionVersion(mutation.actionVersion())
                        .actionState(SyncActionState.SUCCESS)
                        .modelType(resolveActionName(new DecryptedMutation.Trusted(
                                mutation.actionIndex(),
                                mutation.actionValue(),
                                mutation.operation(),
                                mutation.actionValue().timestamp().orElse(Instant.EPOCH),
                                mutation.actionVersion()
                        )))
                        .modelId(extractModelId(mutation.actionIndex()))
                        .build());
            } else {
                store.removeSyncActionEntry(patchType, mutation.indexMac());
            }
        }

        // Update collection version and LT-Hash
        updateCollectionState(patchType, expectedVersion, uploadInfo.newLtHash());

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
     *
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
     *
     * @param collectionName the collection being processed
     * @param mutations the decrypted mutations to validate
     * @param fatal whether to throw on duplicate (patches) or just log (snapshots)
     */
    private void validateNoDuplicateIndices(
            SyncPatchType collectionName,
            SequencedCollection<DecryptedMutation.Untrusted> mutations,
            boolean fatal
    ) {
        var setIndices = new HashSet<String>();
        var removeIndices = new HashSet<String>();
        for (var mutation : mutations) {
            var index = mutation.index();
            var isDuplicate = mutation.operation() == SyncdOperation.SET
                    ? !setIndices.add(index)
                    : !removeIndices.add(index);
            if (isDuplicate) {
                if (fatal) {
                    throw new WhatsAppWebAppStateSyncException.DuplicateIndexInPatch(collectionName);
                } else {
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
     *
     * @param collectionName the collection being processed
     * @param patches        the patches to validate
     */
    private void validateNoDuplicatePatchVersions(SyncPatchType collectionName, List<SyncdPatch> patches) {
        var seenVersions = new HashSet<Long>();
        for (var patch : patches) {
            var version = patch.version()
                    .map(v -> v.version().orElse(0L))
                    .orElse(0L);
            if (!seenVersions.add(version)) {
                throw new WhatsAppWebAppStateSyncException.DuplicatePatchVersion(collectionName, version);
            }
        }
    }

    private long getCurrentVersion(SyncPatchType patchType) {
        return store.findWebAppState(patchType).version();
    }

    private byte[] getCurrentLTHash(SyncPatchType patchType) {
        var currentHashState = whatsapp.store().findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));
        return currentHashState.hash() != null ? currentHashState.hash() : MutationLTHash.EMPTY_HASH;
    }

    private SyncdSnapshot downloadAndDecodeSnapshot(ExternalBlobReference snapshotRef) {
        // Per WA Web validateExternalBlobReference: validate required fields before download
        validateExternalBlobReference(snapshotRef);

        try {
            var downloadedData = whatsapp.store()
                    .awaitMediaConnection()
                    .download(snapshotRef);
            try (var protobufStream = ProtobufInputStream.fromStream(downloadedData)) {
                return SyncdSnapshotSpec.decode(protobufStream);
            } catch (Throwable throwable) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError("Failed to decode snapshot", throwable);
            }
        } catch (Throwable throwable) {
            if (throwable instanceof WhatsAppWebAppStateSyncException exception) {
                throw exception;
            }
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(throwable);
        }
    }

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
     *
     * @param collectionName    the collection being recovered
     * @param recoveredSnapshot the decoded recovery snapshot from the primary device
     * @return the trusted mutations extracted from the recovery data
     */
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
                var indexMac = javax.crypto.Mac.getInstance("HmacSHA256");
                indexMac.init(keys.indexKey());
                var indexMacResult = indexMac.doFinal(indexBytes);

                // Populate sync action entry store for future LT-Hash computations
                var indexString = new String(indexBytes, java.nio.charset.StandardCharsets.UTF_8);
                store.putSyncActionEntry(collectionName, indexMacResult, new SyncActionEntryBuilder()
                        .indexMac(indexMacResult)
                        .valueMac(valueMac)
                        .keyId(keyId)
                        .actionIndex(indexString)
                        .actionValue(actionValue)
                        .actionVersion(actionVersion)
                        .build());

                trusted.add(new DecryptedMutation.Trusted(
                        indexString,
                        actionValue,
                        SyncdOperation.SET,
                        timestamp,
                        actionVersion
                ));
            } catch (java.security.GeneralSecurityException e) {
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

    private SequencedCollection<SyncdMutation> getMutationsFromPatch(SyncdPatch patch) {
        var hasInline = patch.mutations() != null && !patch.mutations().isEmpty();
        var hasExternal = patch.externalMutations().isPresent();

        // A patch must not contain both inline and external mutations
        if (hasInline && hasExternal) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Patch contains both inline and external mutations", null);
        }

        if (hasExternal) {
            var downloadedData = downloadExternalMutation(patch.externalMutations().get());
            var externalMutations = decodeExternalMutation(downloadedData);
            return externalMutations.mutations() != null
                    ? Collections.unmodifiableList(externalMutations.mutations())
                    : List.of();
        }

        return hasInline
                ? Collections.unmodifiableList(patch.mutations())
                : List.of();
    }

    private InputStream downloadExternalMutation(ExternalBlobReference externalRef) {
        // Per WA Web validateExternalBlobReference: validate required fields before download
        validateExternalBlobReference(externalRef);

        try {
            return whatsapp.store()
                    .awaitMediaConnection()
                    .download(externalRef);
        }catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(throwable);
        }
    }

    private void validateExternalBlobReference(ExternalBlobReference ref) {
        if (ref.mediaDirectPath().isEmpty() || ref.mediaDirectPath().get().isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing directPath",
                    null
            );
        }
        if (ref.mediaKey().isEmpty() || ref.mediaKey().get().length == 0) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing mediaKey",
                    null
            );
        }
        if (ref.fileEncSha256().isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing fileEncSha256",
                    null
            );
        }
        if (ref.fileSha256().isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "External blob reference missing fileSha256",
                    null
            );
        }
    }

    private SyncdMutations decodeExternalMutation(InputStream downloadedData) {
        try(var protobufStream = ProtobufInputStream.fromStream(downloadedData)) {
            return SyncdMutationsSpec.decode(protobufStream);
        }catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError("Failed to decode external mutations", throwable);
        }
    }

    private SequencedCollection<DecryptedMutation.Untrusted> decryptMutations(SequencedCollection<SyncdMutation> mutations) {
        // Per WA Web: proactively scan all key IDs before decrypting to detect all missing keys at once
        byte[] firstMissingKeyId = null;
        for (var mutation : mutations) {
            var keyId = mutation.record()
                    .flatMap(SyncdRecord::keyId)
                    .flatMap(KeyId::id)
                    .orElse(null);
            if (keyId != null && whatsapp.store().findWebAppStateKeyById(keyId).isEmpty()) {
                if (firstMissingKeyId == null) {
                    firstMissingKeyId = keyId;
                }
                missingSyncKeyRequestService.requestMissingKey(keyId);
            }
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
                throw new WhatsAppWebAppStateSyncException.DecryptionFailed(e);
            }
        }

        return Collections.unmodifiableSequencedCollection(decrypted);
    }

    private void applyMutations(SyncPatchType collectionName, SequencedCollection<DecryptedMutation.Trusted> remoteMutations) {
        // Step 1: Resolve conflicts with pending local mutations
        var mutationsToApply = resolveConflicts(remoteMutations, collectionName);

        // Step 2: Group mutations by action type
        var mutationsByAction = new HashMap<String, List<DecryptedMutation.Trusted>>();

        for (var mutation : mutationsToApply) {
            var actionName = resolveActionName(mutation);
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

            try {
                var results = handler.get().applyMutationBatchResults(whatsapp, versionGated);
                for (int i = 0; i < results.size(); i++) {
                    var mutation = versionGated.get(i);
                    var result = results.get(i);
                    recordMutationState(collectionName, mutation, entry.getKey(), result);
                    if (result.isOrphan()) {
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
            } catch (WhatsAppWebAppStateSyncException exception) {
                whatsapp.handleFailure(exception);
            } catch (Throwable throwable) {
                whatsapp.handleFailure(new WhatsAppWebAppStateSyncException.UnexpectedError(throwable));
            }
        }

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
     *
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
     *
     * @param collectionName the collection these orphans belong to
     * @param orphans        the orphan entries to retry
     */
    private void retryOrphanEntries(SyncPatchType collectionName, List<OrphanMutationEntry> orphans) {
        for (var orphan : orphans) {
            var mutation = new DecryptedMutation.Trusted(
                    orphan.index(),
                    orphan.value(),
                    orphan.operation(),
                    orphan.timestamp(),
                    orphan.actionVersion()
            );
            var actionName = resolveActionName(mutation);
            var handler = handlerRegistry.findHandler(actionName);
            if (handler.isEmpty()) {
                store.addOrphanMutation(collectionName, orphan);
                continue;
            }

            try {
                var result = handler.get().applyMutationResult(whatsapp, mutation);
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
     *
     * @param mutation  the trusted mutation to persist as an orphan
     * @param modelType the action name extracted from the mutation grouping
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

    private static OrphanMutationEntry buildOrphanEntry(DecryptedMutation.Trusted mutation, String modelType) {
        return buildOrphanEntry(mutation, modelType, null);
    }

    private String resolveActionName(DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray == null || indexArray.isEmpty()) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Invalid empty action index: " + mutation.index(),
                        null
                );
            }
            var actionName = indexArray.getString(0);
            if (actionName == null || actionName.isBlank()) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Invalid action index: " + mutation.index(),
                        null
                );
            }
            return actionName;
        } catch (WhatsAppWebAppStateSyncException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Invalid action index: " + mutation.index(),
                    throwable
            );
        }
    }

    private String extractModelId(String mutationIndex) {
        try {
            var indexArray = JSON.parseArray(mutationIndex);
            if (indexArray != null && indexArray.size() >= 2) {
                return indexArray.getString(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

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

    private void retryUnsupportedMutations() {
        for (var patchType : SyncPatchType.values()) {
            retryUnsupportedMutations(patchType);
        }
    }

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
            var actionName = resolveActionName(mutation);
            var handler = handlerRegistry.findHandler(actionName);
            if (handler.isEmpty() || mutation.actionVersion() > handler.get().version()) {
                continue;
            }

            try {
                var result = handler.get().applyMutationResult(whatsapp, mutation);
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

    private SequencedCollection<DecryptedMutation.Trusted> resolveConflicts(SequencedCollection<DecryptedMutation.Trusted> remoteMutations, SyncPatchType collectionName) {
        // Create index for quick lookup of pending mutations
        var pendingByIndex = new LinkedHashMap<String, DecryptedMutation.Trusted>();
        for (var pendingMutation : whatsapp.store().findPendingMutations(collectionName)) {
            pendingByIndex.put(pendingMutation.mutation().index(), pendingMutation.mutation());
        }

        var results = new ArrayList<DecryptedMutation.Trusted>(remoteMutations.size());
        var pendingToDrop = new HashSet<String>();
        var mergedPendingToAdd = new ArrayList<SyncPendingMutation>();
        for (var remoteMutation : remoteMutations) {
            var localMutation = pendingByIndex.get(remoteMutation.index());
            if (localMutation == null) {
                // No conflict, apply remote
                results.add(remoteMutation);
                continue;
            }

            // Per WA Web: REMOVE conflicts are handled separately from SET conflicts.
            // When the remote is REMOVE and local is SET, apply the remote but do NOT
            // drop the pending local SET — it survives for the next push cycle.
            // When the local is REMOVE, apply the remote and drop the local REMOVE.
            if (remoteMutation.operation() == SyncdOperation.REMOVE
                    && localMutation.operation() != SyncdOperation.REMOVE) {
                results.add(remoteMutation);
                continue;
            }
            if (localMutation.operation() == SyncdOperation.REMOVE) {
                results.add(remoteMutation);
                pendingToDrop.add(remoteMutation.index());
                continue;
            }

            // Delegate to the handler for conflict resolution
            var actionName = resolveActionName(remoteMutation);
            var handler = handlerRegistry.findHandler(actionName).orElse(null);
            var resolution = handler != null
                    ? handler.resolveConflicts(localMutation, remoteMutation)
                    : ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);

            switch (resolution.state()) {
                case APPLY_REMOTE_DROP_LOCAL -> {
                    results.add(remoteMutation);
                    pendingToDrop.add(remoteMutation.index());
                }
                case SKIP_REMOTE -> {
                    // Keep local, skip remote
                }
                case SKIP_REMOTE_DROP_LOCAL -> {
                    pendingToDrop.add(remoteMutation.index());
                    // Per WA Web: when a merged mutation is produced, it replaces
                    // the old local pending and is applied to local state
                    if (resolution.mergedMutation() != null) {
                        results.add(resolution.mergedMutation());
                        mergedPendingToAdd.add(new SyncPendingMutation(resolution.mergedMutation(), 0));
                    }
                }
            }
        }

        var filteredResults = new ArrayList<DecryptedMutation.Trusted>(results.size());
        for (var remoteMutation : results) {
            var actionName = resolveActionName(remoteMutation);
            var handler = handlerRegistry.findHandler(actionName).orElse(null);
            if (handler != null && handler.dropMutationDueToCrossIndexConflict(remoteMutation, pendingByIndex)) {
                continue;
            }
            filteredResults.add(remoteMutation);
        }

        // Drop resolved pending mutations and add merged ones
        if (!pendingToDrop.isEmpty() || !mergedPendingToAdd.isEmpty()) {
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
                var removedEntry = store.findSyncActionEntry(patchType, indexMac);
                if (removedEntry.isPresent()) {
                    toRemove.add(removedEntry.get().valueMac());
                    updates.add(new SyncActionEntryUpdate(indexMac, null, true));
                } else {
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "REMOVE mutation has no local entry for indexMac in " + patchType,
                            null
                    );
                }
            }
        }

        return new LtHashComputation(
                MutationLTHash.subtractThenAdd(currentHash, toAdd, toRemove),
                updates
        );
    }

    private void applySyncActionEntryUpdates(SyncPatchType patchType, List<SyncActionEntryUpdate> updates) {
        for (var update : updates) {
            if (update.remove()) {
                store.removeSyncActionEntry(patchType, update.indexMac());
            } else {
                store.putSyncActionEntry(patchType, update.indexMac(), update.entry());
            }
        }
    }

    private void updateCollectionState(SyncPatchType collectionName, long version, byte[] ltHash) {
        // Per WA Web: guard against applying state updates with an older version
        var currentVersion = getCurrentVersion(collectionName);
        if (version > 0 && currentVersion > 0 && version <= currentVersion) {
            LOGGER.warning("Skipping state update for " + collectionName + ": version " + version + " is not newer than current " + currentVersion);
            return;
        }

        // Per WA Web: update both hash state and collection metadata atomically
        // to prevent inconsistent state on crash between writes.
        // The store's updateWebAppStateVersion updates both maps in one call.
        store.updateWebAppStateVersion(collectionName, version, ltHash);
    }

    private void handleSyncError(Throwable error, SyncPatchType collectionName) {
        if (collectionName == null) {
            return;
        }

        var metadata = store.findWebAppState(collectionName);
        if (error instanceof WhatsAppWebAppStateSyncException.MissingKey missingKeyEx) {
            store.markWebAppStateBlocked(collectionName);
            var keyId = missingKeyEx.keyId();

            // Per WhatsApp Web WAWebSyncdHandleMissingKeys._handleMissingKeys:
            // Request missing key from companion devices and schedule timeout
            missingSyncKeyRequestService.requestMissingKey(keyId);
            missingSyncKeyTimeoutScheduler.scheduleTimeoutCheck();
            missingSyncKeyTimeoutScheduler.startPeriodicReRequestJob();
        } else if (error instanceof WhatsAppWebAppStateSyncException syncEx && syncEx.isFatal()) {
            store.markWebAppStateErrorFatal(collectionName);

            // Per WA Web handleFatalError: sleep 5 seconds, send a fatal exception
            // notification to the primary device, and force logout
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // Per WA Web: collectionNames is the list of affected collections as strings
            sendAppStateFatalExceptionNotification(List.of(String.valueOf(collectionName)));
            whatsapp.handleFailure(syncEx);
        } else {
            var firstFailureTimestamp = metadata.lastErrorTimestamp() > 0
                    ? metadata.lastErrorTimestamp()
                    : System.currentTimeMillis();
            // Per WA Web: extract server backoff from retryable errors and pass as floor
            var serverBackoffMs = error instanceof WhatsAppWebAppStateSyncException.RetryableServerError retryable
                    ? retryable.serverBackoffMs()
                    : null;
            var result = retryScheduler.scheduleRetry(
                    collectionName,
                    firstFailureTimestamp,
                    serverBackoffMs,
                    () -> syncCollection(collectionName)
            );
            if (result) {
                store.markWebAppStateErrorRetry(collectionName);
            } else {
                store.markWebAppStateErrorFatal(collectionName);
            }
        }
    }

    /**
     * Sends a fatal exception notification to the primary device as a peer message.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdFatalExceptionNotificationApi.sendAppStateFatalExceptionNotification}:
     * constructs an {@code AppStateFatalExceptionNotification} protobuf with the
     * affected collection names and the current timestamp, wraps it in a
     * {@code ProtocolMessage} with type {@code APP_STATE_FATAL_EXCEPTION_NOTIFICATION},
     * and sends it to device 0 (primary) as a peer message via
     * {@code encryptAndSendKeyMsg}.
     *
     * @param collectionNames the collections that triggered the fatal error
     */
    private void sendAppStateFatalExceptionNotification(List<String> collectionNames) {
        try {
            var myJid = whatsapp.store().jid().orElse(null);
            if (myJid == null) {
                LOGGER.warning("Cannot send fatal exception notification: own JID not available");
                return;
            }

            var notification = new AppStateFatalExceptionNotificationBuilder()
                    .collectionNames(collectionNames)
                    .timestamp(Instant.now())
                    .build();

            var protocolMessage = new ProtocolMessageBuilder()
                    .type(ProtocolMessage.Type.APP_STATE_FATAL_EXCEPTION_NOTIFICATION)
                    .appStateFatalExceptionNotification(notification)
                    .build();

            var messageContainer = new MessageContainerBuilder()
                    .protocolMessage(protocolMessage)
                    .build();

            var primaryDeviceJid = myJid.withDevice(0);
            var messageKey = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V2, myJid))
                    .parentJid(myJid)
                    .fromMe(true)
                    .senderJid(myJid)
                    .build();
            var messageInfo = new ChatMessageInfoBuilder()
                    .key(messageKey)
                    .message(messageContainer)
                    .build();

            whatsapp.sendPeerMessage(primaryDeviceJid, messageInfo);
        } catch (Exception e) {
            LOGGER.warning("Failed to send fatal exception notification: " + e.getMessage());
        }
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
     * Starts the periodic sync job that syncs all collections every 6 hours.
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
    }

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

    public void reset() {
        stopPeriodicSyncJob();
        syncKeyRotationService.stopPeriodicRotationJob();
        retryScheduler.close();
        missingSyncKeyTimeoutScheduler.shutdown();
    }
}


