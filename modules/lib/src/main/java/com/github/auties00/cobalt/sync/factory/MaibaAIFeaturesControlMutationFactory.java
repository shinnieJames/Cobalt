package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing Maiba-AI-features-control sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.MaibaAIFeaturesControlHandler}.
 */
public final class MaibaAIFeaturesControlMutationFactory {
    /**
     * Constructs a Maiba-AI-features-control mutation factory.
     */
    public MaibaAIFeaturesControlMutationFactory() {

    }

    /**
     * Builds a pending {@code maiba_ai_features_control} mutation carrying the
     * given AI feature status.
     *
     * <p>NO_WA_BASIS: WA Web has no outgoing helper for this action; the shape
     * follows {@code WAWebSyncdActionUtils.buildPendingMutation} as used by
     * every other sibling {@code AccountSyncdActionBase} subclass. Cobalt
     * surfaces the helper so the public
     * {@code WhatsAppClient.editAIFeaturesEnabled} setter can build a single
     * mutation without hand-rolling the protobuf wrapping.
     *
     * @param timestamp the mutation timestamp
     * @param status    the new {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus}
     * @return a pending mutation carrying the {@code maiba_ai_features_control}
     *         action
     * @throws NullPointerException if {@code timestamp} or {@code status} is {@code null}
     */
    public SyncPendingMutation getMaibaAiFeatureStatusMutation(Instant timestamp, MaibaAIFeaturesControlAction.MaibaAIFeatureStatus status) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        var action = new MaibaAIFeaturesControlActionBuilder()
                .aiFeatureStatus(status)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .maibaAiFeaturesControlAction(action)
                .build();
        var index = JSON.toJSONString(List.of(MaibaAIFeaturesControlAction.ACTION_NAME));
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                MaibaAIFeaturesControlAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pending, 0);
    }
}
