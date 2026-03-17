package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "EncryptedPairingRequest")
public final class EncryptedPairingRequest {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] encryptedPayload;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] iv;


    EncryptedPairingRequest(byte[] encryptedPayload, byte[] iv) {
        this.encryptedPayload = encryptedPayload;
        this.iv = iv;
    }

    public Optional<byte[]> encryptedPayload() {
        return Optional.ofNullable(encryptedPayload);
    }

    public Optional<byte[]> iv() {
        return Optional.ofNullable(iv);
    }

    public void setEncryptedPayload(byte[] encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }
}
