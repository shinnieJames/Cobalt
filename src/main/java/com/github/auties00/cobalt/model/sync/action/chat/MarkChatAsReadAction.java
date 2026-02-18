package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MarkChatAsReadAction")
public final class MarkChatAsReadAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean read;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    MarkChatAsReadAction(Boolean read, SyncActionMessageRange messageRange) {
        this.read = read;
        this.messageRange = messageRange;
    }

    public boolean read() {
        return read != null && read;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public MarkChatAsReadAction setRead(Boolean read) {
        this.read = read;
        return this;
    }

    public MarkChatAsReadAction setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
        return this;
    }
}
