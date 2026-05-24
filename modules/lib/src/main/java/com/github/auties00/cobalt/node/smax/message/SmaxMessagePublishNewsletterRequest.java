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
 * The outbound {@code <message to=NEWSLETTER_JID>} stanza builder for
 * publishing to a newsletter; carries either a brand-new post or a
 * reply / reaction / poll-vote keyed by the target's server-id.
 *
 * @apiNote
 * Used by callers driving the WA Web
 * {@code WAWebNewsletterSendMessageQueryJob} surface that dispatches
 * through
 * {@code WASmaxMessagePublishNewsletterRPC.sendNewsletterRPC}. The
 * relay replies with a {@link SmaxMessagePublishNewsletterResponse}:
 * a {@link SmaxMessagePublishNewsletterResponse.Success} when the
 * publish landed, a {@link SmaxMessagePublishNewsletterResponse.Negative}
 * when it was rejected.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutMessagePublishNewsletterRequest")
public final class SmaxMessagePublishNewsletterRequest implements SmaxOperation.Request {
    /**
     * The target newsletter JID; routed verbatim into the message's
     * {@code to} attribute.
     */
    private final Jid newsletterJid;

    /**
     * The disjunctive publish payload selecting between server-id
     * addressing and brand-new-post addressing.
     */
    private final SmaxMessagePublishNewsletterPayload payload;

    /**
     * Constructs a newsletter publish request.
     *
     * @apiNote
     * Use this when assembling a
     * {@link SmaxMessagePublishNewsletterRequest} for dispatch through
     * the smax send pipeline.
     *
     * @param newsletterJid the target newsletter JID; never
     *                      {@code null}
     * @param payload       the disjunctive publish payload; never
     *                      {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxMessagePublishNewsletterRequest(Jid newsletterJid, SmaxMessagePublishNewsletterPayload payload) {
        this.newsletterJid = Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload cannot be null");
    }

    /**
     * Returns the target newsletter JID.
     *
     * @return the JID; never {@code null}
     */
    public Jid newsletterJid() {
        return newsletterJid;
    }

    /**
     * Returns the publish payload.
     *
     * @return the payload; never {@code null}
     */
    public SmaxMessagePublishNewsletterPayload payload() {
        return payload;
    }

    /**
     * Builds the outbound {@code <message>} stanza ready for
     * dispatch.
     *
     * @apiNote
     * The brand-new-post arm folds the optional msg-meta-origin and
     * sender-content-type-media RCAT children alongside the
     * client-id content node; the server-id arm stamps the
     * {@code server_id} attribute and embeds the inner content
     * directly.
     *
     * @implNote
     * This implementation dispatches on the
     * {@link SmaxMessagePublishNewsletterPayload} sealed-interface
     * variants via a Java pattern-matching switch; the dispatch layer
     * stamps the message's outer id-correlation envelope.
     *
     * @return a {@link NodeBuilder} carrying the partially-built
     *         message envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutMessagePublishNewsletterRequest",
            exports = "makeNewsletterRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var builder = new NodeBuilder()
                .description("message")
                .attribute("to", newsletterJid);
        switch (payload) {
            case SmaxMessagePublishNewsletterPayload.WithServerId withServerId -> {
                builder.attribute("id", withServerId.stanzaId());
                builder.attribute("server_id", withServerId.messageServerId());
                builder.content(withServerId.innerContent());
            }
            case SmaxMessagePublishNewsletterPayload.WithClientIdOnly withClientId -> {
                builder.attribute("id", withClientId.stanzaId());
                var children = new ArrayList<Node>(3);
                withClientId.msgMetaOrigin().ifPresent(children::add);
                withClientId.senderContentTypeMediaRcat().ifPresent(children::add);
                children.add(withClientId.clientIdContent());
                builder.content(children.toArray(Node[]::new));
            }
        }
        return builder;
    }

    /**
     * Compares this request to another for value equality on the
     * newsletter JID and publish payload.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a
     *         {@link SmaxMessagePublishNewsletterRequest} with
     *         identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxMessagePublishNewsletterRequest) obj;
        return Objects.equals(this.newsletterJid, that.newsletterJid)
                && Objects.equals(this.payload, that.payload);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(newsletterJid, payload);
    }

    /**
     * Returns a debug-friendly representation of this request.
     *
     * @apiNote
     * Intended for logging; the format is not part of the public
     * contract.
     *
     * @return the string form
     */
    @Override
    public String toString() {
        return "SmaxMessagePublishNewsletterRequest[newsletterJid=" + newsletterJid
                + ", payload=" + payload + ']';
    }
}
