package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.FullHistorySyncOnDemandRequestMetadata")
public final class FullHistorySyncOnDemandRequestMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String requestId;


    FullHistorySyncOnDemandRequestMetadata(String requestId) {
        this.requestId = requestId;
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
