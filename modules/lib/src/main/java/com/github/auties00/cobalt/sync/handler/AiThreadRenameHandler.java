package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.bot.AiThreadTitleBuilder;
import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Handles AI thread rename sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebAiThreadRenameSync}, this handler extends
 * {@code ChatSyncdActionBase} and processes the {@code "ai_thread_rename"} action.
 * It only supports SET operations. The handler validates that {@code index[1]}
 * is a valid bot WID ({@code isWid} and {@code isBot} checks) and that
 * {@code index[2]} is a non-null, non-whitespace thread ID.
 *
 * <p>Index format: {@code ["ai_thread_rename", chatJid, threadId]}
 *
 * <p>The gating check in WA Web verifies {@code isBotEnabled() && isAiChatThreadsInfraEnabled()},
 * which are AB prop-based runtime checks. In Cobalt, these are adapted to a
 * {@code DeviceCapabilities.AiThread.SupportLevel} check since Cobalt does not
 * have an AB props subsystem.
 */
@WhatsAppWebModule(moduleName = "WAWebAiThreadRenameSync")
public final class AiThreadRenameHandler implements WebAppStateActionHandler {
    /**
     * Logger for this handler.
     */
    private static final Logger LOGGER = Logger.getLogger(AiThreadRenameHandler.class.getName());

    /**
     * Constructs the singleton AI thread rename handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AiThreadRenameHandler() {

    }

    /**
     * Returns the action type name this handler processes.
     * @return the action type name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AiThreadRenameAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     * @return the sync patch type / collection name
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AiThreadRenameAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the handler's supported mutation version
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AiThreadRenameAction.ACTION_VERSION;
    }

    /**
     * Applies an AI thread rename mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadRenameSync.applyMutations}, the per-mutation
     * logic performs the following steps:
     * <ol>
     *   <li>If operation is not SET, return {@code Unsupported}</li>
     *   <li>Extract {@code indexParts[1]} (chatJid) and {@code indexParts[2]} (threadId)</li>
     *   <li>Validate both are non-null, non-whitespace; chatJid must be a valid Wid</li>
     *   <li>Validate action value contains a non-null, non-whitespace {@code newTitle}</li>
     *   <li>Create Wid from chatJid and verify it {@code isBot()}</li>
     *   <li>Check bot gating: {@code isBotEnabled() && isAiChatThreadsInfraEnabled()}</li>
     *   <li>Create AI thread from mutation index and resolve thread from store</li>
     *   <li>If thread not found, return {@code Orphan} with orphan model</li>
     *   <li>If found, update thread metadata with new title and return {@code Success}</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
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
            //     return t.malformedActionIndex()
            if (chatJidString == null || chatJidString.isBlank()
                    || threadId == null || threadId.isBlank()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            //     return malformedActionValue(t.collectionName)
            if (!(mutation.value().action().orElse(null) instanceof AiThreadRenameAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var newTitle = action.newTitle().orElse(null);
            if (newTitle == null || newTitle.isBlank()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            // so Cobalt must use Jid.isBot() (not Jid.hasBotServer()) to match semantics.
            var chatJid = Jid.of(chatJidString);
            if (!chatJid.isBot()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            // ADAPTED: WAWebAiThreadRenameSync.applyMutations — WA Web checks
            // isBotEnabled() && isAiChatThreadsInfraEnabled() (AB prop-based gating).
            // Cobalt maps this to DeviceCapabilities.AiThread.SupportLevel check
            // since Cobalt does not have an AB props subsystem.
            var aiThreadSupported = client.store().primaryDeviceCapabilities()
                    .flatMap(DeviceCapabilities::aiThread)
                    .flatMap(DeviceCapabilities.AiThread::supportLevel)
                    .filter(level -> level != DeviceCapabilities.AiThread.SupportLevel.NONE)
                    .isPresent();
            if (!aiThreadSupported) {
                return MutationApplicationResult.unsupported();
            }

            // ADAPTED: WAWebAiThreadRenameSync.applyMutations — WA Web calls
            // createAiThreadFromMutationIndex(botWid, threadId) then resolveThreadForMutationIndex(thread).
            // Cobalt collapses WA Web's ThreadsMetadata IDB table into the aiThreadTitles store.
            var key = chatJidString + "|" + threadId;
            if (client.store().findAiThreadTitle(key).isEmpty()) {
                return MutationApplicationResult.orphan(key, "Thread");
            }

            // ADAPTED: WAWebAiThreadRenameSync.$AiThreadRenameSync$p_1 — WA Web reads
            // lastMessageTimestamp, creationTimestamp, aiThreadInfo from the resolved thread,
            // builds a metadata object with getAiThreadInfoFromType(newTitle, aiThreadType),
            // calls bulkCreateOrUpdateThreadsMetadata, then frontendFireAndForget("updateChatAiThreads").
            // Cobalt's flat aiThreadTitles store only tracks the title string.
            client.store().putAiThreadTitle(new AiThreadTitleBuilder().threadId(key).title(newTitle).build());
            return MutationApplicationResult.success();
        } catch (Exception e) {
            LOGGER.warning("AI thread rename mutation failed: " + e.getMessage());
            return MutationApplicationResult.failed();
        }
    }

}
