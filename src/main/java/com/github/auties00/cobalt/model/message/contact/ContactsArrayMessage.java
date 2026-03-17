package com.github.auties00.cobalt.model.message.contact;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ContactsArrayMessage")
public final class ContactsArrayMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String displayName;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<ContactMessage> contacts;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;


    ContactsArrayMessage(String displayName, List<ContactMessage> contacts, ContextInfo contextInfo) {
        this.displayName = displayName;
        this.contacts = contacts;
        this.contextInfo = contextInfo;
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public List<ContactMessage> contacts() {
        return contacts == null ? List.of() : Collections.unmodifiableList(contacts);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setContacts(List<ContactMessage> contacts) {
        this.contacts = contacts;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
