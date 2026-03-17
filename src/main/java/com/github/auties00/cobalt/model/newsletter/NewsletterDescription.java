package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The description of a newsletter, including its unique identifier,
 * text content, and the timestamp of the last update.
 */
@ProtobufMessage
public final class NewsletterDescription {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant updateTimestamp;

    /**
     * Constructs a new {@code NewsletterDescription} with the specified
     * identifier, text, and update timestamp.
     *
     * @param id              the description identifier, must not be {@code null}
     * @param text            the description text, must not be {@code null}
     * @param updateTimestamp the timestamp of the last description update, may be {@code null}
     * @throws NullPointerException if {@code id} or {@code text} is {@code null}
     */
    NewsletterDescription(String id, String text, Instant updateTimestamp) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.updateTimestamp = updateTimestamp;
    }

    /**
     * Returns the description identifier.
     *
     * @return the id, never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the description text.
     *
     * @return the text, never {@code null}
     */
    public String text() {
        return text;
    }

    /**
     * Returns the timestamp of the last description update, if available.
     *
     * @return an {@link Optional} containing the update timestamp,
     *         or empty if not set
     */
    public Optional<Instant> updateTimestamp() {
        return Optional.ofNullable(updateTimestamp);
    }

    /**
     * Sets the description identifier.
     *
     * @param id the description id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the description text.
     *
     * @param text the description text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Sets the timestamp of the last description update.
     *
     * @param updateTimestamp the update timestamp
     */
    public void setUpdateTimestamp(Instant updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterDescription that
                            && Objects.equals(id, that.id)
                            && Objects.equals(text, that.text)
                            && Objects.equals(updateTimestamp, that.updateTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, updateTimestamp);
    }
}
