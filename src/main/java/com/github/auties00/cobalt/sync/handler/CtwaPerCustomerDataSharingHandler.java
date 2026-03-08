package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles CTWA per-customer data sharing actions.
 *
 * <p>Per WhatsApp Web {@code WAWebCtwaPerCustomerDataSharingSync}, SET and
 * REMOVE are supported. On SET, validates that {@code indexParts[1]}
 * (accountLid) is non-{@code null} and that
 * {@code ctwaPerCustomerDataSharingAction.isCtwaPerCustomerDataSharingEnabled}
 * is non-{@code null}. On REMOVE, succeeds unconditionally.
 *
 * <p>Index format: ["ctwaPerCustomerDataSharing", "accountLid"]
 */
public final class CtwaPerCustomerDataSharingHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code CtwaPerCustomerDataSharingHandler}.
     */
    public static final CtwaPerCustomerDataSharingHandler INSTANCE = new CtwaPerCustomerDataSharingHandler();

    private CtwaPerCustomerDataSharingHandler() {

    }

    @Override
    public String actionName() {
        return CtwaPerCustomerDataSharingAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return CtwaPerCustomerDataSharingAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return CtwaPerCustomerDataSharingAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());

        switch (mutation.operation()) {
            case SET -> {
                var accountLid = indexArray.getString(1);
                if (accountLid == null) {
                    return true;
                }

                if (!(mutation.value().action().orElse(null) instanceof CtwaPerCustomerDataSharingAction action)) {
                    return true;
                }

                client.store().setCtwaDataSharingEnabled(action.isCtwaPerCustomerDataSharingEnabled());
            }
            case REMOVE -> client.store().setCtwaDataSharingEnabled(false);
        }

        return true;
    }
}
