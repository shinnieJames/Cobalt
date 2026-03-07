package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles AI thread delete actions.
 *
 * <p>Per WhatsApp Web {@code WAWebAiThreadDeleteSync}, this action only supports
 * SET operations. The handler validates that index[1] is a valid bot WID and
 * index[2] is a non-empty thread ID.
 *
 * <p>Index format: ["ai_thread_delete", chatJid, threadId]
 */
public final class AiThreadDeleteHandler implements WebAppStateActionHandler {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "ai_thread_delete";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 7;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    public static final AiThreadDeleteHandler INSTANCE = new AiThreadDeleteHandler();

    private AiThreadDeleteHandler() {

    }

    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 3) {
            return true;
        }

        var chatJidString = indexArray.getString(1);
        var threadId = indexArray.getString(2);
        if (chatJidString == null || chatJidString.isBlank()
                || threadId == null || threadId.isBlank()) {
            return true;
        }

        var chatJid = Jid.of(chatJidString);
        if (!chatJid.hasBotServer()) {
            return true;
        }

        return true;
    }
}
