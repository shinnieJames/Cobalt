package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.StickerAction;
import com.github.auties00.cobalt.model.sync.action.media.StickerActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing favourite-sticker sync mutations.
 *
 * <p>Mirrors the {@code generateFavoriteSyncMutation} export of WhatsApp
 * Web's {@code WAWebStickersFavoriteSyncAction} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.FavoriteStickerHandler}.
 */
public final class FavoriteStickerMutationFactory {
    /**
     * Constructs a favourite-sticker mutation factory.
     */
    public FavoriteStickerMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation for favouriting or unfavouriting a
     * sticker identified by its file hash.
     *
     * <p>Per WhatsApp Web {@code WAWebFavoriteStickerSyncActionUtils.getFavoriteStickerMutation}:
     * constructs the {@code stickerAction} sub-message with at minimum the
     * {@code isFavorite} flag and dispatches it at
     * {@code ["favoriteSticker", stickerFileHash]} in the REGULAR_LOW
     * collection with {@code version = 7}.
     *
     * <p>Outgoing favourite mutations only need to carry the
     * {@code isFavorite} flag; the full media descriptor is propagated from
     * the original sticker record on the primary device when the mutation
     * round-trips through app-state sync, so this helper intentionally leaves
     * the media fields unset.
     *
     * @param stickerHash the sticker file hash used as the mutation index
     * @param favorite    {@code true} to mark the sticker as favourite,
     *                    {@code false} to unfavourite it
     * @return the pending mutation ready to be pushed via
     *         {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersFavoriteSyncAction", exports = "generateFavoriteSyncMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getFavoriteStickerMutation(String stickerHash, boolean favorite) {
        var action = new StickerActionBuilder()
                .isFavorite(favorite)
                .build();
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .stickerAction(action)
                .build();
        var index = JSON.toJSONString(List.of(StickerAction.ACTION_NAME, stickerHash));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                StickerAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
