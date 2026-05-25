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
import java.util.Optional;

/**
 * Builds the MEX request that lists pending admin invites awaiting acceptance on a newsletter.
 *
 * <p>This request drives the admin-management surface where the owner inspects the users who have
 * been invited as admins but have not yet accepted. WA Web additionally gates the call behind its
 * newsletter-creation-enabled check and skips it when newsletter creation is disabled.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterPendingInvitesJob")
public final class FetchNewsletterPendingInvitesMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterPendingInvitesJobQuery.graphql} on the WhatsApp relay.
     *
     * <p>Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9783111038412085";

    /**
     * Holds the GraphQL operation name reported by WA Web's {@code MexPerfTracker} for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterPendingInvites";

    /**
     * Holds the newsletter Jid whose pending admin invites are being requested.
     */
    private final String newsletterId;

    /**
     * Constructs a request for the pending admin invites of the given newsletter.
     *
     * <p>Passing {@code null} omits the {@code newsletter_id} variable from the payload entirely.
     *
     * @param newsletterId the newsletter Jid, may be {@code null}
     */
    public FetchNewsletterPendingInvitesMexRequest(String newsletterId) {
        this.newsletterId = newsletterId;
    }

    /**
     * Returns {@link #QUERY_ID}.
     *
     * @return the persisted-query identifier of this query
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * Returns {@link #OPERATION_NAME}.
     *
     * @return the GraphQL operation name of this query
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder}.
     *
     * <p>Produces the {@code {variables: {newsletter_id}}} payload; the {@code newsletter_id}
     * variable is omitted when the field is {@code null}.
     *
     * @implNote This implementation writes the GraphQL variables directly through {@link JSONWriter}
     * and wraps any {@link IOException} from the in-memory writer in an {@link UncheckedIOException}.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterPendingInvitesJob", exports = "mexFetchNewsletterPendingInvites",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public NodeBuilder toNode() {
        try (var writer = JSONWriter.ofUTF8()) {
            writer.startObject();
            writer.writeName("variables");
            writer.writeColon();
            writer.startObject();
            if (newsletterId != null) {
                writer.writeName("newsletter_id");
                writer.writeColon();
                writer.writeString(newsletterId);
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
