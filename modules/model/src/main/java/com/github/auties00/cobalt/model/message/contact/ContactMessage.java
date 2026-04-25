package com.github.auties00.cobalt.model.message.contact;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Represents a message that carries a single contact card.
 *
 * <p>A contact message shares one person's contact details with the
 * recipient. The contact is described by a human readable display name
 * and a vCard payload that follows the standard vCard format, so that
 * the recipient's device can import the contact into its address book.
 *
 * <p>This type of message is typically produced when a user picks a
 * contact from the native contact picker and sends it through a chat.
 * To share multiple contacts in a single message, use
 * {@link ContactsArrayMessage} instead.
 *
 * <p>As a {@link ContextualMessage}, this message can also carry
 * {@link ContextInfo} describing a quoted message, a forwarding score,
 * mentions and other contextual metadata.
 *
 * @implNote The WA Web generator {@code WAWebGenerateVcardMessageProto}
 *           is a tiny factory that wraps an input {@code {contextInfo, json}}
 *           pair into {@code {contactMessage: {displayName: json.vcardFormattedName,
 *           vcard: json.body, contextInfo}}}. Cobalt represents this structure
 *           statically: this protobuf message is the inner {@code {contactMessage: ...}}
 *           object and the surrounding wrapper is {@code MessageContainer.contactMessage}.
 *           Construction goes through the generated {@code ContactMessageBuilder},
 *           which is the direct analog of the JS factory call site. The JS
 *           generator never sets {@code isSelfContact}; that field is populated
 *           elsewhere by WA Web (e.g. on receive-side parsing).
 */
@ProtobufMessage(name = "Message.ContactMessage")
@WhatsAppWebModule(moduleName = "WAWebGenerateVcardMessageProto")
public final class ContactMessage implements ContextualMessage {
    /**
     * The human readable name shown to the recipient for this contact.
     *
     * <p>This field typically corresponds to the full name stored in
     * the contact's vCard and is used as a short label when the
     * recipient previews the message before opening it.
     *
     * @implNote The WA Web generator {@code WAWebGenerateVcardMessageProto}
     *           sources this value from its input {@code json.vcardFormattedName}
     *           and forwards it verbatim onto this field.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebGenerateVcardMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    String displayName;

    /**
     * The contact card payload encoded as a vCard string.
     *
     * <p>The value follows the standard vCard format (typically
     * version 3.0) and contains every shared property of the contact,
     * such as names, phone numbers, e-mail addresses and photos. The
     * recipient's client parses this value to allow importing the
     * contact into the device address book.
     *
     * @implNote The WA Web generator {@code WAWebGenerateVcardMessageProto}
     *           sources this value from its input {@code json.body} and
     *           forwards it verbatim onto this field.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    @WhatsAppWebExport(moduleName = "WAWebGenerateVcardMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    String vcard;

    /**
     * Optional contextual metadata attached to this message.
     *
     * <p>When present, this field describes a quoted message, a
     * forwarding score, mentioned participants or any other
     * context related information.
     *
     * @implNote The WA Web generator {@code WAWebGenerateVcardMessageProto}
     *           forwards its input {@code contextInfo} verbatim onto this field.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(moduleName = "WAWebGenerateVcardMessageProto", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    ContextInfo contextInfo;

    /**
     * Indicates whether the shared contact is the sender themself.
     *
     * <p>When set to {@code true}, the recipient's client may render
     * the message with a different label to highlight that the
     * sender is sharing their own contact card.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    Boolean isSelfContact;


    /**
     * Constructs a new contact message with the given values.
     *
     * <p>This constructor is package private and is invoked by the
     * generated builder. Use {@code ContactMessageBuilder} to create
     * new instances.
     *
     * @param displayName the human readable name for the contact, or {@code null}
     * @param vcard the vCard payload describing the contact, or {@code null}
     * @param contextInfo optional contextual metadata, or {@code null}
     * @param isSelfContact {@code true} if the contact represents the sender, or {@code null}
     */
    ContactMessage(String displayName, String vcard, ContextInfo contextInfo, Boolean isSelfContact) {
        this.displayName = displayName;
        this.vcard = vcard;
        this.contextInfo = contextInfo;
        this.isSelfContact = isSelfContact;
    }

    /**
     * Returns the human readable name of the shared contact.
     *
     * @return an {@link Optional} containing the display name, or an empty {@code Optional} if none was provided
     */
    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    /**
     * Returns the vCard payload describing the shared contact.
     *
     * @return an {@link Optional} containing the vCard string, or an empty {@code Optional} if none was provided
     */
    public Optional<String> vcard() {
        return Optional.ofNullable(vcard);
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
     * Returns whether the shared contact represents the sender.
     *
     * <p>When the underlying field is {@code null}, this method
     * returns {@code false}, meaning that the contact is treated as
     * a regular third party contact.
     *
     * @return {@code true} if the contact is the sender themself, {@code false} otherwise
     */
    public boolean isSelfContact() {
        return isSelfContact != null && isSelfContact;
    }

    /**
     * Sets the human readable name of the shared contact.
     *
     * @param displayName the new display name, or {@code null} to clear the value
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Sets the vCard payload describing the shared contact.
     *
     * @param vcard the new vCard string, or {@code null} to clear the value
     */
    public void setVcard(String vcard) {
        this.vcard = vcard;
    }

    /**
     * Sets the contextual metadata attached to this message.
     *
     * @param contextInfo the new {@link ContextInfo}, or {@code null} to clear the value
     */
    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    /**
     * Sets whether the shared contact represents the sender.
     *
     * @param isSelfContact {@code true} to mark the contact as the sender, {@code false} or {@code null} otherwise
     */
    public void setSelfContact(Boolean isSelfContact) {
        this.isSelfContact = isSelfContact;
    }
}
