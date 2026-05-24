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
 * Outbound MEX query that reads the invite code currently bound to a group
 * without rotating it.
 *
 * @apiNote Surfaced from WA Web's
 * {@code WAWebGroupInviteAction.queryGroupInviteCode} when chat UIs need to
 * display the shareable {@code chat.whatsapp.com/<code>} link, prime the
 * group-info panel, or copy the link to the clipboard. To rotate the code
 * instead of reading it, use {@link CreateInviteCodeMexRequest}.
 *
 * @implNote This implementation forwards an optional caller-supplied
 * {@code query_context} variable; WA Web pins it to the constant
 * {@code "INVITE_CODE"} at the only call site.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupInviteCodeJob")
public final class FetchGroupInviteCodeMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchGroupInviteCodeJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchGroupInviteCodeJobQuery.graphql}. The relay maps
     * this id to its persisted operation; the GraphQL text is never sent on
     * the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInviteCodeJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "29247029834912157";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "fetchMexGroupInviteCode";

    /**
     * The target group identifier bound to the {@code id} GraphQL variable.
     */
    private final String id;

    /**
     * The telemetry context tag bound to the {@code query_context} GraphQL
     * variable.
     */
    private final String queryContext;

    /**
     * Constructs a new request with the two GraphQL variables.
     *
     * @apiNote {@code queryContext} mirrors the WA Web
     * {@code "INVITE_CODE"} tag pinned at
     * {@code WAWebMexFetchGroupInviteCodeJob.fetchMexGroupInviteCode}; pass
     * {@code null} to omit the optional variable from the wire payload.
     *
     * @param id           the target group identifier
     * @param queryContext the optional telemetry context tag, may be {@code null} to omit
     */
    public FetchGroupInviteCodeMexRequest(String id, String queryContext) {
        this.id = id;
        this.queryContext = queryContext;
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
     * fastjson2's {@link JSONWriter} and only emits the {@code id} and
     * {@code query_context} fields when their corresponding constructor
     * argument is non-null, matching the WA Web pattern that omits
     * undefined GraphQL variables. The wrapped envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInviteCodeJob", exports = "fetchMexGroupInviteCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (id != null) {
                writer.writeName("id");
                writer.writeColon();
                writer.writeString(id);
            }
            if (queryContext != null) {
                writer.writeName("query_context");
                writer.writeColon();
                writer.writeString(queryContext);
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
