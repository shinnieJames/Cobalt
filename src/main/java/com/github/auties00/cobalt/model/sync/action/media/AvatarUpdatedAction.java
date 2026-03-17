package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.AvatarUpdatedAction")
public final class AvatarUpdatedAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "avatar_updated_action";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

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


    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    AvatarEventType eventType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<StickerAction> recentAvatarStickers;


    AvatarUpdatedAction(AvatarEventType eventType, List<StickerAction> recentAvatarStickers) {
        this.eventType = eventType;
        this.recentAvatarStickers = recentAvatarStickers;
    }

    public Optional<AvatarEventType> eventType() {
        return Optional.ofNullable(eventType);
    }

    public List<StickerAction> recentAvatarStickers() {
        return recentAvatarStickers == null ? List.of() : Collections.unmodifiableList(recentAvatarStickers);
    }

    public void setEventType(AvatarEventType eventType) {
        this.eventType = eventType;
    }

    public void setRecentAvatarStickers(List<StickerAction> recentAvatarStickers) {
        this.recentAvatarStickers = recentAvatarStickers;
    }

    @ProtobufEnum(name = "SyncActionValue.AvatarUpdatedAction.AvatarEventType")
    public static enum AvatarEventType {
        UPDATED(0),
        CREATED(1),
        DELETED(2);

        AvatarEventType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
