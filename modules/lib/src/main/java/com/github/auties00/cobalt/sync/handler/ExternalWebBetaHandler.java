package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.ExternalWebBetaAction;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles external web beta sync actions.
 *
 * <p>This handler processes incoming mutations that control external web beta
 * enrollment status. On {@code SET}, reads the {@code isOptIn} flag from the
 * mutation value and updates the store. Other operations are acknowledged as
 * unsupported.
 *
 * <p>The action is identified by the {@code "external_web_beta"} action name in
 * {@code SyncActionValue.externalWebBetaAction}. The collection is
 * {@code Regular} and the version is {@code 3}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code AccountSyncdActionBase} and
 * gates on the {@code external_beta_can_join} AB prop. When disabled, all
 * mutations are returned as {@code Unsupported}.
 */
@WhatsAppWebModule(moduleName = "WAWebExternalWebBetaSync")
public final class ExternalWebBetaHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs the handler instance bound to the given AB-props
     * service.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ExternalWebBetaHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for external web beta actions.
     * @return the action name {@code "external_web_beta"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ExternalWebBetaAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for external web beta actions.
     * @return the sync patch type {@code REGULAR}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ExternalWebBetaAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for external web beta actions.
     * @return the version {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ExternalWebBetaAction.ACTION_VERSION;
    }

    /**
     * Applies a single external web beta mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web, the handler first checks if the {@code external_beta_can_join}
     * AB prop is enabled. If not, all mutations are returned as {@code Unsupported}.
     * For {@code SET} operations, the handler extracts the {@code externalWebBetaAction}
     * from the mutation value, validates its presence and the {@code isOptIn} field,
     * then calls {@code WAWebExternalBetaApi.changeOptInStatusForExternalWebBeta(isOptIn)}.
     * Non-{@code SET} operations are returned as {@code Unsupported}.
     *
     * <p>In Cobalt, the {@code changeOptInStatusForExternalWebBeta} call is adapted to
     * a direct store update via {@code setExternalWebBeta(boolean)}, since the API-level
     * side effects (backend restart, AB prop sync, WAM refresh) are not applicable.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.EXTERNAL_BETA_CAN_JOIN)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof ExternalWebBetaAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setExternalWebBeta(action.isOptIn()); // ADAPTED: WAWebExternalWebBetaSync.applyMutations -> WAWebExternalBetaApi.changeOptInStatusForExternalWebBeta(r.isOptIn)
        return MutationApplicationResult.success();
    }
}
