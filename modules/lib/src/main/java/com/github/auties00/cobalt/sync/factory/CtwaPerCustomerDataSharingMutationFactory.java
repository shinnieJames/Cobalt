package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing CTWA-per-customer-data-sharing sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.CtwaPerCustomerDataSharingHandler}.
 */
public final class CtwaPerCustomerDataSharingMutationFactory {
    /**
     * Constructs a CTWA-per-customer-data-sharing mutation factory.
     */
    public CtwaPerCustomerDataSharingMutationFactory() {

    }

    /**
     * Builds a pending mutation for setting or clearing the CTWA per-customer
     * data sharing preference for an account.
     *
     * <p>Per WhatsApp Web {@code WAWebCtwaPerCustomerDataSharingSync.getCtwaPerCustomerDataSharingMutation}:
     * constructs a {@code SyncActionValue} with a {@code ctwaPerCustomerDataSharingAction}
     * containing the enabled flag, then delegates to
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with the handler's collection,
     * index args, version, and a SET operation.
     *
     * @param accountLid the account LID identifying the customer
     * @param isEnabled  whether per-customer data sharing is enabled
     * @return the pending mutation ready to be queued for sync
     */
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "getCtwaPerCustomerDataSharingMutation", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPendingMutation getCtwaPerCustomerDataSharingMutation(Jid accountLid, boolean isEnabled) {
        var timestamp = Instant.now();
        var action = new CtwaPerCustomerDataSharingActionBuilder()
                .isCtwaPerCustomerDataSharingEnabled(isEnabled)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .ctwaPerCustomerDataSharingAction(action)
                .build();
        var index = JSON.toJSONString(List.of(CtwaPerCustomerDataSharingAction.ACTION_NAME, accountLid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                CtwaPerCustomerDataSharingAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
