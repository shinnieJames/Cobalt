package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles user status mute actions.
 *
 * <p>This handler processes mutations that mute or unmute a contact's status updates.
 *
 * <p>Index format: ["userStatusMuteAction", "userJid"]
 */
public final class UserStatusMuteHandler implements WebAppStateActionHandler {
    public static final UserStatusMuteHandler INSTANCE = new UserStatusMuteHandler();

    private UserStatusMuteHandler() {

    }

    @Override
    public String actionName() {
        return UserStatusMuteAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return UserStatusMuteAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return UserStatusMuteAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // Web: only SET is supported; non-SET returns Unsupported
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof UserStatusMuteAction action)) {
            return false;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var userJidString = indexArray.getString(1);
        var userJid = Jid.of(userJidString);

        // Web: handles both user contacts and group metadata.
        // Group status mute (muting a group's status updates) is stored in
        // the group metadata table; we acknowledge it without acting since
        // our Chat model does not track per-group status mute.
        if (userJid.hasGroupServer()) {
            return true;
        }

        // Web: returns Orphan if contact not found (does NOT create a new contact)
        var contact = client.store().findContactByJid(userJid);
        if (contact.isEmpty()) {
            return false;
        }
        contact.get().setStatusMuted(action.muted());

        return true;
    }
}
