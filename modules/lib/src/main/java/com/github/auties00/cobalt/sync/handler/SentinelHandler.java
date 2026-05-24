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
 * Expires retired app-state-sync keys when the primary device announces a
 * new key epoch via the {@code "sentinel"} mutation.
 *
 * @apiNote
 * Cobalt embedders never call this directly; the sync dispatcher hands an
 * incoming sentinel mutation here after the primary device rotates its
 * sync key (typical trigger: a primary-device key-rotation tick) so the
 * companion drops the matching local app-state-sync key from
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} and forces a
 * re-keyed patch to be requested on the next sync.
 */
@WhatsAppWebModule(moduleName = "WAWebSentinelMutationSync")
public final class SentinelHandler implements WebAppStateActionHandler {
    /**
     * The logger used for diagnostic output from sentinel handling.
     */
    private static final Logger LOGGER = Logger.getLogger(SentinelHandler.class.getName());

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SentinelHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return KeyExpirationAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return KeyExpirationAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSentinelMutationSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return KeyExpirationAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebSentinelMutationSync.applyMutations}: non-{@code SET}
     * operations are unsupported; a {@code SET} whose decoded action is
     * not a {@link KeyExpirationAction} or whose {@code expiredKeyEpoch}
     * is empty is malformed; otherwise the named epoch is expired on the
     * local store via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#expireAppStateKeysByEpoch(int)}.
     * The WA Web {@code WALogger.ERROR}/{@code WARN} aggregation of the
     * malformed and unsupported counters is omitted as telemetry, and the
     * outer {@code try/catch} that maps any throw to
     * {@link MutationApplicationResult#failed()} is dropped per the Cobalt
     * error model: thrown exceptions surface to the configured
     * {@link com.github.auties00.cobalt.exception.WhatsAppClientErrorHandler}.
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
