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
 * Outbound MEX mutation that lodges an appeal against a previously submitted
 * channel report, returning the resulting {@link CreateReportAppealMexResponse}
 * payload.
 *
 * @apiNote Issued by WA Web's {@code WAWebCreateReportAppealJob.createReportAppeal}
 * when a channel admin taps "appeal" on a report enforcement notice. The
 * mutation produces a fresh {@code XWA2ChannelsReport} envelope (report id,
 * status, channel name and JID, reported-content data, plus the appeal
 * sub-object carrying the appeal id and state) which WA Web hands to
 * {@code WAWebNewsletterReportModelUtils.mapMexReportToNewsletterReport} so
 * the appeal entry can be rendered immediately. Cobalt callers reuse the
 * envelope verbatim through {@link CreateReportAppealMexResponse}.
 *
 * @implNote This implementation forwards the two GraphQL variables directly;
 * the WA Web wrapper throws a synthetic {@code ServerStatusCodeError(500)}
 * when {@code xwa2_create_channel_report_appeal_v2} is {@code null}, while
 * Cobalt surfaces the same absence as
 * {@link CreateReportAppealMexResponse#of(Node)} returning
 * {@link Optional#empty()}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateReportAppealJob")
public final class CreateReportAppealMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexCreateReportAppealJobMutation} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexCreateReportAppealJobMutation.graphql}. The relay maps
     * the id to a server-side persisted mutation and never sees the GraphQL
     * text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateReportAppealJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "27283301737925761";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this mutation is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "createReportAppeal";

    /**
     * The free-form appeal justification bound to the {@code reason} GraphQL
     * variable.
     */
    private final String reason;

    /**
     * The identifier of the report being contested, bound to the
     * {@code report_id} GraphQL variable.
     */
    private final String reportId;

    /**
     * Constructs a new request with the two GraphQL variables.
     *
     * @apiNote {@code reason} is the user-typed justification surfaced in
     * WA Web's appeal dialog; {@code reportId} identifies the original
     * channel report. Either argument may be {@code null} to omit the
     * variable from the wire payload, mirroring WA Web's fastjson2 omission
     * of undefined GraphQL variables.
     *
     * @param reason   the free-form appeal justification, may be {@code null} to omit
     * @param reportId the identifier of the report being contested, may be {@code null} to omit
     */
    public CreateReportAppealMexRequest(String reason, String reportId) {
        this.reason = reason;
        this.reportId = reportId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation streams the GraphQL variables through
     * fastjson2's {@link JSONWriter} and only emits each field when its
     * corresponding constructor argument is non-{@code null}, matching the
     * WA Web pattern that omits undefined GraphQL variables. The wrapped
     * envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateReportAppealJob", exports = "createReportAppeal",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (reason != null) {
                writer.writeName("reason");
                writer.writeColon();
                writer.writeString(reason);
            }
            if (reportId != null) {
                writer.writeName("report_id");
                writer.writeColon();
                writer.writeString(reportId);
            }
            writer.endObject();
            writer.endObject();
            try (var output = new StringWriter()) {
                writer.flushTo(output);
                return Json.createMexNode(QUERY_ID, output.toString());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
