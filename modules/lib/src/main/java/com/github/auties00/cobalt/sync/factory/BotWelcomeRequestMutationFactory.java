package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that record whether a bot welcome message has been requested for a chat.
 *
 * <p>The flag is set to {@code true} when the user opens a bot chat for the
 * first time and cleared back to {@code false} after the bot replies, keeping
 * the per-bot-chat welcome affordance consistent across linked devices. This
 * factory builds the outgoing mutation; the inbound counterpart is
 * {@link com.github.auties00.cobalt.sync.handler.BotWelcomeRequestHandler}.
 */
public final class BotWelcomeRequestMutationFactory {
    /**
     * Creates a stateless factory with no collaborators.
     *
     * <p>A single instance may be shared across the lifetime of the client.
     */
    public BotWelcomeRequestMutationFactory() {

    }

    /**
     * Returns a {@link SyncPendingMutation} that sets the welcome-requested flag for the given bot chat.
     *
     * <p>Emit this mutation right after sending the bot a welcome request (with
     * {@code isSent == true}) so other linked devices stop showing the welcome
     * affordance, and again after the bot replies (with {@code isSent == false})
     * so the affordance can be reused later. The mutation index follows
     * {@snippet :
     *     ["botWelcomeRequest", chatJid.toString()]
     * }
     * and the {@link BotWelcomeRequestAction} sub-message carries the
     * {@code isSent} flag.
     *
     * @implNote
     * This implementation captures the timestamp via {@link Instant#now()} and
     * pins the version to {@link BotWelcomeRequestAction#ACTION_VERSION}.
     *
     * @param chatJid the bot chat {@link Jid}
     * @param isSent  {@code true} once the welcome has been requested, {@code false} after the bot replies
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getBotWelcomeRequestSetMutation(Jid chatJid, boolean isSent) {
        var action = new BotWelcomeRequestActionBuilder()
                .isSent(isSent)
                .build();
        var timestamp = Instant.now();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .botWelcomeRequestAction(action)
                .build();
        var index = JSON.toJSONString(List.of(BotWelcomeRequestAction.ACTION_NAME, chatJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                BotWelcomeRequestAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
