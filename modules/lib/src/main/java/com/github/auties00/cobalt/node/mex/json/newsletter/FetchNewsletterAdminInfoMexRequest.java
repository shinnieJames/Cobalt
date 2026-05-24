package com.github.auties00.cobalt.node.mex.json.newsletter;

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
import java.util.OptionalLong;

/**
 * Builds the MEX request that fetches the admin headcount on a newsletter.
 *
 * @apiNote
 * Drives the per-channel admin-headcount lookup consumed by
 * {@code WAWebNewsletterGetAdminInfoJob}. The relay returns the
 * {@code admin_count} scalar on the {@code xwa2_newsletter_admin} root
 * which the UI surfaces as an "N admins" affordance. WA Web's full
 * response shape also carries {@code admin_profile} and
 * {@code admin_settings} sub-objects, but Cobalt only exposes the count
 * scalar via {@link FetchNewsletterAdminInfoMexResponse}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterAdminInfoJob")
public final class FetchNewsletterAdminInfoMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexFetchNewsletterAdminInfoJobQuery.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "26278439461859188";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this query.
     */
    public static final String OPERATION_NAME = "mexFetchNewsletterAdminInfo";

    /**
     * The Jid string of the newsletter whose admin info is being fetched.
     */
    private final String newsletterId;

    /**
     * Constructs a request targeting the given newsletter.
     *
     * @param newsletterId the newsletter Jid string
     */
    public FetchNewsletterAdminInfoMexRequest(String newsletterId) {
        this.newsletterId = newsletterId;
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
     * Produces the {@code {variables: {newsletter_id: "<id>"}}} payload;
     * the {@code newsletter_id} entry is omitted when {@link #newsletterId}
     * is {@code null} so the GraphQL schema never receives an explicit
     * {@code null} variable.
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
    @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterAdminInfoJob", exports = "mexFetchNewsletterAdminInfo",
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
