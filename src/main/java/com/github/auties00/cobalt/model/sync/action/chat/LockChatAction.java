package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.LockChatAction")
public final class LockChatAction implements SyncAction<LockChatActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "lock";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


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
