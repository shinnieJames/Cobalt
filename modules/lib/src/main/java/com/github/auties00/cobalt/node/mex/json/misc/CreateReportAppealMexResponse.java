package com.github.auties00.cobalt.node.mex.json.misc;

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
 * Exposes the parsed {@code xwa2_create_channel_report_appeal_v2} envelope returned by the
 * {@link CreateReportAppealMexRequest} mutation.
 *
 * <p>The envelope projects the updated channel-report record so callers can render an appeal entry
 * on a report management surface. Each top-level scalar is exposed as an {@link Optional}, the two
 * epoch-second timestamps as {@link Instant}, and the appeal-specific scalars through the nested
 * {@link Appeal} record.
 *
 * @implNote This implementation flattens the {@code reported_content_data} inline-fragment block
 * into three independent {@link Optional} scalars (server message id, server response id, notify
 * name) rather than preserving the fragment nesting.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateReportAppealJob")
public final class CreateReportAppealMexResponse implements MexOperation.Response.Json {
    /**
     * Holds the {@code report_id} scalar projected from the envelope.
     */
    private final String reportId;

    /**
     * Holds the {@code status} scalar projected from the envelope.
     */
    private final String status;

    /**
     * Holds the {@code creation_time} scalar (Unix epoch second) projected from the envelope.
     */
    private final Long creationTime;

    /**
     * Holds the {@code last_update_time} scalar (Unix epoch second) projected from the envelope.
     */
    private final Long lastUpdateTime;

    /**
     * Holds the {@code channel_name} scalar projected from the envelope.
     */
    private final String channelName;

    /**
     * Holds the {@code channel_jid} scalar projected from the envelope.
     */
    private final String channelJid;

    /**
     * Holds the {@code server_msg_id} scalar flattened from the {@code reported_content_data}
     * inline-fragment block.
     */
    private final String serverMsgId;

    /**
     * Holds the {@code server_response_id} scalar flattened from the {@code reported_content_data}
     * inline-fragment block.
     */
    private final String responseServerMsgId;

    /**
     * Holds the {@code notify_name} scalar flattened from the {@code reported_content_data}
     * inline-fragment block.
     */
    private final String notifyName;

    /**
     * Holds the parsed {@code appeal} sub-object carrying the appeal-specific scalars.
     */
    private final Appeal appeal;

    /**
     * Constructs a new response wrapping the parsed scalar and nested fields of the
     * {@code xwa2_create_channel_report_appeal_v2} envelope.
     *
     * <p>Instances are produced only by the {@link #of(Node)} parser.
     *
     * @param reportId             the {@code report_id} scalar, may be {@code null}
     * @param status               the {@code status} scalar, may be {@code null}
     * @param creationTime         the {@code creation_time} scalar (Unix epoch second), may be {@code null}
     * @param lastUpdateTime       the {@code last_update_time} scalar (Unix epoch second), may be {@code null}
     * @param channelName          the {@code channel_name} scalar, may be {@code null}
     * @param channelJid           the {@code channel_jid} scalar, may be {@code null}
     * @param serverMsgId          the {@code server_msg_id} scalar, may be {@code null}
     * @param responseServerMsgId  the {@code server_response_id} scalar, may be {@code null}
     * @param notifyName           the {@code notify_name} scalar, may be {@code null}
     * @param appeal               the parsed {@link Appeal} sub-object, may be {@code null}
     */
    private CreateReportAppealMexResponse(String reportId, String status, Long creationTime, Long lastUpdateTime, String channelName, String channelJid, String serverMsgId, String responseServerMsgId, String notifyName, Appeal appeal) {
        this.reportId = reportId;
        this.status = status;
        this.creationTime = creationTime;
        this.lastUpdateTime = lastUpdateTime;
        this.channelName = channelName;
        this.channelJid = channelJid;
        this.serverMsgId = serverMsgId;
        this.responseServerMsgId = responseServerMsgId;
        this.notifyName = notifyName;
        this.appeal = appeal;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * <p>Reads the {@code <result>} child's byte content and routes it through the private
     * byte-level parser. Yields {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_create_channel_report_appeal_v2} envelope is absent.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateReportAppealJob", exports = "createReportAppeal",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<CreateReportAppealMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(CreateReportAppealMexResponse::of);
    }

    /**
     * Returns the {@code report_id} scalar projected from the envelope.
     *
     * @return an {@link Optional} containing the report identifier, or {@link Optional#empty()} if
     *         the relay omitted the scalar
     */
    public Optional<String> reportId() {
        return Optional.ofNullable(reportId);
    }

