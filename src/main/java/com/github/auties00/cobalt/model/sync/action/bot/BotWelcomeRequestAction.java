package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.BotWelcomeRequestAction")
public final class BotWelcomeRequestAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isSent;


    BotWelcomeRequestAction(Boolean isSent) {
        this.isSent = isSent;
    }

    public boolean isSent() {
        return isSent != null && isSent;
    }

    public BotWelcomeRequestAction setSent(Boolean isSent) {
        this.isSent = isSent;
        return this;
    }
}
