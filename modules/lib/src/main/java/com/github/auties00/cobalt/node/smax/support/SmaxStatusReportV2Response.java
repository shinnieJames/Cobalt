package com.github.auties00.cobalt.node.smax.support;

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
 * Sealed family of inbound replies to {@link SmaxStatusReportV2Request}, modelling the two
 * outcomes WA Web's {@code WASmaxSpamStatusReportV2RPC.sendStatusReportV2RPC} switches
 * between: accepted report and rejected report.
 *
 * @apiNote
 * Drives the "Report newsletter status" submit-result UI consumed by WA Web's
 * {@code WAWebNewsletterReportUtils}; a {@link Success} carries the optional report id used
 * for follow-up, while {@link Error} surfaces the relay's rejection code/text for banner
 * rendering.
 *
 * @implNote
 * This implementation matches WA Web's parser order: {@link Success} first, then
 * {@link Error}. An inbound stanza that fits neither shape returns an empty {@link Optional}
 * instead of WA Web's {@code SmaxParsingFailure}.
 */
public sealed interface SmaxStatusReportV2Response extends SmaxOperation.Response
        permits SmaxStatusReportV2Response.Success, SmaxStatusReportV2Response.Error {

    /**
     * Parses the inbound v2 status-report reply against each {@link SmaxStatusReportV2Response}
     * variant and returns the first that matches.
     *
     * @apiNote
     * Use after the relay's IQ arrives in response to a {@link SmaxStatusReportV2Request}; an
     * empty {@link Optional} means the inbound stanza did not fit either of the two documented
     * shapes.
     *
     * @implNote
     * This implementation tries {@link Success#of(Node, Node)} first and falls back to
     * {@link Error#of(Node, Node)}; no parse exception is raised on total miss.
     *
     * @param node    the inbound IQ stanza; never {@code null}
     * @param request the originating outbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxSpamStatusReportV2RPC",
            exports = "sendStatusReportV2RPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxStatusReportV2Response> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        var success = Success.of(node, request);
        if (success.isPresent()) {
            return success;
        }
        return Error.of(node, request);
    }

    /**
     * Accepted-report variant carrying the optional opaque report id.
     *
     * @apiNote
     * Surfaces the relay's "report queued" acknowledgement; {@link #reportId()} is empty when
     * the relay did not assign a follow-up id.
     *
     * @implNote
     * This implementation reads the id from the {@code <report id>} attribute per WA Web's
     * {@code WASmaxInSpamReportIdMixin}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSpamStatusReportV2ResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInSpamReportIdMixin")
    final class Success implements SmaxStatusReportV2Response {
        /**
         * The optional opaque report id assigned by the relay.
         *
         * @apiNote
         * Surfaces the value of the {@code <report id>} attribute, or {@code null} when the
         * relay omitted it.
         */
        private final String reportId;

        /**
         * Constructs an accepted-report reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <iq type="result"/>} envelope
         * validated.
         *
         * @param reportId the optional report id; may be {@code null}
         */
        public Success(String reportId) {
            this.reportId = reportId;
        }

        /**
         * Returns the optional opaque report id.
         *
         * @apiNote
         * Empty when the relay did not assign a follow-up id.
         *
         * @return an {@link Optional} carrying the id, or empty when omitted
         */
        public Optional<String> reportId() {
            return Optional.ofNullable(reportId);
        }

        /**
         * Tries to parse an inbound stanza as a {@link Success}.
         *
         * @apiNote
         * Returns empty when the IQ envelope does not match a result for {@code request}.
         *
         * @implNote
         * This implementation defers IQ-envelope validation to
         * {@link SmaxIqResultResponseMixin#validate(Node, Node)} and then reads
         * {@code <report id>} via {@link Node#getChild(String)} chained with
         * {@link Node#getAttributeAsString(String)}.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSpamStatusReportV2ResponseSuccess",
                exports = "parseStatusReportV2ResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var reportId = node.getChild("report")
                    .flatMap(child -> child.getAttributeAsString("id"))
                    .orElse(null);
            return Optional.of(new Success(reportId));
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
            return Objects.equals(this.reportId, that.reportId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reportId);
        }

        @Override
        public String toString() {
            return "SmaxStatusReportV2Response.Success[reportId=" + reportId + ']';
        }
    }

    /**
     * Rejected-report variant carrying the relay's {@code (code, text)} error pair.
     *
     * @apiNote
     * Surfaces the relay refusal; the caller renders an error banner. WA Web aliases the
     * documented v2 codes ({@code 400 bad-request}, {@code 403 forbidden},
     * {@code 429 rate-overlimit}, {@code 500 internal-server-error}) via
     * {@code WASmaxInSpamIQErrorInternalServerErrorOrBadRequestOrForbiddenOrRateOverlimitMixinGroup};
     * Cobalt forwards the raw pair to leave the mapping to the caller.
     *
     * @implNote
     * This implementation delegates IQ-envelope and {@code <error/>} extraction to
     * {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} (4xx) and
     * {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} (5xx).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInSpamStatusReportV2ResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxInSpamIQErrorInternalServerErrorOrBadRequestOrForbiddenOrRateOverlimitMixinGroup")
    final class Error implements SmaxStatusReportV2Response {
        /**
         * The numeric error code from the {@code <error/>} envelope.
         *
         * @apiNote
         * Surfaces the relay's classification of the rejection.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text.
         *
         * @apiNote
         * Surfaces the paired text from {@code <error text="..."/>}; {@code null} when the
         * envelope omitted it.
         */
        private final String errorText;

        /**
         * Constructs a rejected-report reply from the parsed fields.
         *
         * @apiNote
         * Invoked by {@link #of(Node, Node)} after the {@code <error/>} envelope validated.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public Error(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code.
         *
         * @apiNote
         * Surfaces the relay's rejection classification.
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
         * @return an {@link Optional} carrying the text, or empty when omitted
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse an inbound stanza as an {@link Error}.
         *
         * @apiNote
         * Returns empty when neither the 4xx nor 5xx envelope matched.
         *
         * @implNote
         * This implementation tries {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)}
         * first and falls back to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)};
         * the first match wins.
         *
         * @param node    the inbound IQ stanza
         * @param request the originating outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInSpamStatusReportV2ResponseError",
                exports = "parseStatusReportV2ResponseError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Error> of(Node node, Node request) {
            var clientError = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (clientError != null) {
                return Optional.of(new Error(clientError.code(), clientError.text()));
            }
            var serverError = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (serverError != null) {
                return Optional.of(new Error(serverError.code(), serverError.text()));
            }
            return Optional.empty();
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
            return this.errorCode == that.errorCode && Objects.equals(this.errorText, that.errorText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        @Override
        public String toString() {
            return "SmaxStatusReportV2Response.Error[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
