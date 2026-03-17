package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.StatusNotificationMessage")
public final class StatusNotificationMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey responseMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageKey originalMessageKey;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    StatusNotificationType type;


    StatusNotificationMessage(MessageKey responseMessageKey, MessageKey originalMessageKey, StatusNotificationType type) {
        this.responseMessageKey = responseMessageKey;
        this.originalMessageKey = originalMessageKey;
        this.type = type;
    }

    public Optional<MessageKey> responseMessageKey() {
        return Optional.ofNullable(responseMessageKey);
    }

    public Optional<MessageKey> originalMessageKey() {
        return Optional.ofNullable(originalMessageKey);
    }

    public Optional<StatusNotificationType> type() {
        return Optional.ofNullable(type);
    }

    public void setResponseMessageKey(MessageKey responseMessageKey) {
        this.responseMessageKey = responseMessageKey;
    }

    public void setOriginalMessageKey(MessageKey originalMessageKey) {
        this.originalMessageKey = originalMessageKey;
    }

    public void setType(StatusNotificationType type) {
        this.type = type;
    }

    @ProtobufEnum(name = "Message.StatusNotificationMessage.StatusNotificationType")
    public static enum StatusNotificationType {
        UNKNOWN(0),
        STATUS_ADD_YOURS(1),
        STATUS_RESHARE(2),
        STATUS_QUESTION_ANSWER_RESHARE(3);

        StatusNotificationType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
