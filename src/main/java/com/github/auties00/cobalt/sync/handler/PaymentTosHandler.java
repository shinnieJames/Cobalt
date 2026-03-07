package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles payment terms of service actions.
 *
 * <p>Per WhatsApp Web {@code WAWebPaymentTosSync}, only SET is supported.
 * On SET, validates that {@code paymentTosAction} is non-{@code null}.
 *
 * <p>Index format: ["payment_tos"]
 */
public final class PaymentTosHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code PaymentTosHandler}.
     */
    public static final PaymentTosHandler INSTANCE = new PaymentTosHandler();

    private PaymentTosHandler() {

    }

    @Override
    public String actionName() {
        return PaymentTosAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return PaymentTosAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return PaymentTosAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof PaymentTosAction)) {
            return true;
        }

        return true;
    }
}
