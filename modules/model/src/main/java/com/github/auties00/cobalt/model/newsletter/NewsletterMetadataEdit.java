package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Input model for {@code WhatsAppClient.editNewsletterMetadata}. The
 * {@link #newsletter} JID identifies the target; the remaining fields
 * are optional and only the ones that are set are written to the server.
 *
 * <p>{@link #newsletter} is required. The other three fields are
 * independently optional — unset fields are omitted from the wire
 * update and leave the corresponding server-side value untouched.
 */
@ProtobufMessage
public final class NewsletterMetadataEdit {
    /**
     * JID of the newsletter whose metadata is being edited.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Jid newsletter;

    /**
     * Optional new display name, or {@code null} to leave the existing
     * name untouched.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    final String name;

    /**
     * Optional new description, or {@code null} to leave the existing
     * description untouched.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    final String description;

    /**
     * Optional new JPEG-encoded profile picture, or {@code null} to
     * leave the existing picture untouched. A zero-length array clears
     * the picture, mirroring WhatsApp Web's {@code encodePicture}
     * semantics.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    final byte[] picture;

    /**
     * Constructs a new {@code NewsletterMetadataEdit}.
     *
     * @param newsletter  the newsletter JID; required
     * @param name        the new display name, or {@code null}
     * @param description the new description, or {@code null}
     * @param picture     the new JPEG-encoded picture, or {@code null}
     * @throws NullPointerException if {@code newsletter} is {@code null}
     */
    NewsletterMetadataEdit(Jid newsletter, String name, String description, byte[] picture) {
        this.newsletter = Objects.requireNonNull(newsletter, "newsletter cannot be null");
        this.name = name;
        this.description = description;
        this.picture = picture;
    }

    /**
     * Returns the newsletter JID.
     *
     * @return the newsletter JID, never {@code null}
     */
    public Jid newsletter() {
        return newsletter;
    }

    /**
     * Returns the new display name.
     *
     * @return an {@link Optional} carrying the new name, or empty when unset
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the new description.
     *
     * @return an {@link Optional} carrying the new description, or empty
     *         when unset
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the new JPEG-encoded picture bytes.
     *
     * @return an {@link Optional} carrying the new picture, or empty
     *         when unset
     */
    public Optional<byte[]> picture() {
        return Optional.ofNullable(picture);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NewsletterMetadataEdit) obj;
        return Objects.equals(newsletter, that.newsletter) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Arrays.equals(picture, that.picture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(newsletter, name, description, Arrays.hashCode(picture));
    }

    @Override
    public String toString() {
        return "NewsletterMetadataEdit[" +
                "newsletter=" + newsletter + ", " +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "picture=" + (picture == null ? "null" : picture.length + " bytes") + ']';
    }
}
