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
 * The outbound {@code <iq type="set" xmlns="w:g2" to="<parentGroupJid>">} stanza that deactivates a community
 * (parent group) along with its sub-groups.
 *
 * @apiNote
 * Drives the community-delete pipeline surfaced by {@code WAWebGroupCommunityJob.deleteParentGroup}; emit
 * one of these per community to be torn down and pair it with {@link SmaxGroupsDeleteParentGroupResponse} to
 * read the relay's verdict. The payload is a bare {@code <delete_parent/>} child with no attributes; the
 * target community is identified solely by the IQ envelope's {@code to} attribute.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsDeleteParentGroupRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseSetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQSetRequestMixin")
public final class SmaxGroupsDeleteParentGroupRequest implements SmaxOperation.Request {
    /**
     * The parent (community) group {@link Jid} routed verbatim into the IQ envelope's {@code to} attribute.
     */
    private final Jid parentGroupJid;

    /**
     * Constructs a request targeting the given community.
     *
     * @apiNote
     * Build one request per community deletion; the relay does not accept multiple parents in a single IQ.
     *
     * @param parentGroupJid the community {@link Jid}; never {@code null}
     * @throws NullPointerException if {@code parentGroupJid} is {@code null}
     */
    public SmaxGroupsDeleteParentGroupRequest(Jid parentGroupJid) {
        this.parentGroupJid = Objects.requireNonNull(parentGroupJid, "parentGroupJid cannot be null");
    }

    /**
     * Returns the parent community {@link Jid} targeted by this request.
     *
     * @apiNote
     * Mirrors the value that will appear in the rendered IQ envelope's {@code to} attribute.
     *
     * @return the parent group {@link Jid}; never {@code null}
     */
    public Jid parentGroupJid() {
        return parentGroupJid;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a single empty {@code <delete_parent/>} child inside the canonical
     * {@code <iq xmlns="w:g2" type="set" to="<parentGroupJid>">} envelope; no further attributes or children
     * are sent.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsDeleteParentGroupRequest",
            exports = "makeDeleteParentGroupRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var deleteParentNode = new NodeBuilder()
                .description("delete_parent")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", parentGroupJid)
                .attribute("type", "set")
                .content(deleteParentNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsDeleteParentGroupRequest) obj;
        return Objects.equals(this.parentGroupJid, that.parentGroupJid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentGroupJid);
    }

    @Override
    public String toString() {
        return "SmaxGroupsDeleteParentGroupRequest[parentGroupJid=" + parentGroupJid + ']';
    }
}
