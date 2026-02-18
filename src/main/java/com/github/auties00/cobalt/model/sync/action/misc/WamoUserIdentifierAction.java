package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.WamoUserIdentifierAction")
public final class WamoUserIdentifierAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String identifier;


    WamoUserIdentifierAction(String identifier) {
        this.identifier = identifier;
    }

    public Optional<String> identifier() {
        return Optional.ofNullable(identifier);
    }

    public WamoUserIdentifierAction setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }
}
