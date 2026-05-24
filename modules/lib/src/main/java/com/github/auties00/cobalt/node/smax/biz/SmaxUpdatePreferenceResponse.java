package com.github.auties00.cobalt.node.smax.biz;

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
 * The sealed family of inbound reply variants produced by the relay
 * in response to a {@link SmaxUpdatePreferenceRequest}.
 *
 * @apiNote
 * Surfaced by the biz user-feedback preference flow whose JS caller
 * {@code WAWebBizUpdatePreferenceJob.updateUserPreferenceFeedback}
 * records a per-contact feedback action (block, unblock, allow,
 * report) on a business chat; the three variants split the wire
 * outcome into {@link Success} (relay accepted the write),
 * {@link ClientError} (relay returned an
 * {@code UpdatePreferenceResponseInvalidRequest} envelope; the JS
 * caller logs the {@code (code, text)} pair via {@code WALogger.WARN}
 * and returns it to the UI) and {@link ServerError} (relay returned
 * an {@code UpdatePreferenceResponseServerError} envelope, handled
 * the same way).
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code WASmaxBizMsgUserFeedbackUpdatePreferenceRPC.sendUpdatePreferenceRPC}
 * by trying each variant in priority order via {@link #of} and
 * returning the first successful parse.
 */
public sealed interface SmaxUpdatePreferenceResponse extends SmaxOperation.Response
        permits SmaxUpdatePreferenceResponse.Success, SmaxUpdatePreferenceResponse.ClientError, SmaxUpdatePreferenceResponse.ServerError {

    /**
     * Tries each {@link SmaxUpdatePreferenceResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Invoked by the smax reply pump after dispatching a
     * {@link SmaxUpdatePreferenceRequest}; the priority order matches
     * WA Web's {@code parsing} dispatch table
     * ({@code Success}/{@code InvalidRequest}/{@code ServerError}) so
     * that a malformed {@code Success} stanza falls through to
     * {@link ClientError} rather than masking an error.
     *
     * @implNote
     * This implementation invokes {@link Success#of(Node, Node)}
     * first, then {@link ClientError#of(Node, Node)}, then
     * {@link ServerError#of(Node, Node)}; an unrecognised stanza
     * shape returns {@link Optional#empty()}.
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
    @WhatsAppWebExport(moduleName = "WASmaxBizMsgUserFeedbackUpdatePreferenceRPC",
            exports = "sendUpdatePreferenceRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxUpdatePreferenceResponse> of(Node node, Node request) {
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
     * The {@code Success} reply variant signalling that the relay
     * accepted the user-feedback preference write.
     *
     * @apiNote
     * Projected by {@link SmaxUpdatePreferenceResponse#of(Node, Node)}
     * when the relay returns the documented bare {@code <iq
     * type="result"/>} envelope; carries no payload because
     * {@code parseUpdatePreferenceResponseSuccess} simply checks the
     * IQ result envelope and propagates no fields. The WA Web caller
     * {@code updateUserPreferenceFeedback} silently treats this
     * branch as the no-error outcome.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseSuccess")
    final class Success implements SmaxUpdatePreferenceResponse {
        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the IQ result
         * envelope has been validated; takes no arguments because
         * the wire form carries no projected payload.
         */
        public Success() {
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation enforces the
         * {@link SmaxIqResultResponseMixin} envelope check (asserts
         * the {@code iq} tag and routes through
         * {@code parseIQResultResponseMixin}) and produces a payload
         * free instance on success.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         success schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseSuccess",
                exports = "parseUpdatePreferenceResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "SmaxUpdatePreferenceResponse.Success[]";
        }
    }

    /**
     * The {@code ClientError} reply variant carrying a documented
     * {@code 4xx} preference-update rejection drawn from the
     * {@code WASmaxInBizMsgUserFeedbackUpdatePreferenceReqErrors}
     * catalogue.
     *
     * @apiNote
     * Surfaced when the relay rejected the request as malformed,
     * unauthorised, rate-limited, or not-acceptable for the active
     * user; WA Web's
     * {@code WAWebBizUpdatePreferenceJob.updateUserPreferenceFeedback}
     * unwraps {@code errorUpdatePreferenceReqErrors} and returns the
     * {@code (errorCode, errorText)} pair to the UI rather than
     * retrying.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseInvalidRequest")
    @WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceReqErrors")
    final class ClientError implements SmaxUpdatePreferenceResponse {
        /**
         * The numeric server-side error code in the {@code 4xx}
         * range.
         */
        private final int errorCode;

        /**
         * The human-readable error text, when the relay supplied
         * one.
         */
        private final String errorText;

        /**
         * Constructs a new client-error reply.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the
         * {@code 4xx} envelope has been validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may
         *                  be {@code null}
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
         * Returns the optional human-readable error text.
         *
         * @return an {@link Optional} carrying the error text, or
         *         empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation routes the {@code <iq>}/{@code <error>}
         * extraction through
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * and admits the full {@code 4xx} range as a catch-all,
         * matching WA Web's
         * {@code parseUpdatePreferenceReqErrors} disjunction over the
         * {@code bad-request}/{@code forbidden}/{@code resource-limit}/{@code not-acceptable}
         * mixins.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         client-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseInvalidRequest",
                exports = "parseUpdatePreferenceResponseInvalidRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
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
            return "SmaxUpdatePreferenceResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The {@code ServerError} reply variant carrying a transient
     * {@code 5xx} relay failure drawn from the
     * {@code WASmaxInBizMsgUserFeedbackUpdatePreferenceServerErrors}
     * catalogue.
     *
     * @apiNote
     * Surfaced when the relay returned a transient internal failure
     * while processing the preference write; the caller can re-issue
     * the request with backoff.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseServerError")
    @WhatsAppWebModule(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceServerErrors")
    final class ServerError implements SmaxUpdatePreferenceResponse {
        /**
         * The numeric server-side error code in the {@code 5xx}
         * range.
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
         * @param errorText the optional human-readable text; may
         *                  be {@code null}
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
         * Tries to parse a {@link ServerError} variant from the given
         * inbound stanza.
         *
         * @implNote
         * This implementation delegates the {@code 5xx} range check
         * to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * any stanza outside the {@code 5xx} range yields
         * {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant,
         *         or empty when the stanza does not match the
         *         server-error schema
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBizMsgUserFeedbackUpdatePreferenceResponseServerError",
                exports = "parseUpdatePreferenceResponseServerError",
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
            return "SmaxUpdatePreferenceResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
