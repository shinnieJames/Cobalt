package com.github.auties00.cobalt.node.mex.json.group;

import com.alibaba.fastjson2.JSON;
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
import java.util.Optional;

/**
 * Outbound MEX query that probes whether a group is flagged as "internal"
 * by the WhatsApp relay.
 *
 * @apiNote Issued from WA Web's
 * {@code WAWebGroupQueryJob.queryGroupAndUpdate} when the
 * {@code internal_group_indicator} AB-prop is enabled and the user is
 * entering the group-info panel; the boolean drives the staff-only
 * indicator badge rendered on Meta-internal testing groups. Most embedders
 * do not need this query.
 *
 * @implNote This implementation issues a single GraphQL variable
 * ({@code id}) and reads the four-way inline-fragment
 * {@code XWA2*Properties.internal} scalar from the response; the four
 * group variants ({@code XWA2GroupRegularGroup},
 * {@code XWA2CommunityGroup}, {@code XWA2CommunityDefaultSubGroup},
 * {@code XWA2CommunitySubGroup}) all surface the flag under the same
 * {@code properties.internal} path so the parser collapses them.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupIsInternalJob")
public final class FetchGroupIsInternalMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchGroupIsInternalJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchGroupIsInternalJobQuery.graphql}. The relay maps
     * this id to its persisted operation; the GraphQL text is never sent on
     * the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupIsInternalJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "34119218944390847";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexFetchGroupIsInternal";

    /**
     * The target group identifier bound to the {@code id} GraphQL variable.
     */
    private final String groupId;

    /**
     * Constructs a new request with the single {@code id} GraphQL variable.
     *
     * @apiNote Forwarded verbatim to the relay; pass {@code null} to omit
     * the variable from the wire payload (matching the WA Web
     * undefined-variable convention).
     *
     * @param groupId the target group identifier, may be {@code null} to omit
     */
    public FetchGroupIsInternalMexRequest(String groupId) {
        this.groupId = groupId;
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
     * @implNote This implementation streams the single {@code id} GraphQL
     * variable through fastjson2's {@link JSONWriter} and wraps the
     * resulting variables object in the standard MEX IQ envelope built
     * through {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupIsInternalJob", exports = "mexFetchGroupIsInternal",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (groupId != null) {
                writer.writeName("id");
                writer.writeColon();
                writer.writeString(groupId);
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
