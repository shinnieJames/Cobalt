package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.ExternalWebBetaAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles external web beta actions.
 *
 * <p>This handler processes mutations that control external web beta enrollment
 * status. On SET, reads the {@code isOptIn} flag from the mutation value and
 * updates the store. Other operations are acknowledged as unsupported.
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
        return ExternalWebBetaAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ExternalWebBetaAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ExternalWebBetaAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof ExternalWebBetaAction action)) {
            return true;
        }

        client.store().setExternalWebBeta(action.isOptIn());
        return true;
    }
}
