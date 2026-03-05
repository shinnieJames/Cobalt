package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles payment info actions.
 *
 * <p>This handler processes mutations related to payment information settings.
 * The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["payment_info"]
 */
public final class PaymentInfoHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code PaymentInfoHandler}.
     */
    public static final PaymentInfoHandler INSTANCE = new PaymentInfoHandler();

    private PaymentInfoHandler() {

    }

    @Override
    public String actionName() {
        return "payment_info";
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
