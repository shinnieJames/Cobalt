package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles LID contact actions.
 *
 * <p>LID contact handling is complex and requires dedicated model support.
 * This handler acknowledges the mutation without further processing.
 *
 * <p>Index format: ["lid_contact", ...]
 */
public final class LidContactHandler implements WebAppStateActionHandler {
    public static final LidContactHandler INSTANCE = new LidContactHandler();

    private LidContactHandler() {

    }

    @Override
    public String actionName() {
        return "lid_contact";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.CRITICAL_UNBLOCK_LOW;
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
