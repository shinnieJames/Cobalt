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
 * Builds the MEX request that fetches the follower roster of a newsletter.
 *
 * @apiNote
 * Drives the admin follower-list surface where the owner inspects the
 * users following the newsletter; each returned edge carries the
 * follower Jid, role (owner/admin/subscriber), follow time and optional
 * admin profile.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterFollowersJob")
public final class FetchNewsletterFollowersMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterFollowersJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "25895136756785869";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterFollowers";

    /**
     * The newsletter Jid whose followers are being requested.
     */
    private final String newsletterId;

    /**
     * The requested follower page size, or {@code null} for the server
     * default.
     */
    private final Integer count;

    /**
     * Constructs a request for the follower roster of the given newsletter.
     *
     * @apiNote
     * WA Web clamps {@code count} to
     * {@code WAWebNewsletterGatingUtils.getMaxSubscriberNumber()} before
     * sending; callers should apply the same clamp here when they want
     * parity.
     *
     * @param newsletterId the newsletter Jid, may be {@code null}
     * @param count        the requested page size, may be {@code null}
     */
    public FetchNewsletterFollowersMexRequest(String newsletterId, Integer count) {
        this.newsletterId = newsletterId;
        this.count = count;
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
     * Produces the {@code {variables: {input: {newsletter_id, count}}}}
     * payload; each scalar is omitted when its field is {@code null}.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterFollowersJob", exports = "mexFetchNewsletterFollowers",
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
            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
            }
            if (count != null) {
                writer.writeName("count");
                writer.writeColon();
                writer.writeInt32(count);
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
