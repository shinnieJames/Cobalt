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
 * Maintains the local VoIP call-log history from {@code call_log} sync mutations.
 *
 * <p>When a call is placed, received, or missed on another device, the server
 * replays the resulting call-log record here as a {@link CallLogAction}, and the
 * record is mirrored into the store via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#addCallLog(com.github.auties00.cobalt.model.call.CallLog)}.
 *
 * @implNote
 * This implementation stores the protobuf record directly via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#addCallLog(com.github.auties00.cobalt.model.call.CallLog)}
 * keyed by the record's own {@link com.github.auties00.cobalt.model.call.CallLog#callId()},
 * rather than running WA Web's {@code generateCallLogFromCallSyncRecord} which
 * writes a VoIP-flavored chat message. The pairing-timestamp filter and the
 * one-minute {@code shouldHideInConversation} window are intentionally dropped
 * because they only control browser UI behaviour (whether the call appears
 * inline in the conversation view).
 */
@WhatsAppWebModule(moduleName = "WAWebCallLogSync")
public final class CallLogHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton call-log handler.
     *
     * <p>The sync handler registry instantiates this type exactly once.
     */
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CallLogHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CallLogAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CallLogAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCallLogSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CallLogAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For {@link SyncdOperation#SET} mutations, validates that the
     * {@link CallLogAction#log()} record is present, validates the four-element
     * index {@code ["call_log", peerJid, callId, fromMe]}, and stores the record.
     * For {@link SyncdOperation#REMOVE} mutations, drops the record keyed by the
     * {@code callId} in index slot 2. Returns
     * {@link MutationApplicationResult#unsupported()} for other operations and
     * {@link MutationApplicationResult#failed()} on any thrown exception.
     *
     * @implNote
     * This implementation requires a non-empty
     * {@link com.github.auties00.cobalt.model.call.CallLog#callId()}
     * on the action payload because the store keys call logs by
     * record-internal id. WA Web instead keys by the composite index
     * {@code peerJid|callId|fromMe} via
     * {@code generateCallLogFromCallSyncRecord}; Cobalt rejects the
     * mutation as
     * {@link SyncdIndexUtils#malformedActionValue(String)} when the
     * record id is missing rather than fabricate the composite key.
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

                if (log.callId().isEmpty()) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }
                client.store().addCallLog(log);

                return MutationApplicationResult.success();
            } else if (mutation.operation() == SyncdOperation.REMOVE) {
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
