package com.github.auties00.cobalt.node.iq.syncd;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.iq.IqOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

/**
 * Sealed family of inbound reply variants produced by the relay in response to an
 * {@link IqSyncdServerSyncRequest}.
 *
 * @apiNote
 * Switch on the returned variant to discriminate the relay outcome: a {@link Success}
 * carries the typed per-collection projections (state, version, patches and
 * snapshot), a {@link ClientError} carries the global {@code SyncdFatalError} codes
 * ({@code 400, 404, 405, 406}) that WA Web treats as non-retryable, and a
 * {@link ServerError} carries the transient codes that WA Web wraps as
 * {@code SyncdRetryableError} and retries with exponential backoff plus the relay's
 * optional backoff hint.
 *
 * @implNote
 * This implementation mirrors WA Web's {@code WAWebSyncdResponseParser.syncResponseParser}
 * verbatim for the per-collection projection, except the {@code <patch>} and
 * {@code <snapshot>} payloads are surfaced as raw byte arrays (Cobalt decodes the
 * {@code SyncdPatch} / {@code ExternalBlobReference} protobufs at a higher layer
 * rather than inline in the parser).
 */
public sealed interface IqSyncdServerSyncResponse extends IqOperation.Response
        permits IqSyncdServerSyncResponse.Success, IqSyncdServerSyncResponse.ClientError, IqSyncdServerSyncResponse.ServerError {

    /**
     * Parses the inbound stanza into the first matching {@link IqSyncdServerSyncResponse}
     * variant.
     *
     * @apiNote
     * Try this once per inbound reply; the priority ordering (success, then
     * client-error, then server-error) matches the wire shape and never returns
     * ambiguous matches.
     *
     * @implNote
     * This implementation calls each variant's {@code of(node, request)} in turn
     * and returns the first present result.
     *
     * @param node    the inbound IQ stanza received from the relay; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no
     *         documented variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync",
            exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends IqSyncdServerSyncResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var clientError = ClientError.of(node, request);
        if (clientError.isPresent()) {
            return clientError;
        }
        return ServerError.of(node, request);
    }

    /**
     * Success variant. The relay returned per-collection projections inside the
     * {@code <sync>} envelope.
     *
     * @apiNote
     * Inspect {@link #collections()} to drive the per-collection apply pipeline;
     * each entry's {@link IqSyncdServerSyncResponseCollection#state() state} drives
     * the next-iteration decision (apply, reconcile, retry or stop) per
     * {@link IqSyncdServerSyncCollectionState}.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
    @WhatsAppWebModule(moduleName = "WAWebSyncdResponseParser")
    final class Success implements IqSyncdServerSyncResponse {
        /**
         * Holds the typed per-collection projections, one per {@code <collection/>}
         * child of the inbound {@code <sync/>} envelope.
         */
        private final List<IqSyncdServerSyncResponseCollection> collections;

        /**
         * Constructs a successful reply bound to the given per-collection
         * projections.
         *
         * @param collections the projections; never {@code null}, possibly empty
         * @throws NullPointerException if {@code collections} is {@code null}
         */
        public Success(List<IqSyncdServerSyncResponseCollection> collections) {
            Objects.requireNonNull(collections, "collections cannot be null");
            this.collections = collections;
        }

        /**
         * Returns the typed list of per-collection projections.
         *
         * @return an unmodifiable view of the collections; never {@code null},
         *         possibly empty
         */
        public SequencedCollection<IqSyncdServerSyncResponseCollection> collections() {
            return Collections.unmodifiableSequencedCollection(collections);
        }

        /**
         * Parses the inbound stanza into a {@link Success} variant when it
         * matches the success schema.
         *
         * @apiNote
         * Returns empty when the SMAX result-envelope check fails or when the
         * {@code <sync>} child is absent. {@code <collection>} children missing
         * the {@code name} attribute are silently skipped (WA Web throws a
         * {@code SyncdFatalError} for the same condition; see implementation note
         * below).
         *
         * @implNote
         * This implementation silently drops nameless {@code <collection>}
         * children instead of throwing, deferring fatal-collection-name handling
         * to the caller's apply pipeline rather than the parser.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when
         *         the stanza does not match the success schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSyncdResponseParser",
                exports = "syncResponseParser", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var syncChild = node.getChild("sync").orElse(null);
            if (syncChild == null) {
                return Optional.empty();
            }
            var collections = new ArrayList<IqSyncdServerSyncResponseCollection>();
            for (var collectionChild : syncChild.getChildren("collection")) {
                parseCollection(collectionChild).ifPresent(collections::add);
            }
            return Optional.of(new Success(collections));
        }

        /**
         * Projects a single {@code <collection/>} child into a typed
         * {@link IqSyncdServerSyncResponseCollection}.
         *
         * @apiNote
         * Internal helper for {@link #of(Node, Node)}; not exposed because the
         * surrounding success-envelope assertions belong to the caller.
         *
         * @implNote
         * This implementation mirrors WA Web's {@code state-of-collection} branch
         * inside {@code syncResponseParser}: {@code 409} maps to
         * {@link IqSyncdServerSyncCollectionState#CONFLICT} or
         * {@link IqSyncdServerSyncCollectionState#CONFLICT_HAS_MORE} depending on
         * the {@code has_more_patches} attribute, {@code 400/404/405} map to
         * {@link IqSyncdServerSyncCollectionState#ERROR_FATAL}, any other code
         * maps to {@link IqSyncdServerSyncCollectionState#ERROR_RETRY}, and the
         * absence of an error maps to {@link IqSyncdServerSyncCollectionState#SUCCESS}
         * or {@link IqSyncdServerSyncCollectionState#SUCCESS_HAS_MORE}.
         *
         * @param child the {@code <collection/>} node; never {@code null}
         * @return an {@link Optional} carrying the projection, or empty when the
         *         child lacks a {@code name} attribute
         */
        private static Optional<IqSyncdServerSyncResponseCollection> parseCollection(Node child) {
            var name = child.getAttributeAsString("name").orElse(null);
            if (name == null) {
                return Optional.empty();
            }
            var hasMorePatches = child.hasAttribute("has_more_patches");
            IqSyncdServerSyncCollectionState state;
            if (child.hasAttribute("type", "error")) {
                var errorChild = child.getChild("error").orElse(null);
                var errorCode = errorChild == null
                        ? null
                        : errorChild.getAttributeAsString("code").orElse(null);
                if ("409".equals(errorCode)) {
                    state = hasMorePatches
                            ? IqSyncdServerSyncCollectionState.CONFLICT_HAS_MORE
                            : IqSyncdServerSyncCollectionState.CONFLICT;
                } else if ("400".equals(errorCode)
                        || "404".equals(errorCode)
                        || "405".equals(errorCode)) {
                    state = IqSyncdServerSyncCollectionState.ERROR_FATAL;
                } else {
                    state = IqSyncdServerSyncCollectionState.ERROR_RETRY;
                }
            } else {
                state = hasMorePatches
                        ? IqSyncdServerSyncCollectionState.SUCCESS_HAS_MORE
                        : IqSyncdServerSyncCollectionState.SUCCESS;
            }
            var version = child.getAttributeAsLong("version").stream().boxed().findFirst().orElse(null);
            var patches = new ArrayList<byte[]>();
            child.getChild("patches").ifPresent(patchesNode ->
                    patchesNode.streamChildren("patch").forEach(patchNode ->
                            patchNode.toContentBytes().ifPresent(patches::add)));
            var snapshot = child.getChild("snapshot")
                    .flatMap(Node::toContentBytes)
                    .orElse(null);
            return Optional.of(new IqSyncdServerSyncResponseCollection(
                    name, state, version, patches, snapshot));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.collections, that.collections);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collections);
        }

        @Override
        public String toString() {
            return "IqSyncdServerSyncResponse.Success[collections=" + collections + ']';
        }
    }

    /**
     * Client-error variant. The relay rejected the whole sync with a code WA Web
     * treats as fatal ({@code 400, 404, 405, 406}).
     *
     * @apiNote
     * WA Web wraps each code in a {@code SyncdFatalError} after reporting the
     * specific {@code SyncdFatalErrorType} WAM event (e.g.
     * {@code XMPP_BAD_REQUEST_GLOBAL_ERROR} for 400). Treat the same way; the
     * sync iteration is not retryable.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
    final class ClientError implements IqSyncdServerSyncResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ClientError} variant when it
         * matches the standard SMAX client-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the client-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync",
                exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ClientError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqSyncdServerSyncResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant. The relay encountered a transient internal failure
     * (typically a code outside the fatal set) processing the sync.
     *
     * @apiNote
     * WA Web wraps this in a {@code SyncdRetryableError} carrying the relay's
     * backoff hint and reschedules the sync iteration through the standard
     * exponential-backoff loop.
     */
    @WhatsAppWebModule(moduleName = "WAWebSyncdServerSync")
    final class ServerError implements IqSyncdServerSyncResponse {
        /**
         * Holds the numeric server-side error code.
         */
        private final int errorCode;

        /**
         * Holds the optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply carrying the relay-echoed envelope.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric server-side error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or empty when the
         *         relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Parses the inbound stanza into a {@link ServerError} variant when it
         * matches the standard SMAX server-error envelope.
         *
         * @apiNote
         * Returns empty when the envelope check fails; delegates entirely to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty
         *         when the stanza does not match the server-error schema
         */
        @WhatsAppWebExport(moduleName = "WAWebSyncdServerSync",
                exports = "serverSync", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ServerError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "IqSyncdServerSyncResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
