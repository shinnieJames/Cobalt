package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.PinAction")
public final class PinAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean pinned;


    PinAction(Boolean pinned) {
        this.pinned = pinned;
    }

    public boolean pinned() {
        return pinned != null && pinned;
    }

    public PinAction setPinned(Boolean pinned) {
        this.pinned = pinned;
        return this;
    }
}
