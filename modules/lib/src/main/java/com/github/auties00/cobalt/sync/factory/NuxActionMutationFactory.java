package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.device.NuxAction;
import com.github.auties00.cobalt.model.sync.action.device.NuxActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing NUX-action sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.NuxActionHandler}.
 */
public final class NuxActionMutationFactory {
    /**
     * Constructs a NUX-action mutation factory.
     */
    public NuxActionMutationFactory() {

    }

    /**
     * Builds a pending mutation for acknowledging or unacknowledging a NUX
     * item.
     *
     * <p>Per WhatsApp Web {@code WAWebNuxSync.$NuxSync$p_1}:
     * <ol>
     *   <li>Wraps the {@code acknowledged} flag in a
     *       {@code {nuxAction: {acknowledged}}} value</li>
     *   <li>Calls {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       {@code collection}, {@code indexArgs = [nuxKey]}, value,
     *       version, operation {@code SET}, and the supplied timestamp</li>
     * </ol>
     *
     * @param nuxKey       the NUX identifier (the {@code indexArgs[0]} entry)
     * @param timestamp    the mutation timestamp
     * @param acknowledged whether the NUX item is acknowledged
     * @return the pending mutation for the NUX action
     */
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "$NuxSync$p_1", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getNuxMutation(String nuxKey, Instant timestamp, boolean acknowledged) {
        Objects.requireNonNull(nuxKey, "nuxKey cannot be null"); // ADAPTED: defensive null check not present in WA Web
        Objects.requireNonNull(timestamp, "timestamp cannot be null"); // ADAPTED: defensive null check not present in WA Web
        var action = new NuxActionBuilder()
                .acknowledged(acknowledged)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .nuxAction(action)
                .build();
        var index = JSON.toJSONString(List.of(NuxAction.ACTION_NAME, nuxKey));
        var pendingMutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                NuxAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pendingMutation, 0);
    }
}
