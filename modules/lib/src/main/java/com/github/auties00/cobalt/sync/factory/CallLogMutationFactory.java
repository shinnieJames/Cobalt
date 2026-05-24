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
 * Builds outgoing app-state mutations that record a call-log entry.
 *
 * @apiNote
 * Drives WhatsApp's call-history surface. The single consumer in WA Web is
 * {@code WAWebVoipHandleNativeCallEventCallLogHandlers}, which calls
 * {@code WAWebCallLogSync.getCallLogMutation} when a VoIP call ends and
 * pushes the result through {@code WAWebSyncdCoreApi.lockForSync} so the
 * call appears in every linked device's call tab. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.CallLogHandler}.
 *
 * @implNote
 * This implementation takes the resolved caller JID as a parameter. WA Web
 * derives it inline by reading {@code callCreatorJid} first, then falling
 * back to the local-device PN ({@code WAWebUserPrefsMeUser.getMeDevicePnOrThrow})
 * when {@code fromMe}, or to the {@code peerJid} otherwise; Cobalt callers
 * resolve that upstream because the Me-user lookup is store-side.
 */
public final class CallLogMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public CallLogMutationFactory() {

    }

    /**
     * Returns a SET mutation that records the given call in the cross-device call log.
     *
     * @apiNote
     * Emit one mutation per terminated VoIP call. The mutation index follows
     * {@snippet :
     *     ["call_log", callerJid.toString(), callId, fromMe ? "1" : "0"]
     * }
     * and the {@link CallLogAction} sub-message carries the {@link CallLog}
     * record (result, duration, start time, video flag, participants,
     * scheduled-call metadata).
     *
     * @implNote
     * This implementation passes through {@code log} verbatim into the
     * {@link CallLogAction}. WA Web's
     * {@code WAWebCallLogSync.getCallLogMutation} builds the
     * {@code callLogRecord} payload field-by-field via
     * {@code WAWebVoipWaCallEnums.getSyncCallResultFromCallLogResult} and
     * the per-participant projection; Cobalt has the same shape on
     * {@link CallLog} already so the projection collapses into a single
     * builder field.
     *
     * @param timestamp the mutation timestamp
     * @param callerJid the JID used as the first index segment (pre-resolved
     *                  caller, falling back to the peer or the local device PN)
     * @param callId    the call identifier as exposed by the VoIP stack
     * @param fromMe    {@code true} when the local user initiated the call,
     *                  encoded as {@code "1"}/{@code "0"} in the index
     * @param log       the call record to ship
     * @return the pending mutation ready to be queued for outbound app-state sync
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
