package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
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
        return "lock";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var chatJidString = JSON.parseArray(mutation.index()).getString(1);
        var chatJid = Jid.of(chatJidString);

        var chat = client.store()
                .findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        switch (mutation.operation()) {
            case SET -> {
                var action = mutation.value()
                        .lockChatAction()
                        .orElseThrow(() -> new IllegalArgumentException("Missing lockChatAction"));
                chat.get().setLocked(action.locked());
            }
            case REMOVE -> chat.get().setLocked(false);
        }

        return true;
    }
}
