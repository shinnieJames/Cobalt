package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Handles AI thread delete sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebAiThreadDeleteSync}, this handler extends
 * {@code ChatSyncdActionBase} and processes the {@code "ai_thread_delete"} action.
 * It only supports SET operations. The handler validates that {@code index[1]}
 * is a valid bot WID ({@code isWid} and {@code isBot} checks) and that
 * {@code index[2]} is a non-null, non-whitespace thread ID.
 *
 * <p>Index format: {@code ["ai_thread_delete", chatJid, threadId]}
 *
 * <p>The gating check in WA Web verifies {@code isBotEnabled() && isAiChatThreadsInfraEnabled()},
 * which are AB prop-based runtime checks. In Cobalt, these are adapted to a
 * {@code DeviceCapabilities.AiThread.SupportLevel} check since Cobalt does not
 * have an AB props subsystem.
 */
@WhatsAppWebModule(moduleName = "WAWebAiThreadDeleteSync")
public final class AiThreadDeleteHandler implements WebAppStateActionHandler {
    /**
     * Logger for this handler.
     */
    private static final Logger LOGGER = Logger.getLogger(AiThreadDeleteHandler.class.getName());

    /**
     * Canonical WhatsApp Web action name for the AI thread delete action type.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final String ACTION_NAME = "ai_thread_delete";

    /**
     * Canonical WhatsApp Web mutation format version for this action type.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * Constructs the singleton AI thread delete handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AiThreadDeleteHandler() {

    }

    /**
     * Returns the action type name this handler processes.
     * @return the action type name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the sync patch type / collection name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the handler's supported mutation version
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ACTION_VERSION;
    }

    /**
     * Applies an AI thread delete mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadDeleteSync.applyMutations}, the per-mutation
     * logic performs the following steps wrapped in a try/catch:
     * <ol>
     *   <li>If operation is not SET, return {@code Unsupported}</li>
     *   <li>Extract {@code indexParts[1]} (chatJid) and {@code indexParts[2]} (threadId)</li>
     *   <li>Validate both are non-null, non-whitespace; chatJid must be a valid Wid</li>
     *   <li>Create Wid from chatJid and verify it {@code isBot()}</li>
     *   <li>Check bot gating: {@code isBotEnabled() && isAiChatThreadsInfraEnabled()}</li>
     *   <li>Create AI thread from mutation index and resolve thread from store</li>
     *   <li>If thread not found, return {@code Orphan} with orphan model</li>
     *   <li>If found, call bulk delete and fire frontend notification, return {@code Success}</li>
     * </ol>
     *
     * <p>Any exception thrown inside the block is caught and reported as
     * {@link SyncActionState#FAILED}, mirroring WA Web's {@code catch(e) { return {actionState: Failed} }}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() < 3) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJidString = indexArray.getString(1);
            var threadId = indexArray.getString(2);
            if (chatJidString == null || chatJidString.isBlank()
                    || threadId == null || threadId.isBlank()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJid = Jid.of(chatJidString);
            if (!chatJid.isBot()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            // ADAPTED: WAWebAiThreadDeleteSync.applyMutations — WA Web checks isBotEnabled() && isAiChatThreadsInfraEnabled()
            // (AB prop-based gating). Cobalt maps this to DeviceCapabilities.AiThread.SupportLevel check
            // since Cobalt does not have an AB props subsystem.
            var aiThreadSupported = client.store().primaryDeviceCapabilities()
                    .flatMap(DeviceCapabilities::aiThread)
                    .flatMap(DeviceCapabilities.AiThread::supportLevel)
                    .filter(level -> level != DeviceCapabilities.AiThread.SupportLevel.NONE)
                    .isPresent();
            if (!aiThreadSupported) {
                return MutationApplicationResult.unsupported();
            }

            // ADAPTED: WAWebAiThreadDeleteSync.applyMutations — WA Web calls
            // createAiThreadFromMutationIndex(botWid, threadId) then resolveThreadForMutationIndex(thread).
            // Cobalt collapses WA Web's ThreadsMetadata IDB table into the aiThreadTitles store.
            var key = chatJidString + "|" + threadId;
            if (client.store().findAiThreadTitle(key).isEmpty()) {
                // WA Web uses SyncModelType.Thread for orphan model type.
                return MutationApplicationResult.orphan(key, "Thread");
            }

            // ADAPTED: WAWebAiThreadDeleteSync.$AiThreadDeleteSync$p_1 — WA Web calls
            // bulkDeleteThreads(botWid, [thread]) which deletes metadata and messages from IDB,
            // then fires frontendFireAndForget("deleteChatAiThreads", {chatId, threadIds, msgIds}).
            // Cobalt removes from the flat aiThreadTitles store; the frontend notification
            // is intentionally omitted because Cobalt has no browser frontend bridge.
            client.store().removeAiThreadTitle(key);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            LOGGER.warning("AI thread delete mutation failed: " + e.getMessage());
            return MutationApplicationResult.failed();
        }
    }

}
