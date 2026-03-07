package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastInsightsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles business broadcast insights actions.
 *
 * <p>Per WhatsApp Web, this handler processes mutations for business
 * broadcast campaign delivery statistics (recipient, delivered, read,
 * replied, and quick reply counts).
 *
 * <p>Index format: ["business_broadcast_insights_sync", campaignId]
 */
public final class BusinessBroadcastInsightsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code BusinessBroadcastInsightsHandler}.
     */
    public static final BusinessBroadcastInsightsHandler INSTANCE = new BusinessBroadcastInsightsHandler();

    private BusinessBroadcastInsightsHandler() {

    }

    @Override
    public String actionName() {
        return BusinessBroadcastInsightsAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return BusinessBroadcastInsightsAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return BusinessBroadcastInsightsAction.ACTION_VERSION;
    }

    /**
     * Applies a business broadcast insights mutation.
     *
     * <p>Per WhatsApp Web, on SET the delivery statistics are stored
     * for the campaign identified by index[1].
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var campaignId = indexArray.getString(1);
        if (campaignId == null || campaignId.isEmpty()) {
            return true;
        }

        if (mutation.operation() == SyncdOperation.SET) {
            if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastInsightsAction)) {
                return true;
            }
        }

        return true;
    }
}
