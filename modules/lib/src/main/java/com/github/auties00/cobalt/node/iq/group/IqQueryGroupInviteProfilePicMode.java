package com.github.auties00.cobalt.node.iq.group;

/**
 * The dispatch mode for an {@link IqQueryGroupInviteProfilePicRequest}.
 *
 * @apiNote
 * Pick {@link #INVITE_LINK} when previewing the group avatar attached
 * to a {@code chat.whatsapp.com/...} share, and {@link #INVITE_MESSAGE}
 * when previewing the avatar attached to an in-chat invite stanza that
 * carries an {@code add_request} payload. The two modes ship different
 * IQ envelopes (the link mode targets the group JID under
 * {@code w:g2}, the message mode targets the {@code s.whatsapp.net}
 * server under {@code w:profile:picture}); the request builder picks
 * between them based on this enum.
 *
 * @implNote
 * This implementation models the public/private split that
 * {@link com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule WAWebQueryGroupInviteProfilePicApi}
 * encodes as the {@code queryGroupInviteLinkProfilePic} and
 * {@code queryGroupInviteMessageProfilePic} exports, collapsed to a
 * single {@link IqQueryGroupInviteProfilePicRequest} class with one
 * enum-driven branch.
 */
public enum IqQueryGroupInviteProfilePicMode {
    /**
     * The invite-link variant.
     *
     * @apiNote
     * Used when the caller holds a bare invite code (the
     * {@code https://chat.whatsapp.com/<code>} share format) and
     * wants to preview the group avatar before deciding whether to
     * join. The request emits
     * {@code <iq xmlns="w:g2" type="get" to="<group-jid>"><picture invite="<code>"/></iq>}.
     */
    INVITE_LINK,

    /**
     * The invite-message variant.
     *
     * @apiNote
     * Used when the caller has received an in-chat invite stanza
     * with an admin JID and expiration timestamp (as embedded in
     * a group-invite-message attachment) and wants to preview the
     * group avatar before accepting. The request emits
     * {@code <iq xmlns="w:profile:picture" type="get" to="s.whatsapp.net" target="<group-jid>"><picture><add_request code expiration admin/></picture></iq>}.
     */
    INVITE_MESSAGE
}
