package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the MEX request that fetches the reaction-sender list for a
 * specific newsletter message.
 *
 * @apiNote
 * Drives the admin reactions inspector where the owner taps a reaction
 * count under a newsletter message to see which subscribers used each
 * emoji; the response groups senders by {@code reactionCode}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob")
public final class FetchNewsletterMessageReactionSenderListMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterMessageReactionSenderListJobQuery.graphql}
     * on the WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "29575462448733991";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterMessageReactionSenderList";

    /**
     * The newsletter Jid that owns the target message.
     */
    private final String newsletterId;

    /**
     * The server-assigned identifier of the target message.
     */
    private final long serverId;

    /**
     * Constructs a request that targets the reaction senders for the message
     * identified by {@code (newsletterId, serverId)}.
     *
     * @apiNote
     * Both fields are mandatory; pass the newsletter Jid string (no
     * {@code Jid} wrapping) and the canonical server message id as
     * returned in the message envelope.
     *
     * @param newsletterId the newsletter Jid
     * @param serverId     the server-assigned message identifier
     * @throws NullPointerException if {@code newsletterId} is {@code null}
     */
    public FetchNewsletterMessageReactionSenderListMexRequest(String newsletterId, long serverId) {
        this.newsletterId = Objects.requireNonNull(newsletterId, "newsletterId");
        this.serverId = serverId;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #OPERATION_NAME}.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder}.
     *
     * @apiNote
     * Produces the {@code {variables: {input: {id, server_id}}}} payload.
     *
     * @implNote
     * This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and wraps any {@link IOException} from the
     * in-memory writer in an {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised
     *         GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterMessageReactionSenderListJob", exports = "mexFetchNewsletterMessageReactionSenderList",
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
            writer.writeName("id");
            writer.writeColon();
            writer.writeString(newsletterId);
            writer.writeName("server_id");
            writer.writeColon();
            writer.writeInt64(serverId);
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
