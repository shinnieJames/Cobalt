package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class PrivateProcessingSettingHandler implements WebAppStateActionHandler {
    public static final PrivateProcessingSettingHandler INSTANCE = new PrivateProcessingSettingHandler();

    private PrivateProcessingSettingHandler() {

    }

    @Override
    public String actionName() {
        return PrivateProcessingSettingAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    @Override
    public int version() {
        return PrivateProcessingSettingAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof PrivateProcessingSettingAction action)
                || action.privateProcessingStatus().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setPrivateProcessingStatus(action.privateProcessingStatus().get());
        return MutationApplicationResult.success();
    }
}
