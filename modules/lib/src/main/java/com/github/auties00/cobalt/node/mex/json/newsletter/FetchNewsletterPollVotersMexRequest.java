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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the MEX request that fetches the voter list for a newsletter
 * poll option.
 *
 * @apiNote
 * Drives the admin poll-results surface; the request targets one poll
 * message by {@code (newsletterId, serverId)} and one option by
 * {@code voteHash} (a base64-encoded hash returned in the poll-update
 * message), and the response groups voters by option.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterPollVotersJob")
public final class FetchNewsletterPollVotersMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterPollVotersJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9407762219322536";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "fetchNewsletterPollVoters";

    /**
     * The newsletter Jid that owns the poll message.
     */
    private final String newsletterId;

    /**
     * The maximum number of voter edges to return.
     */
    private final long limit;

    /**
     * The server-assigned identifier of the poll message.
     */
    private final long serverId;

    /**
     * The base64-encoded option hash, or {@code null} to fetch voters
     * for every option.
     */
    private final String voteHash;

    /**
     * Constructs a request for the voter list of one poll option.
     *
     * @apiNote
     * Pass a {@code null} {@code voteHash} to receive the voter list for
     * every option of the poll; pass a specific hash to restrict to one
     * option.
     *
     * @param newsletterId the newsletter Jid
     * @param limit        the maximum voter-edge count
     * @param serverId     the server-assigned poll message identifier
     * @param voteHash     the base64-encoded option hash, or {@code null}
     *                     for all options
     */
    public FetchNewsletterPollVotersMexRequest(String newsletterId, long limit, long serverId, String voteHash) {
        this.newsletterId = newsletterId;
        this.limit = limit;
        this.serverId = serverId;
        this.voteHash = voteHash;
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
     * Produces the
     * {@code {variables: {input: {limit, server_id, newsletter_id, vote_hash}}}}
     * payload; the {@code server_id} is encoded as a base-10 string to
     * preserve full 64-bit precision through JSON, matching the JS source.
     * {@code newsletter_id} and {@code vote_hash} are omitted when their
     * fields are {@code null}.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterPollVotersJob", exports = "default",
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
            writer.writeName("limit");
            writer.writeColon();
            writer.writeInt64(limit);
            writer.writeName("server_id");
            writer.writeColon();
            writer.writeString(Long.toString(serverId, 10));
            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
            }
            if (voteHash != null) {
                writer.writeName("vote_hash");
                writer.writeColon();
                writer.writeString(voteHash);
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
