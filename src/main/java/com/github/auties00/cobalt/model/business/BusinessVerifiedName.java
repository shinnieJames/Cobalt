package com.github.auties00.cobalt.model.business;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@ProtobufMessage
public final class BusinessVerifiedName {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Jid jid;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    int level;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64)
    long serial;

    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    boolean isApi;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    boolean isSmb;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    HostStorageType hostStorage;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    ActualActorsType actualActors;

    @ProtobufProperty(index = 9, type = ProtobufType.INT64)
    Long privacyModeTimestampSeconds;

    BusinessVerifiedName(Jid jid, String name, int level, long serial, boolean isApi, boolean isSmb,
                         HostStorageType hostStorage, ActualActorsType actualActors,
                         Long privacyModeTimestampSeconds) {
        this.jid = Objects.requireNonNull(jid, "jid");
        this.name = name;
        this.level = level;
        this.serial = serial;
        this.isApi = isApi;
        this.isSmb = isSmb;
        this.hostStorage = hostStorage;
        this.actualActors = actualActors;
        this.privacyModeTimestampSeconds = privacyModeTimestampSeconds;
    }

    public Jid jid() {
        return jid;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public int level() {
        return level;
    }

    public long serial() {
        return serial;
    }

    public boolean isApi() {
        return isApi;
    }

    public boolean isSmb() {
        return isSmb;
    }

    public Optional<HostStorageType> hostStorage() {
        return Optional.ofNullable(hostStorage);
    }

    public Optional<ActualActorsType> actualActors() {
        return Optional.ofNullable(actualActors);
    }

    public Optional<Instant> privacyModeTimestamp() {
        return Optional.ofNullable(privacyModeTimestampSeconds)
                .map(Instant::ofEpochSecond);
    }

    public boolean hasPrivacyMode() {
        return hostStorage != null && actualActors != null && privacyModeTimestampSeconds != null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BusinessVerifiedName that && Objects.equals(jid, that.jid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jid);
    }

    @Override
    public String toString() {
        return "VerifiedBusinessName[jid=" + jid + ", name=" + name + ", level=" + level +
               ", isApi=" + isApi + ", hostStorage=" + hostStorage +
               ", actualActors=" + actualActors + ", privacyModeTs=" + privacyModeTimestampSeconds + ']';
    }

    @ProtobufEnum
    public enum HostStorageType {
        ON_PREMISE(1),
        FACEBOOK(2);

        final int index;

        HostStorageType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }

    @ProtobufEnum
    public enum ActualActorsType {
        SELF(1),
        BSP(2),
        CAPI(3);

        final int index;

        ActualActorsType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }
}