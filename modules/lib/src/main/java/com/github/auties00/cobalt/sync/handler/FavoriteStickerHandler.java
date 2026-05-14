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
 * Handles favorite sticker actions.
 *
 * <p>This handler processes mutations that add or remove stickers from the
 * favorites collection. The handler routes mutations whose action name is
 * {@code "favoriteSticker"} (the {@code FavoriteSticker} entry in
 * {@code WASyncdConst.Actions}) and whose collection is {@code RegularLow}.
 *
 * <p>Per WhatsApp Web {@code WAWebStickersFavoriteSyncAction.applyMutations},
 * for each mutation:
 * <ol>
 *   <li>Non-{@code SET} operations are acknowledged with {@code UNSUPPORTED}.</li>
 *   <li>The sticker file hash is read from {@code indexParts[1]}; an empty or
 *       missing value yields {@code malformedActionIndex}.</li>
 *   <li>The decoded {@code stickerAction} sub-message must be present and the
 *       {@code isFavorite} flag must be present; otherwise the mutation is
 *       reported as {@code malformedActionValue}.</li>
 *   <li>If the {@code "favorite_sticker"} primary feature flag is not enabled,
 *       the mutation is reported as an {@code Orphan} with model id equal to
 *       the sticker hash and model type {@code "FavoriteSticker"}.</li>
 *   <li>When {@code isFavorite} is {@code true}, the sticker is added to the
 *       favorite-stickers collection if it is not already present (mirroring
 *       {@code FavoriteStickerCollection.addOrUpdateStickers}, which filters
 *       out stickers whose id is already in the collection).</li>
 *   <li>When {@code isFavorite} is {@code false}, the sticker is removed from
 *       the favorite-stickers collection if it is present (mirroring
 *       {@code FavoriteStickerCollection.removeAndSave}); a removal targeting
 *       a sticker that is not in the collection is a no-op success.</li>
 * </ol>
 *
 * <p>Index format: {@code ["favoriteSticker", stickerFileHash]}.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersFavoriteSyncAction")
public final class FavoriteStickerHandler implements WebAppStateActionHandler {
    /**
     * The {@code "favorite_sticker"} primary feature flag name.
     *
     * <p>Per WhatsApp Web {@code WAWebMiscGatingUtils.isFavoriteStickersEnabled}:
     * {@code function d() { return WAWebPrimaryFeatures.primaryFeatureEnabled("favorite_sticker"); }}.
     * The handler must consult the primary device's reported feature set rather
     * than any AB prop, since favorite-sticker sync is gated on the primary's
     * support for the feature, not on a per-companion experiment.
     */
    private static final String FAVORITE_STICKER_FEATURE = "favorite_sticker";

    /**
     * Constructs the singleton instance.
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
     * <p>Per WhatsApp Web {@code WAWebStickersFavoriteSyncAction.applyMutations}
     * (per-mutation closure):
     * <ol>
     *   <li>Non-{@code SET} operations short-circuit with {@code UNSUPPORTED}.</li>
     *   <li>The sticker hash is read from {@code indexParts[1]} ({@code u = t[1]}).
     *       An empty or missing value triggers {@code this.malformedActionIndex()}.</li>
     *   <li>The {@code stickerAction} sub-message ({@code c}) must be non-null,
     *       otherwise {@code WAWebSyncdIndexUtils.malformedActionValue} is returned.</li>
     *   <li>The {@code isFavorite} field ({@code g}) is destructured from the
     *       sub-message and must be non-null; otherwise the same
     *       {@code malformedActionValue} branch is taken.</li>
     *   <li>If {@code WAWebMiscGatingUtils.isFavoriteStickersEnabled()} returns
     *       {@code false}, the mutation is reported as
     *       {@code {Orphan, modelId: u, modelType: FavoriteSticker}}.</li>
     *   <li>If {@code isFavorite} is {@code true}, an existing entry for the same
     *       hash short-circuits with {@code Success}; otherwise a new
     *       {@code StickerModel} is constructed with the hash, direct path,
     *       fileEncSha256 (base64-decoded), mediaKey (base64-decoded if present),
     *       mediaKeyTimestamp set to the mutation timestamp, width, height and
     *       mimetype, then handed to
     *       {@code FavoriteStickerCollection.addOrUpdateStickers}.</li>
     *   <li>If {@code isFavorite} is {@code false}, the entry is looked up by
     *       hash; an absent entry yields a no-op {@code Success}, otherwise
     *       {@code FavoriteStickerCollection.removeAndSave(u)} is invoked.</li>
     *   <li>Any thrown error is captured and reported as {@code Failed}.</li>
     * </ol>
     *
     * <p>Cobalt's {@code action.isFavorite()} accessor coalesces a {@code null}
     * protobuf {@code isFavorite} field to {@code false} per the project rule
     * "use existing boolean accessors for nullable Boolean fields". This is an
     * intentional architectural difference from WA Web, which would treat the
     * same case as {@code malformedActionValue}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexArray = JSON.parseArray(mutation.index());
            // WAWebStickersFavoriteSyncAction.applyMutations: var u=t[1]; if(!u) return r.malformedActionIndex().
            // The slot-missing case must yield MALFORMED, not FAILED via the outer catch.
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
            // ADAPTED: WAWebStickersFavoriteSyncAction.applyMutations — WA Web checks if (g == null) on the protobuf isFavorite flag and returns malformedActionValue.
            // Cobalt's StickerAction.isFavorite() accessor coalesces a null protobuf field to false per the project's "no Optional<Boolean>" rule, so the malformed-on-null check is intentionally not replicated here.
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
                // ADAPTED: WAWebStickersFavoriteSyncAction.applyMutations — WA Web reads the entry first (var v = FavoriteStickerCollection.get(u)) and short-circuits with Success when absent, otherwise calls removeAndSave(u). Cobalt's removeFavouriteSticker is idempotent (returns Optional), so the explicit pre-check would be redundant; the observable outcome is identical.
                client.store().removeFavouriteSticker(stickerHash);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
