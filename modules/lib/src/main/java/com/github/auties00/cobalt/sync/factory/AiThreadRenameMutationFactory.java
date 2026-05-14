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
 * Builds outgoing AI-thread-rename sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.AiThreadRenameHandler}.
 */
public final class AiThreadRenameMutationFactory {
    /**
     * Constructs an AI-thread-rename mutation factory.
     */
    public AiThreadRenameMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation that renames an AI thread across
     * linked devices.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadRenameSync}: emits a SET
     * mutation at {@code ["ai_thread_rename", botJid, threadId]} in the
     * REGULAR_LOW collection with {@code version = 7} and an
     * {@code aiThreadRenameAction} sub-message carrying the new title.
     *
     * @param chatJid  the bot JID owning the thread
     * @param threadId the AI thread identifier
     * @param newTitle the new thread title
     * @return the pending mutation ready to be pushed via
     *         {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
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
        var index = JSON.toJSONString(List.of(AiThreadRenameAction.ACTION_NAME, chatJid.toString(), threadId)); // ["ai_thread_rename", chatJid, threadId]
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
