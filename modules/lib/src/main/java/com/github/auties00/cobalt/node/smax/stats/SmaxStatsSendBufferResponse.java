package com.github.auties00.cobalt.node.smax.stats;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Models the closed family of inbound reply variants to a {@link SmaxStatsSendBufferRequest}.
 * <p>
 * Exactly one of three variants is permitted: {@link Success}, when the relay accepted the batch and
 * the local WAM buffer can be cleared; {@link ErrorNoRetry}, a permanent rejection that must not be
 * retried; and {@link ErrorRetry}, a transient {@code 503 service-unavailable} that requires
 * re-buffering the batch and retrying on the next flush window.
 */
public sealed interface SmaxStatsSendBufferResponse extends SmaxOperation.Response
        permits SmaxStatsSendBufferResponse.Success, SmaxStatsSendBufferResponse.ErrorNoRetry, SmaxStatsSendBufferResponse.ErrorRetry {

    /**
     * Parses the inbound stanza into the first matching {@link SmaxStatsSendBufferResponse} variant.
     * <p>
     * Each variant is tried in priority order: {@link Success} first, then {@link ErrorNoRetry} (the
     * {@code 400}/{@code 406}/{@code 501} disjunction), then {@link ErrorRetry} (the {@code 503}
     * fallback). The first variant that parses cleanly is returned, and an empty result indicates
     * that no documented variant matched the stanza.
     *
     * @param node the inbound IQ stanza; never {@code null}
     * @param request the original outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty when no documented variant
     *         matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxStatsSendBufferRPC",
            exports = "sendSendBufferRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxStatsSendBufferResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var noRetry = ErrorNoRetry.of(node, request);
        if (noRetry.isPresent()) {
            return noRetry;
        }
        return ErrorRetry.of(node, request);
    }

    /**
     * Represents the {@code Success} reply variant, signalling that the relay accepted the WAM batch.
     * <p>
     * The variant carries no payload beyond the {@code <iq type="result">} envelope echo; the
     * {@code <add>} body the request supplied is not echoed back. Observing this variant lets the
     * local WAM buffer be cleared.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInStatsSendBufferResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsIQResultResponseMixin")
    final class Success implements SmaxStatsSendBufferResponse {
        /**
         * Constructs a new successful reply.
         * <p>
         * Invoked by {@link #of(Node, Node)} after the envelope shape has been validated; embedders
         * typically do not instantiate this directly.
         */
        public Success() {
        }

        /**
         * Parses a {@link Success} variant from the given inbound stanza.
         * <p>
         * Returns {@link Optional#empty()} when the stanza is not an {@code <iq type="result">} that
         * echoes the original request's {@code id} and {@code to}.
         *
         * @implNote
         * This implementation delegates the envelope shape check to
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)}, which folds the
         * {@code id}/{@code from}/{@code type} echo predicates into a single call.
         *
         * @param node the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInStatsSendBufferResponseSuccess",
                exports = "parseSendBufferResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            return Optional.of(new Success());
        }

        /**
         * Compares this variant with the given object for equality.
         * <p>
         * Because {@link Success} carries no fields, any other {@link Success} instance is equal.
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
         * Returns a constant hash code; the {@link Success} variant carries no fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Success.class.hashCode();
        }

        /**
         * Returns a debug-friendly textual representation of this variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxStatsSendBufferResponse.Success[]";
        }
    }

    /**
     * Represents the {@code ErrorNoRetry} reply variant, a permanent rejection that the local client
     * must not retry.
     * <p>
     * The variant carries one of three documented {@code (code, text)} pairs:
     * {@code (400, "bad-request")}, {@code (406, "not-acceptable")}, or
     * {@code (501, "feature-not-implemented")}. The {@code 406} sub-shape additionally exposes a
     * {@code <field name reason/>} grandchild lifted into the {@code (fieldName, fieldReason)} pair
     * so the caller can surface the rejected field to its diagnostic log.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInStatsSendBufferResponseErrorNoRetry")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsSendBufferNoRetryError")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsIQErrorBadRequestMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsIQErrorNotAcceptableMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsIQErrorFeatureNotImplementedMixin")
    final class ErrorNoRetry implements SmaxStatsSendBufferResponse {
        /**
         * Holds the numeric error code; one of {@code 400}, {@code 406}, or {@code 501}.
         */
        private final int errorCode;

        /**
         * Holds the error text; one of {@code "bad-request"}, {@code "not-acceptable"}, or
         * {@code "feature-not-implemented"}.
         */
        private final String errorText;

        /**
         * Holds the optional {@code <field name="..."/>} grandchild surfaced by the
         * {@code 406 not-acceptable} sub-shape; {@code null} for the other two codes.
         */
        private final String fieldName;

        /**
         * Holds the optional {@code <field reason="..."/>} grandchild surfaced by the
         * {@code 406 not-acceptable} sub-shape; {@code null} for the other two codes.
         */
        private final String fieldReason;

        /**
         * Constructs a new no-retry error reply from a parsed envelope and its optional field
         * grandchild.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         * @param fieldName the optional field name; may be {@code null}
         * @param fieldReason the optional field reason; may be {@code null}
         */
        public ErrorNoRetry(int errorCode, String errorText, String fieldName, String fieldReason) {
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.fieldName = fieldName;
            this.fieldReason = fieldReason;
        }

        /**
         * Returns the numeric error code.
         *
         * @return the code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @return an {@link Optional} carrying the text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Returns the optional {@code field} name surfaced by the {@code 406 not-acceptable}
         * sub-shape.
         *
         * @return an {@link Optional} carrying the field name, or empty for the other two codes
         */
        public Optional<String> fieldName() {
            return Optional.ofNullable(fieldName);
        }

        /**
         * Returns the optional {@code field} reason surfaced by the {@code 406 not-acceptable}
         * sub-shape.
         *
         * @return an {@link Optional} carrying the field reason, or empty for the other two codes
         */
        public Optional<String> fieldReason() {
            return Optional.ofNullable(fieldReason);
        }

        /**
         * Parses an {@link ErrorNoRetry} variant from the given inbound stanza.
         * <p>
         * Returns {@link Optional#empty()} when the envelope does not match one of the three
         * documented {@code (code, text)} pairs, or when the {@code 406 not-acceptable} sub-shape
         * carries a {@code <field>} grandchild that is missing either {@code name} or {@code reason}.
         *
         * @implNote
         * This implementation routes a 4xx envelope through
         * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} and a 5xx envelope through
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)}; unknown codes fall through
         * to the {@link ErrorRetry} parser. The {@code <field>} grandchild is read by navigating
         * {@code error/field/} via {@link Node#getChild(String)} so a present-but-empty
         * {@code <field>} (one with neither attribute) is treated as malformed.
         *
         * @param node the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInStatsSendBufferResponseErrorNoRetry",
                exports = "parseSendBufferResponseErrorNoRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ErrorNoRetry> of(Node node, Node request) {
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
            String fieldName = null;
            String fieldReason = null;
            if (code == 400 && "bad-request".equals(text)) {
            } else if (code == 406 && "not-acceptable".equals(text)) {
                var errorChild = node.getChild("error").orElse(null);
                if (errorChild != null) {
                    var fieldNode = errorChild.getChild("field").orElse(null);
                    if (fieldNode != null) {
                        fieldName = fieldNode.getAttributeAsString("name").orElse(null);
                        fieldReason = fieldNode.getAttributeAsString("reason").orElse(null);
                        if (fieldName == null || fieldReason == null) {
                            return Optional.empty();
                        }
                    }
                }
            } else if (code == 501 && "feature-not-implemented".equals(text)) {
            } else {
                return Optional.empty();
            }
            return Optional.of(new ErrorNoRetry(code, text, fieldName, fieldReason));
        }

        /**
         * Compares this variant with the given object for equality.
         * <p>
         * Two instances are equal when both are {@link ErrorNoRetry} with the same code, text, field
         * name, and field reason.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when every field matches
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ErrorNoRetry) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText)
                    && Objects.equals(this.fieldName, that.fieldName)
                    && Objects.equals(this.fieldReason, that.fieldReason);
        }

        /**
         * Returns a hash code derived from every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText, fieldName, fieldReason);
        }

        /**
         * Returns a debug-friendly textual representation of this variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxStatsSendBufferResponse.ErrorNoRetry[errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", fieldName=" + fieldName
                    + ", fieldReason=" + fieldReason + ']';
        }
    }

    /**
     * Represents the {@code ErrorRetry} reply variant, a transient {@code 503 service-unavailable}
     * rejection.
     * <p>
     * The local client should re-buffer the WAM batch and retry on the next flush window. The variant
     * carries no payload because the {@code (503, "service-unavailable")} pair is implied by the type.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInStatsSendBufferResponseErrorRetry")
    @WhatsAppWebModule(moduleName = "WASmaxInStatsIQErrorServiceUnavailableMixin")
    final class ErrorRetry implements SmaxStatsSendBufferResponse {
        /**
         * Constructs a new retry-error reply.
         * <p>
         * Invoked by {@link #of(Node, Node)} after the envelope shape has been validated against the
         * {@code (503, "service-unavailable")} pair; embedders typically do not instantiate this
         * directly.
         */
        public ErrorRetry() {
        }

        /**
         * Returns the numeric error code; always {@code 503}.
         *
         * @return the code
         */
        public int errorCode() {
            return 503;
        }

        /**
         * Returns the error text; always {@code "service-unavailable"}.
         *
         * @return the text
         */
        public String errorText() {
            return "service-unavailable";
        }

        /**
         * Parses an {@link ErrorRetry} variant from the given inbound stanza.
         * <p>
         * Returns {@link Optional#empty()} when the envelope is not a server-range error or when its
         * code/text pair is not exactly {@code (503, "service-unavailable")}.
         *
         * @param node the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInStatsSendBufferResponseErrorRetry",
                exports = "parseSendBufferResponseErrorRetry",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ErrorRetry> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            if (envelope.code() != 503 || !"service-unavailable".equals(envelope.text())) {
                return Optional.empty();
            }
            return Optional.of(new ErrorRetry());
        }

        /**
         * Compares this variant with the given object for equality.
         * <p>
         * Because {@link ErrorRetry} carries no fields, any other {@link ErrorRetry} instance is
         * equal.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when {@code obj} is an {@link ErrorRetry}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns a constant hash code; the variant carries no fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return ErrorRetry.class.hashCode();
        }

        /**
         * Returns a debug-friendly textual representation of this variant.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxStatsSendBufferResponse.ErrorRetry[]";
        }
    }
}
