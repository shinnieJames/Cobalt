package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles share own phone number actions.
 *
 * <p>This handler processes mutations that control whether the user's phone number
 * is shared with a specific contact. The mutation is acknowledged but not applied
 * locally.
 *
 * <p>Index format: ["shareOwnPn", "chatJid", "pnJid"]
 */
public final class ShareOwnPnHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ShareOwnPnHandler}.
     */
    public static final ShareOwnPnHandler INSTANCE = new ShareOwnPnHandler();

    private ShareOwnPnHandler() {

    }

    @Override
    public String actionName() {
        return "shareOwnPn";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 8;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
