package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.NuxAction")
public final class NuxAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean acknowledged;


    NuxAction(Boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public boolean acknowledged() {
        return acknowledged != null && acknowledged;
    }

    public NuxAction setAcknowledged(Boolean acknowledged) {
        this.acknowledged = acknowledged;
        return this;
    }
}
