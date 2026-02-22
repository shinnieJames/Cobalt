package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.PinAction")
public final class PinAction implements SyncAction<PinActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "pin_v1";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 5;

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
