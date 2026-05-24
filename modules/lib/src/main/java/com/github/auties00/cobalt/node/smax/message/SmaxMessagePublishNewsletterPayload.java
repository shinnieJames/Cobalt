package com.github.auties00.cobalt.node.smax.message;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/**
 * The addressing mode of a {@link SmaxMessagePublishNewsletterRequest}:
 * either a reply addressed by stanza-id and the targeted message's
 * server-id ({@link WithServerId}), or a brand-new post addressed by
 * stanza-id only ({@link WithClientIdOnly}).
 *
 * @apiNote
 * Pick {@link WithServerId} when publishing a question-response,
 * reaction, reaction-revoke, or poll-vote tied to a previously
 * delivered newsletter message; pick {@link WithClientIdOnly} when
 * publishing a brand-new newsletter message that may carry an
 * optional msg-meta-origin marker and an optional sender
 * content-type-media RCAT payload.
 *
 * @implNote
 * This implementation models the WA Web
 * {@code clientNewsletterAndServerOrNewsletterIDMixinGroup}
 * disjunction as a sealed interface with two final implementations;
 * callers pre-build the inner content as a {@link Node} so this type
 * does not need to model every newsletter publish payload shape.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishClientNewsletterAndServerOrNewsletterIDMixinGroup")
public sealed interface SmaxMessagePublishNewsletterPayload permits SmaxMessagePublishNewsletterPayload.WithServerId, SmaxMessagePublishNewsletterPayload.WithClientIdOnly {

    /**
     * The addressing variant for a publish that references a
     * previously delivered newsletter message by its server-id.
     *
     * @apiNote
     * Pick this variant for question-response, reaction,
     * reaction-revoke, and poll-vote publishes. The
     * {@link #innerContent()} field carries the pre-built variant
     * payload shape.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterClientAndServerIDMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterQuestionResponsePublishOrReactionOrReactionRevokeOrPollVoteMixinGroup")
    final class WithServerId implements SmaxMessagePublishNewsletterPayload {
        /**
         * The locally-generated publish stanza id.
         */
        private final String stanzaId;

        /**
         * The targeted message's server-id within the newsletter.
         */
        private final long messageServerId;

        /**
         * The pre-built inner content payload.
         *
         * @apiNote
         * One of the four
         * {@code WASmaxOutMessagePublishNewsletter*} variants
         * (question-response-publish, reaction, reaction-revoke,
         * poll-vote); the caller selects the variant by constructing
         * the appropriate node tree before passing it in.
         */
        private final Node innerContent;

        /**
         * Constructs a server-id-addressed payload.
         *
         * @apiNote
         * Use this when assembling a
         * {@link SmaxMessagePublishNewsletterRequest} that targets a
         * specific previously delivered newsletter message.
         *
         * @param stanzaId        the publish stanza id; never
         *                        {@code null}
         * @param messageServerId the targeted message's server-id
         * @param innerContent    the variant-shaped inner payload;
         *                        never {@code null}
         * @throws NullPointerException if {@code stanzaId} or
         *                              {@code innerContent} is
         *                              {@code null}
         */
        public WithServerId(String stanzaId, long messageServerId, Node innerContent) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.messageServerId = messageServerId;
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
         * Returns the targeted message's server-id.
         *
         * @return the server-id
         */
        public long messageServerId() {
            return messageServerId;
        }

        /**
         * Returns the pre-built inner content node.
         *
         * @apiNote
         * Read by
         * {@link SmaxMessagePublishNewsletterRequest#toNode()} when
         * fanning out the publish.
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
            return this.messageServerId == that.messageServerId
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
            return Objects.hash(stanzaId, messageServerId, innerContent);
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
            return "SmaxMessagePublishNewsletterPayload.WithServerId[stanzaId=" + stanzaId
                    + ", messageServerId=" + messageServerId
                    + ", innerContent=" + innerContent + ']';
        }
    }

    /**
     * The addressing variant for a brand-new newsletter post.
     *
     * @apiNote
     * Pick this variant for posting a new newsletter message that
     * does not reference a prior message; the optional fields cover
     * the origin marker (CTWA, ad, forward, etc.) and the media RCAT
     * payload when the post carries non-text content.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterClientIDMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterClientIdContent")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishMsgMetaOriginMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishSenderContentTypeMediaRCATMixin")
    final class WithClientIdOnly implements SmaxMessagePublishNewsletterPayload {
        /**
         * The locally-generated publish stanza id.
         */
        private final String stanzaId;

        /**
         * The optional pre-built msg-meta-origin marker child;
         * {@code null} when the publish is not an origin-tagged
         * broadcast.
         */
        private final Node msgMetaOrigin;

        /**
         * The optional pre-built sender content-type-media RCAT
         * child carrying the {@code <plaintext mediatype="url"/>}
         * plus {@code <rcat>...</rcat>} pair; {@code null} when the
         * publish does not carry a media payload.
         */
        private final Node senderContentTypeMediaRcat;

        /**
         * The pre-built inner client-id content payload.
         */
        private final Node clientIdContent;

        /**
         * Constructs a brand-new-post payload.
         *
         * @apiNote
         * Use this when assembling a
         * {@link SmaxMessagePublishNewsletterRequest} for a new post
         * (text, media, poll-creation, edit, revoke, question).
         *
         * @param stanzaId                   the publish stanza id;
         *                                   never {@code null}
         * @param msgMetaOrigin              the optional origin
         *                                   marker child; may be
         *                                   {@code null}
         * @param senderContentTypeMediaRcat the optional media RCAT
         *                                   child; may be
         *                                   {@code null}
         * @param clientIdContent            the inner client-id
         *                                   content node; never
         *                                   {@code null}
         * @throws NullPointerException if {@code stanzaId} or
         *                              {@code clientIdContent} is
         *                              {@code null}
         */
        public WithClientIdOnly(String stanzaId,
                                Node msgMetaOrigin,
                                Node senderContentTypeMediaRcat,
                                Node clientIdContent) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.msgMetaOrigin = msgMetaOrigin;
            this.senderContentTypeMediaRcat = senderContentTypeMediaRcat;
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
         * Returns the optional msg-meta-origin child.
         *
         * @apiNote
         * Read by
         * {@link SmaxMessagePublishNewsletterRequest#toNode()} when
         * folding the marker into the message body.
         *
         * @return an {@link Optional} carrying the node, or
         *         {@link Optional#empty()} when omitted
         */
        public Optional<Node> msgMetaOrigin() {
            return Optional.ofNullable(msgMetaOrigin);
        }

        /**
         * Returns the optional sender content-type-media RCAT child.
         *
         * @apiNote
         * Read by
         * {@link SmaxMessagePublishNewsletterRequest#toNode()} when
         * folding the media payload into the message body.
         *
         * @return an {@link Optional} carrying the node, or
         *         {@link Optional#empty()} when no media payload is
         *         being sent
         */
        public Optional<Node> senderContentTypeMediaRcat() {
            return Optional.ofNullable(senderContentTypeMediaRcat);
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
                    && Objects.equals(this.msgMetaOrigin, that.msgMetaOrigin)
                    && Objects.equals(this.senderContentTypeMediaRcat, that.senderContentTypeMediaRcat)
                    && Objects.equals(this.clientIdContent, that.clientIdContent);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, msgMetaOrigin, senderContentTypeMediaRcat, clientIdContent);
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
            return "SmaxMessagePublishNewsletterPayload.WithClientIdOnly[stanzaId=" + stanzaId
                    + ", msgMetaOrigin=" + msgMetaOrigin
                    + ", senderContentTypeMediaRcat=" + senderContentTypeMediaRcat
                    + ", clientIdContent=" + clientIdContent + ']';
        }
    }
}
