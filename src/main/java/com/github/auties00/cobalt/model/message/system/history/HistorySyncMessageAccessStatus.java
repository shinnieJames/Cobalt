package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "Message.HistorySyncMessageAccessStatus")
public final class HistorySyncMessageAccessStatus implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean completeAccessGranted;


    HistorySyncMessageAccessStatus(Boolean completeAccessGranted) {
        this.completeAccessGranted = completeAccessGranted;
    }

    public boolean completeAccessGranted() {
        return completeAccessGranted != null && completeAccessGranted;
    }

    public void setCompleteAccessGranted(Boolean completeAccessGranted) {
        this.completeAccessGranted = completeAccessGranted;
    }
}
