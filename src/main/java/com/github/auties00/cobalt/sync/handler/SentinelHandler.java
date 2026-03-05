package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles sentinel actions for sync key expiration.
 *
 * <p>This handler processes mutations that signal sync key expiration. The actual
 * key expiration logic is handled elsewhere in the key management subsystem; this
 * handler acknowledges the mutation.
 *
 * <p>Index format: ["sentinel"]
 */
public final class SentinelHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code SentinelHandler}.
     */
    public static final SentinelHandler INSTANCE = new SentinelHandler();

    private SentinelHandler() {

    }

    @Override
    public String actionName() {
        return "sentinel";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 3;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
