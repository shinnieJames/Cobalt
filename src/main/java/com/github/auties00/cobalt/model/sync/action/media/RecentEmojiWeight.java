package com.github.auties00.cobalt.model.sync.action.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalDouble;

@ProtobufMessage(name = "RecentEmojiWeight")
public final class RecentEmojiWeight {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String emoji;

    @ProtobufProperty(index = 2, type = ProtobufType.FLOAT)
    Float weight;


    RecentEmojiWeight(String emoji, Float weight) {
        this.emoji = emoji;
        this.weight = weight;
    }

    public Optional<String> emoji() {
        return Optional.ofNullable(emoji);
    }

    public OptionalDouble weight() {
        return weight == null ? OptionalDouble.empty() : OptionalDouble.of(weight);
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }
}
