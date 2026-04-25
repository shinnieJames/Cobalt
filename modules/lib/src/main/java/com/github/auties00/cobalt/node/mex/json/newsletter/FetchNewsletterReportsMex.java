package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;
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
 * Fetches the list of user-submitted reports against a newsletter.
 *
 * <p>Newsletter admins and moderators use this query to review reports filed by followers, including the report reason, reporter metadata and timestamps.
 *
 * @implNote WAWebMexFetchNewsletterReportsJob: adapts the {@code mexFetchNewsletterReports} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterReportsJob")
public sealed interface FetchNewsletterReportsMex extends MexJsonOperation permits FetchNewsletterReportsMex.Request, FetchNewsletterReportsMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterReports} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterReportsJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterReports} query,
     * extracted from the relay-supplied {@code params.id} property.
     */
    String QUERY_ID = "24241374008893508";

    /**
     * The request variant of {@link FetchNewsletterReportsMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterReportsJob")
    final class Request implements FetchNewsletterReportsMex {

        /**
         * Constructs an empty request. The underlying GraphQL operation does not
         * accept arguments, so the corresponding {@code variables} object is
         * always serialised as an empty JSON object.
         */
        public Request() {
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports: WA Web constructs the
         * {@code variables} object inline as {@code var e = {}} and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterReportsJob", exports = "mexFetchNewsletterReports",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                writer.endObject();
                writer.endObject();
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /**
     * The response variant of {@link FetchNewsletterReportsMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterReportsJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterReportsJob")
    final class Response implements FetchNewsletterReportsMex {
        private final List<ChannelsReports> channelsReports;

        /**
         * Constructs a response value carrying the parsed list of channel
         * reports.
         *
         * @param channelsReports the parsed list of reports; never {@code null}
         */
        private Response(List<ChannelsReports> channelsReports) {
            this.channelsReports = channelsReports;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports: WA Web relies on the
         * GraphQL client to unwrap the response. Cobalt performs the
         * unwrapping manually from the IQ {@code <result>} child.
         * @param node the IQ response node received from the relay
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the node is missing a result payload
         */
        public static Optional<Response> of(Node node) {
            return node.getChild("result")
                    .flatMap(Node::toContentBytes)
                    .flatMap(Response::of);
        }

        /**
         * Returns the {@code channels_reports} field.
         *
         * @return the list of values, empty if absent
         */
        public List<ChannelsReports> channelsReports() {
            return channelsReports;
        }

        /**
         * A parsed {@code ChannelsReports} object.
         */
        public static final class ChannelsReports {
            private final String reportId;
            private final String status;
            private final Long creationTime;
            private final Long lastUpdateTime;
            private final String channelName;
            private final String channelJid;
            private final ReportedContentData reportedContentData;
            private final Appeal appeal;

            /**
             * Constructs a single channel-report entry.
             *
             * @param reportId            the {@code report_id} value, may be {@code null}
             * @param status              the {@code status} value, may be {@code null}
             * @param creationTime        the {@code creation_time} epoch seconds, may be {@code null}
             * @param lastUpdateTime      the {@code last_update_time} epoch seconds, may be {@code null}
             * @param channelName         the {@code channel_name} value, may be {@code null}
             * @param channelJid          the {@code channel_jid} value, may be {@code null}
             * @param reportedContentData the parsed nested {@code reported_content_data} payload, may be {@code null}
             * @param appeal              the parsed nested {@code appeal} payload, may be {@code null}
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
             * Returns the {@code report_id} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> reportId() {
                return Optional.ofNullable(reportId);
            }

            /**
             * Returns the {@code status} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> status() {
                return Optional.ofNullable(status);
            }

            /**
             * Returns the {@code creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> creationTime() {
                return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code last_update_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> lastUpdateTime() {
                return Optional.ofNullable(lastUpdateTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code channel_name} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> channelName() {
                return Optional.ofNullable(channelName);
            }

            /**
             * Returns the {@code channel_jid} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> channelJid() {
                return Optional.ofNullable(channelJid);
            }

            /**
             * Returns the {@code reported_content_data} nested object.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<ReportedContentData> reportedContentData() {
                return Optional.ofNullable(reportedContentData);
            }

            /**
             * Returns the {@code appeal} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<Appeal> appeal() {
                return Optional.ofNullable(appeal);
            }

            /**
             * The polymorphic {@code reported_content_data} object embedded in
             * each report entry.
             *
             * <p>The relay returns one of three GraphQL inline-fragment shapes
             * keyed by the {@code __typename} discriminator:
             * <ul>
             *   <li>{@code XWA2ChannelServerMsgData} — carries {@code server_msg_id}.</li>
             *   <li>{@code XWA2ChannelStatusData} — carries {@code server_id}.</li>
             *   <li>{@code XWA2ChannelQuestionResponseData} — carries
             *       {@code server_response_id}, {@code notify_name} and a
             *       nested {@code question_data} fragment of type
             *       {@code XWA2ChannelServerMsgData}.</li>
             * </ul>
             *
             * @implNote WAWebMexFetchNewsletterReportsJobQuery.graphql: the
             * compiled GraphQL document declares this field as a {@code LinkedField}
             * with three {@code InlineFragment} selections discriminated by
             * {@code __typename}.
             */
            public static final class ReportedContentData {
                private final String typename;
                private final String serverMsgId;
                private final String serverId;
                private final String serverResponseId;
                private final String notifyName;
                private final QuestionData questionData;

                /**
                 * Constructs a parsed {@code reported_content_data} value.
                 *
                 * @param typename         the GraphQL {@code __typename} discriminator, may be {@code null}
                 * @param serverMsgId      the {@code server_msg_id} value (set on {@code XWA2ChannelServerMsgData}), may be {@code null}
                 * @param serverId         the {@code server_id} value (set on {@code XWA2ChannelStatusData}), may be {@code null}
                 * @param serverResponseId the {@code server_response_id} value (set on {@code XWA2ChannelQuestionResponseData}), may be {@code null}
                 * @param notifyName       the {@code notify_name} value (set on {@code XWA2ChannelQuestionResponseData}), may be {@code null}
                 * @param questionData     the parsed nested {@code question_data} payload, may be {@code null}
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
                 * Returns the {@code __typename} discriminator field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> typename() {
                    return Optional.ofNullable(typename);
                }

                /**
                 * Returns the {@code server_msg_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> serverMsgId() {
                    return Optional.ofNullable(serverMsgId);
                }

                /**
                 * Returns the {@code server_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> serverId() {
                    return Optional.ofNullable(serverId);
                }

                /**
                 * Returns the {@code server_response_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> serverResponseId() {
                    return Optional.ofNullable(serverResponseId);
                }

                /**
                 * Returns the {@code notify_name} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> notifyName() {
                    return Optional.ofNullable(notifyName);
                }

                /**
                 * Returns the {@code question_data} nested fragment.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<QuestionData> questionData() {
                    return Optional.ofNullable(questionData);
                }

                /**
                 * Parses a {@code ReportedContentData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
                 * {@code XWA2ChannelQuestionResponseData}.
                 *
                 * @implNote WAWebMexFetchNewsletterReportsJobQuery.graphql:
                 * declared as a {@code LinkedField} with a single
                 * {@code XWA2ChannelServerMsgData} {@code InlineFragment}
                 * selection providing {@code server_msg_id}.
                 */
                public static final class QuestionData {
                    private final String typename;
                    private final String serverMsgId;

                    /**
                     * Constructs a parsed {@code question_data} value.
                     *
                     * @param typename    the {@code __typename} discriminator, may be {@code null}
                     * @param serverMsgId the {@code server_msg_id} value, may be {@code null}
                     */
                    private QuestionData(String typename, String serverMsgId) {
                        this.typename = typename;
                        this.serverMsgId = serverMsgId;
                    }

                    /**
                     * Returns the {@code __typename} discriminator field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> typename() {
                        return Optional.ofNullable(typename);
                    }

                    /**
                     * Returns the {@code server_msg_id} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> serverMsgId() {
                        return Optional.ofNullable(serverMsgId);
                    }

                    /**
                     * Parses a {@code QuestionData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
             * A parsed {@code Appeal} object.
             */
            public static final class Appeal {
                private final String state;
                private final String appealReason;
                private final Long creationTime;
                private final String reportId;
                private final String appealId;

                /**
                 * Constructs a parsed {@code appeal} value.
                 *
                 * @param state        the {@code state} value, may be {@code null}
                 * @param appealReason the {@code appeal_reason} value, may be {@code null}
                 * @param creationTime the {@code creation_time} epoch seconds, may be {@code null}
                 * @param reportId     the {@code report_id} value, may be {@code null}
                 * @param appealId     the {@code appeal_id} value, may be {@code null}
                 */
                private Appeal(String state, String appealReason, Long creationTime, String reportId, String appealId) {
                    this.state = state;
                    this.appealReason = appealReason;
                    this.creationTime = creationTime;
                    this.reportId = reportId;
                    this.appealId = appealId;
                }

                /**
                 * Returns the {@code state} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> state() {
                    return Optional.ofNullable(state);
                }

                /**
                 * Returns the {@code appeal_reason} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> appealReason() {
                    return Optional.ofNullable(appealReason);
                }

                /**
                 * Returns the {@code creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> creationTime() {
                    return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code report_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> reportId() {
                    return Optional.ofNullable(reportId);
                }

                /**
                 * Returns the {@code appeal_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> appealId() {
                    return Optional.ofNullable(appealId);
                }

                /**
                 * Parses an {@code Appeal} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
                 * Parses a list of {@code Appeal} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
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
             * Parses a {@code ChannelsReports} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
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
             * Parses a list of {@code ChannelsReports} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
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
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_channels_reports} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterReportsJob.mexFetchNewsletterReports
            // Extracts the operation-specific root keyed by xwa2_channels_reports
            var root = data.getJSONObject("xwa2_channels_reports");
            if (root == null) {
                return Optional.empty();
            }

            var channelsReports = ChannelsReports.ofArray(root.getJSONArray("channels_reports"));

            return Optional.of(new Response(channelsReports));
        }
    }
}
