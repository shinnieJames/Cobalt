package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "MessageSecretMessage")
public final class SecretMessageContainer {
    @ProtobufProperty(index = 1, type = ProtobufType.SFIXED32)
    Integer version;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encIv;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encPayload;


    SecretMessageContainer(Integer version, byte[] encIv, byte[] encPayload) {
        this.version = version;
        this.encIv = encIv;
        this.encPayload = encPayload;
    }

    public OptionalInt version() {
        return version == null ? OptionalInt.empty() : OptionalInt.of(version);
    }

    public Optional<byte[]> encIv() {
        return Optional.ofNullable(encIv);
    }

    public Optional<byte[]> encPayload() {
        return Optional.ofNullable(encPayload);
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }
}
