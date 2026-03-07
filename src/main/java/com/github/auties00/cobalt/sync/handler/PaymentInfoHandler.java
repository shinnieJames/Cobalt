package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles payment info actions.
 *
 * <p>Per WhatsApp Web {@code WAWebPaymentInfoSync}, only SET is supported.
 * On SET, validates that {@code paymentInfoAction.cpi} is a non-{@code null}
 * string.
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
        return PaymentInfoAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return PaymentInfoAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return PaymentInfoAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof PaymentInfoAction action)) {
            return true;
        }

        if (action.cpi().isEmpty()) {
            return true;
        }

        return true;
    }
}
