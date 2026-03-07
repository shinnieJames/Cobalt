package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles waffle account link state actions.
 *
 * <p>Index format: ["waffle_account_link_state"]
 */
public final class WaffleAccountLinkStateHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code WaffleAccountLinkStateHandler}.
     */
    public static final WaffleAccountLinkStateHandler INSTANCE = new WaffleAccountLinkStateHandler();

    private WaffleAccountLinkStateHandler() {

    }

    @Override
    public String actionName() {
        return WaffleAccountLinkStateAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return WaffleAccountLinkStateAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return WaffleAccountLinkStateAction.ACTION_VERSION;
    }

    /**
     * Applies a waffle account link state mutation.
     *
     * <p>Per WhatsApp Web (WAWebWaffleAccountLinkStateSync), only SET is supported;
     * non-SET operations are acknowledged as unsupported. On SET, the web client
     * validates that {@code linkState} is non-null, then stores the link state
     * and requests a waffle linking nonce fetch.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged, {@code false} otherwise
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof WaffleAccountLinkStateAction action)) {
            return true;
        }

        if (action.linkState().isEmpty()) {
            return true;
        }

        return true;
    }
}
