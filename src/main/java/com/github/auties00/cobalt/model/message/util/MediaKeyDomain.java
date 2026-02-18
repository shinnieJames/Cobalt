package com.github.auties00.cobalt.model.message.util;

@ProtobufEnum(name = "Message.MediaKeyDomain")
public enum MediaKeyDomain {
    UNSET(0),
    E2EE_CHAT(1),
    STATUS(2),
    CAPI(3),
    BOT(4);

    MediaKeyDomain(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
