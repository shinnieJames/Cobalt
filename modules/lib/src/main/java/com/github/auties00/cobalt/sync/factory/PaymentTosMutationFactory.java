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
 * @apiNote
 * Drives the SMB (WhatsApp Business) Brazil Pix payments terms-of-service
 * flow, which is the only WA Web surface that creates outgoing
 * payment-TOS mutations; consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.PaymentTosHandler} which
 * persists the action via {@code WAWebUserPrefsPaymentTos.setPaymentTos}.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebPaymentTosSync.getPaymentTosSetMutation}, which is invoked
 * from {@code WAWebPaymentsTosJob}; the receiver-side branch additionally
 * gates the application on the {@code payments_br_pix_on_web} AB prop
 * and on {@code WAWebMobilePlatforms.isSMB()}.
 */
public final class PaymentTosMutationFactory {
    /**
     * Constructs a payment-TOS mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the payments-TOS job. The factory keeps no state, so
     * a single instance is sufficient per client.
     */
    public PaymentTosMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the payment terms of service.
     *
     * @apiNote
     * Invoked from the payments-TOS acceptance flow; the caller passes a
     * fully populated {@link PaymentTosAction} and this method wraps it
     * into a pending mutation ready for
     * {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}.
     * The index carries only the action name because the action is a
     * singleton per account.
     *
     * @implNote
     * This implementation stamps {@link Instant#now()} on both the outer
     * mutation timestamp and the inner {@code SyncActionValue.timestamp},
     * matching {@code WAWebPaymentTosSync.getPaymentTosSetMutation}'s
     * single call to {@code WATimeUtils.unixTimeMs}.
     *
     * @param action the payment terms of service action to wrap into the
     *               outgoing mutation; the inner shape is forwarded
     *               verbatim to the receiver
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "getPaymentTosSetMutation", adaptation = WhatsAppAdaptation.DIRECT)
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
