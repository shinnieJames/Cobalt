package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code label_reordering} app-state sync action that publishes a
 * new sort order for the user's chat labels.
 *
 * <p>The sorted id array fans out across the {@link SyncPatchType#REGULAR}
 * collection so companion devices render the same order. The mutation index
 * has no variable parts and is always
 * {@snippet :
 *     ["label_reordering"]
 * }
 *
 * @implNote
 * This implementation walks {@link LabelReorderingAction#sortedLabelIds()} and
 * writes each label's zero-based position into {@link Label#orderIndex()} via
 * {@link Label#setOrderIndex(Integer)}. Ids referenced by the action but
 * missing from the local store are silently skipped.
 */
@WhatsAppWebModule(moduleName = "WAWebLabelReorderingSync")
public final class LabelReorderingHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link LabelReorderingHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LabelReorderingHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LabelReorderingAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LabelReorderingAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LabelReorderingAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rejects non-{@link SyncdOperation#SET} operations as
     * {@link MutationApplicationResult#unsupported()}, an absent action payload
     * and an empty {@link LabelReorderingAction#sortedLabelIds()} list as
     * malformed. Each id is matched against the store via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#findLabel(String)};
     * present rows have their {@link Label#orderIndex()} set to the loop
     * position while absent ids are skipped. Labels present in the store but
     * absent from the action keep their existing {@link Label#orderIndex()}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof LabelReorderingAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var sortedLabelIds = action.sortedLabelIds();
        if (sortedLabelIds.isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        for (var position = 0; position < sortedLabelIds.size(); position++) {
            var labelId = sortedLabelIds.get(position);
            var labelIdString = String.valueOf(labelId);
            var label = client.store().findLabel(labelIdString).orElse(null);
            if (label != null) {
                label.setOrderIndex(position);
            }
        }

        return MutationApplicationResult.success();
    }

}
