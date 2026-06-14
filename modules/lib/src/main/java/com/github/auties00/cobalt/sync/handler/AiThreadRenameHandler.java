package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.bot.AiThreadTitleBuilder;
import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Renames a Meta-AI conversation thread in the local store in response to an {@code ai_thread_rename} sync mutation.
 *
 * <p>When a single AI conversation thread is renamed on another device, the
 * server replays the rename here and the stored title is updated; the new
 * title is read back through
 * {@link com.github.auties00.cobalt.store.BusinessStore#findAiThreadTitle(String)}.
 *
 * @implNote
 * This implementation collapses WA Web's full {@code ThreadsMetadata}
 * row (last-message timestamp, creation timestamp, AI thread info,
 * etc.) into a single flat {@code aiThreadTitles} entry that tracks
 * only the title string. The frontend
 * {@code updateChatAiThreads} fire-and-forget notification is
 * intentionally omitted because Cobalt has no browser frontend bridge.
 */
@WhatsAppWebModule(moduleName = "WAWebAiThreadRenameSync")
public final class AiThreadRenameHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to record failed mutation attempts.
     *
     * <p>Records the {@link Exception} message of a mutation that threw before
     * completing.
     */
    private static final Logger LOGGER = Logger.getLogger(AiThreadRenameHandler.class.getName());

    /**
     * Constructs the singleton AI thread rename handler.
     *
     * <p>The sync handler registry instantiates this type exactly once.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AiThreadRenameHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AiThreadRenameAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AiThreadRenameAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AiThreadRenameAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates the JSON index {@code ["ai_thread_rename", chatJid, threadId]}
     * and the {@link AiThreadRenameAction#newTitle()} payload, confirms the chat
     * JID is a bot, gates on AI-thread support, and stores the new title via
     * {@link com.github.auties00.cobalt.store.BusinessStore#putAiThreadTitle(com.github.auties00.cobalt.model.bot.AiThreadTitle)}.
     * Returns {@link SyncActionState#UNSUPPORTED} for non-{@link SyncdOperation#SET}
     * operations or when AI-thread support is off, {@link SyncActionState#ORPHAN}
     * when no matching thread is in the store, and {@link SyncActionState#FAILED}
     * on any thrown exception.
     *
     * @implNote
     * This implementation maps WA Web's
     * {@code isBotEnabled() && isAiChatThreadsInfraEnabled()} runtime
     * AB-prop gate onto a {@link DeviceCapabilities.AiThread.SupportLevel}
     * lookup against the primary device because Cobalt has no AB-props
     * subsystem. The store key is {@code "<chatJid>|<threadId>"}, and
     * the orphan model type {@code "Thread"} mirrors WA Web's
     * {@code SyncModelType.Thread}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
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

            if (!(mutation.value().action().orElse(null) instanceof AiThreadRenameAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var newTitle = action.newTitle().orElse(null);
            if (newTitle == null || newTitle.isBlank()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJid = Jid.of(chatJidString);
            if (!chatJid.isBot()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var aiThreadSupported = client.store().contactStore().primaryDeviceCapabilities()
                    .flatMap(DeviceCapabilities::aiThread)
                    .flatMap(DeviceCapabilities.AiThread::supportLevel)
                    .filter(level -> level != DeviceCapabilities.AiThread.SupportLevel.NONE)
                    .isPresent();
            if (!aiThreadSupported) {
                return MutationApplicationResult.unsupported();
            }

            var key = chatJidString + "|" + threadId;
            if (client.store().businessStore().findAiThreadTitle(key).isEmpty()) {
                return MutationApplicationResult.orphan(key, "Thread");
            }

            client.store().businessStore().putAiThreadTitle(new AiThreadTitleBuilder().threadId(key).title(newTitle).build());
            return MutationApplicationResult.success();
        } catch (Exception e) {
            LOGGER.warning("AI thread rename mutation failed: " + e.getMessage());
            return MutationApplicationResult.failed();
        }
    }

}
