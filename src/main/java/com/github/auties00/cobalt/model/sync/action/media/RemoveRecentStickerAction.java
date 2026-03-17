package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.RemoveRecentStickerAction")
public final class RemoveRecentStickerAction implements SyncAction<RemoveRecentStickerActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "removeRecentSticker";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

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


    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastStickerSentTs;


    RemoveRecentStickerAction(Instant lastStickerSentTs) {
        this.lastStickerSentTs = lastStickerSentTs;
    }

    public Optional<Instant> lastStickerSentTs() {
        return Optional.ofNullable(lastStickerSentTs);
    }

    public void setLastStickerSentTs(Instant lastStickerSentTs) {
        this.lastStickerSentTs = lastStickerSentTs;
    }


}
