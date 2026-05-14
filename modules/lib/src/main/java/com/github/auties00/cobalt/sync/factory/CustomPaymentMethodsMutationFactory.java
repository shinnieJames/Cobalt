package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing custom-payment-methods sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.CustomPaymentMethodsHandler}.
 */
public final class CustomPaymentMethodsMutationFactory {
    /**
     * Constructs a custom-payment-methods mutation factory.
     */
    public CustomPaymentMethodsMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for custom payment methods.
     *
     * <p>Per WhatsApp Web {@code WAWebCustomPaymentMethodsSync.getCustomPaymentMethodSetMutation}:
     * <ol>
     *   <li>Captures the current time via {@code WATimeUtils.unixTimeMs()}</li>
     *   <li>Wraps the action in a value object:
     *       {@code {customPaymentMethodsAction: action}}</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       collection={@code RegularLow}, indexArgs={@code []},
     *       operation={@code SET}, version={@code 7},
     *       action={@code "custom_payment_methods"}</li>
     * </ol>
     *
     * @param action the custom payment methods action to build the mutation for
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "getCustomPaymentMethodSetMutation", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPendingMutation getCustomPaymentMethodSetMutation(CustomPaymentMethodsAction action) {
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .customPaymentMethodsAction(action)
                .build();
        var index = JSON.toJSONString(List.of(CustomPaymentMethodsAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                CustomPaymentMethodsAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
