package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles CTWA per-customer data sharing actions.
 *
 * <p>Index format: ["ctwaPerCustomerDataSharing", ...]
 */
public final class CtwaPerCustomerDataSharingHandler implements WebAppStateActionHandler {
    public static final CtwaPerCustomerDataSharingHandler INSTANCE = new CtwaPerCustomerDataSharingHandler();

    private CtwaPerCustomerDataSharingHandler() {

    }

    @Override
    public String actionName() {
        return "ctwaPerCustomerDataSharing";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
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
