package com.github.auties00.cobalt.model.message.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "EmbeddedContent")
public final class EmbeddedContent {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    EmbeddedMessage embeddedMessage;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    EmbeddedMusic embeddedMusic;


    EmbeddedContent(EmbeddedMessage embeddedMessage, EmbeddedMusic embeddedMusic) {
        this.embeddedMessage = embeddedMessage;
        this.embeddedMusic = embeddedMusic;
    }

    public Optional<? extends EmbeddedContentVariant> content() {
        if (embeddedMessage != null) return Optional.of(embeddedMessage);
        if (embeddedMusic != null) return Optional.of(embeddedMusic);
        return Optional.empty();
    }

    public void setEmbeddedMessage(EmbeddedMessage embeddedMessage) {
        this.embeddedMessage = embeddedMessage;
    }

    public void setEmbeddedMusic(EmbeddedMusic embeddedMusic) {
        this.embeddedMusic = embeddedMusic;
    }
}
