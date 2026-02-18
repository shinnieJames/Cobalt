package com.github.auties00.cobalt.model.message.interactive;

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

    public InteractiveMessageAdditionalMetadata setGalaxyFlowCompleted(Boolean isGalaxyFlowCompleted) {
        this.isGalaxyFlowCompleted = isGalaxyFlowCompleted;
        return this;
    }
}
