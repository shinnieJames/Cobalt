package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles status privacy actions.
 *
 * <p>This handler processes mutations that control the privacy settings for
 * status updates (e.g., who can see the user's status). The mutation is
 * acknowledged but not applied locally.
 *
 * <p>Index format: ["status_privacy"]
 */
public final class StatusPrivacyHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code StatusPrivacyHandler}.
     */
    public static final StatusPrivacyHandler INSTANCE = new StatusPrivacyHandler();

    private StatusPrivacyHandler() {

    }

    @Override
    public String actionName() {
        return "status_privacy";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
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
