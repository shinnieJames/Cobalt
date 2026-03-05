package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles primary feature actions.
 *
 * <p>This handler processes mutations that communicate primary device feature flags.
 * The flags are acknowledged but not acted upon, as feature gating is managed
 * by other subsystems.
 *
 * <p>Index format: ["primary_feature"]
 */
public final class PrimaryFeatureHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code PrimaryFeatureHandler}.
     */
    public static final PrimaryFeatureHandler INSTANCE = new PrimaryFeatureHandler();

    private PrimaryFeatureHandler() {

    }

    @Override
    public String actionName() {
        return "primary_feature";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
