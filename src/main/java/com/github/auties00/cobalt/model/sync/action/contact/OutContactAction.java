package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A sync action representing an outgoing contact entry.
 *
 * <p>Per WhatsApp Web, this action stores a contact's name as seen by
 * the current user in their address book, synced via the
 * {@code REGULAR_LOW} collection.
 */
@ProtobufMessage(name = "SyncActionValue.OutContactAction")
public final class OutContactAction implements SyncAction<OutContactActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "out_contact";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String fullName;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String firstName;

    OutContactAction(String fullName, String firstName) {
        this.fullName = fullName;
        this.firstName = firstName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

    /**
     * Returns the full name of the contact.
     *
     * @return the full name, or empty if not set
     */
    public Optional<String> fullName() {
        return Optional.ofNullable(fullName);
    }

    /**
     * Returns the first name of the contact.
     *
     * @return the first name, or empty if not set
     */
    public Optional<String> firstName() {
        return Optional.ofNullable(firstName);
    }
}
