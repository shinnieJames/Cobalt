package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "PrivacySystemMessage")
public enum PrivacySystemMessage {
    E2EE_MSG(1),
    NE2EE_SELF(2),
    NE2EE_OTHER(3);

    PrivacySystemMessage(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
