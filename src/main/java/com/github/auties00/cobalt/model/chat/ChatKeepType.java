package com.github.auties00.cobalt.model.chat;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "KeepType")
public enum ChatKeepType {
    UNKNOWN(0),
    KEEP_FOR_ALL(1),
    UNDO_KEEP_FOR_ALL(2);

    ChatKeepType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
