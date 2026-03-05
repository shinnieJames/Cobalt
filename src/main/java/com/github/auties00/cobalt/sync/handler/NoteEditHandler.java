package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles note edit actions.
 *
 * <p>This handler processes mutations related to editing notes within chats.
 * The mutation is acknowledged but not applied locally.
 *
 * <p>Index format: ["note_edit"]
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
        return "note_edit";
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
        return true;
    }
}
