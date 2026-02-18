package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.AiThreadRenameAction")
public final class AiThreadRenameAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String newTitle;


    AiThreadRenameAction(String newTitle) {
        this.newTitle = newTitle;
    }

    public Optional<String> newTitle() {
        return Optional.ofNullable(newTitle);
    }

    public AiThreadRenameAction setNewTitle(String newTitle) {
        this.newTitle = newTitle;
        return this;
    }
}
