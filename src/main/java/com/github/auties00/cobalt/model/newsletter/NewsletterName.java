package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The name of a newsletter, including its unique identifier, display text,
 * and the timestamp of the last update.
 */
@ProtobufMessage
public final class NewsletterName {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant updateTime;

    /**
     * Constructs a new {@code NewsletterName} with the specified identifier,
     * text, and update timestamp.
     *
     * @param id         the name identifier, must not be {@code null}
     * @param text       the display text, must not be {@code null}
     * @param updateTime the timestamp of the last name update, may be {@code null}
     * @throws NullPointerException if {@code id} or {@code text} is {@code null}
     */
    NewsletterName(String id, String text, Instant updateTime) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.updateTime = updateTime;
    }

    /**
     * Returns the name identifier.
     *
     * @return the id, never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the display text of the newsletter name.
     *
     * @return the text, never {@code null}
     */
    public String text() {
        return text;
    }

    /**
     * Returns the timestamp of the last name update, if available.
     *
     * @return an {@link Optional} containing the update timestamp,
     *         or empty if not set
     */
    public Optional<Instant> updateTimeSeconds() {
        return Optional.ofNullable(updateTime);
    }

    /**
     * Sets the name identifier.
     *
     * @param id the name id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the display text.
     *
     * @param text the display text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Sets the timestamp of the last name update.
     *
     * @param updateTime the update timestamp
     */
    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterName that
                            && Objects.equals(id, that.id)
                            && Objects.equals(text, that.text)
                            && Objects.equals(updateTime, that.updateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, updateTime);
    }
}
