package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MaibaAIFeaturesControlAction")
public final class MaibaAIFeaturesControlAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    MaibaAIFeatureStatus aiFeatureStatus;


    MaibaAIFeaturesControlAction(MaibaAIFeatureStatus aiFeatureStatus) {
        this.aiFeatureStatus = aiFeatureStatus;
    }

    public Optional<MaibaAIFeatureStatus> aiFeatureStatus() {
        return Optional.ofNullable(aiFeatureStatus);
    }

    public MaibaAIFeaturesControlAction setAiFeatureStatus(MaibaAIFeatureStatus aiFeatureStatus) {
        this.aiFeatureStatus = aiFeatureStatus;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.MaibaAIFeaturesControlAction.MaibaAIFeatureStatus")
    public static enum MaibaAIFeatureStatus {
        ENABLED(0),
        ENABLED_HAS_LEARNING(1),
        DISABLED(2);

        MaibaAIFeatureStatus(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
