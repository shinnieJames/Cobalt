package com.github.auties00.cobalt.stanza.iq.profilepicture;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.media.SizedInputStream;
import com.github.auties00.cobalt.stanza.StanzaBuilder;
import com.github.auties00.cobalt.stanza.iq.IqStanza;

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
 * supplied stream becomes the new picture, otherwise the existing picture is cleared.
 *
 * <p>The picture is carried as a {@link SizedInputStream} so the payload is streamed straight to
 * the wire at serialisation time rather than buffered into a {@code byte[]} first; the relay
 * echoes the new picture id in its success reply.
 */
@WhatsAppWebModule(moduleName = "WAWebSendProfilePictureJob")
public final class IqSendProfilePictureRequest implements IqStanza.Request {
    /**
     * Holds the target group JID when updating a group profile picture, or {@code null} when
     * updating the calling user's own picture.
     *
     * <p>When present this value is stamped into the {@code target} attribute on the
     * {@code <iq>} envelope; when {@code null} the attribute is omitted entirely.
     */
    private final Jid groupTarget;

    /**
     * Holds the new JPEG profile-picture stream, or {@code null} to clear the existing picture.
     *
     * <p>A non-{@code null} value is streamed into a {@code <picture type="image">} child of the
     * {@code <iq>} envelope; a {@code null} value produces an envelope with no
     * {@code <picture/>} child, which the relay interprets as a clear request.
     */
    private final SizedInputStream picture;

    /**
     * Constructs a send-profile-picture request from a nullable group target and nullable
     * picture stream.
     *
     * <p>Both nullable arguments let one constructor express all four (set or clear) by (self or
     * group) call shapes.
     *
     * @param groupTarget the group JID for a group-picture update, or {@code null} for a
     *                    self-picture update
     * @param picture     the new JPEG picture, or {@code null} to clear the existing picture
     */
    public IqSendProfilePictureRequest(Jid groupTarget, SizedInputStream picture) {
        this.groupTarget = groupTarget;
        this.picture = picture;
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
     * Returns the optional new picture stream of this update.
     *
     * @return an {@link Optional} carrying the picture stream, or {@link Optional#empty()} when
     *         the picture is being cleared
     */
    public Optional<SizedInputStream> picture() {
        return Optional.ofNullable(picture);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Produces an {@code <iq xmlns="w:profile:picture" type="set">} envelope addressed to
     * {@link JidServer#user()}. The {@code target} attribute is stamped only when
     * {@link #groupTarget()} is present, and a {@code <picture type="image">} child is appended
     * only when {@link #picture()} is present, streaming its payload at serialisation time.
     *
     * @return a {@link StanzaBuilder} carrying the {@code <iq>} envelope and the optional
     *         {@code <picture>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSendProfilePictureJob",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public StanzaBuilder toStanza() {
        var iqBuilder = new StanzaBuilder()
                .description("iq")
                .attribute("xmlns", "w:profile:picture")
                .attribute("to", JidServer.user())
                .attribute("type", "set");
        if (groupTarget != null) {
            iqBuilder.attribute("target", groupTarget);
        }
        if (picture != null) {
            var pictureNode = new StanzaBuilder()
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
     * <p>Two requests are equal when their group targets are equal and they carry the same
     * picture stream instance (a sized stream has no value equality).
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
                && this.picture == that.picture;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the combined hash of the group target and the advertised picture length
     */
    @Override
    public int hashCode() {
        return Objects.hash(groupTarget, picture == null ? -1L : picture.length());
    }

    /**
     * Returns a debug representation of this request.
     *
     * <p>The picture is summarised as its advertised length rather than read, and a length of
     * {@code -1} denotes a cleared (absent) picture.
     *
     * @return a string carrying the group target and the picture length
     */
    @Override
    public String toString() {
        return "IqSendProfilePictureRequest[groupTarget=" + groupTarget
                + ", pictureLength=" + (picture == null ? -1 : picture.length()) + ']';
    }
}
