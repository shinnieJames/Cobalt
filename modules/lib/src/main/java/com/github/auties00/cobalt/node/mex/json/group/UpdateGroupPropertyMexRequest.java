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
 * Outbound MEX mutation that updates a single property on a group (member add mode, announcement-only
 * flag, addressing-mode override, and similar) by issuing the corresponding GraphQL operation.
 *
 * <p>The relay rejects the mutation with HTTP 405 when the group is not {@code ACTIVE}. WA Web reuses
 * this operation both to set group properties one mutation at a time and to force an
 * addressing-mode override for tooling purposes.
 *
 * @implNote This implementation accepts the property-update payload as an opaque pre-serialised JSON
 * string for the {@code update} GraphQL variable, leaving payload composition to the caller; WA Web
 * builds the same payload from a typed property-update object at the call site. Cobalt does not
 * duplicate that property-codec surface.
 */
@WhatsAppWebModule(moduleName = "WAWebMexUpdateGroupPropertyJob")
public final class UpdateGroupPropertyMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the {@code WAWebMexUpdateGroupPropertyJobMutation}
     * document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text is never sent on the
     * wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexUpdateGroupPropertyJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "9418211574894172";

    /**
     * GraphQL operation name reported alongside this mutation when it is dispatched.
     *
     * <p>Tags the query in latency and error metrics; kept on the request for embedders mirroring
     * WhatsApp's telemetry surface.
     *
     * @implNote This implementation uses the GraphQL document name {@code mexUpdateGroupProperty},
     * which differs from the WA Web exporter function name {@code mexUpdateGroupPropertyJob}.
     */
    public static final String OPERATION_NAME = "mexUpdateGroupProperty";

    /**
     * Target group id bound to the {@code group_id} GraphQL variable.
     */
    private final String groupId;

    /**
     * Pre-serialised property update payload bound to the {@code update} GraphQL variable.
     */
    private final String update;

    /**
     * Constructs a new request with the two GraphQL variables.
     *
     * <p>The {@code update} is the pre-serialised JSON encoding of the single-property mutation, for
     * example:
     * {@snippet :
     *     var update = "{\"addressing_mode_override\":{\"addressing_mode\":\"LID\"}}";
     *     new UpdateGroupPropertyMexRequest(groupId, update);
     * }
     * Either argument may be {@code null} to omit it from the wire payload, matching the WA Web
     * undefined-variable convention; an empty mutation is normally rejected by the relay.
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
     * @implNote This implementation streams the GraphQL variables through fastjson2's
     * {@link JSONWriter} and emits the {@code group_id} and {@code update} fields only when their
     * corresponding constructor argument is non-null, matching the WA Web convention of omitting
     * undefined GraphQL variables. The {@code update} value is written as a JSON-string literal with
     * its content forwarded verbatim rather than re-parsed, mirroring the WA Web call shape where the
     * variable is already a JSON-serialisable scalar.
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
