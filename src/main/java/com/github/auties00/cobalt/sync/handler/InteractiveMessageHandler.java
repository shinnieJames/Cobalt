package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles interactive message actions.
 *
 * <p>Per WhatsApp Web {@code WAWebInteractiveMessageSync}, only SET operations
 * are supported. The handler validates that all 5 index parts (chatJid, messageId,
 * fromMe, participant, subId) are present and non-empty, and that the action value
 * contains a non-null {@code interactiveMessageAction}.
 *
 * <p>Index format: ["interactive_message_action", "chatJid", "messageId", "fromMe", "participant", "subId"]
 */
public final class InteractiveMessageHandler implements WebAppStateActionHandler {
    public static final InteractiveMessageHandler INSTANCE = new InteractiveMessageHandler();

    private InteractiveMessageHandler() {

    }

    @Override
    public String actionName() {
        return InteractiveMessageAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return InteractiveMessageAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return InteractiveMessageAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 6) {
            return true;
        }

        var chatJid = indexArray.getString(1);
        var messageId = indexArray.getString(2);
        var fromMe = indexArray.getString(3);
        var participant = indexArray.getString(4);
        var subId = indexArray.getString(5);
        if (chatJid == null || chatJid.isEmpty()
                || messageId == null || messageId.isEmpty()
                || fromMe == null || fromMe.isEmpty()
                || participant == null || participant.isEmpty()
                || subId == null || subId.isEmpty()) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof InteractiveMessageAction)) {
            return true;
        }

        return true;
    }
}
