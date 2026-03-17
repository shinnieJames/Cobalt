package com.github.auties00.cobalt.model.device.identity;

import com.github.auties00.cobalt.model.device.DevicePlatformType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "CompanionEphemeralIdentity")
public final class CompanionEphemeralIdentity {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] publicKey;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    DevicePlatformType deviceType;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String ref;


    CompanionEphemeralIdentity(byte[] publicKey, DevicePlatformType deviceType, String ref) {
        this.publicKey = publicKey;
        this.deviceType = deviceType;
        this.ref = ref;
    }

    public Optional<byte[]> publicKey() {
        return Optional.ofNullable(publicKey);
    }

    public Optional<DevicePlatformType> deviceType() {
        return Optional.ofNullable(deviceType);
    }

    public Optional<String> ref() {
        return Optional.ofNullable(ref);
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public void setDeviceType(DevicePlatformType deviceType) {
        this.deviceType = deviceType;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }
}
