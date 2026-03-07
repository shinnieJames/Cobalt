package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles note edit actions.
 *
 * <p>Index format: ["note_edit", "noteId"]
 */
public final class NoteEditHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code NoteEditHandler}.
     */
    public static final NoteEditHandler INSTANCE = new NoteEditHandler();

    private NoteEditHandler() {

    }

    @Override
    public String actionName() {
        return NoteEditAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return NoteEditAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return NoteEditAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var noteId = indexArray.getString(1);
        if (noteId == null || noteId.isEmpty()) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof NoteEditAction action)) {
            return true;
        }

        if (action.deleted()) {
            return true;
        }

        if (action.type().isEmpty()) {
            return true;
        }

        var chatJid = action.chatJid();
        if (chatJid.isEmpty()) {
            return true;
        }

        var chat = client.store().findChatByJid(chatJid.get());
        if (chat.isEmpty()) {
            return false;
        }

        return true;
    }
}
