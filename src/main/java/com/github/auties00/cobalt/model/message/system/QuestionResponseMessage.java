package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.QuestionResponseMessage")
public final class QuestionResponseMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;


    QuestionResponseMessage(MessageKey key, String text) {
        this.key = key;
        this.text = text;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public QuestionResponseMessage setKey(MessageKey key) {
        this.key = key;
        return this;
    }

    public QuestionResponseMessage setText(String text) {
        this.text = text;
        return this;
    }
}
