package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Handles sentinel actions for sync key expiration.
 *
 * <p>The sentinel is a special sync action used as a keepalive/liveness check
 * for the app state sync key subsystem. It creates SET mutations with the
 * current key ID and timestamp, used to verify that sync keys are working
 * correctly. The handler's apply method expires sync keys matching the
 * sentinel's expired key epoch. The set method creates sentinel mutations
 * for all collections with the active key's fingerprint data.
 *
 * <p>Per WhatsApp Web, the sentinel handler extends {@code AccountSyncdActionBase}
 * with collection name {@code RegularLow}, version {@code 3}, and action
 * {@code "sentinel"}.
 */
@WhatsAppWebModule(moduleName = "WAWebSentinelMutationSync")
public final class SentinelHandler implements WebAppStateActionHandler {
    /**
     * Logger for sentinel mutation sync operations.
     */
    private static final Logger LOGGER = Logger.getLogger(SentinelHandler.class.getName());

    /**
     * Constructs the singleton sentinel handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SentinelHandler() {

    }

    /**
     * Returns the action name for sentinel mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync.getAction()}: returns
     * {@code WASyncdConst.Actions.Sentinel} which resolves to {@code "sentinel"}.
     * @return the sentinel action name {@code "sentinel"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return KeyExpirationAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for sentinel mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync}: the constructor sets
     * {@code this.collectionName = CollectionName.RegularLow} which resolves to
     * {@code "regular_low"}.
     * @return the sync patch type {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return KeyExpirationAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for sentinel mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync.getVersion()}: returns {@code 3}.
     * @return the version number {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return KeyExpirationAction.ACTION_VERSION;
    }

    /**
     * Applies a single sentinel mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinelMutationSync.applyMutations}: for each
     * mutation in the batch:
     * <ul>
     *   <li>If operation is {@code "set"}: extracts {@code value.keyExpiration.expiredKeyEpoch}.
     *       If the epoch is {@code null}, increments a malformed counter and returns
     *       {@code malformedActionValue(collectionName)}. Otherwise, calls
     *       {@code WAWebGetSyncKey.expireSyncKeyInTransaction(epoch)} and returns
     *       {@code {actionState: Success}}.</li>
     *   <li>For any other operation: increments an unsupported counter and returns
     *       {@code {actionState: Unsupported}}.</li>
     *   <li>On exception: returns {@code {actionState: Failed}}.</li>
     * </ul>
     * @param client   the WhatsApp client instance
     * @param mutation the sentinel mutation to apply
     * @return the mutation application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof KeyExpirationAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var expiredEpoch = action.expiredKeyEpoch();
        if (expiredEpoch.isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().expireAppStateKeysByEpoch(expiredEpoch.getAsInt());
        return MutationApplicationResult.success();
    }

}
