package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ADVSignedDeviceIdentityHMAC")
public final class ADVSignedDeviceIdentityHMAC {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] details;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] hmac;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    ADVEncryptionType accountType;


    ADVSignedDeviceIdentityHMAC(byte[] details, byte[] hmac, ADVEncryptionType accountType) {
        this.details = details;
        this.hmac = hmac;
        this.accountType = accountType;
    }

    public Optional<byte[]> details() {
        return Optional.ofNullable(details);
    }

    public Optional<byte[]> hmac() {
        return Optional.ofNullable(hmac);
    }

    public Optional<ADVEncryptionType> accountType() {
        return Optional.ofNullable(accountType);
    }

    public ADVSignedDeviceIdentityHMAC setDetails(byte[] details) {
        this.details = details;
        return this;
    }

    public ADVSignedDeviceIdentityHMAC setHmac(byte[] hmac) {
        this.hmac = hmac;
        return this;
    }

    public ADVSignedDeviceIdentityHMAC setAccountType(ADVEncryptionType accountType) {
        this.accountType = accountType;
        return this;
    }
}
