package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.NctSaltSyncAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Applies the {@code nct_salt_sync} app-state action that distributes the
 * Notification Content Tokenizer (NCT) salt across linked devices.
 *
 * @apiNote
 * Drives the privacy-preserving notification content pipeline: the salt
 * is consumed by NCT to tokenize the previewable content of incoming
 * notifications without leaking message text to the OS notification
 * surface. {@link SyncdOperation#REMOVE} clears the locally stored salt
 * via {@code WhatsAppStore.setNotificationContentTokenSalt(null)};
 * {@link SyncdOperation#SET} writes the new salt; any other operation
 * is reported as {@link MutationApplicationResult#unsupported()}. The
 * mutation index is the singleton {@snippet :
 *     ["nct_salt_sync"]
 * }
 *
 * @implNote
 * This implementation persists the raw salt {@code byte[]} directly on
 * the typed Cobalt store, rather than the base64-encoded string WA Web
 * stores in {@code userPrefsIdb} under the {@code BACKEND_ONLY_KEYS.NCT_SALT}
 * key; the base64 round-trip is a JSON-only-storage requirement of
 * IndexedDB and has no equivalent in a typed in-memory store. The
 * {@code WALogger.LOG/WARN} batch counters from
 * {@code WAWebNctSaltSync.applyMutations} are dropped; per-mutation
 * outcomes are surfaced through {@link MutationApplicationResult}.
 */
@WhatsAppWebModule(moduleName = "WAWebNctSaltSync")
public final class NctSaltSyncHandler implements WebAppStateActionHandler {
    /**
     * Logger used for diagnostic traces emitted by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     *
     * @apiNote
     * Internal logger; not exposed outside this class.
     *
     * @implNote
     * This implementation logs at {@code FINE} for the success/clear
     * paths and {@code WARNING} for the unsupported/missing-salt paths,
     * mirroring the WA Web {@code WALogger.LOG}/{@code WALogger.WARN}
     * granularity even though the per-batch counters are dropped.
     */
    private static final Logger LOGGER = Logger.getLogger(NctSaltSyncHandler.class.getName());

    /**
     * Constructs the singleton NCT salt sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; no AB-prop or store dependency
     * is held.
     */
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public NctSaltSyncHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return NctSaltSyncAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return NctSaltSyncAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return NctSaltSyncAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the per-mutation arm of
     * {@code WAWebNctSaltSync.$NctSaltSync$p_1}:
     * <ul>
     *   <li>{@link SyncdOperation#REMOVE} clears the stored salt and
     *       returns {@link MutationApplicationResult#success()};</li>
     *   <li>any operation other than {@link SyncdOperation#SET} returns
     *       {@link MutationApplicationResult#unsupported()};</li>
     *   <li>a missing
     *       {@link NctSaltSyncAction#salt()} returns
     *       {@link MutationApplicationResult#malformed()} via
     *       {@link SyncdIndexUtils#malformedActionIndex(String, String)};</li>
     *   <li>the resolved {@code byte[]} salt is written via
     *       {@code WhatsAppStore.setNotificationContentTokenSalt}.</li>
     * </ul>
     * The base64 encode/decode WA Web performs to round-trip the salt
     * through {@code userPrefsIdb} is elided; the typed Cobalt store
     * holds the raw bytes directly.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() == SyncdOperation.REMOVE) {
            LOGGER.fine("[nct-salt-sync] Removing stored NCT salt");
            client.store().setNotificationContentTokenSalt(null);
            return MutationApplicationResult.success();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            LOGGER.warning(() -> "[nct-salt-sync] Unsupported operation: " + mutation.operation());
            return MutationApplicationResult.unsupported();
        }

        var salt = mutation.value().action()
                .filter(NctSaltSyncAction.class::isInstance)
                .map(NctSaltSyncAction.class::cast)
                .flatMap(NctSaltSyncAction::salt)
                .orElse(null);
        if (salt == null) {
            LOGGER.warning("[nct-salt-sync] Missing salt in nctSaltSyncAction");
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        client.store().setNotificationContentTokenSalt(salt);
        LOGGER.fine("[nct-salt-sync] Stored NCT salt");
        return MutationApplicationResult.success();
    }
}
