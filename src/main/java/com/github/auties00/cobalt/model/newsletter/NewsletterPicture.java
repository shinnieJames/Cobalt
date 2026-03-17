package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * A newsletter profile picture reference, containing the picture
 * identifier, type (image or preview), and the direct path for
 * downloading the image from the media server.
 *
 * <p>WhatsApp Web distinguishes between a full-resolution {@code "image"}
 * type and a smaller {@code "preview"} type. Both share the same
 * structure.
 */
@ProtobufMessage
public final class NewsletterPicture {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String type;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String directPath;

    /**
     * Constructs a new {@code NewsletterPicture} with the specified
     * identifier, type, and direct path.
     *
     * @param id         the picture identifier, must not be {@code null}
     * @param type       the picture type ({@code "image"} or {@code "preview"}),
     *                   must not be {@code null}
     * @param directPath the direct path to the picture on the media server,
     *                   must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    NewsletterPicture(String id, String type, String directPath) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.directPath = Objects.requireNonNull(directPath, "directPath cannot be null");
    }

    /**
     * Returns the picture identifier.
     *
     * @return the id, never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the picture type.
     *
     * @return the type ({@code "image"} or {@code "preview"}), never {@code null}
     */
    public String type() {
        return type;
    }

    /**
     * Returns the direct path to the picture on the media server.
     *
     * @return the direct path, never {@code null}
     */
    public String directPath() {
        return directPath;
    }

    /**
     * Sets the picture identifier.
     *
     * @param id the picture id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the picture type.
     *
     * @param type the picture type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Sets the direct path to the picture.
     *
     * @param directPath the direct path
     */
    public void setDirectPath(String directPath) {
        this.directPath = directPath;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterPicture that
                            && Objects.equals(id, that.id)
                            && Objects.equals(type, that.type)
                            && Objects.equals(directPath, that.directPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, directPath);
    }
}
