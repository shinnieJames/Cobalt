package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles VoIP relay all calls setting actions.
 *
 * <p>This handler processes mutations that control whether all VoIP calls
 * should be relayed through WhatsApp servers. On SET, reads the
 * {@code isEnabled} flag and updates the store. Other operations are
 * acknowledged as unsupported.
 *
 * <p>Index format: ["setting_relayAllCalls"]
 */
public final class VoipRelayAllCallsHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code VoipRelayAllCallsHandler}.
     */
    public static final VoipRelayAllCallsHandler INSTANCE = new VoipRelayAllCallsHandler();

    private VoipRelayAllCallsHandler() {

    }

    @Override
    public String actionName() {
        return PrivacySettingRelayAllCalls.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return PrivacySettingRelayAllCalls.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return PrivacySettingRelayAllCalls.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof PrivacySettingRelayAllCalls action)) {
            return true;
        }

        client.store().setRelayAllCalls(action.isEnabled());
        return true;
    }
}
