package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.RemoveRecentStickerAction")
public final class RemoveRecentStickerAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant lastStickerSentTs;


    RemoveRecentStickerAction(Instant lastStickerSentTs) {
        this.lastStickerSentTs = lastStickerSentTs;
    }

    public Optional<Instant> lastStickerSentTs() {
        return Optional.ofNullable(lastStickerSentTs);
    }

    public RemoveRecentStickerAction setLastStickerSentTs(Instant lastStickerSentTs) {
        this.lastStickerSentTs = lastStickerSentTs;
        return this;
    }
}
