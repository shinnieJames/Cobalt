package com.github.auties00.cobalt.model.sync.setting.privacy;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PrivateProcessingSettingAction")
public final class PrivateProcessingSettingAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    PrivateProcessingStatus privateProcessingStatus;


    PrivateProcessingSettingAction(PrivateProcessingStatus privateProcessingStatus) {
        this.privateProcessingStatus = privateProcessingStatus;
    }

    public Optional<PrivateProcessingStatus> privateProcessingStatus() {
        return Optional.ofNullable(privateProcessingStatus);
    }

    public PrivateProcessingSettingAction setPrivateProcessingStatus(PrivateProcessingStatus privateProcessingStatus) {
        this.privateProcessingStatus = privateProcessingStatus;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.PrivateProcessingSettingAction.PrivateProcessingStatus")
    public static enum PrivateProcessingStatus {
        UNDEFINED(0),
        ENABLED(1),
        DISABLED(2);

        PrivateProcessingStatus(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
