package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles recent emoji weights actions.
 *
 * <p>This handler processes mutations that track frequently used emojis and their weights.
 */
public final class RecentEmojiWeightsHandler implements WebAppStateActionHandler {

    public static final RecentEmojiWeightsHandler INSTANCE = new RecentEmojiWeightsHandler();

    private RecentEmojiWeightsHandler() {
    }

    @Override
    public String actionName() {
        return "recent_emoji_weights_action";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 11;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // Not handled
        return true;
    }
}
