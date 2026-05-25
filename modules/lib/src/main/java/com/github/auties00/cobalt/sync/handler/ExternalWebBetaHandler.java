package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.ExternalWebBetaAction;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code external_web_beta} app-state sync action that toggles the
 * user's enrolment in the WhatsApp Web external beta programme.
 *
 * <p>The action carries a single boolean fanned out across the
 * {@link SyncPatchType#REGULAR} collection so every linked surface stops or
 * starts pulling beta builds. The handler is gated by the
 * {@link ABProp#EXTERNAL_BETA_CAN_JOIN} A/B prop; while the prop is off every
 * mutation is reported as {@link MutationApplicationResult#unsupported()}
 * regardless of payload.
 *
 * @implNote
 * This implementation persists the bit through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setExternalWebBeta(boolean)}
 * and performs none of the backend restart, A/B-prop refresh or telemetry that
 * the build-channel negotiation would otherwise trigger, because a Cobalt
 * embedder does not negotiate its build channel through Meta's update service.
 */
@WhatsAppWebModule(moduleName = "WAWebExternalWebBetaSync")
public final class ExternalWebBetaHandler implements WebAppStateActionHandler {
    /**
     * The {@link ABPropsService} consulted before every mutation to gate the
     * handler on {@link ABProp#EXTERNAL_BETA_CAN_JOIN}.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs an {@link ExternalWebBetaHandler} bound to the given A/B-props
     * service.
     *
     * @param abPropsService the A/B-props service consulted on every mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ExternalWebBetaHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ExternalWebBetaAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ExternalWebBetaAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebExternalWebBetaSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ExternalWebBetaAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads {@link ABProp#EXTERNAL_BETA_CAN_JOIN} first and short-circuits
     * the mutation as {@link MutationApplicationResult#unsupported()} when the
     * flag is off; non-{@link SyncdOperation#SET} operations are also reported
     * as unsupported and a missing or mistyped action payload yields a
     * malformed result. When the flag is on the value is persisted through
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setExternalWebBeta(boolean)}.
     *
     * @implNote
     * This implementation re-reads the A/B prop on every mutation rather than
     * caching it, so a server-side prop flip reaches the next incoming sync
     * without restarting the client.
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

        client.store().setExternalWebBeta(action.isOptIn());
        return MutationApplicationResult.success();
    }
}
