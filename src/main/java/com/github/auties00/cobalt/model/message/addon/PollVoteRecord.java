package com.github.auties00.cobalt.model.message.addon;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.poll.PollVoteMessage;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "PollUpdate")
public final class PollVoteRecord {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey pollUpdateMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    PollVoteMessage vote;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderTimestampMs;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant serverTimestampMs;

    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    Boolean unread;


    PollVoteRecord(MessageKey pollUpdateMessageKey, PollVoteMessage vote, Instant senderTimestampMs, Instant serverTimestampMs, Boolean unread) {
        this.pollUpdateMessageKey = pollUpdateMessageKey;
        this.vote = vote;
        this.senderTimestampMs = senderTimestampMs;
        this.serverTimestampMs = serverTimestampMs;
        this.unread = unread;
    }

    public Optional<MessageKey> pollUpdateMessageKey() {
        return Optional.ofNullable(pollUpdateMessageKey);
    }

    public Optional<PollVoteMessage> vote() {
        return Optional.ofNullable(vote);
    }

    public Optional<Instant> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    public Optional<Instant> serverTimestampMs() {
        return Optional.ofNullable(serverTimestampMs);
    }

    public boolean unread() {
        return unread != null && unread;
    }

    public void setPollUpdateMessageKey(MessageKey pollUpdateMessageKey) {
        this.pollUpdateMessageKey = pollUpdateMessageKey;
    }

    public void setVote(PollVoteMessage vote) {
        this.vote = vote;
    }

    public void setSenderTimestampMs(Instant senderTimestampMs) {
        this.senderTimestampMs = senderTimestampMs;
    }

    public void setServerTimestampMs(Instant serverTimestampMs) {
        this.serverTimestampMs = serverTimestampMs;
    }

    public void setUnread(Boolean unread) {
        this.unread = unread;
    }
}
