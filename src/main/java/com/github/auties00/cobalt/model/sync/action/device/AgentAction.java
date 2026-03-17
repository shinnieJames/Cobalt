package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.AgentAction")
public final class AgentAction implements SyncAction<AgentActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "deviceAgent";

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

    public void setName(String name) {
        this.name = name;
    }

    public void setDeviceID(Integer deviceID) {
        this.deviceID = deviceID;
    }

    public void setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }


}
