package com.github.auties00.cobalt.model.message.context;

import com.github.auties00.cobalt.model.message.bot.AIRichResponseMessage;
import com.github.auties00.cobalt.model.message.media.MediaMessage;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.call.CallOfferMessage;
import com.github.auties00.cobalt.model.message.commerce.*;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.group.GroupInviteMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveResponseMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateButtonReplyMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.message.list.ListMessage;
import com.github.auties00.cobalt.model.message.list.ListResponseMessage;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.AlbumMessage;
import com.github.auties00.cobalt.model.message.media.StickerPackMessage;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterAdminInviteMessage;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterFollowerInviteMessage;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.system.RequestPhoneNumberMessage;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryBundle;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryNotice;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;

import java.util.Optional;

/**
 * A {@link Message} that carries {@link ContextInfo} metadata such as quoted
 * messages, mentions, forwarding status, and other contextual attributes.
 *
 * <p>Not every message type supports context information. Only the permitted
 * subtypes of this interface expose a {@code contextInfo} accessor and setter.
 */
public sealed interface ContextualMessage extends Message permits
        MediaMessage,
    AIRichResponseMessage,
    AlbumMessage,
    ButtonsMessage,
    ButtonsResponseMessage,
    CallOfferMessage,
    ContactMessage,
    ContactsArrayMessage,
    EventMessage,
    ExtendedTextMessage,
    GroupInviteMessage,
    InteractiveMessage,
    InteractiveResponseMessage,
    ListMessage,
    ListResponseMessage,
    LiveLocationMessage,
    LocationMessage,
    MessageHistoryBundle,
    MessageHistoryNotice,
    NewsletterAdminInviteMessage,
    NewsletterFollowerInviteMessage,
    OrderMessage,
    PollCreationMessage,
    PollResultSnapshotMessage,
    ProductMessage,
    RequestPhoneNumberMessage,
    StickerPackMessage,
    TemplateButtonReplyMessage,
    TemplateMessage {

    /**
     * Returns the context information associated with this message, if present.
     *
     * @return an {@code Optional} describing the {@link ContextInfo}, or an
     *         empty {@code Optional} if no context information is set
     */
    Optional<ContextInfo> contextInfo();

    /**
     * Sets the context information for this message.
     *
     * @param contextInfo the context information to associate with this message
     * @return this message instance for method chaining
     */
    void setContextInfo(ContextInfo contextInfo);
}
