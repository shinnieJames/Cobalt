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
 * Builds the MEX request that creates a newsletter admin invite token.
 *
 * @apiNote
 * Drives the "invite as admin" flow consumed by
 * {@code WAWebNewsletterSendMsgAction}: the newsletter owner picks a target
 * user, this mutation records the pending invite server-side, and the
 * response timestamp is then included in the invite chat message sent to the
 * invitee. The invitee later accepts via
 * {@link AcceptNewsletterAdminInviteMexRequest}.
 *
 * @implNote
 * This implementation expects the caller to have already converted the
 * target user's Jid to its LID string; WA Web performs the conversion via
 * {@code WAWebLidMigrationUtils.toUserLidOrThrow}.
 */
@WhatsAppWebModule(moduleName = "WAWebMexCreateNewsletterAdminInviteJob")
public final class CreateNewsletterAdminInviteMexRequest implements MexOperation.Request.Json {
    /**
     * The compiled persisted-query identifier of
     * {@code WAWebMexCreateNewsletterAdminInviteJobMutation.graphql} on the
     * WhatsApp relay.
     *
     * @apiNote
     * Sent as the {@code id} attribute of the outgoing {@code <query>} child.
     */
    public static final String QUERY_ID = "9387141988078609";

    /**
     * The GraphQL operation name reported by WA Web's {@code MexPerfTracker}
     * for this mutation.
     */
    public static final String OPERATION_NAME = "createNewsletterAdminInvite";

    /**
     * The Jid string of the newsletter on which the admin invite is created.
     */
    private final String newsletterId;

    /**
     * The user LID string of the recipient that will receive the admin
     * invite.
     */
    private final String userId;

    /**
     * Constructs a request that creates an admin invite for the given
     * newsletter and target user.
     *
     * @apiNote
     * The {@code userId} parameter must be the user LID string; WA Web's
     * {@code WAWebLidMigrationUtils.toUserLidOrThrow} performs the
     * conversion before invoking the underlying mutation.
     *
     * @param newsletterId the newsletter Jid string the invite targets
     * @param userId       the user LID of the invitee
     */
    public CreateNewsletterAdminInviteMexRequest(String newsletterId, String userId) {
        this.newsletterId = newsletterId;
        this.userId = userId;
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
     * Produces the {@code {variables: {newsletter_id, user_id}}} payload;
     * either field is omitted when its backing string is {@code null} so the
     * server-side schema never receives an explicit {@code null} variable.
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
    @WhatsAppWebExport(moduleName = "WAWebMexCreateNewsletterAdminInviteJob", exports = "createNewsletterAdminInvite",
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
