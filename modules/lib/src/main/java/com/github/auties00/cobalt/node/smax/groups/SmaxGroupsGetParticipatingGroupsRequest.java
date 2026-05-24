package com.github.auties00.cobalt.node.smax.groups;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="w:g2" type="get" to="g.us">} stanza that lists every group the caller participates in.
 *
 * @apiNote Drives the {@code WAWebGroupQueryJob.queryGroups} bulk-loader fired after the admin-ship cache is cleared,
 * for example on login or on group-server-side cache invalidation. The two flags toggle the per-group projection:
 * {@link #includeParticipants()} asks the relay to inline each group's participant list, {@link #includeDescription()}
 * asks for the per-group description body.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsGetParticipatingGroupsRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseGetServerMixin")
public final class SmaxGroupsGetParticipatingGroupsRequest implements SmaxOperation.Request {
    /**
     * Whether the relay should include a per-group participant list inside each {@code <group/>} child.
     */
    private final boolean includeParticipants;

    /**
     * Whether the relay should include the per-group description inside each {@code <group/>} child.
     */
    private final boolean includeDescription;

    /**
     * Constructs a request with explicit projection flags.
     *
     * @apiNote Passing {@code includeParticipants=true} and {@code includeDescription=true} matches the WA Web bulk
     * loader's default; passing both as {@code false} retrieves only the minimal {@code <group jid .../>} envelope.
     *
     * @param includeParticipants {@code true} to inline per-group participant lists
     * @param includeDescription  {@code true} to inline per-group description bodies
     */
    public SmaxGroupsGetParticipatingGroupsRequest(boolean includeParticipants, boolean includeDescription) {
        this.includeParticipants = includeParticipants;
        this.includeDescription = includeDescription;
    }

    /**
     * Returns whether participants are included.
     *
     * @return {@code true} when each group's participant list should be inlined
     */
    public boolean includeParticipants() {
        return includeParticipants;
    }

    /**
     * Returns whether descriptions are included.
     *
     * @return {@code true} when each group's description body should be inlined
     */
    public boolean includeDescription() {
        return includeDescription;
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="g.us" type="get">
     *         <participating>
     *             <participants/>
     *             <description/>
     *         </participating>
     *     </iq>
     * }
     * where the {@code <participants/>} and {@code <description/>} markers are present only when the matching flag is
     * {@code true}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <participating/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsGetParticipatingGroupsRequest",
            exports = "makeGetParticipatingGroupsRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var participatingBuilder = new NodeBuilder().description("participating");
        if (includeParticipants) {
            participatingBuilder.content(new NodeBuilder().description("participants").build());
        }
        if (includeDescription) {
            participatingBuilder.content(new NodeBuilder().description("description").build());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", JidServer.groupOrCommunity())
                .attribute("type", "get")
                .content(participatingBuilder.build());
    }

    /**
     * Compares this request to {@code obj} for value equality across every field.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsGetParticipatingGroupsRequest} with identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsGetParticipatingGroupsRequest) obj;
        return this.includeParticipants == that.includeParticipants
                && this.includeDescription == that.includeDescription;
    }

    /**
     * Returns a hash composed of every field.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(includeParticipants, includeDescription);
    }

    /**
     * Returns a debug string carrying every field.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsGetParticipatingGroupsRequest[includeParticipants=" + includeParticipants
                + ", includeDescription=" + includeDescription + ']';
    }
}
