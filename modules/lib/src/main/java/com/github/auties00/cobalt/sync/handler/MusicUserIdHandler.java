package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.MusicUserIdAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies {@link MusicUserIdAction} sync mutations carrying the user's
 * per-provider music account identifiers.
 *
 * @apiNote
 * Persists the user's resolved {@code (musicUserId, musicUserIdMap)} on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} so per-provider
 * music identifiers are visible across linked devices. Only {@code SET}
 * is accepted; non-{@code SET} operations and payloads where both
 * {@code musicUserId} and {@code musicUserIdMap} are empty are reported
 * as {@link MutationApplicationResult#unsupported()} and
 * {@link MutationApplicationResult#malformed()} respectively.
 *
 * @implNote
 * This implementation has no WA Web counterpart. The
 * {@code SyncActionValue.MusicUserIdAction} protobuf is declared in
 * {@code WAWebProtobufSyncAction.pb} (the search-code lookup of
 * {@code "music_user_id"} returns exactly that one module), but no
 * {@code WAWebMusicUserIdSync} module ships in the current snapshot
 * and the action is absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, so WA Web
 * silently drops any incoming mutation. The handler exists in Cobalt
 * as a forward-looking implementation that follows the same singleton
 * + {@code applyMutation} contract as every other registered handler;
 * the {@link SyncPatchType#REGULAR} collection and the
 * empty-payload-rejects-as-malformed shape are inferred from sibling
 * identifier-style handlers and from the protobuf shape itself.
 */
public final class MusicUserIdHandler implements WebAppStateActionHandler {

    /**
     * Constructs the music-user-id sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; the handler holds no
     * AB-prop / store / WAM dependency.
     */
    public MusicUserIdHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return MusicUserIdAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR} as a
     * forward-looking default, because no WA Web handler module exists
     * to consult; sibling identifier-style handlers use the same
     * collection.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int version() {
        return MusicUserIdAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation accepts only {@link SyncdOperation#SET}: the
     * action is identifier-style (a single string and/or a string-keyed
     * map) and there is no semantic for {@code REMOVE}. A mutation whose
     * resolved {@link MusicUserIdAction} has both {@code musicUserId}
     * empty AND {@code musicUserIdMap} empty is rejected as
     * {@link MutationApplicationResult#malformed()} so a
     * default-constructed protobuf does not silently overwrite the
     * stored identifiers. On success the resolved action is written via
     * {@code WhatsAppStore.setMusicUserIdState}.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof MusicUserIdAction action)) {
            return MutationApplicationResult.malformed();
        }

        if (action.musicUserId().isEmpty() && action.musicUserIdMap().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setMusicUserIdState(action);
        return MutationApplicationResult.success();
    }
}
