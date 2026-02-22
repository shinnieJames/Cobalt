package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.DeleteChatAction")
public final class DeleteChatAction implements SyncAction<DeleteChatActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "deleteChat";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 6;

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


    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    DeleteChatAction(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public DeleteChatAction setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
        return this;
    }


}
