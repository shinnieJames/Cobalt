package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.ArchiveChatAction")
public final class ArchiveChatAction implements SyncAction<ArchiveChatActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "archive";

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
    Boolean archived;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionMessageRange messageRange;


    ArchiveChatAction(Boolean archived, SyncActionMessageRange messageRange) {
        this.archived = archived;
        this.messageRange = messageRange;
    }

    public boolean archived() {
        return archived != null && archived;
    }

    public Optional<SyncActionMessageRange> messageRange() {
        return Optional.ofNullable(messageRange);
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public void setMessageRange(SyncActionMessageRange messageRange) {
        this.messageRange = messageRange;
    }


}
