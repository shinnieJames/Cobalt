package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ScheduledCallEditMessage")
public final class ScheduledCallEditMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    EditType editType;


    ScheduledCallEditMessage(MessageKey key, EditType editType) {
        this.key = key;
        this.editType = editType;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<EditType> editType() {
        return Optional.ofNullable(editType);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setEditType(EditType editType) {
        this.editType = editType;
    }

    @ProtobufEnum(name = "Message.ScheduledCallEditMessage.EditType")
    public static enum EditType {
        UNKNOWN(0),
        CANCEL(1);

        EditType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
