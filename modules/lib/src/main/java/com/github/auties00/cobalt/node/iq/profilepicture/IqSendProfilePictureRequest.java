package com.github.auties00.cobalt.node.iq.profilepicture;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbound {@code <iq xmlns="w:profile:picture" type="set">} stanza setting or clearing the
 * profile picture for the calling user or for a target group.
 *
 * @apiNote
 * Used by the Settings ("change my photo") and group-admin ("change group photo") surfaces
 * via WA Web's {@code WAWebContactProfilePicThumbBridge.sendSetPicture}; the same request
 * shape covers the four operations (self-set, self-clear, group-set, group-clear) by
 * varying {@link #groupTarget()} and {@link #picture()}. The relay returns the new picture
 * id in the success reply so the client can refresh the photo cache.
 */
@WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
public final class IqSendProfilePictureRequest implements IqOperation.Request {
    /**
     * Target group JID when updating a group profile picture.
     *
     * @apiNote
     * {@code null} when updating the calling user's own picture; routed into the {@code target}
     * attribute on the {@code <iq>} envelope when present, omitted entirely otherwise (WA
     * Web uses {@code WAWap.DROP_ATTR} to express the same omission).
     */
    private final Jid groupTarget;

    /**
     * New JPEG profile-picture bytes.
     *
     * @apiNote
     * {@code null} signals "clear the existing picture", which produces an {@code <iq>}
     * envelope with no {@code <picture/>} child; a non-{@code null} value is wrapped in
     * {@code <picture type="image">PIC_BYTES</picture>}.
     */
    private final byte[] picture;

    /**
     * Constructs a new send-profile-picture request.
     *
     * @apiNote
     * Defensively clones {@code picture}; both arguments are nullable to support the four
     * (set/clear) x (self/group) call shapes.
     *
     * @param groupTarget the group JID for a group-picture update, or {@code null} for a
     *                    self-picture update
     * @param picture     the new JPEG bytes, or {@code null} to clear the existing picture
     */
    public IqSendProfilePictureRequest(Jid groupTarget, byte[] picture) {
        this.groupTarget = groupTarget;
        this.picture = picture == null ? null : picture.clone();
    }

    /**
     * Returns the optional group JID target.
     *
     * @return an {@link Optional} carrying the group JID, or {@link Optional#empty()} when
     *         this is a self-picture update
     */
    public Optional<Jid> groupTarget() {
        return Optional.ofNullable(groupTarget);
    }

    /**
     * Returns the optional new picture bytes.
     *
     * @apiNote
     * Returns a defensive copy; callers may mutate the array without affecting subsequent
     * reads or the dispatched stanza.
     *
     * @return an {@link Optional} carrying a clone of the JPEG bytes, or
     *         {@link Optional#empty()} when the picture is being cleared
     */
    public Optional<byte[]> picture() {
        return Optional.ofNullable(picture).map(byte[]::clone);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="w:profile:picture" type="set">} envelope addressed to
     * {@link JidServer#user()}; the {@code target} attribute is stamped only when
     * {@link #groupTarget()} is present, and the {@code <picture/>} child is added only when
     * {@link #picture()} is present.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the optional
     *         {@code <picture>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var iqBuilder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture")
                .attribute("to", JidServer.user())
                .attribute("type", "set");
        if (groupTarget != null) {
            iqBuilder.attribute("target", groupTarget);
        }
        if (picture != null) {
            var pictureNode = new NodeBuilder()
                    .description("picture")
                    .attribute("type", "image")
                    .content(picture)
                    .build();
            iqBuilder.content(pictureNode);
        }
        return iqBuilder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSendProfilePictureRequest) obj;
        return Objects.equals(this.groupTarget, that.groupTarget)
                && Arrays.equals(this.picture, that.picture);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupTarget, Arrays.hashCode(picture));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        var pictureLength = picture == null ? -1 : picture.length;
        return "IqSendProfilePictureRequest[groupTarget=" + groupTarget
                + ", pictureLength=" + pictureLength + ']';
    }
}
