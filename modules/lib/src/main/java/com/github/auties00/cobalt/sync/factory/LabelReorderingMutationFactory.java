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
 * @apiNote
 * Drives the chat-label drag-to-reorder gesture on the Business chat-list
 * surface; one call produces a single {@link SyncPendingMutation} that
 * carries the full ordered identifier list and replays via
 * {@link com.github.auties00.cobalt.sync.handler.LabelReorderingHandler}
 * on every linked device.
 *
 * @implNote
 * This implementation has no direct WA Web counterpart: the
 * {@code WAWebLabelReorderingSync} module ships only the inbound
 * {@code applyMutations} half and pairs it with a sibling
 * {@code WAWebDBLabelsReorder.updateLabelsSortOrder} call rather than a
 * typed mutation builder. The shape mirrors
 * {@code WAWebSyncdActionUtils.buildPendingMutation} and reuses the action
 * version exposed by {@link LabelReorderingAction}.
 */
public final class LabelReorderingMutationFactory {
    /**
     * Constructs a label-reordering mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory is
     * wired into the public {@link com.github.auties00.cobalt.client.WhatsAppClient}
     * reorder-label entry point. The factory keeps no state, so a single
     * instance is sufficient per client.
     */
    public LabelReorderingMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that broadcasts the current chat-label
     * ordering to every linked device.
     *
     * @apiNote
     * Invoked from the public label-reorder entry point on the Business
     * chat-list surface; the resulting {@link SyncPendingMutation} is
     * appended to the {@code Regular} collection queue and consumed by
     * {@link com.github.auties00.cobalt.sync.handler.LabelReorderingHandler}
     * on receiving devices. Empty or unordered lists are rejected by the
     * receiver via {@code malformedActionValue}; callers must pass the full
     * sorted identifier list.
     *
     * @implNote
     * This implementation mirrors the sibling
     * {@code WAWebSyncdActionUtils.buildPendingMutation} shape because
     * {@code WAWebLabelReorderingSync} exposes no outgoing helper of its
     * own; the index uses the canonical action name with no
     * {@code indexArgs} since the action is a singleton per account.
     *
     * @param sortedLabelIds the full ordered list of integer label
     *                       identifiers; receiving devices store this list
     *                       verbatim and call {@code updateLabelsSortOrder}
     *                       on it
     * @param timestamp      the mutation timestamp recorded both on the
     *                       outer mutation and on the inner
     *                       {@code SyncActionValue}
     * @return the pending mutation for the reorder operation
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelReorderingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getReorderLabelsMutation(
            List<Integer> sortedLabelIds,
            Instant timestamp
    ) {
        var action = new LabelReorderingActionBuilder()
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
