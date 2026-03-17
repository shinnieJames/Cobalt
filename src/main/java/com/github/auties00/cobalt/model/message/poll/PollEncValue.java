package com.github.auties00.cobalt.model.message.poll;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PollEncValue")
public final class PollEncValue implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encIv;


    PollEncValue(byte[] encPayload, byte[] encIv) {
        this.encPayload = encPayload;
        this.encIv = encIv;
    }

    public Optional<byte[]> encPayload() {
        return Optional.ofNullable(encPayload);
    }

    public Optional<byte[]> encIv() {
        return Optional.ofNullable(encIv);
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }
}
