package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.ContactAction")
public final class ContactAction implements SyncAction<ContactActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "contact";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 2;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.CRITICAL_UNBLOCK_LOW;

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


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String fullName;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String firstName;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    Jid lidJid;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean saveOnPrimaryAddressbook;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    Jid pnJid;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String username;


    ContactAction(String fullName, String firstName, Jid lidJid, Boolean saveOnPrimaryAddressbook, Jid pnJid, String username) {
        this.fullName = fullName;
        this.firstName = firstName;
        this.lidJid = lidJid;
        this.saveOnPrimaryAddressbook = saveOnPrimaryAddressbook;
        this.pnJid = pnJid;
        this.username = username;
    }

    public Optional<String> fullName() {
        return Optional.ofNullable(fullName);
    }

    public Optional<String> firstName() {
        return Optional.ofNullable(firstName);
    }

    public Optional<Jid> lidJid() {
        return Optional.ofNullable(lidJid);
    }

    public boolean saveOnPrimaryAddressbook() {
        return saveOnPrimaryAddressbook != null && saveOnPrimaryAddressbook;
    }

    public Optional<Jid> pnJid() {
        return Optional.ofNullable(pnJid);
    }

    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLidJid(Jid lidJid) {
        this.lidJid = lidJid;
    }

    public void setSaveOnPrimaryAddressbook(Boolean saveOnPrimaryAddressbook) {
        this.saveOnPrimaryAddressbook = saveOnPrimaryAddressbook;
    }

    public void setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
    }

    public void setUsername(String username) {
        this.username = username;
    }


}
