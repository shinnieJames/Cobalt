package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class MaibaAIFeaturesControlHandler implements WebAppStateActionHandler {
    public static final MaibaAIFeaturesControlHandler INSTANCE = new MaibaAIFeaturesControlHandler();

    private MaibaAIFeaturesControlHandler() {

    }

    @Override
    public String actionName() {
        return MaibaAIFeaturesControlAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    @Override
    public int version() {
        return MaibaAIFeaturesControlAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return applyMutationResult(client, mutation).actionState() == com.github.auties00.cobalt.model.sync.SyncActionState.SUCCESS;
    }

    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MaibaAIFeaturesControlAction action)
                || action.aiFeatureStatus().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setMaibaAiFeatureStatus(action.aiFeatureStatus().get());
        return MutationApplicationResult.success();
    }
}
