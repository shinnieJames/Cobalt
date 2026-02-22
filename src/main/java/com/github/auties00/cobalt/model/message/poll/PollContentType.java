package com.github.auties00.cobalt.model.message.poll;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "Message.PollContentType")
public enum PollContentType {
    UNKNOWN(0),
    TEXT(1),
    IMAGE(2);

    PollContentType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
