package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.AvatarUpdatedAction")
public final class AvatarUpdatedAction implements SyncAction {
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

    public AvatarUpdatedAction setEventType(AvatarEventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public AvatarUpdatedAction setRecentAvatarStickers(List<StickerAction> recentAvatarStickers) {
        this.recentAvatarStickers = recentAvatarStickers;
        return this;
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
