package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "WebMessageInfo.Status")
public enum MessageStatus {
    ERROR(0),
    PENDING(1),
    SERVER_ACK(2),
    DELIVERY_ACK(3),
    READ(4),
    PLAYED(5);

    MessageStatus(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
