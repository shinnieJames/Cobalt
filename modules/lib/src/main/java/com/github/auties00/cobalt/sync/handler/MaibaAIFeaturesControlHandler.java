package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code maiba_ai_features_control} app-state sync action that
 * persists the merchant AI business agent feature status.
 *
 * @apiNote
 * Drives the SMB Maiba AI assistant control surface: each mutation
 * carries a single
 * {@link MaibaAIFeaturesControlAction.MaibaAIFeatureStatus} value
 * ({@code ENABLED}, {@code ENABLED_HAS_LEARNING} or
 * {@code DISABLED}) which is persisted on the local store via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus)}
 * so SMB-AI features can read the current opt-in state without
 * re-decoding the protobuf.
 *
 * @implNote
 * This implementation is forward-looking: WA Web ships the
 * {@code SyncActionValue.MaibaAIFeaturesControlAction} field at index
 * {@code 68} on {@code WAWebProtobufSyncAction.pb} but does not
 * register a corresponding {@code WAWebMaibaAiFeaturesControlSync}
 * module in
 * {@code WAWebCollectionHandlerActions.ActionHandlers}. The collection
 * name, mutation behaviour and store target are therefore inferred
 * from sibling preference-style handlers; every behavioural step is
 * Cobalt-inferred until WA Web ships the matching module.
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
     * This implementation returns {@link SyncPatchType#REGULAR_HIGH}
     * because no WA Web sync handler declares a collection for
     * {@code maiba_ai_features_control}; the value matches sibling
     * preference-style handlers in the same registry.
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
     * @implNote
     * This implementation accepts only
     * {@link SyncdOperation#SET} because the action carries a single
     * mandatory {@code aiFeatureStatus} enum and there is no semantic
     * for {@link SyncdOperation#REMOVE}; an empty
     * {@link MaibaAIFeaturesControlAction#aiFeatureStatus()} is
     * reported as {@link MutationApplicationResult#malformed()} so the
     * orchestrator does not silently overwrite the store with a
     * default value.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MaibaAIFeaturesControlAction action)
                || action.aiFeatureStatus().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setAiBusinessAgentStatus(action.aiFeatureStatus().get());
        return MutationApplicationResult.success();
    }

}
