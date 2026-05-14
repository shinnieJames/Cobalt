package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing payment-TOS sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.PaymentTosHandler}.
 */
public final class PaymentTosMutationFactory {
    /**
     * Constructs a payment-TOS mutation factory.
     */
    public PaymentTosMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for payment terms of service.
     *
     * <p>Per WhatsApp Web {@code WAWebPaymentTosSync.getPaymentTosSetMutation}:
     * <ol>
     *   <li>Captures the current time via {@code WATimeUtils.unixTimeMs()}</li>
     *   <li>Wraps the action in a value object:
     *       {@code {paymentTosAction: action}}</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       collection={@code RegularLow}, indexArgs={@code []},
     *       operation={@code SET}, version={@code 7},
     *       action={@code "payment_tos"}</li>
     * </ol>
     *
     * @param action the payment terms of service action to build the mutation for
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "getPaymentTosSetMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getPaymentTosSetMutation(PaymentTosAction action) {
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .paymentTosAction(action)
                .build();
        var index = JSON.toJSONString(List.of(PaymentTosAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                PaymentTosAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
