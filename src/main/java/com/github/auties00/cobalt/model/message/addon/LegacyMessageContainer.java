package com.github.auties00.cobalt.model.message.addon;

import com.github.auties00.cobalt.model.message.event.EventResponseMessage;
import com.github.auties00.cobalt.model.message.poll.PollVoteMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "LegacyMessage")
public final class LegacyMessageContainer {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    EventResponseMessage eventResponseMessage;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    PollVoteMessage pollVote;


    LegacyMessageContainer(EventResponseMessage eventResponseMessage, PollVoteMessage pollVote) {
        this.eventResponseMessage = eventResponseMessage;
        this.pollVote = pollVote;
    }

    public Optional<EventResponseMessage> eventResponseMessage() {
        return Optional.ofNullable(eventResponseMessage);
    }

    public Optional<PollVoteMessage> pollVote() {
        return Optional.ofNullable(pollVote);
    }

    public void setEventResponseMessage(EventResponseMessage eventResponseMessage) {
        this.eventResponseMessage = eventResponseMessage;
    }

    public void setPollVote(PollVoteMessage pollVote) {
        this.pollVote = pollVote;
    }
}
