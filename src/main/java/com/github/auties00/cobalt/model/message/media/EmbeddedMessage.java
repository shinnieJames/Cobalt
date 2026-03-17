package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.MessageContainer;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "EmbeddedMessage")
public final class EmbeddedMessage implements EmbeddedContentVariant {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String stanzaId;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer messageContainer;


    EmbeddedMessage(String stanzaId, MessageContainer messageContainer) {
        this.stanzaId = stanzaId;
        this.messageContainer = messageContainer;
    }

    public Optional<String> stanzaId() {
        return Optional.ofNullable(stanzaId);
    }

    public Optional<MessageContainer> message() {
        return Optional.ofNullable(messageContainer);
    }

    public void setStanzaId(String stanzaId) {
        this.stanzaId = stanzaId;
    }

    public void setMessage(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }
}
