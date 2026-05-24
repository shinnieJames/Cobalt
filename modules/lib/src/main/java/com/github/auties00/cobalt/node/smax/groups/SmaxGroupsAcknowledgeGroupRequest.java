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
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="set" xmlns="w:g2">} stanza that marks a group chat as not-spam.
 *
 * @apiNote Drives the "report as not spam" affordance on the group safety panel:
 * {@code WAWebConversationSpamUtils.acknowledgeGroupAsNotSpam} and {@code WAWebSendNotSpamJob} both invoke this
 * RPC fire-and-forget when the local user dismisses the spam warning. Send one IQ per group; the relay does not
 * batch.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsAcknowledgeGroupRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseSetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQSetRequestMixin")
public final class SmaxGroupsAcknowledgeGroupRequest implements SmaxOperation.Request {
    /**
     * The group {@link Jid} being acknowledged.
     */
    private final Jid groupJid;

    /**
     * Constructs an acknowledgement request for the given group.
     *
     * @param groupJid the group {@link Jid}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxGroupsAcknowledgeGroupRequest(Jid groupJid) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
    }

    /**
     * Returns the group {@link Jid} being acknowledged.
     *
     * @apiNote The value routes verbatim into the IQ's {@code to} attribute.
     *
     * @return the group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="<groupJid>" type="set">
     *         <ack/>
     *     </iq>
     * }
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the bare {@code <ack/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsAcknowledgeGroupRequest",
            exports = "makeAcknowledgeGroupRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var ackNode = new NodeBuilder()
                .description("ack")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "set")
                .content(ackNode);
    }

    /**
     * Compares this request to {@code obj} for value equality on {@link #groupJid()}.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsAcknowledgeGroupRequest} with the same group
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsAcknowledgeGroupRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid);
    }

    /**
     * Returns a hash derived from {@link #groupJid()}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJid);
    }

    /**
     * Returns a debug string carrying {@link #groupJid()}.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsAcknowledgeGroupRequest[groupJid=" + groupJid + ']';
    }
}
