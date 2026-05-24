package com.github.auties00.cobalt.node.mex.json.community;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Outbound MEX request that fetches the current participant count for one
 * or more subgroups inside a community.
 *
 * @apiNote Drives the community-panel participant-count refresh path:
 * fetches only the {@code id} and {@code total_participants_count} fields
 * rather than reloading full subgroup metadata. Surfaced from
 * {@code WAWebQueryAndUpdateSubgroupParticipantCountAction} via
 * {@code WAWebMexQuerySubgroupParticipantCountJob.mexQuerySubgroupParticipantCountJob}
 * (called with {@code query_context="INTERACTIVE"}) typically when the user
 * scrolls or sorts the community subgroup list.
 *
 * @implNote This implementation accepts the GraphQL {@code input} variable
 * as a single opaque pre-serialised JSON string rather than modelling its
 * inner shape ({@code group_jid}, {@code query_context},
 * {@code sub_group_jid_hint}). Callers serialise the input themselves and
 * pass the resulting JSON; the field is dropped from the wire payload when
 * {@code null}.
 */
public final class QuerySubgroupParticipantCountMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexQuerySubgroupParticipantCountJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexQuerySubgroupParticipantCountJobQuery.graphql}. The
     * relay maps this id to its persisted operation; the GraphQL text is
     * never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "24079399904996141";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in latency
     * and error metrics; Cobalt keeps the name on the request for embedders
     * mirroring WA Web's telemetry surface.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.name",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexQuerySubgroupParticipantCount";

    private final String input;

    /**
     * Constructs a new request carrying the serialised input payload.
     *
     * @apiNote The WA Web {@code input} variable is a nested object of the
     * shape {@code {"group_jid": "...", "query_context": "...",
     * "sub_group_jid_hint": "..."}}. Callers serialise this themselves and
     * pass the resulting JSON string; passing {@code null} omits the field
     * entirely from the wire payload.
     *
     * @param input the serialised input variable, may be {@code null} to
     *              omit
     */
    public QuerySubgroupParticipantCountMexRequest(String input) {
        this.input = input;
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
     * fastjson2's {@link JSONWriter} and only emits the {@code input} field
     * when the constructor argument is non-null, matching the WA Web
     * pattern that omits undefined GraphQL variables. The wrapped envelope
     * is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     *
     * @return the IQ {@link NodeBuilder} ready to be built and dispatched
     */
    @WhatsAppWebExport(moduleName = "WAWebMexQuerySubgroupParticipantCountJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (input != null) {
                writer.writeName("input");
                writer.writeColon();
                writer.writeString(input);
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
