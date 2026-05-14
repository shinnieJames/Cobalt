package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingAction;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing label-reordering sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.LabelReorderingHandler}.
 */
public final class LabelReorderingMutationFactory {
    /**
     * Constructs a label-reordering mutation factory.
     */
    public LabelReorderingMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for reordering the user's chat labels.
     *
     * <p>The mutation carries the full ordered list of integer label
     * identifiers (matching the on-wire {@code INT32} type of
     * {@link LabelReorderingAction#sortedLabelIds()}). Per WhatsApp Web the
     * reorder action uses an empty index (Cobalt still prefixes the action
     * name as the canonical first element to stay consistent with every
     * other handler's index layout).
     *
     * @param sortedLabelIds the full ordered list of integer label identifiers
     * @param timestamp      the mutation timestamp
     * @return the pending mutation for the reorder operation
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getReorderLabelsMutation(
            List<Integer> sortedLabelIds,
            Instant timestamp
    ) {
        var action = new LabelReorderingActionBuilder() // ADAPTED: WAWebLabelReorderingSync has no public getter; Cobalt mirrors the sibling WAWebLabelSync.default.getLabelMutation shape
                .sortedLabelIds(sortedLabelIds)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .labelReorderingAction(action)
                .build();
        var index = JSON.toJSONString(List.of(LabelReorderingAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                LabelReorderingAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
