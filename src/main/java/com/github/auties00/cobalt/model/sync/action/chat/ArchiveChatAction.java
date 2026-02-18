package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.ArchiveChatAction")
public final class ArchiveChatAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean archived;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    ArchiveChatAction(Boolean archived, SyncActionMessageRange messageRange) {
        this.archived = archived;
        this.messageRange = messageRange;
    }

    public boolean archived() {
        return archived != null && archived;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public ArchiveChatAction setArchived(Boolean archived) {
        this.archived = archived;
        return this;
    }

    public ArchiveChatAction setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
        return this;
    }
}
