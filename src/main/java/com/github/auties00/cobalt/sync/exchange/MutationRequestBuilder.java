package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.signal.KeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.data.*;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.crypto.EncryptedMutation;
import com.github.auties00.cobalt.sync.crypto.MutationIntegrityVerifier;
import com.github.auties00.cobalt.sync.crypto.MutationKeys;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Builds outgoing sync request nodes with encrypted mutations.
 *
 * <p>Per WhatsApp Web behavior, outgoing patches are serialized as
 * {@code SyncdPatch} protobuf blobs containing encrypted mutations,
 * computed snapshotMac and patchMac, key ID, and device metadata.
 */
public final class MutationRequestBuilder {
    private static final Logger LOGGER = Logger.getLogger(MutationRequestBuilder.class.getName());

    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;

    /**
     * Constructs a new mutation request builder.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param abPropsService the AB props service for threshold configuration
     */
    public MutationRequestBuilder(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
    }

    /**
     * Builds a sync request node for the specified collection.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdServerSync}: when the request
     * includes outgoing mutations, the returned {@link SyncRequest} carries
     * upload metadata so the caller can run the {@code _uploadSuccessful}
     * path after a successful server response.
     *
     * @param patchType the collection type to sync
     * @param patches   the pending mutations to include
     * @return the sync request containing the IQ node and optional upload info
     */
    public SyncRequest buildSyncRequest(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // Get current hash state for this collection
        var hashState = whatsapp.store()
                .findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));

        // Build collection node
        var collectionBuilder = new NodeBuilder()
                .description("collection")
                .attribute("name", patchType.toString())
                .attribute("version", hashState.version())
                .attribute("return_snapshot", hashState.version() == 0 ? "true" : "false");

        // Compact: deduplicate by index, keeping the last mutation for each index
        var compacted = compactPatch(patches);

        // Build patch node if we have mutations
        SyncRequest.UploadedPatchInfo uploadInfo = null;
        if (!compacted.isEmpty()) {
            var buildResult = buildPatchProtobuf(patchType, compacted, hashState);
            var patchNode = new NodeBuilder()
                    .description("patch")
                    .content(buildResult.bytes())
                    .build();
            collectionBuilder.content(patchNode);
            uploadInfo = buildResult.uploadInfo();
        }

        var collectionNode = collectionBuilder.build();

        // Build sync node
        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNode)
                .build();

        // Build IQ request
        var node = new NodeBuilder()
                .description("iq")
                .attribute("type", "set")
                .attribute("xmlns", "w:sync:app:state")
                .content(syncNode);

        return new SyncRequest(node, uploadInfo);
    }

    private record PatchBuildResult(byte[] bytes, SyncRequest.UploadedPatchInfo uploadInfo) {
    }

    /**
     * Builds a serialized {@code SyncdPatch} protobuf containing encrypted
     * mutations with computed snapshotMac and patchMac.
     *
     * <p>Also captures upload metadata for post-response processing via
     * {@link SyncRequest.UploadedPatchInfo}.
     */
    private PatchBuildResult buildPatchProtobuf(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches, SyncHashValue hashState) {
        var keys = whatsapp.store().appStateKeys();
        if (keys.isEmpty()) {
            throw new IllegalStateException("No app state sync keys available");
        }

        var latestKey = keys.getLast();
        var latestKeyId = latestKey.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .orElseThrow(() -> new IllegalArgumentException("No app state sync key found"));
        var latestKeyData = latestKey.keyData()
                .flatMap(AppStateSyncKeyData::keyData)
                .orElseThrow(() -> new IllegalStateException("Latest app state sync key data has no ID"));

        try (var derivedKeys = MutationKeys.ofSyncKey(latestKeyData)) {
            // Step 1: Encrypt all mutations
            var encryptedMutations = encryptMutations(patchType, patches, derivedKeys, latestKeyId);
            var userMutationCount = encryptedMutations.size();

            // Step 1b: Key rotation — re-encrypt old-key entries with the latest key
            var keyRotationSources = new ArrayList<DecryptedMutation.Trusted>();
            var keyRotationMutations = buildKeyRotationMutations(patchType, patches, derivedKeys, latestKeyId, keyRotationSources);
            encryptedMutations.addAll(keyRotationMutations);

            // Step 2: Compute new LT-Hash from encrypted mutations
            var currentLtHash = hashState.hash() != null ? hashState.hash() : MutationLTHash.EMPTY_HASH;
            var toAdd = new ArrayList<byte[]>(encryptedMutations.size());
            var toRemove = new ArrayList<byte[]>();
            for (var encrypted : encryptedMutations) {
                if (encrypted.operation() == SyncdOperation.SET) {
                    // Check for existing entry to subtract
                    whatsapp.store()
                            .findSyncActionEntry(patchType, encrypted.indexMac())
                            .ifPresent(existing -> toRemove.add(existing.valueMac()));
                    toAdd.add(encrypted.valueMac());
                } else {
                    whatsapp.store()
                            .removeSyncActionEntry(patchType, encrypted.indexMac())
                            .ifPresent(existing -> toRemove.add(existing.valueMac()));
                }
            }
            var newLtHash = MutationLTHash.subtractThenAdd(currentLtHash, toAdd, toRemove);

            // Build upload metadata by pairing encrypted mutations with source data
            var patchesIterator = patches.iterator();
            var keyRotationIterator = keyRotationSources.iterator();
            var uploadedMutations = new ArrayList<SyncRequest.UploadedMutationInfo>(encryptedMutations.size());
            var index = 0;
            for (var encrypted : encryptedMutations) {
                var source = index < userMutationCount
                        ? patchesIterator.next().mutation()
                        : keyRotationIterator.next();
                uploadedMutations.add(new SyncRequest.UploadedMutationInfo(
                        encrypted.indexMac(),
                        encrypted.valueMac(),
                        encrypted.keyId(),
                        encrypted.operation(),
                        source.index(),
                        source.value(),
                        source.actionVersion()
                ));
                index++;
            }

            // Step 3: Compute MACs
            var newVersion = hashState.version() + 1;
            var snapshotMac = MutationIntegrityVerifier.computeSnapshotMac(
                    derivedKeys.snapshotMacKey(), newLtHash, newVersion, patchType);
            var valueMacs = encryptedMutations.stream()
                    .map(EncryptedMutation::valueMac)
                    .toList();
            var patchMac = MutationIntegrityVerifier.computePatchMac(
                    derivedKeys.patchMacKey(), snapshotMac, valueMacs, newVersion, patchType);

            // Step 4: Build SyncdMutation list
            var syncdMutations = new ArrayList<SyncdMutation>(encryptedMutations.size());
            for (var encrypted : encryptedMutations) {
                var record = new SyncdRecordBuilder()
                        .index(new SyncdIndexBuilder().blob(encrypted.indexMac()).build())
                        .value(new SyncdValueBuilder().blob(encrypted.encryptedValue()).build())
                        .keyId(new KeyIdBuilder().id(encrypted.keyId()).build())
                        .build();
                syncdMutations.add(new SyncdMutationBuilder()
                        .operation(encrypted.operation())
                        .record(record)
                        .build());
            }

            // Per WA Web: include deviceIndex (companion device ID) and clientDebugData
            var deviceIndex = whatsapp.store().jid()
                    .map(jid -> jid.device())
                    .orElse(0);
            var debugData = new PatchDebugDataBuilder()
                    .isSenderPrimary(false)
                    .senderPlatform(PatchDebugData.Platform.WEB)
                    .build();
            var clientDebugData = PatchDebugDataSpec.encode(debugData);

            // Capture upload metadata for post-response processing
            var uploadInfo = new SyncRequest.UploadedPatchInfo(
                    patchType, newLtHash, newVersion, List.copyOf(uploadedMutations));

            // Step 5: Check if mutations should be uploaded externally
            var maxInlineCount = Math.min(2000, Math.max(100, abPropsService.getInt(ABProp.SYNCD_INLINE_MUTATIONS_MAX_COUNT)));
            var externalRef = (ExternalBlobReference) null;
            if (syncdMutations.size() > maxInlineCount) {
                externalRef = uploadExternalMutations(syncdMutations);
            } else {
                // Check encoded size against threshold
                var inlinePatch = new SyncdPatchBuilder()
                        .version(new SyncdVersionBuilder().version(newVersion).build())
                        .mutations(syncdMutations)
                        .snapshotMac(snapshotMac)
                        .patchMac(patchMac)
                        .keyId(new KeyIdBuilder().id(latestKeyId).build())
                        .deviceIndex(deviceIndex)
                        .clientDebugData(clientDebugData)
                        .build();
                var inlineBytes = SyncdPatchSpec.encode(inlinePatch);
                var maxSizeBytes = Math.min(100, Math.max(10, abPropsService.getInt(ABProp.SYNCD_PATCH_PROTOBUF_MAX_SIZE))) * 1000L;
                if (inlineBytes.length > maxSizeBytes) {
                    externalRef = uploadExternalMutations(syncdMutations);
                } else {
                    return new PatchBuildResult(inlineBytes, uploadInfo);
                }
            }

            // Build patch with external reference instead of inline mutations
            var syncdPatch = new SyncdPatchBuilder()
                    .version(new SyncdVersionBuilder().version(newVersion).build())
                    .externalMutations(externalRef)
                    .snapshotMac(snapshotMac)
                    .patchMac(patchMac)
                    .keyId(new KeyIdBuilder().id(latestKeyId).build())
                    .deviceIndex(deviceIndex)
                    .clientDebugData(clientDebugData)
                    .build();

            return new PatchBuildResult(SyncdPatchSpec.encode(syncdPatch), uploadInfo);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to build outgoing patch", exception);
        }
    }

    /**
     * Encrypts pending mutations into {@code EncryptedMutation} objects.
     */
    private SequencedCollection<EncryptedMutation> encryptMutations(
            SyncPatchType patchType,
            SequencedCollection<SyncPendingMutation> patches,
            MutationKeys derivedKeys,
            byte[] latestKeyId
    ) throws GeneralSecurityException {
        var result = new ArrayList<EncryptedMutation>(patches.size());
        for (var patch : patches) {
            var mutation = patch.mutation();
            EncryptedMutation encrypted;

            if (mutation.operation() == SyncdOperation.REMOVE) {
                // For REMOVE: look up the original SET mutation's key from store
                var indexBytes = mutation.index().getBytes(StandardCharsets.UTF_8);
                var indexMac = Mac.getInstance("HmacSHA256");
                indexMac.init(derivedKeys.indexKey());
                var indexMacResult = indexMac.doFinal(indexBytes);

                var originalEntry = whatsapp.store()
                        .findSyncActionEntry(patchType, indexMacResult)
                        .orElseThrow(() -> new IllegalStateException(
                                "Cannot find original key for REMOVE operation on index: " + mutation.index()
                        ));

                var originalKeyData = whatsapp.store()
                        .findWebAppStateKeyById(originalEntry.keyId())
                        .flatMap(AppStateSyncKey::keyData)
                        .flatMap(AppStateSyncKeyData::keyData)
                        .orElseThrow(() -> new IllegalStateException(
                                "Original sync key not found for REMOVE operation"
                        ));

                try (var originalDerivedKeys = MutationKeys.ofSyncKey(originalKeyData)) {
                    encrypted = EncryptedMutation.of(patch, originalDerivedKeys, originalEntry.keyId());
                }
            } else {
                encrypted = EncryptedMutation.of(patch, derivedKeys, latestKeyId);
            }

            result.add(encrypted);
        }
        return result;
    }

    /**
     * Builds additional SET and REMOVE mutations for key rotation.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRequestBuilderBuild}: when building
     * an outgoing patch, entries encrypted with keys older than the current key
     * are included as additional SET mutations to gradually rotate all entries
     * to the latest key. The number of additional SET mutations per patch is
     * limited by the {@code syncd_additional_mutations_count} AB prop.
     *
     * <p>Per WhatsApp Web {@code _generateMutationsToUpload}: after collecting
     * SET mutations (both user and rotation), REMOVE mutations are generated for
     * every stored entry whose index appears as a SET in the batch and whose key
     * is not the latest. This tells the server to explicitly remove the old-key
     * entry before the new-key entry is applied.
     *
     * @param patchType   the collection type
     * @param patches     the core mutations being sent (to exclude from rotation)
     * @param derivedKeys the derived keys from the latest sync key
     * @param latestKeyId the latest app state sync key ID
     * @param keyRotationSourcesOut output list populated with source data for upload metadata
     * @return the additional encrypted mutations for key rotation (SETs + REMOVEs)
     * @throws GeneralSecurityException if encryption fails
     */
    private List<EncryptedMutation> buildKeyRotationMutations(
            SyncPatchType patchType,
            SequencedCollection<SyncPendingMutation> patches,
            MutationKeys derivedKeys,
            byte[] latestKeyId,
            List<DecryptedMutation.Trusted> keyRotationSourcesOut
    ) throws GeneralSecurityException {
        // Collect indices already in the current batch to avoid duplicates
        var batchIndices = new HashSet<String>();
        for (var patch : patches) {
            batchIndices.add(patch.mutation().index());
        }

        // Find entries with old keys that are not in the current batch
        var allEntries = whatsapp.store().getSyncActionEntries(patchType);
        var maxAdditional = Math.min(5, Math.max(1, abPropsService.getInt(ABProp.SYNCD_ADDITIONAL_MUTATIONS_COUNT)));
        var result = new ArrayList<EncryptedMutation>();

        for (var entry : allEntries) {
            if (result.size() >= maxAdditional) {
                break;
            }

            // Skip entries already using the latest key
            if (Arrays.equals(entry.keyId(), latestKeyId)) {
                continue;
            }

            // Skip entries without stored plaintext (e.g., from older sessions)
            if (entry.actionIndex() == null || entry.actionValue() == null) {
                continue;
            }

            // Skip entries whose index is already in the current batch
            if (batchIndices.contains(entry.actionIndex())) {
                continue;
            }

            // Re-encrypt with the latest key as a SET mutation
            var trusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(),
                    entry.actionValue(),
                    SyncdOperation.SET,
                    Instant.now(),
                    entry.actionVersion()
            );
            keyRotationSourcesOut.add(trusted);
            var pending = new SyncPendingMutation(trusted, 0);
            result.add(EncryptedMutation.of(pending, derivedKeys, latestKeyId));
        }

        // Per WA Web: generate REMOVE mutations for old-key entries whose index
        // is being SET (either by user mutations or rotation SETs above).
        // Collect all SET indices from user mutations + rotation SETs.
        var allSetIndices = new HashSet<>(batchIndices);
        for (var rotationSource : keyRotationSourcesOut) {
            allSetIndices.add(rotationSource.index());
        }

        for (var entry : allEntries) {
            // Only process entries whose index appears as a SET
            if (entry.actionIndex() == null || !allSetIndices.contains(entry.actionIndex())) {
                continue;
            }

            // Only generate REMOVE for entries with an old key
            if (Arrays.equals(entry.keyId(), latestKeyId)) {
                continue;
            }

            // Skip entries without stored plaintext
            if (entry.actionValue() == null) {
                continue;
            }

            var removeTrusted = new DecryptedMutation.Trusted(
                    entry.actionIndex(),
                    entry.actionValue(),
                    SyncdOperation.REMOVE,
                    Instant.now(),
                    entry.actionVersion()
            );
            keyRotationSourcesOut.add(removeTrusted);
            var removePending = new SyncPendingMutation(removeTrusted, 0);
            result.add(EncryptedMutation.of(removePending, derivedKeys, latestKeyId));
        }

        return result;
    }

    /**
     * Builds a sync request node that batches multiple collections into a
     * single IQ stanza.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRequestBuilder}: all dirty collections
     * are batched into a single IQ with one {@code <sync>} node containing
     * multiple {@code <collection>} children.
     *
     * @param collectionPatches map of collection types to their pending mutations
     * @return the IQ request node builder
     */
    public NodeBuilder buildBatchedSyncRequest(Map<SyncPatchType, SequencedCollection<SyncPendingMutation>> collectionPatches) {
        var collectionNodes = new ArrayList<com.github.auties00.cobalt.node.Node>();
        for (var entry : collectionPatches.entrySet()) {
            var patchType = entry.getKey();
            var patches = entry.getValue();

            var hashState = whatsapp.store()
                    .findWebAppHashStateByName(patchType)
                    .orElseGet(() -> new SyncHashValue(patchType));

            var collectionBuilder = new NodeBuilder()
                    .description("collection")
                    .attribute("name", patchType.toString())
                    .attribute("version", hashState.version())
                    .attribute("return_snapshot", hashState.version() == 0 ? "true" : "false");

            var compacted = compactPatch(patches);
            if (!compacted.isEmpty()) {
                var buildResult = buildPatchProtobuf(patchType, compacted, hashState);
                var patchNode = new NodeBuilder()
                        .description("patch")
                        .content(buildResult.bytes())
                        .build();
                collectionBuilder.content(patchNode);
            }

            collectionNodes.add(collectionBuilder.build());
        }

        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNodes)
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("type", "set")
                .attribute("xmlns", "w:sync:app:state")
                .content(syncNode);
    }

    /**
     * Uploads a list of mutations as an external blob to the media CDN.
     *
     * <p>Per WhatsApp Web behavior, when the number of mutations or the encoded
     * protobuf size exceeds the configured inline thresholds, mutations are
     * serialized as a {@code SyncdMutations} protobuf, uploaded to the CDN via
     * the media connection, and referenced by an {@link ExternalBlobReference}
     * in the outgoing {@code SyncdPatch}.
     *
     * @param mutations the list of mutations to upload externally
     * @return the populated {@code ExternalBlobReference} with CDN metadata
     */
    private ExternalBlobReference uploadExternalMutations(List<SyncdMutation> mutations) {
        var syncdMutations = new SyncdMutationsBuilder()
                .mutations(mutations)
                .build();
        var encoded = SyncdMutationsSpec.encode(syncdMutations);
        var externalRef = new ExternalBlobReferenceBuilder().build();
        try {
            var uploaded = whatsapp.store()
                    .awaitMediaConnection()
                    .upload(externalRef, new ByteArrayInputStream(encoded));
            if (!uploaded) {
                throw new IllegalStateException("Failed to upload external mutations to CDN");
            }
            return externalRef;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while uploading external mutations", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to upload external mutations", throwable);
        }
    }

    /**
     * Deduplicates pending mutations by index, keeping only the last mutation
     * for each index.
     *
     * <p>Per WhatsApp Web {@code compactPatch}: when multiple mutations target
     * the same index (e.g., rapidly toggling a setting), only the final state
     * is sent to the server.
     *
     * @param patches the pending mutations to compact
     * @return the compacted mutations
     */
    private SequencedCollection<SyncPendingMutation> compactPatch(SequencedCollection<SyncPendingMutation> patches) {
        var byIndex = new LinkedHashMap<String, SyncPendingMutation>();
        for (var patch : patches) {
            byIndex.put(patch.mutation().index(), patch);
        }
        return List.copyOf(byIndex.values());
    }
}
