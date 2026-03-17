package com.github.auties00.cobalt.model.sync.action.bot;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.MaibaAIFeaturesControlAction")
public final class MaibaAIFeaturesControlAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "maiba_ai_features_control";

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
    MaibaAIFeatureStatus aiFeatureStatus;


    MaibaAIFeaturesControlAction(MaibaAIFeatureStatus aiFeatureStatus) {
        this.aiFeatureStatus = aiFeatureStatus;
    }

    public Optional<MaibaAIFeatureStatus> aiFeatureStatus() {
        return Optional.ofNullable(aiFeatureStatus);
    }

    public void setAiFeatureStatus(MaibaAIFeatureStatus aiFeatureStatus) {
        this.aiFeatureStatus = aiFeatureStatus;
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
