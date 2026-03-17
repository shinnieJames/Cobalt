package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.call.DeleteIndividualCallLogAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.HashMap;

public final class DeleteIndividualCallLogHandler implements WebAppStateActionHandler {
    public static final DeleteIndividualCallLogHandler INSTANCE = new DeleteIndividualCallLogHandler();

    private DeleteIndividualCallLogHandler() {

    }

    @Override
    public String actionName() {
        return DeleteIndividualCallLogAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return DeleteIndividualCallLogAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof DeleteIndividualCallLogAction)) {
            return MutationApplicationResult.malformed();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 4) {
            return MutationApplicationResult.malformed();
        }

        var peer = indexArray.getString(1);
        var callId = indexArray.getString(2);
        var fromMe = indexArray.getString(3);
        if (peer == null || callId == null || fromMe == null) {
            return MutationApplicationResult.malformed();
        }

        var states = new HashMap<>(client.store().callLogStates());
        states.remove(peer + "|" + callId + "|" + fromMe);
        client.store().setCallLogStates(states);
        return MutationApplicationResult.success();
    }
}
