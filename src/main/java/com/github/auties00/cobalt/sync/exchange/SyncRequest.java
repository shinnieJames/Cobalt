package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.model.sync.SyncActionValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.List;

/**
 * Represents the result of building a sync request, pairing the IQ request
 * node with optional upload metadata for post-response processing.
 *
 * <p>Per WhatsApp Web {@code WAWebSyncdServerSync}: when a sync request
 * includes outgoing mutations, the encrypted mutation metadata and pending
 * mutation IDs are carried alongside the request so that
 * {@code _uploadSuccessful} can persist sync action entries, update the
 * collection version and LT-Hash, and clear pending mutations after the
 * server acknowledges the upload.
 *
 * @param node       the IQ request node builder
 * @param uploadInfo the upload metadata, or {@code null} if no mutations
 *                   were included in this request
 */
public record SyncRequest(
        NodeBuilder node,
        UploadedPatchInfo uploadInfo
) {
    /**
     * Metadata about an uploaded patch, captured during request building
     * for post-upload success processing.
     *
     * <p>Per WhatsApp Web {@code _uploadSuccessful}: after the server
     * acknowledges a push, the client uses this metadata to persist sync
     * action entries for SET mutations, remove entries for REMOVE mutations,
     * update the collection version and LT-Hash, and clear uploaded
     * pending mutations.
     *
     * @param patchType  the collection type
     * @param newLtHash  the LT-Hash computed after applying outgoing mutations
     * @param newVersion the expected new collection version (local + 1)
     * @param mutations  the per-mutation metadata for sync action entry persistence
     */
    public record UploadedPatchInfo(
            SyncPatchType patchType,
            byte[] newLtHash,
            long newVersion,
            List<UploadedMutationInfo> mutations
    ) {
    }

    /**
     * Per-mutation metadata pairing the encrypted output with the plaintext
     * source data needed for sync action entry persistence.
     *
     * @param indexMac      the HMAC of the mutation index
     * @param valueMac      the HMAC of the encrypted value
     * @param keyId         the encrypting key ID
     * @param operation     the mutation operation (SET or REMOVE)
     * @param actionIndex   the plaintext index string
     * @param actionValue   the decoded action value
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
