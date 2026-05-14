package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.LabelAssociationAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelAssociationActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing label-jid association sync mutations.
 *
 * <p>Mirrors the {@code createLabelAssociationMutations} export of WhatsApp
 * Web's {@code WAWebLabelJidSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.LabelAssociationHandler}.
 */
public final class LabelAssociationMutationFactory {
    /**
     * Constructs a label-association mutation factory.
     */
    public LabelAssociationMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for associating (or disassociating) a
     * label with a chat/contact JID.
     *
     * <p>Per WhatsApp Web {@code WAWebLabelJidSync.createLabelAssociationMutations}
     * the association is emitted as a {@link LabelAssociationAction} whose
     * {@code labeled} flag maps {@code true} to "add" and {@code false} to
     * "remove". The mutation index is
     * {@code ["label_jid", labelId, targetJid]} where {@code targetJid} is
     * the canonical chat/contact index JID (callers should pre-resolve LID
     * 1x1 migration when applicable).
     *
     * <p>WAM telemetry ({@code WAWebWamLabelSyncTrackingReporter}) is
     * intentionally omitted in Cobalt.
     *
     * @param labelId   the label identifier
     * @param targetJid the chat or contact JID the label is being applied to
     * @param labeled   {@code true} to add the association, {@code false} to remove
     * @param timestamp the mutation timestamp
     * @return the pending mutation for the label association
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelJidSync", exports = "createLabelAssociationMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation createLabelAssociationMutation(
            String labelId,
            Jid targetJid,
            boolean labeled,
            Instant timestamp
    ) {
        var action = new LabelAssociationActionBuilder()
                .labeled(labeled)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .labelAssociationAction(action)
                .build();
        var index = JSON.toJSONString(List.of(LabelAssociationAction.ACTION_NAME, labelId, targetJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                LabelAssociationAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0); // ADAPTED: WA Web returns the raw pending mutation; Cobalt wraps it in SyncPendingMutation
    }
}
