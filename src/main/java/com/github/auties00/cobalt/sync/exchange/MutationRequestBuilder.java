package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.sync.crypto.EncryptedMutation;
import com.github.auties00.cobalt.sync.crypto.MutationKeys;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

public final class MutationRequestBuilder {
    private final WhatsAppClient whatsapp;

    public MutationRequestBuilder(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    public NodeBuilder buildSyncRequest(SyncPatchType patchType, SequencedCollection<SyncPendingMutation> patches) {
        // Get current hash state for this collection
        var hashState = whatsapp.store()
                .findWebAppHashStateByName(patchType)
                .orElseGet(() -> new SyncHashValue(patchType));

        // Encrypt mutations if we have any to push
        var mutationNodes = encryptMutations(patches);

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
        if (!mutationNodes.isEmpty()) {
            var mutationsNode = new NodeBuilder()
                    .description("mutations")
                    .content(mutationNodes.toArray(Node[]::new))
                    .build();

            var patchNode = new NodeBuilder()
                    .description("patch")
                    .content(mutationsNode)
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


    private SequencedCollection<Node> encryptMutations(SequencedCollection<SyncPendingMutation> patches) {
        if(patches.isEmpty()) {
            return List.of();
        }

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
            var mutationNodes = new ArrayList<Node>(patches.size());

            for (var patch : patches) {
                var encrypted = EncryptedMutation.of(patch, derivedKeys, latestKeyId);

                var index = new NodeBuilder()
                        .description("index")
                        .content(encrypted.indexMac())
                        .build();
                var value = new NodeBuilder()
                        .description("value")
                        .content(encrypted.encryptedValue())
                        .build();
                var keyId = new NodeBuilder()
                        .description("keyId")
                        .content(encrypted.keyId())
                        .build();
                var record = new NodeBuilder()
                        .description("record")
                        .content(index, value, keyId)
                        .build();
                mutationNodes.add(record);
            }

            return mutationNodes;
        }catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt mutations", exception);
        }
    }
}
