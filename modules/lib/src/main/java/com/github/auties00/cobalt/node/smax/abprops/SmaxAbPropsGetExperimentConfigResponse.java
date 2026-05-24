package com.github.auties00.cobalt.node.smax.abprops;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
 * {@link SmaxAbPropsGetExperimentConfigRequest}.
 *
 * @apiNote
 * Mirrors the three-arm disjunction the WA Web RPC
 * {@code WASmaxAbPropsGetExperimentConfigRPC.sendGetExperimentConfigRPC}
 * returns: a {@link Success} carrying the materialised props bundle,
 * a {@link ClientError} (do-not-retry), or a {@link ServerError}
 * (retry-eligible). {@code WAWebAbPropsSyncJob} pattern-matches on
 * the variant to either flush the bundle into local storage or back
 * off; Cobalt embedders mirror that branching when wiring the same
 * sync loop.
 */
public sealed interface SmaxAbPropsGetExperimentConfigResponse extends SmaxOperation.Response
        permits SmaxAbPropsGetExperimentConfigResponse.Success, SmaxAbPropsGetExperimentConfigResponse.ClientError, SmaxAbPropsGetExperimentConfigResponse.ServerError {

    /**
     * Tries each {@link SmaxAbPropsGetExperimentConfigResponse}
     * variant in priority order.
     *
     * @apiNote
     * Models {@code sendGetExperimentConfigRPC}'s post-await
     * disjunction: {@link Success} first, then {@link ClientError},
     * then {@link ServerError}; embedders pass the inbound stanza
     * and the original request to disambiguate the {@code id}
     * correlation.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when no
     * variant parses cleanly; WA Web throws a
     * {@code SmaxParsingFailure} so the upstream
     * {@code WAGetAbPropsProtocol} promise rejects through its
     * warn-and-return-error tail. Cobalt's dispatcher surfaces the
     * empty Optional through the configurable error handler.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxAbPropsGetExperimentConfigRPC",
            exports = "sendGetExperimentConfigRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxAbPropsGetExperimentConfigResponse> of(Node node, Node request) {
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
     * The success reply variant carrying the materialised props
     * bundle.
     *
     * @apiNote
     * Surfaced when the relay returns the {@code <props/>} subtree;
     * {@code WAWebAbPropsSyncJob} flushes the parsed
     * {@code (hash, refresh, refreshId, props)} tuple to
     * {@code WAWebABPropsLocalStorage} and resets the next-sync
     * timer.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsIQResultResponseMixin")
    final class Success implements SmaxAbPropsGetExperimentConfigResponse {
        /**
         * The relay-returned content hash.
         *
         * @apiNote
         * Echoed back on the next conditional fetch so the relay can
         * short-circuit to a delta.
         */
        private final String propsHash;

        /**
         * The relay-returned refresh-cooldown hint, in seconds.
         *
         * @apiNote
         * Drives the WA Web next-sync timer; embedders schedule the
         * next {@link SmaxAbPropsGetExperimentConfigRequest} dispatch
         * accordingly.
         */
        private final Integer propsRefresh;

        /**
         * The relay-returned refresh id.
         *
         * @apiNote
         * Echoed back on the next refresh under the {@code 3330}
         * gate so the relay can correlate the fetch with a prior
         * server-pushed bump.
         */
        private final Integer propsRefreshId;

        /**
         * The relay-returned A/B framework key.
         *
         * @apiNote
         * Identifies the A/B framework variant the relay assigned to
         * this client; surfaced in downstream WAM tagging.
         */
        private final String propsAbKey;

        /**
         * The raw {@code <props/>} subtree.
         *
         * @apiNote
         * Carries the per-experiment configuration entries; consumers
         * re-parse it through the dedicated
         * {@code WAWebABPropsParseConfigValue} pipeline to materialise
         * the {@code newProps} / {@code samplingConfigs} tuple
         * {@code WAGetAbPropsProtocol} returns to
         * {@code WAWebAbPropsSyncJob}.
         */
        private final Node propsNode;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the IQ-result mixin
         * validation passes and the {@code <props/>} child is found.
         *
         * @param propsHash       the content hash; may be
         *                        {@code null}
         * @param propsRefresh    the refresh-cooldown; may be
         *                        {@code null}
         * @param propsRefreshId  the refresh id; may be {@code null}
         * @param propsAbKey      the A/B framework key; may be
         *                        {@code null}
         * @param propsNode       the {@code <props/>} subtree; never
         *                        {@code null}
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
         * Empty when the relay omitted it; otherwise echoed back on
         * the next fetch.
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
         * Empty when the relay omitted it; otherwise the suggested
         * delay before the next fetch, in seconds.
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
         * Empty when the relay omitted it; otherwise surfaced in
         * WAM tagging.
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
         * configuration entries.
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
         * {@code WASmaxInAbPropsGetExperimentConfigResponseSuccess.parseGetExperimentConfigResponseSuccess};
         * empty when the IQ-result mixin fails, when the
         * {@code <props/>} child is missing, or when the
         * {@code protocol} attribute is not {@code "1"}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseSuccess",
                exports = "parseGetExperimentConfigResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var props = node.getChild("props").orElse(null);
            if (props == null) {
                return Optional.empty();
            }
            if (!props.hasAttribute("protocol", "1")) {
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
            return "SmaxAbPropsGetExperimentConfigResponse.Success[propsHash=" + propsHash
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
     * failure and does not retry, matching
     * {@code WAGetAbPropsProtocol}'s warn-and-error tail.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseErrorNoRetry")
    final class ClientError implements SmaxAbPropsGetExperimentConfigResponse {
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
         * {@code WASmaxInAbPropsGetExperimentConfigResponseErrorNoRetry.parseGetExperimentConfigResponseErrorNoRetry};
         * empty when the inbound stanza is not a 4xx error envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseErrorNoRetry",
                exports = "parseGetExperimentConfigResponseErrorNoRetry",
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
            return "SmaxAbPropsGetExperimentConfigResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The server-error reply variant.
     *
     * @apiNote
     * Surfaced when the relay encountered a transient internal
     * failure; embedders re-issue the request after a backoff to
     * mirror the retry-on-server-error behaviour WA Web's sync job
     * inherits from its caller.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseErrorRetry")
    final class ServerError implements SmaxAbPropsGetExperimentConfigResponse {
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
         * {@code WASmaxInAbPropsGetExperimentConfigResponseErrorRetry.parseGetExperimentConfigResponseErrorRetry};
         * empty when the inbound stanza is not a 5xx error envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInAbPropsGetExperimentConfigResponseErrorRetry",
                exports = "parseGetExperimentConfigResponseErrorRetry",
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
            return "SmaxAbPropsGetExperimentConfigResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
