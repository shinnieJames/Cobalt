package com.github.auties00.cobalt.model.device;

import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ProtobufMessage(name = "DeviceListMetadata")
public final class DeviceListMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] senderKeyHash;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant senderTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32, packed = true)
    List<Integer> senderKeyIndexes;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    ADVEncryptionType senderAccountType;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    ADVEncryptionType receiverAccountType;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] recipientKeyHash;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant recipientTimestamp;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT32, packed = true)
    List<Integer> recipientKeyIndexes;


    DeviceListMetadata(byte[] senderKeyHash, Instant senderTimestamp, List<Integer> senderKeyIndexes, ADVEncryptionType senderAccountType, ADVEncryptionType receiverAccountType, byte[] recipientKeyHash, Instant recipientTimestamp, List<Integer> recipientKeyIndexes) {
        this.senderKeyHash = senderKeyHash;
        this.senderTimestamp = senderTimestamp;
        this.senderKeyIndexes = senderKeyIndexes;
        this.senderAccountType = senderAccountType;
        this.receiverAccountType = receiverAccountType;
        this.recipientKeyHash = recipientKeyHash;
        this.recipientTimestamp = recipientTimestamp;
        this.recipientKeyIndexes = recipientKeyIndexes;
    }

    public Optional<byte[]> senderKeyHash() {
        return Optional.ofNullable(senderKeyHash);
    }

    public Optional<Instant> senderTimestamp() {
        return Optional.ofNullable(senderTimestamp);
    }

    public List<Integer> senderKeyIndexes() {
        return senderKeyIndexes == null ? List.of() : Collections.unmodifiableList(senderKeyIndexes);
    }

    public Optional<ADVEncryptionType> senderAccountType() {
        return Optional.ofNullable(senderAccountType);
    }

    public Optional<ADVEncryptionType> receiverAccountType() {
        return Optional.ofNullable(receiverAccountType);
    }

    public Optional<byte[]> recipientKeyHash() {
        return Optional.ofNullable(recipientKeyHash);
    }

    public Optional<Instant> recipientTimestamp() {
        return Optional.ofNullable(recipientTimestamp);
    }

    public List<Integer> recipientKeyIndexes() {
        return recipientKeyIndexes == null ? List.of() : Collections.unmodifiableList(recipientKeyIndexes);
    }

    public void setSenderKeyHash(byte[] senderKeyHash) {
        this.senderKeyHash = senderKeyHash;
    }

    public void setSenderTimestamp(Instant senderTimestamp) {
        this.senderTimestamp = senderTimestamp;
    }

    public void setSenderKeyIndexes(List<Integer> senderKeyIndexes) {
        this.senderKeyIndexes = senderKeyIndexes;
    }

    public void setSenderAccountType(ADVEncryptionType senderAccountType) {
        this.senderAccountType = senderAccountType;
    }

    public void setReceiverAccountType(ADVEncryptionType receiverAccountType) {
        this.receiverAccountType = receiverAccountType;
    }

    public void setRecipientKeyHash(byte[] recipientKeyHash) {
        this.recipientKeyHash = recipientKeyHash;
    }

    public void setRecipientTimestamp(Instant recipientTimestamp) {
        this.recipientTimestamp = recipientTimestamp;
    }

    public void setRecipientKeyIndexes(List<Integer> recipientKeyIndexes) {
        this.recipientKeyIndexes = recipientKeyIndexes;
    }
}
