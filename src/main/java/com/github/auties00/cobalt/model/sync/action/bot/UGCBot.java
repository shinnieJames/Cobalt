package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.UGCBot")
public final class UGCBot implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] definition;


    UGCBot(byte[] definition) {
        this.definition = definition;
    }

    public Optional<byte[]> definition() {
        return Optional.ofNullable(definition);
    }

    public UGCBot setDefinition(byte[] definition) {
        this.definition = definition;
        return this;
    }
}
