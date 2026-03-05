package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles VoIP relay all calls setting actions.
 *
 * <p>Index format: ["setting_relayAllCalls", ...]
 */
public final class VoipRelayAllCallsHandler implements WebAppStateActionHandler {
    public static final VoipRelayAllCallsHandler INSTANCE = new VoipRelayAllCallsHandler();

    private VoipRelayAllCallsHandler() {

    }

    @Override
    public String actionName() {
        return "setting_relayAllCalls";
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
