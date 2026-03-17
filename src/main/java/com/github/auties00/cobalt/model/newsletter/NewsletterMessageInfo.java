package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A message within a newsletter, containing the message content, metadata
 * such as view counts, reactions, poll votes, forwards, and administrative
 * information about the sender.
 *
 * <p>Newsletter messages are not end-to-end encrypted. The message content
 * is received as plaintext protobuf bytes using the standard
 * {@code Message} protobuf specification.
 */
@ProtobufMessage
public final class NewsletterMessageInfo implements MessageInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    int serverId;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long views;

    @ProtobufProperty(index = 5, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    Map<String, NewsletterReaction> reactions;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    MessageContainer message;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    MessageStatus status;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    boolean starred;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    List<MessageReceipt> receipts;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT64)
    Long forwardsCount;

    @ProtobufProperty(index = 11, type = ProtobufType.UINT64)
    Long questionResponsesCount;

    @ProtobufProperty(index = 12, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant lastUpdateFromServerTimestamp;

    @ProtobufProperty(index = 13, type = ProtobufType.UINT64)
    Long latestEditSenderTimestampMs;

    @ProtobufProperty(index = 14, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant originalTimestamp;

    @ProtobufProperty(index = 15, type = ProtobufType.BOOL)
    boolean wamoSub;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    NewsletterAdminProfile adminProfile;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    List<NewsletterPollVote> pollVotes;

    String mediaHandle;

    /**
     * Constructs a new {@code NewsletterMessageInfo} with the specified fields.
     *
     * @param key                            the message key, must not be {@code null}
     * @param serverId                       the server-assigned message identifier
     * @param timestamp                      the message timestamp, may be {@code null}
     * @param views                          the view count, may be {@code null}
     * @param reactions                      the reaction map keyed by emoji content, may be {@code null}
     * @param message                        the message content container, may be {@code null}
     * @param status                         the message delivery status, may be {@code null}
     * @param starred                        whether the message is starred
     * @param receipts                       the message receipts, may be {@code null}
     * @param forwardsCount                  the number of times the message was forwarded, may be {@code null}
     * @param questionResponsesCount         the number of question responses, may be {@code null}
     * @param lastUpdateFromServerTimestamp  the timestamp of the last server update, may be {@code null}
     * @param latestEditSenderTimestampMs    the timestamp in milliseconds of the latest edit, may be {@code null}
     * @param originalTimestamp              the original timestamp before edits, may be {@code null}
     * @param wamoSub                        whether this is a WAMO subscription message
     * @param adminProfile                   the admin profile who sent the message, may be {@code null}
     * @param pollVotes                      the poll vote data, may be {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    NewsletterMessageInfo(MessageKey key, int serverId, Instant timestamp, Long views, Map<String, NewsletterReaction> reactions, MessageContainer message, MessageStatus status, boolean starred, List<MessageReceipt> receipts, Long forwardsCount, Long questionResponsesCount, Instant lastUpdateFromServerTimestamp, Long latestEditSenderTimestampMs, Instant originalTimestamp, boolean wamoSub, NewsletterAdminProfile adminProfile, List<NewsletterPollVote> pollVotes) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.serverId = serverId;
        this.timestamp = timestamp;
        this.views = views;
        this.reactions = reactions;
        this.message = message;
        this.status = status;
        this.starred = starred;
        this.receipts = receipts;
        this.forwardsCount = forwardsCount;
        this.questionResponsesCount = questionResponsesCount;
        this.lastUpdateFromServerTimestamp = lastUpdateFromServerTimestamp;
        this.latestEditSenderTimestampMs = latestEditSenderTimestampMs;
        this.originalTimestamp = originalTimestamp;
        this.wamoSub = wamoSub;
        this.adminProfile = adminProfile;
        this.pollVotes = Objects.requireNonNullElseGet(pollVotes, ArrayList::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageKey key() {
        return key;
    }

    /**
     * Sets the message key.
     *
     * @param key the message key, must not be {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void setKey(MessageKey key) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }

    /**
     * Returns the server-assigned message identifier.
     *
     * @return the server id
     */
    public int serverId() {
        return serverId;
    }

    /**
     * Sets the server-assigned message identifier.
     *
     * @param serverId the server id
     */
    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Sets the message timestamp.
     *
     * @param timestamp the timestamp
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the view count, if available.
     *
     * @return an {@link OptionalLong} containing the view count,
     *         or empty if not set
     */
    public OptionalLong views() {
        return views == null ? OptionalLong.empty() : OptionalLong.of(views);
    }

    /**
     * Sets the view count.
     *
     * @param views the view count
     */
    public void setViews(Long views) {
        this.views = views;
    }

    /**
     * Sets the reactions from a collection, merging duplicates.
     *
     * @param reactions the collection of reactions, may be {@code null}
     */
    public void setReactions(Collection<NewsletterReaction> reactions) {
        if (reactions == null) {
            this.reactions = new HashMap<>();
        } else {
            this.reactions = reactions.stream().collect(Collectors.toMap(reaction -> reaction.content, Function.identity(), (firstReaction, secondReaction) -> {
                var firstReactionContent = firstReaction.content;
                var firstReactionCount = firstReaction.count;
                var firstReactionFromMe = firstReaction.fromMe;

                var secondReactionContent = secondReaction.content;
                var secondReactionCount = secondReaction.count;
                var secondReactionFromMe = secondReaction.fromMe;

                assert Objects.equals(firstReactionContent, secondReactionContent);
                assert firstReactionFromMe == secondReactionFromMe;

                if (firstReactionCount == secondReactionCount) {
                    return new NewsletterReaction(firstReactionContent, firstReactionCount + 1, firstReactionFromMe);
                } else {
                    return new NewsletterReaction(firstReactionContent, Math.max(firstReactionCount, secondReactionCount), firstReactionFromMe);
                }
            }));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageContainer message() {
        return message != null ? message : MessageContainer.empty();
    }

    /**
     * Sets the message content container.
     *
     * @param message the message container
     */
    public void setMessage(MessageContainer message) {
        this.message = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<MessageStatus> status() {
        return Optional.ofNullable(status);
    }

    /**
     * Sets the message delivery status.
     *
     * @param status the delivery status
     */
    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    /**
     * Sets the message receipts.
     *
     * @param receipts the receipt
     */
    public void setReceipts(List<MessageReceipt> receipts) {
        this.receipts = receipts;
    }

    /**
     * Returns the media handle, if available.
     *
     * @return the media handle, may be {@code null}
     */
    public Optional<String> mediaHandle() {
        return Optional.ofNullable(mediaHandle);
    }

    /**
     * Sets the media handle.
     *
     * @param mediaHandle the media handle
     */
    public void setMediaHandle(String mediaHandle) {
        this.mediaHandle = mediaHandle;
    }

    /**
     * Returns an unmodifiable view of the reactions.
     *
     * @return the reactions collection, never {@code null}
     */
    public Collection<NewsletterReaction> reactions() {
        return Collections.unmodifiableCollection(reactions.values());
    }

    /**
     * Finds a reaction by its emoji content.
     *
     * @param value the emoji content to search for
     * @return an {@link Optional} containing the matching reaction,
     *         or empty if not found
     */
    public Optional<NewsletterReaction> findReaction(String value) {
        return Optional.ofNullable(reactions.get(value));
    }

    /**
     * Adds or replaces a reaction.
     *
     * @param reaction the reaction to add
     * @return an {@link Optional} containing the previously associated reaction,
     *         or empty if there was none
     */
    public Optional<NewsletterReaction> addReaction(NewsletterReaction reaction) {
        return Optional.ofNullable(reactions.put(reaction.content(), reaction));
    }

    /**
     * Removes a reaction by its emoji code.
     *
     * @param code the emoji code to remove
     * @return an {@link Optional} containing the removed reaction,
     *         or empty if not found
     */
    public Optional<NewsletterReaction> removeReaction(String code) {
        return Optional.ofNullable(reactions.remove(code));
    }

    /**
     * Increments the count for a reaction, creating it if it does not exist.
     *
     * @param code   the emoji code
     * @param fromMe whether the reaction is from the current user
     */
    public void incrementReaction(String code, boolean fromMe) {
        findReaction(code).ifPresentOrElse(reaction -> {
            reaction.setCount(reaction.count() + 1);
            reaction.setFromMe(fromMe);
        }, () -> {
            var reaction = new NewsletterReaction(code, 1, fromMe);
            addReaction(reaction);
        });
    }

    /**
     * Decrements the count for a reaction, removing it if the count
     * reaches zero.
     *
     * @param code the emoji code
     */
    public void decrementReaction(String code) {
        findReaction(code).ifPresent(reaction -> {
            if (reaction.count() <= 1) {
                removeReaction(reaction.content());
                return;
            }

            reaction.setCount(reaction.count() - 1);
            reaction.setFromMe(false);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean starred() {
        return starred;
    }

    /**
     * Sets whether the message is starred.
     *
     * @param starred {@code true} to star the message
     */
    public void setStarred(boolean starred) {
        this.starred = starred;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MessageReceipt> receipts() {
        return receipts == null ? List.of() : Collections.unmodifiableList(receipts);
    }

    /**
     * Returns the number of times the message was forwarded, if available.
     *
     * @return an {@link OptionalLong} containing the forwards count,
     *         or empty if not set
     */
    public OptionalLong forwardsCount() {
        return forwardsCount == null ? OptionalLong.empty() : OptionalLong.of(forwardsCount);
    }

    /**
     * Sets the forwards count.
     *
     * @param forwardsCount the forwards count
     */
    public void setForwardsCount(Long forwardsCount) {
        this.forwardsCount = forwardsCount;
    }

    /**
     * Returns the number of question responses, if available.
     *
     * @return an {@link OptionalLong} containing the question responses count,
     *         or empty if not set
     */
    public OptionalLong questionResponsesCount() {
        return questionResponsesCount == null ? OptionalLong.empty() : OptionalLong.of(questionResponsesCount);
    }

    /**
     * Sets the question responses count.
     *
     * @param questionResponsesCount the question responses count
     */
    public void setQuestionResponsesCount(Long questionResponsesCount) {
        this.questionResponsesCount = questionResponsesCount;
    }

    /**
     * Returns the timestamp of the last server update, if available.
     *
     * @return an {@link Optional} containing the last update timestamp,
     *         or empty if not set
     */
    public Optional<Instant> lastUpdateFromServerTimestamp() {
        return Optional.ofNullable(lastUpdateFromServerTimestamp);
    }

    /**
     * Sets the timestamp of the last server update.
     *
     * @param lastUpdateFromServerTimestamp the last update timestamp
     */
    public void setLastUpdateFromServerTimestamp(Instant lastUpdateFromServerTimestamp) {
        this.lastUpdateFromServerTimestamp = lastUpdateFromServerTimestamp;
    }

    /**
     * Returns the timestamp in milliseconds of the latest edit by the
     * sender, if available.
     *
     * @return an {@link OptionalLong} containing the edit timestamp in
     *         milliseconds, or empty if not set
     */
    public OptionalLong latestEditSenderTimestampMs() {
        return latestEditSenderTimestampMs == null ? OptionalLong.empty() : OptionalLong.of(latestEditSenderTimestampMs);
    }

    /**
     * Sets the latest edit sender timestamp in milliseconds.
     *
     * @param latestEditSenderTimestampMs the edit timestamp in milliseconds
     */
    public void setLatestEditSenderTimestampMs(Long latestEditSenderTimestampMs) {
        this.latestEditSenderTimestampMs = latestEditSenderTimestampMs;
    }

    /**
     * Returns the original timestamp of the message before any edits,
     * if available.
     *
     * @return an {@link Optional} containing the original timestamp,
     *         or empty if not set or the message was not edited
     */
    public Optional<Instant> originalTimestamp() {
        return Optional.ofNullable(originalTimestamp);
    }

    /**
     * Sets the original timestamp before edits.
     *
     * @param originalTimestamp the original timestamp
     */
    public void setOriginalTimestamp(Instant originalTimestamp) {
        this.originalTimestamp = originalTimestamp;
    }

    /**
     * Returns whether this is a WAMO subscription message.
     *
     * @return {@code true} if this is a WAMO sub message
     */
    public boolean wamoSub() {
        return wamoSub;
    }

    /**
     * Sets whether this is a WAMO subscription message.
     *
     * @param wamoSub {@code true} if this is a WAMO sub message
     */
    public void setWamoSub(boolean wamoSub) {
        this.wamoSub = wamoSub;
    }

    /**
     * Returns the admin profile of the message sender, if available.
     *
     * @return an {@link Optional} containing the admin profile,
     *         or empty if not set
     */
    public Optional<NewsletterAdminProfile> adminProfile() {
        return Optional.ofNullable(adminProfile);
    }

    /**
     * Sets the admin profile of the message sender.
     *
     * @param adminProfile the admin profile
     */
    public void setAdminProfile(NewsletterAdminProfile adminProfile) {
        this.adminProfile = adminProfile;
    }

    /**
     * Returns the poll vote data for this message, if it is a poll.
     *
     * @return an unmodifiable list of poll votes, never {@code null}
     */
    public List<NewsletterPollVote> pollVotes() {
        return Collections.unmodifiableList(pollVotes);
    }

    /**
     * Sets the poll vote data.
     *
     * @param pollVotes the list of poll votes
     */
    public void setPollVotes(List<NewsletterPollVote> pollVotes) {
        this.pollVotes = Objects.requireNonNullElseGet(pollVotes, ArrayList::new);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NewsletterMessageInfo that
               && serverId == that.serverId
               && starred == that.starred
               && wamoSub == that.wamoSub
               && Objects.equals(key, that.key)
               && Objects.equals(timestamp, that.timestamp)
               && Objects.equals(views, that.views)
               && Objects.equals(reactions, that.reactions)
               && Objects.equals(message, that.message)
               && status == that.status
               && Objects.equals(forwardsCount, that.forwardsCount)
               && Objects.equals(questionResponsesCount, that.questionResponsesCount)
               && Objects.equals(lastUpdateFromServerTimestamp, that.lastUpdateFromServerTimestamp)
               && Objects.equals(latestEditSenderTimestampMs, that.latestEditSenderTimestampMs)
               && Objects.equals(originalTimestamp, that.originalTimestamp)
               && Objects.equals(adminProfile, that.adminProfile)
               && Objects.equals(pollVotes, that.pollVotes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, serverId, timestamp, views, reactions, message, status, starred, forwardsCount, questionResponsesCount, lastUpdateFromServerTimestamp, latestEditSenderTimestampMs, originalTimestamp, wamoSub, adminProfile, pollVotes);
    }
}
