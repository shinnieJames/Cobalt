package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ADVSignedDeviceIdentity")
public final class ADVSignedDeviceIdentity {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] details;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] accountSignatureKey;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] accountSignature;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] deviceSignature;


    ADVSignedDeviceIdentity(byte[] details, byte[] accountSignatureKey, byte[] accountSignature, byte[] deviceSignature) {
        this.details = details;
        this.accountSignatureKey = accountSignatureKey;
        this.accountSignature = accountSignature;
        this.deviceSignature = deviceSignature;
    }

    public Optional<byte[]> details() {
        return Optional.ofNullable(details);
    }

    public Optional<byte[]> accountSignatureKey() {
        return Optional.ofNullable(accountSignatureKey);
    }

    public Optional<byte[]> accountSignature() {
        return Optional.ofNullable(accountSignature);
    }

    public Optional<byte[]> deviceSignature() {
        return Optional.ofNullable(deviceSignature);
    }

    public void setDetails(byte[] details) {
        this.details = details;
    }

    public void setAccountSignatureKey(byte[] accountSignatureKey) {
        this.accountSignatureKey = accountSignatureKey;
    }

    public void setAccountSignature(byte[] accountSignature) {
        this.accountSignature = accountSignature;
    }

    public void setDeviceSignature(byte[] deviceSignature) {
        this.deviceSignature = deviceSignature;
    }
}
