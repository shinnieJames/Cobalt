package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.Message;

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

    public HistorySyncMessageAccessStatus setCompleteAccessGranted(Boolean completeAccessGranted) {
        this.completeAccessGranted = completeAccessGranted;
        return this;
    }
}
