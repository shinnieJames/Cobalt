package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PlaceholderMessage")
public final class PlaceholderMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    PlaceholderType type;


    PlaceholderMessage(PlaceholderType type) {
        this.type = type;
    }

    public Optional<PlaceholderType> type() {
        return Optional.ofNullable(type);
    }

    public void setType(PlaceholderType type) {
        this.type = type;
    }

    @ProtobufEnum(name = "Message.PlaceholderMessage.PlaceholderType")
    public static enum PlaceholderType {
        MASK_LINKED_DEVICES(0);

        PlaceholderType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
