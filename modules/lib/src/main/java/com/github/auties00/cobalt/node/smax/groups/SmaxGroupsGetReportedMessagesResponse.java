package com.github.auties00.cobalt.node.smax.groups;

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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed reply family for a {@link SmaxGroupsGetReportedMessagesRequest}.
 *
 * @apiNote The three variants mirror the WA Web RPC dispatcher's {@code Success}/{@code ClientError}/{@code ServerError}
 * cases: {@link Success} carries the per-message {@link Report} rows that drive the admin moderation drawer, the two
 * error variants surface the relay's reason codes. The {@code WAWebReportToAdminJob.getReportedMsgs} caller in WA Web
 * uses the same dispatch shape and additionally folds the LID-to-PN mappings into
 * {@code WAWebDBCreateLidPnMappings.createLidPnMappings}.
 */
public sealed interface SmaxGroupsGetReportedMessagesResponse extends SmaxOperation.Response
        permits SmaxGroupsGetReportedMessagesResponse.Success, SmaxGroupsGetReportedMessagesResponse.ClientError, SmaxGroupsGetReportedMessagesResponse.ServerError {

    /**
     * Dispatches the inbound IQ across each {@link SmaxGroupsGetReportedMessagesResponse} variant in priority order and
     * returns the first that parses cleanly.
     *
     * @apiNote The priority order matches the WA Web RPC dispatcher in {@code WASmaxGroupsGetReportedMessagesRPC}:
     * {@link Success} first, then {@link ClientError}, then {@link ServerError}.
     *
     * @implNote The empty {@link Optional} surfaces when the stanza shape matches none of the three documented
     * variants; WA Web throws {@code SmaxParsingFailure} on the same path, but Cobalt defers the decision to the
     * caller so it can apply its own error-handling policy.
     *
     * @param node    the inbound IQ stanza
     * @param request the original outbound request
     * @return an {@link Optional} carrying the parsed variant, or empty when no variant matched
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxGroupsGetReportedMessagesRPC",
            exports = "sendGetReportedMessagesRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxGroupsGetReportedMessagesResponse> of(Node node, Node request) {
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
     * The reply variant emitted when the relay returned the pending moderation queue.
     *
     * @apiNote Surfaces as the {@code GetReportedMessagesResponseSuccess} case in {@code WAWebReportToAdminJob};
     * each {@link Report} entry is keyed by the reported message id and the admin moderation drawer renders the
     * matching reporter list.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetReportedMessagesResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGroupAddressingModeMixin")
    final class Success implements SmaxGroupsGetReportedMessagesResponse {
        /**
         * The per-message report rows.
         */
        private final List<Report> reports;

        /**
         * Constructs a {@link Success} reply.
         *
         * @param reports the per-message report rows; never {@code null}
         * @throws NullPointerException if {@code reports} is {@code null}
         */
        public Success(List<Report> reports) {
            Objects.requireNonNull(reports, "reports cannot be null");
            this.reports = List.copyOf(reports);
        }

        /**
         * Returns the per-message report rows.
         *
         * @return an unmodifiable list of {@link Report}; never {@code null}
         */
        public List<Report> reports() {
            return reports;
        }

        /**
         * Tries to parse a {@link Success} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxIqResultResponseMixin#validate(Node, Node)} for envelope validation, then
         * matches the {@code <reports>} wrapper holding zero or more {@code <report/>} entries.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetReportedMessagesResponseSuccess",
                exports = "parseGetReportedMessagesResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            if (!SmaxIqResultResponseMixin.validate(node, request)) {
                return Optional.empty();
            }
            var wrapper = node.getChild("reports").orElse(null);
            if (wrapper == null) {
                return Optional.empty();
            }
            var reportNodes = wrapper.getChildren("report");
            var reports = new ArrayList<Report>(reportNodes.size());
            for (var reportNode : reportNodes) {
                var report = Report.of(reportNode).orElse(null);
                if (report == null) {
                    return Optional.empty();
                }
                reports.add(report);
            }
            return Optional.of(new Success(reports));
        }

        /**
         * Compares this success to {@code obj} for value equality across every field.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Success} with identical fields
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
            return Objects.equals(this.reports, that.reports);
        }

        /**
         * Returns a hash composed of every field.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(reports);
        }

        /**
         * Returns a debug string carrying every field.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetReportedMessagesResponse.Success[reports=" + reports + ']';
        }
    }

    /**
     * Per-report projection carrying the reported message id and the non-empty reporter list.
     *
     * @apiNote Mirrors the {@code <report/>} child shape; the {@link #messageId()} corresponds to the stanza id of the
     * original group message and the {@link #reporters()} list is guaranteed non-empty because the relay omits the
     * row entirely when no reporters remain.
     */
    final class Report {
        /**
         * The stanza id of the reported group message.
         */
        private final String messageId;

        /**
         * The reporters who flagged the message.
         */
        private final List<Reporter> reporters;

        /**
         * Constructs a {@link Report} entry.
         *
         * @param messageId the reported message stanza id; never {@code null}
         * @param reporters the reporter list; never {@code null} and never empty
         * @throws NullPointerException     if {@code messageId} or {@code reporters} is {@code null}
         * @throws IllegalArgumentException if {@code reporters} is empty
         */
        public Report(String messageId, List<Reporter> reporters) {
            this.messageId = Objects.requireNonNull(messageId, "messageId cannot be null");
            Objects.requireNonNull(reporters, "reporters cannot be null");
            if (reporters.isEmpty()) {
                throw new IllegalArgumentException("reporters cannot be empty");
            }
            this.reporters = List.copyOf(reporters);
        }

        /**
         * Returns the reported message stanza id.
         *
         * @return the message id; never {@code null}
         */
        public String messageId() {
            return messageId;
        }

        /**
         * Returns the reporter list.
         *
         * @return an unmodifiable list of {@link Reporter}; never empty
         */
        public List<Reporter> reporters() {
            return reporters;
        }

        /**
         * Tries to parse a {@link Report} from the given {@code <report/>} child.
         *
         * @apiNote Matches when the child carries the {@code message_id} attribute and at least one
         * {@code <reporter/>} grandchild; the relay never emits an empty report row.
         *
         * @param node the {@code <report/>} child node
         * @return an {@link Optional} carrying the parsed report, or empty when the child does not match
         */
        public static Optional<Report> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("report")) {
                return Optional.empty();
            }
            var messageId = node.getAttributeAsString("message_id").orElse(null);
            if (messageId == null) {
                return Optional.empty();
            }
            var reporterNodes = node.getChildren("reporter");
            if (reporterNodes.isEmpty()) {
                return Optional.empty();
            }
            var reporters = new ArrayList<Reporter>(reporterNodes.size());
            for (var reporterNode : reporterNodes) {
                var reporter = Reporter.of(reporterNode).orElse(null);
                if (reporter == null) {
                    return Optional.empty();
                }
                reporters.add(reporter);
            }
            return Optional.of(new Report(messageId, reporters));
        }

        /**
         * Compares this report to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Report} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Report) obj;
            return Objects.equals(this.messageId, that.messageId)
                    && Objects.equals(this.reporters, that.reporters);
        }

        /**
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(messageId, reporters);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetReportedMessagesResponse.Report[messageId=" + messageId
                    + ", reporters=" + reporters + ']';
        }
    }

    /**
     * Per-reporter projection carrying the reporting user's JID and the unix-seconds timestamp at which the report
     * was filed.
     *
     * @apiNote Mirrors the {@code IdentityMixin} shape; WA Web folds the LID-to-PN identity mapping carried alongside
     * into {@code WAWebDBCreateLidPnMappings.createLidPnMappings} as a side effect.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsIdentityMixin")
    final class Reporter {
        /**
         * The reporter's user {@link Jid}.
         */
        private final Jid jid;

        /**
         * The unix-seconds timestamp at which the report was filed.
         */
        private final long timestamp;

        /**
         * Constructs a {@link Reporter} entry.
         *
         * @param jid       the reporter's {@link Jid}; never {@code null}
         * @param timestamp the unix-seconds timestamp
         * @throws NullPointerException     if {@code jid} is {@code null}
         * @throws IllegalArgumentException if {@code timestamp} is negative
         */
        public Reporter(Jid jid, long timestamp) {
            this.jid = Objects.requireNonNull(jid, "jid cannot be null");
            if (timestamp < 0) {
                throw new IllegalArgumentException("timestamp must be non-negative");
            }
            this.timestamp = timestamp;
        }

        /**
         * Returns the reporter's {@link Jid}.
         *
         * @return the reporter JID; never {@code null}
         */
        public Jid jid() {
            return jid;
        }

        /**
         * Returns the report timestamp.
         *
         * @return the unix-seconds timestamp
         */
        public long timestamp() {
            return timestamp;
        }

        /**
         * Tries to parse a {@link Reporter} from the given {@code <reporter/>} child.
         *
         * @apiNote Matches when the child carries the {@code jid} and {@code timestamp} attributes.
         *
         * @param node the {@code <reporter/>} child node
         * @return an {@link Optional} carrying the parsed reporter, or empty when the child does not match
         */
        public static Optional<Reporter> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("reporter")) {
                return Optional.empty();
            }
            var jid = node.getAttributeAsJid("jid").orElse(null);
            if (jid == null) {
                return Optional.empty();
            }
            var timestampOptional = node.getAttributeAsLong("timestamp");
            if (timestampOptional.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Reporter(jid, timestampOptional.getAsLong()));
        }

        /**
         * Compares this reporter to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link Reporter} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Reporter) obj;
            return this.timestamp == that.timestamp
                    && Objects.equals(this.jid, that.jid);
        }

        /**
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(jid, timestamp);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetReportedMessagesResponse.Reporter[jid=" + jid
                    + ", timestamp=" + timestamp + ']';
        }
    }

    /**
     * The reply variant emitted when the relay rejected the moderation-queue query as malformed or unauthorised.
     *
     * @apiNote Surfaces as the {@code GetReportedMessagesResponseClientError} case in {@code WAWebReportToAdminJob},
     * which logs the {@link #errorCode()} as the HTTP-style status passed back to the admin moderation drawer.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetReportedMessagesResponseClientError")
    final class ClientError implements SmaxGroupsGetReportedMessagesResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ClientError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ClientError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ClientError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseClientError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetReportedMessagesResponseClientError",
                exports = "parseGetReportedMessagesResponseClientError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ClientError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseClientError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ClientError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ClientError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetReportedMessagesResponse.ClientError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }

    /**
     * The reply variant emitted on transient relay-side failure.
     *
     * @apiNote Surfaces as the {@code GetReportedMessagesResponseServerError} case in {@code WAWebReportToAdminJob},
     * where it is logged at the same severity as {@link ClientError} but typically signals retry-eligible relay
     * outages rather than caller error.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInGroupsGetReportedMessagesResponseServerError")
    final class ServerError implements SmaxGroupsGetReportedMessagesResponse {
        /**
         * The numeric error code echoed by the relay.
         */
        private final int errorCode;

        /**
         * The optional human-readable error text echoed by the relay.
         */
        private final String errorText;

        /**
         * Constructs a {@link ServerError} from raw error attributes.
         *
         * @param errorCode the numeric error code
         * @param errorText the optional error text; may be {@code null}
         */
        public ServerError(int errorCode, String errorText) {
            this.errorCode = errorCode;
            this.errorText = errorText;
        }

        /**
         * Returns the numeric error code echoed by the relay.
         *
         * @return the error code
         */
        public int errorCode() {
            return errorCode;
        }

        /**
         * Returns the optional human-readable error text echoed by the relay.
         *
         * @return an {@link Optional} carrying the error text, or empty when the relay omitted it
         */
        public Optional<String> errorText() {
            return Optional.ofNullable(errorText);
        }

        /**
         * Tries to parse a {@link ServerError} variant from {@code node}.
         *
         * @apiNote Delegates to {@link SmaxBaseServerErrorMixin#parseServerError(Node, Node)} which validates the
         * shared {@code <iq type="error"><error code="..." text="..."/></iq>} envelope.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or empty when the stanza does not match
         */
        @WhatsAppWebExport(moduleName = "WASmaxInGroupsGetReportedMessagesResponseServerError",
                exports = "parseGetReportedMessagesResponseServerError",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<ServerError> of(Node node, Node request) {
            var envelope = SmaxBaseServerErrorMixin.parseServerError(node, request).orElse(null);
            if (envelope == null) {
                return Optional.empty();
            }
            return Optional.of(new ServerError(envelope.code(), envelope.text()));
        }

        /**
         * Compares this error to {@code obj} for value equality across both fields.
         *
         * @param obj the other object
         * @return {@code true} when {@code obj} is a {@link ServerError} with identical fields
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
         * Returns a hash composed of both fields.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(errorCode, errorText);
        }

        /**
         * Returns a debug string carrying both fields.
         *
         * @return the debug representation
         */
        @Override
        public String toString() {
            return "SmaxGroupsGetReportedMessagesResponse.ServerError[errorCode=" + errorCode
                    + ", errorText=" + errorText + ']';
        }
    }
}
