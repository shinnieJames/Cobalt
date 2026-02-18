package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PrimaryVersionAction")
public final class PrimaryVersionAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String version;


    PrimaryVersionAction(String version) {
        this.version = version;
    }

    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    public PrimaryVersionAction setVersion(String version) {
        this.version = version;
        return this;
    }
}
