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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="set" xmlns="w:g2" to="<parent>">} stanza that posts a new sub-group
 * suggestion (a fresh sub-group to spin up, or one or more existing groups to absorb) inside a community.
 *
 * @apiNote
 * Drives the sub-group suggestion pipeline surfaced by {@code WAWebSubgroupSuggestionCreateJob}; pair with
 * {@link SmaxGroupsCreateSubGroupSuggestionResponse} to read the relay's verdict. The body is a single
 * {@code <sub_group_suggestion/>} child whose shape is decided by the {@link #suggestion()} oneof.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsCreateSubGroupSuggestionRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseSetGroupMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseIQSetRequestMixin")
public final class SmaxGroupsCreateSubGroupSuggestionRequest implements SmaxOperation.Request {
    /**
     * The parent community {@link Jid} routed verbatim into the IQ envelope's {@code to} attribute.
     */
    private final Jid parentGroupJid;

    /**
     * The suggestion-body oneof choosing between {@link SmaxGroupsCreateSubGroupSuggestionSuggestion.NewGroup}
     * (spin up a fresh sub-group) and {@link SmaxGroupsCreateSubGroupSuggestionSuggestion.ExistingGroups}
     * (link existing groups in as sub-groups).
     */
    private final SmaxGroupsCreateSubGroupSuggestionSuggestion suggestion;

    /**
     * Constructs a request targeting the given community with the supplied suggestion body.
     *
     * @apiNote
     * Pick the {@link SmaxGroupsCreateSubGroupSuggestionSuggestion} variant that matches the user-facing
     * action; the request envelope is identical in both cases.
     *
     * @param parentGroupJid the parent community {@link Jid}; never {@code null}
     * @param suggestion     the suggestion body; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxGroupsCreateSubGroupSuggestionRequest(Jid parentGroupJid, SmaxGroupsCreateSubGroupSuggestionSuggestion suggestion) {
        this.parentGroupJid = Objects.requireNonNull(parentGroupJid, "parentGroupJid cannot be null");
        this.suggestion = Objects.requireNonNull(suggestion, "suggestion cannot be null");
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
     * Returns the suggestion-body oneof.
     *
     * @apiNote
     * Inspect via {@code instanceof} to discriminate between the new-group and existing-groups branches.
     *
     * @return the suggestion; never {@code null}
     */
    public SmaxGroupsCreateSubGroupSuggestionSuggestion suggestion() {
        return suggestion;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation builds a single {@code <sub_group_suggestion/>} child, lets the
     * {@link SmaxGroupsCreateSubGroupSuggestionSuggestion#contributeTo(NodeBuilder)} hook stamp the
     * branch-specific attributes and children, then wraps it in the canonical
     * {@code <iq xmlns="w:g2" type="set" to="<parentGroupJid>">} envelope.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsCreateSubGroupSuggestionRequest",
            exports = "makeCreateSubGroupSuggestionRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var suggestionBuilder = new NodeBuilder()
                .description("sub_group_suggestion");
        suggestion.contributeTo(suggestionBuilder);
        var suggestionNode = suggestionBuilder.build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", parentGroupJid)
                .attribute("type", "set")
                .content(suggestionNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsCreateSubGroupSuggestionRequest) obj;
        return Objects.equals(this.parentGroupJid, that.parentGroupJid)
                && Objects.equals(this.suggestion, that.suggestion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentGroupJid, suggestion);
    }

    @Override
    public String toString() {
        return "SmaxGroupsCreateSubGroupSuggestionRequest[parentGroupJid=" + parentGroupJid
                + ", suggestion=" + suggestion + ']';
    }
}
