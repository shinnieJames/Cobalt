package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.StickerAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code favoriteSticker} app-state sync action that adds or
 * removes a sticker from the user's favourites collection.
 *
 * @apiNote
 * Drives the sticker-tray "favourite" star: the primary device fans out
 * each toggle through the {@link SyncPatchType#REGULAR_LOW} collection so
 * companions render the same favourites set. The mutation index keys
 * each entry by the sticker's filehash, formatted as
 * {@snippet :
 *     ["favoriteSticker", stickerFileHash]
 * }
 *
 * @implNote
 * This implementation gates the apply path on the primary device's
 * {@code "favorite_sticker"} {@link com.github.auties00.cobalt.model.feature.PrimaryFeature}
 * report rather than WA Web's
 * {@code WAWebMiscGatingUtils.isFavoriteStickersEnabled()}, which itself
 * delegates to {@code WAWebPrimaryFeatures.primaryFeatureEnabled}; the
 * effect is identical because Cobalt mirrors the primary feature set in
 * its store. Removal is unconditional via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#removeFavouriteSticker(String)}
 * because the call is idempotent, where WA Web pre-checks the entry and
 * short-circuits when absent.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersFavoriteSyncAction")
public final class FavoriteStickerHandler implements WebAppStateActionHandler {
    /**
     * The {@link com.github.auties00.cobalt.model.feature.PrimaryFeature}
     * id consulted to gate favourite-sticker sync.
     *
     * @apiNote
     * Mirrors the literal {@code "favorite_sticker"} that WA Web's
     * {@code WAWebMiscGatingUtils.isFavoriteStickersEnabled} resolves
     * through {@code primaryFeatureEnabled}; the gate is per-primary
     * (not per-companion) so reading the store's primary-feature set is
     * the correct source of truth.
     */
    private static final String FAVORITE_STICKER_FEATURE = "favorite_sticker";

    /**
     * Constructs a new singleton {@link FavoriteStickerHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public FavoriteStickerHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return StickerAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return StickerAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return StickerAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation classifies a missing index slot as
     * {@link MutationApplicationResult#malformed()} explicitly so the
     * outer try/catch does not turn the malformed index into
     * {@link MutationApplicationResult#failed()} via an
     * {@code IndexOutOfBoundsException} from {@code JSON.parseArray}.
     * The {@link StickerAction#isFavorite()} accessor coalesces a
     * {@code null} protobuf field to {@code false} per the project's
     * "no Optional&lt;Boolean&gt;" rule, so the
     * {@code malformedActionValue} branch that WA Web takes when
     * {@code isFavorite} is {@code null} is not replicated. The set
     * branch fast-paths an existing entry through
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#findFavouriteSticker(String)}
     * mirroring WA Web's
     * {@code FavoriteStickerCollection.get(u)} short-circuit.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() <= 1) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var stickerHash = indexArray.getString(1);
            if (stickerHash == null || stickerHash.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof StickerAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }
            if (!client.store().primaryFeatures().contains(FAVORITE_STICKER_FEATURE)) {
                return MutationApplicationResult.orphan(stickerHash, "FavoriteSticker");
            }

            if (action.isFavorite()) {
                if (client.store().findFavouriteSticker(stickerHash).isPresent()) {
                    return MutationApplicationResult.success();
                }
                var sticker = action.toSticker();
                sticker.setTimestamp(mutation.timestamp().getEpochSecond());
                client.store().addFavouriteSticker(stickerHash, sticker);
            } else {
                client.store().removeFavouriteSticker(stickerHash);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
