package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles detected outcomes status actions.
 *
 * <p>Index format: ["detected_outcomes_status_action", ...]
 */
public final class DetectedOutcomesStatusHandler implements WebAppStateActionHandler {
    public static final DetectedOutcomesStatusHandler INSTANCE = new DetectedOutcomesStatusHandler();

    private DetectedOutcomesStatusHandler() {

    }

    @Override
    public String actionName() {
        return "detected_outcomes_status_action";
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
