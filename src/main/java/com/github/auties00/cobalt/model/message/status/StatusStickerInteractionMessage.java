package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.StatusStickerInteractionMessage")
public final class StatusStickerInteractionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String stickerKey;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    StatusStickerType type;


    StatusStickerInteractionMessage(MessageKey key, String stickerKey, StatusStickerType type) {
        this.key = key;
        this.stickerKey = stickerKey;
        this.type = type;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<String> stickerKey() {
        return Optional.ofNullable(stickerKey);
    }

    public Optional<StatusStickerType> type() {
        return Optional.ofNullable(type);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setStickerKey(String stickerKey) {
        this.stickerKey = stickerKey;
    }

    public void setType(StatusStickerType type) {
        this.type = type;
    }

    @ProtobufEnum(name = "Message.StatusStickerInteractionMessage.StatusStickerType")
    public static enum StatusStickerType {
        UNKNOWN(0),
        REACTION(1);

        StatusStickerType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
