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
 * Outbound MEX query that fetches the current reachout-timelock
 * enforcement state for the account, a cooldown window during which
 * outgoing contact attempts are restricted following spam reports or
 * suspicious-account-behaviour signals.
 *
 * @apiNote Issued by WA Web's
 * {@code WAWebGetReachoutTimelockJob.fetchReachoutTimelock}; the
 * resulting snapshot is forwarded to
 * {@code WAWebMexReachoutTimelockNotificationHandler.handleReachoutTimelockUpdate}
 * which gates the reach-out UI and emits the matching notification.
 * Cobalt embedders may apply the gating themselves using the three
 * scalars exposed by {@link FetchReachoutTimelockMexResponse}.
 *
 * @implNote This implementation emits an empty {@code variables} object
 * because the compiled GraphQL artifact takes no inputs; the WA Web
 * call-site likewise passes an empty object literal before awaiting the
 * relay response.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchReachoutTimelockJob")
public final class FetchReachoutTimelockMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexFetchReachoutTimelockJobQuery} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexFetchReachoutTimelockJobQuery.graphql}. The relay
     * maps the id to a server-side persisted operation and never sees the
     * GraphQL text on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchReachoutTimelockJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "23983697327930364";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this query is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexFetchReachoutTimelock";

    /**
     * Constructs a new request carrying no GraphQL variables.
     *
     * @apiNote The compiled GraphQL artifact takes no inputs so a single
     * instance is sufficient for every dispatch.
     */
    public FetchReachoutTimelockMexRequest() {
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
     * @implNote This implementation writes an empty {@code variables}
     * object using fastjson2's {@link JSONWriter} and wraps it via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchReachoutTimelockJobQuery.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
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
