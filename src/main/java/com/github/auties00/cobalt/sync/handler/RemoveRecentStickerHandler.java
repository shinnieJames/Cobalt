package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles remove recent sticker actions.
 *
 * <p>This handler processes mutations that remove stickers from the recent list.
 *
 * <p>Index format: ["removeRecentStickerAction", "stickerHash"]
 */
public final class RemoveRecentStickerHandler implements WebAppStateActionHandler {

    public static final RemoveRecentStickerHandler INSTANCE = new RemoveRecentStickerHandler();

    private RemoveRecentStickerHandler() {
    }

    @Override
    public String actionName() {
        return RemoveRecentStickerAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return RemoveRecentStickerAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return RemoveRecentStickerAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // Web: WAWebStickersRemoveRecentSyncAction — only SET is supported
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var stickerHash = indexArray.getString(1);
        if (stickerHash == null) {
            return false;
        }

        // Web: returns Orphan if sticker is not in the collection
        var sticker = client.store().findRecentSticker(stickerHash);
        if (sticker.isEmpty()) {
            return false;
        }

        // Web: reads removeRecentStickerAction?.lastStickerSentTs
        // If action is missing, lastStickerSentTs is null and sticker is still removed
        var action = mutation.value().action().orElse(null) instanceof RemoveRecentStickerAction a ? a : null;
        var lastStickerSentTs = action != null
                ? action.lastStickerSentTs().map(java.time.Instant::getEpochSecond).orElse(null)
                : null;
        var stickerTimestamp = sticker.get().timestamp().orElse(0L);
        if (lastStickerSentTs == null || stickerTimestamp <= lastStickerSentTs) {
            client.store().removeRecentSticker(stickerHash);
        }

        return true;
    }
}
