package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;
import java.util.Optional;

/**
 * The current state of a newsletter, indicating whether it is active,
 * suspended, or geo-suspended.
 *
 * <p>The type is stored as a lowercase string matching the values defined
 * in the WhatsApp protocol: {@code "active"}, {@code "suspended"}, or
 * {@code "geosuspended"}.
 */
@ProtobufMessage
public final class NewsletterState {
    private static final NewsletterState UNKNOWN = new NewsletterState(null);

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String type;

    /**
     * Constructs a new {@code NewsletterState} with the specified type.
     *
     * @param type the state type string, may be {@code null}
     */
    NewsletterState(String type) {
        this.type = type;
    }

    /**
     * Returns a {@code NewsletterState} representing an unknown state.
     *
     * @return the unknown state singleton, never {@code null}
     */
    public static NewsletterState unknown() {
        return UNKNOWN;
    }

    /**
     * Returns the state type, if available.
     *
     * @return an {@link Optional} containing the state type string,
     *         or empty if the state is unknown
     */
    public Optional<String> type() {
        return Optional.ofNullable(type);
    }

    /**
     * Sets the state type.
     *
     * @param type the state type string
     */
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof NewsletterState that
                            && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }
}
