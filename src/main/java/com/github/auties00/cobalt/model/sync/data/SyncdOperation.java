package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "SyncdMutation.SyncdOperation")
public enum SyncdOperation {
    SET(0, ((byte) (0x1))),
    REMOVE(1, ((byte) (0x2)));

    final int index;
    private final byte content;

    SyncdOperation(@ProtobufEnumIndex int index, byte content) {
        this.index = index;
        this.content = content;
    }

    public int index() {
        return index;
    }

    public byte content() {
        return content;
    }
}
