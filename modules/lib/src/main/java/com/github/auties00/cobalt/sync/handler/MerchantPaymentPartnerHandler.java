package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles merchant payment partner sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebMerchantPaymentPartnerSync}, this handler
 * processes the {@code "merchant_payment_partner"} sync action in the
 * {@code RegularLow} collection at version {@code 7}. The handler is
 * restricted to SMB (Small/Medium Business) platforms with the
 * {@code payments_br_merchant_psp_account_status_sync} AB prop enabled, and
 * only {@code SET} operations are supported.
 *
 * <p>On {@code SET}, validates that {@code merchantPaymentPartnerAction} is
 * non-{@code null} and persists the partner to the store via
 * {@code setMerchantPaymentPartner}.
 *
 * <p>Index format: {@code ["merchant_payment_partner"]}
 */
public final class MerchantPaymentPartnerHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Creates a new {@code MerchantPaymentPartnerHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    public MerchantPaymentPartnerHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for merchant payment partner mutations.
     * @return the action name {@code "merchant_payment_partner"}
     */
    @Override
    public String actionName() {
        return MerchantPaymentPartnerAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for merchant payment partner mutations.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    public SyncPatchType collectionName() {
        return MerchantPaymentPartnerAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for merchant payment partner mutations.
     * @return {@code 7}
     */
    @Override
    public int version() {
        return MerchantPaymentPartnerAction.ACTION_VERSION;
    }

    /**
     * Applies a single merchant payment partner mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebMerchantPaymentPartnerSync.applyMutations}:
     * <ol>
     *   <li>If the platform is not SMB ({@code isSMB() !== true}), returns
     *       {@code Unsupported} (WA Web logs a WARN and returns
     *       {@code Unsupported} for the entire batch).</li>
     *   <li>If the AB prop
     *       {@code payments_br_merchant_psp_account_status_sync} is not
     *       {@code true}, returns {@code Unsupported} (WA Web logs a WARN
     *       and returns {@code Unsupported} for the entire batch).</li>
     *   <li>If the operation is not {@code "set"}, returns {@code Unsupported}
     *       (WA Web increments an unsupported-count warning at end of batch).</li>
     *   <li>If {@code mutation.value.merchantPaymentPartnerAction} is
     *       {@code null}, returns {@code Malformed} via
     *       {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}
     *       (WA Web increments a malformed-count warning at end of batch).</li>
     *   <li>Otherwise calls
     *       {@code WAWebUserPrefsMerchantPaymentPartner.setMerchantPaymentPartner(action)}
     *       and returns {@code Success}.</li>
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
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var platform = client.store().device().platform(); // ADAPTED: WAWebMobilePlatforms.isSMB — checks c === u.SMBA || c === u.SMBI where SMBA = "smba" (ANDROID_BUSINESS) and SMBI = "smbi" (IOS_BUSINESS)
        if (platform != ClientPlatformType.IOS_BUSINESS && platform != ClientPlatformType.ANDROID_BUSINESS) {
            return MutationApplicationResult.unsupported();
        }

        if (!abPropsService.getBool(ABProp.PAYMENTS_BR_MERCHANT_PSP_ACCOUNT_STATUS_SYNC)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MerchantPaymentPartnerAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setMerchantPaymentPartner(action); // ADAPTED: WAWebUserPrefsMerchantPaymentPartner.setMerchantPaymentPartner -> WhatsAppStore.setMerchantPaymentPartner
        return MutationApplicationResult.success();
    }
}
