package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LidContactAction;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles LID contact actions.
 *
 * <p>This handler processes mutations that create, update, or remove contacts
 * identified by a LID (Linked Identity) JID. It mirrors the logic in the
 * WhatsApp Web {@code WAWebLidContactSync} module.
 *
 * <p>Index format: ["lid_contact", "lidJid"]
 */
public final class LidContactHandler implements WebAppStateActionHandler {
    public static final LidContactHandler INSTANCE = new LidContactHandler();

    private LidContactHandler() {

    }

    @Override
    public String actionName() {
        return LidContactAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return LidContactAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return LidContactAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var lidJidString = indexArray.getString(1);
        if (lidJidString == null || lidJidString.isEmpty()) {
            return false;
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return false;
        }

        switch (mutation.operation()) {
            case SET -> {
                if (!(mutation.value().action().orElse(null) instanceof LidContactAction action)) {
                    return false;
                }

                var contact = client.store()
                        .findContactByJid(lidJid)
                        .orElseGet(() -> client.store().addNewContact(lidJid));
                // Web: fullName ?? "" (coalesces absent to empty string)
                var fullName = action.fullName().orElse("");
                contact.setFullName(fullName);
                // Web: firstName ?? getShortName(firstName) ?? ""
                var shortName = action.firstName().orElse("");
                contact.setShortName(shortName);
                action.username().ifPresent(contact::setUsername);
            }
            case REMOVE -> {
                // Web: calls setNotAddressBookContacts and
                // setContactsNotMyUsernameContacts, which clears name,
                // shortName, sets type to "out", and clears username
                // Only applies to contacts that were added via username flow
                client.store().findContactByJid(lidJid).ifPresent(contact -> {
                    if (contact.isAddedByUsername()) {
                        contact.setFullName(null);
                        contact.setShortName(null);
                        contact.setUsername(null);
                        contact.setAddedByUsername(false);
                    }
                });
            }
        }

        return true;
    }
}
