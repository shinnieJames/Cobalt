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
 * Handles NCT (Notification Content Tokenizer) salt sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebNctSaltSync}, the salt is used for
 * privacy-preserving notification content processing. On {@code set},
 * the salt bytes are persisted locally; on {@code remove}, the stored
 * salt is cleared. Any other operation is reported as {@code unsupported}.
 * A mutation whose {@code set} value is missing the {@code salt} field
 * is treated as a malformed action index.
 *
 * <p>Index format: {@code ["nct_salt_sync"]}.
 */
@WhatsAppWebModule(moduleName = "WAWebNctSaltSync")
public final class NctSaltSyncHandler implements WebAppStateActionHandler {
    /**
     * Logger for this handler.
     */
    private static final Logger LOGGER = Logger.getLogger(NctSaltSyncHandler.class.getName());

    /**
     * Constructs the singleton NCT salt sync handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public NctSaltSyncHandler() {

    }

    /**
     * Returns the action type name this handler processes.
     * @return the action type name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return NctSaltSyncAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the sync patch type / collection name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return NctSaltSyncAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the handler's supported mutation version
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return NctSaltSyncAction.ACTION_VERSION;
    }

    /**
     * Applies an NCT salt sync mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebNctSaltSync.$NctSaltSync$p_1} the logic is:
     * <ol>
     *   <li>If {@code operation === "remove"}: log, clear the stored NCT salt,
     *       and return {@code {actionState: Success}}.</li>
     *   <li>If {@code operation !== "set"}: log a warning and return
     *       {@code {actionState: Unsupported}}.</li>
     *   <li>Read {@code n = value?.nctSaltSyncAction?.salt}. If {@code n == null},
     *       log a warning and return {@code this.malformedActionIndex()}.</li>
     *   <li>Otherwise, base64-encode the salt, store it under
     *       {@code BACKEND_ONLY_KEYS.NCT_SALT}, log, and return
     *       {@code {actionState: Success}}.</li>
     * </ol>
     *
     * <p>In WA Web the salt is stored as a base64-encoded string in the
     * {@code userPrefsIdb} IndexedDB table because that storage layer only
     * holds JSON-compatible values. Cobalt's {@link com.github.auties00.cobalt.store.WhatsAppStore}
     * holds the raw {@code byte[]} directly, so the base64 round-trip is
     * elided as an architectural adaptation.
     * @param client   the {@link WhatsAppClient} instance linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNctSaltSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() == SyncdOperation.REMOVE) {
            LOGGER.fine("[nct-salt-sync] Removing stored NCT salt");
            //   yield WAWebUserPrefsIndexedDBStorage.userPrefsIdb.remove(BACKEND_ONLY_KEYS.NCT_SALT)
            // ADAPTED: Cobalt's WhatsAppStore holds the raw byte[]; clearing the field
            // is equivalent to removing the userPrefsIdb entry.
            client.store().setNotificationContentTokenSalt(null);
            return MutationApplicationResult.success();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            LOGGER.warning(() -> "[nct-salt-sync] Unsupported operation: " + mutation.operation());
            return MutationApplicationResult.unsupported();
        }

        //   var n = (t = e.value) == null || (t = t.nctSaltSyncAction) == null ? void 0 : t.salt
        // In Cobalt, a missing nctSaltSyncAction oneof variant is equivalent to
        // value?.nctSaltSyncAction being null, so the resulting salt is null and
        // the malformedActionIndex() branch is taken below.
        var salt = mutation.value().action()
                .filter(NctSaltSyncAction.class::isInstance)
                .map(NctSaltSyncAction.class::cast)
                .flatMap(NctSaltSyncAction::salt)
                .orElse(null);
        if (salt == null) {
            LOGGER.warning("[nct-salt-sync] Missing salt in nctSaltSyncAction");
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        //   yield userPrefsIdb.set(BACKEND_ONLY_KEYS.NCT_SALT, r)
        // ADAPTED: Cobalt's WhatsAppStore persists the raw byte[]; the base64
        // round-trip is a WA Web userPrefsIdb serialization requirement and has
        // no equivalent in a typed in-memory store.
        client.store().setNotificationContentTokenSalt(salt);
        LOGGER.fine("[nct-salt-sync] Stored NCT salt");
        return MutationApplicationResult.success();
    }
}
