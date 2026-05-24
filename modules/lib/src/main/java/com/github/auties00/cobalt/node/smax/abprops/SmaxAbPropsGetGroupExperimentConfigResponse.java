package com.github.auties00.cobalt.node.smax.abprops;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a
 * {@link SmaxAbPropsGetGroupExperimentConfigRequest}.
 *
 * @apiNote
 * Mirrors the three-arm disjunction the WA Web RPC
 * {@code WASmaxAbPropsGetGroupExperimentConfigRPC.sendGetGroupExperimentConfigRPC}
 * returns: a {@link Success} carrying the materialised
 * group-scoped props bundle, a {@link ClientError} (do-not-retry), or
 * a {@link ServerError} (retry-eligible). {@code WAWebGroupAbPropsSyncJob}
 * pattern-matches on the variant to flush the bundle into the local
 * per-group store or signal a sync failure; Cobalt embedders mirror
 * that branching when wiring the same per-group sync loop.
 */
public sealed interface SmaxAbPropsGetGroupExperimentConfigResponse extends SmaxOperation.Response
        permits SmaxAbPropsGetGroupExperimentConfigResponse.Success, SmaxAbPropsGetGroupExperimentConfigResponse.ClientError, SmaxAbPropsGetGroupExperimentConfigResponse.ServerError {

    /**
     * Tries each {@link SmaxAbPropsGetGroupExperimentConfigResponse}
     * variant in priority order.
     *
     * @apiNote
     * Models {@code sendGetGroupExperimentConfigRPC}'s post-await
     * disjunction: {@link Success} first, then {@link ClientError},
     * then {@link ServerError}.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when no
     * variant parses cleanly; WA Web throws a
     * {@code SmaxParsingFailure} so the upstream
     * {@code WAGetGroupAbPropsProtocol} promise rejects through its
     * warn-and-error tail. Cobalt's dispatcher surfaces the empty
     * Optional through the configurable error handler.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxAbPropsGetGroupExperimentConfigRPC",
            exports = "sendGetGroupExperimentConfigRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxAbPropsGetGroupExperimentConfigResponse> of(Node node, Node request) {
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
     * The success reply variant carrying the materialised
     * group-scoped props bundle.
     *
     * @apiNote
     * Surfaced when the relay returns the {@code <props/>} subtree
     * scoped to the requested group; {@code WAWebGroupAbPropsSyncJob}
     * flushes the parsed {@code (hash, refresh, refreshId, props)}
     * tuple into the per-group cache.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsIQResultResponseMixin")
    final class Success implements SmaxAbPropsGetGroupExperimentConfigResponse {
        /**
         * The relay-returned content hash.
         *
         * @apiNote
         * Echoed back on the next conditional fetch for the same
         * group.
         */
        private final String propsHash;

        /**
         * The relay-returned refresh-cooldown hint, in seconds.
         *
         * @apiNote
         * Drives the next-sync timer for the per-group sync loop.
         */
        private final Integer propsRefresh;

        /**
         * The relay-returned refresh id.
         */
        private final Integer propsRefreshId;

        /**
         * The relay-returned A/B framework key.
         *
         * @apiNote
         * Identifies the framework variant the relay assigned to this
         * group; surfaced in downstream WAM tagging.
         */
        private final String propsAbKey;

        /**
         * The raw {@code <props/>} subtree.
         *
         * @apiNote
         * Carries the per-experiment overrides for this group;
         * consumers re-parse it through
         * {@code WAGetGroupAbPropsProtocol} to extract the
         * {@code ExperimentConfig} entries.
         */
        private final Node propsNode;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the IQ-result mixin
         * validation passes and the {@code <props/>} child is found.
         *
         * @param propsHash      the content hash; may be
         *                       {@code null}
         * @param propsRefresh   the refresh-cooldown; may be
         *                       {@code null}
         * @param propsRefreshId the refresh id; may be {@code null}
         * @param propsAbKey     the A/B framework key; may be
         *                       {@code null}
         * @param propsNode      the {@code <props/>} subtree; never
         *                       {@code null}
         * @throws NullPointerException if {@code propsNode} is
         *                              {@code null}
         */
        public Success(String propsHash, Integer propsRefresh, Integer propsRefreshId,
                       String propsAbKey, Node propsNode) {
            this.propsHash = propsHash;
            this.propsRefresh = propsRefresh;
            this.propsRefreshId = propsRefreshId;
            this.propsAbKey = propsAbKey;
            this.propsNode = Objects.requireNonNull(propsNode, "propsNode cannot be null");
        }

        /**
         * Returns the content hash.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the hash
         */
        public Optional<String> propsHash() {
            return Optional.ofNullable(propsHash);
        }

        /**
         * Returns the refresh-cooldown hint.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the cooldown
         */
        public Optional<Integer> propsRefresh() {
            return Optional.ofNullable(propsRefresh);
        }

        /**
         * Returns the refresh id.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the refresh id
         */
        public Optional<Integer> propsRefreshId() {
            return Optional.ofNullable(propsRefreshId);
        }

        /**
         * Returns the A/B framework key.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the key
         */
        public Optional<String> propsAbKey() {
            return Optional.ofNullable(propsAbKey);
        }

        /**
         * Returns the raw {@code <props/>} subtree.
         *
         * @apiNote
         * Embedders re-parse the subtree to extract the per-experiment
         * overrides for this group.
         *
         * @return the {@code <props/>} node; never {@code null}
         */
        public Node propsNode() {
            return propsNode;
        }

        /**
         * Tries to parse a {@link Success} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInAbPropsGetGroupExperimentConfigResponseSuccess.parseGetGroupExperimentConfigResponseSuccess};
         * empty when the IQ-result mixin fails or when the
         * {@code <props/>} child is missing.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseSuccess",
                exports = "parseGetGroupExperimentConfigResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var props = node.getChild("props").orElse(null);
            if (props == null) {
                return Optional.empty();
            }
            var hash = props.getAttributeAsString("hash").orElse(null);
            var refresh = props.getAttributeAsInt("refresh").isPresent()
                    ? props.getAttributeAsInt("refresh").getAsInt()
                    : null;
            var refreshId = props.getAttributeAsInt("refresh_id").isPresent()
                    ? props.getAttributeAsInt("refresh_id").getAsInt()
                    : null;
            var abKey = props.getAttributeAsString("ab_key").orElse(null);
            return Optional.of(new Success(hash, refresh, refreshId, abKey, props));
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
            return Objects.equals(this.propsHash, that.propsHash)
                    && Objects.equals(this.propsRefresh, that.propsRefresh)
                    && Objects.equals(this.propsRefreshId, that.propsRefreshId)
                    && Objects.equals(this.propsAbKey, that.propsAbKey)
                    && Objects.equals(this.propsNode, that.propsNode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(propsHash, propsRefresh, propsRefreshId, propsAbKey, propsNode);
        }

        @Override
        public String toString() {
            return "SmaxAbPropsGetGroupExperimentConfigResponse.Success[propsHash=" + propsHash
                    + ", propsRefresh=" + propsRefresh
                    + ", propsRefreshId=" + propsRefreshId
                    + ", propsAbKey=" + propsAbKey + ']';
        }
    }

    /**
     * The client-error reply variant.
     *
     * @apiNote
     * Surfaced when the relay rejects the request as malformed or
     * unauthorised; the WA Web sync job treats this as a permanent
     * per-group failure.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseErrorNoRetry")
    final class ClientError implements SmaxAbPropsGetGroupExperimentConfigResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new client-error projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} once the client-error
         * envelope has been lifted from the inbound stanza.
         *
         * @param errorCode the error code
         * @param errorText the optional text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInAbPropsGetGroupExperimentConfigResponseErrorNoRetry.parseGetGroupExperimentConfigResponseErrorNoRetry};
         * empty when the inbound stanza is not a 4xx error envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseErrorNoRetry",
                exports = "parseGetGroupExperimentConfigResponseErrorNoRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxAbPropsGetGroupExperimentConfigResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The server-error reply variant.
     *
     * @apiNote
     * Surfaced when the relay encountered a transient internal
     * failure; embedders re-issue the request after a backoff.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseErrorRetry")
    final class ServerError implements SmaxAbPropsGetGroupExperimentConfigResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         */
        private final String errorText;

        /**
         * Constructs a new server-error projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} once the server-error
         * envelope has been lifted from the inbound stanza.
         *
         * @param errorCode the error code
         * @param errorText the optional text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @apiNote
         * Empty when the relay omitted it.
         *
         * @return an {@link Optional} carrying the text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInAbPropsGetGroupExperimentConfigResponseErrorRetry.parseGetGroupExperimentConfigResponseErrorRetry};
         * empty when the inbound stanza is not a 5xx error envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetGroupExperimentConfigResponseErrorRetry",
                exports = "parseGetGroupExperimentConfigResponseErrorRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxAbPropsGetGroupExperimentConfigResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
