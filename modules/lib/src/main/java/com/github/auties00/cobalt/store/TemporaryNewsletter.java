package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.collections.ConcurrentLinkedHashMap;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The in-memory {@link Newsletter} subtype used as the value type of
 * {@link TemporaryStore#newsletters}.
 *
 * @apiNote
 * Cobalt embedders never construct this directly; {@link TemporaryStore#addNewNewsletter(Jid)}
 * returns one as a {@link Newsletter}. Mirrors {@link TemporaryChat} but the underlying map is
 * keyed by the {@link NewsletterMessageInfo} message-key id rather than the numeric server id,
 * because the abstract API hands messages around as id strings.
 *
 * @implNote
 * This implementation keys by the message-key id string and preserves insertion order via the
 * sequenced-map contract, so {@link #oldestMessage()} and {@link #newestMessage()} reduce to
 * sequenced-map first/last lookups.
 */
final class TemporaryNewsletter extends Newsletter {
    /**
     * The in-memory message store, keyed by message-key id and preserving insertion order.
     */
    private final ConcurrentLinkedHashMap<String, NewsletterMessageInfo> messages;

    /**
     * Constructs an in-memory newsletter keyed under {@code jid} with every other metadata
     * field defaulted.
     *
     * @apiNote
     * Package-private; called by {@link TemporaryStore#addNewNewsletter(Jid)}. The defaulted
     * metadata scalars are populated on demand as the receiver path observes the corresponding
     * stanzas.
     *
     * @param jid the newsletter JID
     */
    TemporaryNewsletter(Jid jid) {
        super(jid, null, null, null, 0, null);
        this.messages = new ConcurrentLinkedHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation silently drops messages with no key id because the underlying map
     * requires a non-null key; production code paths always populate the id before insertion.
     */
    @Override
    public void addMessage(NewsletterMessageInfo info) {
        Objects.requireNonNull(info, "info cannot be null");
        info.key().id().ifPresent(id -> messages.put(id, info));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation accepts the raw message-key id and returns {@code false} on a
     * {@code null} input; unlike the persistent variant, no server-id parsing is required.
     */
    @Override
    public boolean removeMessage(String messageId) {
        return messageId != null && messages.remove(messageId) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to {@link ConcurrentLinkedHashMap#clear()}.
     */
    @Override
    public void removeMessages() {
        messages.clear();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation streams the sequenced values directly; the stream owns no native
     * resources.
     */
    @Override
    public Stream<NewsletterMessageInfo> messages() {
        return messages.sequencedValues().stream();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link ConcurrentLinkedHashMap#size()} which is {@code O(1)}.
     */
    @Override
    public int messageCount() {
        return messages.size();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} on a {@code null} id; the underlying
     * map lookup is a direct hash and does not iterate the value set.
     */
    @Override
    public Optional<NewsletterMessageInfo> getMessageById(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        var byId = messages.get(messageId);
        return Optional.ofNullable(byId);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the first entry of the sequenced map, which is the earliest
     * inserted message still present.
     */
    @Override
    public Optional<NewsletterMessageInfo> oldestMessage() {
        var entry = messages.firstEntry();
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the last entry of the sequenced map, which is the most
     * recently inserted message.
     */
    @Override
    public Optional<NewsletterMessageInfo> newestMessage() {
        var entry = messages.lastEntry();
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }
}
