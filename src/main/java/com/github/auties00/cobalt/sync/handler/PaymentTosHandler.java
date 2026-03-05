package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles payment terms of service actions.
 *
 * <p>Index format: ["payment_tos", ...]
 */
public final class PaymentTosHandler implements WebAppStateActionHandler {
    public static final PaymentTosHandler INSTANCE = new PaymentTosHandler();

    private PaymentTosHandler() {

    }

    @Override
    public String actionName() {
        return "payment_tos";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
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
