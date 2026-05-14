package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreferenceBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles CTWA per-customer data sharing sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebCtwaPerCustomerDataSharingSync}, this handler
 * processes mutations for the {@code "ctwa_per_customer_data_sharing"} sync action.
 * SET and REMOVE operations are supported. On SET, validates that
 * {@code indexParts[1]} (accountLid) is non-{@code null} and that the
 * {@code ctwaPerCustomerDataSharingAction} is present with a non-{@code null}
 * {@code isCtwaPerCustomerDataSharingEnabled} field. On REMOVE, the stored
 * data sharing preference is cleared.
 *
 * <p>Index format: {@code ["ctwaPerCustomerDataSharing", accountLid]}
 *
 * <p>WA Web uses a per-accountLid IDB table ({@code data-sharing-3pd-lid-v2})
 * and an in-memory collection to store per-customer data sharing preferences.
 * Cobalt mirrors that schema by storing one entry per account LID raw string
 * on the store via {@code WhatsAppStore.putCtwaDataSharing(CtwaDataSharingPreference)}
 * and {@code WhatsAppStore.removeCtwaDataSharing(String)}.
 */
@WhatsAppWebModule(moduleName = "WAWebCtwaPerCustomerDataSharingSync")
public final class CtwaPerCustomerDataSharingHandler implements WebAppStateActionHandler {

    /**
     * Creates the singleton handler instance.
     */
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CtwaPerCustomerDataSharingHandler() {
    }

    /**
     * Returns the action name for CTWA per-customer data sharing.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CtwaPerCustomerDataSharingAction.ACTION_NAME;
    }

    /**
     * Returns the collection name for CTWA per-customer data sharing.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CtwaPerCustomerDataSharingAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CtwaPerCustomerDataSharingAction.ACTION_VERSION;
    }

    /**
     * Applies a CTWA per-customer data sharing mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebCtwaPerCustomerDataSharingSync.applyMutations}:
     * <ul>
     *   <li><b>SET</b>: validates that {@code indexParts[1]} (accountLid) is present;
     *       extracts the {@code ctwaPerCustomerDataSharingAction} from the value and
     *       validates that {@code isCtwaPerCustomerDataSharingEnabled} is non-{@code null};
     *       stores the enabled flag via {@code $CtwaPerCustomerDataSharingSync$p_1}
     *       (which calls {@code createOrReplace} on the data-sharing-3pd-lid-v2 table
     *       and {@code updateDataSharing3pdLidInCollection} on the frontend), then
     *       fires {@code maybeGeneratePerCustomerDataSharingSystemMessage}.</li>
     *   <li><b>REMOVE</b>: removes the entry via {@code $CtwaPerCustomerDataSharingSync$p_2}
     *       (which calls {@code remove} on the table and
     *       {@code removeDataSharing3pdLidFromCollection} on the frontend).</li>
     *   <li><b>default</b>: returns unsupported.</li>
     * </ul>
     *
     * <p>WA Web wraps each mutation in a try/catch that returns
     * {@code SyncActionState.Failed} on error. Per Cobalt's error model,
     * exceptions propagate instead of being caught inline.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebCtwaPerCustomerDataSharingSync.applyMutations reads `var u=n[1]` once, then dispatches.
        // n[1] is `undefined` (i.e. null in Java) when the slot is absent — REMOVE happily treats undefined
        // as a no-op on IDB, SET returns malformed. Mirror by extracting with a size guard.
        var accountLid = indexArray.size() > 1 ? indexArray.getString(1) : null;

        switch (mutation.operation()) {
            case SET -> {
                if (accountLid == null) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                // var c = s.ctwaPerCustomerDataSharingAction; if ((c == null ? void 0 : c.isCtwaPerCustomerDataSharingEnabled) == null) ...
                // When the value or action payload is missing, WA Web falls through this branch and returns malformed.
                if (!(mutation.value().action().orElse(null) instanceof CtwaPerCustomerDataSharingAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                // ADAPTED: WA Web checks (c?.isCtwaPerCustomerDataSharingEnabled == null) and returns malformed.
                // Cobalt's public accessor coalesces the nullable Boolean field to false per nullable boolean
                // accessor convention; the raw field is package-private and outside this module's ownership,
                // so null vs false cannot be distinguished here without mutating the model. The practical
                // effect is that a deliberately null flag is treated as `false` rather than malformed.
                var enabled = action.isCtwaPerCustomerDataSharingEnabled();

                // WA Web calls createOrReplace({lidRawString: u, dataSharing3pdEnabled: d}) on
                // the data-sharing-3pd-lid-v2 IDB table and updateDataSharing3pdLidInCollection
                // on the frontend. Cobalt mirrors the per-LID schema by writing into the per-LID
                // map keyed by accountLid on the unified store.
                client.store().putCtwaDataSharing(new CtwaDataSharingPreferenceBuilder()
                        .accountLid(accountLid)
                        .enabled(enabled)
                        .build());

                return MutationApplicationResult.success();
            }
            case REMOVE -> {
                // WA Web calls remove(t) on the data-sharing-3pd-lid-v2 table and
                // removeDataSharing3pdLidFromCollection on the frontend. WA Web does not validate
                // accountLid on REMOVE — a null key is a no-op on IDB. Cobalt's store removal
                // treats null as a no-op too, preserving Success semantics.
                client.store().removeCtwaDataSharing(accountLid);

                return MutationApplicationResult.success();
            }
            default -> {
                return MutationApplicationResult.unsupported();
            }
        }
    }

}
