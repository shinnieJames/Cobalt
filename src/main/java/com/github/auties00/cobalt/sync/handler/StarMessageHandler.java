package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles star message actions.
 *
 * <p>This handler processes mutations that star or unstar messages.
 *
 * <p>Index format: ["starAction", "chatJid", "messageId", "fromMe", "participant"]
 */
public final class StarMessageHandler implements WebAppStateActionHandler {
    public static final StarMessageHandler INSTANCE = new StarMessageHandler();

    private StarMessageHandler() {

    }

    @Override
    public String actionName() {
        return "starAction";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    @Override
    public int version() {
        return 5;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {

        var action = mutation.value().starAction().orElseThrow(() -> new IllegalArgumentException("Missing starAction"));

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 5) {
            return false;
        }

        var chatJidString = indexArray.getString(1);
        var messageId = indexArray.getString(2);
        // var fromMe = indexArray.getString(3);
        // var participant = indexArray.getString(4);

        var chatJid = Jid.of(chatJidString);

        var message = client.store()
                .findMessageById(chatJid, messageId);
        if (message.isEmpty()) {
            return false;
        }

        switch (mutation.operation()) {
            case SET -> starMessage(message.get(), action.starred());
            case REMOVE -> starMessage(message.get(), false);
        }

        return true;
    }

    private static void starMessage(MessageInfo message, boolean action) {
        switch (message) {
            case ChatMessageInfo chatMessageInfo -> chatMessageInfo.setStarred(action);
            case NewsletterMessageInfo newsletterMessageInfo -> newsletterMessageInfo.setStarred(action);
        };
    }
}
