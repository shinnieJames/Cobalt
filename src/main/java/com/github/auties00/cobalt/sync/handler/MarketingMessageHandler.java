package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles marketing message actions.
 *
 * <p>This handler processes mutations related to marketing message preferences.
 * The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["marketingMessage"]
 */
public final class MarketingMessageHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code MarketingMessageHandler}.
     */
    public static final MarketingMessageHandler INSTANCE = new MarketingMessageHandler();

    private MarketingMessageHandler() {

    }

    @Override
    public String actionName() {
        return "marketingMessage";
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
