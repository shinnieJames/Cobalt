package com.github.auties00.cobalt.model.device.pairing;

import com.github.auties00.cobalt.model.device.identity.CompanionCommitment;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage(name = "ProloguePayload")
public final class ProloguePayload {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] companionEphemeralIdentity;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    CompanionCommitment commitment;


    ProloguePayload(byte[] companionEphemeralIdentity, CompanionCommitment commitment) {
        this.companionEphemeralIdentity = companionEphemeralIdentity;
        this.commitment = commitment;
    }

    public Optional<byte[]> companionEphemeralIdentity() {
        return Optional.ofNullable(companionEphemeralIdentity);
    }

    public Optional<CompanionCommitment> commitment() {
        return Optional.ofNullable(commitment);
    }

    public void setCompanionEphemeralIdentity(byte[] companionEphemeralIdentity) {
        this.companionEphemeralIdentity = companionEphemeralIdentity;
    }

    public void setCommitment(CompanionCommitment commitment) {
        this.commitment = commitment;
    }
}
