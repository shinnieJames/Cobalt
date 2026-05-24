package com.github.auties00.cobalt.node.iq.group;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The outbound {@code <iq xmlns="w:g2" type="set" to="g.us"><leave>...</leave></iq>}
 * request that leaves one or more groups or communities in a single
 * batched stanza.
 *
 * @apiNote
 * Send this when implementing the "leave group", "leave community",
 * or "leave several communities" admin actions (the menu entries
 * surfaced by WA Web's chat options, by the community detail panel,
 * and by the bulk community-leave flow). Pick {@link Mode#GROUP} for
 * regular groups (each target encodes as {@code <group id="..."/>})
 * and {@link Mode#LINKED_GROUPS} for communities (each target encodes
 * as {@code <linked_groups parent_group_jid="..."/>}, which the relay
 * expands server-side to leave the parent and every linked sub-group
 * atomically).
 *
 * @implNote
 * This implementation collapses WA Web's three exports
 * ({@code leaveGroup} for one regular group, {@code leaveCommunity}
 * for one community, and {@code leaveCommunities} for the bulk-leave
 * surface) into a single request class keyed by {@link Mode} and a
 * list of targets; the single-target exports in WA Web are thin
 * wrappers around the bulk path that extract {@code result[0]} from
 * the parser output.
 */
@WhatsAppWebModule(moduleName = "WAWebGroupExitJob")
public final class IqGroupExitRequest implements IqOperation.Request {
    /**
     * The grandchild-shape discriminator carried inside the
     * {@code <leave>} payload.
     *
     * @apiNote
     * Choose between regular-group leave (one
     * {@code <group id="..."/>} per target) and community leave (one
     * {@code <linked_groups parent_group_jid="..."/>} per target);
     * the two shapes cannot be mixed inside a single request.
     */
    public enum Mode {
        /**
         * The regular-group leave shape.
         *
         * @apiNote
         * Each target encodes as {@code <group id="GROUP_JID"/>} and
         * causes the relay to leave that group only. Use this for
         * both regular groups and announcement-only groups.
         */
        GROUP,
        /**
         * The community-leave shape.
         *
         * @apiNote
         * Each target encodes as
         * {@code <linked_groups parent_group_jid="COMMUNITY_JID"/>}
         * and causes the relay to leave the named community plus
         * every sub-group linked to it in one atomic step.
         */
        LINKED_GROUPS
    }

    /**
     * The list of target JIDs to leave.
     */
    private final List<Jid> targets;

    /**
     * The grandchild-shape discriminator.
     */
    private final Mode mode;

    /**
     * Constructs a request that leaves the given list of targets in
     * the given mode.
     *
     * @apiNote
     * For a single-target leave, pass a one-element list; the relay's
     * batched and single-target paths use the same wire format and
     * differ only in the size of the {@code <leave>} payload.
     *
     * @implNote
     * This implementation defensively copies {@code targets} via
     * {@link List#copyOf(java.util.Collection)} so later mutation of
     * the caller's list cannot reach the dispatched stanza.
     *
     * @param targets the list of group or community {@link Jid}s to leave; never {@code null} and never empty
     * @param mode    the grandchild-shape discriminator; never {@code null}
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code targets} is empty
     */
    public IqGroupExitRequest(List<Jid> targets, Mode mode) {
        Objects.requireNonNull(targets, "targets cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets cannot be empty");
        }
        this.targets = List.copyOf(targets);
        this.mode = mode;
    }

    /**
     * Returns the list of JIDs being left.
     *
     * @return an unmodifiable {@link List} of target {@link Jid}s; never {@code null}
     */
    public List<Jid> targets() {
        return targets;
    }

    /**
     * Returns the grandchild-shape discriminator.
     *
     * @return the {@link Mode}; never {@code null}
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Builds the outbound IQ stanza as a {@link NodeBuilder} ready
     * for dispatch.
     *
     * @implNote
     * This implementation matches WA Web's
     * {@code wap("iq", {to:G_US, type:"set", xmlns:"w:g2", id}, wap("leave", null, [...children]))}
     * call verbatim; each target in {@link #targets()} becomes one
     * {@code <group id="..."/>} child in {@link Mode#GROUP} or one
     * {@code <linked_groups parent_group_jid="..."/>} child in
     * {@link Mode#LINKED_GROUPS}, and the IQ {@code to} is always the
     * {@link JidServer#groupOrCommunity()} group server rather than
     * any per-target group JID.
     *
     * @return the IQ envelope builder
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveGroup", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveCommunity", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebGroupExitJob",
            exports = "leaveCommunities", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var grandchildren = new ArrayList<Node>(targets.size());
        for (var target : targets) {
            NodeBuilder childBuilder;
            if (mode == Mode.GROUP) {
                childBuilder = new NodeBuilder()
                        .description("group")
                        .attribute("id", target);
            } else {
                childBuilder = new NodeBuilder()
                        .description("linked_groups")
                        .attribute("parent_group_jid", target);
            }
            grandchildren.add(childBuilder.build());
        }
        var leaveNode = new NodeBuilder()
                .description("leave")
                .content(grandchildren)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", JidServer.groupOrCommunity())
                .attribute("type", "set")
                .content(leaveNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqGroupExitRequest) obj;
        return Objects.equals(this.targets, that.targets)
                && this.mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targets, mode);
    }

    @Override
    public String toString() {
        return "IqGroupExitRequest[targets=" + targets
                + ", mode=" + mode + ']';
    }
}
