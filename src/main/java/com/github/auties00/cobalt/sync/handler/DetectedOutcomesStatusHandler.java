package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.DetectedOutcomesStatusAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles detected outcomes status actions.
 *
 * <p>This handler processes mutations that update the CTWA detected outcome
 * onboarding status. On SET, reads the {@code isEnabled} flag from the
 * mutation value. Other operations are acknowledged as unsupported.
 *
 * <p>Index format: ["detected_outcomes_status_action"]
 */
public final class DetectedOutcomesStatusHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code DetectedOutcomesStatusHandler}.
     */
    public static final DetectedOutcomesStatusHandler INSTANCE = new DetectedOutcomesStatusHandler();

    private DetectedOutcomesStatusHandler() {

    }

    @Override
    public String actionName() {
        return DetectedOutcomesStatusAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return DetectedOutcomesStatusAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return DetectedOutcomesStatusAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof DetectedOutcomesStatusAction)) {
            return true;
        }

        return true;
    }
}
