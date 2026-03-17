package com.github.auties00.cobalt.model.message.interactive;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "InteractiveMessageAdditionalMetadata")
public final class InteractiveMessageAdditionalMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isGalaxyFlowCompleted;


    InteractiveMessageAdditionalMetadata(Boolean isGalaxyFlowCompleted) {
        this.isGalaxyFlowCompleted = isGalaxyFlowCompleted;
    }

    public boolean isGalaxyFlowCompleted() {
        return isGalaxyFlowCompleted != null && isGalaxyFlowCompleted;
    }

    public void setGalaxyFlowCompleted(Boolean isGalaxyFlowCompleted) {
        this.isGalaxyFlowCompleted = isGalaxyFlowCompleted;
    }
}
