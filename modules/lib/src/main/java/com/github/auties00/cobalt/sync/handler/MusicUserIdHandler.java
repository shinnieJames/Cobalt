package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.MusicUserIdAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles {@link MusicUserIdAction} sync mutations ({@code "music_user_id"}).
 *
 * <p>Each mutation carries either a single {@code musicUserId} string or a
 * {@code musicUserIdMap} of provider {@code -> userId} entries. The whole
 * action object is persisted on the local {@code WhatsAppStore} via
 * {@code setMusicUserIdState}. Only {@code SET} operations are accepted; any
 * other operation maps to {@link MutationApplicationResult#unsupported()} and
 * a missing, wrong-typed, or completely empty value maps to
 * {@link MutationApplicationResult#malformed()}.
 *
 * <p><b>NO_WA_BASIS:</b> The {@code SyncActionValue.MusicUserIdAction}
 * protobuf is defined in {@code WAWebProtobufSyncAction.pb} (exported as
 * {@code SyncActionValue$MusicUserIdActionSpec}) with two optional fields
 * ({@code musicUserId: string} at index {@code 1} and
 * {@code musicUserIdMap: map<string, string>} at index {@code 2}), but the
 * current WA Web snapshot does <em>not</em> ship a corresponding sync handler
 * module (no {@code WAWebMusicUserIdSync}). The action is also absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, the registry consumed
 * by {@code WAWebSyncdGetActionHandler.setActionHandlers}, so WA Web would
 * never dispatch any incoming mutation with this action via
 * {@code WAWebSyncdGetActionHandler.getActionHandler("music_user_id")} (the
 * lookup would return {@code undefined} and the mutation would be skipped).
 * The closest WA Web modules that touch the music surface
 * ({@code WAWebMusicParsingUtils}, {@code WAWebMusicGatingUtils},
 * {@code WAWebMusicPlaybackUtils}, {@code WAWebMusicUserPrefs},
 * {@code WAWebMusicConsumptionEligibilityUpdater},
 * {@code WAWebMusicEligibleCountriesProvider},
 * {@code WAWebFetchMusicEligibleCountries},
 * {@code WAWebUpdateMusicBlocklistAction},
 * {@code WAWebSNAPLUploadMusicConsumptionLogs},
 * {@code WAWebMediaDownloadMmsMusicArtwork}) all deal with music consumption
 * eligibility, country gating, parsing, playback or media download — none of
 * them consume {@code SyncActionValue.MusicUserIdAction}.
 *
 * <p>The Cobalt handler is a forward-looking implementation: it follows the
 * Cobalt sync handler conventions used by every other registered handler
 * (singleton, {@code applyMutation} producing a typed
 * {@link MutationApplicationResult}, eager store update on {@code SET}). Every
 * behavioural step here is Cobalt-inferred until WA Web ships the matching
 * {@code WAWebMusicUserIdSync} module.
 */
public final class MusicUserIdHandler implements WebAppStateActionHandler {

    /**
     * Private constructor that enforces the singleton pattern.
     */
    public MusicUserIdHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "music_user_id"} string
     */
    @Override
    public String actionName() {
        return MusicUserIdAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link SyncPatchType#REGULAR} as an inferred default.
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR; // NO_WA_BASIS: no WA Web sync handler declares a collection for "music_user_id"; REGULAR matches sibling identifier-style handlers
    }

    /**
     * {@inheritDoc}
     * @return the integer version constant declared on the action class
     */
    @Override
    public int version() {
        return MusicUserIdAction.ACTION_VERSION;
    }

    /**
     * Applies a music user id mutation and returns the detailed outcome.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only {@code SET}
     *       mutations are accepted; the action carries an identifier-style
     *       payload (a single string and/or a string-keyed map) and there is
     *       no semantic for {@code REMOVE}.</li>
     *   <li>Resolve the mutation value to a {@link MusicUserIdAction}; if the
     *       value is missing or of the wrong type, return
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li>Reject mutations where both {@code musicUserId} and
     *       {@code musicUserIdMap} are empty by returning
     *       {@link MutationApplicationResult#malformed()}: at least one of
     *       the two protobuf fields must be present for the mutation to carry
     *       any meaningful update.</li>
     *   <li>Persist the resolved {@link MusicUserIdAction} on the store via
     *       {@code WhatsAppStore.setMusicUserIdState} and return
     *       {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>The store accessors {@code musicUserIdState()} and
     * {@code setMusicUserIdState(...)} already exist on
     * {@code WhatsAppStore} / {@code AbstractWhatsAppStore}; this handler is
     * the sole writer.
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
