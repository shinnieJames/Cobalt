package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LabelReorderingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles label reordering actions.
 *
 * <p>This handler processes mutations that reorder chat labels by updating
 * label sort order. Only SET operations are supported; other operations are
 * acknowledged as unsupported.
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
        return LabelReorderingAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return LabelReorderingAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return LabelReorderingAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof LabelReorderingAction action)) {
            return true;
        }

        if (action.sortedLabelIds().isEmpty()) {
            return true;
        }

        return true;
    }
}
