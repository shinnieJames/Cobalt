package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles label reordering actions.
 *
 * <p>This handler processes mutations that reorder chat labels. The reordering
 * is acknowledged but not applied locally, as Cobalt does not currently maintain
 * a label ordering model.
 *
 * <p>Index format: ["label_reordering"]
 */
public final class LabelReorderingHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code LabelReorderingHandler}.
     */
    public static final LabelReorderingHandler INSTANCE = new LabelReorderingHandler();

    private LabelReorderingHandler() {

    }

    @Override
    public String actionName() {
        return "label_reordering";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 3;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
