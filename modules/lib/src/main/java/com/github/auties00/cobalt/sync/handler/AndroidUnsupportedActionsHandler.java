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
 * Handles Android unsupported actions sync mutations.
 *
 * <p>This handler processes mutations that declare which actions are unsupported
 * on Android devices. On SET, reads the {@code androidUnsupportedActions} field from the
 * mutation value. If the {@code allowed} flag is {@code true}, updates the primary
 * allows-all-mutations flag in the store (only if not already set). Other operations
 * are acknowledged as unsupported.
 *
 * <p>Index format: ["android_unsupported_actions"]
 */
@WhatsAppWebModule(moduleName = "WAWebAndroidUnsupportedActionsSync")
public final class AndroidUnsupportedActionsHandler implements WebAppStateActionHandler {
    /**
     * Logger for this handler.
     */
    private static final Logger LOGGER = Logger.getLogger(AndroidUnsupportedActionsHandler.class.getName());

    /**
     * Constructs the singleton handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AndroidUnsupportedActionsHandler() {

    }

    /**
     * Returns the action name for this handler.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AndroidUnsupportedActions.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AndroidUnsupportedActions.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AndroidUnsupportedActions.ACTION_VERSION;
    }

    /**
     * Applies a single mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web, the per-mutation logic within {@code applyMutations}:
     * <ul>
     *   <li>If operation is not SET: returns {@code {actionState: Unsupported}}</li>
     *   <li>If operation is SET but {@code androidUnsupportedActions} is falsy: returns
     *       malformed via {@code WAWebSyncdIndexUtils.malformedActionValue(collectionName)}</li>
     *   <li>If {@code allowed === true}: calls {@code updatePrimaryAllowsAllMutationsFlag}</li>
     *   <li>Returns {@code {actionState: Success}}</li>
     *   <li>On exception: returns {@code {actionState: Failed}}</li>
     * </ul>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
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
     * Updates the primary allows-all-mutations flag in the store if it is not already set.
     *
     * <p>Per WhatsApp Web {@code updatePrimaryAllowsAllMutationsFlag}: first checks
     * {@code getPrimaryAllowsAllMutations()} and only sets the flag (and logs) when it
     * is currently falsy. This avoids redundant updates and provides a log entry when
     * the flag transitions from unset to set.
     * @param client the WhatsApp client instance providing store access
     */
    @WhatsAppWebExport(moduleName = "WAWebAndroidUnsupportedActionsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private void updatePrimaryAllowsAllMutationsFlag(WhatsAppClient client) {
        if (!client.store().primaryAllowsAllMutations()) {
            LOGGER.info("[syncd] primary allows all mutations flag set: allow_unsupported_mutation");
            client.store().setPrimaryAllowsAllMutations(true);
        }
    }
}
