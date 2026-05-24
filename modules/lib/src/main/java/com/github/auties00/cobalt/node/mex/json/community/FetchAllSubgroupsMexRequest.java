package com.github.auties00.cobalt.node.mex.json.community;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Outbound MEX request that fetches every subgroup belonging to a WhatsApp
 * community.
 *
 * @apiNote Primary query issued when loading a community. The response
 * carries the default announcement subgroup (always a single group) and the
 * full edge list of regular subgroups, each tagged with id, subject,
 * properties (general-chat flag, membership-approval mode, hidden flag) and
 * the pending membership-approval-request counter scoped to the current
 * user. Surfaced from {@code WAWebQuerySubGroupAction} via
 * {@code WAWebMexFetchAllSubgroupsJob.mexFetchAllSubgroups}, called with
 * {@code query_context="INTERACTIVE"} when the user opens the community
 * panel.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAllSubgroupsJob")
public final class FetchAllSubgroupsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchAllSubgroupsJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchAllSubgroupsJobQuery.graphql}. The relay maps this
     * id to its persisted operation; the GraphQL text is never sent on the
     * wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9935467776504344";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface. The WA Web operation function is
     * called {@code mexFetchAllSubgroups} but the GraphQL document name is
     * {@code mexCommunityGetSubgroups}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexCommunityGetSubgroups";

    private final String groupId;
    private final String queryContext;
    private final String subGroupHintId;

    /**
     * Constructs a new request with the three GraphQL variables.
     *
     * @apiNote The {@code queryContext} tag mirrors the WA Web
     * {@code "INTERACTIVE"} marker used when the user is browsing the
     * community panel; pass {@code null} to omit it. {@code subGroupHintId}
     * lets the relay prioritise edges around a known subgroup; both
     * optional fields are dropped from the wire payload when {@code null}.
     *
     * @param groupId        the community parent group id, may be
     *                       {@code null} to omit
     * @param queryContext   the optional query-context tag, may be
     *                       {@code null} to omit
     * @param subGroupHintId the optional subgroup hint identifier, may be
     *                       {@code null} to omit
     */
    public FetchAllSubgroupsMexRequest(String groupId, String queryContext, String subGroupHintId) {
        this.groupId = groupId;
        this.queryContext = queryContext;
        this.subGroupHintId = subGroupHintId;
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
     * fastjson2's {@link JSONWriter} and only emits the optional
     * {@code group_id}, {@code query_context} and {@code sub_group_hint_id}
     * fields when their corresponding constructor argument is non-null,
     * matching the WA Web {@code t == null ? void 0 : t.toString()} pattern
     * that omits undefined GraphQL variables. The wrapped envelope is built
     * through {@link MexOperation.Request.Json#createMexNode(String, String)}.
     *
     * @return the IQ {@link NodeBuilder} ready to be built and dispatched
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (groupId != null) {
                writer.writeName("group_id");
                writer.writeColon();
                writer.writeString(groupId);
            }
            if (queryContext != null) {
                writer.writeName("query_context");
                writer.writeColon();
                writer.writeString(queryContext);
            }
            if (subGroupHintId != null) {
                writer.writeName("sub_group_hint_id");
                writer.writeColon();
                writer.writeString(subGroupHintId);
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
