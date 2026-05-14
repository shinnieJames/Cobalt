package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelEditActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing label-edit sync mutations.
 *
 * <p>Mirrors the {@code getLabelMutation} export of WhatsApp Web's
 * {@code WAWebLabelSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.LabelEditHandler}.
 */
public final class LabelEditMutationFactory {
    /**
     * Constructs a label-edit mutation factory.
     */
    public LabelEditMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for creating or editing a chat label.
     *
     * <p>Per WhatsApp Web {@code WAWebLabelSync.default.getLabelMutation},
     * assembles a {@link LabelEditAction} with the supplied fields (null
     * values are preserved so that deleted flags, missing colour, missing
     * type and so on round-trip correctly) and wraps it in a
     * {@link SyncPendingMutation} with the canonical index
     * {@code ["label_edit", labelId]}. WAM telemetry
     * ({@code WAWebWamLabelSyncTrackingReporter}) is intentionally omitted.
     *
     * @param labelId      the label identifier (index arg, stringified by the caller)
     * @param name         the display name, may be {@code null}
     * @param color        the palette colour index, or {@code null} when unchanged
     * @param deleted      whether the label is being deleted
     * @param predefinedId the predefined list identifier, or {@code null}
     * @param isActive     the active flag, or {@code null} when unchanged
     * @param type         the list type, or {@code null} when unchanged
     * @param timestamp    the mutation timestamp
     * @return the pending mutation for the label edit
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelSync", exports = "getLabelMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getLabelMutation(
            String labelId,
            String name,
            Integer color,
            boolean deleted,
            Integer predefinedId,
            Boolean isActive,
            LabelEditAction.ListType type,
            Instant timestamp
    ) {
        var action = new LabelEditActionBuilder()
                .name(name)
                .deleted(deleted)
                .color(color)
                .predefinedId(predefinedId)
                .isActive(isActive)
                .type(type)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .labelEditAction(action)
                .build();
        var index = JSON.toJSONString(List.of(LabelEditAction.ACTION_NAME, labelId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                LabelEditAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0); // ADAPTED: WA Web returns the raw pending mutation; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }
}
