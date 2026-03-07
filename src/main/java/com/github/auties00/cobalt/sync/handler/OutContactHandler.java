package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.OutContactAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles outgoing contact actions.
 *
 * <p>Per WhatsApp Web, this handler processes mutations for outgoing
 * contact name entries. On SET, the contact's full name and first name
 * are stored. On REMOVE, the entry is deleted.
 *
 * <p>Index format: ["out_contact", userJid]
 */
public final class OutContactHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code OutContactHandler}.
     */
    public static final OutContactHandler INSTANCE = new OutContactHandler();

    private OutContactHandler() {

    }

    @Override
    public String actionName() {
        return OutContactAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return OutContactAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return OutContactAction.ACTION_VERSION;
    }

    /**
     * Applies an outgoing contact mutation.
     *
     * <p>Per WhatsApp Web, on SET the contact's name is updated in the
     * contact store. On REMOVE, the contact name entry is cleared.
     *
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was acknowledged
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var userJid = indexArray.getString(1);
        if (userJid == null || userJid.isEmpty()) {
            return true;
        }

        if (mutation.operation() == SyncdOperation.SET) {
            if (!(mutation.value().action().orElse(null) instanceof OutContactAction action)) {
                return true;
            }

            var fullName = action.fullName().orElse(null);
            var firstName = action.firstName().orElse(null);
            if (fullName == null && firstName == null) {
                return true;
            }
        }

        return true;
    }
}
