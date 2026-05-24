package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound reply variants produced by the relay
 * in response to a {@link SmaxGetAccountNonceRequest}.
 *
 * @apiNote
 * Each variant projects a distinct outcome of the SMB
 * business-linking nonce bridge: {@link Success} carries the issued
 * nonce that the CTWA ad-creation flow forwards to Meta's ads
 * backend, {@link ClientError} carries the documented {@code 400}
 * bad-request or {@code 475} notice-required rejections, and
 * {@link ServerError} carries a transient {@code 5xx} relay
 * failure.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WASmaxBizLinkingGetAccountNonceRPC.sendGetAccountNonceRPC}
 * by trying each variant in priority order via {@link #of} and
 * returning the first successful parse.
 */
public sealed interface SmaxGetAccountNonceResponse extends SmaxOperation.Response
        permits SmaxGetAccountNonceResponse.Success, SmaxGetAccountNonceResponse.ClientError, SmaxGetAccountNonceResponse.ServerError {

    /**
     * Tries each {@link SmaxGetAccountNonceResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Invoked by the smax reply pump after dispatching a
     * {@link SmaxGetAccountNonceRequest}; the priority order matches
     * WA Web's {@code parsing} dispatch table so that a malformed
     * {@code Success} stanza falls through to {@link ClientError}
     * rather than masking an error.
     *
     * @implNote
     * This implementation invokes {@link Success#of(Node, Node)}
     * first, then {@link ClientError#of(Node, Node)}, then
     * {@link ServerError#of(Node, Node)}; an unrecognised stanza
     * shape returns {@link Optional#empty()}, matching WA Web's
     * {@code errorMessageRpcParsing} failure fallthrough.
     *
     * @param node    the inbound IQ stanza received from the relay;
     *                never {@code null}
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} when no documented variant
     *         matched the stanza shape
     * @throws NullPointerException if either argument is
     *                              {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBizLinkingGetAccountNonceRPC",
            exports = "sendGetAccountNonceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGetAccountNonceResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant carrying the freshly-issued
     * account-binding nonce.
     *
     * @apiNote
     * Projected by {@link SmaxGetAccountNonceResponse#of(Node, Node)}
     * when the relay returns the documented {@code <detail><nonce>}
     * tree; the nonce is the value consumed by
     * {@code WAWebQueryLinkedAccountNonceJob.queryNonce} as
     * {@code detailNonceElementValue}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseSuccess")
    final class Success implements SmaxGetAccountNonceResponse {
        /**
         * The element-content of the {@code <nonce>} child of
         * {@code <detail>}; the freshly-issued account-binding
         * nonce.
         */
        private final String nonce;

        /**
         * The optional element-content of the
         * {@code <request><id/></request>} echo; {@code null} when
         * the relay omitted the {@code <request>} grandchild.
         */
        private final String requestId;

        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code <detail><nonce>} tree has been validated; clients
         * normally read the value back through {@link #nonce()}.
         *
         * @param nonce     the issued nonce; never {@code null}
         * @param requestId the optional request-id echo; may be
         *                  {@code null}
         * @throws NullPointerException if {@code nonce} is
         *                              {@code null}
         */
        public Success(String nonce, String requestId) {
            this.nonce = Objects.requireNonNull(nonce, "nonce cannot be null");
            this.requestId = requestId;
        }

        /**
         * Returns the issued nonce.
         *
         * @apiNote
         * Use as the {@code detailNonceElementValue} parameter when
         * forwarding the CTWA ad-creation handshake to Meta's ads
         * backend.
         *
         * @return the nonce; never {@code null}
         */
        public String nonce() {
            return nonce;
        }

        /**
         * Returns the optional request-id echo.
         *
         * @apiNote
         * Empty unless the relay returned a
         * {@code <request><id/></request>} grandchild; callers that
         * pipeline multiple nonce requests can use it to correlate
         * the reply with the original.
         *
         * @return an {@link Optional} carrying the id, or empty
         *         when the relay omitted the {@code <request>}
         *         grandchild
         */
        public Optional<String> requestId() {
            return Optional.ofNullable(requestId);
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Returns empty when the stanza is not an IQ result for the
         * original request, when the {@code <detail>} child is
         * absent, when the {@code <nonce>} grandchild is missing,
         * or when the optional {@code <request>} grandchild is
         * present but its inner {@code <id/>} is malformed.
         *
         * @implNote
         * This implementation enforces the
         * {@code SmaxIqResultResponseMixin} envelope check first,
         * then extracts the {@code <detail><nonce>} content as a
         * string; if the optional {@code <request>} grandchild is
         * present its {@code <id>} content must also resolve, and
         * any malformed sub-tree causes a fall-through to
         * {@link Optional#empty()} rather than an exception.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseSuccess",
                exports = "parseGetAccountNonceResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseSuccess",
                exports = "parseGetAccountNonceResponseSuccessDetailRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var detail = node.getChild("detail").orElse(null);
            if (detail == null) {
                return Optional.empty();
            }
            var nonceNode = detail.getChild("nonce").orElse(null);
            if (nonceNode == null) {
                return Optional.empty();
            }
            var nonce = nonceNode.toContentString().orElse(null);
            if (nonce == null) {
                return Optional.empty();
            }
            String requestId = null;
            var requestNode = detail.getChild("request").orElse(null);
            if (requestNode != null) {
                var idNode = requestNode.getChild("id").orElse(null);
                if (idNode == null) {
                    return Optional.empty();
                }
                requestId = idNode.toContentString().orElse(null);
                if (requestId == null) {
                    return Optional.empty();
                }
            }
            return Optional.of(new Success(nonce, requestId));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.nonce, that.nonce)
                    && Objects.equals(this.requestId, that.requestId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(nonce, requestId);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetAccountNonceResponse.Success[nonce=" + nonce
                    + ", requestId=" + requestId + ']';
        }
    }

    /**
     * The {@code ClientError} reply variant carrying a
     * documented {@code 4xx} rejection.
     *
     * @apiNote
     * Projects the two documented sub-variants of the
     * business-linking nonce bridge: {@code (400, "bad-request")}
     * for malformed payloads and {@code (475, "notice-required")}
     * when the calling business has not yet acknowledged a required
     * ToS update; the latter carries a {@code tos_version} surfaced
     * via {@link #tosVersion()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingAccountNonceErrors")
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingIQErrorNoticeRequiredMixin")
    final class ClientError implements SmaxGetAccountNonceResponse {
        /**
         * The numeric server-side error code; one of {@code 400} or
         * {@code 475}.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
         */
        private final String errorText;

        /**
         * The {@code tos_version} carried only by the
         * {@code notice-required} sub-variant ({@code 475}); may be
         * {@code null} for the {@code bad-request} arm.
         */
        private final Integer tosVersion;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the documented
         * {@code (code, text)} pair has been validated.
         *
         * @param errorCode  the numeric error code
         * @param errorText  the optional human-readable text; may
         *                   be {@code null}
         * @param tosVersion the optional {@code tos_version}; may
         *                   be {@code null} when the error is not
         *                   {@code notice-required}
         */
        public ClientError(int errorCode, String errorText, Integer tosVersion) {
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.tosVersion = tosVersion;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * One of {@code 400} or {@code 475}; callers branch on the
         * code to distinguish bad-request from notice-required.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text.
         *
         * @apiNote
         * Suitable for surfacing in diagnostic logs; not a stable
         * identifier for programmatic branching.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Returns the optional {@code tos_version}.
         *
         * @apiNote
         * Present only on the {@code (475, "notice-required")}
         * sub-variant; callers show the matching ToS-update prompt
         * to the user and re-issue the nonce request once the
         * acknowledgement has been recorded.
         *
         * @return an {@link Optional} carrying the version, or
         *         empty when the error is not
         *         {@code notice-required}
         */
        public Optional<Integer> tosVersion() {
            return Optional.ofNullable(tosVersion);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation routes the {@code <iq>}/{@code <error>}
         * extraction through
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)},
         * then validates the resulting {@code (code, text)} pair
         * against the documented disjunction
         * ({@code (400, "bad-request")} or
         * {@code (475, "notice-required")}); the notice-required
         * arm additionally projects the {@code tos_version}
         * attribute on the {@code <error/>} child through the
         * {@code [1, 65535]} range check before surfacing it.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseError",
                exports = "parseGetAccountNonceResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingAccountNonceErrors",
                exports = "parseAccountNonceErrors",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingIQErrorNoticeRequiredMixin",
                exports = "parseIQErrorNoticeRequiredMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            var code = envelope.code();
            var text = envelope.text();
            Integer tosVersion = null;
            if (code == 400 && "bad-request".equals(text)) {
                // IQErrorBadRequestMixin literal pair.
            } else if (code == 475 && "notice-required".equals(text)) {
                var errorChild = node.getChild("error").orElse(null);
                if (errorChild == null) {
                    return Optional.empty();
                }
                var tos = errorChild.getAttributeAsInt("tos_version");
                if (tos.isEmpty()) {
                    return Optional.empty();
                }
                var tosValue = tos.getAsInt();
                if (tosValue < 1 || tosValue > 65535) {
                    return Optional.empty();
                }
                tosVersion = tosValue;
            } else {
                return Optional.empty();
            }
            return Optional.of(new ClientError(code, text, tosVersion));
        }

        /**
         * {@inheritDoc}
         */
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
                    && Objects.equals(this.errorText, that.errorText)
                    && Objects.equals(this.tosVersion, that.tosVersion);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText, tosVersion);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetAccountNonceResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", tosVersion=" + tosVersion + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant carrying a transient
     * {@code 5xx} relay failure.
     *
     * @apiNote
     * Indicates the relay could not complete the nonce issuance for
     * an internal reason; the caller can re-issue the request with
     * backoff.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingAccountNonceErrors")
    @WhatsAppWebModule(moduleName = "WASmaxInBizLinkingIQErrorInternalServerErrorMixin")
    final class ServerError implements SmaxGetAccountNonceResponse {
        /**
         * The numeric server-side error code.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
         */
        private final String errorText;

        /**
         * Constructs a new server-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code 5xx} envelope has been validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from the
         * given inbound stanza.
         *
         * @implNote
         * This implementation delegates the {@code 5xx} range check
         * to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)},
         * which enforces the {@code IQErrorInternalServerErrorMixin}
         * envelope semantics; any other {@code (code, text)} pair
         * yields {@link Optional#empty()} and falls through to
         * the orchestrator's unrecognised-variant fallback.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingGetAccountNonceResponseError",
                exports = "parseGetAccountNonceResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingAccountNonceErrors",
                exports = "parseAccountNonceErrors", adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBizLinkingIQErrorInternalServerErrorMixin",
                exports = "parseIQErrorInternalServerErrorMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxGetAccountNonceResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
