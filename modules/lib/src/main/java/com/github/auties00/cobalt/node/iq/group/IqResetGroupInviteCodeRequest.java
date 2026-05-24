package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * The outbound {@code <iq xmlns="w:g2" type="set" to="<group-jid>"><invite/></iq>}
 * request that rotates the invite code of a group.
 *
 * @apiNote
 * Send this when implementing the "revoke invite link" admin action
 * (the menu entry surfaced by WA Web's group settings panel). The
 * relay responds with the freshly-issued code; subscribers update the
 * cached {@code chat.whatsapp.com/<code>} URL accordingly. The caller
 * must be a group admin or a privileged super-admin; otherwise the
 * server returns an {@link IqResetGroupInviteCodeResponse.ClientError}.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@link WhatsAppWebExport
 * resetGroupInviteCode} export verbatim: the payload is a bare
 * {@code <invite/>} child with no attributes, and the IQ {@code to}
 * is the target group JID rather than the {@code g.us} group server.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
public final class IqResetGroupInviteCodeRequest implements IqOperation.Request {
    /**
     * The group whose invite code is being rotated.
     */
    private final Jid groupJid;

    /**
     * Constructs a request that rotates the invite code of the given
     * group.
     *
     * @apiNote
     * The {@code groupJid} ends up verbatim in the {@code <iq to="...">}
     * attribute and never as the payload of a child element, so it
     * must be a group JID (server {@code g.us}); other JIDs cause the
     * server to reply with a malformed-stanza error.
     *
     * @param groupJid the group whose invite code is to be rotated; never {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public IqResetGroupInviteCodeRequest(Jid groupJid) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
    }

    /**
     * Returns the group whose invite code is being rotated.
     *
     * @return the group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Builds the outbound IQ stanza as a {@link NodeBuilder} ready for
     * dispatch.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code wap("iq", {type:"set", xmlns:"w:g2", to:GROUP_JID(t), id}, wap("invite", null))}
     * call verbatim; the {@code <invite/>} child is empty (no
     * attributes, no children) because the relay derives the new code
     * server-side and echoes it back inside the
     * {@link IqResetGroupInviteCodeResponse.Success} reply.
     *
     * @return the IQ envelope builder
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
            exports = "resetGroupInviteCode", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var invitePayload = new NodeBuilder()
                .description("invite")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "set")
                .content(invitePayload);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqResetGroupInviteCodeRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupJid);
    }

    @Override
    public String toString() {
        return "IqResetGroupInviteCodeRequest[groupJid=" + groupJid + ']';
    }
}
