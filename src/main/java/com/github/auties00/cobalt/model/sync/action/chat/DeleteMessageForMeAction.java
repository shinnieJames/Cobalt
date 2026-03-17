package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.DeleteMessageForMeAction")
public final class DeleteMessageForMeAction implements SyncAction<DeleteMessageForMeActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "deleteMessageForMe";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 3;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

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
    Boolean deleteMedia;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant messageTimestamp;


    DeleteMessageForMeAction(Boolean deleteMedia, Instant messageTimestamp) {
        this.deleteMedia = deleteMedia;
        this.messageTimestamp = messageTimestamp;
    }

    public boolean deleteMedia() {
        return deleteMedia != null && deleteMedia;
    }

    public Optional<Instant> messageTimestamp() {
        return Optional.ofNullable(messageTimestamp);
    }

    public void setDeleteMedia(Boolean deleteMedia) {
        this.deleteMedia = deleteMedia;
    }

    public void setMessageTimestamp(Instant messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
    }


}
