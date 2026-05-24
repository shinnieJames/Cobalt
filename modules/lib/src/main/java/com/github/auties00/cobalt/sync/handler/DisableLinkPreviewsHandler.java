package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the {@code setting_disableLinkPreviews} app-state sync action that
 * toggles whether outgoing chat messages render link previews.
 *
 * @apiNote
 * Drives the privacy-settings "Disable link previews" switch on companion
 * devices: the primary fans out a single boolean across the
 * {@link SyncPatchType#REGULAR} collection. The mutation index has no
 * variable parts and is always
 * {@snippet :
 *     ["setting_disableLinkPreviews"]
 * }
 *
 * @implNote
 * This implementation supplies a batch override that mirrors WA Web's
 * "fold the latest valid value, persist once" pattern from
 * {@code WAWebDisableLinkPreviewsSync.applyMutations}, while
 * {@link #applyMutation} persists each mutation eagerly so the
 * single-mutation entry point produced by the orchestrator default still
 * keeps the store in sync.
 */
@WhatsAppWebModule(moduleName = "WAWebDisableLinkPreviewsSync")
public final class DisableLinkPreviewsHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link DisableLinkPreviewsHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public DisableLinkPreviewsHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PrivacySettingDisableLinkPreviewsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PrivacySettingDisableLinkPreviewsAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PrivacySettingDisableLinkPreviewsAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the batch once to classify each mutation,
     * tracking the last valid {@code isPreviewsDisabled} flag from any
     * {@link SyncdOperation#SET}, and persists that single value via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setDisableLinkPreviews(boolean)}
     * after the loop, mirroring WA Web's
     * {@code setDisableLinkPreviewsToUserPrefs(r)} call site that runs
     * once after the per-mutation closures complete.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (mutations.isEmpty()) {
            return List.of();
        }

        Boolean lastValid = null;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                results.add(MutationApplicationResult.unsupported());
                continue;
            }

            if (mutation.value().action().orElse(null) instanceof PrivacySettingDisableLinkPreviewsAction action) {
                lastValid = action.isPreviewsDisabled();
                results.add(MutationApplicationResult.success());
            } else {
                results.add(MutationApplicationResult.malformed());
            }
        }

        if (lastValid != null) {
            client.store().setDisableLinkPreviews(lastValid);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation persists the value eagerly via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setDisableLinkPreviews(boolean)},
     * skipping the "fold latest, persist once" optimisation reserved for
     * {@link #applyMutationBatch(WhatsAppClient, List)}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PrivacySettingDisableLinkPreviewsAction action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setDisableLinkPreviews(action.isPreviewsDisabled());
        return MutationApplicationResult.success();
    }

}
