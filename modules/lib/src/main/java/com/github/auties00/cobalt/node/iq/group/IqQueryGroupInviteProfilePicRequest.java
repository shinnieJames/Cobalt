package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound IQ request that fetches the profile picture of an
 * invite-only group before the caller has joined it.
 *
 * @apiNote
 * Send this when implementing the "preview group avatar from an
 * invite" surface, fed by either a {@code chat.whatsapp.com/<code>}
 * link or an in-chat invite-message attachment. The two flows ship
 * different stanza shapes, selected by
 * {@link IqQueryGroupInviteProfilePicMode}: the link mode addresses
 * the group JID under {@code w:g2} and only carries the invite code,
 * while the message mode addresses {@code s.whatsapp.net} under
 * {@code w:profile:picture} and additionally carries the inviting
 * admin JID and the invite-message expiration timestamp. The relay
 * responds with the CDN URL and a stable picture id that the caller
 * can pass back on subsequent fetches to short-circuit when the cached
 * id is still authoritative.
 *
 * @implNote
 * This implementation collapses WA Web's
 * {@code queryGroupInviteLinkProfilePic} and
 * {@code queryGroupInviteMessageProfilePic} exports into a single
 * request class keyed by {@link IqQueryGroupInviteProfilePicMode};
 * the WA Web exports take an options object
 * {@code {id, type, query}}, which Cobalt maps to the
 * {@link #pictureId()}, {@link #pictureType()} and
 * {@link #pictureQuery()} accessors.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryGroupInviteProfilePicApi")
public final class IqQueryGroupInviteProfilePicRequest implements IqOperation.Request {
    /**
     * The dispatch mode.
     */
    private final IqQueryGroupInviteProfilePicMode mode;

    /**
     * The target group JID.
     */
    private final Jid groupJid;

    /**
     * The invite code being previewed.
     */
    private final String code;

    /**
     * The previously-cached picture identifier, or {@code null} when
     * no cached id is supplied.
     */
    private final String pictureId;

    /**
     * The picture variant, or {@code null} when the caller defers to
     * the relay default.
     */
    private final String pictureType;

    /**
     * The picture-query mode, or {@code null} when the caller defers
     * to the relay default.
     */
    private final String pictureQuery;

    /**
     * The inviting admin JID, only used in
     * {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE}.
     */
    private final Jid adminJid;

    /**
     * The invite-message expiration timestamp (seconds since the
     * epoch), only used in
     * {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE}.
     */
    private final String expiration;

    /**
     * Constructs a request with the given parameters.
     *
     * @apiNote
     * Pass {@code pictureId} when the caller already holds a cached
     * picture id from an earlier fetch; the relay then omits the
     * {@code <picture>} child entirely on the success reply (signalling
     * "your cached id is still authoritative"). Pass
     * {@code pictureType = "preview"} for the low-resolution thumbnail
     * and {@code pictureType = "image"} for the full-size avatar; the
     * {@link WhatsAppWebModule
     * WAWebInviteProfilePicAction} module hard-codes
     * {@code {type: "preview", query: "url"}} for link mode and
     * {@code {type: "image", query: "url"}} for message mode.
     *
     * @param mode         the dispatch mode; never {@code null}
     * @param groupJid     the target group {@link Jid}; never {@code null}
     * @param code         the invite code; never {@code null}
     * @param pictureId    the cached picture id, or {@code null}
     * @param pictureType  the picture variant, or {@code null}
     * @param pictureQuery the picture-query mode, or {@code null}
     * @param adminJid     the inviting admin {@link Jid}; required in {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE}, ignored in {@link IqQueryGroupInviteProfilePicMode#INVITE_LINK}
     * @param expiration   the invite expiration timestamp; required in {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE}, ignored in {@link IqQueryGroupInviteProfilePicMode#INVITE_LINK}
     * @throws NullPointerException if {@code mode}, {@code groupJid} or {@code code} is {@code null}, or if {@code adminJid} or {@code expiration} is {@code null} in {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE}
     */
    public IqQueryGroupInviteProfilePicRequest(IqQueryGroupInviteProfilePicMode mode, Jid groupJid, String code, String pictureId, String pictureType,
                   String pictureQuery, Jid adminJid, String expiration) {
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        if (mode == IqQueryGroupInviteProfilePicMode.INVITE_MESSAGE) {
            Objects.requireNonNull(adminJid, "adminJid cannot be null in INVITE_MESSAGE mode");
            Objects.requireNonNull(expiration, "expiration cannot be null in INVITE_MESSAGE mode");
        }
        this.mode = mode;
        this.groupJid = groupJid;
        this.code = code;
        this.pictureId = pictureId;
        this.pictureType = pictureType;
        this.pictureQuery = pictureQuery;
        this.adminJid = adminJid;
        this.expiration = expiration;
    }

    /**
     * Returns the dispatch mode.
     *
     * @return the {@link IqQueryGroupInviteProfilePicMode}; never {@code null}
     */
    public IqQueryGroupInviteProfilePicMode mode() {
        return mode;
    }

    /**
     * Returns the target group JID.
     *
     * @return the group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Returns the invite code.
     *
     * @return the code; never {@code null}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the optional cached picture identifier.
     *
     * @return an {@link Optional} carrying the cached picture id, or empty when no cached id was supplied
     */
    public Optional<String> pictureId() {
        return Optional.ofNullable(pictureId);
    }

    /**
     * Returns the optional picture variant.
     *
     * @return an {@link Optional} carrying the picture variant, or empty when the caller defers to the relay default
     */
    public Optional<String> pictureType() {
        return Optional.ofNullable(pictureType);
    }

    /**
     * Returns the optional picture-query mode.
     *
     * @return an {@link Optional} carrying the picture-query mode, or empty when the caller defers to the relay default
     */
    public Optional<String> pictureQuery() {
        return Optional.ofNullable(pictureQuery);
    }

    /**
     * Returns the optional inviting admin JID.
     *
     * @return an {@link Optional} carrying the admin {@link Jid}, or empty in {@link IqQueryGroupInviteProfilePicMode#INVITE_LINK}
     */
    public Optional<Jid> adminJid() {
        return Optional.ofNullable(adminJid);
    }

    /**
     * Returns the optional invite-message expiration timestamp.
     *
     * @return an {@link Optional} carrying the expiration, or empty in {@link IqQueryGroupInviteProfilePicMode#INVITE_LINK}
     */
    public Optional<String> expiration() {
        return Optional.ofNullable(expiration);
    }

    /**
     * Builds the outbound IQ stanza as a {@link NodeBuilder} ready
     * for dispatch.
     *
     * @implNote
     * This implementation switches on {@link #mode()} and delegates
     * to either {@link #buildInviteLinkStanza()} or
     * {@link #buildInviteMessageStanza()}; the two helpers mirror
     * WA Web's {@code queryGroupInviteLinkProfilePic} and
     * {@code queryGroupInviteMessageProfilePic} bodies verbatim,
     * including the WA Web idiom of dropping omitted picture-option
     * attributes via {@code DROP_ATTR} (Cobalt achieves the same by
     * skipping the {@code attribute(...)} calls when the field is
     * {@code null}).
     *
     * @return the IQ envelope builder
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
            exports = "queryGroupInviteLinkProfilePic",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebQueryGroupInviteProfilePicApi",
            exports = "queryGroupInviteMessageProfilePic",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        if (mode == IqQueryGroupInviteProfilePicMode.INVITE_LINK) {
            return buildInviteLinkStanza();
        }
        return buildInviteMessageStanza();
    }

    /**
     * Builds the {@link IqQueryGroupInviteProfilePicMode#INVITE_LINK}
     * stanza variant.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code queryGroupInviteLinkProfilePic} body verbatim: the IQ
     * envelope is {@code {to:GROUP_JID(a), type:"get", xmlns:"w:g2"}}
     * and the {@code <picture>} child carries the invite code under
     * the {@code invite} attribute plus the optional
     * {@code id}/{@code type}/{@code query} picture options.
     *
     * @return the IQ envelope builder
     */
    private NodeBuilder buildInviteLinkStanza() {
        var pictureBuilder = new NodeBuilder()
                .description("picture")
                .attribute("invite", code);
        if (pictureId != null) {
            pictureBuilder.attribute("id", pictureId);
        }
        if (pictureType != null) {
            pictureBuilder.attribute("type", pictureType);
        }
        if (pictureQuery != null) {
            pictureBuilder.attribute("query", pictureQuery);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "get")
                .content(pictureBuilder.build());
    }

    /**
     * Builds the
     * {@link IqQueryGroupInviteProfilePicMode#INVITE_MESSAGE} stanza
     * variant.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code queryGroupInviteMessageProfilePic} body verbatim: the IQ
     * envelope is
     * {@code {to:S_WHATSAPP_NET, type:"get", target:GROUP_JID(l), xmlns:"w:profile:picture"}},
     * and the {@code <picture>} child wraps an
     * {@code <add_request code expiration admin/>} grandchild that
     * proves the caller holds a valid in-chat invite-message context
     * for the target group.
     *
     * @return the IQ envelope builder
     */
    private NodeBuilder buildInviteMessageStanza() {
        var addRequestBuilder = new NodeBuilder()
                .description("add_request")
                .attribute("code", code)
                .attribute("expiration", expiration)
                .attribute("admin", adminJid);
        var pictureBuilder = new NodeBuilder()
                .description("picture");
        if (pictureId != null) {
            pictureBuilder.attribute("id", pictureId);
        }
        if (pictureType != null) {
            pictureBuilder.attribute("type", pictureType);
        }
        if (pictureQuery != null) {
            pictureBuilder.attribute("query", pictureQuery);
        }
        pictureBuilder.content(addRequestBuilder.build());
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture")
                .attribute("to", JidServer.user())
                .attribute("target", groupJid)
                .attribute("type", "get")
                .content(pictureBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqQueryGroupInviteProfilePicRequest) obj;
        return this.mode == that.mode
                && Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.code, that.code)
                && Objects.equals(this.pictureId, that.pictureId)
                && Objects.equals(this.pictureType, that.pictureType)
                && Objects.equals(this.pictureQuery, that.pictureQuery)
                && Objects.equals(this.adminJid, that.adminJid)
                && Objects.equals(this.expiration, that.expiration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, groupJid, code, pictureId, pictureType, pictureQuery,
                adminJid, expiration);
    }

    @Override
    public String toString() {
        return "IqQueryGroupInviteProfilePicRequest[mode=" + mode
                + ", groupJid=" + groupJid
                + ", code=" + code
                + ", pictureId=" + pictureId
                + ", pictureType=" + pictureType
                + ", pictureQuery=" + pictureQuery
                + ", adminJid=" + adminJid
                + ", expiration=" + expiration + ']';
    }
}
