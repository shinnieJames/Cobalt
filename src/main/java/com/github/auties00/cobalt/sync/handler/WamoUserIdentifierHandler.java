package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.WamoUserIdentifierAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class WamoUserIdentifierHandler implements WebAppStateActionHandler {
    public static final WamoUserIdentifierHandler INSTANCE = new WamoUserIdentifierHandler();

    private WamoUserIdentifierHandler() {

    }

    @Override
    public String actionName() {
        return WamoUserIdentifierAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.CRITICAL_BLOCK;
    }

    @Override
    public int version() {
        return WamoUserIdentifierAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof WamoUserIdentifierAction action)
                || action.identifier().isEmpty()
                || action.identifier().get().isBlank()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setWamoUserIdentifier(action.identifier().get());
        return MutationApplicationResult.success();
    }
}
