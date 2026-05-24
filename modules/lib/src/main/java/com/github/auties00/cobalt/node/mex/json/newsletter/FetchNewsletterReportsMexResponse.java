package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the MEX response of the fetch-newsletter-reports query built by
 * {@link FetchNewsletterReportsMexRequest}.
 *
 * @apiNote
 * Surfaces one {@link ChannelsReports} per report row in the relay's
 * {@code data.xwa2_channels_reports.channels_reports} array; each row carries
 * the report id, status, creation and last-update timestamps, the channel
 * name and Jid, a polymorphic {@link ChannelsReports.ReportedContentData}
 * payload identifying the offending message, and an optional
 * {@link ChannelsReports.Appeal} block when the offender has appealed.
 * Mirrors the shape WA Web's {@code WAWebNewsletterReportParseUtils} consumes
 * before forwarding entries to the moderation UI.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterReportsJob")
public final class FetchNewsletterReportsMexResponse implements MexOperation.Response.Json {
    /**
     * The parsed list of channel reports; ordered as returned by the relay.
     */
    private final List<ChannelsReports> channelsReports;

    /**
     * Constructs a response wrapping the parsed list of channel reports.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param channelsReports the parsed list of reports
     */
    private FetchNewsletterReportsMexResponse(List<ChannelsReports> channelsReports) {
        this.channelsReports = channelsReports;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_channels_reports} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<FetchNewsletterReportsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterReportsMexResponse::of);
    }

    /**
     * Returns the parsed list of channel reports.
     *
     * @apiNote
     * The list is ordered as returned by the relay and may be empty when no
     * reports are outstanding against newsletters the local user
     * administers.
     *
     * @return the parsed list, empty when the {@code channels_reports} array
     *         was missing or empty
     */
    public List<ChannelsReports> channelsReports() {
        return channelsReports;
    }

    /**
     * A single entry of the {@code channels_reports} array returned by the
     * relay.
     *
     * @apiNote
     * Carries the metadata WA Web's moderation UI renders for one outstanding
     * report: the report id, current {@code status}, creation and
     * last-update timestamps, the channel display name and Jid, the
     * {@link ReportedContentData} payload identifying the offending content,
     * and an optional {@link Appeal} block when the offender has filed an
     * appeal.
     */
    public static final class ChannelsReports {
        /**
         * The relay-issued report identifier echoed under {@code report_id}.
         */
        private final String reportId;

        /**
         * The current moderation status echoed under {@code status}.
         */
        private final String status;

        /**
         * The report-creation timestamp in epoch seconds, echoed under
         * {@code creation_time}.
         */
        private final Long creationTime;

        /**
         * The last-update timestamp in epoch seconds, echoed under
         * {@code last_update_time}.
         */
        private final Long lastUpdateTime;

        /**
         * The display name of the reported channel, echoed under
         * {@code channel_name}.
         */
        private final String channelName;

        /**
         * The Jid of the reported channel, echoed under {@code channel_jid}.
         */
        private final String channelJid;

        /**
         * The polymorphic payload identifying the reported content, echoed
         * under {@code reported_content_data}.
         */
        private final ReportedContentData reportedContentData;

        /**
         * The optional appeal record echoed under {@code appeal}.
         */
        private final Appeal appeal;

        /**
         * Constructs a single channel-report entry.
         *
         * @apiNote
         * Reserved for {@link #of(JSONObject)}; external callers do not
         * instantiate report entries directly.
         *
         * @param reportId            the relay-issued report identifier
         * @param status              the current moderation status
         * @param creationTime        the report-creation epoch seconds
         * @param lastUpdateTime      the last-update epoch seconds
         * @param channelName         the reported channel display name
         * @param channelJid          the reported channel Jid
         * @param reportedContentData the polymorphic reported-content payload
         * @param appeal              the optional appeal record
         */
        private ChannelsReports(String reportId, String status, Long creationTime, Long lastUpdateTime, String channelName, String channelJid, ReportedContentData reportedContentData, Appeal appeal) {
            this.reportId = reportId;
            this.status = status;
            this.creationTime = creationTime;
            this.lastUpdateTime = lastUpdateTime;
            this.channelName = channelName;
            this.channelJid = channelJid;
            this.reportedContentData = reportedContentData;
            this.appeal = appeal;
        }

        /**
         * Returns the relay-issued report identifier.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code report_id}.
         *
         * @return the {@code report_id} value, or empty when omitted
         */
        public Optional<String> reportId() {
            return Optional.ofNullable(reportId);
        }

        /**
         * Returns the current moderation status of the report.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code status}; otherwise
         * carries the relay-defined status code (for example
         * {@code "PENDING"}, {@code "ACTIONED"}).
         *
         * @return the {@code status} value, or empty when omitted
         */
        public Optional<String> status() {
            return Optional.ofNullable(status);
        }

        /**
         * Returns the moment the report was filed.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code creation_time}; the
         * underlying value is the wire-level epoch-second integer remapped
         * to an {@link Instant}.
         *
         * @return the {@code creation_time} as an {@link Instant}, or empty
         *         when omitted
         */
        public Optional<Instant> creationTime() {
            return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the moment the report was last updated.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code last_update_time};
         * the underlying value is the wire-level epoch-second integer
         * remapped to an {@link Instant}.
         *
         * @return the {@code last_update_time} as an {@link Instant}, or
         *         empty when omitted
         */
        public Optional<Instant> lastUpdateTime() {
            return Optional.ofNullable(lastUpdateTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the display name of the reported channel.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code channel_name}.
         *
         * @return the {@code channel_name} value, or empty when omitted
         */
        public Optional<String> channelName() {
            return Optional.ofNullable(channelName);
        }

        /**
         * Returns the Jid of the reported channel.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code channel_jid}.
         *
         * @return the {@code channel_jid} value, or empty when omitted
         */
        public Optional<String> channelJid() {
            return Optional.ofNullable(channelJid);
        }

        /**
         * Returns the polymorphic payload identifying the reported content.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code reported_content_data}.
         *
         * @return the parsed {@link ReportedContentData}, or empty when
         *         omitted
         */
        public Optional<ReportedContentData> reportedContentData() {
            return Optional.ofNullable(reportedContentData);
        }

        /**
         * Returns the appeal record attached to the report.
         *
         * @apiNote
         * Empty when no appeal has been filed (or the GraphQL envelope omits
         * {@code appeal}); otherwise carries the appellant's reason and
         * state.
         *
         * @return the parsed {@link Appeal}, or empty when omitted
         */
        public Optional<Appeal> appeal() {
            return Optional.ofNullable(appeal);
        }

        /**
         * The polymorphic {@code reported_content_data} payload embedded in
         * each report entry.
         *
         * @apiNote
         * The relay returns one of three GraphQL inline-fragment shapes
         * keyed by the {@code __typename} discriminator:
         * <ul>
         *   <li>{@code XWA2ChannelServerMsgData} carries {@code server_msg_id}.</li>
         *   <li>{@code XWA2ChannelStatusData} carries {@code server_id}.</li>
         *   <li>{@code XWA2ChannelQuestionResponseData} carries
         *       {@code server_response_id}, {@code notify_name} and a
         *       nested {@code question_data} fragment of type
         *       {@code XWA2ChannelServerMsgData}.</li>
         * </ul>
         */
        public static final class ReportedContentData {
            /**
             * The GraphQL {@code __typename} discriminator selecting which
             * inline-fragment fields are populated.
             */
            private final String typename;

            /**
             * The {@code server_msg_id} populated on
             * {@code XWA2ChannelServerMsgData} fragments.
             */
            private final String serverMsgId;

            /**
             * The {@code server_id} populated on
             * {@code XWA2ChannelStatusData} fragments.
             */
            private final String serverId;

            /**
             * The {@code server_response_id} populated on
             * {@code XWA2ChannelQuestionResponseData} fragments.
             */
            private final String serverResponseId;

            /**
             * The {@code notify_name} populated on
             * {@code XWA2ChannelQuestionResponseData} fragments.
             */
            private final String notifyName;

            /**
             * The nested {@code question_data} fragment populated on
             * {@code XWA2ChannelQuestionResponseData}.
             */
            private final QuestionData questionData;

            /**
             * Constructs a parsed {@code reported_content_data} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}; only the fields
             * matching the active {@code __typename} fragment are populated.
             *
             * @param typename         the {@code __typename} discriminator
             * @param serverMsgId      the {@code server_msg_id} value
             * @param serverId         the {@code server_id} value
             * @param serverResponseId the {@code server_response_id} value
             * @param notifyName       the {@code notify_name} value
             * @param questionData     the nested {@code question_data} fragment
             */
            private ReportedContentData(String typename, String serverMsgId, String serverId, String serverResponseId, String notifyName, QuestionData questionData) {
                this.typename = typename;
                this.serverMsgId = serverMsgId;
                this.serverId = serverId;
                this.serverResponseId = serverResponseId;
                this.notifyName = notifyName;
                this.questionData = questionData;
            }

            /**
             * Returns the {@code __typename} discriminator.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code __typename};
             * otherwise selects which inline-fragment getters carry data.
             *
             * @return the {@code __typename} value, or empty when omitted
             */
            public Optional<String> typename() {
                return Optional.ofNullable(typename);
            }

            /**
             * Returns the message identifier of the reported channel
             * message.
             *
             * @apiNote
             * Populated only when {@link #typename()} resolves to
             * {@code XWA2ChannelServerMsgData}.
             *
             * @return the {@code server_msg_id} value, or empty when not
             *         applicable to the active fragment
             */
            public Optional<String> serverMsgId() {
                return Optional.ofNullable(serverMsgId);
            }

            /**
             * Returns the status identifier of the reported channel status.
             *
             * @apiNote
             * Populated only when {@link #typename()} resolves to
             * {@code XWA2ChannelStatusData}.
             *
             * @return the {@code server_id} value, or empty when not
             *         applicable to the active fragment
             */
            public Optional<String> serverId() {
                return Optional.ofNullable(serverId);
            }

            /**
             * Returns the response identifier of the reported question
             * response.
             *
             * @apiNote
             * Populated only when {@link #typename()} resolves to
             * {@code XWA2ChannelQuestionResponseData}.
             *
             * @return the {@code server_response_id} value, or empty when
             *         not applicable to the active fragment
             */
            public Optional<String> serverResponseId() {
                return Optional.ofNullable(serverResponseId);
            }

            /**
             * Returns the notify-name of the question respondent.
             *
             * @apiNote
             * Populated only when {@link #typename()} resolves to
             * {@code XWA2ChannelQuestionResponseData}.
             *
             * @return the {@code notify_name} value, or empty when not
             *         applicable to the active fragment
             */
            public Optional<String> notifyName() {
                return Optional.ofNullable(notifyName);
            }

            /**
             * Returns the nested question-message fragment carried by
             * question-response reports.
             *
             * @apiNote
             * Populated only when {@link #typename()} resolves to
             * {@code XWA2ChannelQuestionResponseData}; identifies the
             * underlying question whose response was reported.
             *
             * @return the parsed {@link QuestionData}, or empty when not
             *         applicable to the active fragment
             */
            public Optional<QuestionData> questionData() {
                return Optional.ofNullable(questionData);
            }

            /**
             * Parses a {@code reported_content_data} fragment from the
             * given JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null} so
             * an absent fragment cleanly back-propagates to the outer
             * {@code reported_content_data()} getter.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<ReportedContentData> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var typename = obj.getString("__typename");
                var serverMsgId = obj.getString("server_msg_id");
                var serverId = obj.getString("server_id");
                var serverResponseId = obj.getString("server_response_id");
                var notifyName = obj.getString("notify_name");
                var questionData = QuestionData.of(obj.getJSONObject("question_data")).orElse(null);
                return Optional.of(new ReportedContentData(typename, serverMsgId, serverId, serverResponseId, notifyName, questionData));
            }

            /**
             * The {@code question_data} nested fragment carried by
             * {@code XWA2ChannelQuestionResponseData} reports.
             *
             * @apiNote
             * Identifies the underlying question message that produced the
             * reported response.
             */
            public static final class QuestionData {
                /**
                 * The GraphQL {@code __typename} discriminator on the
                 * question fragment.
                 */
                private final String typename;

                /**
                 * The {@code server_msg_id} of the underlying question
                 * message.
                 */
                private final String serverMsgId;

                /**
                 * Constructs a parsed {@code question_data} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param typename    the {@code __typename} discriminator
                 * @param serverMsgId the question message identifier
                 */
                private QuestionData(String typename, String serverMsgId) {
                    this.typename = typename;
                    this.serverMsgId = serverMsgId;
                }

                /**
                 * Returns the {@code __typename} discriminator on the
                 * question fragment.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code __typename}.
                 *
                 * @return the {@code __typename} value, or empty when
                 *         omitted
                 */
                public Optional<String> typename() {
                    return Optional.ofNullable(typename);
                }

                /**
                 * Returns the message identifier of the underlying question
                 * message.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code server_msg_id}.
                 *
                 * @return the {@code server_msg_id} value, or empty when
                 *         omitted
                 */
                public Optional<String> serverMsgId() {
                    return Optional.ofNullable(serverMsgId);
                }

                /**
                 * Parses a {@code question_data} fragment from the given
                 * JSON object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
                 *         {@code null}
                 */
                static Optional<QuestionData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var typename = obj.getString("__typename");
                    var serverMsgId = obj.getString("server_msg_id");
                    return Optional.of(new QuestionData(typename, serverMsgId));
                }
            }
        }

        /**
         * The {@code appeal} record attached to reports the offender has
         * appealed.
         *
         * @apiNote
         * Carries the appellant's stated reason, the appeal state, and the
         * cross-reference identifiers linking the appeal back to its report.
         */
        public static final class Appeal {
            /**
             * The appeal state (for example {@code "PENDING"},
             * {@code "RESOLVED"}).
             */
            private final String state;

            /**
             * The free-text reason the offender supplied when filing the
             * appeal.
             */
            private final String appealReason;

            /**
             * The appeal-creation timestamp in epoch seconds.
             */
            private final Long creationTime;

            /**
             * The cross-reference to the appealed report's identifier.
             */
            private final String reportId;

            /**
             * The relay-issued identifier of the appeal record itself.
             */
            private final String appealId;

            /**
             * Constructs a parsed {@code appeal} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param state        the appeal state
             * @param appealReason the appellant's stated reason
             * @param creationTime the appeal-creation epoch seconds
             * @param reportId     the cross-reference to the appealed report
             * @param appealId     the relay-issued appeal identifier
             */
            private Appeal(String state, String appealReason, Long creationTime, String reportId, String appealId) {
                this.state = state;
                this.appealReason = appealReason;
                this.creationTime = creationTime;
                this.reportId = reportId;
                this.appealId = appealId;
            }

            /**
             * Returns the appeal state.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code state}.
             *
             * @return the {@code state} value, or empty when omitted
             */
            public Optional<String> state() {
                return Optional.ofNullable(state);
            }

            /**
             * Returns the appellant's stated reason.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code appeal_reason}.
             *
             * @return the {@code appeal_reason} value, or empty when omitted
             */
            public Optional<String> appealReason() {
                return Optional.ofNullable(appealReason);
            }

            /**
             * Returns the moment the appeal was filed.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code creation_time};
             * the underlying value is the wire-level epoch-second integer
             * remapped to an {@link Instant}.
             *
             * @return the {@code creation_time} as an {@link Instant}, or
             *         empty when omitted
             */
            public Optional<Instant> creationTime() {
                return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the cross-reference to the appealed report.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code report_id}.
             *
             * @return the {@code report_id} value, or empty when omitted
             */
            public Optional<String> reportId() {
                return Optional.ofNullable(reportId);
            }

            /**
             * Returns the relay-issued appeal identifier.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code appeal_id}.
             *
             * @return the {@code appeal_id} value, or empty when omitted
             */
            public Optional<String> appealId() {
                return Optional.ofNullable(appealId);
            }

            /**
             * Parses an {@code appeal} record from the given JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null} so
             * an absent appeal cleanly back-propagates to the outer
             * {@code appeal()} getter.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Appeal> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var state = obj.getString("state");
                var appealReason = obj.getString("appeal_reason");
                var creationTime = obj.getLong("creation_time");
                var reportId = obj.getString("report_id");
                var appealId = obj.getString("appeal_id");
                return Optional.of(new Appeal(state, appealReason, creationTime, reportId, appealId));
            }

            /**
             * Parses every {@code appeal} record in the given JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched appeal arrays;
             * returns {@link List#of()} when {@code arr} is {@code null} so
             * an absent array surfaces as an empty list.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
             *         {@code null}
             */
            static List<Appeal> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Appeal>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a single {@code channels_reports} entry from the given JSON
         * object.
         *
         * @apiNote
         * Reserved for {@link #ofArray(JSONArray)}; returns
         * {@link Optional#empty()} when {@code obj} is {@code null} so an
         * absent element does not allocate a sentinel entry.
         *
         * @param obj the JSON object to parse
         * @return the parsed value, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<ChannelsReports> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var reportId = obj.getString("report_id");
            var status = obj.getString("status");
            var creationTime = obj.getLong("creation_time");
            var lastUpdateTime = obj.getLong("last_update_time");
            var channelName = obj.getString("channel_name");
            var channelJid = obj.getString("channel_jid");
            var reportedContentData = ReportedContentData.of(obj.getJSONObject("reported_content_data")).orElse(null);
            var appeal = Appeal.of(obj.getJSONObject("appeal")).orElse(null);
            return Optional.of(new ChannelsReports(reportId, status, creationTime, lastUpdateTime, channelName, channelJid, reportedContentData, appeal));
        }

        /**
         * Parses every {@code channels_reports} entry in the given JSON
         * array.
         *
         * @apiNote
         * Materialises the {@code data.xwa2_channels_reports.channels_reports}
         * array into a fresh {@link List}; returns {@link List#of()} when
         * {@code arr} is {@code null} so an absent or empty server response
         * surfaces as an empty list.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed values, empty when {@code arr} is
         *         {@code null}
         */
        static List<ChannelsReports> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<ChannelsReports>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload; callers should not
     * hold raw JSON bytes.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception, mirroring the defensive null-checks in WA Web's caller.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_channels_reports} root
     */
    private static Optional<FetchNewsletterReportsMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_channels_reports");
        if (root == null) {
            return Optional.empty();
        }

        var channelsReports = ChannelsReports.ofArray(root.getJSONArray("channels_reports"));

        return Optional.of(new FetchNewsletterReportsMexResponse(channelsReports));
    }
}
