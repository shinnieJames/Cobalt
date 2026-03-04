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
import com.github.auties00.cobalt.sync.key.MissingSyncKeyRequestService;
import com.github.auties00.cobalt.sync.key.MissingSyncKeyTimeoutScheduler;
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

    /**
     * Creates a new WebAppStateManager instance.
     *
     * @param whatsapp the Whatsapp instance to use for store access and node sending
     * @param abPropsService the AB props service for configuration values
     */
    public WebAppStateService(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.store = whatsapp.store();
        this.requestBuilder = new MutationRequestBuilder(whatsapp);
        this.responseParser = new MutationResponseParser();
        this.handlerRegistry = new WebAppStateHandlerRegistry();
        this.integrityVerifier = new MutationIntegrityVerifier(store);
        this.retryScheduler = new WebAppStateBackoffScheduler();
        this.missingSyncKeyTimeoutScheduler = new MissingSyncKeyTimeoutScheduler(whatsapp, abPropsService);
        this.missingSyncKeyRequestService = new MissingSyncKeyRequestService(whatsapp);
    }

    /**
     * Pushes local patches to the server.
     * Called from Whatsapp.pushWebAppState().
     *
     * @param patchType the collection type to sync
     * @param patches the patches to push
     */
    public void pushPatches(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
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

    private static final int MAX_CONFLICT_RETRIES = 5;
    private static final int MAX_CONFLICT_RETRIES_HAS_MORE = 500;

    private void syncCollection(SyncPatchType patchType) {
        var remoteMutations = new ArrayList<DecryptedMutation.Trusted>();
        var conflictRetries = 0;
        while(store.findWebAppState(patchType).state() != SyncCollectionState.UP_TO_DATE) {
            try {
                // Get the sync response
                var syncResponse = sendSyncRequestOrThrow(patchType);

                // Process the result
                var results = handleSyncResponse(syncResponse);
                remoteMutations.addAll(results);

                // Reset conflict counter on success
                conflictRetries = 0;
            } catch (WhatsAppWebAppStateSyncException.Conflict conflict) {
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

    private MutationSyncResponse sendSyncRequestOrThrow(SyncPatchType patchType) {
        // Get pending mutations
        var pending = whatsapp.store()
                .findPendingMutations(patchType);

        // Build request
        var request = requestBuilder.buildSyncRequest(patchType, pending);

        // Mark as in-flight
        store.markWebAppStateInFlight(patchType);

        // Send a request and get a response (synchronous)
        var response = whatsapp.sendNode(request);

        // Handle response
        return responseParser.parseSyncResponse(response);
    }

    private SequencedCollection<DecryptedMutation.Trusted> handleSyncResponse(MutationSyncResponse syncResponse) {
        try {
            var collectionName = syncResponse.collectionName();
            var allTrusted = new ArrayList<DecryptedMutation.Trusted>();

            // Phase A: Process snapshot if present
            if (syncResponse.snapshotReference().isPresent()) {
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

                    // Verify snapshot MAC
                    integrityVerifier.verifySnapshotMac(collectionName, syncResponse.version(), snapshot, newHash);

                    // Persist state before processing patches
                    updateCollectionState(collectionName, syncResponse.version(), newHash);

                    // Collect trusted mutations
                    for (var entry : untrusted) {
                        allTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp()));
                    }
                }
            }

            // Phase B: Process each patch individually
            // Issue 4: Sort patches by version ascending
            var sortedPatches = new ArrayList<>(syncResponse.patches());
            sortedPatches.sort(Comparator.comparingLong(patch -> patch.version()
                    .map(version -> version.version().orElse(0L))
                    .orElse(0L)));

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

                // Compute incremental LT-hash using all raw mutations (before deduplication)
                // Per WA Web: LT-Hash is computed from all wire mutations, dedup is only for model application
                var newHash = computeNewLTHash(collectionName, currentHash, untrusted);

                // Collect this patch's valueMacs from the raw (undeduplicated) mutations
                var patchValueMacs = untrusted.stream()
                        .map(DecryptedMutation.Untrusted::valueMac)
                        .toList();

                // Verify this patch's integrity using wire snapshotMac
                long patchVersion = patch.version()
                        .map(version -> version.version().orElse(0L))
                        .orElse(0L);
                integrityVerifier.verifyPatchIntegrity(collectionName, patch, newHash, patchValueMacs);

                // Persist state before processing next patch
                updateCollectionState(collectionName, patchVersion, newHash);

                // Deduplicate SET/REMOVE for same index (SET wins) and order REMOVE before SET
                // Per WA Web: dedup only affects model application, not LT-Hash
                var ordered = deduplicateAndOrder(untrusted);

                // Collect trusted mutations (ordered: REMOVEs first, then SETs)
                for (var entry : ordered) {
                    allTrusted.add(new DecryptedMutation.Trusted(entry.index(), entry.value(), entry.operation(), entry.timestamp()));
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

    private long getCurrentVersion(SyncPatchType patchType) {
        return store.findWebAppState(patchType).version();
    }

    private byte[] getCurrentLTHash(SyncPatchType patchType) {
        var currentHashState = whatsapp.store().findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));
        return currentHashState.hash() != null ? currentHashState.hash() : MutationLTHash.EMPTY_HASH;
    }

    private SyncdSnapshot downloadAndDecodeSnapshot(ExternalBlobReference snapshotRef) {
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

    private SequencedCollection<SyncdMutation> getMutationsFromPatch(SyncdPatch patch) {
        var result = new ArrayList<SyncdMutation>();
        if (patch.mutations() != null) {
            result.addAll(patch.mutations());
        }

        if (patch.externalMutations().isPresent()) {
            var downloadedData = downloadExternalMutation(patch.externalMutations().get());
            var externalMutations = decodeExternalMutation(downloadedData);
            if (externalMutations.mutations() != null) {
                result.addAll(externalMutations.mutations());
            }
        }

        return Collections.unmodifiableList(result);
    }

    private InputStream downloadExternalMutation(ExternalBlobReference externalRef) {
        try {
            return whatsapp.store()
                    .awaitMediaConnection()
                    .download(externalRef);
        }catch (Throwable throwable) {
            throw new WhatsAppWebAppStateSyncException.ExternalDownloadFailed(throwable);
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
        var decrypted = new ArrayList<DecryptedMutation.Untrusted>(mutations.size());

        for (var mutation : mutations) {
            var record = mutation.record();
            if (record.isEmpty()) {
                continue;
            }

            var operation = mutation.operation();
            if(operation.isEmpty()) {
                continue;
            }

            var recordValueBlob = record.get()
                    .value()
                    .flatMap(SyncdValue::blob);
            if(recordValueBlob.isEmpty()) {
                continue;
            }

            var recordIndexBlob = record.get()
                    .index()
                    .flatMap(SyncdIndex::blob);
            if(recordIndexBlob.isEmpty()) {
                continue;
            }

            var keyId = record.get()
                    .keyId()
                    .flatMap(KeyId::id);
            if (keyId.isEmpty()) {
                continue;
            }

            // Get encryption key
            var syncKey = whatsapp.store()
                    .findWebAppStateKeyById(keyId.get())
                    .flatMap(AppStateSyncKey::keyData)
                    .flatMap(AppStateSyncKeyData::keyData)
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.MissingKey(keyId.get()));

            // Derive keys and decrypt
            try (var keys = MutationKeys.ofSyncKey(syncKey)) {
                var decryptedMutation = DecryptedMutation.Untrusted.of(
                        recordValueBlob.get(),
                        recordIndexBlob.get(),
                        keys,
                        operation.get(),
                        keyId.get()
                );
                decrypted.add(decryptedMutation);
            }catch (Exception e) {
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
        for (var entry : mutationsByAction.entrySet()) {
            var handler = handlerRegistry.findHandler(entry.getKey());
            if (handler.isEmpty()) {
                continue;
            }

            var mutations = entry.getValue();
            for (var mutation : mutations) {
                try {
                    handler.get().applyMutation(whatsapp, mutation);
                } catch (WhatsAppWebAppStateSyncException exception) {
                    whatsapp.handleFailure(exception);
                } catch (Throwable throwable) {
                    whatsapp.handleFailure(new WhatsAppWebAppStateSyncException.UnexpectedError(throwable));
                }
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
                    : ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL;

            switch (resolution) {
                case APPLY_REMOTE_DROP_LOCAL -> {
                    results.add(remoteMutation);
                    pendingToDrop.add(remoteMutation.index());
                }
                case SKIP_REMOTE -> {
                    // Keep local, skip remote
                }
                case SKIP_REMOTE_DROP_LOCAL -> pendingToDrop.add(remoteMutation.index());
            }
        }

        // Drop resolved pending mutations
        if (!pendingToDrop.isEmpty()) {
            var remaining = whatsapp.store()
                    .findPendingMutations(collectionName)
                    .stream()
                    .filter(pm -> !pendingToDrop.contains(pm.mutation().index()))
                    .toList();
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
                        .build());
            } else {
                // REMOVE: look up and remove the stored entry for this indexMac
                store.removeSyncActionEntry(patchType, indexMac)
                        .ifPresent(existing -> toRemove.add(existing.valueMac()));
            }
        }

        return MutationLTHash.subtractThenAdd(currentHash, toAdd, toRemove);
    }

    private void updateCollectionState(SyncPatchType collectionName, long version, byte[] ltHash) {
        var hashState = new SyncHashValue(collectionName);
        hashState.setHash(ltHash);
        hashState.setVersion(version);
        whatsapp.store()
                .addWebAppHashState(hashState);
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
        } else if (error instanceof WhatsAppWebAppStateSyncException syncEx && syncEx.isFatal()) {
            store.markWebAppStateErrorFatal(collectionName);
            throw syncEx;
        } else {
            var firstFailureTimestamp = metadata.lastErrorTimestamp() > 0
                    ? metadata.lastErrorTimestamp()
                    : System.currentTimeMillis();
            var result = retryScheduler.scheduleRetry(
                    collectionName,
                    firstFailureTimestamp,
                    metadata.retryCount(),
                    () -> syncCollection(collectionName)
            );
            if (result) {
                store.markWebAppStateErrorRetry(collectionName);
            } else {
                store.markWebAppStateErrorFatal(collectionName);
            }
        }
    }

    public void reset() {
        retryScheduler.close();
        missingSyncKeyTimeoutScheduler.shutdown();
    }
}

