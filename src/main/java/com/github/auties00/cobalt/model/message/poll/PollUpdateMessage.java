package com.github.auties00.cobalt.model.message.poll;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PollUpdateMessage")
public final class PollUpdateMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey pollCreationMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    PollEncValue vote;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    PollUpdateMessageMetadata metadata;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant senderTimestampMs;


    PollUpdateMessage(MessageKey pollCreationMessageKey, PollEncValue vote, PollUpdateMessageMetadata metadata, Instant senderTimestampMs) {
        this.pollCreationMessageKey = pollCreationMessageKey;
        this.vote = vote;
        this.metadata = metadata;
        this.senderTimestampMs = senderTimestampMs;
    }

    public Optional<MessageKey> pollCreationMessageKey() {
        return Optional.ofNullable(pollCreationMessageKey);
    }

    public Optional<PollEncValue> vote() {
        return Optional.ofNullable(vote);
    }

    public Optional<PollUpdateMessageMetadata> metadata() {
        return Optional.ofNullable(metadata);
    }

    public Optional<Instant> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    public PollUpdateMessage setPollCreationMessageKey(MessageKey pollCreationMessageKey) {
        this.pollCreationMessageKey = pollCreationMessageKey;
        return this;
    }

    public PollUpdateMessage setVote(PollEncValue vote) {
        this.vote = vote;
        return this;
    }

    public PollUpdateMessage setMetadata(PollUpdateMessageMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public PollUpdateMessage setSenderTimestampMs(Instant senderTimestampMs) {
        this.senderTimestampMs = senderTimestampMs;
        return this;
    }
}
