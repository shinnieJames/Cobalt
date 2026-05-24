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
 * Outbound MEX mutation that updates a single property on a group (member
 * add mode, announcement-only flag, addressing-mode override, etc.) by
 * issuing the corresponding GraphQL operation.
 *
 * @apiNote Surfaces of the same operation in WA Web include
 * {@code WAWebSetPropertyGroupAction.setGroupProperty}, which batches one
 * mutation per property under {@code WAPromiseEach}, and
 * {@code WAWebDebugLidMigration}, which forces an
 * {@code addressing_mode_override} for tooling purposes. The relay rejects
 * the mutation with HTTP 405 when the group is not {@code ACTIVE}.
 *
 * @implNote This implementation accepts the property-update payload as an
 * opaque pre-serialised JSON string for the {@code update} GraphQL
 * variable. WA Web builds the same payload from a typed
 * {@code GroupPropertyUpdate} object at the call site; Cobalt leaves
 * payload composition to the caller to avoid duplicating the WA Web
 * property-codec surface.
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateGroupPropertyJob")
public final class UpdateGroupPropertyMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexUpdateGroupPropertyJobMutation} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexUpdateGroupPropertyJobMutation.graphql}. The relay
     * maps this id to its persisted operation; the GraphQL text is never
     * sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateGroupPropertyJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9418211574894172";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this mutation is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface. WA Web's exporter
     * function is named {@code mexUpdateGroupPropertyJob} while the
     * GraphQL document name is {@code mexUpdateGroupProperty}.
     */
    public static final String OPERATION_NAME = "mexUpdateGroupProperty";

    /**
     * The target group id bound to the {@code group_id} GraphQL variable.
     */
    private final String groupId;

    /**
     * The pre-serialised property update payload bound to the
     * {@code update} GraphQL variable.
     */
    private final String update;

    /**
     * Constructs a new request with the two GraphQL variables.
     *
     * @apiNote {@code update} is the pre-serialised JSON encoding of the
     * single-property mutation (e.g.
     * {@snippet :
     *     // change addressing mode override
     *     var update = "{\"addressing_mode_override\":{\"addressing_mode\":\"LID\"}}";
     *     new UpdateGroupPropertyMexRequest(groupId, update);
     * }
     * Either argument may be {@code null} to omit it from the wire payload,
     * matching the WA Web undefined-variable convention; an empty mutation
     * is normally rejected by the relay.
     *
     * @param groupId the target group id, may be {@code null} to omit
     * @param update  the pre-serialised JSON property-update payload, may be {@code null} to omit
     */
    public UpdateGroupPropertyMexRequest(String groupId, String update) {
        this.groupId = groupId;
        this.update = update;
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
     * fastjson2's {@link JSONWriter} and only emits the {@code group_id}
     * and {@code update} fields when their corresponding constructor
     * argument is non-null, matching the WA Web pattern that omits
     * undefined GraphQL variables. The {@code update} value is written as
     * a JSON-string literal (its content is forwarded verbatim, not
     * re-parsed), mirroring the WA Web call shape where the variable is
     * already a JSON-serialisable scalar.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateGroupPropertyJob", exports = "mexUpdateGroupPropertyJob",
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
            if (update != null) {
                writer.writeName("update");
                writer.writeColon();
                writer.writeString(update);
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
