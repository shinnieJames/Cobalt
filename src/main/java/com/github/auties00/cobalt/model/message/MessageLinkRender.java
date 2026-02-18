package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "WebLinkRenderConfig")
public enum MessageLinkRender {
    WEBVIEW(0),
    SYSTEM(1);

    MessageLinkRender(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
