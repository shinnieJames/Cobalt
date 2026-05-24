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
 * Outbound MEX request that fetches the list of public subgroups suggested
 * to the user as candidates for a given community.
 *
 * @apiNote Drives the "suggested subgroups" picker in the community
 * management UI. Suggestions include existing groups the user already
 * belongs to that could be moved into the community; each entry carries
 * id, subject, description, creator, creation timestamp, participant count
 * and the hidden-from-directory flag. Surfaced from
 * {@code WAWebQueryAndUpdateSubgroupSuggestionsJob} via
 * {@code WAWebMexFetchSubgroupSuggestionsJob.mexFetchSubgroupSuggestions},
 * called with {@code query_context="INTERACTIVE"} when the user opens the
 * picker.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchSubgroupSuggestionsJob")
public final class FetchSubgroupSuggestionsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchSubgroupSuggestionsJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchSubgroupSuggestionsJobQuery.graphql}. The relay
     * maps this id to its persisted operation; the GraphQL text is never
     * sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "23972005349071865";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexFetchSubgroupSuggestions";

    private final String groupId;
    private final String queryContext;
    private final String subGroupHintId;

    /**
     * Constructs a new request with the three GraphQL variables.
     *
     * @apiNote {@code queryContext} mirrors the WA Web {@code "INTERACTIVE"}
     * marker used when the user is browsing the picker. The
     * {@code subGroupHintId} lets the relay prioritise suggestions around a
     * known subgroup; all three fields are dropped from the wire payload
     * when {@code null}.
     *
     * @param groupId        the community parent group id, may be
     *                       {@code null} to omit
     * @param queryContext   the optional query-context tag, may be
     *                       {@code null} to omit
     * @param subGroupHintId the optional subgroup hint identifier, may be
     *                       {@code null} to omit
     */
    public FetchSubgroupSuggestionsMexRequest(String groupId, String queryContext, String subGroupHintId) {
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
     * fields when their corresponding constructor argument is non-null.
     * The wrapped envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     *
     * @return the IQ {@link NodeBuilder} ready to be built and dispatched
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
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
