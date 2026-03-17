package com.github.auties00.cobalt.model.signal;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "DeviceConsistencyCodeMessage")
public final class DeviceConsistencyCodeMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer generation;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] signature;


    DeviceConsistencyCodeMessage(Integer generation, byte[] signature) {
        this.generation = generation;
        this.signature = signature;
    }

    public OptionalInt generation() {
        return generation == null ? OptionalInt.empty() : OptionalInt.of(generation);
    }

    public Optional<byte[]> signature() {
        return Optional.ofNullable(signature);
    }

    public void setGeneration(Integer generation) {
        this.generation = generation;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }
}
