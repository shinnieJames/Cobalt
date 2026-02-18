package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.MarketingMessageBroadcastAction")
public final class MarketingMessageBroadcastAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer repliedCount;


    MarketingMessageBroadcastAction(Integer repliedCount) {
        this.repliedCount = repliedCount;
    }

    public OptionalInt repliedCount() {
        return repliedCount == null ? OptionalInt.empty() : OptionalInt.of(repliedCount);
    }

    public MarketingMessageBroadcastAction setRepliedCount(Integer repliedCount) {
        this.repliedCount = repliedCount;
        return this;
    }
}
