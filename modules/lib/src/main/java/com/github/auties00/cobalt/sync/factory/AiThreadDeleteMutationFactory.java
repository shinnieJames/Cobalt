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
 * Builds outgoing AI-thread-delete sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.AiThreadDeleteHandler}.
 */
public final class AiThreadDeleteMutationFactory {
    /**
     * Constructs an AI-thread-delete mutation factory.
     */
    public AiThreadDeleteMutationFactory() {

    }

    /**
     * Builds a pending outgoing mutation that deletes an AI thread across
     * linked devices.
     *
     * <p>Per WhatsApp Web {@code WAWebAiThreadDeleteSync}: emits a SET
     * mutation at {@code ["ai_thread_delete", botJid, threadId]} in the
     * REGULAR_HIGH collection with {@code version = 7}. The action has no
     * dedicated sub-message payload, so the index alone identifies the thread.
     *
     * @param chatJid  the bot JID owning the thread
     * @param threadId the AI thread identifier
     * @return the pending mutation ready to be pushed via
     *         {@link com.github.auties00.cobalt.sync.WebAppStateService#pushPatches}
     */
    @WhatsAppWebExport(moduleName = "WAWebAiThreadDeleteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getAiThreadDeleteMutation(Jid chatJid, String threadId) {
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .build();
        var index = JSON.toJSONString(List.of(AiThreadDeleteHandler.ACTION_NAME, chatJid.toString(), threadId)); // ["ai_thread_delete", chatJid, threadId]
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
