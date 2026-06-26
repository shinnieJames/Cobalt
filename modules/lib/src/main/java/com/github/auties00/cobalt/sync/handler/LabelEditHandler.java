package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.LabelBuilder;
import com.github.auties00.cobalt.model.sync.mutation.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppSettingsStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code label_edit} app-state sync action that creates, edits or
 * deletes a chat label.
 *
 * <p>The action fans out across the {@link SyncPatchType#REGULAR} collection so
 * companion devices show the same set of labels. The mutation index keys each
 * entry by the server-assigned label id, formatted as
 * {@snippet :
 *     ["label_edit", labelId]
 * }
 *
 * @implNote
 * This implementation merges incoming edits into the existing {@link Label} in
 * place so the assignments populated by {@link LabelAssociationHandler} survive
 * the edit. Server-assigned labels (type
 * {@link LabelEditAction.ListType#SERVER_ASSIGNED}) are not added to the main
 * collection because Cobalt has no server-assigned id map yet, so the
 * {@code predefinedId} mapping is currently dropped.
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
     * <p>Rejects non-{@link SyncdOperation#SET} operations as
     * {@link MutationApplicationResult#unsupported()} and a missing label id or
     * action payload as malformed. A {@link LabelEditAction#deleted()} action
     * removes the label via
     * {@link LinkedWhatsAppSettingsStore#removeLabel(String)};
     * otherwise the row is upserted, merging into an existing {@link Label} in
     * place when one is found or building a new one via {@link LabelBuilder}.
     *
     * @implNote
     * This implementation classifies a missing label-id slot as
     * {@link MutationApplicationResult#malformed()} before parsing to avoid an
     * out-of-bounds exception on {@code JSON.parseArray}. On an upsert the
     * existing {@link Label#assignments()} set survives because the merge path
     * mutates the existing row. Because the {@code isActive} and
     * {@code isImmutable} flags coalesce {@code null} to {@code false}, a
     * {@code true} reading is persisted but a {@code false} reading does not
     * clobber a previously-set {@code true}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
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

        if (!(mutation.value().flatMap(sav -> sav.action()).orElse(null) instanceof LabelEditAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (action.deleted()) {
            client.store().settingsStore().removeLabel(labelId);
            return MutationApplicationResult.success();
        }

        var name = action.name().orElse("");
        var color = action.color().orElse(0);

        var type = action.type().orElse(null);
        if (type == LabelEditAction.ListType.SERVER_ASSIGNED) {
            // TODO: persist the server-assigned label id to predefined id mapping; Cobalt has
            //       no equivalent store field yet, so the predefinedId association is dropped.
            return MutationApplicationResult.success();
        }

        var existing = client.store().settingsStore().findLabel(labelId).orElse(null);
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
            client.store().settingsStore().addLabel(label);
        }

        return MutationApplicationResult.success();
    }

}
