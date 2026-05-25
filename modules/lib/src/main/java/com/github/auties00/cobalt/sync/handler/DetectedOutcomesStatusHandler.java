package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.DetectedOutcomesStatusAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code detectedOutcomeStatus} app-state sync action that
 * carries the click-to-WhatsApp-ad detected-outcome onboarding flag.
 *
 * <p>This handler drives the CTWA "detected outcome" onboarding banner on
 * companion devices: when a business completes the primary-device onboarding
 * flow the resulting {@code isEnabled} bit fans out across the
 * {@link SyncPatchType#REGULAR} collection so every linked surface can render
 * the matching state.
 *
 * @implNote
 * This implementation persists the bit through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setDetectedOutcomesEnabled(boolean)}
 * instead of WA Web's
 * {@code frontendSendAndReceive("ctwaDetectedOutcomeOnboardingStatusUpdate")}
 * RPC, since Cobalt has no JS frontend to notify. The malformed-on-null
 * check is preserved at the action-instance level; per the project's
 * "no Optional&lt;Boolean&gt;" rule, a {@code null} {@code isEnabled}
 * field on a present action coalesces to {@code false} rather than
 * triggering {@link MutationApplicationResult#malformed()}.
 */
@WhatsAppWebModule(moduleName = "WAWebDetectedOutcomesStatusSync")
public final class DetectedOutcomesStatusHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link DetectedOutcomesStatusHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebDetectedOutcomesStatusSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public DetectedOutcomesStatusHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDetectedOutcomesStatusSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return DetectedOutcomesStatusAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDetectedOutcomesStatusSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return DetectedOutcomesStatusAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDetectedOutcomesStatusSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return DetectedOutcomesStatusAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation lets exceptions propagate to the orchestrator
     * rather than catching them inline as
     * {@link com.github.auties00.cobalt.model.sync.SyncActionState#FAILED}
     * the way WA Web's per-mutation closure does, matching Cobalt's
     * pluggable error model. The malformed and unsupported batch tallies
     * that WA Web emits via {@code WALogger.WARN} after the loop are
     * intentionally not recreated.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDetectedOutcomesStatusSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof DetectedOutcomesStatusAction action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setDetectedOutcomesEnabled(action.isEnabled());
        return MutationApplicationResult.success();
    }
}
