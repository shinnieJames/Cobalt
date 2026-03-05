package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles marketing message broadcast actions.
 *
 * <p>This handler processes mutations related to marketing message broadcast
 * preferences. The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["marketingMessageBroadcast"]
 */
public final class MarketingMessageBroadcastHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code MarketingMessageBroadcastHandler}.
     */
    public static final MarketingMessageBroadcastHandler INSTANCE = new MarketingMessageBroadcastHandler();

    private MarketingMessageBroadcastHandler() {

    }

    @Override
    public String actionName() {
        return "marketingMessageBroadcast";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
