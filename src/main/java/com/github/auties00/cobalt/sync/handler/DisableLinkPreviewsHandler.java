package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles disable link previews setting actions.
 *
 * <p>This handler processes mutations that control whether link previews are
 * disabled in chat messages. The mutation is acknowledged but not applied
 * locally.
 *
 * <p>Index format: ["setting_disableLinkPreviews"]
 */
public final class DisableLinkPreviewsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code DisableLinkPreviewsHandler}.
     */
    public static final DisableLinkPreviewsHandler INSTANCE = new DisableLinkPreviewsHandler();

    private DisableLinkPreviewsHandler() {

    }

    @Override
    public String actionName() {
        return "setting_disableLinkPreviews";
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
