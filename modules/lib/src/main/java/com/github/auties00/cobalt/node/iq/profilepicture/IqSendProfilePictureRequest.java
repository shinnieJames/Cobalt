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
 * Models the outbound {@code <iq xmlns="w:profile:picture" type="set">} stanza that sets or
 * clears a profile picture.
 *
 * <p>A single request shape covers the four operations the relay accepts, selected by the
 * combination of {@link #groupTarget()} and {@link #picture()}: self-set, self-clear,
 * group-set, and group-clear. When {@link #groupTarget()} is present the picture belongs to
 * that group, otherwise it belongs to the calling user. When {@link #picture()} is present the
 * bytes become the new picture, otherwise the existing picture is cleared. The relay echoes the
 * new picture id in its success reply, which the caller can use to refresh its photo cache.
 */
@WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
public final class IqSendProfilePictureRequest implements IqOperation.Request {
    /**
     * Holds the target group JID when updating a group profile picture, or {@code null} when
     * updating the calling user's own picture.
     *
     * <p>When present this value is stamped into the {@code target} attribute on the
     * {@code <iq>} envelope; when {@code null} the attribute is omitted entirely.
     */
    private final Jid groupTarget;

    /**
     * Holds the new JPEG profile-picture bytes, or {@code null} to clear the existing picture.
     *
     * <p>A non-{@code null} value is wrapped in a {@code <picture type="image">} child of the
     * {@code <iq>} envelope; a {@code null} value produces an envelope with no
     * {@code <picture/>} child, which the relay interprets as a clear request.
     */
    private final byte[] picture;

    /**
     * Constructs a send-profile-picture request from a nullable group target and nullable
     * picture payload.
     *
     * <p>Both arguments are nullable so that one constructor expresses all four (set or clear)
     * by (self or group) call shapes. The {@code picture} array is defensively cloned, so later
     * mutation of the caller's array does not affect the dispatched stanza.
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
     * Returns the optional group JID target of this update.
     *
     * @return an {@link Optional} carrying the group JID, or {@link Optional#empty()} when this
     *         is a self-picture update
     */
    public Optional<Jid> groupTarget() {
        return Optional.ofNullable(groupTarget);
    }

    /**
     * Returns the optional new picture bytes of this update.
     *
     * <p>The returned array is a fresh clone, so callers may mutate it without affecting
     * subsequent reads or the dispatched stanza.
     *
     * @return an {@link Optional} carrying a clone of the JPEG bytes, or {@link Optional#empty()}
     *         when the picture is being cleared
     */
    public Optional<byte[]> picture() {
        return Optional.ofNullable(picture).map(byte[]::clone);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces an {@code <iq xmlns="w:profile:picture" type="set">} envelope addressed to
     * {@link JidServer#user()}. The {@code target} attribute is stamped only when
     * {@link #groupTarget()} is present, and a {@code <picture type="image">} child is appended
     * only when {@link #picture()} is present.
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
     * Compares this request with another object for value equality.
     *
     * <p>Two requests are equal when their group targets are equal and their picture byte
     * arrays are element-wise equal.
     *
     * @param obj the object to compare against
     * @return {@code true} if {@code obj} is an equal {@link IqSendProfilePictureRequest},
     *         otherwise {@code false}
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
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the combined hash of the group target and the picture byte content
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupTarget, Arrays.hashCode(picture));
    }

    /**
     * Returns a debug representation of this request.
     *
     * <p>The picture bytes are summarised as their length rather than printed in full, and a
     * length of {@code -1} denotes a cleared (absent) picture.
     *
     * @return a string carrying the group target and the picture length
     */
    @Override
    public String toString() {
        var pictureLength = picture == null ? -1 : picture.length;
        return "IqSendProfilePictureRequest[groupTarget=" + groupTarget
                + ", pictureLength=" + pictureLength + ']';
    }
}
