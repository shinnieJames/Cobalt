package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.PnForLidChatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles phone number for LID chat actions.
 */
public final class PnForLidChatHandler implements WebAppStateActionHandler {
    public static final PnForLidChatHandler INSTANCE = new PnForLidChatHandler();

    private PnForLidChatHandler() {

    }

    @Override
    public String actionName() {
        return PnForLidChatAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return PnForLidChatAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return PnForLidChatAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return applyMutationResult(client, mutation).actionState() == com.github.auties00.cobalt.model.sync.SyncActionState.SUCCESS;
    }

    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!client.abPropsService().getBool(ABProp.PNH_PN_FOR_LID_CHAT_SYNC)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PnForLidChatAction action)) {
            return MutationApplicationResult.malformed();
        }

        var lidJidString = JSON.parseArray(mutation.index()).getString(1);
        if (lidJidString == null || lidJidString.isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return MutationApplicationResult.malformed();
        }

        var pnJid = action.pnJid().orElse(null);
        if (pnJid == null) {
            return MutationApplicationResult.malformed();
        }

        client.store().registerLidMapping(pnJid, lidJid);
        return MutationApplicationResult.success();
    }
}
