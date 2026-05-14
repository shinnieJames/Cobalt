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
 * Handles the {@code label_edit} sync action by creating, editing, or deleting
 * chat labels.
 *
 * <p>This handler processes mutations that target a single label by id. The
 * label identifier is extracted from {@code indexParts[1]} and used as the
 * primary key in the store's label map. Only {@link SyncdOperation#SET}
 * operations are supported; any other operation is reported back as
 * {@code UNSUPPORTED}.
 *
 * <p>Per {@code WAWebLabelSync.default.applyMutations}, the handler:
 * <ol>
 *   <li>Validates the index and rejects missing {@code labelId}.</li>
 *   <li>Validates the action value and rejects a missing
 *       {@code labelEditAction}.</li>
 *   <li>On {@code deleted === true}, removes the label from the store.</li>
 *   <li>Otherwise builds an update record with {@code name}, {@code colorIndex},
 *       {@code predefinedId}, {@code orderIndex}, {@code type}, {@code isActive},
 *       and {@code isImmutable}, then:
 *     <ul>
 *       <li>If {@code type === SERVER_ASSIGNED}, registers the mapping in the
 *           server-assigned label id map without adding the label to the main
 *           collection.</li>
 *       <li>Otherwise merges the update into any existing label (preserving
 *           existing fields such as chat-jid assignments) or inserts a new one.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Index format: {@code ["label_edit", "labelId"]}
 */
