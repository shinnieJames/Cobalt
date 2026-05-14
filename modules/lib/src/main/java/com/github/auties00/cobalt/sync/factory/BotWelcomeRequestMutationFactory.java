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
 * Builds outgoing bot-welcome-request sync mutations.
 *
 * <p>Mirrors the {@code getBotWelcomeRequestSetMutation} export of WhatsApp
 * Web's {@code WAWebBotWelcomeRequestSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.BotWelcomeRequestHandler}.
 */
public final class BotWelcomeRequestMutationFactory {
    /**
     * Constructs a bot-welcome-request mutation factory.
     */
    public BotWelcomeRequestMutationFactory() {

    }

    /**
     * Builds a pending mutation for setting the bot welcome request state on a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebBotWelcomeRequestSync.getBotWelcomeRequestSetMutation}:
     * <ol>
     *   <li>Constructs the value with {@code {botWelcomeRequestAction: {isSent: t}}}</li>
     *   <li>Resolves the chat JID for mutation index via
     *       {@code WAWebSyncdGetChat.getChatJidMutationIndexForChat(e, Actions.BotWelcomeRequest)}</li>
     *   <li>Builds the pending mutation via {@code WAWebSyncdActionUtils.buildPendingMutation}
     *       with collection, index, value, version, operation SET, and current unix time</li>
     * </ol>
     *
     * @param chatJid the JID of the bot chat
     * @param isSent  whether the welcome message has been sent
     * @return the pending mutation for the bot welcome request action
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
