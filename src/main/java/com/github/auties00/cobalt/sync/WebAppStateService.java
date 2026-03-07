package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.signal.KeyId;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.data.*;
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
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;


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
    private final SnapshotRecoveryService snapshotRecoveryService;
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
        var pullCriticalBlock = false;
        var pullCriticalUnblockLow = false;
        var pullNonCritical = new HashSet<SyncPatchType>();

        for (var patchType : patchTypes) {
            switch (patchType) {
                case CRITICAL_BLOCK -> pullCriticalBlock = true;
                case CRITICAL_UNBLOCK_LOW -> pullCriticalUnblockLow = true;
                default -> pullNonCritical.add(patchType);
            }
        }

        if (pullCriticalBlock) {
            syncCollection(SyncPatchType.CRITICAL_BLOCK);
        }

        if (pullCriticalUnblockLow) {
            syncCollection(SyncPatchType.CRITICAL_UNBLOCK_LOW);
        }

        if (!pullNonCritical.isEmpty()) {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (var type : pullNonCritical) {
                    executor.execute(() -> syncCollection(type));
                }
            }
        }
    }

    /**
     * Retries orphan mutations across all collections.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdOrphan}: orphan mutations are retried
     * when new entities become available (e.g., after history sync brings in new
     * chats/messages, or after contact sync). This event-driven retry resolves
     * mutations that previously failed because their referenced entity did not
     * exist yet.
     */
    public void retryAllOrphanMutations() {
        for (var patchType : SyncPatchType.values()) {
            retryOrphanMutations(patchType);
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
        for (var patchType : SyncPatchType.values()) {
            var metadata = store.findWebAppState(patchType);
            switch (metadata.state()) {
                case IN_FLIGHT, PENDING, ERROR_RETRY -> store.markWebAppStateDirty(patchType);
                case BLOCKED -> {
                    missingSyncKeyTimeoutScheduler.scheduleTimeoutCheck();
                    missingSyncKeyTimeoutScheduler.startPeriodicReRequestJob();
                }
                default -> {
                    // UP_TO_DATE, DIRTY, ERROR_FATAL: no action needed
                }
            }
        }
    }

    private static final int MAX_CONFLICT_RETRIES = 5;
    private static final int MAX_CONFLICT_RETRIES_HAS_MORE = 500;
    private static final int MAX_PAGINATION_ITERATIONS = 500;

    private record SyncRoundResult(
            MutationSyncResponse response,
            SyncRequest.UploadedPatchInfo uploadInfo
    ) {
    }

    private void syncCollection(SyncPatchType patchType) {
        var remoteMutations = new ArrayList<DecryptedMutation.Trusted>();
        var conflictRetries = 0;
        var paginationIterations = 0;
        while(store.findWebAppState(patchType).state() != SyncCollectionState.UP_TO_DATE) {
            // Per WA Web: cap overall pagination iterations to prevent infinite loops
            if (++paginationIterations > MAX_PAGINATION_ITERATIONS) {
                LOGGER.warning("Pagination cap reached for collection " + patchType + " after " + MAX_PAGINATION_ITERATIONS + " iterations");
                store.markWebAppStateErrorFatal(patchType);
                break;
            }

            try {
                // Get the sync response
                var syncResult = sendSyncRequestOrThrow(patchType);

                // Process the result
                var results = handleSyncResponse(syncResult.response());
                remoteMutations.addAll(results);

                // Per WA Web _uploadSuccessful: if we pushed mutations,
                // persist sync action entries, update version/LT-Hash,
                // and clear uploaded pending mutations
                if (syncResult.uploadInfo() != null) {
                    processUploadSuccess(syncResult.uploadInfo());
                }

                // Reset conflict counter on success
                conflictRetries = 0;
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
        if(!remoteMutations.isEmpty()) {
            applyMutations(patchType, remoteMutations);
        }
    }

    private SyncRoundResult sendSyncRequestOrThrow(SyncPatchType patchType) {
        // Get pending mutations
        var pending = whatsapp.store()
                .findPendingMutations(patchType);

        // Per WA Web collectionsToSkip: skip pending mutations for unbootstrapped collections
        if (!pending.isEmpty() && getCurrentVersion(patchType) == 0) {
            LOGGER.fine("Skipping pending mutations for unbootstrapped collection " + patchType);
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
        return new SyncRoundResult(parsedResponse, syncRequest.uploadInfo());
    }

    private SequencedCollection<DecryptedMutation.Trusted> handleSyncResponse(MutationSyncResponse syncResponse) {
        try {
            var collectionName = syncResponse.collectionName();
            var allTrusted = new ArrayList<DecryptedMutation.Trusted>();

            // Phase A: Process snapshot if present
            var recoveredFromSnapshot = false;
            if (syncResponse.snapshotReference().isPresent()) {
                // Per WA Web: validate snapshot version before processing
                if (syncResponse.version() <= 0) {
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Snapshot missing required version in " + collectionName, null);
                }

                // Reset state map for full state reset
                store.clearSyncActionEntries(collectionName);

                var snapshot = downloadAndDecodeSnapshot(syncResponse.snapshotReference().get());

                // Convert snapshot records to mutations
                var snapshotMutations = getMutationsFromSnapshot(snapshot);
                if (!snapshotMutations.isEmpty()) {
                    // Decrypt snapshot mutations
                    var untrusted = decryptMutations(snapshotMutations);

                    // Validate no duplicate indices (log-only for snapshots, per WA Web)
                    validateNoDuplicateIndices(collectionName, untrusted, false);

                    // Compute LT-Hash from EMPTY_HASH for snapshot
                    var newHash = computeNewLTHash(collectionName, MutationLTHash.EMPTY_HASH, untrusted);

                    // Verify snapshot MAC, attempting recovery on failure
                    try {
                        integrityVerifier.verifySnapshotMac(collectionName, syncResponse.version(), snapshot, newHash);

                        // MAC valid: persist state and collect trusted mutations
                        updateCollectionState(collectionName, syncResponse.version(), newHash);
                        for (var entry : untrusted) {
                            allTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
                        }
                    } catch (WhatsAppWebAppStateSyncException e) {
                        // Per WA Web: only attempt recovery for fatal errors (SyncdFatalError)
                        if (!e.isFatal() || !snapshotRecoveryService.shouldAttemptRecovery(collectionName, snapshotMutations.size())) {
                            throw e;
                        }

                        var recoveryResponse = snapshotRecoveryService.requestRecovery(collectionName);
                        if (recoveryResponse == null) {
                            throw e;
                        }

                        var recoveredSnapshot = snapshotRecoveryService.decodeRecoverySnapshot(recoveryResponse);

                        // Per WA Web: validate that recovered collection matches requested collection
                        var recoveredName = recoveredSnapshot.collectionName().orElse(null);
                        if (recoveredName != null && !recoveredName.equals(collectionName.toString())) {
                            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                                    "Recovery response collection mismatch: expected " + collectionName + " but got " + recoveredName, null);
                        }

                        // Clear sync action entries computed from corrupted data
                        store.clearSyncActionEntries(collectionName);

                        // Process recovered data in-place
                        allTrusted.addAll(processRecoveredSnapshot(collectionName, recoveredSnapshot));
                        recoveredFromSnapshot = true;
                    }
                }
            }

            // Phase B: Process each patch individually
            // Per WA Web: skip patches when snapshot was recovered from primary
            if (recoveredFromSnapshot) {
                store.markWebAppStateUpToDate(collectionName);
                return Collections.unmodifiableList(allTrusted);
            }

            // Per WA Web WAWebSyncdCollectionUtils.isBootstrap:
            // If no snapshot was received and local version is 0, initialize with empty state
            if (syncResponse.snapshotReference().isEmpty() && getCurrentVersion(collectionName) == 0) {
                updateCollectionState(collectionName, 0L, MutationLTHash.EMPTY_HASH);
            }

            // Issue 4: Sort patches by version ascending
            var sortedPatches = new ArrayList<>(syncResponse.patches());
            sortedPatches.sort(Comparator.comparingLong(patch -> patch.version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L)));

            // Per WA Web validateNoDuplicatePatchVersionInCollection:
            // Ensure no two patches share the same version
            validateNoDuplicatePatchVersions(collectionName, sortedPatches);

            // Issue 8: Detect missing patch gaps
            if (!sortedPatches.isEmpty()) {
                var localVersion = getCurrentVersion(collectionName);
                var minPatchVersion = sortedPatches.getFirst().version()
                        .map(version -> version.version().orElse(0L))
                        .orElse(0L);
                if (localVersion > 0 && minPatchVersion > localVersion + 1) {
                    throw new WhatsAppWebAppStateSyncException.MissingPatches(collectionName, localVersion, minPatchVersion);
                }
            }

            for (var patch : sortedPatches) {
                // Issue 9: Handle terminal exit codes
                if (patch.exitCode().isPresent()) {
                    throw new WhatsAppWebAppStateSyncException.TerminalPatch(collectionName, patch.exitCode().get());
                }

                // Per WA Web: validate required patch protobuf fields upfront
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

                // Decrypt only this patch's mutations
                var untrusted = decryptMutations(patchMutations);

                // Validate no duplicate indices (fatal for patches, per WA Web)
                validateNoDuplicateIndices(collectionName, untrusted, true);

                // Read current LT-hash from store (updated by previous patch/snapshot)
                var currentHash = getCurrentLTHash(collectionName);

                // Per WA Web: guard against applying patches with empty LT-Hash for non-v1 patches
                long patchVersion = patch.version()
                        .map(version -> version.version().orElse(0L))
                        .orElse(0L);
                if (patchVersion > 1 && Arrays.equals(currentHash, MutationLTHash.EMPTY_HASH)) {
                    throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Empty LT-Hash for non-bootstrap patch version " + patchVersion + " in " + collectionName, null);
                }

                // Compute incremental LT-hash using all raw mutations (before deduplication)
                // Per WA Web: LT-Hash is computed from all wire mutations, dedup is only for model application
                var newHash = computeNewLTHash(collectionName, currentHash, untrusted);

                // Collect this patch's valueMacs from the raw (undeduplicated) mutations
                var patchValueMacs = untrusted.stream()
                        .map(DecryptedMutation.Untrusted::valueMac)
                        .toList();
                // Per WA Web: patch MAC mismatch is fatal, snapshot MAC mismatch marks collection
                var snapshotMacValid = integrityVerifier.verifyPatchIntegrity(collectionName, patch, newHash, patchValueMacs);
                if (!snapshotMacValid) {
                    store.markWebAppStateMacMismatch(collectionName);
                    LOGGER.warning("Patch snapshot MAC mismatch for " + collectionName + " at version " + patchVersion + ", marking mac-mismatch");
                }

                // Persist state before processing next patch
                updateCollectionState(collectionName, patchVersion, newHash);

                // Deduplicate SET/REMOVE for same index (SET wins) and order REMOVE before SET
                // Per WA Web: dedup only affects model application, not LT-Hash
                var ordered = deduplicateAndOrder(untrusted);

                // Collect trusted mutations (ordered: REMOVEs first, then SETs)
                for (var entry : ordered) {
                    allTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp(), entry.actionVersion()));
                }
            }

            if (allTrusted.isEmpty()) {
                // No updates - mark as up-to-date
                store.markWebAppStateUpToDate(collectionName);
                return List.of();
            }

            // Check if more data available
            if (syncResponse.hasMore()) {
                store.markWebAppStatePending(collectionName);
            } else {
                store.markWebAppStateUpToDate(collectionName);
            }

            return Collections.unmodifiableList(allTrusted);
        } catch (Exception e) {
            handleSyncError(e, syncResponse.collectionName());
            return List.of();
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
        }

        // Per WA Web: persist sync action entries for SET mutations,
        // REMOVE entries were already handled during buildPatchProtobuf
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
                        .build());
            }
        }

        // Update collection version and LT-Hash
        updateCollectionState(patchType, expectedVersion, uploadInfo.newLtHash());

        // Clear uploaded pending mutations
        whatsapp.store().clearPendingMutations(patchType);
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
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Duplicate patch version " + version + " in collection " + collectionName, null);
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
            }
        } catch (Throwable throwable) {
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
            var actionVersion = actionData.version().orElse(0);
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
                .map(v -> v.version().orElse(0L))
                .orElse(0L);
        var recoveryLtHash = recoveredSnapshot.collectionLthash()
                .orElse(MutationLTHash.EMPTY_HASH);
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
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(
                    new IllegalArgumentException("External blob reference missing directPath"));
        }
        if (ref.mediaKey().isEmpty() || ref.mediaKey().get().length == 0) {
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(
                    new IllegalArgumentException("External blob reference missing mediaKey"));
        }
        if (ref.fileEncSha256().isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(
                    new IllegalArgumentException("External blob reference missing fileEncSha256"));
        }
        if (ref.fileSha256().isEmpty()) {
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(
                    new IllegalArgumentException("External blob reference missing fileSha256"));
        }
    }

    private SyncdMutations decodeExternalMutation(InputStream downloadedData) {
        try(var protobufStream = ProtobufInputStream.fromStream(downloadedData)) {
            return SyncdMutationsSpec.decode(protobufStream);
        }catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.ExternalDecodeFailed(throwable);
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
            // Determine action name from action or setting
            mutation.value()
                    .action()
                    .map(SyncAction::actionName)
                    .ifPresent(actionName -> {
                        mutationsByAction
                                .computeIfAbsent(actionName, _ -> new ArrayList<>())
                                .add(mutation);
                    });
        }

        // Step 3: Apply each action group via its handler
        // Per WA Web: handlers receive the full batch via applyMutations(mutations, options)
        for (var entry : mutationsByAction.entrySet()) {
            var handler = handlerRegistry.findHandler(entry.getKey());
            if (handler.isEmpty()) {
                // Per WA Web: unsupported actions are persisted as orphans
                // so they can be retried when a handler is registered
                for (var mutation : entry.getValue()) {
                    var orphanEntry = new OrphanMutationEntryBuilder()
                            .index(mutation.index())
                            .value(mutation.value())
                            .operation(mutation.operation())
                            .timestamp(mutation.timestamp())
                            .actionVersion(mutation.actionVersion())
                            .build();
                    store.addOrphanMutation(collectionName, orphanEntry);
                }
                continue;
            }

            // Per WA Web: skip mutations with version higher than handler supports
            var maxVersion = handler.get().version();
            var versionGated = entry.getValue().stream()
                    .filter(m -> m.actionVersion() <= maxVersion)
                    .toList();
            if (versionGated.isEmpty()) {
                continue;
            }

            try {
                var results = handler.get().applyMutationBatch(whatsapp, versionGated);
                for (int i = 0; i < results.size(); i++) {
                    if (!results.get(i)) {
                        // Mutation references an entity that doesn't exist yet (orphan)
                        var orphan = versionGated.get(i);
                        var orphanEntry = new OrphanMutationEntryBuilder()
                                .index(orphan.index())
                                .value(orphan.value())
                                .operation(orphan.operation())
                                .timestamp(orphan.timestamp())
                                .actionVersion(orphan.actionVersion())
                                .build();
                        store.addOrphanMutation(collectionName, orphanEntry);
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
        for (var orphan : orphans) {
            var actionName = orphan.value()
                    .action()
                    .map(SyncAction::actionName)
                    .orElse(null);
            if (actionName == null) {
                continue;
            }

            var handler = handlerRegistry.findHandler(actionName);
            if (handler.isEmpty()) {
                // Still no handler — re-persist for future retry
                store.addOrphanMutation(collectionName, orphan);
                continue;
            }

            try {
                var mutation = new DecryptedMutation.Trusted(
                        orphan.index(),
                        orphan.value(),
                        orphan.operation(),
                        orphan.timestamp(),
                        orphan.actionVersion()
                );
                var applied = handler.get().applyMutation(whatsapp, mutation);
                if (!applied) {
                    // Still orphaned, re-add for next retry
                    store.addOrphanMutation(collectionName, orphan);
                }
            } catch (Throwable throwable) {
                LOGGER.warning("Failed to retry orphan mutation: " + throwable.getMessage());
            }
        }
    }

    private SequencedCollection<DecryptedMutation.Trusted> resolveConflicts(SequencedCollection<DecryptedMutation.Trusted> remoteMutations, SyncPatchType collectionName) {
        // Create index for quick lookup of pending mutations
        var pendingByIndex = whatsapp.store()
                .findPendingMutations(collectionName)
                .stream()
                .map(SyncPendingMutation::mutation)
                .collect(Collectors.toUnmodifiableMap(DecryptedMutation.Trusted::index, Function.identity()));

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

            // Delegate to the handler for conflict resolution
            var actionName = remoteMutation.value()
                    .action()
                    .map(SyncAction::actionName)
                    .orElse(null);
            var handler = actionName != null ? handlerRegistry.findHandler(actionName).orElse(null) : null;
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

        // Drop resolved pending mutations and add merged ones
        if (!pendingToDrop.isEmpty() || !mergedPendingToAdd.isEmpty()) {
            var remaining = whatsapp.store()
                    .findPendingMutations(collectionName)
                    .stream()
                    .filter(pm -> !pendingToDrop.contains(pm.mutation().index()))
                    .collect(Collectors.toCollection(ArrayList::new));
            remaining.addAll(mergedPendingToAdd);
            whatsapp.store().clearPendingMutations(collectionName);
            whatsapp.store().addPendingMutations(collectionName, remaining);
        }

        return Collections.unmodifiableSequencedCollection(results);
    }

    private byte[] computeNewLTHash(SyncPatchType patchType, byte[] baseHash, SequencedCollection<DecryptedMutation.Untrusted> mutations) {
        var currentHash = baseHash != null ? baseHash : MutationLTHash.EMPTY_HASH;
        var toAdd = new ArrayList<byte[]>();
        var toRemove = new ArrayList<byte[]>();

        for (var mutation : mutations) {
            var indexMac = mutation.indexMac();
            var valueMac = mutation.valueMac();

            if (mutation.operation() == SyncdOperation.SET) {
                // Check if there's an existing entry (override)
                store.findSyncActionEntry(patchType, indexMac)
                        .ifPresent(existing -> toRemove.add(existing.valueMac()));
                toAdd.add(valueMac);
                // Persist the new entry
                store.putSyncActionEntry(patchType, indexMac, new SyncActionEntryBuilder()
                        .indexMac(indexMac)
                        .valueMac(valueMac)
                        .keyId(mutation.keyId())
                        .actionIndex(mutation.index())
                        .actionValue(mutation.value())
                        .actionVersion(mutation.actionVersion())
                        .build());
            } else {
                // REMOVE: look up and remove the stored entry for this indexMac
                var removedEntry = store.removeSyncActionEntry(patchType, indexMac);
                if (removedEntry.isPresent()) {
                    toRemove.add(removedEntry.get().valueMac());
                } else {
                    // Per WA Web: fallback — use the wire valueMac directly when no local entry exists
                    LOGGER.fine("REMOVE mutation has no local entry for indexMac in " + patchType + ", using wire valueMac as fallback");
                    toRemove.add(valueMac);
                }
            }
        }

        return MutationLTHash.subtractThenAdd(currentHash, toAdd, toRemove);
    }

    private void updateCollectionState(SyncPatchType collectionName, long version, byte[] ltHash) {
        // Per WA Web: guard against applying state updates with an older version
        var currentVersion = getCurrentVersion(collectionName);
        if (version > 0 && currentVersion > 0 && version < currentVersion) {
            LOGGER.warning("Skipping state update for " + collectionName + ": version " + version + " is older than current " + currentVersion);
            return;
        }

        // Per WA Web: update both hash state and collection metadata atomically
        // to prevent inconsistent state on crash between writes
        var hashState = new SyncHashValue(collectionName);
        hashState.setHash(ltHash);
        hashState.setVersion(version);
        store.updateWebAppStateVersion(collectionName, version, ltHash);
        whatsapp.store().addWebAppHashState(hashState);
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
        } else if (error instanceof WhatsAppWebAppStateSyncException syncEx && syncEx.isFatal()) {
            store.markWebAppStateErrorFatal(collectionName);
            throw syncEx;
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
                    metadata.retryCount(),
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

    public void reset() {
        retryScheduler.close();
        missingSyncKeyTimeoutScheduler.shutdown();
    }
}

