package com.github.auties00.cobalt.model.message.system.history;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "Message.HistorySyncType")
public enum HistorySyncType {
    INITIAL_BOOTSTRAP(0),
    INITIAL_STATUS_V3(1),
    FULL(2),
    RECENT(3),
    PUSH_NAME(4),
    NON_BLOCKING_DATA(5),
    ON_DEMAND(6),
    NO_HISTORY(7),
    MESSAGE_ACCESS_STATUS(8);

    HistorySyncType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
