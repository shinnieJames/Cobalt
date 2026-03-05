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
        // Check for IQ-level error (separate from collection-level errors)
        var iqType = responseNode.getAttributeAsString("type");
        if (iqType.isPresent() && iqType.get().equals("error")) {
            var errorCode = responseNode.getChild("error")
                    .flatMap(e -> e.getAttributeAsString("code"))
                    .map(Integer::parseInt)
                    .orElse(0);
            var errorText = responseNode.getChild("error")
                    .flatMap(e -> e.getAttributeAsString("text"))
                    .orElse("unknown");
            handleIqLevelError(errorCode, errorText);
        }

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

        var hasMore = collectionNode.hasAttribute("has_more_patches");

        // Check if response contains snapshot or patches
        var snapshotNode = collectionNode.getChild("snapshot");
        var patchesNode = collectionNode.getChild("patches");

        // Parse snapshot and patches independently — a response may contain both
        var snapshotRef = snapshotNode.map(this::parseSnapshotReference).orElse(null);
        var patches = patchesNode.map(this::parsePatches).orElse(List.of());
        return new MutationSyncResponse(patchType, version, hasMore, patches, snapshotRef);
    }

    /**
     * Parses a batched sync response node into multiple {@link MutationSyncResponse} objects.
     *
     * <p>Per WhatsApp Web behavior, a single IQ response can contain multiple
     * {@code <collection>} children under the {@code <sync>} node.
     *
     * @param responseNode the raw response node from the server
     * @return the list of parsed sync responses, one per collection
     * @throws WhatsAppWebAppStateSyncException.Conflict if any collection returns a 409 error
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError if the server returns a fatal error
     */
    public List<MutationSyncResponse> parseBatchedSyncResponse(Node responseNode) {
        // Check for IQ-level error
        var iqType = responseNode.getAttributeAsString("type");
        if (iqType.isPresent() && iqType.get().equals("error")) {
            var errorCode = responseNode.getChild("error")
                    .flatMap(e -> e.getAttributeAsString("code"))
                    .map(Integer::parseInt)
                    .orElse(0);
            var errorText = responseNode.getChild("error")
                    .flatMap(e -> e.getAttributeAsString("text"))
                    .orElse("unknown");
            handleIqLevelError(errorCode, errorText);
        }

        var syncNode = responseNode.getChild("sync")
                .orElseThrow(() -> new IllegalArgumentException("Response missing 'sync' node"));

        var collectionNodes = syncNode.getChildren("collection");
        var results = new ArrayList<MutationSyncResponse>(collectionNodes.size());
        for (var collectionNode : collectionNodes) {
            results.add(parseCollectionNode(collectionNode));
        }
        return results;
    }

    /**
     * Parses a single collection node into a {@link MutationSyncResponse}.
     *
     * @param collectionNode the collection node to parse
     * @return the parsed sync response
     */
    private MutationSyncResponse parseCollectionNode(Node collectionNode) {
        var type = collectionNode.getAttributeAsString("type");
        if (type.isPresent() && type.get().equals("error")) {
            handleErrorResponse(collectionNode);
        }

        var collectionName = collectionNode.getAttributeAsString("name")
                .orElseThrow(() -> new IllegalArgumentException("Collection missing 'name' attribute"));
        var patchType = SyncPatchType.of(collectionName)
                .orElseThrow(() -> new IllegalArgumentException("Invalid collection name: " + collectionName));

        var version = collectionNode.getAttributeAsLong("version")
                .orElse(0L);
        var hasMore = collectionNode.hasAttribute("has_more_patches");

        var snapshotNode = collectionNode.getChild("snapshot");
        var patchesNode = collectionNode.getChild("patches");

        var snapshotRef = snapshotNode.map(this::parseSnapshotReference).orElse(null);
        var patches = patchesNode.map(this::parsePatches).orElse(List.of());
        return new MutationSyncResponse(patchType, version, hasMore, patches, snapshotRef);
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
                    collectionNode.hasAttribute("has_more_patches")
            );
            case "400", "404", "405", "406" -> throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Server returned fatal error code: " + errorCode, null
            );
            default -> throw new WhatsAppWebAppStateSyncException.RetryableServerError(errorCode);
        }
    }

    /**
     * Handles IQ-level errors that affect all collections in the request.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdServerSync}: IQ-level errors are checked
     * before parsing individual collection responses. Error codes 400, 404, 405,
     * and 406 are fatal; all others are retryable.
     *
     * @param errorCode the IQ error code
     * @param errorText the IQ error text
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError for fatal error codes
     * @throws WhatsAppWebAppStateSyncException.RetryableServerError for retryable error codes
     */
    private void handleIqLevelError(int errorCode, String errorText) {
        switch (errorCode) {
            case 400, 404, 405, 406 -> throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "IQ-level fatal error " + errorCode + ": " + errorText, null);
            default -> throw new WhatsAppWebAppStateSyncException.RetryableServerError(
                    String.valueOf(errorCode));
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
