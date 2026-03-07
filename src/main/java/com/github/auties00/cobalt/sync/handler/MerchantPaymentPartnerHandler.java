package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles merchant payment partner actions.
 *
 * <p>Per WhatsApp Web {@code WAWebMerchantPaymentPartnerSync}, only SET is
 * supported. On SET, validates that {@code merchantPaymentPartnerAction} is
 * non-{@code null}.
 *
 * <p>Index format: ["merchant_payment_partner"]
 */
public final class MerchantPaymentPartnerHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code MerchantPaymentPartnerHandler}.
     */
    public static final MerchantPaymentPartnerHandler INSTANCE = new MerchantPaymentPartnerHandler();

    private MerchantPaymentPartnerHandler() {

    }

    @Override
    public String actionName() {
        return MerchantPaymentPartnerAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return MerchantPaymentPartnerAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return MerchantPaymentPartnerAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof MerchantPaymentPartnerAction)) {
            return true;
        }

        return true;
    }
}
