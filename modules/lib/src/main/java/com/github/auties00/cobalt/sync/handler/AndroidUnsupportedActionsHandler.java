package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.AndroidUnsupportedActions;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Tracks the primary device's permission for companions to ship mutations the primary cannot interpret.
 *
 * <p>A WA Android primary device running an older release that does not
 * understand a newer mutation type can still grant companions permission to
 * author such mutations rather than have the server gate them out. This
 * handler reads the {@link AndroidUnsupportedActions#allowed()} flag from each
 * {@code android_unsupported_actions} mutation and, when it is {@code true},
 * latches the
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#primaryAllowsAllMutations()}
 * flag so the rest of Cobalt's sync engine knows it is safe to author action
 * types the primary may not yet understand.
 *
 * @implNote
 * This implementation latches the flag once on the first observed
 * {@code allowed = true}; further mutations that would re-set it are
 * no-ops, matching WA Web's
 * {@code getPrimaryAllowsAllMutations()}-gated update.
 */
@WhatsAppWebModule(moduleName = "WAWebAndroidUnsupportedActionsSync")
public final class AndroidUnsupportedActionsHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to record the one-shot flag transition.
     *
     * <p>Records a line when the latch first transitions from unset to set.
     */
    private static final Logger LOGGER = Logger.getLogger(AndroidUnsupportedActionsHandler.class.getName());

    /**
     * Constructs the singleton android-unsupported-actions handler.
     *
     * <p>The sync handler registry instantiates this type exactly once.
     */
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AndroidUnsupportedActionsHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AndroidUnsupportedActions.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AndroidUnsupportedActions.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AndroidUnsupportedActions.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the {@link AndroidUnsupportedActions#allowed()} flag from the
     * mutation value and, when it is {@code true}, calls
     * {@link #updatePrimaryAllowsAllMutationsFlag(WhatsAppClient)}. Non-{@link SyncdOperation#SET}
     * operations are reported as {@link MutationApplicationResult#unsupported()};
     * a missing or mistyped action payload is reported as malformed via
     * {@link SyncdIndexUtils#malformedActionValue(String)}; any thrown exception
     * is reported as {@link MutationApplicationResult#failed()}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            if (!(mutation.value().action().orElse(null) instanceof AndroidUnsupportedActions action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            if (action.allowed()) {
                updatePrimaryAllowsAllMutationsFlag(client);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Latches the {@code primaryAllowsAllMutations} store flag if it is not already set.
     *
     * <p>Invoked from {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * when the incoming mutation's {@code allowed} flag is {@code true}.
     *
     * @implNote
     * This implementation reads the current value first and only writes
     * (and emits a log line) when the flag transitions from unset to
     * set, mirroring WA Web's
     * {@code getPrimaryAllowsAllMutations() || setPrimaryAllowsAllMutations()}
     * guard.
     *
     * @param client the {@link WhatsAppClient} whose store hosts the flag
     */
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private void updatePrimaryAllowsAllMutationsFlag(WhatsAppClient client) {
        if (!client.store().primaryAllowsAllMutations()) {
            LOGGER.info("[syncd] primary allows all mutations flag set: allow_unsupported_mutation");
            client.store().setPrimaryAllowsAllMutations(true);
        }
    }
}
