package com.github.auties00.cobalt.model.sync;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionData")
public final class SyncActionData {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] index;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SyncActionValue value;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] padding;

    @ProtobufProperty(index = 4, type = ProtobufType.INT32)
    Integer version;


    SyncActionData(byte[] index, SyncActionValue value, byte[] padding, Integer version) {
        this.index = index;
        this.value = value;
        this.padding = padding;
        this.version = version;
    }

    public Optional<byte[]> index() {
        return Optional.ofNullable(index);
    }

    public Optional<SyncActionValue> value() {
        return Optional.ofNullable(value);
    }

    public Optional<byte[]> padding() {
        return Optional.ofNullable(padding);
    }

    public OptionalInt version() {
        return version == null ? OptionalInt.empty() : OptionalInt.of(version);
    }

    public SyncActionData setIndex(byte[] index) {
        this.index = index;
        return this;
    }

    public SyncActionData setValue(SyncActionValue value) {
        this.value = value;
        return this;
    }

    public SyncActionData setPadding(byte[] padding) {
        this.padding = padding;
        return this;
    }

    public SyncActionData setVersion(Integer version) {
        this.version = version;
        return this;
    }
}
