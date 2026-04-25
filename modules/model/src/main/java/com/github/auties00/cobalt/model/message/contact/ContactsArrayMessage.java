package com.github.auties00.cobalt.model.message.contact;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Represents a message that carries a collection of contact cards.
 *
 * <p>A contacts array message bundles multiple {@link ContactMessage}
 * entries so that a user can share several contacts in a single chat
 * message. Each nested contact keeps its own display name and vCard
 * payload, while the array level display name is used as a label for
 * the whole group, for example when previewing the message in a chat
 * list.
 *
 * <p>To share a single contact, use {@link ContactMessage} directly.
 *
 * <p>As a {@link ContextualMessage}, this message can also carry
 * {@link ContextInfo} describing a quoted message, a forwarding score,
 * mentions and other contextual metadata.
 *
 * @implNote The WA Web generator {@code WAWebGenerateMultiVcardMessageProto}
 *           is a tiny factory that wraps an input {@code {contextInfo, json}}
 *           pair into {@code {contactsArrayMessage: {contacts: json.vcardList.map(e => e), contextInfo}}}.
 *           The JS {@code .map(e => e)} is an identity copy that preserves
 *           element shape; the input {@code vcardList} entries are already
 *           contact-shaped records with a {@code vcard} string (they are
 *           consumed as {@code ContactMessage} protos by recipients). Cobalt
 *           represents this structure statically: this protobuf message is
 *           the inner {@code {contactsArrayMessage: ...}} object and the
 *           surrounding wrapper is {@code MessageContainer.contactsArrayMessage}.
 *           Construction goes through the generated
 *           {@code ContactsArrayMessageBuilder}, which is the direct analog of
 *           the JS factory call site. The JS generator never sets
 *           {@code displayName}; that field is populated elsewhere by WA Web
 *           (e.g. on receive-side parsing or preview formatting).
 */
@ProtobufMessage(name = "Message.ContactsArrayMessage")
@WhatsAppWebModule(moduleName = "WAWebGenerateMultiVcardMessageProto")
public final class ContactsArrayMessage implements ContextualMessage {
    /**
     * The human readable label shown for the bundle of contacts.
     *
     * <p>This value is typically used when previewing the message
     * before opening it, to summarise the set of shared contacts
     * with a single name.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String displayName;

    /**
     * The list of contacts shared by this message.
     *
     * <p>Each entry is a fully formed {@link ContactMessage} with its
     * own display name and vCard payload, so that the recipient's
     * client can import each contact independently.
     *
     * @implNote In the WA Web generator this list is produced by
     *           {@code json.vcardList.map(function(e){return e})}, an identity
     *           map that copies the array while preserving element identity.
     *           Cobalt's builder performs the same role by accepting a
     *           {@code List<ContactMessage>} directly.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebGenerateMultiVcardMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    List<ContactMessage> contacts;

    /**
     * Optional contextual metadata attached to this message.
     *
     * <p>When present, this field describes a quoted message, a
     * forwarding score, mentioned participants or any other
     * context related information.
     *
     * @implNote The WA Web generator {@code WAWebGenerateMultiVcardMessageProto}
     *           forwards its input {@code contextInfo} verbatim onto this field.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebGenerateMultiVcardMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    ContextInfo contextInfo;


    /**
     * Constructs a new contacts array message with the given values.
     *
     * <p>This constructor is package private and is invoked by the
     * generated builder. Use {@code ContactsArrayMessageBuilder} to
     * create new instances.
     *
     * @param displayName the human readable label for the bundle, or {@code null}
     * @param contacts the list of shared contacts, or {@code null}
     * @param contextInfo optional contextual metadata, or {@code null}
     */
    ContactsArrayMessage(String displayName, List<ContactMessage> contacts, ContextInfo contextInfo) {
        this.displayName = displayName;
        this.contacts = contacts;
        this.contextInfo = contextInfo;
    }

    /**
     * Returns the human readable label for the bundle of contacts.
     *
     * @return an {@link Optional} containing the display name, or an empty {@code Optional} if none was provided
     */
    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    /**
     * Returns the list of contacts shared by this message.
     *
     * <p>The returned list is unmodifiable. If no contacts were set,
     * this method returns an empty list rather than {@code null}.
     *
     * @return an unmodifiable {@link List} of {@link ContactMessage} entries, never {@code null}
     */
    public List<ContactMessage> contacts() {
        return contacts == null ? List.of() : Collections.unmodifiableList(contacts);
    }

    /**
     * Returns the contextual metadata attached to this message.
     *
     * @return an {@link Optional} containing the {@link ContextInfo}, or an empty {@code Optional} if none was provided
     */
    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    /**
     * Sets the human readable label for the bundle of contacts.
     *
     * @param displayName the new display name, or {@code null} to clear the value
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Sets the list of contacts shared by this message.
     *
     * @param contacts the new list of {@link ContactMessage} entries, or {@code null} to clear the value
     */
    public void setContacts(List<ContactMessage> contacts) {
        this.contacts = contacts;
    }

    /**
     * Sets the contextual metadata attached to this message.
     *
     * @param contextInfo the new {@link ContextInfo}, or {@code null} to clear the value
     */
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }
}