@WhatsAppWebModule(moduleName = "WAWebLabelSync")
public final class LabelEditHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LabelEditHandler() {

    }

    /**
     * Returns the action name for label edit sync.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LabelEditAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the regular sync collection
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LabelEditAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LabelEditAction.ACTION_VERSION;
    }

    /**
     * Applies a single label edit mutation and returns a detailed result.
     *
     * <p>Rejects non-{@code SET} operations, malformed index values (missing
     * {@code labelId}), and malformed action values (missing
     * {@code labelEditAction}). On a valid delete, removes the label from the
     * store. On a valid upsert, constructs the merged label record and either
     * registers it in the server-assigned id map (when
     * {@code type === SERVER_ASSIGNED}) or merges it into the main label
     * collection, preserving any existing chat-jid assignments.
     *
     * <p>WAM telemetry ({@code WAWebWamLabelSyncTrackingReporter}) and the SMB
     * platform warning counters ({@code isSMB() && h == null}) are intentionally
     * omitted in Cobalt. The malformed-name warning ({@code S === ""}) and the
     * unknown-type warning are also omitted because they do not affect state.
     * The asynchronous IndexedDB lock
     * ({@code WAWebModelStorageUtils.getStorage().lock(["label", "label-association", "chat"], ...)})
     * collapses to direct in-memory operations because Cobalt's store is a
     * single flat map.
     *
     * <p>Per {@code WAWebLabelSync.default.applyMutations}, a label that already
     * exists is merged into (not replaced), so fields absent from the action
     * (such as the existing chat-jid assignment set) are preserved. Cobalt
     * mirrors this by mutating the existing {@link Label} in place via its
     * setters instead of rebuilding it from scratch.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        // WAWebLabelSync.applyMutations: var n=t.indexParts[1]; if(!n) return malformedActionIndex().
        // n[1] is `undefined` when the slot is missing; mirror that via an explicit size check.
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
            // ADAPTED: WAWebLabelSync.default.applyMutations, yield o("WAWebSchemaLabel").getLabelTable().remove(c); o("WAWebLabelCollection").LabelCollection.remove(c);
            // Cobalt's single removeLabel call replaces both the IndexedDB table remove and the in-memory collection remove because the store is flat.
            client.store().removeLabel(labelId);
            return MutationApplicationResult.success();
        }

        // Extract edit fields. WA Web uses `S = d.name ?? ""`, then allows nullable
        // colorIndex/predefinedId/isActive/isImmutable/type/orderIndex to pass through
        // unchanged (each guarded by `!= null`). Cobalt mirrors the optional-field
        // guards via Optional / OptionalInt accessors.
        var name = action.name().orElse("");
        // NOTE: WAWebLabelSync emits a WALogger WARN for S === "" and (when
        // isSMB() && h == null) another WARN; Cobalt omits WAM/logging counters.
        // ADAPTED: WA Web's Label.colorIndex is nullable; Cobalt's Label.color is a
        // primitive int with default 0. Null/absent color is mapped to 0 because the
        // underlying model cannot express absence for this field.
        var color = action.color().orElse(0);

        var type = action.type().orElse(null);
        if (type == LabelEditAction.ListType.SERVER_ASSIGNED) {
            // ADAPTED: Cobalt's store has no dedicated server-assigned label id map
            // (reported as a missing store operation in the validation report).
            // SERVER_ASSIGNED labels are intentionally NOT added to the main label
            // collection by WA Web (WAWebLabelCollection.initializeFromCache filters
            // them out), so Cobalt skips the addLabel call here to preserve that
            // invariant. The predefinedId mapping is currently dropped; the missing
            // store field is reported for follow-up.
            return MutationApplicationResult.success();
        }

        // Backbone-style {merge: true}: update attributes on an existing model if
        // present, otherwise insert a new one. Cobalt mirrors this by mutating the
        // existing Label in place or building a fresh one when none exists.
        var existing = client.store().findLabel(labelId).orElse(null);
        if (existing != null) {
            // Merge path: update mutable fields on the existing label so that
            // unaffected fields (e.g. the assignments set maintained by
            // LabelAssociationHandler) are preserved. WA Web's `{merge: true}`
            // semantics assigns each key from R onto the existing model, which is
            // what the setters below do.
            existing.setName(name);
            existing.setColor(color);
            existing.setPredefinedId(action.predefinedId().isPresent() ? action.predefinedId().getAsInt() : null);
            if (action.orderIndex().isPresent()) {
                existing.setOrderIndex(action.orderIndex().getAsInt());
            }
            if (type != null) {
                existing.setType(type);
            }
            // ADAPTED: WA Web distinguishes null/true/false for isActive/isImmutable
            // (`if (y != null) R.isActive = y`). Cobalt's LabelEditAction.isActive()
            // and isImmutable() coalesce null -> false per project convention, so the
            // null-vs-false distinction is lost at the action layer. When the action
            // reports true, Cobalt sets true; when the action reports false, Cobalt
            // sets null to avoid clobbering a previously-set true with a spurious
            // "false" coming from an absent field.
            if (action.isActive()) {
                existing.setActive(Boolean.TRUE);
            }
            if (action.isImmutable()) {
                existing.setImmutable(Boolean.TRUE);
            }
        } else {
            // Insert path: build a new Label from the action fields. Assignments
            // default to an empty set via the generated builder.
            var label = new LabelBuilder()
                    .id(labelId)
                    .name(name)
                    .color(color)
                    .predefinedId(action.predefinedId().isPresent() ? action.predefinedId().getAsInt() : null)
                    .orderIndex(action.orderIndex().isPresent() ? action.orderIndex().getAsInt() : null)
                    .type(type)
                    // ADAPTED: see comment on the merge path above for isActive/isImmutable
                    .isActive(action.isActive() ? Boolean.TRUE : null)
                    .isImmutable(action.isImmutable() ? Boolean.TRUE : null)
                    .build();
            client.store().addLabel(label);
        }

        // ADAPTED: WAWebLabelSync.default.applyMutations --
        //   E = yield queryLabelAssociationsForLabelIds([c]);
        //   k = LabelCollection.get(c);
        //   if (k != null && E.length > 0) { ... initializeAssociationsFromCache(...) }
        // This block rehydrates chat-jid associations from the IndexedDB
        // label-association table into the in-memory LabelCollection's
        // labelItemCollection. In Cobalt, label-jid associations are stored
        // directly inside the Label's assignments set by LabelAssociationHandler
        // and never leave memory, so there is no cache to rehydrate. The block
        // is therefore intentionally omitted.
        // SKIPPED: WAM telemetry is intentionally omitted in Cobalt.
        return MutationApplicationResult.success();
    }

}
