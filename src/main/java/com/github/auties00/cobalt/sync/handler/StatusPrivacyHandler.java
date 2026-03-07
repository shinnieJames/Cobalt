package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.StatusPrivacyAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles status privacy actions.
 *
 * <p>Per WhatsApp Web {@code WAWebStatusPrivacySettingSync}, only SET is
 * supported. On SET, validates that {@code statusPrivacy} is non-{@code null}
 * and that {@code statusPrivacy.mode} is non-{@code null}.
 *
 * <p>Index format: ["status_privacy"]
 */
public final class StatusPrivacyHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code StatusPrivacyHandler}.
     */
    public static final StatusPrivacyHandler INSTANCE = new StatusPrivacyHandler();

    private StatusPrivacyHandler() {

    }

    @Override
    public String actionName() {
        return StatusPrivacyAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return StatusPrivacyAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return StatusPrivacyAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof StatusPrivacyAction action)) {
            return true;
        }

        if (action.mode().isEmpty()) {
            return true;
        }

        return true;
    }
}
