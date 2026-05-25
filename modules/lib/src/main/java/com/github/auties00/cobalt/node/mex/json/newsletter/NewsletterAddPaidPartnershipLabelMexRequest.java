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

/**
 * Builds the MEX request that attaches a paid-partnership disclosure label to a newsletter message.
 *
 * <p>This request backs the paid-partnership tagging flow: after a monetised newsletter creator
 * sends a sponsored message, this mutation attaches the legal disclosure label to the server-stored
 * message so the relay renders the disclosure badge to downstream subscribers. Construct it with the
 * newsletter Jid and the relay-issued message server-id, submit it through the MEX IQ dispatcher,
 * and check {@link NewsletterAddPaidPartnershipLabelMexResponse#id()} to confirm success.
 *
 * @implNote This implementation omits a {@code message_type} variable from the wire payload; the
 * relay accepts the shorter shape because the GraphQL schema defaults the field. TODO: surface
 * {@code message_type} for parity with the source mutation signature.
 */
@WhatsAppWebModule(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob")
public final class NewsletterAddPaidPartnershipLabelMexRequest implements MexOperation.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of the add-paid-partnership-label mutation.
     *
     * <p>Emitted as the {@code query_id} attribute of the outgoing {@code <query>} child; the relay
     * refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "25690501173969818";

    /**
     * Holds the GraphQL operation name reported for this mutation.
     *
     * <p>Forwarded to observability sinks that key telemetry on the operation name.
     */
    public static final String OPERATION_NAME = "mexNewsletterAddPaidPartnershipLabelJob";

    /**
     * Holds the Jid string of the newsletter that owns the labelled message.
     */
    private final String newsletterId;

    /**
     * Holds the relay-issued server identifier of the message being labelled.
     */
    private final String serverId;

    /**
     * Constructs a request targeting the given newsletter message.
     *
     * <p>Both arguments are required for the relay to locate the message. A {@code null} value is
     * preserved (the corresponding variable is simply omitted from the wire payload) so the relay
     * returns a validation error rather than this request raising synchronously.
     *
     * @param newsletterId the Jid of the newsletter that owns the message
     * @param serverId     the relay-issued message server-id
     */
    public NewsletterAddPaidPartnershipLabelMexRequest(String newsletterId, String serverId) {
        this.newsletterId = newsletterId;
        this.serverId = serverId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link #QUERY_ID}, the persisted-query identifier of the mutation.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link #OPERATION_NAME}, the operation name reported for this mutation.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder} ready to be dispatched through the
     * WhatsApp relay.
     *
     * <p>Produces the {@code {variables: {newsletter_id?, server_id?}}} payload consumed by the
     * persisted-query identified by {@link #QUERY_ID}. Both entries are omitted when {@code null} so
     * the GraphQL schema never receives explicit {@code null} variables.
     *
     * @implNote This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and delegates IQ envelope construction to
     * {@link Json#createMexNode(String, String)}; any {@link IOException} raised by the in-memory
     * writer is wrapped in an {@link UncheckedIOException} since neither sink can fail in practice.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob", exports = "mexNewsletterAddPaidPartnershipLabelJob",
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

            if (serverId != null) {
                writer.writeName("server_id");
                writer.writeColon();
                writer.writeString(serverId);
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
