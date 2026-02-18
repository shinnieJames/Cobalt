package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.LockChatAction")
public final class LockChatAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean locked;


    LockChatAction(Boolean locked) {
        this.locked = locked;
    }

    public boolean locked() {
        return locked != null && locked;
    }

    public LockChatAction setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }
}
