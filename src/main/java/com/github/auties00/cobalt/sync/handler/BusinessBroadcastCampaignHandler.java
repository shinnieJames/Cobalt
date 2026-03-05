package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles business broadcast campaign actions.
 *
 * <p>Index format: ["business_broadcast_campaign", ...]
 */
public final class BusinessBroadcastCampaignHandler implements WebAppStateActionHandler {
    public static final BusinessBroadcastCampaignHandler INSTANCE = new BusinessBroadcastCampaignHandler();

    private BusinessBroadcastCampaignHandler() {

    }

    @Override
    public String actionName() {
        return "business_broadcast_campaign";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
