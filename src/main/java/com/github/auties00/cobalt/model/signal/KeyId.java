package com.github.auties00.cobalt.model.signal;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "KeyId")
public final class KeyId {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] id;


    KeyId(byte[] id) {
        this.id = id;
    }

    public Optional<byte[]> id() {
        return Optional.ofNullable(id);
    }

    public void setId(byte[] id) {
        this.id = id;
    }
}
