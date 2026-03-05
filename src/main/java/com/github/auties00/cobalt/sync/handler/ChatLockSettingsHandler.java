package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles chat lock settings actions.
 *
 * <p>This handler processes mutations related to the global chat lock settings
 * (e.g., whether chat locking is enabled, the secret code hash). The mutation
 * is acknowledged but not applied locally.
 *
 * <p>Index format: ["setting_chatLock"]
 */
public final class ChatLockSettingsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ChatLockSettingsHandler}.
     */
    public static final ChatLockSettingsHandler INSTANCE = new ChatLockSettingsHandler();

    private ChatLockSettingsHandler() {

    }

    @Override
    public String actionName() {
        return "setting_chatLock";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
