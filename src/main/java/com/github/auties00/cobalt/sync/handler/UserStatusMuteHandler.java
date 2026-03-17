package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles user status mute actions.
 */
public final class UserStatusMuteHandler implements WebAppStateActionHandler {
    public static final UserStatusMuteHandler INSTANCE = new UserStatusMuteHandler();

    private UserStatusMuteHandler() {

    }

    @Override
    public String actionName() {
        return UserStatusMuteAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return UserStatusMuteAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return UserStatusMuteAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof UserStatusMuteAction action)) {
            return MutationApplicationResult.malformed();
        }

        var userJidString = JSON.parseArray(mutation.index()).getString(1);
        if (userJidString == null || userJidString.isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        var userJid = Jid.of(userJidString);
        if (userJid.hasServer(JidServer.groupOrCommunity())) {
            var states = new java.util.HashMap<>(client.store().groupStatusMuteStates());
            states.put(userJidString, action.muted());
            client.store().setGroupStatusMuteStates(states);
            return MutationApplicationResult.success();
        }

        var contact = client.store().findContactByJid(userJid);
        if (contact.isEmpty()) {
            return MutationApplicationResult.orphan(userJidString, "UserStatusMute");
        }

        contact.get().setStatusMuted(action.muted());
        return MutationApplicationResult.success();
    }
}
