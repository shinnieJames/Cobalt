package com.github.auties00.cobalt.model.device.identity;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "ADVDeviceIdentity")
public final class ADVDeviceIdentity {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer rawId;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer keyIndex;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    ADVEncryptionType accountType;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    ADVEncryptionType deviceType;


    ADVDeviceIdentity(Integer rawId, Instant timestamp, Integer keyIndex, ADVEncryptionType accountType, ADVEncryptionType deviceType) {
        this.rawId = rawId;
        this.timestamp = timestamp;
        this.keyIndex = keyIndex;
        this.accountType = accountType;
        this.deviceType = deviceType;
    }

    public OptionalInt rawId() {
        return rawId == null ? OptionalInt.empty() : OptionalInt.of(rawId);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public OptionalInt keyIndex() {
        return keyIndex == null ? OptionalInt.empty() : OptionalInt.of(keyIndex);
    }

    public Optional<ADVEncryptionType> accountType() {
        return Optional.ofNullable(accountType);
    }

    public Optional<ADVEncryptionType> deviceType() {
        return Optional.ofNullable(deviceType);
    }

    public void setRawId(Integer rawId) {
        this.rawId = rawId;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setKeyIndex(Integer keyIndex) {
        this.keyIndex = keyIndex;
    }

    public void setAccountType(ADVEncryptionType accountType) {
        this.accountType = accountType;
    }

    public void setDeviceType(ADVEncryptionType deviceType) {
        this.deviceType = deviceType;
    }
}
