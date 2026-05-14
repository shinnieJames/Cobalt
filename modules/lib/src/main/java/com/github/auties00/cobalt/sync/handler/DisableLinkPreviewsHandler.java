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
 * Handles disable link previews setting sync actions.
 *
 * <p>This handler processes mutations that control whether link previews are
 * disabled in chat messages. It maps to the singleton instance exported
 * as {@code default} from the WA Web module, which extends
 * {@code AccountSyncdActionBase} with collection {@code Regular},
 * version {@code 8}, and action {@code "setting_disableLinkPreviews"}.
 *
 * <p>Index format: {@code ["setting_disableLinkPreviews"]}
 */
@WhatsAppWebModule(moduleName = "WAWebDisableLinkPreviewsSync")
public final class DisableLinkPreviewsHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code DisableLinkPreviewsHandler}.
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
     * <p>Per WhatsApp Web {@code WAWebDisableLinkPreviewsSync.applyMutations}: iterates
     * all mutations, accumulating the last valid {@code isPreviewsDisabled} value from
     * SET operations. Non-SET operations are counted and logged, returning
     * {@code Unsupported}. Mutations where {@code isPreviewsDisabled} is {@code null}
     * are counted and logged, returning {@code Malformed} via
     * {@code WAWebSyncdIndexUtils.malformedActionValue}. After iteration,
     * persists the accumulated value once via
     * {@code WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs}.
     * @param client    the WhatsApp client instance
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
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
            client.store().setDisableLinkPreviews(lastValid); // ADAPTED: WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs(r) -> direct store call
        }

        return results;
    }

    /**
     * Applies a single disable link previews mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebDisableLinkPreviewsSync.applyMutations}
     * (single-mutation path within the batch):
     * <ol>
     *   <li>If the operation is not {@code SET}, returns {@code Unsupported}.</li>
     *   <li>Extracts {@code isPreviewsDisabled} from the
     *       {@code privacySettingDisableLinkPreviewsAction}. If the action or
     *       value is {@code null}, returns {@code Malformed}.</li>
     *   <li>Persists the value via
     *       {@code WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs}
     *       and returns {@code Success}.</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
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

        client.store().setDisableLinkPreviews(action.isPreviewsDisabled()); // ADAPTED: WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs(s) -> direct store call
        return MutationApplicationResult.success();
    }

}
