package com.github.auties00.cobalt.model.sync.data;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "PatchDebugData")
public final class PatchDebugData {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] currentLthash;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] newLthash;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] patchVersion;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] collectionName;

    @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
    byte[] firstFourBytesFromAHashOfSnapshotMacKey;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] newLthashSubtract;

    @ProtobufProperty(index = 7, type = ProtobufType.INT32)
    Integer numberAdd;

    @ProtobufProperty(index = 8, type = ProtobufType.INT32)
    Integer numberRemove;

    @ProtobufProperty(index = 9, type = ProtobufType.INT32)
    Integer numberOverride;

    @ProtobufProperty(index = 10, type = ProtobufType.ENUM)
    Platform senderPlatform;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    Boolean isSenderPrimary;


    PatchDebugData(byte[] currentLthash, byte[] newLthash, byte[] patchVersion, byte[] collectionName, byte[] firstFourBytesFromAHashOfSnapshotMacKey, byte[] newLthashSubtract, Integer numberAdd, Integer numberRemove, Integer numberOverride, Platform senderPlatform, Boolean isSenderPrimary) {
        this.currentLthash = currentLthash;
        this.newLthash = newLthash;
        this.patchVersion = patchVersion;
        this.collectionName = collectionName;
        this.firstFourBytesFromAHashOfSnapshotMacKey = firstFourBytesFromAHashOfSnapshotMacKey;
        this.newLthashSubtract = newLthashSubtract;
        this.numberAdd = numberAdd;
        this.numberRemove = numberRemove;
        this.numberOverride = numberOverride;
        this.senderPlatform = senderPlatform;
        this.isSenderPrimary = isSenderPrimary;
    }

    public Optional<byte[]> currentLthash() {
        return Optional.ofNullable(currentLthash);
    }

    public Optional<byte[]> newLthash() {
        return Optional.ofNullable(newLthash);
    }

    public Optional<byte[]> patchVersion() {
        return Optional.ofNullable(patchVersion);
    }

    public Optional<byte[]> collectionName() {
        return Optional.ofNullable(collectionName);
    }

    public Optional<byte[]> firstFourBytesFromAHashOfSnapshotMacKey() {
        return Optional.ofNullable(firstFourBytesFromAHashOfSnapshotMacKey);
    }

    public Optional<byte[]> newLthashSubtract() {
        return Optional.ofNullable(newLthashSubtract);
    }

    public OptionalInt numberAdd() {
        return numberAdd == null ? OptionalInt.empty() : OptionalInt.of(numberAdd);
    }

    public OptionalInt numberRemove() {
        return numberRemove == null ? OptionalInt.empty() : OptionalInt.of(numberRemove);
    }

    public OptionalInt numberOverride() {
        return numberOverride == null ? OptionalInt.empty() : OptionalInt.of(numberOverride);
    }

    public Optional<Platform> senderPlatform() {
        return Optional.ofNullable(senderPlatform);
    }

    public boolean isSenderPrimary() {
        return isSenderPrimary != null && isSenderPrimary;
    }

    public void setCurrentLthash(byte[] currentLthash) {
        this.currentLthash = currentLthash;
    }

    public void setNewLthash(byte[] newLthash) {
        this.newLthash = newLthash;
    }

    public void setPatchVersion(byte[] patchVersion) {
        this.patchVersion = patchVersion;
    }

    public void setCollectionName(byte[] collectionName) {
        this.collectionName = collectionName;
    }

    public void setFirstFourBytesFromAHashOfSnapshotMacKey(byte[] firstFourBytesFromAHashOfSnapshotMacKey) {
        this.firstFourBytesFromAHashOfSnapshotMacKey = firstFourBytesFromAHashOfSnapshotMacKey;
    }

    public void setNewLthashSubtract(byte[] newLthashSubtract) {
        this.newLthashSubtract = newLthashSubtract;
    }

    public void setNumberAdd(Integer numberAdd) {
        this.numberAdd = numberAdd;
    }

    public void setNumberRemove(Integer numberRemove) {
        this.numberRemove = numberRemove;
    }

    public void setNumberOverride(Integer numberOverride) {
        this.numberOverride = numberOverride;
    }

    public void setSenderPlatform(Platform senderPlatform) {
        this.senderPlatform = senderPlatform;
    }

    public void setSenderPrimary(Boolean isSenderPrimary) {
        this.isSenderPrimary = isSenderPrimary;
    }

    @ProtobufEnum(name = "PatchDebugData.Platform")
    public static enum Platform {
        ANDROID(0),
        SMBA(1),
        IPHONE(2),
        SMBI(3),
        WEB(4),
        UWP(5),
        DARWIN(6),
        IPAD(7),
        WEAROS(8),
        WASG(9),
        WEARM(10),
        CAPI(11);

        Platform(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
