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

    public DeviceListMetadata setSenderKeyHash(byte[] senderKeyHash) {
        this.senderKeyHash = senderKeyHash;
        return this;
    }

    public DeviceListMetadata setSenderTimestamp(Instant senderTimestamp) {
        this.senderTimestamp = senderTimestamp;
        return this;
    }

    public DeviceListMetadata setSenderKeyIndexes(List<Integer> senderKeyIndexes) {
        this.senderKeyIndexes = senderKeyIndexes;
        return this;
    }

    public DeviceListMetadata setSenderAccountType(ADVEncryptionType senderAccountType) {
        this.senderAccountType = senderAccountType;
        return this;
    }

    public DeviceListMetadata setReceiverAccountType(ADVEncryptionType receiverAccountType) {
        this.receiverAccountType = receiverAccountType;
        return this;
    }

    public DeviceListMetadata setRecipientKeyHash(byte[] recipientKeyHash) {
        this.recipientKeyHash = recipientKeyHash;
        return this;
    }

    public DeviceListMetadata setRecipientTimestamp(Instant recipientTimestamp) {
        this.recipientTimestamp = recipientTimestamp;
        return this;
    }

    public DeviceListMetadata setRecipientKeyIndexes(List<Integer> recipientKeyIndexes) {
        this.recipientKeyIndexes = recipientKeyIndexes;
        return this;
    }
}
