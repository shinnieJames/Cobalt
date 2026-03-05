package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles phone number for LID chat actions.
 *
 * <p>This handler processes mutations that associate a phone number with a
 * LID-based chat. The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["pnForLidChat"]
 */
public final class PnForLidChatHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code PnForLidChatHandler}.
     */
    public static final PnForLidChatHandler INSTANCE = new PnForLidChatHandler();

    private PnForLidChatHandler() {

    }

    @Override
    public String actionName() {
        return "pnForLidChat";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 8;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
