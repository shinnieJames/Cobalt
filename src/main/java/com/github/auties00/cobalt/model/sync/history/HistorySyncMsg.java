package com.github.auties00.cobalt.model.sync.history;

import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "HistorySyncMsg")
public final class HistorySyncMsg {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    ChatMessageInfo message;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Long msgOrderId;

    HistorySyncMsg(ChatMessageInfo message, Long msgOrderId) {
        this.message = message;
        this.msgOrderId = msgOrderId;
    }

    public Optional<ChatMessageInfo> message() {
        return Optional.ofNullable(message);
    }

    public OptionalLong msgOrderId() {
        return msgOrderId == null ? OptionalLong.empty() : OptionalLong.of(msgOrderId);
    }

    public void setMessage(ChatMessageInfo message) {
        this.message = message;
    }

    public void setMsgOrderId(Long msgOrderId) {
        this.msgOrderId = msgOrderId;
    }
}