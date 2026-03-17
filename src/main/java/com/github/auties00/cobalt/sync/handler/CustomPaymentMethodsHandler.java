package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.props.ABProp;
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
        return applyMutationResult(client, mutation).actionState() == com.github.auties00.cobalt.model.sync.SyncActionState.SUCCESS;
    }

    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var platform = client.store().device().platform();
        if(platform != ClientPlatformType.IOS_BUSINESS && platform != ClientPlatformType.ANDROID_BUSINESS) {
            return MutationApplicationResult.unsupported();
        }

        if (!client.abPropsService().getBool(ABProp.PAYMENTS_BR_PIX_PHASE_1_SELLER_SYNC_ENABLED)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof CustomPaymentMethodsAction action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setCustomPaymentMethods(action.customPaymentMethods());
        return MutationApplicationResult.success();
    }
}
