package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.signal.KeyIdBuilder;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.data.*;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.sync.crypto.EncryptedMutation;
import com.github.auties00.cobalt.sync.crypto.MutationIntegrityVerifier;
import com.github.auties00.cobalt.sync.crypto.MutationKeys;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

/**
 * Builds outgoing sync request nodes with encrypted mutations.
 *
 * <p>Per WhatsApp Web behavior, outgoing patches are serialized as
 * {@code SyncdPatch} protobuf blobs containing encrypted mutations,
 * computed snapshotMac and patchMac, key ID, and device metadata.
 */
public final class MutationRequestBuilder {
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new mutation request builder.
     *
     * @param whatsapp the WhatsApp client instance
     */
    public MutationRequestBuilder(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Builds a sync request node for the specified collection.
     *
     * @param patchType the collection type to sync
     * @param patches   the pending mutations to include
     * @return the IQ request node builder
     */
    public NodeBuilder buildSyncRequest(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // Get current hash state for this collection
        var hashState = whatsapp.store()
                .findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));

        // Build collection node
        var collectionBuilder = new NodeBuilder()
                .description("collection")
                .attribute("name", patchType.toString())
                .attribute("return_snapshot", hashState.version() == 0);

        // Only include version if we've synced before
        if (hashState.version() > 0) {
            collectionBuilder.attribute("version", hashState.version());
        }

        // Build patch node if we have mutations
        if (!patches.isEmpty()) {
            var patchBytes = buildPatchProtobuf(patchType, patches, hashState);
            var patchNode = new NodeBuilder()
                    .description("patch")
                    .content(patchBytes)
                    .build();
            collectionBuilder.content(patchNode);
        }

        var collectionNode = collectionBuilder.build();

        // Build sync node
        var syncNode = new NodeBuilder()
                .description("sync")
                .content(collectionNode)
                .build();

        // Build IQ request
        return new NodeBuilder()
                .description("iq")
                .attribute("type", "set")
                .attribute("xmlns", "w:sync:app:state")
                .content(syncNode);
    }

    /**
     * Builds a serialized {@code SyncdPatch} protobuf containing encrypted
     * mutations with computed snapshotMac and patchMac.
     */
    private byte[] buildPatchProtobuf(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches, SyncHashValue hashState) {
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

            // Step 3: Compute MACs
            var newVersion = hashState.version() + 1;
            var snapshotMac = MutationIntegrityVerifier.computeSnapshotMac(
                    derivedKeys.snapshotMacKey(), newLtHash, newVersion, patchType);
            var valueMacs = encryptedMutations.stream()
                    .map(EncryptedMutation::valueMac)
                    .toList();
            var patchMac = MutationIntegrityVerifier.computePatchMac(
                    derivedKeys.patchMacKey(), snapshotMac, valueMacs, newVersion, patchType);

            // Step 4: Build SyncdPatch protobuf
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

            var syncdPatch = new SyncdPatchBuilder()
                    .version(new SyncdVersionBuilder().version(newVersion).build())
                    .mutations(syncdMutations)
                    .snapshotMac(snapshotMac)
                    .patchMac(patchMac)
                    .keyId(new KeyIdBuilder().id(latestKeyId).build())
                    .build();

            return SyncdPatchSpec.encode(syncdPatch);
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
}
