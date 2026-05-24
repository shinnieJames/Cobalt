package com.github.auties00.cobalt.node.smax.voip;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <call><link_query/></call>} request that resolves an
 * existing call-link token to its metadata (creator, media, scheduled-event
 * presence, waiting-room state).
 *
 * @apiNote
 * Drives the "Open call link" surface: when a user follows a
 * {@code https://call.whatsapp.com/{voice|video}/<token>} URL the client
 * issues this RPC to discover the link's owner and waiting-room flag before
 * joining or asking for approval.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutVoipLinkQueryRequest")
public final class SmaxLinkQueryRequest implements SmaxOperation.Request {
    /**
     * The call-link token to resolve.
     *
     * @apiNote
     * The same opaque suffix carried by the public
     * {@code https://call.whatsapp.com/{voice|video}/<token>} URL.
     */
    private final String linkQueryToken;

    /**
     * The media type the caller plans to use, carried by the {@code media}
     * attribute.
     *
     * @apiNote
     * Either {@code "audio"} or {@code "video"} on the wire; the relay
     * uses this to confirm the link's configured media matches the caller's
     * intent before authorising the join.
     */
    private final String linkQueryMedia;

    /**
     * The optional action attribute. Either {@code "preview"} for a passive
     * resolve or {@code "edit"} for a creator-side metadata edit; absent for
     * the default resolve.
     */
    private final String linkQueryAction;

    /**
     * Constructs a request.
     *
     * @param linkQueryToken  the call-link token; never {@code null}
     * @param linkQueryMedia  the media type; never {@code null}
     * @param linkQueryAction the optional action; may be {@code null}
     * @throws NullPointerException if {@code linkQueryToken} or {@code linkQueryMedia} is {@code null}
     */
    public SmaxLinkQueryRequest(String linkQueryToken, String linkQueryMedia, String linkQueryAction) {
        this.linkQueryToken = Objects.requireNonNull(linkQueryToken, "linkQueryToken cannot be null");
        this.linkQueryMedia = Objects.requireNonNull(linkQueryMedia, "linkQueryMedia cannot be null");
        this.linkQueryAction = linkQueryAction;
    }

    /**
     * Returns the call-link token to resolve.
     *
     * @return the token; never {@code null}
     */
    public String linkQueryToken() {
        return linkQueryToken;
    }

    /**
     * Returns the media type the caller plans to use.
     *
     * @return the media type; never {@code null}
     */
    public String linkQueryMedia() {
        return linkQueryMedia;
    }

    /**
     * Returns the optional action.
     *
     * @return an {@link Optional} carrying the action, or empty when omitted
     */
    public Optional<String> linkQueryAction() {
        return Optional.ofNullable(linkQueryAction);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a {@code <call to="call">} envelope around a
     * {@code <link_query>} child, mirroring
     * {@code WASmaxOutVoipLinkQueryRequest.makeLinkQueryRequest}. The
     * {@code action} attribute is omitted when {@link #linkQueryAction()}
     * is empty.
     *
     * @return a {@link NodeBuilder} carrying the {@code <call><link_query/></call>}
     *         stanza
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutVoipLinkQueryRequest",
            exports = "makeLinkQueryRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var linkQueryBuilder = new NodeBuilder()
                .description("link_query")
                .attribute("token", linkQueryToken)
                .attribute("media", linkQueryMedia);
        if (linkQueryAction != null) {
            linkQueryBuilder.attribute("action", linkQueryAction);
        }
        return new NodeBuilder()
                .description("call")
                .attribute("to", JidServer.call())
                .content(linkQueryBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxLinkQueryRequest) obj;
        return Objects.equals(this.linkQueryToken, that.linkQueryToken)
                && Objects.equals(this.linkQueryMedia, that.linkQueryMedia)
                && Objects.equals(this.linkQueryAction, that.linkQueryAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkQueryToken, linkQueryMedia, linkQueryAction);
    }

    @Override
    public String toString() {
        return "SmaxLinkQueryRequest[linkQueryToken=" + linkQueryToken
                + ", linkQueryMedia=" + linkQueryMedia
                + ", linkQueryAction=" + linkQueryAction + ']';
    }
}
