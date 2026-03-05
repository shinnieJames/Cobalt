package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles call log actions.
 *
 * <p>Index format: ["call_log", ...]
 */
public final class CallLogHandler implements WebAppStateActionHandler {
    public static final CallLogHandler INSTANCE = new CallLogHandler();

    private CallLogHandler() {

    }

    @Override
    public String actionName() {
        return "call_log";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
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
