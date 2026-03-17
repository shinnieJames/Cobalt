package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingChannelsPersonalisedRecommendationAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class PrivacySettingChannelsPersonalisedRecommendationHandler implements WebAppStateActionHandler {
    public static final PrivacySettingChannelsPersonalisedRecommendationHandler INSTANCE = new PrivacySettingChannelsPersonalisedRecommendationHandler();

    private PrivacySettingChannelsPersonalisedRecommendationHandler() {

    }

    @Override
    public String actionName() {
        return PrivacySettingChannelsPersonalisedRecommendationAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return PrivacySettingChannelsPersonalisedRecommendationAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof PrivacySettingChannelsPersonalisedRecommendationAction)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setChannelsPersonalisedRecommendationOptOut(
                ((PrivacySettingChannelsPersonalisedRecommendationAction) mutation.value().action().orElseThrow()).isUserOptedOut()
        );
        return MutationApplicationResult.success();
    }
}
