package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.AppStateSyncKeyData")
public final class AppStateSyncKeyData implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] keyData;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    AppStateSyncKeyFingerprint fingerprint;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;


    AppStateSyncKeyData(byte[] keyData, AppStateSyncKeyFingerprint fingerprint, Instant timestamp) {
        this.keyData = keyData;
        this.fingerprint = fingerprint;
        this.timestamp = timestamp;
    }

    public Optional<byte[]> keyData() {
        return Optional.ofNullable(keyData);
    }

    public Optional<AppStateSyncKeyFingerprint> fingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public AppStateSyncKeyData setKeyData(byte[] keyData) {
        this.keyData = keyData;
        return this;
    }

    public AppStateSyncKeyData setFingerprint(AppStateSyncKeyFingerprint fingerprint) {
        this.fingerprint = fingerprint;
        return this;
    }

    public AppStateSyncKeyData setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
