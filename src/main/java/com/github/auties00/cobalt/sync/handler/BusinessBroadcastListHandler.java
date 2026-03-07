package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles business broadcast list actions.
 *
 * <p>This handler processes mutations for business broadcast lists.
 *
 * <p>Index format: ["business_broadcast_list", listId]
 */
public final class BusinessBroadcastListHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code BusinessBroadcastListHandler}.
     */
    public static final BusinessBroadcastListHandler INSTANCE = new BusinessBroadcastListHandler();

    private BusinessBroadcastListHandler() {

    }

    @Override
    public String actionName() {
        return BusinessBroadcastListAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return BusinessBroadcastListAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return BusinessBroadcastListAction.ACTION_VERSION;
    }

    /**
     * Applies a business broadcast list mutation.
     *
     * <p>Per WhatsApp Web (WAWebBroadcastListSync), on SET the web client validates
     * that the action value (businessBroadcastListAction) is present, then upserts
     * the broadcast list into storage. On REMOVE, the broadcast list is removed.
     * The listId from index[1] must be present or the mutation is malformed.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged, {@code false} otherwise
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var listId = indexArray.getString(1);
        if (listId == null || listId.isEmpty()) {
            return true;
        }

        if (mutation.operation() == SyncdOperation.SET) {
            if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastListAction)) {
                return true;
            }
        }

        return true;
    }
}
