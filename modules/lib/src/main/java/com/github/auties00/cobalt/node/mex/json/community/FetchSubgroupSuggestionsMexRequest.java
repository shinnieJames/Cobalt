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
 * Outbound MEX request that fetches the list of public subgroups suggested to
 * the user as candidates for a given community.
 *
 * <p>This query backs the suggested-subgroups picker in the community
 * management UI. Suggestions include existing groups the user already belongs
 * to that could be promoted into the community; the reply, modelled by
 * {@link FetchSubgroupSuggestionsMexResponse}, carries each candidate's id,
 * subject, description, creator, creation timestamp, participant count and the
 * hidden-from-directory flag. The {@code queryContext} variable is set to
 * {@code "INTERACTIVE"} when the user opens the picker.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchSubgroupSuggestionsJob")
public final class FetchSubgroupSuggestionsMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the subgroup-suggestions document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "23972005349071865";

    /**
     * GraphQL operation name carried by this query.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchSubgroupSuggestionsJob", exports = "mexFetchSubgroupSuggestions",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexFetchSubgroupSuggestions";

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
     * user is browsing the picker. The {@code subGroupHintId} lets the relay
     * prioritise suggestions around a known subgroup. Each variable may be
     * {@code null}, in which case it is dropped from the wire payload.
     *
     * @param groupId        the community parent group id, may be {@code null}
     * @param queryContext   the query-context tag, may be {@code null}
     * @param subGroupHintId the subgroup hint identifier, may be {@code null}
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
     * fastjson2's {@link JSONWriter} and emits each of {@code group_id},
     * {@code query_context} and {@code sub_group_hint_id} only when its
     * constructor argument is non-null. The envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
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
