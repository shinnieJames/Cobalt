package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.AppStateSyncKeyId")
public final class AppStateSyncKeyId implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] keyId;


    AppStateSyncKeyId(byte[] keyId) {
        this.keyId = keyId;
    }

    public Optional<byte[]> keyId() {
        return Optional.ofNullable(keyId);
    }

    public void setKeyId(byte[] keyId) {
        this.keyId = keyId;
    }
}
