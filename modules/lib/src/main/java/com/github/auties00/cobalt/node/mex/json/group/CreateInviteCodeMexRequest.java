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
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound MEX mutation that rotates a group's invite code by asking the
 * relay to mint a fresh opaque code string.
 *
 * @apiNote Issued by callers that surface WA Web's
 * {@code WAWebOutContactInviteAction.sendInvite} flow, where a fresh invite
 * code is generated before sending the share text on behalf of a contact.
 * The mutation invalidates any previously distributed
 * {@code chat.whatsapp.com/<code>} link bound to the same receiver. To read
 * the current code without rotating it, use
 * {@link FetchGroupInviteCodeMexRequest} instead.
 *
 * @implNote This implementation hard-codes the WA Web
 * {@code server_send_sms = false} input field by omitting it from the
 * payload; the wire-level GraphQL variables object only carries
 * {@code receiver} and {@code entry_point}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateInviteCodeJob")
public final class CreateInviteCodeMexRequest implements MexOperation.Request.Json {
    /**
     * Compiled GraphQL query identifier for the
     * {@code WAWebMexCreateInviteCodeJobMutation} document.
     *
     * @apiNote Mirrors the {@code params.id} value baked into
     * {@code WAWebMexCreateInviteCodeJobMutation.graphql}. The relay maps
     * this id to its persisted operation; the GraphQL text is never sent on
     * the wire.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateInviteCodeJobMutation.graphql", exports = "params.id",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String QUERY_ID = "26155584267463745";

    /**
     * GraphQL operation name reported to
     * {@code MexPerfTracker.setOperationName} when this mutation is
     * dispatched.
     *
     * @apiNote Used by WA Web's MEX perf tracker to tag the query in
     * latency and error metrics; Cobalt keeps the name on the request for
     * embedders mirroring WA Web's telemetry surface.
     */
    public static final String OPERATION_NAME = "mexCreateInviteCode";

    /**
     * The receiver identifier bound to the {@code input.receiver} GraphQL
     * input field.
     */
    private final String receiver;

    /**
     * The originating UI surface tag bound to the {@code input.entry_point}
     * GraphQL input field.
     */
    private final String entryPoint;

    /**
     * Constructs a new request with the two GraphQL input fields.
     *
     * @apiNote Both arguments are required and forwarded verbatim to the
     * relay. {@code entryPoint} is the telemetry attribution tag (e.g.
     * {@code "CHAT_INFO_INVITE_BUTTON"}) the relay associates with the
     * rotation event.
     *
     * @param receiver   the receiver identifier the code is minted for, never {@code null}
     * @param entryPoint the originating UI surface tag, never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public CreateInviteCodeMexRequest(String receiver, String entryPoint) {
        this.receiver = Objects.requireNonNull(receiver, "receiver cannot be null");
        this.entryPoint = Objects.requireNonNull(entryPoint, "entryPoint cannot be null");
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
     * @implNote This implementation streams the {@code input.receiver} and
     * {@code input.entry_point} GraphQL fields through fastjson2's
     * {@link JSONWriter} and wraps them in the standard MEX IQ envelope
     * built through
     * {@link MexOperation.Request.Json#createMexNode(String, String)}. The
     * WA Web {@code input.server_send_sms = false} field is omitted; the
     * relay treats the absent flag as {@code false}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexCreateInviteCodeJob", exports = "mexCreateInviteCode",
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
            writer.writeName("receiver");
            writer.writeColon();
            writer.writeString(receiver);
            writer.writeName("entry_point");
            writer.writeColon();
            writer.writeString(entryPoint);
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
