package com.github.auties00.cobalt.node.smax.bugreporting;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound replies to a
 * {@link SmaxBugReportingReportBugRequest}.
 *
 * @apiNote
 * Mirrors the two-arm disjunction the WA Web RPC
 * {@code WASmaxBugReportingReportBugRPC.sendReportBugRPC} returns: a
 * {@link Success} carrying the backend-assigned task id or an
 * {@link Error} carrying the relay's bad-request / internal-server-error
 * verdict; Cobalt embedders pattern-match on the variant to decide
 * whether to surface a confirmation toast or a retry-eligible failure.
 */
public sealed interface SmaxBugReportingReportBugResponse extends SmaxOperation.Response
        permits SmaxBugReportingReportBugResponse.Success, SmaxBugReportingReportBugResponse.Error {

    /**
     * Tries each {@link SmaxBugReportingReportBugResponse} variant in
     * the WA Web declared order.
     *
     * @apiNote
     * Models {@code sendReportBugRPC}'s post-{@code sendSmaxStanza}
     * disjunction: {@link Success} first, then {@link Error}; embedders
     * pass the awaited {@code <iq result/>} stanza along with the
     * original request and pattern-match on the returned variant.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when neither
     * variant parses cleanly; WA Web instead throws a
     * {@code SmaxParsingFailure} so the caller's promise rejects.
     * Cobalt's dispatch layer surfaces the empty Optional through the
     * configurable error handler instead of hardcoding the throw.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxBugReportingReportBugRPC",
            exports = "sendReportBugRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxBugReportingReportBugResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return Error.of(node, request);
    }

    /**
     * The success reply variant carrying the backend-assigned task id.
     *
     * @apiNote
     * Surfaced by {@link #of(Node, Node)} when the relay accepted the
     * report; embedders can join the {@link #taskIdElementValue} back to
     * their telemetry through
     * {@code WAWebSupportBugReportSubmitMutation.resolveSmaxReportId}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingReportBugResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingHackBaseIQResultResponseMixin")
    final class Success implements SmaxBugReportingReportBugResponse {
        /**
         * The optional {@code to} attribute echoed back by the relay.
         *
         * @apiNote
         * Present when the original request stamped {@code from} with a
         * user JID, so the relay echoes that JID into the reply's
         * {@code to} attribute.
         */
        private final Jid replyTo;

        /**
         * The backend-side task id assigned to the report.
         *
         * @apiNote
         * Returned by the bug-tracking backend; the
         * {@code resolveSmaxReportId} helper splits it into a
         * {@code reportType} / {@code reportId} pair when surfacing it
         * in the UI.
         */
        private final String taskIdElementValue;

        /**
         * Constructs a new success projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} after the inbound stanza
         * passes the IQ-result mixin validation.
         *
         * @param replyTo            the optional reply-to JID; may be
         *                           {@code null}
         * @param taskIdElementValue the task id; never {@code null}
         * @throws NullPointerException if {@code taskIdElementValue} is
         *                              {@code null}
         */
        public Success(Jid replyTo, String taskIdElementValue) {
            this.replyTo = replyTo;
            this.taskIdElementValue = Objects.requireNonNull(taskIdElementValue,
                    "taskIdElementValue cannot be null");
        }

        /**
         * Returns the optional reply-to JID.
         *
         * @apiNote
         * Empty when the original request did not stamp {@code from},
         * so the relay had nothing to echo back.
         *
         * @return an {@link Optional} carrying the JID
         */
        public Optional<Jid> replyTo() {
            return Optional.ofNullable(replyTo);
        }

        /**
         * Returns the backend-side task id.
         *
         * @apiNote
         * Embedders surface this id in the confirmation UI and route
         * it through {@code resolveSmaxReportId} when correlating with
         * server logs.
         *
         * @return the task id; never {@code null}
         */
        public String taskIdElementValue() {
            return taskIdElementValue;
        }

        /**
         * Tries to parse a {@link Success} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Empty when the stanza is not an IQ result, when the
         * {@code <task_id/>} child is missing, or when its content
         * cannot be decoded as a string; mirrors
         * {@code WASmaxInBugReportingReportBugResponseSuccess.parseReportBugResponseSuccess}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBugReportingReportBugResponseSuccess",
                exports = "parseReportBugResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var taskIdNode = node.getChild("task_id").orElse(null);
            if (taskIdNode == null) {
                return Optional.empty();
            }
            var taskId = taskIdNode.toContentString().orElse(null);
            if (taskId == null) {
                return Optional.empty();
            }
            var replyTo = node.getAttributeAsJid("to").orElse(null);
            return Optional.of(new Success(replyTo, taskId));
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
            return Objects.equals(this.replyTo, that.replyTo)
                    && Objects.equals(this.taskIdElementValue, that.taskIdElementValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(replyTo, taskIdElementValue);
        }

        @Override
        public String toString() {
            return "SmaxBugReportingReportBugResponse.Success[replyTo=" + replyTo
                    + ", taskIdElementValue=" + taskIdElementValue + ']';
        }
    }

    /**
     * The error reply variant carrying the relay's rejection code.
     *
     * @apiNote
     * Surfaced by {@link #of(Node, Node)} when the relay rejected the
     * report; the only two documented disjuncts are
     * {@code 400 bad-request} (client should not retry) and
     * {@code 500 internal-server-error} (transient, retry-eligible),
     * enumerated by
     * {@code WASmaxInBugReportingReportBugErrors.parseReportBugErrors}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingReportBugResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingHackBaseIQErrorResponseMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingReportBugErrors")
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingIQErrorBadRequestMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInBugReportingIQErrorInternalServerErrorMixin")
    final class Error implements SmaxBugReportingReportBugResponse {
        /**
         * The optional {@code to} attribute echoed back by the relay.
         */
        private final Jid replyTo;

        /**
         * The numeric error code; one of {@code 400} or {@code 500}.
         */
        private final int errorCode;

        /**
         * The error text paired with {@link #errorCode}; one of
         * {@code "bad-request"} or {@code "internal-server-error"}.
         */
        private final String errorText;

        /**
         * Constructs a new error projection.
         *
         * @apiNote
         * Called by {@link #of(Node, Node)} once the
         * {@link SmaxBaseServerErrorMixin} envelope has been classified
         * against the documented {@code (400, "bad-request")} /
         * {@code (500, "internal-server-error")} pairs.
         *
         * @param replyTo   the optional reply-to JID; may be
         *                  {@code null}
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public Error(Jid replyTo, int errorCode, String errorText) {
            this.replyTo = replyTo;
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the optional reply-to JID.
         *
         * @apiNote
         * Empty when the original request did not stamp {@code from},
         * so the relay had nothing to echo back.
         *
         * @return an {@link Optional} carrying the JID
         */
        public Optional<Jid> replyTo() {
            return Optional.ofNullable(replyTo);
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * One of {@code 400} (do-not-retry) or {@code 500}
         * (retry-eligible); used by embedders to decide whether to
         * resurface the dialog with the original payload pre-filled.
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
         * Empty only when the relay omitted the text; the
         * disjunction-validation in {@link #of(Node, Node)} normally
         * guarantees it is present and paired with {@link #errorCode}.
         *
         * @return an {@link Optional} carrying the text
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse an {@link Error} variant from the given
         * inbound stanza.
         *
         * @apiNote
         * Tries the client-error envelope first (4xx) then the
         * server-error envelope (5xx) and verifies the resulting
         * code/text pair against the documented disjunction; mirrors
         * {@code parseReportBugResponseError} composed with
         * {@code parseReportBugErrors}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInBugReportingReportBugResponseError",
                exports = "parseReportBugResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WASmaxInBugReportingReportBugErrors",
                exports = "parseReportBugErrors",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Error> of(Node node, Node request) {
            var clientEnvelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            var serverEnvelope = clientEnvelope == null
                    ? SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null)
                    : null;
            var envelope = clientEnvelope != null ? clientEnvelope : serverEnvelope;
            if (envelope == null) {
                return Optional.empty();
            }
            var code = envelope.code();
            var text = envelope.text();
            if (code == 400 && "bad-request".equals(text)) {
                // IQErrorBadRequestMixin
            } else if (code == 500 && "internal-server-error".equals(text)) {
                // IQErrorInternalServerErrorMixin
            } else {
                return Optional.empty();
            }
            var replyTo = node.getAttributeAsJid("to").orElse(null);
            return Optional.of(new Error(replyTo, code, text));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Error) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.replyTo, that.replyTo)
                    && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(replyTo, errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxBugReportingReportBugResponse.Error[replyTo=" + replyTo
                    + ", errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
