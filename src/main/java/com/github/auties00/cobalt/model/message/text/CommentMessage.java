package com.github.auties00.cobalt.model.message.text;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.CommentMessage")
public final class CommentMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageContainer messageContainer;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;


    CommentMessage(MessageContainer messageContainer, MessageKey targetMessageKey) {
        this.messageContainer = messageContainer;
        this.targetMessageKey = targetMessageKey;
    }

    public Optional<MessageContainer> message() {
        return Optional.ofNullable(messageContainer);
    }

    public Optional<MessageKey> targetMessageKey() {
        return Optional.ofNullable(targetMessageKey);
    }

    public void setMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }

    public void setTargetMessageKey(MessageKey targetMessageKey) {
        this.targetMessageKey = targetMessageKey;
    }
}
