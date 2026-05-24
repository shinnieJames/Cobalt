package com.github.auties00.cobalt.node.smax.status;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The addressing mode of a
 * {@link SmaxStatusPublishPostNewsletterStatusRequest}: either a
 * status-reaction keyed by the target status's server-id
 * ({@link WithServerId}), or a brand-new status keyed by stanza-id
 * only ({@link WithClientIdOnly}).
 *
 * @apiNote
 * Pick {@link WithServerId} when publishing a
 * status-newsletter-reaction or status-newsletter-reaction-revoke;
 * pick {@link WithClientIdOnly} when publishing a brand-new status
 * (which today is the status-revoke flow driven by
 * {@code WAWebNewsletterRevokeStatusQueryJob} or the
 * status-send flow driven by
 * {@code WAWebNewsletterSendStatusQueryJob}).
 *
 * @implNote
 * This implementation models the WA Web
 * {@code clientPostNewsletterStatusAndServerOrPostNewsletterStatusIDMixinGroup}
 * disjunction as a sealed interface with two final implementations;
 * callers pre-build the inner content as a {@link Node}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutStatusPublishClientPostNewsletterStatusAndServerOrPostNewsletterStatusIDMixinGroup")
public sealed interface SmaxStatusPublishPostNewsletterStatusPayload permits SmaxStatusPublishPostNewsletterStatusPayload.WithServerId, SmaxStatusPublishPostNewsletterStatusPayload.WithClientIdOnly {

    /**
     * The addressing variant for a publish that references a
     * previously published status by its server-id.
     *
     * @apiNote
     * Pick this variant for status-reaction and
     * status-reaction-revoke publishes.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutStatusPublishPostNewsletterStatusClientAndServerIDMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutStatusPublishStatusNewsletterReactionStatusNewsletterReactionOrStatusNewsletterReactionRevokeMixinGroup")
    final class WithServerId implements SmaxStatusPublishPostNewsletterStatusPayload {
        /**
         * The locally-generated publish stanza id.
         */
        private final String stanzaId;

        /**
         * The targeted status's server-id.
         */
        private final long statusServerId;

        /**
         * The pre-built reaction or reaction-revoke content payload.
         */
        private final Node innerContent;

        /**
         * Constructs a server-id-addressed payload.
         *
         * @apiNote
         * Use this when assembling a
         * {@link SmaxStatusPublishPostNewsletterStatusRequest} that
         * targets a previously published status.
         *
         * @param stanzaId       the publish stanza id; never
         *                       {@code null}
         * @param statusServerId the targeted status's server-id
         * @param innerContent   the variant-shaped inner payload;
         *                       never {@code null}
         * @throws NullPointerException if {@code stanzaId} or
         *                              {@code innerContent} is
         *                              {@code null}
         */
        public WithServerId(String stanzaId, long statusServerId, Node innerContent) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.statusServerId = statusServerId;
            this.innerContent = Objects.requireNonNull(innerContent, "innerContent cannot be null");
        }

        /**
         * Returns the publish stanza id.
         *
         * @return the id; never {@code null}
         */
        public String stanzaId() {
            return stanzaId;
        }

        /**
         * Returns the targeted status's server-id.
         *
         * @return the server-id
         */
        public long statusServerId() {
            return statusServerId;
        }

        /**
         * Returns the pre-built inner content node.
         *
         * @apiNote
         * Read by
         * {@link SmaxStatusPublishPostNewsletterStatusRequest#toNode()}
         * when fanning out the publish.
         *
         * @return the node; never {@code null}
         */
        public Node innerContent() {
            return innerContent;
        }

        /**
         * Compares this payload to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a
         *         {@link WithServerId} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (WithServerId) obj;
            return this.statusServerId == that.statusServerId
                    && Objects.equals(this.stanzaId, that.stanzaId)
                    && Objects.equals(this.innerContent, that.innerContent);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, statusServerId, innerContent);
        }

        /**
         * Returns a debug-friendly representation of this payload.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxStatusPublishPostNewsletterStatusPayload.WithServerId[stanzaId=" + stanzaId
                    + ", statusServerId=" + statusServerId
                    + ", innerContent=" + innerContent + ']';
        }
    }

    /**
     * The addressing variant for a brand-new status post.
     *
     * @apiNote
     * Pick this variant for status posts that do not reference a
     * prior status (i.e. send-new and revoke-existing flows the
     * caller drives through
     * {@code WAWebNewsletterSendStatusQueryJob} /
     * {@code WAWebNewsletterRevokeStatusQueryJob}).
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutStatusPublishPostNewsletterStatusClientIDMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutStatusPublishNewsletterClientIdContent")
    final class WithClientIdOnly implements SmaxStatusPublishPostNewsletterStatusPayload {
        /**
         * The locally-generated publish stanza id.
         */
        private final String stanzaId;

        /**
         * The pre-built inner client-id content payload.
         */
        private final Node clientIdContent;

        /**
         * Constructs a brand-new-post payload.
         *
         * @apiNote
         * Use this when assembling a
         * {@link SmaxStatusPublishPostNewsletterStatusRequest} for a
         * new status.
         *
         * @param stanzaId        the publish stanza id; never
         *                        {@code null}
         * @param clientIdContent the inner client-id content node;
         *                        never {@code null}
         * @throws NullPointerException if either argument is
         *                              {@code null}
         */
        public WithClientIdOnly(String stanzaId, Node clientIdContent) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.clientIdContent = Objects.requireNonNull(clientIdContent, "clientIdContent cannot be null");
        }

        /**
         * Returns the publish stanza id.
         *
         * @return the id; never {@code null}
         */
        public String stanzaId() {
            return stanzaId;
        }

        /**
         * Returns the pre-built inner client-id content node.
         *
         * @return the node; never {@code null}
         */
        public Node clientIdContent() {
            return clientIdContent;
        }

        /**
         * Compares this payload to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a
         *         {@link WithClientIdOnly} with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (WithClientIdOnly) obj;
            return Objects.equals(this.stanzaId, that.stanzaId)
                    && Objects.equals(this.clientIdContent, that.clientIdContent);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, clientIdContent);
        }

        /**
         * Returns a debug-friendly representation of this payload.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxStatusPublishPostNewsletterStatusPayload.WithClientIdOnly[stanzaId=" + stanzaId
                    + ", clientIdContent=" + clientIdContent + ']';
        }
    }
}
