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
 * @apiNote
 * Drives the long-press "remove from recents" gesture on the stickers
 * tray; the WA Web entry point is
 * {@code WAWebRemoveStickerJob} which wraps a single
 * {@code generateRemoveStickerMutation} call in a
 * {@code lockForSync} transaction. Mutations produced here are consumed
 * on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.RemoveRecentStickerHandler}.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebStickersRemoveRecentSyncAction.generateRemoveStickerMutation}.
 * The receiver-side branch gates application on
 * {@code WAWebMiscGatingUtils.isRecentStickersMDEnabled} and only
 * removes the local entry when its timestamp is at most the carried
 * {@code lastStickerSentTs} value.
 */
public final class RemoveRecentStickerMutationFactory {
    /**
     * Constructs a remove-recent-sticker mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public recent-sticker-removal entry point. The
     * factory keeps no state, so a single instance is sufficient per
     * client.
     */
    public RemoveRecentStickerMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation that removes a sticker from the
     * recent-stickers collection across linked devices.
     *
     * @apiNote
     * Invoked from the public recent-sticker-removal entry point; the
     * receiver compares the carried {@code lastStickerSentTs} against
     * its own recent-sticker entry's timestamp and only removes it when
     * the local entry is at most as recent as the carried value. This
     * ensures a sticker sent on another device after this remove arrives
     * is not retroactively dropped.
     *
     * @implNote
     * This implementation stamps {@link Instant#now()} on both the
     * outer mutation timestamp and the inner
     * {@link RemoveRecentStickerAction#lastStickerSentTs()}, matching
     * {@code WAWebStickersRemoveRecentSyncAction.generateRemoveStickerMutation}'s
     * single call to {@code WATimeUtils.unixTimeMs}. The index follows
     * the standard {@code [actionName, stickerFileHash]} shape and
     * writes into the {@code RegularLow} collection.
     *
     * @param stickerHash the sticker file hash used as the mutation
     *                    index; matches the
     *                    {@code RecentStickerCollectionMd} key on the
     *                    receiver
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
