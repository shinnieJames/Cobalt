package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles custom payment methods sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebCustomPaymentMethodsSync}, this handler
 * processes the {@code "custom_payment_methods"} sync action in the
 * {@code RegularLow} collection at version 7. Only SET operations are
 * supported, and the handler is restricted to SMB (Small/Medium Business)
 * platforms with the {@code payments_br_pix_phase_1_seller_sync_enabled}
 * AB prop enabled.
 *
 * <p>On SET, validates that
 * {@code customPaymentMethodsAction.customPaymentMethods} is non-{@code null},
 * then persists the custom payment methods to the store.
 *
 * <p>Index format: {@code ["custom_payment_methods"]}
 */
@WhatsAppWebModule(moduleName = "WAWebCustomPaymentMethodsSync")
public final class CustomPaymentMethodsHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new {@code CustomPaymentMethodsHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CustomPaymentMethodsHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for custom payment methods.
     * @return the action name {@code "custom_payment_methods"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CustomPaymentMethodsAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for custom payment methods.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CustomPaymentMethodsAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for custom payment methods.
     * @return {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CustomPaymentMethodsAction.ACTION_VERSION;
    }

    /**
     * Applies a single custom payment methods mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebCustomPaymentMethodsSync.applyMutations}:
     * <ol>
     *   <li>If the platform is not SMB ({@code isSMB() !== true}), returns
     *       {@code Unsupported} for all mutations in the batch.</li>
     *   <li>If the AB prop {@code payments_br_pix_phase_1_seller_sync_enabled}
     *       is not {@code true}, returns {@code Unsupported} for all mutations.</li>
     *   <li>If the operation is not {@code "set"}, returns {@code Unsupported}.</li>
     *   <li>If {@code customPaymentMethodsAction.customPaymentMethods} is
     *       {@code null}, returns {@code Malformed} via
     *       {@code WAWebSyncdIndexUtils.malformedActionValue}.</li>
     *   <li>Otherwise calls
     *       {@code WAWebBackendApi.frontendFireAndForget("setCustomPaymentMethods", ...)}
     *       and returns {@code Success}.</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomPaymentMethodsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var platform = client.store().device().platform(); // ADAPTED: WAWebMobilePlatforms.isSMB — checks c === u.SMBA || c === u.SMBI where SMBA = "smba" (ANDROID_BUSINESS) and SMBI = "smbi" (IOS_BUSINESS)
        if (platform != ClientPlatformType.IOS_BUSINESS && platform != ClientPlatformType.ANDROID_BUSINESS) {
            return MutationApplicationResult.unsupported();
        }

        if (!abPropsService.getBool(ABProp.PAYMENTS_BR_PIX_PHASE_1_SELLER_SYNC_ENABLED)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof CustomPaymentMethodsAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setCustomPaymentMethods(action.customPaymentMethods());
        return MutationApplicationResult.success();
    }

}
