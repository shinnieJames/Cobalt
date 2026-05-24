package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Builds the MEX request that accepts a pending newsletter admin invite.
 *
 * @apiNote
 * Drives the "accept newsletter admin invite" UI flow surfaced by
 * {@code WAWebNewsletterAcceptAdminInviteAction}: the invitee opens the
 * pending invite modal, the action runs this mutation against the newsletter
 * Jid, then refreshes local membership state to admin. Build via the
 * constructor with the newsletter Jid string and submit through the MEX IQ
 * dispatcher.
 */
@WhatsAppWebModule(moduleName = "WAWebMexAcceptNewsletterAdminInviteJob")
public final class AcceptNewsletterAdminInviteMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexAcceptNewsletterAdminInviteJobMutation.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child;
     * the WhatsApp relay refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "9580828702035549";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     *
     * @apiNote
     * Reported to observability sinks that key telemetry on the operation
     * name; the value mirrors the export name exposed by
     * {@code WAWebMexAcceptNewsletterAdminInviteJob}.
     */
    public static final String OPERATION_NAME = "acceptNewsletterAdminInvite";

    /**
     * The Jid string of the newsletter whose admin invite is being accepted.
     */
    @WhatsAppWebExport(moduleName = "WAWebMexAcceptNewsletterAdminInviteJob", exports = "acceptNewsletterAdminInvite",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final String newsletterId;

    /**
     * Constructs a request targeting the given newsletter.
     *
     * @apiNote
     * The {@code newsletterId} must be the newsletter Jid string as accepted
     * by {@code WAWebNewsletterValidationUtils.toNewsletterJidOrThrow}; the
     * server rejects user or group ids.
     *
     * @param newsletterId the newsletter Jid whose pending admin invite is
     *                     accepted
     */
    @WhatsAppWebExport(moduleName = "WAWebMexAcceptNewsletterAdminInviteJob", exports = "acceptNewsletterAdminInvite",
            adaptation = WhatsAppAdaptation.DIRECT)
    public AcceptNewsletterAdminInviteMexRequest(String newsletterId) {
        this.newsletterId = newsletterId;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #QUERY_ID}, the persisted-query identifier of the
     * mutation.
     */
    @Override
    public String id() {
        return QUERY_ID;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Returns {@link #OPERATION_NAME}, the value WA Web's
     * {@code MexPerfTracker} reports for this mutation.
     */
    @Override
    public String name() {
        return OPERATION_NAME;
    }

    /**
     * Serialises this request into a MEX IQ {@link NodeBuilder} ready to be
     * dispatched through the WhatsApp relay.
     *
     * @apiNote
     * Produces the {@code {variables: {newsletter_id: "<id>"}}} payload
     * consumed by the persisted-query identified by {@link #QUERY_ID}; the
     * {@code newsletter_id} entry is omitted when {@link #newsletterId} is
     * {@code null} so the GraphQL schema never receives an explicit
     * {@code null} variable.
     *
     * @implNote
     * This implementation writes the GraphQL variables directly through
     * {@link JSONWriter} and delegates IQ envelope construction to
     * {@link Json#createMexNode(String, String)}; any {@link IOException}
     * raised by the in-memory writer is wrapped in an
     * {@link UncheckedIOException} since neither sink can fail in practice.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and serialised
     *         GraphQL variables
     * @throws UncheckedIOException if the underlying writer fails
     */
    @WhatsAppWebExport(moduleName = "WAWebMexAcceptNewsletterAdminInviteJob", exports = "acceptNewsletterAdminInvite",
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
