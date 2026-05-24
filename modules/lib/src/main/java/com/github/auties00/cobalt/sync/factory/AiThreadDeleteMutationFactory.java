package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.sync.handler.AiThreadDeleteHandler;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that delete an AI thread on a Meta-AI bot chat.
 *
 * @apiNote
 * Drives the Meta-AI bot UI's "delete thread" affordance: when the local user
 * removes a thread from a bot chat, the resulting {@link SyncPendingMutation}
 * is pushed through {@link com.github.auties00.cobalt.sync.WebAppStateService}
 * so every linked device drops the same thread from its bulk thread-metadata
 * store. The factory is the outgoing-mutation counterpart of
 * {@link AiThreadDeleteHandler}; the handler resolves the inbound mutation by
 * invoking {@code WAWebThreadMetadataBulkJob.bulkDeleteThreads}.
 *
 * @implNote
 * This implementation gates only on the bot/thread identifiers; the WA Web
 * {@code applyMutations} branch additionally checks
 * {@code WAWebBotBaseGating.isBotEnabled()} and
 * {@code isAiChatThreadsInfraEnabled()}, but those gates are receive-side
 * only and Cobalt callers are expected to skip the factory call when the
 * surface is gated off.
 */
public final class AiThreadDeleteMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; instantiation is cheap and a single instance
     * may be shared across the lifetime of the client.
     */
    public AiThreadDeleteMutationFactory() {

    }

    /**
     * Returns a {@link SyncPendingMutation} that deletes the given AI thread from the bot chat.
     *
     * @apiNote
     * Call this when the user removes an AI thread; the returned mutation
     * must be enqueued via
     * {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     * to fan it out to linked devices. The mutation index follows
     * {@snippet :
     *     ["ai_thread_delete", chatJid.toString(), threadId]
     * }
     * and the action carries no sub-message because the index alone identifies the thread.
     *
     * @implNote
     * This implementation emits the {@link DecryptedMutation.Trusted} variant
     * with {@link SyncdOperation#SET}; WA Web's
     * {@code WAWebAiThreadDeleteSync.buildMutation} delegates to
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with the same
     * collection, version, and operation values that
     * {@link AiThreadDeleteHandler#ACTION_VERSION} pins.
     *
     * @param chatJid  the bot {@link Jid} owning the thread
     * @param threadId the thread identifier as exposed by the bot
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getAiThreadDeleteMutation(Jid chatJid, String threadId) {
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .build();
        var index = JSON.toJSONString(List.of(AiThreadDeleteHandler.ACTION_NAME, chatJid.toString(), threadId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                AiThreadDeleteHandler.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
