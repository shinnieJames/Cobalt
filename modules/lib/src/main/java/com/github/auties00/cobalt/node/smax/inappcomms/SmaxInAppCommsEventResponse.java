package com.github.auties00.cobalt.node.smax.inappcomms;

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
 * The closed family of inbound reply variants to a
 * {@link SmaxInAppCommsEventRequest}.
 *
 * @apiNote
 * Permits {@link Success} (the relay accepted the report) and
 * {@link Error} (the relay rejected or dropped the report); the WA Web
 * call sites in {@code WAWebJob*QuickPromotion} log the {@code Error}
 * variant but never retry, so embedders should treat both variants as
 * terminal.
 */
public sealed interface SmaxInAppCommsEventResponse extends SmaxOperation.Response
        permits SmaxInAppCommsEventResponse.Success, SmaxInAppCommsEventResponse.Error {

    /**
     * Tries each {@link SmaxInAppCommsEventResponse} variant in
     * priority order and returns the first that parses cleanly.
     *
     * @apiNote
     * Mirrors WA Web's {@code WASmaxInAppCommsEventRPC.sendEventRPC}
     * dispatcher: {@link Success} is tried first; on miss the
     * {@link Error} variant absorbs both the {@code 408} client-range
     * timeout and the {@code 500}/{@code 503} server-range rejections.
     *
     * @param node the inbound IQ stanza received from the relay;
     *             never {@code null}
     * @param request the original outbound stanza used to validate
     *                echoed identifiers; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or
     *         empty when no documented variant matched the stanza
     *         shape
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInAppCommsEventRPC",
            exports = "sendEventRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxInAppCommsEventResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return Error.of(node, request);
    }

    /**
     * The {@code Success} reply variant; the relay accepted the event
     * report.
     *
     * @apiNote
     * Carries no payload beyond the {@code <iq type="result">}
     * envelope echo; the {@code <event>} body the request supplied is
     * not echoed back.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInInAppCommsEventResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInInAppCommsIQResultResponseMixin")
    final class Success implements SmaxInAppCommsEventResponse {
        /**
         * Constructs a new successful reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after the envelope shape has
         * been validated; embedders typically do not instantiate this
         * directly.
         */
        public Success() {
        }

        /**
         * Parses a {@link Success} variant from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the stanza is not an
         * {@code <iq type="result">} that echoes the original
         * request's {@code id} and {@code to}.
         *
         * @implNote
         * This implementation delegates the envelope shape check to
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)}; the
         * mixin folds the {@code id}/{@code from}/{@code type} echo
         * predicates into a single call so each per-RPC parser can
         * stay one line.
         *
         * @param node the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInInAppCommsEventResponseSuccess",
                exports = "parseEventResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * Returns whether the given object is also a {@link Success}.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when {@code obj} is a {@link Success}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns a constant hash code; the {@link Success} variant
         * carries no fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * Returns a debug-friendly textual representation of this
         * variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxInAppCommsEventResponse.Success[]";
        }
    }

    /**
     * The {@code Error} reply variant; the relay rejected or dropped
     * the event report.
     *
     * @apiNote
     * The InAppComms domain documents three reply codes:
     * {@code 408 request-timeout} (the relay's client-range timeout
     * envelope), {@code 500 internal-server-error}, and
     * {@code 503 service-unavailable} (both server-range envelopes).
     * Cobalt collapses all three into the single
     * {@code (errorCode, errorText)} pair; embedders that need
     * code-specific retry policy switch on {@link #errorCode()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInInAppCommsEventResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInInAppCommsEventErrorTypes")
    final class Error implements SmaxInAppCommsEventResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The human-readable error text supplied by the relay, when
         * present.
         */
        private final String errorText;

        /**
         * Constructs a new error reply.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional human-readable text; may be
         *                  {@code null}
         */
        public Error(int errorCode, String errorText) {
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
         * Parses an {@link Error} variant from the given inbound
         * stanza.
         *
         * @apiNote
         * Tries the server-range error envelope first; on miss
         * accepts a client-range envelope only when its code is
         * {@code 408} so unrelated {@code 4xx} replies are not
         * silently absorbed.
         *
         * @implNote
         * This implementation reuses
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}
         * and
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * rather than duplicating the envelope-shape predicate; the
         * priority ordering matches WA Web's combined "Error"
         * disjunction in {@code WASmaxInInAppCommsEventResponseError}.
         *
         * @param node the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInInAppCommsEventResponseError",
                exports = "parseEventResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Error> of(Node node, Node request) {
            var serverEnvelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (serverEnvelope != null) {
                return Optional.of(new Error(serverEnvelope.code(), serverEnvelope.text()));
            }
            var clientEnvelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (clientEnvelope != null && clientEnvelope.code() == 408) {
                return Optional.of(new Error(clientEnvelope.code(), clientEnvelope.text()));
            }
            return Optional.empty();
        }

        /**
         * Returns whether the given object is an {@link Error} with
         * an equal code and text.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both code and text match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Error) obj;
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        /**
         * Returns a hash code derived from the code and text.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug-friendly textual representation of this
         * variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxInAppCommsEventResponse.Error[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