    /**
     * Returns the {@code status} scalar projected from the envelope.
     *
     * @return an {@link Optional} containing the report status, or {@link Optional#empty()} if the
     *         relay omitted the scalar
     */
    public Optional<String> status() {
        return Optional.ofNullable(status);
    }

    /**
     * Returns the {@code creation_time} scalar converted from Unix epoch seconds to an
     * {@link Instant}.
     *
     * @return an {@link Optional} containing the creation instant, or {@link Optional#empty()} if
     *         the relay omitted the scalar
     */
    public Optional<Instant> creationTime() {
        return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
    }

    /**
     * Returns the {@code last_update_time} scalar converted from Unix epoch seconds to an
     * {@link Instant}.
     *
     * @return an {@link Optional} containing the last update instant, or {@link Optional#empty()}
     *         if the relay omitted the scalar
     */
    public Optional<Instant> lastUpdateTime() {
        return Optional.ofNullable(lastUpdateTime).map(Instant::ofEpochSecond);
    }

    /**
     * Returns the {@code channel_name} scalar projected from the envelope.
     *
     * @return an {@link Optional} containing the channel name, or {@link Optional#empty()} if the
     *         relay omitted the scalar
     */
    public Optional<String> channelName() {
        return Optional.ofNullable(channelName);
    }

    /**
     * Returns the {@code channel_jid} scalar projected from the envelope.
     *
     * @return an {@link Optional} containing the channel JID, or {@link Optional#empty()} if the
     *         relay omitted the scalar
     */
    public Optional<String> channelJid() {
        return Optional.ofNullable(channelJid);
    }

    /**
     * Returns the {@code server_msg_id} scalar flattened from the {@code reported_content_data}
     * inline-fragment block.
     *
     * @return an {@link Optional} containing the server message id, or {@link Optional#empty()} if
     *         the relay omitted the scalar
     */
    public Optional<String> serverMsgId() {
        return Optional.ofNullable(serverMsgId);
    }

    /**
     * Returns the {@code server_response_id} scalar flattened from the
     * {@code reported_content_data} inline-fragment block.
     *
     * @return an {@link Optional} containing the server response id, or {@link Optional#empty()} if
     *         the relay omitted the scalar
     */
    public Optional<String> responseServerMsgId() {
        return Optional.ofNullable(responseServerMsgId);
    }

    /**
     * Returns the {@code notify_name} scalar flattened from the {@code reported_content_data}
     * inline-fragment block.
     *
     * @return an {@link Optional} containing the reporter notify name, or {@link Optional#empty()}
     *         if the relay omitted the scalar
     */
    public Optional<String> notifyName() {
        return Optional.ofNullable(notifyName);
    }

    /**
     * Returns the parsed {@code appeal} sub-object exposing the appeal-specific scalars.
     *
     * @return an {@link Optional} containing the parsed {@link Appeal}, or {@link Optional#empty()}
     *         if the relay omitted the sub-object
     */
    public Optional<Appeal> appeal() {
        return Optional.ofNullable(appeal);
    }

    /**
     * Holds the parsed projection of the {@code appeal} sub-object nested under
     * {@code xwa2_create_channel_report_appeal_v2}.
     *
     * <p>Carries the appeal-specific scalars (state, reason, creation timestamp, original report
     * id, generated appeal id) that drive the appeal timeline entry on a report management surface.
     */
    public static final class Appeal {
        /**
         * Holds the {@code state} scalar of the appeal (for example {@code "PENDING"},
         * {@code "ACCEPTED"}, {@code "REJECTED"}).
         */
        private final String state;

        /**
         * Holds the {@code appeal_reason} scalar carrying the free-form justification submitted by
         * the appellant.
         */
        private final String appealReason;

        /**
         * Holds the {@code creation_time} scalar (Unix epoch second) marking when the appeal record
         * was created.
         */
        private final Long creationTime;

        /**
         * Holds the {@code report_id} scalar identifying the original report this appeal targets.
         */
        private final String reportId;

        /**
         * Holds the {@code appeal_id} scalar carrying the relay-assigned appeal identifier.
         */
        private final String appealId;

