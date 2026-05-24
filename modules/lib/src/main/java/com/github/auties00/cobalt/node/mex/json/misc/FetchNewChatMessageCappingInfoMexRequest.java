package com.github.auties00.cobalt.node.mex.json.misc;

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
 * Outbound MEX query that fetches the new-chat messaging quota counters
 * and per-policy status flags currently enforced on the account.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebGetNewChatMessageCappingInfoJob.fetchOrUpdateCapStatus} when
 * the individual new-chat messaging capping feature is enabled. WA Web
 * persists the parsed snapshot under
 * {@code WAWebIndividualNewChatMessageCappingLimitUtils.NEW_CHAT_MESSAGE_CAPPING_IDB_KEY}
 * in user-prefs IDB, then dispatches
 * {@code individualNewChatMessageCappingStateChange} to the frontend so
 * UI surfaces can refresh; Cobalt callers may persist the snapshot in
 * their own store and act on the {@code capping_status},
 * {@code ote_status} and {@code mv_status} flags without invoking the
 * WA Web frontend pipeline.
 *
 * @implNote This implementation forwards the {@code input} variable as an
 * opaque caller-supplied JSON string; WA Web hard-codes
 * {@code {"type":"INDIVIDUAL_NEW_CHAT_THREAD"}} at the call-site. Cobalt
 * leaves the choice to the caller because the surrounding
 * {@code MessageCappingWamEvent} telemetry instrumentation (action
 * {@code fetch_capping_data}/{@code fetch_capping_data_response}) is not
 * mirrored.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJob")
public final class FetchNewChatMessageCappingInfoMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchNewChatMessageCappingInfoJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchNewChatMessageCappingInfoJobQuery.graphql}. The
     * relay maps the id to a server-side persisted operation and never
     * sees the GraphQL text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "24503548349331633";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexFetchNewChatMessageCapping";

    /**
     * The serialised JSON payload bound to the {@code input} GraphQL
     * variable.
     *
     * @apiNote WA Web sends
     * {@snippet :
     * String input = "{\"type\":\"INDIVIDUAL_NEW_CHAT_THREAD\"}";
     * }
     * but the wire schema allows any caller-defined shape.
     */
    private final String input;

    /**
     * Constructs a new request with the serialised {@code input} GraphQL
     * variable.
     *
     * @apiNote The caller is responsible for producing the JSON payload;
     * passing {@code null} omits the variable from the wire envelope.
     *
     * @param input the serialised {@code input} JSON payload, may be {@code null} to omit
     */
    public FetchNewChatMessageCappingInfoMexRequest(String input) {
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
     * fastjson2's {@link JSONWriter}, emits the {@code input} string only
     * when the constructor argument is non-{@code null}, then wraps the
     * payload via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewChatMessageCappingInfoJobQuery.graphql", exports = "params.id",
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
