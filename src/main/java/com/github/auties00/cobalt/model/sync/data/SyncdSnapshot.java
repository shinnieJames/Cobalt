package com.github.auties00.cobalt.model.sync.data;

import com.github.auties00.cobalt.model.signal.KeyId;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncdSnapshot")
public final class SyncdSnapshot {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    SyncdVersion version;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<SyncdRecord> records;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mac;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    KeyId keyId;


    SyncdSnapshot(SyncdVersion version, List<SyncdRecord> records, byte[] mac, KeyId keyId) {
        this.version = version;
        this.records = records;
        this.mac = mac;
        this.keyId = keyId;
    }

    public Optional<SyncdVersion> version() {
        return Optional.ofNullable(version);
    }

    public List<SyncdRecord> records() {
        return records == null ? List.of() : Collections.unmodifiableList(records);
    }

    public Optional<byte[]> mac() {
        return Optional.ofNullable(mac);
    }

    public Optional<KeyId> keyId() {
        return Optional.ofNullable(keyId);
    }

    public void setVersion(SyncdVersion version) {
        this.version = version;
    }

    public void setRecords(List<SyncdRecord> records) {
        this.records = records;
    }

    public void setMac(byte[] mac) {
        this.mac = mac;
    }

    public void setKeyId(KeyId keyId) {
        this.keyId = keyId;
    }
}
