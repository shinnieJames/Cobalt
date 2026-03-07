package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.StarAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles star message actions.
 *
 * <p>This handler processes mutations that star or unstar messages.
 * Only SET operations are supported, matching the WhatsApp Web
 * {@code WAWebStarMessageSync} module behavior.
 *
 * <p>Index format: ["star", "chatJid", "messageId", "fromMe", "participant"]
 */
public final class StarMessageHandler implements WebAppStateActionHandler {
    public static final StarMessageHandler INSTANCE = new StarMessageHandler();

    private StarMessageHandler() {

    }

    @Override
    public String actionName() {
        return StarAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return StarAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return StarAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // Web only supports SET for star mutations (REMOVE returns Unsupported)
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof StarAction action)) {
            return false;
        }

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

        starMessage(message.get(), action.starred());

        return true;
    }

    private static void starMessage(MessageInfo message, boolean action) {
        switch (message) {
            case ChatMessageInfo chatMessageInfo -> chatMessageInfo.setStarred(action);
            case NewsletterMessageInfo newsletterMessageInfo -> newsletterMessageInfo.setStarred(action);
        };
    }
}
