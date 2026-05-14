package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles payment terms of service sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebPaymentTosSync}, this handler processes the
 * {@code "payment_tos"} sync action in the {@code RegularLow} collection at
 * version {@code 7}. The handler is restricted to SMB (Small/Medium Business)
 * platforms with the {@code payments_br_pix_on_web} AB prop enabled, and only
 * {@code SET} operations are supported.
 *
 * <p>On {@code SET}, validates that {@code paymentTosAction} is non-{@code null}
 * and persists the accepted payment terms of service to the store via
 * {@code setPaymentTos}.
 *
 * <p>Index format: {@code ["payment_tos"]}
 */
@WhatsAppWebModule(moduleName = "WAWebPaymentTosSync")
public final class PaymentTosHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new {@code PaymentTosHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PaymentTosHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for payment terms of service mutations.
     * @return the action name {@code "payment_tos"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PaymentTosAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for payment terms of service mutations.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PaymentTosAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for payment terms of service mutations.
     * @return {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PaymentTosAction.ACTION_VERSION;
    }

    /**
     * Applies a single payment terms of service mutation and returns a
     * detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebPaymentTosSync.applyMutations}:
     * <ol>
     *   <li>If the platform is not SMB ({@code isSMB() !== true}), returns
     *       {@code Unsupported} (WA Web logs a WARN "Payment Tos sync:
     *       operation not supported, app is not SMB" and returns
     *       {@code Unsupported} for the entire batch).</li>
     *   <li>If the AB prop {@code payments_br_pix_on_web} is not {@code true},
     *       returns {@code Unsupported} (WA Web logs a WARN "Payment Tos sync:
     *       unsupported, ABProp check failed" and returns {@code Unsupported}
     *       for the entire batch).</li>
     *   <li>If the operation is not {@code "set"}, returns {@code Unsupported}
     *       (WA Web increments an unsupported-count warning at end of batch).</li>
     *   <li>If {@code mutation.value.paymentTosAction} is {@code null},
     *       returns {@code Malformed} via
     *       {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}
     *       (WA Web increments a malformed-count warning at end of batch).</li>
     *   <li>Otherwise calls
     *       {@code WAWebUserPrefsPaymentTos.setPaymentTos(action)} and returns
     *       {@code Success}.</li>
     * </ol>
     *
     * <p>WA Web's {@code WALogger.WARN} calls for the unsupported/malformed
     * batch counters and the SMB/ABProp gate failures are intentionally
     * omitted in Cobalt; the return semantics are preserved exactly.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentTosSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var platform = client.store().device().platform(); // ADAPTED: WAWebMobilePlatforms.isSMB — checks c === u.SMBA || c === u.SMBI where SMBA = "smba" (ANDROID_BUSINESS) and SMBI = "smbi" (IOS_BUSINESS)
        if (platform != ClientPlatformType.IOS_BUSINESS && platform != ClientPlatformType.ANDROID_BUSINESS) {
            return MutationApplicationResult.unsupported();
        }

        if (!abPropsService.getBool(ABProp.PAYMENTS_BR_PIX_ON_WEB)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PaymentTosAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setPaymentTos(action); // ADAPTED: WAWebUserPrefsPaymentTos.setPaymentTos -> WhatsAppStore.setPaymentTos
        return MutationApplicationResult.success();
    }

}
