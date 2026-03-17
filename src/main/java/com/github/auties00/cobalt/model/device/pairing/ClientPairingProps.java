package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "ClientPairingProps")
public final class ClientPairingProps {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isChatDbLidMigrated;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean isSyncdPureLidSession;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean isSyncdSnapshotRecoveryEnabled;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean isHsThumbnailSyncEnabled;


    ClientPairingProps(Boolean isChatDbLidMigrated, Boolean isSyncdPureLidSession, Boolean isSyncdSnapshotRecoveryEnabled, Boolean isHsThumbnailSyncEnabled) {
        this.isChatDbLidMigrated = isChatDbLidMigrated;
        this.isSyncdPureLidSession = isSyncdPureLidSession;
        this.isSyncdSnapshotRecoveryEnabled = isSyncdSnapshotRecoveryEnabled;
        this.isHsThumbnailSyncEnabled = isHsThumbnailSyncEnabled;
    }

    public boolean isChatDbLidMigrated() {
        return isChatDbLidMigrated != null && isChatDbLidMigrated;
    }

    public boolean isSyncdPureLidSession() {
        return isSyncdPureLidSession != null && isSyncdPureLidSession;
    }

    public boolean isSyncdSnapshotRecoveryEnabled() {
        return isSyncdSnapshotRecoveryEnabled != null && isSyncdSnapshotRecoveryEnabled;
    }

    public boolean isHsThumbnailSyncEnabled() {
        return isHsThumbnailSyncEnabled != null && isHsThumbnailSyncEnabled;
    }

    public void setChatDbLidMigrated(Boolean isChatDbLidMigrated) {
        this.isChatDbLidMigrated = isChatDbLidMigrated;
    }

    public void setSyncdPureLidSession(Boolean isSyncdPureLidSession) {
        this.isSyncdPureLidSession = isSyncdPureLidSession;
    }

    public void setSyncdSnapshotRecoveryEnabled(Boolean isSyncdSnapshotRecoveryEnabled) {
        this.isSyncdSnapshotRecoveryEnabled = isSyncdSnapshotRecoveryEnabled;
    }

    public void setHsThumbnailSyncEnabled(Boolean isHsThumbnailSyncEnabled) {
        this.isHsThumbnailSyncEnabled = isHsThumbnailSyncEnabled;
    }
}
