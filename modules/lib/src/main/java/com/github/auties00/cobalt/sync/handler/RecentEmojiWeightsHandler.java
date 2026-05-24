package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeightsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code recent_emoji_weights_action} app-state action that
 * distributes the user's recent-emoji frequency table across linked
 * devices.
 *
 * @apiNote
 * Persists a list of {@link RecentEmojiWeight} entries
 * ({@code (emoji, weight)} pairs) on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} so the emoji
 * suggestion ranker reflects the same usage counts on every device.
 * Only {@link SyncdOperation#SET} is accepted; any other operation is
 * reported as {@link MutationApplicationResult#unsupported()} and a
 * wrong-typed value as {@link MutationApplicationResult#malformed()}.
 *
 * @implNote
 * This implementation has no WA Web counterpart: the
 * {@code SyncActionValue.RecentEmojiWeightsAction} protobuf (action
 * index 11, action name {@code "recent_emoji_weights_action"}) is
 * declared in {@code WAWebProtobufSyncAction.pb} and the inline
 * collection router maps it to {@link SyncPatchType#REGULAR_LOW},
 * but no {@code WAWebRecentEmojiWeightsSync} module ships and the
 * action is absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}. WA Web tracks
 * recent emojis entirely in the in-memory
 * {@code WAWebRecentEmojiCollection} (incremented by
 * {@code WAWebEmojiSuggestions.react} and consumed by
 * {@code WAWebEmojiSearch}) without ever reading or writing the
 * protobuf, so the action appears to be a mobile-only sync surface
 * that the WA Web client tolerates only at the protobuf shape level.
 * The handler exists in Cobalt as a forward-looking implementation;
 * the entire payload (the full snapshot of the weights list) is
 * persisted in one store write.
 */
public final class RecentEmojiWeightsHandler implements WebAppStateActionHandler {

    /**
     * Constructs the recent-emoji-weights sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; the handler holds no
     * AB-prop / store / WAM dependency.
     */
    public RecentEmojiWeightsHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return RecentEmojiWeightsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns
     * {@link RecentEmojiWeightsAction#COLLECTION_NAME}
     * ({@link SyncPatchType#REGULAR_LOW}) as declared by the inline
     * router in {@code WAWebProtobufSyncAction.pb}
     * ({@code RECENT_EMOJI_WEIGHTS_ACTION -> REGULAR_LOW}).
     */
    @Override
    public SyncPatchType collectionName() {
        return RecentEmojiWeightsAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns
     * {@link RecentEmojiWeightsAction#ACTION_VERSION} as a
     * forward-looking default; WA Web has no concrete handler module
     * to consult, so the constant defaults to the protobuf field
     * index.
     */
    @Override
    public int version() {
        return RecentEmojiWeightsAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation accepts only {@link SyncdOperation#SET}: the
     * action carries a full snapshot of the recent-emoji weight list
     * and there is no semantic for {@code REMOVE}. A wrong-typed value
     * surfaces as {@link MutationApplicationResult#malformed()}; on
     * success the resolved {@link RecentEmojiWeight} list is written
     * via {@code WhatsAppStore.setRecentEmojiWeights}.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof RecentEmojiWeightsAction action)) {
            return MutationApplicationResult.malformed();
        }

        var weights = action.weights();
        client.store().setRecentEmojiWeights(weights);
        return MutationApplicationResult.success();
    }
}
