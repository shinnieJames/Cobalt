package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncdIndex")
public final class SyncdIndex {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] blob;


    SyncdIndex(byte[] blob) {
        this.blob = blob;
    }

    public Optional<byte[]> blob() {
        return Optional.ofNullable(blob);
    }

    public void setBlob(byte[] blob) {
        this.blob = blob;
    }
}
