package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MarkChatAsReadAction")
public final class MarkChatAsReadAction implements SyncAction<MarkChatAsReadActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "markChatAsRead";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

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
    Boolean read;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    MarkChatAsReadAction(Boolean read, SyncActionMessageRange messageRange) {
        this.read = read;
        this.messageRange = messageRange;
    }

    public boolean read() {
        return read != null && read;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public void setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
    }


}
