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
 * The outbound {@code <iq xmlns="w:g2" type="get">} stanza that asks the relay for the union of participants across a
 * community's sub-groups.
 *
 * @apiNote Drives the {@code WAWebGroupGetCommunityParticipantsJob.getCommunityParticipants} flow used by both the
 * community member-list page and the share-VCard surface ({@code WAWebFrontendVcardUtils}); pass the community parent
 * group {@link Jid} and dispatch through the matching {@link SmaxGroupsGetLinkedGroupsParticipantsResponse} parser.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetLinkedGroupsParticipantsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQGetRequestMixin")
public final class SmaxGroupsGetLinkedGroupsParticipantsRequest implements SmaxOperation.Request {
    /**
     * The community parent group {@link Jid} surfaced on the IQ's {@code to} attribute.
     */
    private final Jid groupJid;

    /**
     * Constructs a request for the given community parent group.
     *
     * @apiNote Pass only a community parent JID; the relay rejects requests targeting non-parent groups.
     *
     * @param groupJid the community parent group {@link Jid}; never {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxGroupsGetLinkedGroupsParticipantsRequest(Jid groupJid) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
    }

    /**
     * Returns the community parent group {@link Jid}.
     *
     * @return the parent group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="<groupJid>" type="get">
     *         <linked_groups_participants/>
     *     </iq>
     * }
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <linked_groups_participants/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetLinkedGroupsParticipantsRequest",
            exports = "makeGetLinkedGroupsParticipantsRequest",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var payload = new NodeBuilder()
                .description("linked_groups_participants")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "get")
                .content(payload);
    }

    /**
     * Compares this request to {@code obj} for value equality across every field.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsGetLinkedGroupsParticipantsRequest} with identical
     *         fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetLinkedGroupsParticipantsRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid);
    }

    /**
     * Returns a hash composed of every field.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJid);
    }

    /**
     * Returns a debug string carrying every field.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsGetLinkedGroupsParticipantsRequest[groupJid=" + groupJid + ']';
    }
}
