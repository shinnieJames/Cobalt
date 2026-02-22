package com.github.auties00.cobalt.model.sync.action.privacy;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.PrivateProcessingSettingAction")
public final class PrivateProcessingSettingAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "private_processing_setting";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


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
