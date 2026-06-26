package com.github.auties00.cobalt.stanza.mex.json.newsletter;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.mex.MexStanza;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the MEX request that attaches a paid-partnership disclosure label to a newsletter message.
 *
 * <p>This request backs the paid-partnership tagging flow: after a monetised newsletter creator
 * sends a sponsored message, this mutation attaches the legal disclosure label to the server-stored
 * message so the relay renders the disclosure badge to downstream subscribers. Construct it with the
 * newsletter Jid, the relay-issued message server-id, and the message type discriminator, submit it
 * through the MEX IQ dispatcher, and check {@link NewsletterAddPaidPartnershipLabelMexResponse#id()}
 * to confirm success.
 */
@WhatsAppWebModule(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob")
public final class NewsletterAddPaidPartnershipLabelMexRequest implements MexStanza.Request.Json {
    /**
     * Holds the compiled persisted-query identifier of the add-paid-partnership-label mutation.
     *
     * <p>Emitted as the {@code query_id} attribute of the outgoing {@code <query>} child; the relay
     * refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "26102375079404865";

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
     * Holds the type discriminator of the message being labelled.
     */
    private final String messageType;

    /**
     * Constructs a request targeting the given newsletter message without a message-type
     * discriminator.
     *
     * <p>Both arguments help the relay locate the message; the {@code message_type} variable is
     * omitted from the wire payload. A {@code null} value is preserved (the corresponding variable is
     * simply omitted) so the relay returns a validation error rather than this request raising
     * synchronously.
     *
     * @param newsletterId the Jid of the newsletter that owns the message
     * @param serverId     the relay-issued message server-id
     */
    public NewsletterAddPaidPartnershipLabelMexRequest(String newsletterId, String serverId) {
        this(newsletterId, serverId, null);
    }

    /**
     * Constructs a request targeting the given newsletter message.
     *
     * <p>All three arguments help the relay locate and classify the message. A {@code null} value is
     * preserved (the corresponding variable is simply omitted from the wire payload) so the relay
     * returns a validation error rather than this request raising synchronously.
     *
     * @param newsletterId the Jid of the newsletter that owns the message
     * @param serverId     the relay-issued message server-id
     * @param messageType  the type discriminator of the message being labelled
     */
    public NewsletterAddPaidPartnershipLabelMexRequest(String newsletterId, String serverId, String messageType) {
        this.newsletterId = newsletterId;
        this.serverId = serverId;
        this.messageType = messageType;
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
     * Serialises this request into a MEX IQ {@link StanzaBuilder} ready to be dispatched through the
     * WhatsApp relay.
     *
     * <p>Produces the {@code {variables: {newsletter_id?, server_id?, message_type?}}} payload
     * consumed by the persisted-query identified by {@link #QUERY_ID}. Each entry is omitted when
     * {@code null} so the GraphQL schema never receives explicit {@code null} variables.
     *
     * @implNote This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and delegates IQ envelope construction to
     * {@link Json#createMexNode(String, String)}; any {@link IOException} raised by the in-memory
     * writer is wrapped in an {@link UncheckedIOException} since neither sink can fail in practice.
     *
     * @return the {@link StanzaBuilder} carrying the IQ envelope and serialised GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexNewsletterAddPaidPartnershipLabelJob", exports = "mexNewsletterAddPaidPartnershipLabelJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public StanzaBuilder toStanza() {
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

            if (messageType != null) {
                writer.writeName("message_type");
                writer.writeColon();
                writer.writeString(messageType);
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
