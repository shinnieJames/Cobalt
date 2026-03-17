package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.StickerSyncRMRMessage")
public final class StickerSyncRMRMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    List<String> filehash;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String rmrSource;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant requestTimestamp;


    StickerSyncRMRMessage(List<String> filehash, String rmrSource, Instant requestTimestamp) {
        this.filehash = filehash;
        this.rmrSource = rmrSource;
        this.requestTimestamp = requestTimestamp;
    }

    public List<String> filehash() {
        return filehash == null ? List.of() : Collections.unmodifiableList(filehash);
    }

    public Optional<String> rmrSource() {
        return Optional.ofNullable(rmrSource);
    }

    public Optional<Instant> requestTimestamp() {
        return Optional.ofNullable(requestTimestamp);
    }

    public void setFilehash(List<String> filehash) {
        this.filehash = filehash;
    }

    public void setRmrSource(String rmrSource) {
        this.rmrSource = rmrSource;
    }

    public void setRequestTimestamp(Instant requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }
}
