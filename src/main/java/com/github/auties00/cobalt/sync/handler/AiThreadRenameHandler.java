package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles AI thread rename actions.
 *
 * <p>Per WhatsApp Web {@code WAWebAiThreadRenameSync}, this action only supports
 * SET operations. The handler validates that index[1] is a valid bot WID,
 * index[2] is a non-empty thread ID, and that the action value contains a
 * non-null, non-whitespace {@code newTitle}.
 *
 * <p>Index format: ["ai_thread_rename", chatJid, threadId]
 */
public final class AiThreadRenameHandler implements WebAppStateActionHandler {
    public static final AiThreadRenameHandler INSTANCE = new AiThreadRenameHandler();

    private AiThreadRenameHandler() {

    }

    @Override
    public String actionName() {
        return AiThreadRenameAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return AiThreadRenameAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return AiThreadRenameAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof AiThreadRenameAction action)) {
            return true;
        }

        var newTitle = action.newTitle().orElse(null);
        if (newTitle == null || newTitle.isBlank()) {
            return true;
        }

        return true;
    }
}
