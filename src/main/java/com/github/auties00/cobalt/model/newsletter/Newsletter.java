package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

/**
 * A WhatsApp newsletter (channel), containing its JID, state, metadata,
 * viewer metadata, and an abstract message store.
 *
 * <p>Newsletters are not end-to-end encrypted. Message content is
 * received as plaintext protobuf and decoded using the standard
 * {@code Message} protobuf specification.
 *
 * <p>All metadata fields and their accessors are concrete. Message
 * operations are abstract so that different store implementations
 * can provide their own storage strategy.
 */
@ProtobufMessage
public abstract non-sealed class Newsletter implements JidProvider {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    private Jid jid;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    private NewsletterState state;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    private NewsletterMetadata metadata;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    private NewsletterViewerMetadata viewerMetadata;

    @ProtobufProperty(index = 6, type = ProtobufType.INT32)
    private int unreadMessagesCount;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    private Instant timestamp;

    protected Newsletter(Jid jid, NewsletterState state, NewsletterMetadata metadata, NewsletterViewerMetadata viewerMetadata, int unreadMessagesCount, Instant timestamp) {
        this.jid = jid;
        this.state = state;
        this.metadata = metadata;
        this.viewerMetadata = viewerMetadata;
        this.unreadMessagesCount = unreadMessagesCount;
        this.timestamp = timestamp;
    }

    /**
     * Adds a message to this newsletter's message collection.
     *
     * @param info the message to add
     */
    public abstract void addMessage(NewsletterMessageInfo info);

    /**
     * Removes a message from this newsletter by its identifier.
     *
     * @param messageId the message identifier
     * @return {@code true} if the message was removed
     */
    public abstract boolean removeMessage(String messageId);

    /**
     * Removes all messages from this newsletter.
     */
    public abstract void removeMessages();

    /**
     * Returns an unmodifiable sequenced view of the messages.
     *
     * @return the messages view, never {@code null}
     */
    public abstract SequencedCollection<NewsletterMessageInfo> messages();

    /**
     * Finds a message by its identifier.
     *
     * @param messageId the message identifier
     * @return an {@link Optional} containing the message, or empty if not found
     */
    public abstract Optional<NewsletterMessageInfo> getMessageById(String messageId);

    /**
     * Returns the oldest message in this newsletter, if any.
     *
     * @return an {@link Optional} containing the oldest message,
     *         or empty if there are no messages
     */
    public abstract Optional<NewsletterMessageInfo> oldestMessage();

    /**
     * Returns the newest message in this newsletter, if any.
     *
     * @return an {@link Optional} containing the newest message,
     *         or empty if there are no messages
     */
    public abstract Optional<NewsletterMessageInfo> newestMessage();

    /**
     * Returns this newsletter's JID.
     *
     * @return the JID, never {@code null}
     */
    @Override
    public Jid toJid() {
        return jid;
    }

    /**
     * Returns this newsletter's JID.
     *
     * @return the JID, never {@code null}
     */
    public Jid jid() {
        return jid;
    }

    /**
     * Sets this newsletter's JID.
     *
     * @param jid the JID, must not be {@code null}
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    public void setJid(Jid jid) {
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
    }

    /**
     * Returns the newsletter state, if available.
     *
     * @return an {@link Optional} containing the state, or empty if not set
     */
    public Optional<NewsletterState> state() {
        return Optional.ofNullable(state);
    }

    /**
     * Sets the newsletter state.
     *
     * @param state the newsletter state
     */
    public void setState(NewsletterState state) {
        this.state = state;
    }

    /**
     * Returns the newsletter metadata, if available.
     *
     * @return an {@link Optional} containing the metadata, or empty if not set
     */
    public Optional<NewsletterMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }

    /**
     * Sets the newsletter metadata.
     *
     * @param metadata the newsletter metadata
     */
    public void setMetadata(NewsletterMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Returns the viewer's metadata, if available.
     *
     * @return an {@link Optional} containing the viewer metadata,
     *         or empty if not set
     */
    public Optional<NewsletterViewerMetadata> viewerMetadata() {
        return Optional.ofNullable(viewerMetadata);
    }

    /**
     * Sets the viewer's metadata.
     *
     * @param viewerMetadata the viewer metadata
     */
    public void setViewerMetadata(NewsletterViewerMetadata viewerMetadata) {
        this.viewerMetadata = viewerMetadata;
    }

    /**
     * Returns the number of unread messages.
     *
     * @return the unread messages count
     */
    public int unreadMessagesCount() {
        return unreadMessagesCount;
    }

    /**
     * Sets the number of unread messages.
     *
     * @param unreadMessagesCount the unread messages count
     */
    public void setUnreadMessagesCount(int unreadMessagesCount) {
        this.unreadMessagesCount = unreadMessagesCount;
    }

    /**
     * Returns the timestamp in seconds of the last activity.
     *
     * @return the timestamp in seconds
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Sets the timestamp in seconds of the last activity.
     *
     * @param timestamp the timestamp in seconds
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof Newsletter that
                            && unreadMessagesCount == that.unreadMessagesCount
                            && Objects.equals(jid, that.jid)
                            && Objects.equals(state, that.state)
                            && Objects.equals(metadata, that.metadata)
                            && Objects.equals(viewerMetadata, that.viewerMetadata)
                            && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jid, state, metadata, viewerMetadata, unreadMessagesCount, timestamp);
    }
}
