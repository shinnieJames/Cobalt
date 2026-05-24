package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.sync.SyncActionValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.List;

/**
 * Carrier for the output of {@link MutationRequestBuilder#buildSyncRequest}: the IQ
 * {@link NodeBuilder} ready to be id-tagged and sent, paired with the upload metadata
 * needed by the post-response success path.
 *
 * @apiNote
 * The {@link #node} field is intentionally a still-mutable {@link NodeBuilder} so the
 * caller (typically {@link com.github.auties00.cobalt.client.WhatsAppClient}'s send path)
 * can attach the auto-generated stanza id before serialising. The {@link #uploadInfo} is
 * {@code null} when no mutations were emitted (an empty-patches build), in which case
 * there is nothing for the post-response success path to apply.
 *
 * @param node the IQ {@link NodeBuilder} carrying {@code <iq type="set" xmlns="w:sync:app:state">}
 * @param uploadInfo the metadata for {@code _uploadSuccessful}, or {@code null} when no mutations were emitted
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
public record SyncRequest(
        NodeBuilder node,
        UploadedPatchInfo uploadInfo
) {
    /**
     * Per-collection upload metadata captured during patch encoding for the post-response
     * success path.
     *
     * @apiNote
     * Mirrors WA Web's {@code _uploadSuccessful} input. After the server ACKs an outgoing
     * patch the success path persists the new sync action entries built from
     * {@link #mutations}, advances the collection version to {@link #newVersion} and the
     * LT-Hash to {@link #newLtHash}, and clears every pending mutation id listed in
     * {@link #uploadedPendingMutationIds}.
     *
     * @implNote
     * The {@link #uploadedPendingMutationIds} is captured pre-compaction so the success
     * path clears every original pending mutation, not just the deduplicated subset that
     * actually rode the wire.
     *
     * @param patchType the collection type
     * @param newLtHash the LT-Hash computed after applying outgoing mutations
     * @param newVersion the expected new collection version (local + 1)
     * @param uploadedPendingMutationIds the ids of every pending mutation (pre-compaction) included in the upload
     * @param mutations the per-mutation metadata for sync action entry persistence
     */
    public record UploadedPatchInfo(
            SyncPatchType patchType,
            byte[] newLtHash,
            long newVersion,
            List<String> uploadedPendingMutationIds,
            List<UploadedMutationInfo> mutations
    ) {
    }

    /**
     * Per-mutation metadata captured during patch encoding for the post-response success
     * path.
     *
     * @apiNote
     * Pairs the encrypted output ({@link #indexMac}, {@link #valueMac}, {@link #keyId},
     * {@link #operation}) with the plaintext source data ({@link #actionIndex},
     * {@link #actionValue}, {@link #actionVersion}) so the success path can persist a
     * matching {@link com.github.auties00.cobalt.model.sync.SyncActionEntry} without
     * re-decrypting the patch.
     *
     * @param indexMac the HMAC of the mutation index, also used as the entry's primary key
     * @param valueMac the HMAC of the encrypted value, used as the LT-Hash add/remove input
     * @param keyId the encrypting sync key id (latest active for SET, original for REMOVE)
     * @param operation the {@link SyncdOperation}
     * @param actionIndex the plaintext index string
     * @param actionValue the decoded {@link SyncActionValue}
     * @param actionVersion the action version number
     */
    public record UploadedMutationInfo(
            byte[] indexMac,
            byte[] valueMac,
            byte[] keyId,
            SyncdOperation operation,
            String actionIndex,
            SyncActionValue actionValue,
            int actionVersion
    ) {
    }
}
