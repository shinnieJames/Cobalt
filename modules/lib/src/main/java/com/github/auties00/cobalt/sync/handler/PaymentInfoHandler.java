package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles payment info sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebPaymentInfoSync}, this handler processes
 * the {@code "payment_info"} sync action in the {@code RegularLow} collection
 * at version {@code 7}. The handler is restricted to SMB (Small/Medium
 * Business) platforms with the
 * {@code order_details_payment_instructions_sync_enabled} AB prop enabled,
 * and only {@code SET} operations are supported.
 *
 * <p>On {@code SET}, validates that {@code paymentInfoAction.cpi} is a
 * non-{@code null} string and persists the CPI info to the store via
 * {@code setPaymentInstructionCpi}.
 *
 * <p>Index format: {@code ["payment_info"]}
 */
@WhatsAppWebModule(moduleName = "WAWebPaymentInfoSync")
public final class PaymentInfoHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new {@code PaymentInfoHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebPaymentInfoSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PaymentInfoHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for payment info mutations.
     * @return the action name {@code "payment_info"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentInfoSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PaymentInfoAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for payment info mutations.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentInfoSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PaymentInfoAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for payment info mutations.
     * @return {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentInfoSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PaymentInfoAction.ACTION_VERSION;
    }

    /**
     * Applies a single payment info mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebPaymentInfoSync.applyMutations}:
     * <ol>
     *   <li>If the platform is not SMB ({@code isSMB() !== true}), returns
     *       {@code Unsupported} (WA Web logs a WARN
     *       {@code "payment info sync: operation not supported, app is not SMB"}
     *       and returns {@code Unsupported} for the entire batch).</li>
     *   <li>If the AB prop
     *       {@code order_details_payment_instructions_sync_enabled} is not
     *       {@code true}, returns {@code Unsupported} (WA Web logs a WARN
     *       {@code "payment info sync: unsupported, ABProp not passed"}
     *       and returns {@code Unsupported} for the entire batch).</li>
     *   <li>If the operation is not {@code "set"}, returns {@code Unsupported}
     *       (WA Web increments the {@code r} counter and at end of batch logs
     *       {@code "payment info sync: <r> operations not supported"}).</li>
     *   <li>If {@code mutation.value.paymentInfoAction?.cpi} is not a
     *       {@code string}, returns {@code Malformed} via
     *       {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}
     *       (WA Web increments the {@code a} counter and at end of batch
     *       logs {@code "cpi payment info sync: <a> malformed mutations"}).</li>
     *   <li>Otherwise calls
     *       {@code WAWebBackendApi.frontendFireAndForget("setCPIInfo", {cpiInfo: i})}
     *       which routes via {@code WAWebPaymentInfoSyncBridgeApi.setCPIInfo}
     *       to {@code WAWebPaymentInfo.PaymentInfo.setCPIInfo} which diffs
     *       against the current value, calls
     *       {@code WAWebUserPrefsPaymentInfo.setCPIInfo(n)}, and triggers
     *       the {@code CPI_INFO_CHANGE_EVENT}. Returns {@code Success}.</li>
     * </ol>
     *
     * <p>WA Web's {@code WALogger.WARN} calls for the unsupported/malformed
     * batch counters and the SMB/ABProp gate failures are intentionally
     * omitted in Cobalt; the return semantics are preserved exactly. The
     * diff-against-current and event-emission logic in
     * {@code WAWebPaymentInfo.setCPIInfo} is collapsed into the single
     * store setter {@link com.github.auties00.cobalt.store.WhatsAppStore#setPaymentInstructionCpi(String)}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPaymentInfoSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var platform = client.store().device().platform(); // ADAPTED: WAWebMobilePlatforms.isSMB — checks c === u.SMBA || c === u.SMBI where SMBA = "smba" (ANDROID_BUSINESS) and SMBI = "smbi" (IOS_BUSINESS)
        if (platform != ClientPlatformType.IOS_BUSINESS && platform != ClientPlatformType.ANDROID_BUSINESS) {
            return MutationApplicationResult.unsupported();
        }

        if (!abPropsService.getBool(ABProp.ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PaymentInfoAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var cpi = action.cpi().orElse(null);
        if (cpi == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setPaymentInstructionCpi(cpi); // ADAPTED: WAWebBackendApi.frontendFireAndForget("setCPIInfo") -> WAWebPaymentInfoSyncBridgeApi.setCPIInfo -> WAWebPaymentInfo.setCPIInfo -> WAWebUserPrefsPaymentInfo.setCPIInfo collapsed into WhatsAppStore.setPaymentInstructionCpi
        return MutationApplicationResult.success();
    }
}
