package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles bot welcome request actions.
 *
 * <p>Index format: ["bot_welcome_request", "chatJid"]
 */
public final class BotWelcomeRequestHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code BotWelcomeRequestHandler}.
     */
    public static final BotWelcomeRequestHandler INSTANCE = new BotWelcomeRequestHandler();

    private BotWelcomeRequestHandler() {

    }

    @Override
    public String actionName() {
        return BotWelcomeRequestAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return BotWelcomeRequestAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return BotWelcomeRequestAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() == SyncdOperation.REMOVE) {
            return true;
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.getString(1);
        if (chatJidString == null || chatJidString.isEmpty()) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof BotWelcomeRequestAction)) {
            return true;
        }

        var chatJid = Jid.of(chatJidString);
        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        return true;
    }
}
