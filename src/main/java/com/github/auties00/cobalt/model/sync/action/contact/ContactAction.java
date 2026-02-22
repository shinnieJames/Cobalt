package com.github.auties00.cobalt.model.sync.action.contact;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

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

    public ContactAction setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public ContactAction setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public ContactAction setLidJid(Jid lidJid) {
        this.lidJid = lidJid;
        return this;
    }

    public ContactAction setSaveOnPrimaryAddressbook(Boolean saveOnPrimaryAddressbook) {
        this.saveOnPrimaryAddressbook = saveOnPrimaryAddressbook;
        return this;
    }

    public ContactAction setPnJid(Jid pnJid) {
        this.pnJid = pnJid;
        return this;
    }

    public ContactAction setUsername(String username) {
        this.username = username;
        return this;
    }


}
