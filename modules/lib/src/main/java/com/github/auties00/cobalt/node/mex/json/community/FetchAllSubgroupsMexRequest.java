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
 * <p>This is the primary query issued when a community is loaded. The reply,
 * modelled by {@link FetchAllSubgroupsMexResponse}, carries the default
 * announcement subgroup (always a single group) and the full edge list of
 * regular subgroups, each tagged with its id, subject, property flags
 * (general-chat, membership-approval mode, hidden) and the pending
 * membership-approval-request counter scoped to the current user. The
 * {@code queryContext} variable is set to {@code "INTERACTIVE"} when the user
 * opens the community panel.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchAllSubgroupsJob")
public final class FetchAllSubgroupsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the all-subgroups document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9935467776504344";

    /**
     * GraphQL operation name carried by this query.
     *
     * @implNote The WA Web operation function is named
     * {@code mexFetchAllSubgroups}, but the GraphQL document name reported for
     * telemetry is {@code mexCommunityGetSubgroups}; this constant keeps the
     * document name.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchAllSubgroupsJob", exports = "mexFetchAllSubgroups",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexCommunityGetSubgroups";

    /**
     * Community parent group identifier, or {@code null} to omit it.
     */
    private final String groupId;

    /**
     * Query-context tag, or {@code null} to omit it.
     */
    private final String queryContext;

    /**
     * Subgroup hint identifier, or {@code null} to omit it.
     */
    private final String subGroupHintId;

    /**
     * Constructs a new request with the three GraphQL variables.
     *
     * <p>The {@code queryContext} tag is set to {@code "INTERACTIVE"} when the
     * user is browsing the community panel. The {@code subGroupHintId} lets
     * the relay prioritise edges around a known subgroup. Each variable may be
     * {@code null}, in which case it is dropped from the wire payload.
     *
     * @param groupId        the community parent group id, may be {@code null}
     * @param queryContext   the query-context tag, may be {@code null}
     * @param subGroupHintId the subgroup hint identifier, may be {@code null}
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
     * fastjson2's {@link JSONWriter} and emits each of {@code group_id},
     * {@code query_context} and {@code sub_group_hint_id} only when its
     * constructor argument is non-null, matching WA Web's pattern of omitting
     * undefined GraphQL variables. The envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
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
