package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles settings sync actions.
 *
 * <p>Index format: ["settings_sync", ...]
 */
public final class SettingsSyncHandler implements WebAppStateActionHandler {
    public static final SettingsSyncHandler INSTANCE = new SettingsSyncHandler();

    private SettingsSyncHandler() {

    }

    @Override
    public String actionName() {
        return "settings_sync";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
