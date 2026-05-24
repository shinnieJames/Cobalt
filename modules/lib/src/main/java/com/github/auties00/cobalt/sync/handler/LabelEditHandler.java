package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.LabelBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code label_edit} app-state sync action that creates, edits or
 * deletes a chat label.
 *
 * @apiNote
 * Drives the SMB/Business "manage labels" sheet: when the primary device
 * adds, renames, recolours or removes a label the corresponding
 * mutation fans out across the {@link SyncPatchType#REGULAR} collection
 * so companion devices show the same set of labels. The mutation index
 * keys each entry by the server-assigned label id, formatted as
 * {@snippet :
 *     ["label_edit", labelId]
 * }
 *
 * @implNote
 * This implementation merges incoming edits into the existing
 * {@link Label} in place rather than rebuilding the row from scratch,
 * matching WA Web's
 * {@code LabelCollection.add(R, {merge: true})} semantics so that the
 * assignments populated by {@link LabelAssociationHandler} survive the
 * edit. Server-assigned labels (type
 * {@link LabelEditAction.ListType#SERVER_ASSIGNED}) are intentionally
 * not added to the main label collection because WA Web's
 * {@code WAWebLabelCollection.initializeFromCache} filters them out;
 * Cobalt has no equivalent server-assigned id map yet, so the
 * {@code predefinedId} mapping is currently dropped. The
 * {@code WAWebWamLabelSyncTrackingReporter} telemetry, the
 * AI-handoff/AI-responding deduplication paths and the IndexedDB
 * {@code lock("label", "label-association", "chat")} are not modelled
 * because Cobalt's store is a flat in-memory map.
 */
@WhatsAppWebModule(moduleName = "WAWebLabelSync")
public final class LabelEditHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link LabelEditHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LabelEditHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LabelEditAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LabelEditAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LabelEditAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation classifies a missing label-id slot as
     * {@link MutationApplicationResult#malformed()} explicitly to avoid
     * an out-of-bounds exception on
     * {@code JSON.parseArray}. On a delete the label is removed via a
     * single {@link com.github.auties00.cobalt.store.WhatsAppStore#removeLabel(String)}
     * call that collapses WA Web's
     * {@code getLabelTable().remove + LabelCollection.remove} into one
     * operation. On an upsert the existing
     * {@link Label#assignments()} set survives because the merge path
     * mutates the existing row in place. The
     * {@code isActive} and {@code isImmutable} flags coalesce
     * {@code null} to {@code false} per the project's
     * "no Optional&lt;Boolean&gt;" rule, so a {@code true} reading is
     * persisted but a {@code false} reading does not clobber a
     * previously-set {@code true}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var labelId = indexArray.getString(1);
        if (labelId == null || labelId.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (!(mutation.value().action().orElse(null) instanceof LabelEditAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (action.deleted()) {
            client.store().removeLabel(labelId);
            return MutationApplicationResult.success();
        }

        var name = action.name().orElse("");
        var color = action.color().orElse(0);

        var type = action.type().orElse(null);
        if (type == LabelEditAction.ListType.SERVER_ASSIGNED) {
            // TODO: persist the server-assigned label id to predefined id mapping. WA Web
            //       calls LabelCollection.addToServerAssignedLabelIdMap(c, S); Cobalt has
            //       no equivalent store field yet, so the predefinedId association is
            //       currently dropped on the floor.
            return MutationApplicationResult.success();
        }

        var existing = client.store().findLabel(labelId).orElse(null);
        if (existing != null) {
            existing.setName(name);
            existing.setColor(color);
            existing.setPredefinedId(action.predefinedId().isPresent() ? action.predefinedId().getAsInt() : null);
            if (action.orderIndex().isPresent()) {
                existing.setOrderIndex(action.orderIndex().getAsInt());
            }
            if (type != null) {
                existing.setType(type);
            }
            if (action.isActive()) {
                existing.setActive(Boolean.TRUE);
            }
            if (action.isImmutable()) {
                existing.setImmutable(Boolean.TRUE);
            }
        } else {
            var label = new LabelBuilder()
                    .id(labelId)
                    .name(name)
                    .color(color)
                    .predefinedId(action.predefinedId().isPresent() ? action.predefinedId().getAsInt() : null)
                    .orderIndex(action.orderIndex().isPresent() ? action.orderIndex().getAsInt() : null)
                    .type(type)
                    .isActive(action.isActive() ? Boolean.TRUE : null)
                    .isImmutable(action.isImmutable() ? Boolean.TRUE : null)
                    .build();
            client.store().addLabel(label);
        }

        return MutationApplicationResult.success();
    }

}
