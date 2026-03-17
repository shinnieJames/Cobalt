package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.StatusPostOptInNotificationPreferencesAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class StatusPostOptInNotificationPreferencesHandler implements WebAppStateActionHandler {
    public static final StatusPostOptInNotificationPreferencesHandler INSTANCE = new StatusPostOptInNotificationPreferencesHandler();

    private StatusPostOptInNotificationPreferencesHandler() {

    }

    @Override
    public String actionName() {
        return StatusPostOptInNotificationPreferencesAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    @Override
    public int version() {
        return StatusPostOptInNotificationPreferencesAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof StatusPostOptInNotificationPreferencesAction)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setStatusPostOptInNotificationPreferencesEnabled(
                ((StatusPostOptInNotificationPreferencesAction) mutation.value().action().orElseThrow()).enabled()
        );
        return MutationApplicationResult.success();
    }
}
