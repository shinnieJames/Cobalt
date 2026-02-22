package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.BotWelcomeRequestAction")
public final class BotWelcomeRequestAction implements SyncAction<BotWelcomeRequestActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "bot_welcome_request";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 2;

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
