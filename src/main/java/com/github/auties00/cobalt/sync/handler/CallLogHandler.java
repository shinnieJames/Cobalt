package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles call log actions.
 *
 * <p>Index format: ["call_log", "peerJid", "callId", "isFromMe"]
 */
public final class CallLogHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code CallLogHandler}.
     */
    public static final CallLogHandler INSTANCE = new CallLogHandler();

    private CallLogHandler() {

    }

    @Override
    public String actionName() {
        return CallLogAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return CallLogAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return CallLogAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() == SyncdOperation.SET) {
            if (!(mutation.value().action().orElse(null) instanceof CallLogAction action)) {
                return true;
            }

            if (action.log().isEmpty()) {
                return true;
            }

            return true;
        }

        if (mutation.operation() == SyncdOperation.REMOVE) {
            return true;
        }

        return true;
    }
}
