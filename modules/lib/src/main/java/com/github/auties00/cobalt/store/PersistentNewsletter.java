package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMetadata;
import com.github.auties00.cobalt.model.newsletter.NewsletterState;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerMetadata;
import it.auties.protobuf.annotation.ProtobufMessage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Metadata-only {@link Newsletter}
 * subclass used as the value type of the
 * {@link PersistentStore#newsletters} map. Mirrors {@link PersistentChat} but
 * delegates to the newsletter dbi keyed by {@code serverId}.
 */
@ProtobufMessage
final class PersistentNewsletter extends Newsletter {
    /**
     * The LMDB facade providing the message storage.
     */
    private volatile PersistentMessageStore messageStore;

    /**
     * Cached message count.
     */
    private final AtomicInteger messageCount;

    /**
     * Constructs a new metadata-only newsletter; invoked by the
     * generated protobuf builder and deserializer.
     */
    PersistentNewsletter(Jid jid, NewsletterState state, NewsletterMetadata metadata, NewsletterViewerMetadata viewerMetadata, int unreadMessagesCount, Instant timestamp) {
        super(jid, state, metadata, viewerMetadata, unreadMessagesCount, timestamp);
        this.messageCount = new AtomicInteger();
    }

    /**
     * Wires the LMDB facade into this newsletter and seeds the
     * cached message count.
     *
     * @param messageStore the LMDB facade owned by the parent store
     */
    void attach(PersistentMessageStore messageStore) {
        this.messageStore = messageStore;
        this.messageCount.set(messageStore.countNewsletterMessages(jid()));
    }

    @Override
    public void addMessage(NewsletterMessageInfo info) {
        Objects.requireNonNull(info, "info cannot be null");
        messageStore.putNewsletterMessage(jid(), info);
        messageCount.incrementAndGet();
    }

    @Override
    public boolean removeMessage(String messageId) {
        if (messageId == null) {
            return false;
        }
        // Newsletter messages are keyed by serverId; the message-id
        // string the caller passes maps to a serverId via parse.
        try {
            var serverId = Integer.parseInt(messageId);
            var removed = messageStore.removeNewsletterMessageByServerId(jid(), serverId);
            if (removed) {
                messageCount.decrementAndGet();
            }
            return removed;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    @Override
    public void removeMessages() {
        messageStore.removeNewsletterMessages(jid());
        messageCount.set(0);
    }

    @Override
    public Stream<NewsletterMessageInfo> messages() {
        return messageStore.streamNewsletterMessages(jid());
    }

    @Override
    public int messageCount() {
        return messageCount.get();
    }

    @Override
    public Optional<NewsletterMessageInfo> getMessageById(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        try {
            return messageStore.getNewsletterMessageByServerId(jid(), Integer.parseInt(messageId));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NewsletterMessageInfo> oldestMessage() {
        return messageStore.oldestNewsletterMessage(jid());
    }

    @Override
    public Optional<NewsletterMessageInfo> newestMessage() {
        return messageStore.newestNewsletterMessage(jid());
    }
}
