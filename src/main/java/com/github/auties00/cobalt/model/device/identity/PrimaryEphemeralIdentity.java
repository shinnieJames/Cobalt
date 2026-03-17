package com.github.auties00.cobalt.model.device.identity;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "PrimaryEphemeralIdentity")
public final class PrimaryEphemeralIdentity {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] publicKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] nonce;


    PrimaryEphemeralIdentity(byte[] publicKey, byte[] nonce) {
        this.publicKey = publicKey;
        this.nonce = nonce;
    }

    public Optional<byte[]> publicKey() {
        return Optional.ofNullable(publicKey);
    }

    public Optional<byte[]> nonce() {
        return Optional.ofNullable(nonce);
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }
}
