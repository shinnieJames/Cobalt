package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.alibaba.fastjson2.JSON;

/**
 * Handles call log sync actions.
 *
 * <p>This handler processes incoming mutations that create or remove call log
 * records. The action is identified by the {@code "call_log"} action name in
 * {@code SyncActionValue.callLogAction}. The mutation index format is
 * {@code ["call_log", peerJid, callId, fromMe]}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code AccountSyncdActionBase} and
 * stores its mutations in the {@code Regular} collection at version {@code 1}.
 * When a {@code SET} mutation arrives, the handler extracts the
 * {@code callLogRecord} from the action value and delegates to
 * {@code WAWebVoipActionWriteCallLogSync.generateCallLogFromCallSyncRecord} to
 * write it as a VoIP call log message. In Cobalt, the record is stored directly
 * in the {@code callLogStates} map of the store.
 */
@WhatsAppWebModule(moduleName = "WAWebCallLogSync")
public final class CallLogHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CallLogHandler() {

    }

    /**
     * Returns the action name for call log actions.
     * @return the action name {@code "call_log"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CallLogAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for call log actions.
     *
     * <p>Per WhatsApp Web, the call log handler's {@code collectionName} is set
     * to {@code WASyncdConst.CollectionName.Regular} in the constructor.
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CallLogAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for call log actions.
     * @return the version number {@code 1}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CallLogAction.ACTION_VERSION;
    }

    /**
     * Applies a call log mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebCallLogSync.applyMutations}, for each
     * mutation:
     * <ol>
     *   <li>If {@code operation === "set"}:
     *     <ul>
     *       <li>Extracts the {@code callLogAction} and its {@code callLogRecord}</li>
     *       <li>If the record is missing, returns {@code malformedActionValue}</li>
     *       <li>Checks the pairing timestamp and whether the mutation timestamp
     *           is after the pairing time</li>
     *       <li>Computes {@code shouldHideInConversation} based on whether the
     *           mutation happened within one minute</li>
     *       <li>Calls {@code generateCallLogFromCallSyncRecord} to write the log</li>
     *       <li>Returns {@code Success}</li>
     *     </ul>
     *   </li>
     *   <li>If {@code operation === "remove"}: returns {@code Success}</li>
     *   <li>Otherwise: returns {@code Unsupported}</li>
     * </ol>
     *
     * <p>In Cobalt, the call log record is stored in the {@code callLogStates}
     * map keyed by {@code peerJid|callId|fromMe} instead of writing a VoIP call
     * log message to a chat. The pairing timestamp and time-window checks are
     * omitted because they control browser-specific UI behavior (whether to show
     * the call in the conversation view).
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() == SyncdOperation.SET) {
                if (!(mutation.value().action().orElse(null) instanceof CallLogAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                var log = action.log().orElse(null);
                if (log == null) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                // ADAPTED: WA Web checks pairingTimestamp and happenedWithin(timestamp, MINUTE_SECONDS)
                // before calling generateCallLogFromCallSyncRecord. These checks control browser UI
                // behavior (shouldHideInConversation). In Cobalt, we store the log unconditionally.
                // ADAPTED: WA Web calls generateCallLogFromCallSyncRecord to write a VoIP call log
                // message to a chat. Cobalt stores the record in callLogStates keyed by index parts.
                var indexArray = JSON.parseArray(mutation.index());
                if (indexArray.size() < 4) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                var peer = indexArray.getString(1);
                var callId = indexArray.getString(2);
                var fromMe = indexArray.getString(3);
                if (peer == null || callId == null || fromMe == null) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                if (log.callId().isEmpty()) { // ADAPTED: addCallLog requires a non-empty callId on the log itself; bail out instead of fabricating a composite key
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }
                client.store().addCallLog(log);

                return MutationApplicationResult.success();
            } else if (mutation.operation() == SyncdOperation.REMOVE) {
                // ADAPTED: WA Web simply returns Success for remove. Cobalt also removes from store.
                var indexArray = JSON.parseArray(mutation.index());
                if (indexArray.size() >= 4) {
                    var callId = indexArray.getString(2);
                    if (callId != null) {
                        client.store().removeCallLog(callId);
                    }
                }
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.unsupported();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
