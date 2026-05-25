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
 * Removes a Meta-AI conversation thread from the local store in response to an {@code ai_thread_delete} sync mutation.
 *
 * <p>A single bot JID can host multiple named AI conversation threads. When
 * one such thread is deleted on another device, the server replays the delete
 * here, and the matching entry is removed; the surviving thread titles are
 * read back through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findAiThreadTitle(String)}.
 *
 * @implNote
 * This implementation collapses WA Web's {@code ThreadsMetadata} IDB
 * table and the per-thread message rows into a single flat
 * {@code aiThreadTitles} entry keyed by {@code "<botJid>|<threadId>"}.
 * The frontend {@code deleteChatAiThreads} fire-and-forget notification
 * is intentionally omitted because Cobalt has no browser frontend
 * bridge.
 */
@WhatsAppWebModule(moduleName = "WAWebAiThreadDeleteSync")
public final class AiThreadDeleteHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to record failed mutation attempts.
     *
     * <p>Records the {@link Exception} message of a mutation that threw before
     * completing.
     */
    private static final Logger LOGGER = Logger.getLogger(AiThreadDeleteHandler.class.getName());

    /**
     * The wire string {@code "ai_thread_delete"} that identifies this action in the sync collection.
     *
     * <p>Equal to the value returned by {@link #actionName()}; exposed as a
     * public constant so outbound mutation builders and tests can reference the
     * wire name without instantiating the handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final String ACTION_NAME = "ai_thread_delete";

    /**
     * The mutation format version {@code 7} declared by WA Web for {@code ai_thread_delete}.
     *
     * <p>The sync engine gates dispatch on this value; mutations whose on-wire
     * version is greater than {@code 7} are skipped so a payload shape this
     * handler does not understand is never applied.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int ACTION_VERSION = 7;

    /**
     * The {@link SyncPatchType#REGULAR_HIGH} collection that hosts {@code ai_thread_delete} mutations.
     *
     * <p>Equal to the value returned by {@link #collectionName()}; the
     * {@link SyncPatchType#REGULAR_HIGH} collection groups latency-sensitive
     * chat-state changes such as AI thread deletes.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * Constructs the singleton AI thread delete handler.
     *
     * <p>The sync handler registry instantiates this type exactly once.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AiThreadDeleteHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates the JSON index {@code ["ai_thread_delete", chatJid, threadId]},
     * confirms the chat JID is a bot, gates on AI-thread support, and removes
     * the entry keyed by {@code "<chatJid>|<threadId>"} via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#removeAiThreadTitle(String)}.
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
     * subsystem. The orphan model type {@code "Thread"} mirrors WA Web's
     * {@code SyncModelType.Thread}.
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

            var aiThreadSupported = client.store().primaryDeviceCapabilities()
                    .flatMap(DeviceCapabilities::aiThread)
                    .flatMap(DeviceCapabilities.AiThread::supportLevel)
                    .filter(level -> level != DeviceCapabilities.AiThread.SupportLevel.NONE)
                    .isPresent();
            if (!aiThreadSupported) {
                return MutationApplicationResult.unsupported();
            }

            var key = chatJidString + "|" + threadId;
            if (client.store().findAiThreadTitle(key).isEmpty()) {
                return MutationApplicationResult.orphan(key, "Thread");
            }

            client.store().removeAiThreadTitle(key);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            LOGGER.warning("AI thread delete mutation failed: " + e.getMessage());
            return MutationApplicationResult.failed();
        }
    }

}
