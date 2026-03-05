package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles bot welcome request actions.
 *
 * <p>This handler processes mutations related to bot welcome request state.
 * The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["bot_welcome_request"]
 */
public final class BotWelcomeRequestHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code BotWelcomeRequestHandler}.
     */
    public static final BotWelcomeRequestHandler INSTANCE = new BotWelcomeRequestHandler();

    private BotWelcomeRequestHandler() {

    }

    @Override
    public String actionName() {
        return "bot_welcome_request";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 2;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
