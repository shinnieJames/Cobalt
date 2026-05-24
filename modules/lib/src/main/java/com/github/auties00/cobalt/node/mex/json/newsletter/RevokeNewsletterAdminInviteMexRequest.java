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
 * Builds the MEX request that cancels a previously issued newsletter
 * admin invitation.
 *
 * @apiNote
 * Drives the "revoke admin invite" owner action surfaced by
 * {@code WAWebRevokeNewsletterAdminInviteAction.revokeNewsletterAdminInviteAction}:
 * a newsletter owner taps "revoke" on a pending admin-invite chip, the
 * action removes the invitee from the local {@code pendingAdmins} cache
 * and runs this mutation to remove the invite server-side. Build via the
 * constructor with the newsletter Jid and the invitee's user-id string;
 * submit through the MEX IQ dispatcher.
 *
 * @implNote
 * WA Web's caller wraps the underlying mutation in
 * {@code WAWebNewsletterRpcUtils.runWithBackoff} and converts the target
 * Jid through {@code WAWebLidMigrationUtils.toUserLidOrThrow} before
 * sending; Cobalt expects the caller to supply the already-migrated user
 * lid string and does not retry on its own. TODO: integrate the lid
 * migration helper to match the JS preprocessing.
 */
@WhatsAppWebModule(moduleName = "WAWebMexRevokeNewsletterAdminInviteJob")
public final class RevokeNewsletterAdminInviteMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexRevokeNewsletterAdminInviteJobMutation.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child;
     * the WhatsApp relay refuses requests whose persisted-query id is unknown.
     */
    public static final String QUERY_ID = "9656078347839416";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     *
     * @apiNote
     * Reported to observability sinks that key telemetry on the operation
     * name; mirrors the export name exposed by
     * {@code WAWebMexRevokeNewsletterAdminInviteJob}.
     */
    public static final String OPERATION_NAME = "revokeNewsletterAdminInvite";

    /**
     * The Jid string of the newsletter whose pending admin invite is
     * being revoked.
     */
    private final String newsletterId;

    /**
     * The lid-migrated user identifier of the invite target.
     */
    private final String userId;

    /**
     * Constructs a request targeting the given pending admin invite.
     *
     * @apiNote
     * Both arguments are required for the relay to identify the invite;
     * a {@code null} value is preserved (the corresponding variable is
     * simply omitted from the wire payload) so the relay returns a
     * validation error rather than this request raising synchronously.
     *
     * @param newsletterId the newsletter Jid whose pending admin invite
     *                     is being revoked
     * @param userId       the lid-migrated identifier of the invite
     *                     target
     */
    public RevokeNewsletterAdminInviteMexRequest(String newsletterId, String userId) {
        this.newsletterId = newsletterId;
        this.userId = userId;
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
     * Produces the {@code {variables: {newsletter_id?, user_id?}}} payload
     * consumed by the persisted-query identified by {@link #QUERY_ID};
     * both entries are omitted when {@code null} so the GraphQL schema
     * never receives explicit {@code null} variables.
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
    @WhatsAppWebExport(moduleName = "WAWebMexRevokeNewsletterAdminInviteJob", exports = "revokeNewsletterAdminInvite",
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

            if (userId != null) {
                writer.writeName("user_id");
                writer.writeColon();
                writer.writeString(userId);
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
