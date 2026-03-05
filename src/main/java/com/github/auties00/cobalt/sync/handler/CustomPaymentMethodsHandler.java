package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles custom payment methods actions.
 *
 * <p>This handler processes mutations related to custom payment method
 * configurations. The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["custom_payment_methods"]
 */
public final class CustomPaymentMethodsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code CustomPaymentMethodsHandler}.
     */
    public static final CustomPaymentMethodsHandler INSTANCE = new CustomPaymentMethodsHandler();

    private CustomPaymentMethodsHandler() {

    }

    @Override
    public String actionName() {
        return "custom_payment_methods";
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
