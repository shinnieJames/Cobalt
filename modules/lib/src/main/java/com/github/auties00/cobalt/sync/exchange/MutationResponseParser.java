package com.github.auties00.cobalt.sync.exchange;

import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.media.ExternalBlobReference;
import com.github.auties00.cobalt.model.media.ExternalBlobReferenceSpec;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdPatch;
import com.github.auties00.cobalt.model.sync.data.SyncdPatchSpec;
import com.github.auties00.cobalt.node.Node;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SequencedCollection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses incoming {@code <iq xmlns="w:sync:app:state">} response stanzas into
 * {@link MutationSyncResponse} records.
 *
 * <p>Two parse modes share the bulk of the body. The single-collection mode
 * ({@link #parseSyncResponse(Node)}) raises on a collection-level error so a failed push can
 * roll back; the batched mode ({@link #parseBatchedSyncResponse(Node)}) captures the same
 * errors on each response record so the caller can process the surviving collections. Both
 * share the same IQ-level error router ({@link #handleIqLevelError(int, String, Node)}) and
 * the same protobuf decoders for {@code <patch>} and {@code <snapshot>} children. The parser
 * is driven directly with the raw {@code <iq>} {@link Node} delivered by the WhatsApp socket;
 * no embedder integration is required.
 *
 * @implNote
 * This implementation skips the WAM/telemetry side effects that WA Web fires from
 * {@code reportSyncdFatalError} per Cobalt's pluggable error model: the routed
 * {@link WhatsAppWebAppStateSyncException} subtypes carry the same classification, and the
 * caller decides whether to log or beacon them via the configured error handler.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdResponseParser")
@WhatsAppWebModule(moduleName = "WAWebSyncdDecode")
@WhatsAppWebModule(moduleName = "WAWebSyncdValidateServerSyncProtobuf")
public final class MutationResponseParser {
    /**
     * Holds the diagnostic logger used for the {@link Level#FINE FINE}-level
     * {@code clientDebugData} dump emitted by {@link #logClientDebugData(SyncdPatch)}.
     */
    private static final Logger LOGGER = Logger.getLogger(MutationResponseParser.class.getName());

    /**
     * Parses a single-collection sync response.
     *
     * <p>Used on the push response path where exactly one collection is expected and a
     * collection-level error should propagate as a thrown exception so the push can roll back.
     * IQ-level errors are routed first via {@link #handleIqLevelError(int, String, Node)}, then
     * collection-level errors via {@link #handleErrorResponse(Node)}.
     * Use {@link #parseBatchedSyncResponse(Node)} for pull responses that legitimately carry
     * multiple collections.
     *
     * @implNote
     * This implementation parses the collection name before the error gate so the
     * unknown-name path raises the same {@link WhatsAppWebAppStateSyncException.UnexpectedError}
     * regardless of whether the response would otherwise have been an error.
     *
     * @param responseNode the raw IQ response node from the server
     * @return the parsed {@link MutationSyncResponse}
     * @throws WhatsAppWebAppStateSyncException.Conflict when the server returns a 409
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError when the server returns a fatal IQ or collection error
     * @throws WhatsAppWebAppStateSyncException.RetryableServerError when the server returns a retryable error
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdResponseParser", exports = "syncResponseParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationSyncResponse parseSyncResponse(Node responseNode) {
        var iqType = responseNode.getAttributeAsString("type");
        if (iqType.isPresent() && iqType.get().equals("error")) {
            var iqErrorNode = responseNode.getChild("error").orElse(null);
            var errorCode = iqErrorNode != null
                    ? iqErrorNode.getAttributeAsString("code").map(Integer::parseInt).orElse(0)
                    : 0;
            var errorText = iqErrorNode != null
                    ? iqErrorNode.getAttributeAsString("text").orElse("unknown")
                    : "unknown";
            handleIqLevelError(errorCode, errorText, iqErrorNode);
        }

        var syncNode = responseNode.getChild("sync")
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Response missing 'sync' node",
                        null
                ));

        var collectionNode = syncNode.getChild("collection")
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Response missing 'collection' node",
                        null
                ));

        var type = collectionNode.getAttributeAsString("type");
        if (type.isPresent() && type.get().equals("error")) {
            handleErrorResponse(collectionNode);
        }

        var collectionName = collectionNode.getAttributeAsString("name")
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Collection missing 'name' attribute",
                        null
                ));
        var patchType = SyncPatchType.of(collectionName)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Invalid collection name: " + collectionName,
                        null
                ));

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
     * Parses a multi-collection sync response.
     *
     * <p>Used on the pull response path where one IQ may carry several {@code <collection>}
     * children, one per dirty collection. Per-collection errors are captured on the response
     * object via {@link #parseCollectionNode(Node)} so the caller can apply the successful
     * collections and retry only the failed ones; IQ-level errors still throw.
     *
     * @implNote
     * This implementation iterates the {@code <collection>} children in document order, and the
     * resulting list preserves that order so callers can correlate it positionally with their
     * push input.
     *
     * @param responseNode the raw IQ response node from the server
     * @return the per-collection {@link MutationSyncResponse} list
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError when an IQ-level fatal error fires
     * @throws WhatsAppWebAppStateSyncException.RetryableServerError when an IQ-level retryable error fires
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdResponseParser", exports = "syncResponseParser", adaptation = WhatsAppAdaptation.DIRECT)
    public List<MutationSyncResponse> parseBatchedSyncResponse(Node responseNode) {
        var iqType = responseNode.getAttributeAsString("type");
        if (iqType.isPresent() && iqType.get().equals("error")) {
            var iqErrorNode = responseNode.getChild("error").orElse(null);
            var errorCode = iqErrorNode != null
                    ? iqErrorNode.getAttributeAsString("code").map(Integer::parseInt).orElse(0)
                    : 0;
            var errorText = iqErrorNode != null
                    ? iqErrorNode.getAttributeAsString("text").orElse("unknown")
                    : "unknown";
            handleIqLevelError(errorCode, errorText, iqErrorNode);
        }

        var syncNode = responseNode.getChild("sync")
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Response missing 'sync' node",
                        null
                ));

        var collectionNodes = syncNode.getChildren("collection");
        var results = new ArrayList<MutationSyncResponse>(collectionNodes.size());
        for (var collectionNode : collectionNodes) {
            results.add(parseCollectionNode(collectionNode));
        }
        return results;
    }

    /**
     * Parses a single {@code <collection>} child into a {@link MutationSyncResponse}, capturing
     * any collection-level error on the response rather than throwing.
     *
     * <p>Used by {@link #parseBatchedSyncResponse(Node)} so the failure of one collection in a
     * batch does not poison the rest. A collection in error state yields a response carrying the
     * exception via {@link MutationSyncResponse#collectionError()}; an otherwise valid collection
     * yields its decoded patches or snapshot reference.
     *
     * @implNote
     * This implementation reuses {@link #buildCollectionError(Node)} so the collection-level
     * routing (409 to {@link WhatsAppWebAppStateSyncException.Conflict}, 400/404 to
     * {@link WhatsAppWebAppStateSyncException.UnexpectedError}, anything else to
     * {@link WhatsAppWebAppStateSyncException.RetryableServerError}) stays consistent between the
     * throwing and capturing call paths.
     *
     * @param collectionNode the collection node to parse
     * @return the parsed {@link MutationSyncResponse}; collection-level errors are surfaced via
     *         {@link MutationSyncResponse#collectionError()}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdResponseParser", exports = "syncResponseParser", adaptation = WhatsAppAdaptation.DIRECT)
    private MutationSyncResponse parseCollectionNode(Node collectionNode) {
        var collectionName = collectionNode.getAttributeAsString("name")
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Collection missing 'name' attribute",
                        null
                ));
        var patchType = SyncPatchType.of(collectionName)
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Invalid collection name: " + collectionName,
                        null
                ));

        var type = collectionNode.getAttributeAsString("type");
        if (type.isPresent() && type.get().equals("error")) {
            var collectionError = buildCollectionError(collectionNode);
            var hasMore = collectionNode.hasAttribute("has_more_patches");
            return new MutationSyncResponse(patchType, 0L, hasMore, List.of(), null, collectionError);
        }

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
     * Builds the {@link WhatsAppWebAppStateSyncException} subtype that corresponds to a given
     * collection-level error code.
     *
     * <p>The routing table is fixed: 409 maps to
     * {@link WhatsAppWebAppStateSyncException.Conflict}, 400 and 404 to
     * {@link WhatsAppWebAppStateSyncException.UnexpectedError}, and any other code (including a
     * missing code attribute) to {@link WhatsAppWebAppStateSyncException.RetryableServerError}.
     *
     * @param collectionNode the collection node carrying the error
     * @return the exception that should be reported for this collection
     */
    private WhatsAppWebAppStateSyncException buildCollectionError(Node collectionNode) {
        var errorNode = collectionNode.getChild("error");
        var errorCode = errorNode
                .flatMap(e -> e.getAttributeAsString("code"))
                .orElse("unknown");

        return switch (errorCode) {
            case "409" -> new WhatsAppWebAppStateSyncException.Conflict(
                    collectionNode.hasAttribute("has_more_patches")
            );
            case "400", "404" -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "Server returned fatal error code: " + errorCode, null
            );
            default -> new WhatsAppWebAppStateSyncException.RetryableServerError(errorCode);
        };
    }

    /**
     * Throws the {@link WhatsAppWebAppStateSyncException} produced by
     * {@link #buildCollectionError(Node)}.
     *
     * <p>Used only by the single-collection parse path {@link #parseSyncResponse(Node)}; the
     * batched path captures the same exception on the response record instead.
     *
     * @param collectionNode the collection node carrying the error
     * @throws WhatsAppWebAppStateSyncException.Conflict for 409 responses
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError for 400/404 responses
     * @throws WhatsAppWebAppStateSyncException.RetryableServerError for any other code
     */
    private void handleErrorResponse(Node collectionNode) {
        throw buildCollectionError(collectionNode);
    }

    /**
     * Routes an IQ-level error code to the appropriate {@link WhatsAppWebAppStateSyncException}
     * subtype and throws it.
     *
     * <p>IQ-level errors affect every collection in the request and short-circuit the parse:
     * {@code 400}, {@code 404}, {@code 405} and {@code 406} are fatal; everything else is
     * retryable, with the optional {@code backoff} attribute preserved on the
     * {@link WhatsAppWebAppStateSyncException.RetryableServerError}.
     *
     * @param errorCode the IQ error code
     * @param errorText the IQ error text
     * @param errorNode the {@code <error>} node, possibly {@code null}
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError for fatal IQ codes
     * @throws WhatsAppWebAppStateSyncException.RetryableServerError for any other IQ code
     */
    private void handleIqLevelError(int errorCode, String errorText, Node errorNode) {
        switch (errorCode) {
            case 400, 404, 405, 406 -> throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "IQ-level fatal error " + errorCode + ": " + errorText, null);
            default -> {
                var serverBackoffMs = errorNode != null
                        ? errorNode.getAttributeAsLong("backoff", null)
                        : null;
                throw new WhatsAppWebAppStateSyncException.RetryableServerError(
                        String.valueOf(errorCode), serverBackoffMs);
            }
        }
    }

    /**
     * Decodes the {@code <snapshot>} node content into an {@link ExternalBlobReference} the
     * caller can later download from MMS.
     *
     * <p>Snapshots are too large to ship inline; the server returns an external blob reference
     * and the caller streams the actual {@code SyncdSnapshot} bytes from MMS via the media
     * connection. A bare snapshot child without content, or content that fails to deserialize,
     * is treated as a fatal error.
     *
     * @implNote
     * This implementation collapses WA Web's separate
     * {@code reportSyncdFatalError(EXTERNAL_BLOB_REFERENCE_PROTOBUF_DESERIALIZATION_FAILED)} WAM
     * beacon plus {@code WALogger.ERROR} into a single thrown
     * {@link WhatsAppWebAppStateSyncException.UnexpectedError}, in line with Cobalt's pluggable
     * error model.
     *
     * @param snapshotNode the {@code <snapshot>} node
     * @return the parsed {@link ExternalBlobReference}
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError when the node has no content or the protobuf fails to deserialize
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdDecode", exports = "decodeExternalBlobReference", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdValidateServerSyncProtobuf", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private ExternalBlobReference parseSnapshotReference(Node snapshotNode) {
        var snapshotBytes = snapshotNode.toContentBytes()
                .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "Snapshot node has no content",
                        null
                ));

        try {
            return ExternalBlobReferenceSpec.decode(snapshotBytes);
        } catch (Exception e) {
            throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                    "syncd: external blob reference protobuf deserialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes every {@code <patch>} child of the supplied {@code <patches>} node into a
     * {@link SyncdPatch}, logging each patch's debug data at {@link Level#FINE FINE}.
     *
     * <p>Used by both parse modes. Returns an empty collection when the {@code <patches>} parent
     * has no children; a missing patch content or undecodable patch protobuf is fatal.
     *
     * @implNote
     * This implementation collapses WA Web's separate
     * {@code reportSyncdFatalError(PATCH_PROTOBUF_DESERIALIZATION_FAILED)} WAM beacon plus
     * {@code WALogger.ERROR} into a single thrown
     * {@link WhatsAppWebAppStateSyncException.UnexpectedError}; the per-patch
     * {@link #logClientDebugData(SyncdPatch)} call surfaces the same diagnostic data WA Web's
     * {@code _applyPatch} prints.
     *
     * @param patchesNode the parent {@code <patches>} node
     * @return the parsed patches in document order
     * @throws WhatsAppWebAppStateSyncException.UnexpectedError when a patch has no content or fails to deserialize
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdDecode", exports = "decodeSyncdPatch", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdValidateServerSyncProtobuf", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private SequencedCollection<SyncdPatch> parsePatches(Node patchesNode) {
        var patches = new ArrayList<SyncdPatch>();

        var patchNodes = patchesNode.getChildren("patch");

        for (var patchNode : patchNodes) {
            var patchBytes = patchNode.toContentBytes()
                    .orElseThrow(() -> new WhatsAppWebAppStateSyncException.UnexpectedError(
                            "Patch node has no content",
                            null
                    ));

            try {
                var patch = SyncdPatchSpec.decode(patchBytes);
                logClientDebugData(patch);
                patches.add(patch);
            } catch (Exception e) {
                throw new WhatsAppWebAppStateSyncException.UnexpectedError(
                        "syncd: patch protobuf deserialization failed: " + e.getMessage(), e);
            }
        }

        return patches;
    }

    /**
     * Logs the decoded {@code currentLthash} and {@code newLthash} of the supplied patch at
     * {@link Level#FINE FINE} for diagnostic cross-checking.
     *
     * <p>Lets a developer correlate the server's reported LT-hash transition against Cobalt's
     * locally computed {@link com.github.auties00.cobalt.sync.crypto.MutationLTHash} state when
     * chasing a desync.
     *
     * @implNote
     * This implementation is a no-op when {@link Level#FINE FINE} is disabled. Decoding the
     * debug data itself is best-effort and does not throw.
     *
     * @param patch the patch whose debug data should be logged; never {@code null}
     */
    private void logClientDebugData(SyncdPatch patch) {
        if (!LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        patch.decodedClientDebugData().ifPresent(debug -> {
            var hex = HexFormat.of();
            var current = debug.currentLthash().map(hex::formatHex).orElse("<none>");
            var next = debug.newLthash().map(hex::formatHex).orElse("<none>");
            LOGGER.fine(() -> "patch debug: currentLthash=" + current + " newLthash=" + next);
        });
    }
}
