package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeightsAction;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles recent emoji weights actions.
 *
 * <p>This handler processes mutations that track frequently used emojis and their
 * weights. No dedicated WA Web sync handler module exists for this action type;
 * the web client stores emoji weights directly in its IndexedDB collection.
 */
public final class RecentEmojiWeightsHandler implements WebAppStateActionHandler {

    public static final RecentEmojiWeightsHandler INSTANCE = new RecentEmojiWeightsHandler();

    private RecentEmojiWeightsHandler() {
    }

    @Override
    public String actionName() {
        return RecentEmojiWeightsAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return RecentEmojiWeightsAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return RecentEmojiWeightsAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
