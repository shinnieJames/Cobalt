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
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq type="set" xmlns="w:g2">} stanza that replaces a group's subject (display name).
 *
 * @apiNote Drives the "Edit group name" affordance on the group-info screen. The relay returns a bare
 * {@link SmaxGroupsSetSubjectResponse.Success} envelope on success; the subject change is broadcast back to all
 * participants via a separate notification path.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsSetSubjectRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsSetSubjectChangeSubjectMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutGroupsBaseSetGroupMixin")
public final class SmaxGroupsSetSubjectRequest implements SmaxOperation.Request {
    /**
     * The group {@link Jid} whose subject is being mutated.
     */
    private final Jid groupJid;

    /**
     * The new subject text (UTF-8, server-bounded length).
     */
    private final String subject;

    /**
     * Constructs a set-subject request.
     *
     * @param groupJid the group {@link Jid}
     * @param subject  the new subject text; the relay enforces a server-side length cap
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxGroupsSetSubjectRequest(Jid groupJid, String subject) {
        this.groupJid = Objects.requireNonNull(groupJid, "groupJid cannot be null");
        this.subject = Objects.requireNonNull(subject, "subject cannot be null");
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
     * Returns the new subject text.
     *
     * @return the new subject; never {@code null}
     */
    public String subject() {
        return subject;
    }

    /**
     * Materialises the outbound IQ stanza ready for dispatch.
     *
     * @apiNote The resulting envelope is
     * {@snippet :
     *     <iq xmlns="w:g2" to="<groupJid>" type="set">
     *         <subject>...UTF-8 bytes...</subject>
     *     </iq>
     * }
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the {@code <subject>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutGroupsSetSubjectRequest",
            exports = "makeSetSubjectRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var subjectNode = new NodeBuilder()
                .description("subject")
                .content(subject.getBytes(StandardCharsets.UTF_8))
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:g2")
                .attribute("to", groupJid)
                .attribute("type", "set")
                .content(subjectNode);
    }

    /**
     * Compares this request to {@code obj} for value equality across both fields.
     *
     * @param obj the other object
     * @return {@code true} when {@code obj} is a {@link SmaxGroupsSetSubjectRequest} with identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGroupsSetSubjectRequest) obj;
        return Objects.equals(this.groupJid, that.groupJid) && Objects.equals(this.subject, that.subject);
    }

    /**
     * Returns a hash composed of both fields.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupJid, subject);
    }

    /**
     * Returns a debug string carrying both fields.
     *
     * @return the debug representation
     */
    @Override
    public String toString() {
        return "SmaxGroupsSetSubjectRequest[groupJid=" + groupJid + ", subject=" + subject + ']';
    }
}
