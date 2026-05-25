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
 * Lodges an appeal against a previously submitted channel report.
 *
 * <p>This mutation is issued when a channel admin contests a report enforcement notice. The relay
 * produces a fresh channel-report envelope (report id, status, channel name and JID,
 * reported-content data, plus the appeal sub-object carrying the appeal id and state) which the
 * caller parses through {@link CreateReportAppealMexResponse} and renders as an updated report
 * record. The two GraphQL variables, {@link #reason} and {@link #reportId}, are forwarded
 * verbatim, and either may be {@code null} to omit it from the wire payload.
 *
 * @implNote This implementation surfaces a missing
 * {@code data.xwa2_create_channel_report_appeal_v2} envelope as
 * {@link CreateReportAppealMexResponse#of(Node)} returning {@link Optional#empty()} rather than
 * raising a synthetic server error.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateReportAppealJob")
public final class CreateReportAppealMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled GraphQL query identifier for the report-appeal mutation document.
     *
     * <p>The relay maps this identifier to a server-side persisted mutation and never sees the
     * GraphQL text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateReportAppealJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "27283301737925761";

    /**
     * Holds the GraphQL operation name reported to the MEX perf tracker when this mutation is
     * dispatched.
     *
     * <p>The name tags the mutation in latency and error metrics; it is kept on the request so
     * embedders mirroring that telemetry surface can emit the same tag.
     */
    public static final String OPERATION_NAME = "createReportAppeal";

    /**
     * Holds the free-form appeal justification bound to the {@code reason} GraphQL variable.
     */
    private final String reason;

    /**
     * Holds the identifier of the report being contested, bound to the {@code report_id} GraphQL
     * variable.
     */
    private final String reportId;

    /**
     * Constructs a new request with the two GraphQL variables.
     *
     * <p>The {@code reason} is the user-typed appeal justification and {@code reportId} identifies
     * the original channel report. Either argument may be {@code null} to omit the corresponding
     * variable from the wire payload.
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
     * @implNote This implementation streams the GraphQL variables through fastjson2's
     * {@link JSONWriter} and emits each field only when its corresponding constructor argument is
     * non-{@code null}, then wraps the payload through
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
