package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.ClearChatAction")
public final class ClearChatAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    ClearChatAction(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public ClearChatAction setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
        return this;
    }
}
