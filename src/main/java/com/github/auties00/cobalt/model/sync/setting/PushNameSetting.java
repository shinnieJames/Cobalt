package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PushNameSetting")
public final class PushNameSetting implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String name;


    PushNameSetting(String name) {
        this.name = name;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public PushNameSetting setName(String name) {
        this.name = name;
        return this;
    }
}
