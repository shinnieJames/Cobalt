package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that rename an AI thread on a Meta-AI bot chat.
 *
 * @apiNote
 * Drives the Meta-AI bot UI's "rename thread" affordance: when the user
 * changes a thread title, the returned mutation is pushed through
 * {@link com.github.auties00.cobalt.sync.WebAppStateService} so every linked
 * device updates the same thread's metadata. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.AiThreadRenameHandler}; the
 * handler resolves the inbound mutation by calling
 * {@code WAWebThreadMetadataBulkJob.bulkCreateOrUpdateThreadsMetadata}.
 *
 * @implNote
 * This implementation skips the WA Web pre-emit
 * {@code isStringNotNullAndNotWhitespaceOnly(newTitle)} guard and forwards
 * whatever title the caller supplies; the receive-side handler still validates
 * the title via {@link AiThreadRenameAction#newTitle()}.
 */
public final class AiThreadRenameMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; instantiation is cheap and a single instance
     * may be shared across the lifetime of the client.
     */
    public AiThreadRenameMutationFactory() {

    }

    /**
     * Returns a {@link SyncPendingMutation} that renames the given AI thread.
     *
     * @apiNote
     * Call this when the user edits an AI thread title; the returned mutation
     * must be enqueued via
     * {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     * to fan it out to linked devices. The mutation index follows
     * {@snippet :
     *     ["ai_thread_rename", chatJid.toString(), threadId]
     * }
     * and the {@link AiThreadRenameAction} sub-message carries the new title.
     *
     * @implNote
     * This implementation emits the {@link DecryptedMutation.Trusted} variant
     * with {@link SyncdOperation#SET}; WA Web's
     * {@code WAWebAiThreadRenameSync.buildMutation} produces the same shape
     * with {@code action = AiThreadRename}, version 7, and collection
     * RegularLow, which {@link AiThreadRenameAction#ACTION_VERSION} pins.
     *
     * @param chatJid  the bot {@link Jid} owning the thread
     * @param threadId the thread identifier as exposed by the bot
     * @param newTitle the new title for the thread; the receive-side handler
     *                 rejects blank values
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadRenameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getAiThreadRenameMutation(Jid chatJid, String threadId, String newTitle) {
        var timestamp = Instant.now();
        var action = new AiThreadRenameActionBuilder()
                .newTitle(newTitle)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .aiThreadRenameAction(action)
                .build();
        var index = JSON.toJSONString(List.of(AiThreadRenameAction.ACTION_NAME, chatJid.toString(), threadId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                AiThreadRenameAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
