package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="set" xmlns="w:g2">} stanza that cancels pending membership-approval requests on
 * an approval-mode group.
 *
 * @apiNote Drives the "Cancel request" affordance surfaced by
 * {@code WAWebGroupCancelMembershipRequestJob.cancelMembershipApprovalRequestJob}: either the requester rescinds
 * their own pending request, or an admin cancels other users' pending requests in bulk. The relay accepts up to
 * 19999 entries per request.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsCancelGroupMembershipRequestsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseSetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQSetRequestMixin")
public final class SmaxGroupsCancelGroupMembershipRequestsRequest implements SmaxOperation.Request {
    /**
     * The group {@link Jid} hosting the pending requests.
     */
    private final Jid groupJid;

    /**
     * The participant {@link Jid}s whose pending requests should be cancelled.
     */
    private final List<Jid> participants;

    /**
     * Constructs a cancel-membership-requests request.
     *
     * @apiNote The relay enforces a 1..19999 cardinality on the {@code <participant>} children.
     *
     * @param groupJid     the group {@link Jid}
     * @param participants the participant {@link Jid}s; defensively copied
     * @throws NullPointerException     if {@code groupJid} or {@code participants} is {@code null}
     * @throws IllegalArgumentException if {@code participants} is empty
     */
    public SmaxGroupsCancelGroupMembershipRequestsRequest(Jid groupJid, List<Jid> participants) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participants, "participants cannot be null");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants cannot be empty");
        }
        this.participants = List.copyOf(participants);
    }

    /**
     * Returns the target group {@link Jid}.
     *
     * @apiNote The value routes verbatim into the IQ's {@code to} attribute.
     *
     * @return the group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Returns the participant {@link Jid}s whose pending requests are being cancelled.
     *
     * @return an unmodifiable list of {@link Jid}s; never {@code null} and never empty
     */
    public List<Jid> participants() {
        return participants;
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="<groupJid>" type="set">
     *         <cancel_membership_requests>
     *             <participant jid="<jid0>"/>
     *             ...
     *         </cancel_membership_requests>
     *     </iq>
     * }
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <cancel_membership_requests>}
     *         payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsCancelGroupMembershipRequestsRequest",
            exports = "makeCancelGroupMembershipRequestsRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var participantNodes = new ArrayList<Node>(participants.size());
        for (var participantJid : participants) {
            var participantNode = new NodeBuilder()
                    .description("participant")
                    .attribute("jid", participantJid)
                    .build();
            participantNodes.add(participantNode);
        }
        var cancelNode = new NodeBuilder()
                .description("cancel_membership_requests")
                .content(participantNodes)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "set")
                .content(cancelNode);
    }

    /**
     * Compares this request to {@code obj} for value equality across both fields.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsCancelGroupMembershipRequestsRequest} with
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
        var that = (SmaxGroupsCancelGroupMembershipRequestsRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.participants, that.participants);
    }

    /**
     * Returns a hash composed of both fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJid, participants);
    }

    /**
     * Returns a debug string carrying both fields.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsCancelGroupMembershipRequestsRequest[groupJid=" + groupJid
                + ", participants=" + participants + ']';
    }
}
