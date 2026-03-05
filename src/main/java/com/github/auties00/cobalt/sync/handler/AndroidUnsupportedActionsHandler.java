package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles Android unsupported actions.
 *
 * <p>This handler processes mutations that declare which actions are unsupported
 * on Android devices. The allowed flags are acknowledged but not acted upon,
 * as this client does not need to enforce Android-specific restrictions.
 *
 * <p>Index format: ["android_unsupported_actions"]
 */
public final class AndroidUnsupportedActionsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code AndroidUnsupportedActionsHandler}.
     */
    public static final AndroidUnsupportedActionsHandler INSTANCE = new AndroidUnsupportedActionsHandler();

    private AndroidUnsupportedActionsHandler() {

    }

    @Override
    public String actionName() {
        return "android_unsupported_actions";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 4;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
