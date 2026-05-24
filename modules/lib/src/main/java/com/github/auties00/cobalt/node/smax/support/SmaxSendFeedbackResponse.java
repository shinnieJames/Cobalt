package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound replies to {@link SmaxSendFeedbackRequest}, modelling the three
 * outcomes WA Web's {@code WASmaxSupportMessageFeedbackSendFeedbackRPC.sendSendFeedbackRPC}
 * switches between: accepted feedback, malformed/rate-limited rejection, and transient internal
 * failure.
 *
 * @apiNote
 * Drives the support-bot message-rating submit-result UI consumed by WA Web's
 * {@code WAWebSendSupportBotFeedbackActions}; a {@link Success} clears the rating dialog,
 * while {@link ClientError} re-renders the form and {@link ServerError} schedules a retry.
 *
 * @implNote
 * This implementation splits WA Web's single
 * {@code WASmaxInSupportMessageFeedbackSendFeedbackResponseError} parser into Cobalt's
 * {@link ClientError} (4xx) and {@link ServerError} (5xx) so callers can dispatch on the
 * outcome without re-inspecting the code; parser order is success first, then client error,
 * then server error.
 */
public sealed interface SmaxSendFeedbackResponse extends SmaxOperation.Response
        permits SmaxSendFeedbackResponse.Success, SmaxSendFeedbackResponse.ClientError, SmaxSendFeedbackResponse.ServerError {

    /**
     * Parses the inbound feedback reply against each {@link SmaxSendFeedbackResponse} variant
     * and returns the first that matches.
     *
     * @apiNote
     * Use after the relay's IQ arrives in response to a {@link SmaxSendFeedbackRequest}; an
     * empty {@link Optional} means the inbound stanza did not fit any of the three documented
     * shapes.
     *
     * @implNote
     * This implementation collapses WA Web's two sequential parser calls into a three-step
     * short-circuit chain (success, client error, server error); no parse exception is raised
     * on total miss.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the originating outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxSupportMessageFeedbackSendFeedbackRPC",
            exports = "sendSendFeedbackRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxSendFeedbackResponse> of(Node node, Node request) {
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
     * Accepted-feedback variant carrying the literal {@code "Success"} status echoed by the
     * relay.
     *
     * @apiNote
     * Surfaces the relay's "feedback recorded" acknowledgement; the caller clears the rating
     * dialog and applies any local UI confirmation.
     *
     * @implNote
     * This implementation validates the {@code <result status="Success"/>} child shape; the
     * carried {@link #resultStatus()} is constant for parity with WA Web's parser output.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseSuccess")
    final class Success implements SmaxSendFeedbackResponse {
        /**
         * The {@code status} attribute of the {@code <result/>} child, always the literal
         * {@code "Success"}.
         *
         * @apiNote
         * Carried as a constant for parity with WA Web's payload shape.
         */
        private final String resultStatus;

        /**
         * Constructs an accepted-feedback reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <result status="Success"/>}
         * envelope validated.
         *
         * @param resultStatus the status; never {@code null}
         * @throws NullPointerException if {@code resultStatus} is {@code null}
         */
        public Success(String resultStatus) {
            this.resultStatus = Objects.requireNonNull(resultStatus, "resultStatus cannot be null");
        }

        /**
         * Returns the result status.
         *
         * @apiNote
         * Always {@code "Success"} for this variant; surfaced verbatim for parity with WA Web.
         *
         * @return the status; never {@code null}
         */
        public String resultStatus() {
            return resultStatus;
        }

        /**
         * Tries to parse an inbound stanza as a {@link Success}.
         *
         * @apiNote
         * Returns empty when the IQ envelope does not match a result for {@code request} or
         * the {@code <result/>} child is missing or carries a non-{@code "Success"} status.
         *
         * @implNote
         * This implementation validates the IQ envelope (description, type, matching id), then
         * descends into {@code <result/>} and asserts {@code status="Success"} before
         * surfacing the variant.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseSuccess",
                exports = "parseSendFeedbackResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null || !node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            var resultChild = node.getChild("result").orElse(null);
            if (resultChild == null) {
                return Optional.empty();
            }
            var status = resultChild.getAttributeAsString("status").orElse(null);
            if (!"Success".equals(status)) {
                return Optional.empty();
            }
            return Optional.of(new Success(status));
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
            return Objects.equals(this.resultStatus, that.resultStatus);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resultStatus);
        }

        @Override
        public String toString() {
            return "SmaxSendFeedbackResponse.Success[resultStatus=" + resultStatus + ']';
        }
    }

    /**
     * Client-error variant carrying a 4xx code/text pair (typically {@code 400 bad-request} or
     * {@code 429 rate-overlimit}).
     *
     * @apiNote
     * Surfaces a non-retryable rejection that the UI is expected to render as an error banner
     * and re-open the rating form; the {@code 429} case can be retried after back-off.
     *
     * @implNote
     * This implementation delegates 4xx envelope extraction to
     * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}; the documented codes are
     * {@code 400} and {@code 429} but the parser does not enforce the disjunction.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseError")
    final class ClientError implements SmaxSendFeedbackResponse {
        /**
         * The numeric error code from the {@code <error/>} envelope ({@code 400} or
         * {@code 429}).
         *
         * @apiNote
         * Surfaces the relay's classification of the rejection.
         */
        private final int errorCode;

        /**
         * The optional error text ({@code "bad-request"} or {@code "rate-overlimit"}).
         *
         * @apiNote
         * Surfaces the paired text from {@code <error text="..."/>}; {@code null} when the
         * envelope omitted it.
         */
        private final String errorText;

        /**
         * Constructs a client-error reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the 4xx envelope validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Surfaces the relay's 4xx classification.
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
         * Empty when the {@code <error/>} envelope omitted the {@code text} attribute.
         *
         * @return an {@link Optional} carrying the error text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse an inbound stanza as a {@link ClientError}.
         *
         * @apiNote
         * Returns empty when the inbound stanza is not a 4xx error reply to {@code request}.
         *
         * @implNote
         * This implementation delegates IQ-envelope and {@code <error/>} extraction to
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseError",
                exports = "parseSendFeedbackResponseError",
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxSendFeedbackResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * Server-error variant carrying a 5xx code/text pair (typically
     * {@code 500 internal-server-error}).
     *
     * @apiNote
     * Surfaces a transient internal failure; the caller is expected to schedule a retry with
     * back-off rather than re-open the form.
     *
     * @implNote
     * This implementation delegates 5xx envelope extraction to
     * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseError")
    final class ServerError implements SmaxSendFeedbackResponse {
        /**
         * The numeric error code from the {@code <error/>} envelope (typically {@code 500}).
         *
         * @apiNote
         * Surfaces the relay's 5xx classification.
         */
        private final int errorCode;

        /**
         * The optional error text (typically {@code "internal-server-error"}).
         *
         * @apiNote
         * Surfaces the paired text from {@code <error text="..."/>}; {@code null} when the
         * envelope omitted it.
         */
        private final String errorText;

        /**
         * Constructs a server-error reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the 5xx envelope validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Surfaces the relay's 5xx classification.
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
         * Empty when the {@code <error/>} envelope omitted the {@code text} attribute.
         *
         * @return an {@link Optional} carrying the error text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse an inbound stanza as a {@link ServerError}.
         *
         * @apiNote
         * Returns empty when the inbound stanza is not a 5xx error reply to {@code request}.
         *
         * @implNote
         * This implementation delegates IQ-envelope and {@code <error/>} extraction to
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportMessageFeedbackSendFeedbackResponseError",
                exports = "parseSendFeedbackResponseError",
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
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxSendFeedbackResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
