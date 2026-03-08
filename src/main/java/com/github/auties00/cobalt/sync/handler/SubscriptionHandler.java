package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.SubscriptionAction;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles subscription actions.
 *
 * <p>Index format: ["subscription"]
 */
public final class SubscriptionHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code SubscriptionHandler}.
     */
    public static final SubscriptionHandler INSTANCE = new SubscriptionHandler();

    private SubscriptionHandler() {

    }

    @Override
    public String actionName() {
        return SubscriptionAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SubscriptionAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return SubscriptionAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!(mutation.value().action().orElse(null) instanceof SubscriptionAction action)) {
            return true;
        }

        client.store()
                .setSubscriptionDeactivated(action.isDeactivated())
                .setSubscriptionAutoRenewing(action.isAutoRenewing())
                .setSubscriptionExpirationDate(action.expirationDate().isPresent() ? action.expirationDate().getAsLong() : null);
        return true;
    }
}
