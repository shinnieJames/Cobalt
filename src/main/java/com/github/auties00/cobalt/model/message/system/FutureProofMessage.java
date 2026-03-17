package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.FutureProofMessage")
public final class FutureProofMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageContainer messageContainer;


    FutureProofMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }

    public Optional<MessageContainer> message() {
        return Optional.ofNullable(messageContainer);
    }

    public void setMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }
}
