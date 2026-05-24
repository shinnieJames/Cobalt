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
 * Builds the MEX request that permanently deletes a newsletter owned by the
 * authenticated user.
 *
 * @apiNote
 * Drives the "delete newsletter" flow consumed by
 * {@code WAWebNewsletterDeleteJob}: only the current owner may dispatch the
 * mutation, the relay then removes the channel and its subscriber list, and
 * followers stop receiving updates immediately. WA Web also evicts the
 * local newsletter metadata and chat entry after this mutation succeeds.
 */
@WhatsAppWebModule(moduleName = "WAWebMexDeleteNewsletterJob")
public final class DeleteNewsletterMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexDeleteNewsletterJobMutation.graphql} on the WhatsApp
     * relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "30062808666639665";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     */
    public static final String OPERATION_NAME = "mexDeleteNewsletter";

    /**
     * The Jid string of the newsletter being deleted.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexDeleteNewsletterJob", exports = "mexDeleteNewsletter",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String newsletterId;

    /**
     * Constructs a request targeting the given newsletter for deletion.
     *
     * @apiNote
     * The newsletter Jid is the same identifier echoed under
     * {@code xwa2_newsletter_delete_v2.id} in the response.
     *
     * @param newsletterId the newsletter Jid to delete
     */
    @WhatsAppWebExport(moduleName = "WAWebMexDeleteNewsletterJob", exports = "mexDeleteNewsletter",
            adaptation = WhatsAppAdaptation.DIRECT)
    public DeleteNewsletterMexRequest(String newsletterId) {
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
     * Produces the {@code {variables: {newsletter_id: "<id>"}}} payload; the
     * {@code newsletter_id} entry is omitted when {@link #newsletterId} is
     * {@code null} so the GraphQL schema never receives an explicit
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
    @WhatsAppWebExport(moduleName = "WAWebMexDeleteNewsletterJob", exports = "mexDeleteNewsletter",
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
