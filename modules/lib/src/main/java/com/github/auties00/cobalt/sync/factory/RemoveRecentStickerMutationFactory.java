package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerAction;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing remove-recent-sticker sync mutations.
 *
 * <p>Mirrors the {@code generateRemoveStickerMutation} export of WhatsApp
 * Web's {@code WAWebStickersRemoveRecentSyncAction} module. The factory is
 * the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.RemoveRecentStickerHandler}.
 */
public final class RemoveRecentStickerMutationFactory {
    /**
     * Constructs a remove-recent-sticker mutation factory.
     */
    public RemoveRecentStickerMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation that removes a sticker from the
     * recent-stickers collection across linked devices.
     *
     * <p>Per WhatsApp Web {@code WAWebStickersRemoveRecentSyncAction}: emits a
     * SET mutation at {@code ["removeRecentSticker", stickerFileHash]} in the
     * REGULAR_LOW collection with {@code version = 7} and a
     * {@code removeRecentStickerAction} sub-message carrying the current
     * timestamp as {@code lastStickerSentTs}. Receiving devices compare this
     * timestamp against their local recent-sticker entry to decide whether to
     * remove it.
     *
     * @param stickerHash the sticker file hash used as the mutation index
     * @return the pending mutation ready to be pushed via
     *         {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "generateRemoveStickerMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getRemoveRecentStickerMutation(String stickerHash) {
        var timestamp = Instant.now();
        var action = new RemoveRecentStickerActionBuilder()
                .lastStickerSentTs(timestamp)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .removeRecentStickerAction(action)
                .build();
        var index = JSON.toJSONString(List.of(RemoveRecentStickerAction.ACTION_NAME, stickerHash));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                RemoveRecentStickerAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
