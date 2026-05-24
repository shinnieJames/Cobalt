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
 * The outbound {@code <iq xmlns="w:g2" type="get">} stanza that asks the relay for the group's pending
 * "Report to admin" moderation queue.
 *
 * @apiNote Drives the {@code WAWebReportToAdminJob.getReportedMsgs} flow that powers the admin moderation drawer:
 * the relay returns every message that participants have flagged for admin review, along with the reporters who
 * flagged it. Dispatch through the matching {@link SmaxGroupsGetReportedMessagesResponse} parser.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetReportedMessagesRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQGetRequestMixin")
public final class SmaxGroupsGetReportedMessagesRequest implements SmaxOperation.Request {
    /**
     * The group {@link Jid} whose moderation queue is being inspected; surfaced on the IQ's {@code to} attribute.
     */
    private final Jid groupJid;

    /**
     * Constructs a request for the given group.
     *
     * @apiNote The caller must be a group admin; the relay rejects non-admin queries as a client error.
     *
     * @param groupJid the group {@link Jid}; never {@code null}
     * @throws NullPointerException if {@code groupJid} is {@code null}
     */
    public SmaxGroupsGetReportedMessagesRequest(Jid groupJid) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
    }

    /**
     * Returns the group {@link Jid}.
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
     *     <iq xmlns="w:g2" to="<groupJid>" type="get">
     *         <reports/>
     *     </iq>
     * }
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <reports/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetReportedMessagesRequest",
            exports = "makeGetReportedMessagesRequest",
            adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var payload = new NodeBuilder()
                .description("reports")
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
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsGetReportedMessagesRequest} with identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetReportedMessagesRequest) obj;
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
        return "SmaxGroupsGetReportedMessagesRequest[groupJid=" + groupJid + ']';
    }
}
