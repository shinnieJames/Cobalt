package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.AgentAction")
public final class AgentAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    Integer deviceID;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean isDeleted;


    AgentAction(String name, Integer deviceID, Boolean isDeleted) {
        this.name = name;
        this.deviceID = deviceID;
        this.isDeleted = isDeleted;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public OptionalInt deviceID() {
        return deviceID == null ? OptionalInt.empty() : OptionalInt.of(deviceID);
    }

    public boolean isDeleted() {
        return isDeleted != null && isDeleted;
    }

    public AgentAction setName(String name) {
        this.name = name;
        return this;
    }

    public AgentAction setDeviceID(Integer deviceID) {
        this.deviceID = deviceID;
        return this;
    }

    public AgentAction setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
        return this;
    }
}
