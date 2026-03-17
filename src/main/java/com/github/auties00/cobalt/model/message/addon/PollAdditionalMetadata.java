package com.github.auties00.cobalt.model.message.addon;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "PollAdditionalMetadata")
public final class PollAdditionalMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean pollInvalidated;


    PollAdditionalMetadata(Boolean pollInvalidated) {
        this.pollInvalidated = pollInvalidated;
    }

    public boolean pollInvalidated() {
        return pollInvalidated != null && pollInvalidated;
    }

    public void setPollInvalidated(Boolean pollInvalidated) {
        this.pollInvalidated = pollInvalidated;
    }
}
