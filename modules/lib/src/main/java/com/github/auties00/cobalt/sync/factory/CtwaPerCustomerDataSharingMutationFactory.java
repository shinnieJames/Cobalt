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
 * Builds outgoing app-state mutations that record a Click-To-WhatsApp per-customer data-sharing preference.
 *
 * @apiNote
 * Drives the SMB "share my data with the advertiser" toggle that the WA Web
 * CTWA system-message surface ({@code maybeGeneratePerCustomerDataSharingSystemMessage})
 * exposes per customer LID. The mutation populates the
 * {@code data-sharing-3pd-lid-v2} table on the primary device and on every
 * linked device. The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.CtwaPerCustomerDataSharingHandler}.
 */
public final class CtwaPerCustomerDataSharingMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public CtwaPerCustomerDataSharingMutationFactory() {

    }

    /**
     * Returns a SET mutation that records the per-customer data-sharing preference for the given account LID.
     *
     * @apiNote
     * The mutation index follows
     * {@snippet :
     *     ["adsCtwaPerCustomerDataSharing", accountLid.toString()]
     * }
     * and the {@link CtwaPerCustomerDataSharingAction} sub-message carries
     * {@code isCtwaPerCustomerDataSharingEnabled}. The receive-side handler
     * upserts the row and emits a CTWA system message tagged with
     * {@code SYNCD_MUTATION} as the entry point.
     *
     * @implNote
     * This implementation captures the timestamp via {@link Instant#now()};
     * WA Web's {@code WAWebCtwaPerCustomerDataSharingSync.getCtwaPerCustomerDataSharingMutation}
     * uses {@code WATimeUtils.unixTimeMs()} for the same purpose.
     *
     * @param accountLid the customer's LID-form {@link Jid}
     * @param isEnabled  {@code true} to opt the customer into per-customer data sharing, {@code false} to opt them out
     * @return the pending mutation ready to be queued for outbound app-state sync
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
