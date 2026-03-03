package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchSpec;
import com.github.auties00.cobalt.node.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

/**
 * Parses sync response nodes into {@link MutationSyncResponse} objects.
 *
 * <p>Handles both snapshot and patch responses, including error detection
 * for server-side error codes (409, 400, 404).
 */
public final class MutationResponseParser {
    /**
     * Parses a sync response node into a {@link MutationSyncResponse}.
     *
     * @param responseNode the raw response node from the server
     * @return the parsed sync response
     * @throws WhatsAppWebAppStateSyncException.Conflict if the server returns a 409 error
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if the server returns a fatal error
     */
    public MutationSyncResponse parseSyncResponse(Node responseNode) {
        // Navigate to sync node
        var syncNode = responseNode.getChild("sync")
                .orElseThrow(() -> new IllegalArgumentException("Response missing 'sync' node"));

        // Navigate to collection node
        var collectionNode = syncNode.getChild("collection")
                .orElseThrow(() -> new IllegalArgumentException("Response missing 'collection' node"));

        // Check for error response
        var type = collectionNode.getAttributeAsString("type");
        if (type.isPresent() && type.get().equals("error")) {
            handleErrorResponse(collectionNode);
        }

        // Extract collection metadata
        var collectionName = collectionNode.getAttributeAsString("name")
                .orElseThrow(() -> new IllegalArgumentException("Collection missing 'name' attribute"));
        var patchType = SyncPatchType.of(collectionName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid collection name: " + collectionName));

        var version = collectionNode.getAttributeAsLong("version")
                .orElse(0L);

        var hasMore = collectionNode.getAttributeAsBool("has_more_patches", false);

        // Check if response contains snapshot or patches
        var snapshotNode = collectionNode.getChild("snapshot");
        var patchesNode = collectionNode.getChild("patches");

        if (snapshotNode.isPresent()) {
            // Parse snapshot as ExternalBlobReference (needs to be downloaded)
            var snapshotRef = parseSnapshotReference(snapshotNode.get());
            return new MutationSyncResponse(patchType, version, hasMore, List.of(), snapshotRef);
        } else if (patchesNode.isPresent()) {
            // Parse patches
            var patches = parsePatches(patchesNode.get());
            return new MutationSyncResponse(patchType, version, hasMore, patches, null);
        } else {
            // No updates available
            return new MutationSyncResponse(patchType, version, false, List.of(), null);
        }
    }

    /**
     * Handles error responses from the server.
     *
     * @param collectionNode the collection node containing the error
     * @throws WhatsAppWebAppStateSyncException.Conflict for 409 errors
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError for 400/404/other errors
     */
    private void handleErrorResponse(Node collectionNode) {
        var errorCode = collectionNode.getChild("error")
                .flatMap(errorNode -> errorNode.getAttributeAsString("code"))
                .orElse("unknown");

        switch (errorCode) {
            case "409" -> throw new WhatsAppWebAppStateSyncException.Conflict(
                    collectionNode.getAttributeAsBool("has_more_patches", false)
            );
            case "400", "404" -> throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Server returned fatal error code: " + errorCode, null
            );
            default -> throw new WhatsAppWebAppStateSyncException.RetryableServerError(errorCode);
        }
    }

    /**
     * Parses snapshot node content as an {@link ExternalBlobReference}.
     *
     * <p>Per WhatsApp Web behavior, the snapshot content bytes encode an
     * {@code ExternalBlobReference} that must be downloaded from MMS to
     * obtain the actual {@code SyncdSnapshot} data.
     *
     * @param snapshotNode the snapshot node
     * @return the parsed external blob reference
     */
    private ExternalBlobReference parseSnapshotReference(Node snapshotNode) {
        var snapshotBytes = snapshotNode.toContentBytes()
                .orElseThrow(() -> new IllegalArgumentException("Snapshot node has no content"));

        try {
            return ExternalBlobReferenceSpec.decode(snapshotBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode snapshot reference", e);
        }
    }

    /**
     * Parses patch nodes into a collection of {@link SyncdPatch} objects.
     *
     * @param patchesNode the patches parent node
     * @return the parsed patches
     */
    private SequencedCollection<SyncdPatch> parsePatches(Node patchesNode) {
        var patches = new ArrayList<SyncdPatch>();

        var patchNodes = patchesNode.getChildren("patch");

        for (var patchNode : patchNodes) {
            var patchBytes = patchNode.toContentBytes()
                    .orElseThrow(() -> new IllegalArgumentException("Patch node has no content"));

            try {
                var patch = SyncdPatchSpec.decode(patchBytes);
                patches.add(patch);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decode patch", e);
            }
        }

        return patches;
    }
}
