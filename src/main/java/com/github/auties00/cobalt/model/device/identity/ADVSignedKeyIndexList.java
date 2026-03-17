package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ADVSignedKeyIndexList")
public final class ADVSignedKeyIndexList {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] details;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] accountSignature;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] accountSignatureKey;


    ADVSignedKeyIndexList(byte[] details, byte[] accountSignature, byte[] accountSignatureKey) {
        this.details = details;
        this.accountSignature = accountSignature;
        this.accountSignatureKey = accountSignatureKey;
    }

    public Optional<byte[]> details() {
        return Optional.ofNullable(details);
    }

    public Optional<byte[]> accountSignature() {
        return Optional.ofNullable(accountSignature);
    }

    public Optional<byte[]> accountSignatureKey() {
        return Optional.ofNullable(accountSignatureKey);
    }

    public void setDetails(byte[] details) {
        this.details = details;
    }

    public void setAccountSignature(byte[] accountSignature) {
        this.accountSignature = accountSignature;
    }

    public void setAccountSignatureKey(byte[] accountSignatureKey) {
        this.accountSignatureKey = accountSignatureKey;
    }
}
