package com.github.auties00.cobalt.node.smax.presence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <presence type="subscribe" to= name? context?/>}
 * subscription stanza.
 *
 * @apiNote
 * Drives WA Web's
 * {@code WASmaxPresenceSubscribeRPC.sendSubscribeRPC}, invoked by
 * {@code WAWebSendPresenceSubscriptionJob.sendUserPresenceSubscription}
 * when the user opens a chat or
 * {@code sendGroupPresenceSubscription} when they enter a group; the
 * relay starts pushing {@link SmaxServerUpdateResponse} updates for
 * the targeted peer until the next subscription expires.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPresenceSubscribeRequest")
public final class SmaxSubscribeRequest implements SmaxOperation.Request {
    /**
     * The peer being subscribed to.
     *
     * @apiNote
     * Routed verbatim into the {@code to} attribute; either a user
     * JID (LID after 1x1 migration, PN before) or a group JID.
     */
    private final Jid presenceTo;

    /**
     * The optional display-name hint to advertise to the peer.
     *
     * @apiNote
     * Routed verbatim into the {@code name} attribute as an
     * {@code OPTIONAL(CUSTOM_STRING, presenceName)}; used by the relay
     * to surface the subscriber's push name in the peer's typing /
     * online UI when applicable.
     */
    private final String presenceName;

    /**
     * The optional parent group JID for participant subscriptions.
     *
     * @apiNote
     * Routed verbatim into the {@code context} attribute as an
     * {@code OPTIONAL(GROUP_JID, presenceContext)}; supplied when the
     * subscription targets a participant of an open group chat so the
     * relay scopes the push to that group's frame only.
     */
    private final Jid presenceContext;

    /**
     * Constructs a new subscription request.
     *
     * @apiNote
     * Embedders build one per peer they want presence updates from;
     * subscriptions expire on the relay, so callers must re-issue
     * them periodically to keep updates flowing.
     *
     * @param presenceTo      the peer to subscribe to; never
     *                        {@code null}
     * @param presenceName    the optional display-name hint; may be
     *                        {@code null}
     * @param presenceContext the optional parent group JID; may be
     *                        {@code null}
     * @throws NullPointerException if {@code presenceTo} is
     *                              {@code null}
     */
    public SmaxSubscribeRequest(Jid presenceTo, String presenceName, Jid presenceContext) {
        this.presenceTo = Objects.requireNonNull(presenceTo, "presenceTo cannot be null");
        this.presenceName = presenceName;
        this.presenceContext = presenceContext;
    }

    /**
     * Returns the peer JID being subscribed to.
     *
     * @apiNote
     * Consumed by {@link #toNode()} to populate the {@code to}
     * attribute.
     *
     * @return the peer JID; never {@code null}
     */
    public Jid presenceTo() {
        return presenceTo;
    }

    /**
     * Returns the optional display-name hint.
     *
     * @apiNote
     * Empty when no hint should be advertised.
     *
     * @return an {@link Optional} carrying the hint
     */
    public Optional<String> presenceName() {
        return Optional.ofNullable(presenceName);
    }

    /**
     * Returns the optional parent group JID.
     *
     * @apiNote
     * Empty when the subscription is not group-scoped.
     *
     * @return an {@link Optional} carrying the group JID
     */
    public Optional<Jid> presenceContext() {
        return Optional.ofNullable(presenceContext);
    }

    /**
     * Builds the outbound presence stanza ready for dispatch.
     *
     * @apiNote
     * Returned unbuilt so the dispatch path can stamp a fresh stanza
     * id before flushing; null-valued attributes are dropped at
     * render time, matching the WA Web {@code OPTIONAL} attribute
     * semantics.
     *
     * @return a {@link NodeBuilder} carrying the
     *         {@code <presence type="subscribe" to= name? context?/>}
     *         envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPresenceSubscribeRequest",
            exports = "makeSubscribeRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        return new NodeBuilder()
                .description("presence")
                .attribute("type", "subscribe")
                .attribute("to", presenceTo)
                .attribute("name", presenceName)
                .attribute("context", presenceContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxSubscribeRequest) obj;
        return Objects.equals(this.presenceTo, that.presenceTo)
                && Objects.equals(this.presenceName, that.presenceName)
                && Objects.equals(this.presenceContext, that.presenceContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(presenceTo, presenceName, presenceContext);
    }

    @Override
    public String toString() {
        return "SmaxSubscribeRequest[presenceTo=" + presenceTo
                + ", presenceName=" + presenceName
                + ", presenceContext=" + presenceContext + ']';
    }
}
