package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.ContactAction;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles contact actions.
 *
 * <p>This handler processes mutations that update contact information
 * (names, profile pictures, etc.).
 *
 * <p>Index format: ["contact", "contactJid"]
 */
public final class ContactActionHandler implements WebAppStateActionHandler {
    public static final ContactActionHandler INSTANCE = new ContactActionHandler();

    private ContactActionHandler() {

    }

    @Override
    public String actionName() {
        return ContactAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ContactAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ContactAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!(mutation.value().action().orElse(null) instanceof ContactAction action)) {
            return false;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var contactJidString = indexArray.getString(1);
        if (contactJidString == null || contactJidString.isEmpty()) {
            return false;
        }

        var contactJid = Jid.of(contactJidString);

        // Web skips LID contacts in the regular contact sync handler
        if (contactJid.hasLidServer()) {
            return true;
        }

        switch (mutation.operation()) {
            case SET -> {
                var contact = client.store()
                        .findContactByJid(contactJid)
                        .orElseGet(() -> client.store().addNewContact(contactJid));
                // Web: fullName ?? "" (coalesces absent to empty string)
                var fullName = action.fullName().orElse("");
                contact.setFullName(fullName);
                // Web: firstName ?? getShortName(fullName) ?? ""
                var shortName = action.firstName()
                        .orElseGet(() -> deriveShortName(fullName));
                contact.setShortName(shortName);
                action.username().ifPresent(contact::setUsername);
                action.lidJid().ifPresent(lid -> {
                    contact.setLid(lid);
                    client.store().registerLidMapping(contactJid, lid);
                });
            }
            case REMOVE -> {
                // Web: setNotMyContact() clears name, shortName, sets type
                // to "out", and sets isUsernameContact to false
                var contact = client.store()
                        .findContactByJid(contactJid);
                if (contact.isPresent()) {
                    contact.get().setFullName(null);
                    contact.get().setShortName(null);
                    contact.get().setUsername(null);
                }
            }
        }

        return true;
    }

    /**
     * Derives a short name from a full name by extracting the first word,
     * matching WhatsApp Web's {@code getShortName} logic.
     *
     * @param fullName the full name to derive from
     * @return the first word of the full name, or an empty string if the name
     *         is blank or the first character is not alphabetic
     */
    static String deriveShortName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "";
        }
        if (!Character.isLetter(fullName.codePointAt(0))) {
            return "";
        }
        var spaceIndex = fullName.indexOf(' ');
        return spaceIndex > 0 ? fullName.substring(0, spaceIndex) : fullName;
    }
}
