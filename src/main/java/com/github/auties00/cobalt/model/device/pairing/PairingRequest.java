package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "PairingRequest")
public final class PairingRequest {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] companionPublicKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] companionIdentityKey;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] advSecret;


    PairingRequest(byte[] companionPublicKey, byte[] companionIdentityKey, byte[] advSecret) {
        this.companionPublicKey = companionPublicKey;
        this.companionIdentityKey = companionIdentityKey;
        this.advSecret = advSecret;
    }

    public Optional<byte[]> companionPublicKey() {
        return Optional.ofNullable(companionPublicKey);
    }

    public Optional<byte[]> companionIdentityKey() {
        return Optional.ofNullable(companionIdentityKey);
    }

    public Optional<byte[]> advSecret() {
        return Optional.ofNullable(advSecret);
    }

    public void setCompanionPublicKey(byte[] companionPublicKey) {
        this.companionPublicKey = companionPublicKey;
    }

    public void setCompanionIdentityKey(byte[] companionIdentityKey) {
        this.companionIdentityKey = companionIdentityKey;
    }

    public void setAdvSecret(byte[] advSecret) {
        this.advSecret = advSecret;
    }
}
