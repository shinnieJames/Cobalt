package com.github.auties00.cobalt.node.smax.support;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound replies to {@link SmaxContactFormRequest}, modelling the three
 * outcomes WA Web's {@code WASmaxSupportContactFormRPC.sendContactFormRPC} switches between:
 * accepted submission, retryable back-pressure, and final rejection.
 *
 * @apiNote
 * Drives the WhatsApp "Help" / "Contact us" submit-result UI; the user-facing acknowledgement
 * text, ticket id and routing group JID surface in {@link ContactFormResponseSuccess}, while a
 * non-zero error code surfaces in {@link ContactFormResponseRetryableError} or
 * {@link ContactFormResponseError} so the caller can re-render the form or banner an error.
 *
 * @implNote
 * This implementation matches WA Web's parser order: {@link ContactFormResponseSuccess} first,
 * then {@link ContactFormResponseRetryableError}, then {@link ContactFormResponseError}. The
 * three parsers are mutually exclusive so the first match wins; an inbound stanza that fails
 * all three returns an empty {@link Optional} instead of WA Web's {@code SmaxParsingFailure}
 * (Cobalt callers surface the parse miss themselves).
 */
public sealed interface SmaxContactFormResponse extends SmaxOperation.Response
        permits SmaxContactFormResponse.ContactFormResponseSuccess,
        SmaxContactFormResponse.ContactFormResponseRetryableError,
        SmaxContactFormResponse.ContactFormResponseError {

    /**
     * Parses the inbound contact-form reply against each {@link SmaxContactFormResponse} variant
     * and returns the first that matches.
     *
     * @apiNote
     * Use after the relay's IQ arrives in response to a {@link SmaxContactFormRequest}; an empty
     * {@link Optional} means the inbound stanza did not fit any of the three documented shapes.
     *
     * @implNote
     * This implementation collapses WA Web's three sequential
     * {@code parse*Success.success}/{@code parse*Error.success} checks into the
     * {@code ContactFormResponseSuccess} / {@code ContactFormResponseRetryableError} /
     * {@code ContactFormResponseError} short-circuit chain; no parse exception is raised on
     * total miss.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the originating outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxSupportContactFormRPC",
            exports = "sendContactFormRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxContactFormResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = ContactFormResponseSuccess.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        var retryableError = ContactFormResponseRetryableError.of(node, request);
        if (retryableError.isPresent()) {
            return retryableError;
        }
        return ContactFormResponseError.of(node, request);
    }

    /**
     * Accepted-submission variant carrying the acknowledgement text, ticket id and routing group
     * JID surfaced to the user.
     *
     * @apiNote
     * Drives the "ticket created" banner: {@link #responseMessageElementValue()} is the body,
     * {@link #responseTicketIdElementValue()} is the follow-up id, and
     * {@link #responseGroupJidElementValue()} names the support routing group.
     *
     * @implNote
     * This implementation mirrors the {@code WASmaxInSupportContactFormResponseSuccess} schema:
     * the outer {@code <iq type="result"/>} envelope's id must equal the originating request id,
     * and the {@code <response status="ok">} child must carry {@code <message/>},
     * {@code <ticket_id/>} and {@code <group_jid/>} text children.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportContactFormResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInSupportHackBaseIQResultResponseMixin")
    final class ContactFormResponseSuccess implements SmaxContactFormResponse {
        /**
         * The literal {@code "ok"} status echoed by the relay's {@code <response/>} child.
         *
         * @apiNote
         * Surfaces the response-envelope discriminator; carried as a constant for parity with
         * WA Web's parser output.
         */
        private final String responseStatus;

        /**
         * The acknowledgement message body shown to the user.
         *
         * @apiNote
         * The text of the {@code <message/>} child under {@code <response/>}.
         */
        private final String responseMessageElementValue;

        /**
         * The opaque ticket id assigned by the relay for follow-up correspondence.
         *
         * @apiNote
         * The text of the {@code <ticket_id/>} child under {@code <response/>}.
         */
        private final String responseTicketIdElementValue;

        /**
         * The routing group JID that the support backend assigned to the case.
         *
         * @apiNote
         * The text of the {@code <group_jid/>} child under {@code <response/>}; surfaces the
         * group thread the user can post follow-up messages to.
         */
        private final String responseGroupJidElementValue;

        /**
         * Constructs a successful-submission reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <response status="ok">} envelope
         * validated; manual construction is rarely useful outside tests.
         *
         * @param responseStatus               the response status; never {@code null}
         * @param responseMessageElementValue  the acknowledgement text; never {@code null}
         * @param responseTicketIdElementValue the ticket id; never {@code null}
         * @param responseGroupJidElementValue the routing group JID; never {@code null}
         * @throws NullPointerException if any argument is {@code null}
         */
        public ContactFormResponseSuccess(String responseStatus,
                                          String responseMessageElementValue,
                                          String responseTicketIdElementValue,
                                          String responseGroupJidElementValue) {
            this.responseStatus = Objects.requireNonNull(responseStatus, "responseStatus cannot be null");
            this.responseMessageElementValue = Objects.requireNonNull(responseMessageElementValue,
                    "responseMessageElementValue cannot be null");
            this.responseTicketIdElementValue = Objects.requireNonNull(responseTicketIdElementValue,
                    "responseTicketIdElementValue cannot be null");
            this.responseGroupJidElementValue = Objects.requireNonNull(responseGroupJidElementValue,
                    "responseGroupJidElementValue cannot be null");
        }

        /**
         * Returns the response status.
         *
         * @apiNote
         * Always {@code "ok"} for this variant; surfaced verbatim for parity with WA Web's
         * payload shape.
         *
         * @return the status; never {@code null}
         */
        public String responseStatus() {
            return responseStatus;
        }

        /**
         * Returns the acknowledgement text.
         *
         * @apiNote
         * The user-visible message rendered into the submit-success banner.
         *
         * @return the text; never {@code null}
         */
        public String responseMessageElementValue() {
            return responseMessageElementValue;
        }

        /**
         * Returns the ticket id.
         *
         * @apiNote
         * The opaque handle the support backend assigns; used for any subsequent follow-up
         * correspondence.
         *
         * @return the id; never {@code null}
         */
        public String responseTicketIdElementValue() {
            return responseTicketIdElementValue;
        }

        /**
         * Returns the routing group JID.
         *
         * @apiNote
         * Names the support group the user can post follow-up messages to.
         *
         * @return the JID; never {@code null}
         */
        public String responseGroupJidElementValue() {
            return responseGroupJidElementValue;
        }

        /**
         * Tries to parse an inbound stanza as a {@link ContactFormResponseSuccess}.
         *
         * @apiNote
         * Returns empty when any of the structural preconditions fail; use the parent
         * {@link SmaxContactFormResponse#of(Node, Node)} to walk all three variants.
         *
         * @implNote
         * This implementation validates the IQ envelope ({@code description="iq"},
         * {@code type="result"}, matching {@code id}) then descends through
         * {@code <response status="ok">} into {@code <message>}, {@code <ticket_id>} and
         * {@code <group_jid>}; any missing or empty child yields an empty {@link Optional}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportContactFormResponseSuccess",
                exports = "parseContactFormResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ContactFormResponseSuccess> of(Node node, Node request) {
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
            var responseChild = node.getChild("response").orElse(null);
            if (responseChild == null) {
                return Optional.empty();
            }
            if (!responseChild.hasAttribute("status", "ok")) {
                return Optional.empty();
            }
            var messageNode = responseChild.getChild("message").orElse(null);
            if (messageNode == null) {
                return Optional.empty();
            }
            var messageValue = messageNode.toContentString().orElse(null);
            if (messageValue == null) {
                return Optional.empty();
            }
            var ticketIdNode = responseChild.getChild("ticket_id").orElse(null);
            if (ticketIdNode == null) {
                return Optional.empty();
            }
            var ticketIdValue = ticketIdNode.toContentString().orElse(null);
            if (ticketIdValue == null) {
                return Optional.empty();
            }
            var groupJidNode = responseChild.getChild("group_jid").orElse(null);
            if (groupJidNode == null) {
                return Optional.empty();
            }
            var groupJidValue = groupJidNode.toContentString().orElse(null);
            if (groupJidValue == null) {
                return Optional.empty();
            }
            return Optional.of(new ContactFormResponseSuccess("ok", messageValue,
                    ticketIdValue, groupJidValue));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ContactFormResponseSuccess) obj;
            return Objects.equals(this.responseStatus, that.responseStatus)
                    && Objects.equals(this.responseMessageElementValue, that.responseMessageElementValue)
                    && Objects.equals(this.responseTicketIdElementValue, that.responseTicketIdElementValue)
                    && Objects.equals(this.responseGroupJidElementValue, that.responseGroupJidElementValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(responseStatus, responseMessageElementValue,
                    responseTicketIdElementValue, responseGroupJidElementValue);
        }

        @Override
        public String toString() {
            return "SmaxContactFormResponse.ContactFormResponseSuccess[responseStatus="
                    + responseStatus
                    + ", responseMessageElementValue=" + responseMessageElementValue
                    + ", responseTicketIdElementValue=" + responseTicketIdElementValue
                    + ", responseGroupJidElementValue=" + responseGroupJidElementValue + ']';
        }
    }

    /**
     * Retryable-back-pressure variant carrying a numeric error code and an optional retry-after
     * timestamp.
     *
     * @apiNote
     * Surfaces a transient relay refusal; the UI is expected to schedule a retry no earlier than
     * {@link #responseNextRetryTs()} (in epoch seconds) when present.
     *
     * @implNote
     * This implementation accepts any numeric {@code error_code} attribute on the
     * {@code <response>} child; WA Web does not validate the code value at parse time so neither
     * does this code path.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportContactFormResponseRetryableError")
    final class ContactFormResponseRetryableError implements SmaxContactFormResponse {
        /**
         * The {@code error_code} attribute echoed by the {@code <response/>} child.
         *
         * @apiNote
         * Surfaces the relay's classification of the transient refusal.
         */
        private final int responseErrorCode;

        /**
         * The optional {@code next_retry_ts} attribute (Unix epoch seconds, text-encoded).
         *
         * @apiNote
         * Carries the earliest moment at which the caller is expected to retry; absent when the
         * relay does not pin a retry floor.
         */
        private final String responseNextRetryTs;

        /**
         * Constructs a retryable-error reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <response error_code=.../>}
         * attribute validated; manual construction is rarely useful outside tests.
         *
         * @param responseErrorCode   the error code echoed by the relay
         * @param responseNextRetryTs the optional retry-after timestamp; may be {@code null}
         */
        public ContactFormResponseRetryableError(int responseErrorCode, String responseNextRetryTs) {
            this.responseErrorCode = responseErrorCode;
            this.responseNextRetryTs = responseNextRetryTs;
        }

        /**
         * Returns the error code echoed by the {@code <response/>} child.
         *
         * @apiNote
         * Surfaces the relay's transient-refusal classification.
         *
         * @return the code
         */
        public int responseErrorCode() {
            return responseErrorCode;
        }

        /**
         * Returns the optional retry-after timestamp (Unix epoch seconds).
         *
         * @apiNote
         * Empty when the relay did not pin a retry floor; otherwise the earliest moment a retry
         * should be attempted.
         *
         * @return an {@link Optional} carrying the timestamp text, or empty when omitted
         */
        public Optional<String> responseNextRetryTs() {
            return Optional.ofNullable(responseNextRetryTs);
        }

        /**
         * Tries to parse an inbound stanza as a {@link ContactFormResponseRetryableError}.
         *
         * @apiNote
         * Returns empty when the IQ envelope does not match or when the {@code <response/>}
         * child lacks an {@code error_code} attribute.
         *
         * @implNote
         * This implementation validates the IQ envelope identically to
         * {@link ContactFormResponseSuccess#of(Node, Node)} and then reads
         * {@code error_code}/{@code next_retry_ts} from the {@code <response/>} attributes
         * (no {@code <message/>} text body is required).
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportContactFormResponseRetryableError",
                exports = "parseContactFormResponseRetryableError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ContactFormResponseRetryableError> of(Node node, Node request) {
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
            var responseChild = node.getChild("response").orElse(null);
            if (responseChild == null) {
                return Optional.empty();
            }
            var errorCode = responseChild.getAttributeAsInt("error_code");
            if (errorCode.isEmpty()) {
                return Optional.empty();
            }
            var nextRetryTs = responseChild.getAttributeAsString("next_retry_ts").orElse(null);
            return Optional.of(new ContactFormResponseRetryableError(errorCode.getAsInt(), nextRetryTs));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ContactFormResponseRetryableError) obj;
            return this.responseErrorCode == that.responseErrorCode
                    && Objects.equals(this.responseNextRetryTs, that.responseNextRetryTs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(responseErrorCode, responseNextRetryTs);
        }

        @Override
        public String toString() {
            return "SmaxContactFormResponse.ContactFormResponseRetryableError[responseErrorCode="
                    + responseErrorCode
                    + ", responseNextRetryTs=" + responseNextRetryTs + ']';
        }
    }

    /**
     * Final-rejection variant carrying one of the three documented client/server error codes
     * ({@code 400} bad-request, {@code 475} notice-required, {@code 500} internal-server-error).
     *
     * @apiNote
     * Surfaces a non-retryable rejection that the UI is expected to render as an error banner;
     * for {@code 475} the carried {@link #tosVersion()} drives the "accept new ToS" prompt
     * before resubmission becomes possible.
     *
     * @implNote
     * This implementation projects WA Web's mutually exclusive
     * {@code IQErrorBadRequestMixin}/{@code IQErrorNoticeRequiredMixin}/{@code IQErrorInternalServerErrorMixin}
     * into a single concrete class indexed by the {@code (code, text)} pair; the {@code 475}
     * branch additionally reads {@code tos_version} from the {@code <error/>} child and clamps
     * it to {@code [1, 65535]}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSupportContactFormResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInSupportContactFormError")
    @WhatsAppWebModule(moduleName = "WASmaxInSupportIQErrorBadRequestMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInSupportIQErrorNoticeRequiredMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInSupportIQErrorInternalServerErrorMixin")
    final class ContactFormResponseError implements SmaxContactFormResponse {
        /**
         * The numeric error code from the {@code <error/>} envelope ({@code 400}, {@code 475} or
         * {@code 500}).
         *
         * @apiNote
         * Surfaces the documented disjunction WA Web validates against.
         */
        private final int errorCode;

        /**
         * The error text paired with {@link #errorCode}
         * ({@code "bad-request"} / {@code "notice-required"} /
         * {@code "internal-server-error"}).
         *
         * @apiNote
         * Surfaces the parity-checked text from the envelope; {@code null} only after manual
         * construction with a {@code null} value.
         */
        private final String errorText;

        /**
         * The {@code tos_version} attribute carried only by the {@code 475 notice-required}
         * sub-variant.
         *
         * @apiNote
         * Drives the "accept new ToS" prompt; {@code null} for the {@code 400} and {@code 500}
         * cases.
         */
        private final Integer tosVersion;

        /**
         * Constructs an error reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code (code, text)} pair matched one of
         * the three documented combinations; manual construction is rarely useful outside tests.
         *
         * @param errorCode  the numeric code
         * @param errorText  the optional error text; may be {@code null}
         * @param tosVersion the optional ToS version; may be {@code null}
         */
        public ContactFormResponseError(int errorCode, String errorText, Integer tosVersion) {
            this.errorCode = errorCode;
            this.errorText = errorText;
            this.tosVersion = tosVersion;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * One of {@code 400}, {@code 475} or {@code 500} when produced by
         * {@link #of(Node, Node)}.
         *
         * @return the code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional error text.
         *
         * @apiNote
         * Empty only when a caller explicitly constructed the variant with a {@code null} text;
         * {@link #of(Node, Node)} always emits a non-{@code null} value.
         *
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Returns the optional {@code tos_version} carried by the {@code 475 notice-required}
         * variant.
         *
         * @apiNote
         * Surfaces the new ToS revision the user must accept before resubmitting; empty for the
         * {@code 400} and {@code 500} cases.
         *
         * @return an {@link Optional} carrying the version, or empty when the error is not
         *         {@code notice-required}
         */
        public Optional<Integer> tosVersion() {
            return Optional.ofNullable(tosVersion);
        }

        /**
         * Tries to parse an inbound stanza as a {@link ContactFormResponseError}.
         *
         * @apiNote
         * Returns empty when the {@code (code, text)} pair does not match one of the three
         * documented combinations; use {@link SmaxContactFormResponse#of(Node, Node)} to walk
         * all three response variants.
         *
         * @implNote
         * This implementation delegates IQ-envelope validation and {@code <error/>} extraction
         * to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} (4xx) and
         * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} (5xx). The {@code 475}
         * branch reads {@code tos_version} from the {@code <error/>} child and discards it when
         * outside {@code [1, 65535]}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSupportContactFormResponseError",
                exports = "parseContactFormResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ContactFormResponseError> of(Node node, Node request) {
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
            Integer tosVersion = null;
            if (code == 400 && "bad-request".equals(text)) {
                // IQErrorBadRequestMixin
            } else if (code == 475 && "notice-required".equals(text)) {
                var errorChild = node.getChild("error").orElse(null);
                if (errorChild != null) {
                    var tos = errorChild.getAttributeAsInt("tos_version");
                    if (tos.isPresent() && tos.getAsInt() >= 1 && tos.getAsInt() <= 65535) {
                        tosVersion = tos.getAsInt();
                    }
                }
            } else if (code == 500 && "internal-server-error".equals(text)) {
                // IQErrorInternalServerErrorMixin
            } else {
                return Optional.empty();
            }
            return Optional.of(new ContactFormResponseError(code, text, tosVersion));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ContactFormResponseError) obj;
            return this.errorCode == that.errorCode
                    && Objects.equals(this.errorText, that.errorText)
                    && Objects.equals(this.tosVersion, that.tosVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText, tosVersion);
        }

        @Override
        public String toString() {
            return "SmaxContactFormResponse.ContactFormResponseError[errorCode=" + errorCode
                    + ", errorText=" + errorText
                    + ", tosVersion=" + tosVersion + ']';
        }
    }
}
