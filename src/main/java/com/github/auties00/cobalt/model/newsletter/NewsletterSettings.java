package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * The settings of a newsletter, currently containing only the reaction
 * codes configuration.
 */
@ProtobufMessage
public final class NewsletterSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    NewsletterReactionSettings reactionCodes;

    /**
     * Constructs a new {@code NewsletterSettings} with the specified reaction
     * codes configuration.
     *
     * @param reactionCodes the reaction codes settings, must not be {@code null}
     * @throws NullPointerException if {@code reactionCodes} is {@code null}
     */
    NewsletterSettings(NewsletterReactionSettings reactionCodes) {
        this.reactionCodes = Objects.requireNonNull(reactionCodes, "reactionCodes cannot be null");
    }

    /**
     * Returns the reaction codes settings.
     *
     * @return the reaction codes settings, never {@code null}
     */
    public NewsletterReactionSettings reactionCodes() {
        return reactionCodes;
    }

    /**
     * Sets the reaction codes settings.
     *
     * @param reactionCodes the reaction codes settings
     */
    public void setReactionCodes(NewsletterReactionSettings reactionCodes) {
        this.reactionCodes = reactionCodes;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterSettings that
               && Objects.equals(reactionCodes, that.reactionCodes);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(reactionCodes);
    }
}
