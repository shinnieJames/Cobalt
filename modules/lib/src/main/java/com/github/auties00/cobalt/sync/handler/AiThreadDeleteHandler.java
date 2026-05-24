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
 * @apiNote
 * Drives the Meta-AI chat surface where the user has pressed
 * "Delete chat" on a single AI conversation thread (a bot WID can host
 * multiple named threads). When that delete is performed on another
 * device, the server replays it to this device as a sync mutation that
 * lands here. Cobalt embedders observe the effect through
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
     * @apiNote
     * Used to record the {@link Exception} message of a mutation that
     * threw before completing; matches the WA Web {@code WALogger.WARN}
     * call in the same {@code catch} arm.
     */
    private static final Logger LOGGER = Logger.getLogger(AiThreadDeleteHandler.class.getName());

    /**
     * The wire string {@code "ai_thread_delete"} that identifies this action in the sync collection.
     *
     * @apiNote
     * Equal to {@link #actionName()}; exposed as a public constant so
     * outbound mutation builders and tests can reference the wire name
     * without instantiating the handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final String ACTION_NAME = "ai_thread_delete";

    /**
     * The mutation format version {@code 7} declared by WA Web for {@code ai_thread_delete}.
     *
     * @apiNote
     * Used by the sync engine to gate which incoming mutations are
     * dispatched to this handler. Mutations with a higher version are
     * skipped to avoid processing with logic that does not yet match
     * the wire shape.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int ACTION_VERSION = 7;

    /**
     * The {@link SyncPatchType#REGULAR_HIGH} collection that hosts {@code ai_thread_delete} mutations.
     *
     * @apiNote
     * Equal to {@link #collectionName()}; the {@code regular_high}
     * collection groups latency-sensitive chat-state changes such as AI
     * thread deletes.
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * Constructs the singleton AI thread delete handler.
     *
     * @apiNote
     * Instantiated once by the sync handler registry. Embedders do not
     * normally construct this directly.
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
     * @apiNote
     * Validates the JSON index {@code ["ai_thread_delete", chatJid, threadId]},
     * confirms the chat JID is a bot, gates on AI-thread support, and
     * removes the keyed entry from
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#removeAiThreadTitle(String)}.
     * Returns {@link SyncActionState#UNSUPPORTED} for non-{@code SET}
     * operations or when AI-thread support is off,
     * {@link SyncActionState#ORPHAN} when no matching thread is in the
     * store, and {@link SyncActionState#FAILED} on any thrown exception.
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
