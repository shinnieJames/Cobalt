package com.github.auties00.cobalt.model.sync.action.chat;

import com.github.auties00.cobalt.model.sync.SyncAction;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.DeleteMessageForMeAction")
public final class DeleteMessageForMeAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean deleteMedia;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
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

    public DeleteMessageForMeAction setDeleteMedia(Boolean deleteMedia) {
        this.deleteMedia = deleteMedia;
        return this;
    }

    public DeleteMessageForMeAction setMessageTimestamp(Instant messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
        return this;
    }
}
