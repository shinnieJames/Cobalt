package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeightsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.List;

/**
 * Handles {@link RecentEmojiWeightsAction} sync mutations
 * ({@code "recent_emoji_weights_action"}).
 *
 * <p>Each mutation carries a list of {@link RecentEmojiWeight} entries — opaque
 * {@code (emoji, weight)} pairs that describe the relative usage frequency of
 * recently picked emojis. The list is persisted on the local
 * {@code WhatsAppStore} via {@code setRecentEmojiWeights} so that emoji
 * suggestion ranking can be restored across devices. Only {@code SET}
 * operations are accepted; any other operation maps to
 * {@link MutationApplicationResult#unsupported()} and a missing or
 * wrong-typed value maps to {@link MutationApplicationResult#malformed()}.
 *
 * <p><b>NO_WA_BASIS:</b> The
 * {@code SyncActionValue.RecentEmojiWeightsAction} protobuf is defined in
 * {@code WAWebProtobufSyncAction.pb} as field
 * {@code recentEmojiWeightsAction} at index {@code 11} (exported as
 * {@code SyncActionValue$RecentEmojiWeightsActionSpec}) with a single
 * repeated field {@code weights: RecentEmojiWeight} at index {@code 1}, where
 * each {@code RecentEmojiWeight} carries an {@code emoji: string} (index
 * {@code 1}) and a {@code weight: float} (index {@code 2}). The mutation
 * name is wired in the same module's
 * {@code MutationProps$MutationName} enum
 * ({@code RECENT_EMOJI_WEIGHTS_ACTION:11}) with the canonical action string
 * {@code "recent_emoji_weights_action"}, and the inline collection router in
 * {@code WAWebProtobufSyncAction.pb}
 * ({@code e===c.RECENT_EMOJI_WEIGHTS_ACTION?u.REGULAR_LOW}) explicitly maps
 * the action to the {@code REGULAR_LOW} collection.
 *
 * <p>However, the current WA Web snapshot does <em>not</em> ship a
 * corresponding sync handler module (no {@code WAWebRecentEmojiWeightsSync}).
 * The action is also absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, the registry consumed
 * by {@code WAWebSyncdGetActionHandler.setActionHandlers}, so WA Web would
 * never dispatch any incoming mutation with this action via
 * {@code WAWebSyncdGetActionHandler.getActionHandler("recent_emoji_weights_action")}
 * (the lookup would return {@code undefined} and the mutation would be
 * skipped). Recent emojis on WA Web are tracked entirely in the in-memory
 * {@code WAWebRecentEmojiCollection} (declared in {@code WAWebCollections}
 * and seeded by {@code WAWebRecentEmojiModel}), incremented by
 * {@code WAWebEmojiSuggestions.react} when the user selects a suggestion,
 * and consumed by ranking helpers in {@code WAWebEmojiSearch}; none of these
 * modules read or write the {@code SyncActionValue.RecentEmojiWeightsAction}
 * protobuf. The action appears to be a mobile-only sync surface that the WA
 * Web client tolerates only at the protobuf shape level.
 *
 * <p>The Cobalt handler is a forward-looking implementation: it follows the
 * Cobalt sync handler conventions used by every other registered handler
 * (singleton, {@code applyMutation} producing a typed
 * {@link MutationApplicationResult}, eager store update on {@code SET}). Every
 * behavioural step here is Cobalt-inferred until WA Web ships the matching
 * {@code WAWebRecentEmojiWeightsSync} module.
 */
public final class RecentEmojiWeightsHandler implements WebAppStateActionHandler {

    /**
     * Private constructor that enforces the singleton pattern.
     */
    public RecentEmojiWeightsHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "recent_emoji_weights_action"} string
     */
    @Override
    public String actionName() {
        return RecentEmojiWeightsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link SyncPatchType#REGULAR_LOW} as inferred from the WA Web
     * protobuf-side collection router.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    public SyncPatchType collectionName() {
        return RecentEmojiWeightsAction.COLLECTION_NAME; // NO_WA_BASIS: matches the WAWebProtobufSyncAction.pb inline collection mapping RECENT_EMOJI_WEIGHTS_ACTION -> REGULAR_LOW
    }

    /**
     * {@inheritDoc}
     * @return the integer version constant declared on the action class
     */
    @Override
    public int version() {
        return RecentEmojiWeightsAction.ACTION_VERSION; // NO_WA_BASIS: WA Web has no recent emoji weights version constant; defaults to the protobuf field index 11
    }

    /**
     * Applies a recent emoji weights mutation and returns the detailed
     * outcome.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only {@code SET}
     *       mutations are accepted; the action carries a full snapshot of the
     *       recent emoji weight list and there is no semantic for
     *       {@code REMOVE}.</li>
     *   <li>Resolve the mutation value to a
     *       {@link RecentEmojiWeightsAction}; if the value is missing or of
     *       the wrong type, return
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li>Persist the resolved {@link RecentEmojiWeight} list on the store
     *       via {@code WhatsAppStore.setRecentEmojiWeights} and return
     *       {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>The store accessors {@code recentEmojiWeights()} and
     * {@code setRecentEmojiWeights(...)} already exist on {@code WhatsAppStore}
     * / {@code AbstractWhatsAppStore}; this handler is the sole writer.
     * @param client   the {@link WhatsAppClient} instance linked to the
     *                 mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof RecentEmojiWeightsAction action)) {
            return MutationApplicationResult.malformed();
        }

        var weights = action.weights(); // NO_WA_BASIS: full snapshot of the recent emoji weight list (already null-safe via the action accessor)
        client.store().setRecentEmojiWeights(weights);
        return MutationApplicationResult.success();
    }
}
