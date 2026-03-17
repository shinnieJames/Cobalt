package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * The profile metadata of the admin who sent a particular newsletter message,
 * including their display name and profile picture information.
 */
@ProtobufMessage
public final class NewsletterAdminProfile {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String pictureId;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String pictureDirectPath;

    /**
     * Constructs a new {@code NewsletterAdminProfile} with the specified
     * admin metadata.
     *
     * @param id                the admin profile identifier, may be {@code null}
     * @param name              the admin display name
     * @param pictureId         the profile picture identifier, may be {@code null}
     * @param pictureDirectPath the direct path to the profile picture, may be {@code null}
     */
    NewsletterAdminProfile(String id, String name, String pictureId, String pictureDirectPath) {
        this.id = id;
        this.name = name;
        this.pictureId = pictureId;
        this.pictureDirectPath = pictureDirectPath;
    }

    /**
     * Returns the admin profile identifier, if available.
     *
     * @return an {@link Optional} containing the admin id, or empty if not set
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the admin display name, if available.
     *
     * @return an {@link Optional} containing the admin name, or empty if not set
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the profile picture identifier, if available.
     *
     * @return an {@link Optional} containing the picture id, or empty if not set
     */
    public Optional<String> pictureId() {
        return Optional.ofNullable(pictureId);
    }

    /**
     * Returns the direct path to the admin's profile picture, if available.
     *
     * @return an {@link Optional} containing the direct path, or empty if not set
     */
    public Optional<String> pictureDirectPath() {
        return Optional.ofNullable(pictureDirectPath);
    }

    /**
     * Sets the admin profile identifier.
     *
     * @param id the admin id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the admin display name.
     *
     * @param name the admin name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the profile picture identifier.
     *
     * @param pictureId the picture id
     */
    public void setPictureId(String pictureId) {
        this.pictureId = pictureId;
    }

    /**
     * Sets the direct path to the admin's profile picture.
     *
     * @param pictureDirectPath the direct path
     */
    public void setPictureDirectPath(String pictureDirectPath) {
        this.pictureDirectPath = pictureDirectPath;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NewsletterAdminProfile that
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(pictureId, that.pictureId)
                && Objects.equals(pictureDirectPath, that.pictureDirectPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, pictureId, pictureDirectPath);
    }

    @Override
    public String toString() {
        return "NewsletterAdminProfile[" +
                "id=" + id +
                ", name=" + name +
                ", pictureId=" + pictureId +
                ", pictureDirectPath=" + pictureDirectPath +
                ']';
    }
}
