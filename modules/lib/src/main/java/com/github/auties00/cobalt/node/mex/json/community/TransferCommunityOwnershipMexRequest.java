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
 * Outbound MEX mutation that transfers ownership of a community from the
 * current owner to another admin.
 *
 * <p>This mutation backs the transfer-ownership action in the community
 * settings. It updates the server-side role mapping for the group; the reply,
 * modelled by {@link TransferCommunityOwnershipMexResponse}, echoes the
 * affected group id and the resulting LID migration state (addressing mode) so
 * callers can update their local view of the community before replaying cached
 * actions. WA Web follows the mutation with a group-metadata refresh only when
 * the addressing mode actually changed.
 *
 * @implNote This implementation accepts the GraphQL {@code input} variable as a
 * single opaque pre-serialised JSON string rather than modelling its inner
 * shape ({@code community_id}, the {@code users_role} update list and the
 * {@code localParentGroupAddressingMode} flag). Callers serialise the input
 * themselves and pass the resulting JSON; the field is dropped from the wire
 * payload when {@code null}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexTransferCommunityOwnershipJob")
public final class TransferCommunityOwnershipMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the transfer-ownership document.
     *
     * <p>The relay maps this id to its persisted operation; the GraphQL text
     * is never sent on the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "29643783178598899";

    /**
     * GraphQL operation name carried by this mutation.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJob", exports = "mexTransferCommunityOwnershipJob",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String OPERATION_NAME = "mexTransferCommunityOwnershipJob";

    /**
     * Pre-serialised GraphQL {@code input} variable, or {@code null} to omit
     * it.
     */
    private final String input;

    /**
     * Constructs a new request carrying the serialised input payload with the
     * community id and the new owner's id.
     *
     * <p>The WA Web {@code input} variable nests {@code community_id}, a
     * {@code users_role} array (the new {@code "SUPERADMIN_MEMBER"} promotion)
     * and the {@code localParentGroupAddressingMode} flag. Callers serialise
     * this themselves and pass the resulting JSON string; passing {@code null}
     * omits the field entirely from the wire payload.
     *
     * @param input the serialised input variable, may be {@code null}
     */
    public TransferCommunityOwnershipMexRequest(String input) {
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
     * fastjson2's {@link JSONWriter} and emits the {@code input} field only
     * when the constructor argument is non-null. The envelope is built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJob", exports = "mexTransferCommunityOwnershipJob",
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
