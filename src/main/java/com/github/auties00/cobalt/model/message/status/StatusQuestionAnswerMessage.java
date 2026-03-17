package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.StatusQuestionAnswerMessage")
public final class StatusQuestionAnswerMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;


    StatusQuestionAnswerMessage(MessageKey key, String text) {
        this.key = key;
        this.text = text;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setText(String text) {
        this.text = text;
    }
}
