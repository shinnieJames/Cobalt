package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.message.MessageContainer;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "StatusMentionMessage")
public final class StatusMentionMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageContainer quotedStatus;


    StatusMentionMessage(MessageContainer quotedStatus) {
        this.quotedStatus = quotedStatus;
    }

    public Optional<MessageContainer> quotedStatus() {
        return Optional.ofNullable(quotedStatus);
    }

    public void setQuotedStatus(MessageContainer quotedStatus) {
        this.quotedStatus = quotedStatus;
    }
}
