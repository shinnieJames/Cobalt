package com.github.auties00.cobalt.model.message.contact;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ContactMessage")
public final class ContactMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String displayName;

    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    String vcard;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    Boolean isSelfContact;


    ContactMessage(String displayName, String vcard, ContextInfo contextInfo, Boolean isSelfContact) {
        this.displayName = displayName;
        this.vcard = vcard;
        this.contextInfo = contextInfo;
        this.isSelfContact = isSelfContact;
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public Optional<String> vcard() {
        return Optional.ofNullable(vcard);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public boolean isSelfContact() {
        return isSelfContact != null && isSelfContact;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setVcard(String vcard) {
        this.vcard = vcard;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setSelfContact(Boolean isSelfContact) {
        this.isSelfContact = isSelfContact;
    }
}
