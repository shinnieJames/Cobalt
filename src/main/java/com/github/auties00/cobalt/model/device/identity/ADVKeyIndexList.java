package com.github.auties00.cobalt.model.device.identity;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "ADVKeyIndexList")
public final class ADVKeyIndexList {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer rawId;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer currentIndex;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32, packed = true)
    List<Integer> validIndexes;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    ADVEncryptionType accountType;


    ADVKeyIndexList(Integer rawId, Instant timestamp, Integer currentIndex, List<Integer> validIndexes, ADVEncryptionType accountType) {
        this.rawId = rawId;
        this.timestamp = timestamp;
        this.currentIndex = currentIndex;
        this.validIndexes = validIndexes;
        this.accountType = accountType;
    }

    public OptionalInt rawId() {
        return rawId == null ? OptionalInt.empty() : OptionalInt.of(rawId);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public OptionalInt currentIndex() {
        return currentIndex == null ? OptionalInt.empty() : OptionalInt.of(currentIndex);
    }

    public List<Integer> validIndexes() {
        return validIndexes == null ? List.of() : Collections.unmodifiableList(validIndexes);
    }

    public Optional<ADVEncryptionType> accountType() {
        return Optional.ofNullable(accountType);
    }

    public void setRawId(Integer rawId) {
        this.rawId = rawId;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setCurrentIndex(Integer currentIndex) {
        this.currentIndex = currentIndex;
    }

    public void setValidIndexes(List<Integer> validIndexes) {
        this.validIndexes = validIndexes;
    }

    public void setAccountType(ADVEncryptionType accountType) {
        this.accountType = accountType;
    }
}
