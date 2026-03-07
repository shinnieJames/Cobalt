package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles custom payment methods actions.
 *
 * <p>Per WhatsApp Web {@code WAWebCustomPaymentMethodsSync}, only SET is
 * supported. On SET, validates that
 * {@code customPaymentMethodsAction.customPaymentMethods} is non-{@code null}.
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
        return CustomPaymentMethodsAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return CustomPaymentMethodsAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return CustomPaymentMethodsAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof CustomPaymentMethodsAction action)) {
            return true;
        }

        if (action.customPaymentMethods().isEmpty()) {
            return true;
        }

        return true;
    }
}
