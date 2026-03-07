package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles lock chat actions.
 *
 * <p>This handler processes mutations that lock or unlock a chat. A locked chat
 * is hidden behind an authentication barrier (e.g., fingerprint or passcode)
 * and does not appear in the main chat list.
 *
 * <p>Index format: ["lock", "chatJid"]
 */
public final class LockChatHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code LockChatHandler}.
     */
    public static final LockChatHandler INSTANCE = new LockChatHandler();

    private LockChatHandler() {

    }

    @Override
    public String actionName() {
        return LockChatAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return LockChatAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return LockChatAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return false;
        }

        if (!(mutation.value().action().orElse(null) instanceof LockChatAction action)) {
            return false;
        }

        var chatJidString = JSON.parseArray(mutation.index()).getString(1);
        var chatJid = Jid.of(chatJidString);

        var chat = client.store()
                .findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        chat.get().setLocked(action.locked());
        if (action.locked()) {
            chat.get().setArchived(false);
            chat.get().setPinnedTimestamp(null);
        }

        return true;
    }
}
