package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "ADVEncryptionType")
public enum ADVEncryptionType {
    E2EE(0),
    HOSTED(1);

    ADVEncryptionType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
