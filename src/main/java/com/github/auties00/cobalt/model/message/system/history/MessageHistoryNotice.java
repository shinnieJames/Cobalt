package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.MessageHistoryNotice")
public final class MessageHistoryNotice implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageHistoryMetadata messageHistoryMetadata;


    MessageHistoryNotice(ContextInfo contextInfo, MessageHistoryMetadata messageHistoryMetadata) {
        this.contextInfo = contextInfo;
        this.messageHistoryMetadata = messageHistoryMetadata;
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<MessageHistoryMetadata> messageHistoryMetadata() {
        return Optional.ofNullable(messageHistoryMetadata);
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setMessageHistoryMetadata(MessageHistoryMetadata messageHistoryMetadata) {
        this.messageHistoryMetadata = messageHistoryMetadata;
    }
}
