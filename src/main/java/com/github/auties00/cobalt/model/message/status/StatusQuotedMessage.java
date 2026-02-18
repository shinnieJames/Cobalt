package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.StatusQuotedMessage")
public final class StatusQuotedMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    StatusQuotedMessageType type;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] thumbnail;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    MessageKey originalStatusId;


    StatusQuotedMessage(StatusQuotedMessageType type, String text, byte[] thumbnail, MessageKey originalStatusId) {
        this.type = type;
        this.text = text;
        this.thumbnail = thumbnail;
        this.originalStatusId = originalStatusId;
    }

    public Optional<StatusQuotedMessageType> type() {
        return Optional.ofNullable(type);
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public Optional<byte[]> thumbnail() {
        return Optional.ofNullable(thumbnail);
    }

    public Optional<MessageKey> originalStatusId() {
        return Optional.ofNullable(originalStatusId);
    }

    public StatusQuotedMessage setType(StatusQuotedMessageType type) {
        this.type = type;
        return this;
    }

    public StatusQuotedMessage setText(String text) {
        this.text = text;
        return this;
    }

    public StatusQuotedMessage setThumbnail(byte[] thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }

    public StatusQuotedMessage setOriginalStatusId(MessageKey originalStatusId) {
        this.originalStatusId = originalStatusId;
        return this;
    }

    @ProtobufEnum(name = "Message.StatusQuotedMessage.StatusQuotedMessageType")
    public static enum StatusQuotedMessageType {
        QUESTION_ANSWER(1);

        StatusQuotedMessageType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
