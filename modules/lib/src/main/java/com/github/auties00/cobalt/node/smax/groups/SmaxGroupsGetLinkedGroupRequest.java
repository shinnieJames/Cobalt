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
 * The outbound {@code <iq xmlns="w:g2" type="get">} stanza that asks the relay to traverse a community parent-or-sub
 * linkage and return the linked group's preview metadata.
 *
 * @apiNote Drives the {@code WAWebGroupQuerySubGroupsJob.querySubgroup} flow: from the community page the caller passes
 * the parent group JID as {@link #groupJid()} (the IQ target), {@code "sub_group"} as {@link #queryLinkedType()}, and
 * the target sub-group JID as {@link #queryLinkedJid()}. The optional {@link #subGroupJid()} disambiguates the sub-group
 * lookup when the parent hosts multiple linked groups with the same announcement role.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetLinkedGroupRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsQueryLinkedGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsOptionalSubGroupMixin")
public final class SmaxGroupsGetLinkedGroupRequest implements SmaxOperation.Request {
    /**
     * The {@link Jid} surfaced on the IQ's {@code to} attribute; identifies the anchor group of the linkage.
     */
    private final Jid groupJid;

    /**
     * The linkage direction selector echoed under {@code <query_linked type="..."/>} (typically {@code "sub_group"} or
     * {@code "parent_group"}).
     */
    private final String queryLinkedType;

    /**
     * The {@link Jid} of the linked group being queried; carried under {@code <query_linked jid="..."/>}.
     */
    private final Jid queryLinkedJid;

    /**
     * The optional sub-group disambiguation hint surfaced under {@code <query_linked sub_group_jid="..."/>}.
     */
    private final Jid subGroupJid;

    /**
     * Constructs a request without a sub-group disambiguation hint.
     *
     * @apiNote Convenience overload for the common case where {@code queryLinkedJid} alone identifies the target.
     *
     * @param groupJid        the IQ {@code to} group {@link Jid}; never {@code null}
     * @param queryLinkedType the linkage direction; never {@code null}
     * @param queryLinkedJid  the linked group {@link Jid}; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxGroupsGetLinkedGroupRequest(Jid groupJid, String queryLinkedType, Jid queryLinkedJid) {
        this(groupJid, queryLinkedType, queryLinkedJid, null);
    }

    /**
     * Constructs a fully-parametrised request.
     *
     * @apiNote Pass {@code subGroupJid} when the anchor group hosts multiple linked groups sharing the same
     * announcement role; the relay uses it to scope the lookup.
     *
     * @param groupJid        the IQ {@code to} group {@link Jid}; never {@code null}
     * @param queryLinkedType the linkage direction; never {@code null}
     * @param queryLinkedJid  the linked group {@link Jid}; never {@code null}
     * @param subGroupJid     the optional sub-group disambiguation hint; may be {@code null}
     * @throws NullPointerException if {@code groupJid}, {@code queryLinkedType}, or {@code queryLinkedJid} is
     *                              {@code null}
     */
    public SmaxGroupsGetLinkedGroupRequest(Jid groupJid, String queryLinkedType, Jid queryLinkedJid, Jid subGroupJid) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        this.queryLinkedType = Objects.requireNonNull(queryLinkedType, "queryLinkedType cannot be null");
        this.queryLinkedJid = Objects.requireNonNull(queryLinkedJid, "queryLinkedJid cannot be null");
        this.subGroupJid = subGroupJid;
    }

    /**
     * Returns the IQ {@code to} group {@link Jid}.
     *
     * @return the anchor group {@link Jid}; never {@code null}
     */
    public Jid groupJid() {
        return groupJid;
    }

    /**
     * Returns the linkage direction selector.
     *
     * @apiNote Surfaced verbatim under {@code <query_linked type="..."/>}; typically {@code "sub_group"} or
     * {@code "parent_group"}.
     *
     * @return the linkage direction token; never {@code null}
     */
    public String queryLinkedType() {
        return queryLinkedType;
    }

    /**
     * Returns the linked group's {@link Jid}.
     *
     * @return the linked group {@link Jid}; never {@code null}
     */
    public Jid queryLinkedJid() {
        return queryLinkedJid;
    }

    /**
     * Returns the optional sub-group disambiguation hint.
     *
     * @return an {@link Optional} carrying the sub-group {@link Jid}, or empty when omitted
     */
    public Optional<Jid> subGroupJid() {
        return Optional.ofNullable(subGroupJid);
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="<groupJid>" type="get">
     *         <query_linked type="<queryLinkedType>" jid="<queryLinkedJid>" sub_group_jid="<subGroupJid>"/>
     *     </iq>
     * }
     * where the {@code sub_group_jid} attribute is omitted when {@link #subGroupJid()} is empty.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <query_linked/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetLinkedGroupRequest",
            exports = "makeGetLinkedGroupRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var queryLinkedBuilder = new NodeBuilder()
                .description("query_linked")
                .attribute("type", queryLinkedType)
                .attribute("jid", queryLinkedJid);
        if (subGroupJid != null) {
            queryLinkedBuilder.attribute("sub_group_jid", subGroupJid);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "get")
                .content(queryLinkedBuilder.build());
    }

    /**
     * Compares this request to {@code obj} for value equality across every field.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsGetLinkedGroupRequest} with identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetLinkedGroupRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid)
                && Objects.equals(this.queryLinkedType, that.queryLinkedType)
                && Objects.equals(this.queryLinkedJid, that.queryLinkedJid)
                && Objects.equals(this.subGroupJid, that.subGroupJid);
    }

    /**
     * Returns a hash composed of every field.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJid, queryLinkedType, queryLinkedJid, subGroupJid);
    }

    /**
     * Returns a debug string carrying every field.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsGetLinkedGroupRequest[groupJid=" + groupJid
                + ", queryLinkedType=" + queryLinkedType
                + ", queryLinkedJid=" + queryLinkedJid
                + ", subGroupJid=" + subGroupJid + ']';
    }
}
