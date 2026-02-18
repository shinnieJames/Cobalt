package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;

@ProtobufMessage(name = "SyncActionValue.AndroidUnsupportedActions")
public final class AndroidUnsupportedActions implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean allowed;


    AndroidUnsupportedActions(Boolean allowed) {
        this.allowed = allowed;
    }

    public boolean allowed() {
        return allowed != null && allowed;
    }

    public AndroidUnsupportedActions setAllowed(Boolean allowed) {
        this.allowed = allowed;
        return this;
    }
}
