package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.action.call.CallLogActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing call-log sync mutations.
 *
 * <p>Mirrors the {@code getCallLogMutation} export of WhatsApp Web's
 * {@code WAWebCallLogSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.CallLogHandler}.
 */
public final class CallLogMutationFactory {
    /**
     * Constructs a call-log mutation factory.
     */
    public CallLogMutationFactory() {

    }

    /**
     * Builds a pending mutation for syncing an outgoing call log record.
     *
     * <p>Per WhatsApp Web {@code WAWebCallLogSync.getCallLogMutation}:
     * <ol>
     *   <li>Determines the caller JID: uses {@code callCreatorJid} from the
     *       record if present, otherwise falls back to the current user's
     *       device PN JID (when {@code fromMe} is {@code true}) or the
     *       {@code peerJid}</li>
     *   <li>Builds the mutation index as
     *       {@code [action, callerJid, callId, fromMe ? "1" : "0"]}</li>
     *   <li>Wraps the record in a {@code callLogAction} value</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation}</li>
     * </ol>
     *
     * <p>In Cobalt, the caller must supply the pre-computed caller JID and the
     * {@code CallLog} record directly.
     *
     * @param timestamp the mutation timestamp
     * @param callerJid the JID to use as the first index key (the resolved
     *                  caller or peer JID)
     * @param callId    the unique call identifier
     * @param fromMe    whether the call was initiated by the current user
     * @param log       the call log record to sync
     * @return the pending mutation for the call log action
     */
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getCallLogMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getCallLogMutation(
            Instant timestamp,
            Jid callerJid,
            String callId,
            boolean fromMe,
            CallLog log
    ) {
        var action = new CallLogActionBuilder()
                .log(log)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .callLogAction(action)
                .build();
        var fromMeStr = fromMe ? "1" : "0";
        var index = JSON.toJSONString(List.of(CallLogAction.ACTION_NAME, callerJid.toString(), callId, fromMeStr));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                CallLogAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
