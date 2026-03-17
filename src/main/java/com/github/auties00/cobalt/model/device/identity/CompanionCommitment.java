package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "CompanionCommitment")
public final class CompanionCommitment {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] hash;


    CompanionCommitment(byte[] hash) {
        this.hash = hash;
    }

    public Optional<byte[]> hash() {
        return Optional.ofNullable(hash);
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }
}
