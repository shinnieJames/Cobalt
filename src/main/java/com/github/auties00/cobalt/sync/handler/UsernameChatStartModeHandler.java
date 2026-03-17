package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class UsernameChatStartModeHandler implements WebAppStateActionHandler {
    public static final UsernameChatStartModeHandler INSTANCE = new UsernameChatStartModeHandler();

    private UsernameChatStartModeHandler() {

    }

    @Override
    public String actionName() {
        return UsernameChatStartModeAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return UsernameChatStartModeAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof UsernameChatStartModeAction action)
                || action.chatStartMode().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setUsernameChatStartMode(action.chatStartMode().get());
        return MutationApplicationResult.success();
    }
}
