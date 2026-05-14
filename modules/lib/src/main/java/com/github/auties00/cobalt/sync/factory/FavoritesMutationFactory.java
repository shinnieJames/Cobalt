package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesAction;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesActionBuilder;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesActionFavoriteBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing favourites sync mutations.
 *
 * <p>Mirrors the {@code getFavoritesMutation} export of WhatsApp Web's
 * {@code WAWebFavoritesSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.FavoritesHandler}.
 */
public final class FavoritesMutationFactory {
    /**
     * Constructs a favourites mutation factory.
     */
    public FavoritesMutationFactory() {

    }

    /**
     * Builds a pending mutation for syncing local favorites changes to the server.
     *
     * <p>Per WhatsApp Web {@code WAWebFavoritesSync.getFavoritesMutation}: takes
     * the current list of favorites with order indices, resolves each to its
     * mutation index JID, sorts by order index, and builds a SET mutation
     * containing the full favorites list.
     *
     * <p>In WA Web, each favorite is resolved via {@code getWidMutationIndexForWid}
     * which converts user JIDs to their LID mutation index when LID migration is
     * active. In Cobalt, the JID is used directly as the mutation index since the
     * LID mapping is handled by the store layer.
     *
     * @param favoriteJids the ordered list of favorite chat JIDs
     * @param timestamp    the mutation timestamp
     * @return the pending mutation for the favorites action
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getFavoritesMutation(List<Jid> favoriteJids, Instant timestamp) {
        var favoriteEntries = favoriteJids.stream()
                .map(jid -> new FavoritesActionFavoriteBuilder()
                        .id(jid.toString())
                        .build())
                .toList();
        var action = new FavoritesActionBuilder()
                .favorites(favoriteEntries)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .favoritesAction(action)
                .build();
        var index = JSON.toJSONString(List.of(FavoritesAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                FavoritesAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
