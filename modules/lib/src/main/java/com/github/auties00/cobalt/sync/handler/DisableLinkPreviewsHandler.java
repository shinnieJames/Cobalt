package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
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
 * <p>The action carries a single boolean fanned out across the
 * {@link SyncPatchType#REGULAR} collection so every linked device shares the
 * same "Disable link previews" privacy setting. The mutation index has no
 * variable parts and is always
 * {@snippet :
 *     ["setting_disableLinkPreviews"]
 * }
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
     * <p>Walks the batch once classifying each mutation, tracks the last valid
     * {@link PrivacySettingDisableLinkPreviewsAction#isPreviewsDisabled()} flag
     * carried by a {@link SyncdOperation#SET}, and persists that single value
     * via {@link com.github.auties00.cobalt.store.SettingsStore#setDisableLinkPreviews(boolean)}
     * after the loop. Non-{@code SET} operations yield
     * {@link MutationApplicationResult#unsupported()} and entries whose action
     * payload is not a {@link PrivacySettingDisableLinkPreviewsAction} yield
     * {@link MutationApplicationResult#malformed()}.
     *
     * @implNote
     * This implementation folds the latest valid value and persists once after
     * the loop rather than writing on every {@link SyncdOperation#SET}, because
     * the boolean is global and only its final value is observable.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(LinkedWhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
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
            client.store().settingsStore().setDisableLinkPreviews(lastValid);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rejects non-{@link SyncdOperation#SET} operations as
     * {@link MutationApplicationResult#unsupported()} and action payloads that
     * are not a {@link PrivacySettingDisableLinkPreviewsAction} as
     * {@link MutationApplicationResult#malformed()}, then persists the value
     * through {@link com.github.auties00.cobalt.store.SettingsStore#setDisableLinkPreviews(boolean)}.
     *
     * @implNote
     * This implementation persists the value eagerly rather than folding it,
     * because a single mutation has no later value to supersede it.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PrivacySettingDisableLinkPreviewsAction action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().settingsStore().setDisableLinkPreviews(action.isPreviewsDisabled());
        return MutationApplicationResult.success();
    }

}
