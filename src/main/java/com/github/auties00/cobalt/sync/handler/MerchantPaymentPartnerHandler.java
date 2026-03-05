package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles merchant payment partner actions.
 *
 * <p>Index format: ["merchant_payment_partner", ...]
 */
public final class MerchantPaymentPartnerHandler implements WebAppStateActionHandler {
    public static final MerchantPaymentPartnerHandler INSTANCE = new MerchantPaymentPartnerHandler();

    private MerchantPaymentPartnerHandler() {

    }

    @Override
    public String actionName() {
        return "merchant_payment_partner";
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
