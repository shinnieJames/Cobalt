package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
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
 * Outbound {@code <iq type="get" xmlns="w:g2" to="g.us">} stanza that fetches metadata for several groups in one
 * round-trip.
 *
 * This request backs the bulk-fetch path used to hydrate community and sub-group panels and search results. The
 * relay accepts up to 10000 entries per request; callers should pre-batch larger working sets.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBatchGetGroupInfoRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetServerMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQGetRequestMixin")
public final class SmaxGroupsBatchGetGroupInfoRequest implements SmaxOperation.Request {
    /**
     * Holds the group {@link Jid}s to fetch metadata for.
     */
    private final List<Jid> groupJids;

    /**
     * Holds the optional caller-supplied correlation token surfaced as the {@code <query context="...">}
     * attribute.
     */
    private final String queryContext;

    /**
     * Constructs a request without a correlation token.
     *
     * @param groupJids the group {@link Jid}s to fetch
     * @throws NullPointerException     if {@code groupJids} is {@code null}
     * @throws IllegalArgumentException if {@code groupJids} is empty
     */
    public SmaxGroupsBatchGetGroupInfoRequest(List<Jid> groupJids) {
        this(groupJids, null);
    }

    /**
     * Constructs a request with the given correlation token.
     *
     * The correlation token round-trips back on the relay's reply and lets the caller correlate the response with
     * the upstream UI request that triggered it. The supplied list is defensively copied.
     *
     * @param groupJids    the group {@link Jid}s to fetch
     * @param queryContext the optional correlation token; may be {@code null}
     * @throws NullPointerException     if {@code groupJids} is {@code null}
     * @throws IllegalArgumentException if {@code groupJids} is empty
     */
    public SmaxGroupsBatchGetGroupInfoRequest(List<Jid> groupJids, String queryContext) {
        Objects.requireNonNull(groupJids, "groupJids cannot be null");
        if (groupJids.isEmpty()) {
            throw new IllegalArgumentException("groupJids cannot be empty");
        }
        this.groupJids = List.copyOf(groupJids);
        this.queryContext = queryContext;
    }

    /**
     * Returns the list of group {@link Jid}s carried by this request.
     *
     * @return an unmodifiable list of group {@link Jid}s; never {@code null} and never empty
     */
    public List<Jid> groupJids() {
        return groupJids;
    }

    /**
     * Returns the optional correlation token.
     *
     * @return an {@link Optional} carrying the correlation token, or empty when the caller did not supply one
     */
    public Optional<String> queryContext() {
        return Optional.ofNullable(queryContext);
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="g.us" type="get">
     *         <query context="<queryContext>">
     *             <group jid="<jid0>"/>
     *             <group jid="<jid1>"/>
     *             ...
     *         </query>
     *     </iq>
     * }
     * The {@code context} attribute is only emitted when {@link #queryContext()} is present.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the per-group {@code <query/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsBatchGetGroupInfoRequest",
            exports = "makeBatchGetGroupInfoRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var groupNodes = new ArrayList<Node>(groupJids.size());
        for (var groupJid : groupJids) {
            var groupNode = new NodeBuilder()
                    .description("group")
                    .attribute("jid", groupJid)
                    .build();
            groupNodes.add(groupNode);
        }
        var queryBuilder = new NodeBuilder()
                .description("query")
                .content(groupNodes);
        if (queryContext != null) {
            queryBuilder.attribute("context", queryContext);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", JidServer.groupOrCommunity())
                .attribute("type", "get")
                .content(queryBuilder.build());
    }

    /**
     * Compares this request to {@code obj} for value equality across both fields.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsBatchGetGroupInfoRequest} with identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsBatchGetGroupInfoRequest) obj;
        return Objects.equals(this.groupJids, that.groupJids)
                && Objects.equals(this.queryContext, that.queryContext);
    }

    /**
     * Returns a hash composed of both fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJids, queryContext);
    }

    /**
     * Returns a debug string carrying both fields.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsBatchGetGroupInfoRequest[groupJids=" + groupJids
                + ", queryContext=" + queryContext + ']';
    }
}
