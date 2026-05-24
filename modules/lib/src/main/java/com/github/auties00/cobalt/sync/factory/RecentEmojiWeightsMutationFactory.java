package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeightsAction;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeightsActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing app-state mutations that publish the local
 * recent-emoji usage ranking to every linked device.
 *
 * @apiNote
 * Drives cross-device convergence of the emoji-picker ordering: each
 * time the user picks an emoji on one device the local weight is
 * bumped, and the entire weight snapshot is broadcast so companions
 * see the same suggestion order.
 *
 * @implNote
 * This implementation mirrors the WA Web flow rooted at
 * {@code WAWebRecentEmojiCollection.increment}, which marks the
 * recent-emoji collection dirty and then publishes the full snapshot
 * through the standard syncd dirty-flag pipeline. Cobalt does not run
 * a dirty-flag scheduler at this layer; callers re-publish on demand
 * via the public client setter.
 */
public final class RecentEmojiWeightsMutationFactory {
    /**
     * Constructs a recent-emoji-weights mutation factory.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across
     * the lifetime of the client.
     */
    public RecentEmojiWeightsMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that overwrites the recent-emoji
     * weight snapshot with the given list.
     *
     * @apiNote
     * Invoked from the public recent-emoji-usage setter on
     * {@link com.github.auties00.cobalt.client.WhatsAppClient}; the
     * index carries only the action name because the snapshot is a
     * singleton per account.
     *
     * @implNote
     * This implementation captures the timestamp via
     * {@link Instant#now()}; WA Web emits the mutation from the
     * dirty-flag scheduler that drains
     * {@code WAWebRecentEmojiCollection} so the timestamp there
     * matches the scheduler tick rather than the user gesture.
     *
     * @param usage the per-emoji weight snapshot to publish; an empty
     *              list is valid and represents "no recent usage"
     * @return the pending mutation ready to be queued for outbound
     *         app-state sync
     * @throws NullPointerException if {@code usage} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebRecentEmojiCollection", exports = "increment", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getRecentEmojiWeightsMutation(List<RecentEmojiWeight> usage) {
        Objects.requireNonNull(usage, "usage cannot be null");
        var timestamp = Instant.now();
        var action = new RecentEmojiWeightsActionBuilder()
                .weights(usage)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .recentEmojiWeightsAction(action)
                .build();
        var index = JSON.toJSONString(List.of(RecentEmojiWeightsAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                RecentEmojiWeightsAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
