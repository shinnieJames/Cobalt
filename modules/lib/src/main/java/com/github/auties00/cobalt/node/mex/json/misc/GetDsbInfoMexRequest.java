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
 * Outbound MEX mutation that requests a Data Subject Access Request
 * (DSAR) reference number for the given entity, returning the
 * relay-generated reference so the user can track the request.
 *
 * @apiNote Issued by WA Web's {@code WAWebGetDsbInfoAction.getDsbInfoAction}
 * (via {@code WAWebGetDsbInfoJob.getDsbInfo}) when a newsletter owner taps
 * the data-subject-request button from the newsletter management surface;
 * WA Web tags the call-site with the
 * {@code newsletter},{@code wa-ice},{@code DSAR} log scopes. Cobalt
 * callers receive the reference number through
 * {@link GetDsbInfoMexResponse#referenceNumber()}.
 *
 * @implNote This implementation models the operation as a MEX mutation
 * (the relay logs side effects when it generates the reference) even
 * though the call site reads as a getter. The lone {@code entity_id}
 * variable is omitted from the wire envelope when {@code null}, while WA
 * Web always passes the user-supplied entity id straight through.
 */
@WhatsAppWebModule(moduleName = "WAWebMexGetDsbInfoJob")
public final class GetDsbInfoMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexGetDsbInfoJobMutation} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexGetDsbInfoJobMutation.graphql}. The relay maps the id
     * to a server-side persisted mutation and never sees the GraphQL text
     * on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetDsbInfoJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9982897848413251";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this mutation is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexGetDsbInfo";

    /**
     * The entity identifier bound to the {@code variables.input.entity_id}
     * GraphQL variable.
     */
    private final String entityId;

    /**
     * Constructs a new request targeting the given entity.
     *
     * @apiNote {@code entityId} is the newsletter (or other DSAR-eligible
     * entity) identifier the data-subject request is being filed against;
     * passing {@code null} omits the variable from the wire envelope.
     *
     * @param entityId the entity identifier whose data-subject request
     *                 should be initiated, may be {@code null} to omit
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetDsbInfoJob", exports = "mexGetDsbInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public GetDsbInfoMexRequest(String entityId) {
        this.entityId = entityId;
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
     * fastjson2's {@link JSONWriter}, opens a nested {@code input} object,
     * emits the {@code entity_id} field only when the constructor argument
     * is non-{@code null}, then wraps the payload via
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetDsbInfoJob", exports = "mexGetDsbInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            writer.writeName("input");
            writer.writeColon();
            writer.startObject();
            if (entityId != null) {
                writer.writeName("entity_id");
                writer.writeColon();
                writer.writeString(entityId);
            }
            writer.endObject();
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
