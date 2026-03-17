package com.github.auties00.cobalt.model.newsletter;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

/**
 * A reaction on a newsletter message, containing the emoji content,
 * the total count of that reaction, and whether the current user
 * has sent it.
 */
@ProtobufMessage
public final class NewsletterReaction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String content;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    long count;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    boolean fromMe;

    /**
     * Constructs a new {@code NewsletterReaction} with the specified emoji
     * content, count, and sender flag.
     *
     * @param content the emoji reaction content, must not be {@code null}
     * @param count   the total number of this reaction
     * @param fromMe  {@code true} if the current user sent this reaction
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public NewsletterReaction(String content, long count, boolean fromMe) {
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.count = count;
        this.fromMe = fromMe;
    }

    /**
     * Returns the emoji reaction content.
     *
     * @return the emoji string, never {@code null}
     */
    public String content() {
        return content;
    }

    /**
     * Sets the emoji reaction content.
     *
     * @param content the emoji string, must not be {@code null}
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns the total count of this reaction.
     *
     * @return the reaction count
     */
    public long count() {
        return count;
    }

    /**
     * Sets the total count of this reaction.
     *
     * @param count the reaction count
     */
    public void setCount(long count) {
        this.count = count;
    }

    /**
     * Returns whether the current user sent this reaction.
     *
     * @return {@code true} if the reaction is from the current user
     */
    public boolean fromMe() {
        return fromMe;
    }

    /**
     * Sets whether the current user sent this reaction.
     *
     * @param fromMe {@code true} if the reaction is from the current user
     */
    public void setFromMe(boolean fromMe) {
        this.fromMe = fromMe;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterReaction that
                            && count == that.count
                            && fromMe == that.fromMe
                            && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, count, fromMe);
    }
}
