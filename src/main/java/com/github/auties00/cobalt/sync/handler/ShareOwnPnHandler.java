package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles share own phone number actions.
 *
 * <p>Per WhatsApp Web {@code WAWebShareOwnPnSync}, only SET operations are
 * supported. The handler validates that index[1] is a valid LID JID.
 *
 * <p>Index format: ["shareOwnPn", "lidJid"]
 */
public final class ShareOwnPnHandler implements WebAppStateActionHandler {
    public static final ShareOwnPnHandler INSTANCE = new ShareOwnPnHandler();

    private ShareOwnPnHandler() {

    }

    @Override
    public String actionName() {
        return "shareOwnPn";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 8;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 2) {
            return true;
        }

        var lidJidString = indexArray.getString(1);
        if (lidJidString == null || lidJidString.isEmpty()) {
            return true;
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return true;
        }

        client.store().findContactByJid(lidJid).ifPresent(contact ->
                contact.setPhoneNumberShared(true));
        return true;
    }
}