        /**
         * Constructs a new appeal record from the parsed scalar fields.
         *
         * <p>Instances are produced only by the {@link #of(JSONObject)} parser.
         *
         * @param state         the {@code state} scalar, may be {@code null}
         * @param appealReason  the {@code appeal_reason} scalar, may be {@code null}
         * @param creationTime  the {@code creation_time} scalar (Unix epoch second), may be {@code null}
         * @param reportId      the {@code report_id} scalar, may be {@code null}
         * @param appealId      the {@code appeal_id} scalar, may be {@code null}
         */
        private Appeal(String state, String appealReason, Long creationTime, String reportId, String appealId) {
            this.state = state;
            this.appealReason = appealReason;
            this.creationTime = creationTime;
            this.reportId = reportId;
            this.appealId = appealId;
        }

        /**
         * Returns the {@code state} scalar of the appeal.
         *
         * @return an {@link Optional} containing the appeal state, or {@link Optional#empty()} if
         *         the relay omitted the scalar
         */
        public Optional<String> state() {
            return Optional.ofNullable(state);
        }

        /**
         * Returns the {@code appeal_reason} scalar.
         *
         * @return an {@link Optional} containing the appeal reason, or {@link Optional#empty()} if
         *         the relay omitted the scalar
         */
        public Optional<String> appealReason() {
            return Optional.ofNullable(appealReason);
        }

        /**
         * Returns the {@code creation_time} scalar converted from Unix epoch seconds to an
         * {@link Instant}.
         *
         * @return an {@link Optional} containing the appeal creation instant, or
         *         {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<Instant> creationTime() {
            return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the {@code report_id} scalar identifying the original report.
         *
         * @return an {@link Optional} containing the report identifier, or {@link Optional#empty()}
         *         if the relay omitted the scalar
         */
        public Optional<String> reportId() {
            return Optional.ofNullable(reportId);
        }

        /**
         * Returns the {@code appeal_id} scalar carrying the relay-assigned appeal identifier.
         *
         * @return an {@link Optional} containing the appeal identifier, or {@link Optional#empty()}
         *         if the relay omitted the scalar
         */
        public Optional<String> appealId() {
            return Optional.ofNullable(appealId);
        }

        /**
         * Parses a single {@link Appeal} record from the given JSON object.
         *
         * <p>Used by the enclosing {@link CreateReportAppealMexResponse#of(byte[])} byte-level
         * parser and by {@link #ofArray(JSONArray)} when an appeal collection is returned. Yields
         * {@link Optional#empty()} when {@code obj} is {@code null}.
         *
         * @param obj the JSON object carrying the appeal scalars, may be {@code null}
         * @return an {@link Optional} wrapping the parsed appeal, or {@link Optional#empty()} if
         *         {@code obj} is {@code null}
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
         * Parses a list of {@link Appeal} records from the given JSON array.
         *
         * <p>Walks every element through {@link #of(JSONObject)}; {@code null} entries inside the
         * array are skipped. A {@code null} array collapses to {@link List#of()}.
         *
         * @param arr the JSON array carrying the appeal records, may be {@code null}
         * @return an unmodifiable list of parsed appeals, empty when {@code arr} is {@code null}
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
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link CreateReportAppealMexResponse}.
     *
     * <p>Routed through {@link #of(Node)} after the byte content of the {@code <result>} child is
     * extracted. Yields {@link Optional#empty()} when the envelope, the {@code data} branch, or the
     * {@code xwa2_create_channel_report_appeal_v2} child is absent.
     *
     * @implNote This implementation collapses the three {@code reported_content_data} scalars to
     * {@code null} when that inline-fragment block is absent rather than throwing, leaving the
     * choice of how to surface absence to the caller via {@link Optional}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or {@link Optional#empty()} if the
     *         {@code data.xwa2_create_channel_report_appeal_v2} envelope is absent
     */
    private static Optional<CreateReportAppealMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_create_channel_report_appeal_v2");
        if (root == null) {
            return Optional.empty();
        }

        var reportId = root.getString("report_id");
        var status = root.getString("status");
        var creationTime = root.getLong("creation_time");
        var lastUpdateTime = root.getLong("last_update_time");
        var channelName = root.getString("channel_name");
        var channelJid = root.getString("channel_jid");
        var reportedContentData = root.getJSONObject("reported_content_data");
        var serverMsgId = reportedContentData == null ? null : reportedContentData.getString("server_msg_id");
        var responseServerMsgId = reportedContentData == null ? null : reportedContentData.getString("server_response_id");
        var notifyName = reportedContentData == null ? null : reportedContentData.getString("notify_name");
        var appeal = Appeal.of(root.getJSONObject("appeal")).orElse(null);

        return Optional.of(new CreateReportAppealMexResponse(reportId, status, creationTime, lastUpdateTime, channelName, channelJid, serverMsgId, responseServerMsgId, notifyName, appeal));
    }
}
