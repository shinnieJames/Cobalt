package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Input model for {@code WhatsAppClient.createNewsletter}. Carries the
 * display name, optional description, and optional profile picture for
 * the new newsletter.
 *
 * <p>{@link #name} is required; {@link #description} and {@link #picture}
 * are optional.
 */
@ProtobufMessage
public final class NewsletterCreate {
    /**
     * Display name of the newsletter.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final String name;

    /**
     * Optional newsletter description.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    final String description;

    /**
     * Optional JPEG-encoded profile picture.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    final byte[] picture;

    /**
     * Constructs a new {@code NewsletterCreate}.
     *
     * @param name        the newsletter display name; required
     * @param description the newsletter description, or {@code null}
     * @param picture     the JPEG-encoded profile picture, or {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    NewsletterCreate(String name, String description, byte[] picture) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description;
        this.picture = picture;
    }

    /**
     * Returns the display name.
     *
     * @return the name, never {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the newsletter description.
     *
     * @return an {@link Optional} carrying the description, or empty
     *         when unset
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the JPEG-encoded profile picture.
     *
     * @return an {@link Optional} carrying the picture bytes, or empty
     *         when unset
     */
    public Optional<byte[]> picture() {
        return Optional.ofNullable(picture);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NewsletterCreate) obj;
        return Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Arrays.equals(picture, that.picture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, Arrays.hashCode(picture));
    }

    @Override
    public String toString() {
        return "NewsletterCreate[" +
                "name=" + name + ", " +
                "description=" + description + ", " +
                "picture=" + (picture == null ? "null" : picture.length + " bytes") + ']';
    }
}
