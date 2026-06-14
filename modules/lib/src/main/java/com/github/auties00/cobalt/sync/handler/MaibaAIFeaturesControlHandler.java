package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code maiba_ai_features_control} app-state sync action that
 * persists the merchant AI business agent feature status.
 *
 * <p>Each mutation carries a single
 * {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus} value
 * ({@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus#ENABLED},
 * {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus#ENABLED_HAS_LEARNING}
 * or {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus#DISABLED}) which
 * is persisted via
 * {@link com.github.auties00.cobalt.store.BusinessStore#setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}
 * so SMB-AI features can read the current opt-in state without re-decoding the
 * protobuf.
 *
 * @implNote
 * This implementation is forward-looking: WhatsApp Web ships the action field
 * on its sync-action protobuf but does not yet register a corresponding sync
 * module, so the collection name, mutation behaviour and store target are
 * inferred from sibling preference-style handlers until WhatsApp Web ships the
 * matching module.
 */
public final class MaibaAIFeaturesControlHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link MaibaAIFeaturesControlHandler}.
     */
    public MaibaAIFeaturesControlHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return MaibaAIFeaturesControlAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR_HIGH} because no
     * WhatsApp Web sync handler declares a collection for this action; the
     * value matches sibling preference-style handlers in the same registry.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int version() {
        return MaibaAIFeaturesControlAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Accepts only {@link SyncdOperation#SET}; other operations are reported
     * as {@link MutationApplicationResult#unsupported()}. An absent action
     * payload or an empty
     * {@link MaibaAIFeaturesControlAction#aiFeatureStatus()} is reported as
     * {@link MutationApplicationResult#malformed()}; otherwise the status is
     * persisted via
     * {@link com.github.auties00.cobalt.store.BusinessStore#setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}.
     *
     * @implNote
     * This implementation treats an empty status as malformed so the
     * orchestrator does not silently overwrite the store with a default value,
     * because the action carries a single mandatory enum with no
     * {@link SyncdOperation#REMOVE} semantic.
     */
    @Override
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MaibaAIFeaturesControlAction action)
                || action.aiFeatureStatus().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().businessStore().setAiBusinessAgentStatus(action.aiFeatureStatus().get());
        return MutationApplicationResult.success();
    }

}
