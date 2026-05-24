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
 * @apiNote
 * Drives the Meta-AI feature-status toggle on the Settings privacy
 * surface; one call produces a single {@link SyncPendingMutation} that
 * propagates the chosen feature status to every linked device and is
 * consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.MaibaAIFeaturesControlHandler}.
 *
 * @implNote
 * This implementation has no direct WA Web counterpart: the
 * {@code maiba_ai_features_control} action is declared only as a protobuf
 * shape in {@code WAWebProtobufSyncAction.pb} and has no dedicated
 * {@code WAWebMaibaAIFeaturesControlSync} module in the current bundle.
 * The shape follows
 * {@code WAWebSyncdActionUtils.buildPendingMutation} as used by every
 * other sibling {@code AccountSyncdActionBase} subclass.
 */
public final class MaibaAIFeaturesControlMutationFactory {
    /**
     * Constructs a Maiba-AI-features-control mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public Meta-AI feature-status setter. The factory
     * keeps no state, so a single instance is sufficient per client.
     */
    public MaibaAIFeaturesControlMutationFactory() {

    }

    /**
     * Builds a pending {@code maiba_ai_features_control} mutation carrying
     * the given AI feature status.
     *
     * @apiNote
     * Invoked from the public Meta-AI feature-status setter; receiving
     * devices store the {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus}
     * value verbatim in their local prefs. The index carries only the
     * action name because the action is a singleton per account.
     *
     * @implNote
     * This implementation models the
     * {@code SyncActionValue.maibaAiFeaturesControlAction} protobuf shape
     * as used by {@code WAWebSyncdActionUtils.buildPendingMutation},
     * routing the result through the {@code Regular} collection alongside
     * the other account-scoped settings.
     *
     * @param timestamp the mutation timestamp recorded on both the outer
     *                  mutation and the inner {@code SyncActionValue}
     * @param status    the new {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus}
     * @return a pending mutation carrying the
     *         {@code maiba_ai_features_control} action
     * @throws NullPointerException if {@code timestamp} or {@code status}
     *                              is {@code null}
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
