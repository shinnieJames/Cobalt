package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * The outbound {@code <iq xmlns="w:g2" type="set" to="g.us"><invite code="..."/></iq>}
 * request that redeems a group invite code.
 *
 * @apiNote
 * Send this when implementing the "join via invite link" or "join
 * from invite message" flows (the entry points WA Web surfaces from
 * a tapped {@code chat.whatsapp.com/<code>} link and from the
 * {@code GroupInviteAction.joinGroupViaInvite} action behind a chat
 * invite-message attachment). The caller declares up front, via
 * {@link #expectsMembershipApproval()}, whether the group is expected
 * to be approval-gated; the relay's reply differs in shape (a
 * {@code <group>} child when joined immediately, a
 * {@code <membership_approval_request>} child when queued) and the
 * response parser uses this expectation to detect approval-mode
 * mismatches and surface them as
 * {@link IqJoinGroupByInviteCodeResponse.UnexpectedJoinShape}.
 *
 * @implNote
 * This implementation matches WA Web's {@code joinGroupViaInvite}
 * export verbatim on the wire; the
 * {@link #expectsMembershipApproval()} flag has no on-wire
 * representation (the relay does not need it; it always returns
 * whichever shape applies) and is carried purely so the response
 * parser can correlate request/reply expectations.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupInviteJob")
public final class IqJoinGroupByInviteCodeRequest implements IqOperation.Request {
    /**
     * The invite code being redeemed.
     */
    private final String code;

    /**
     * {@code true} when the caller expects approval-gated entry,
     * {@code false} when it expects immediate admission.
     */
    private final boolean expectsMembershipApproval;

    /**
     * Constructs a request that redeems the given invite code.
     *
     * @apiNote
     * Discover whether a group is approval-gated by querying the
     * invite-link metadata first (for example via
     * {@code WAWebInviteV4QueryGroupAction.queryGroupInviteV4Info});
     * pass the result here as {@code expectsMembershipApproval} so
     * the response parser can detect mismatches between the
     * caller's expectation and the relay's actual gating mode.
     *
     * @param code                      the invite code; never {@code null}
     * @param expectsMembershipApproval {@code true} when the caller expects approval-gated entry
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public IqJoinGroupByInviteCodeRequest(String code, boolean expectsMembershipApproval) {
        this.code = Objects.requireNonNull(code, "code cannot be null");
        this.expectsMembershipApproval = expectsMembershipApproval;
    }

    /**
     * Returns the invite code being redeemed.
     *
     * @return the code; never {@code null}
     */
    public String code() {
        return code;
    }

    /**
     * Returns whether the caller expects approval-gated entry.
     *
     * @return {@code true} when the caller expects a {@code <membership_approval_request>} reply, {@code false} when the caller expects an immediate {@code <group>} reply
     */
    public boolean expectsMembershipApproval() {
        return expectsMembershipApproval;
    }

    /**
     * Builds the outbound IQ stanza as a {@link NodeBuilder} ready
     * for dispatch.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code wap("iq", {type:"set", xmlns:"w:g2", to:G_US, id}, wap("invite", {code:CUSTOM_STRING(e)}))}
     * call verbatim; only the invite code crosses the wire (the
     * {@link #expectsMembershipApproval()} flag is purely client-side
     * parser plumbing).
     *
     * @return the IQ envelope builder
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupInviteJob",
            exports = "joinGroupViaInvite",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var inviteNode = new NodeBuilder()
                .description("invite")
                .attribute("code", code)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", JidServer.groupOrCommunity())
                .attribute("type", "set")
                .content(inviteNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqJoinGroupByInviteCodeRequest) obj;
        return this.expectsMembershipApproval == that.expectsMembershipApproval
                && Objects.equals(this.code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, expectsMembershipApproval);
    }

    @Override
    public String toString() {
        return "IqJoinGroupByInviteCodeRequest[code=" + code
                + ", expectsMembershipApproval=" + expectsMembershipApproval + ']';
    }
}
