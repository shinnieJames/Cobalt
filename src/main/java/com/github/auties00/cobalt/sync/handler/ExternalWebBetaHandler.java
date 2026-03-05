package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles external web beta actions.
 *
 * <p>This handler processes mutations that control external web beta enrollment
 * status. The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["external_web_beta"]
 */
public final class ExternalWebBetaHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ExternalWebBetaHandler}.
     */
    public static final ExternalWebBetaHandler INSTANCE = new ExternalWebBetaHandler();

    private ExternalWebBetaHandler() {

    }

    @Override
    public String actionName() {
        return "external_web_beta";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
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
