package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles waffle account link state actions.
 *
 * <p>Index format: ["waffle_account_link_state", ...]
 */
public final class WaffleAccountLinkStateHandler implements WebAppStateActionHandler {
    public static final WaffleAccountLinkStateHandler INSTANCE = new WaffleAccountLinkStateHandler();

    private WaffleAccountLinkStateHandler() {

    }

    @Override
    public String actionName() {
        return "waffle_account_link_state";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
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
