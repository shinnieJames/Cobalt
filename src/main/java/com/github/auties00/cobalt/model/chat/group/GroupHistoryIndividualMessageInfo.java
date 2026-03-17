package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.message.MessageKey;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "GroupHistoryIndividualMessageInfo")
public final class GroupHistoryIndividualMessageInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey bundleMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean editedAfterReceivedAsHistory;


    GroupHistoryIndividualMessageInfo(MessageKey bundleMessageKey, Boolean editedAfterReceivedAsHistory) {
        this.bundleMessageKey = bundleMessageKey;
        this.editedAfterReceivedAsHistory = editedAfterReceivedAsHistory;
    }

    public Optional<MessageKey> bundleMessageKey() {
        return Optional.ofNullable(bundleMessageKey);
    }

    public boolean editedAfterReceivedAsHistory() {
        return editedAfterReceivedAsHistory != null && editedAfterReceivedAsHistory;
    }

    public void setBundleMessageKey(MessageKey bundleMessageKey) {
        this.bundleMessageKey = bundleMessageKey;
    }

    public void setEditedAfterReceivedAsHistory(Boolean editedAfterReceivedAsHistory) {
        this.editedAfterReceivedAsHistory = editedAfterReceivedAsHistory;
    }
}
