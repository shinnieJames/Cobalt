package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.message.bot.AIRichResponseMessage;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfo;
import com.github.auties00.cobalt.model.message.call.*;
import com.github.auties00.cobalt.model.message.commerce.*;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventMessage;
import com.github.auties00.cobalt.model.message.group.GroupInviteMessage;
import com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveResponseMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateButtonReplyMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.message.list.ListMessage;
import com.github.auties00.cobalt.model.message.list.ListResponseMessage;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.*;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterAdminInviteMessage;
import com.github.auties00.cobalt.model.message.newsletter.NewsletterFollowerInviteMessage;
import com.github.auties00.cobalt.model.message.payment.*;
import com.github.auties00.cobalt.model.message.poll.PollCreationMessage;
import com.github.auties00.cobalt.model.message.poll.PollResultSnapshotMessage;
import com.github.auties00.cobalt.model.message.poll.PollUpdateMessage;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.security.PlaceholderMessage;
import com.github.auties00.cobalt.model.message.security.SecretEncMessage;
import com.github.auties00.cobalt.model.message.status.StatusNotificationMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuestionAnswerMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuotedMessage;
import com.github.auties00.cobalt.model.message.status.StatusStickerInteractionMessage;
import com.github.auties00.cobalt.model.message.system.*;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryBundle;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryNotice;
import com.github.auties00.cobalt.model.message.text.*;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A container that holds exactly one WhatsApp message of any known type.
 *
 * <p>This class is the Java counterpart of WhatsApp's {@code Message} protobuf
 * definition. It models a discriminated union: although the container declares
 * many fields (one per message type), at most one payload field is populated
 * in any given instance. The populated field determines the type of message
 * that was sent or received.
 *
 * <p>In addition to the payload field, three side-channel fields may coexist
 * alongside the actual message:
 * <ul>
 * <li>{@link #messageContextInfo()} — per-message metadata such as device
 *     list information, encryption secrets, and bot metadata. This is
 *     transport-level metadata, not a message type.
 * <li>{@link #senderKeyDistributionMessage()} — group Signal encryption
 *     key distribution data that piggybacks on regular messages.
 * <li>{@link #fastRatchetKeySenderKeyDistributionMessage()} — fast ratchet
 *     variant of the sender key distribution.
 * </ul>
 *
 * <h2>FutureProofMessage Wrappers</h2>
 *
 * <p>Some fields use {@link FutureProofMessage} as their type rather than a
 * concrete message class. These are forward-compatibility envelopes: each
 * {@code FutureProofMessage} contains a nested {@code MessageContainer},
 * which in turn holds the actual message in one of its fields. This design
 * allows older clients that do not recognize a new message type to still
 * forward the raw protobuf bytes to other participants.
 *
 * <p>Examples include ephemeral (disappearing) messages, view-once media,
 * message edits, and documents with captions. The {@link #content()}
 * method recursively unwraps all envelopes to return the innermost
 * {@link Message}.
 *
 * <h2>DeviceSentMessage</h2>
 *
 * <p>When a message is sent from one linked device, WhatsApp distributes it
 * to the user's other devices wrapped in a {@link DeviceSentMessage}. The
 * {@link #content()} method transparently unwraps this layer so callers
 * receive the original message regardless of how it was delivered.
 *
 * <h2>Versioned Messages</h2>
 *
 * <p>Several message types have multiple protobuf field versions to
 * maintain backward compatibility across client generations. For example,
 * poll creation occupies five different field indices, and view-once media
 * has three versions. All versions share the same Java type. The factory
 * methods ({@link #of(Message)}, {@link #ofViewOnce(Message)}, etc.) and
 * instance converters ({@link #toViewOnce()}, {@link #toGroupStatus()},
 * etc.) always produce the newest version. The {@link #content()}
 * method transparently reads any version.
 *
 * <h2>Usage</h2>
 *
 * <p>Create a container from any supported message:
 * <pre>{@code
 *     MessageContainer container = MessageContainer.of(myImageMessage);
 *     MessageContainer text = MessageContainer.of("Hello, world!");
 *     MessageContainer ephemeral = MessageContainer.ofEphemeral(myTextMessage);
 * }</pre>
 *
 * <p>Retrieve the content, unwrapping all envelopes:
 * <pre>{@code
 *     Optional<Message> msg = container.content();
 *     Optional<ContextualMessage> ctx = container.contentWithContext();
 * }</pre>
 */
@ProtobufMessage(name = "Message")
public final class MessageContainer {
    private static final MessageContainer EMPTY = new MessageContainerBuilder().build();

    /**
     * A plain text message with no context info or formatting.
     *
     * <p>This is the legacy plain-text field. Prefer
     * {@link #extendedTextMessage} for rich text with link previews,
     * mentions, and context metadata.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String conversation;

    /**
     * Group Signal encryption key distribution data.
     *
     * <p>This is a side-channel field that can coexist with the actual
     * message payload. It distributes sender keys to group participants.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SenderKeyDistributionMessage senderKeyDistributionMessage;

    /**
     * An image message with optional caption, thumbnail, and media metadata.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ImageMessage imageMessage;

    /**
     * A single contact card (vCard) message.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ContactMessage contactMessage;

    /**
     * A static location pin message.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    LocationMessage locationMessage;

    /**
     * A rich text message supporting link previews, mentions,
     * formatting, and context info.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    ExtendedTextMessage extendedTextMessage;

    /**
     * A document file message with optional caption.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    DocumentMessage documentMessage;

    /**
     * An audio message (voice note or audio file).
     */
    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    AudioMessage audioMessage;

    /**
     * A video message (or GIF, depending on the {@code gifPlayback} flag).
     */
    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    VideoMessage videoMessage;

    /**
     * A call offer/initiation message.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    CallOfferMessage call;

    /**
     * An internal chat protocol control message.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    ChatProtocolMessage chat;

    /**
     * A protocol-level message for revocations, ephemeral settings,
     * history sync notifications, app state key sharing, message edits,
     * and other system-level operations.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    ProtocolMessage protocolMessage;

    /**
     * A multi-contact card message containing multiple vCards.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    ContactsArrayMessage contactsArrayMessage;

    /**
     * A highly structured message used for business message templates
     * with localizable parameters.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    HighlyStructuredMessage highlyStructuredMessage;

    /**
     * Fast ratchet variant of the sender key distribution.
     *
     * <p>Like {@link #senderKeyDistributionMessage}, this is a
     * side-channel field that can coexist with the actual payload.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage;

    /**
     * A send-payment message.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    SendPaymentMessage sendPaymentMessage;

    /**
     * A live location sharing message.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    LiveLocationMessage liveLocationMessage;

    /**
     * A request-payment message.
     */
    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    RequestPaymentMessage requestPaymentMessage;

    /**
     * A decline-payment-request message.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    DeclinePaymentRequestMessage declinePaymentRequestMessage;

    /**
     * A cancel-payment-request message.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    CancelPaymentRequestMessage cancelPaymentRequestMessage;

    /**
     * A business template message.
     */
    @ProtobufProperty(index = 25, type = ProtobufType.MESSAGE)
    TemplateMessage templateMessage;

    /**
     * A sticker message.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    StickerMessage stickerMessage;

    /**
     * A group invite link message.
     */
    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    GroupInviteMessage groupInviteMessage;

    /**
     * A reply to a template button.
     */
    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    TemplateButtonReplyMessage templateButtonReplyMessage;

    /**
     * A product catalog message.
     */
    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    ProductMessage productMessage;

    /**
     * A message mirrored to linked companion devices.
     *
     * <p>When a user sends a message from one device, WhatsApp distributes
     * it to the user's other linked devices wrapped in this envelope. The
     * inner {@link DeviceSentMessage#message()} contains the original
     * message.
     */
    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    DeviceSentMessage deviceSentMessage;

    /**
     * Per-message transport metadata including device list information,
     * encryption secrets, bot metadata, and limit-sharing data.
     *
     * <p>This is a side-channel field that coexists with the actual
     * message payload. It is not a message type and is excluded from
     * {@link #content()} resolution.
     */
    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    ChatMessageContextInfo messageContextInfo;

    /**
     * A selection list message.
     */
    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    ListMessage listMessage;

    /**
     * A view-once media message (V1), wrapped in a
     * {@link FutureProofMessage} envelope for forward compatibility.
     */
    @ProtobufProperty(index = 37, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessage;

    /**
     * A payment order message.
     */
    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    OrderMessage orderMessage;

    /**
     * A response to a selection list message.
     */
    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    ListResponseMessage listResponseMessage;

    /**
     * A disappearing (ephemeral) message, wrapped in a
     * {@link FutureProofMessage} envelope.
     *
     * <p>The inner container holds the actual message that will
     * auto-delete after the chat's ephemeral timer expires.
     */
    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    FutureProofMessage ephemeralMessage;

    /**
     * A payment invoice message.
     */
    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    InvoiceMessage invoiceMessage;

    /**
     * A buttons message (deprecated in favor of interactive messages).
     */
    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    ButtonsMessage buttonsMessage;

    /**
     * A response to a buttons message.
     */
    @ProtobufProperty(index = 43, type = ProtobufType.MESSAGE)
    ButtonsResponseMessage buttonsResponseMessage;

    /**
     * A payment invite message.
     */
    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    PaymentInviteMessage paymentInviteMessage;

    /**
     * An interactive message (native flows, CTAs, carousels).
     */
    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    InteractiveMessage interactiveMessage;

    /**
     * A reaction (emoji) to another message.
     */
    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    ReactionMessage reactionMessage;

    /**
     * A sticker sync remove-my-receipt message.
     */
    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    StickerSyncRMRMessage stickerSyncRmrMessage;

    /**
     * A response to an interactive message.
     */
    @ProtobufProperty(index = 48, type = ProtobufType.MESSAGE)
    InteractiveResponseMessage interactiveResponseMessage;

    /**
     * A poll creation message (V1).
     */
    @ProtobufProperty(index = 49, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessage;

    /**
     * A poll vote/update message.
     */
    @ProtobufProperty(index = 50, type = ProtobufType.MESSAGE)
    PollUpdateMessage pollUpdateMessage;

    /**
     * A keep-in-chat (bookmark/save) action message.
     */
    @ProtobufProperty(index = 51, type = ProtobufType.MESSAGE)
    KeepInChatMessage keepInChatMessage;

    /**
     * A document message with a separate caption, wrapped in a
     * {@link FutureProofMessage} envelope for forward compatibility.
     */
    @ProtobufProperty(index = 53, type = ProtobufType.MESSAGE)
    FutureProofMessage documentWithCaptionMessage;

    /**
     * A request for the recipient's phone number.
     */
    @ProtobufProperty(index = 54, type = ProtobufType.MESSAGE)
    RequestPhoneNumberMessage requestPhoneNumberMessage;

    /**
     * A view-once media message (V2), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 55, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessageV2;

    /**
     * An E2E-encrypted reaction targeting another message.
     *
     * <p>Carries {@code targetMessageKey}, {@code encPayload}, and
     * {@code encIv} so the server cannot read the reaction content.
     */
    @ProtobufProperty(index = 56, type = ProtobufType.MESSAGE)
    EncReactionMessage encReactionMessage;

    /**
     * An edited message, wrapped in a {@link FutureProofMessage}
     * envelope. The inner container holds the new message content.
     */
    @ProtobufProperty(index = 58, type = ProtobufType.MESSAGE)
    FutureProofMessage editedMessage;

    /**
     * A view-once media message (V2 extension), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 59, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessageV2Extension;

    /**
     * A poll creation message (V2).
     */
    @ProtobufProperty(index = 60, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV2;

    /**
     * A scheduled call creation message.
     */
    @ProtobufProperty(index = 61, type = ProtobufType.MESSAGE)
    ScheduledCallCreationMessage scheduledCallCreationMessage;

    /**
     * A group-mention message, wrapped in a {@link FutureProofMessage}
     * envelope. Has the highest unwrapping priority in WhatsApp Web.
     */
    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    FutureProofMessage groupMentionedMessage;

    /**
     * A pin/unpin message action in a chat.
     */
    @ProtobufProperty(index = 63, type = ProtobufType.MESSAGE)
    PinInChatMessage pinInChatMessage;

    /**
     * A poll creation message (V3).
     */
    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV3;

    /**
     * An edit to a previously scheduled call.
     */
    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    ScheduledCallEditMessage scheduledCallEditMessage;

    /**
     * A push-to-talk video (video note / circular video).
     *
     * <p>Reuses the {@link VideoMessage} type at a different protobuf
     * field index (66 vs 9) to distinguish circular video notes from
     * regular video messages.
     */
    @ProtobufProperty(index = 66, type = ProtobufType.MESSAGE)
    VideoMessage ptvMessage;

    /**
     * A bot invocation message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 67, type = ProtobufType.MESSAGE)
    FutureProofMessage botInvokeMessage;

    /**
     * A call log entry message.
     */
    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    CallLogMessage callLogMesssage;

    /**
     * A history sync bundle containing encrypted message history data.
     */
    @ProtobufProperty(index = 70, type = ProtobufType.MESSAGE)
    MessageHistoryBundle messageHistoryBundle;

    /**
     * An E2E-encrypted comment targeting another message.
     */
    @ProtobufProperty(index = 71, type = ProtobufType.MESSAGE)
    EncCommentMessage encCommentMessage;

    /**
     * A business call (BCall) message.
     */
    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    BCallMessage bcallMessage;

    /**
     * An animated Lottie sticker message, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 74, type = ProtobufType.MESSAGE)
    FutureProofMessage lottieStickerMessage;

    /**
     * A calendar event message.
     */
    @ProtobufProperty(index = 75, type = ProtobufType.MESSAGE)
    EventMessage eventMessage;

    /**
     * An E2E-encrypted event response (RSVP) message.
     */
    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    EncEventResponseMessage encEventResponseMessage;

    /**
     * A comment on a message (channel/community context).
     */
    @ProtobufProperty(index = 77, type = ProtobufType.MESSAGE)
    CommentMessage commentMessage;

    /**
     * A newsletter (channel) admin invite message.
     */
    @ProtobufProperty(index = 78, type = ProtobufType.MESSAGE)
    NewsletterAdminInviteMessage newsletterAdminInviteMessage;

    /**
     * A placeholder for a message that cannot be displayed by the
     * current client version.
     */
    @ProtobufProperty(index = 80, type = ProtobufType.MESSAGE)
    PlaceholderMessage placeholderMessage;

    /**
     * A secret-encrypted message carrying an E2E-encrypted payload
     * targeting another message (used for event edits).
     */
    @ProtobufProperty(index = 82, type = ProtobufType.MESSAGE)
    SecretEncMessage secretEncryptedMessage;

    /**
     * An album message grouping multiple media messages together.
     */
    @ProtobufProperty(index = 83, type = ProtobufType.MESSAGE)
    AlbumMessage albumMessage;

    /**
     * An event cover image, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 85, type = ProtobufType.MESSAGE)
    FutureProofMessage eventCoverImage;

    /**
     * A sticker pack message.
     */
    @ProtobufProperty(index = 86, type = ProtobufType.MESSAGE)
    StickerPackMessage stickerPackMessage;

    /**
     * A status mention message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 87, type = ProtobufType.MESSAGE)
    FutureProofMessage statusMentionMessage;

    /**
     * A snapshot of poll results (V1).
     */
    @ProtobufProperty(index = 88, type = ProtobufType.MESSAGE)
    PollResultSnapshotMessage pollResultSnapshotMessage;

    /**
     * A poll creation option image, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 90, type = ProtobufType.MESSAGE)
    FutureProofMessage pollCreationOptionImageMessage;

    /**
     * An associated child message, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 91, type = ProtobufType.MESSAGE)
    FutureProofMessage associatedChildMessage;

    /**
     * A group status mention message, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 92, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMentionMessage;

    /**
     * A poll creation message (V4), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 93, type = ProtobufType.MESSAGE)
    FutureProofMessage pollCreationMessageV4;

    /**
     * A status "Add Yours" sticker/prompt, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 95, type = ProtobufType.MESSAGE)
    FutureProofMessage statusAddYours;

    /**
     * A group status message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 96, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMessage;

    /**
     * A rich response from the WhatsApp AI bot, composed of typed
     * sub-message fragments (text, code, tables, images, etc.).
     */
    @ProtobufProperty(index = 97, type = ProtobufType.MESSAGE)
    AIRichResponseMessage richResponseMessage;

    /**
     * A status notification message (e.g. reaction or interaction
     * notification on a status update).
     */
    @ProtobufProperty(index = 98, type = ProtobufType.MESSAGE)
    StatusNotificationMessage statusNotificationMessage;

    /**
     * A limit-sharing message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 99, type = ProtobufType.MESSAGE)
    FutureProofMessage limitSharingMessage;

    /**
     * A bot task message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 100, type = ProtobufType.MESSAGE)
    FutureProofMessage botTaskMessage;

    /**
     * A question message (AI/bot context), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 101, type = ProtobufType.MESSAGE)
    FutureProofMessage questionMessage;

    /**
     * A message history notice with sync metadata.
     */
    @ProtobufProperty(index = 102, type = ProtobufType.MESSAGE)
    MessageHistoryNotice messageHistoryNotice;

    /**
     * A group status message (V2), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 103, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMessageV2;

    /**
     * A bot-forwarded message, wrapped in a {@link FutureProofMessage}
     * envelope.
     */
    @ProtobufProperty(index = 104, type = ProtobufType.MESSAGE)
    FutureProofMessage botForwardedMessage;

    /**
     * A status question-and-answer message.
     */
    @ProtobufProperty(index = 105, type = ProtobufType.MESSAGE)
    StatusQuestionAnswerMessage statusQuestionAnswerMessage;

    /**
     * A question reply message (AI/bot context), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 106, type = ProtobufType.MESSAGE)
    FutureProofMessage questionReplyMessage;

    /**
     * A question response message (AI/bot context).
     */
    @ProtobufProperty(index = 107, type = ProtobufType.MESSAGE)
    QuestionResponseMessage questionResponseMessage;

    /**
     * A quoted status message.
     */
    @ProtobufProperty(index = 109, type = ProtobufType.MESSAGE)
    StatusQuotedMessage statusQuotedMessage;

    /**
     * A sticker interaction on a status update.
     */
    @ProtobufProperty(index = 110, type = ProtobufType.MESSAGE)
    StatusStickerInteractionMessage statusStickerInteractionMessage;

    /**
     * A poll creation message (V5).
     */
    @ProtobufProperty(index = 111, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV5;

    /**
     * A newsletter (channel) follower invite message (V2).
     */
    @ProtobufProperty(index = 113, type = ProtobufType.MESSAGE)
    NewsletterFollowerInviteMessage newsletterFollowerInviteMessageV2;

    /**
     * A snapshot of poll results (V3).
     */
    @ProtobufProperty(index = 115, type = ProtobufType.MESSAGE)
    PollResultSnapshotMessage pollResultSnapshotMessageV3;

    /**
     * A newsletter admin profile message, wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 116, type = ProtobufType.MESSAGE)
    FutureProofMessage newsletterAdminProfileMessage;

    /**
     * A newsletter admin profile message (V2), wrapped in a
     * {@link FutureProofMessage} envelope.
     */
    @ProtobufProperty(index = 117, type = ProtobufType.MESSAGE)
    FutureProofMessage newsletterAdminProfileMessageV2;


    MessageContainer(String conversation, SenderKeyDistributionMessage senderKeyDistributionMessage, ImageMessage imageMessage, ContactMessage contactMessage, LocationMessage locationMessage, ExtendedTextMessage extendedTextMessage, DocumentMessage documentMessage, AudioMessage audioMessage, VideoMessage videoMessage, CallOfferMessage call, ChatProtocolMessage chat, ProtocolMessage protocolMessage, ContactsArrayMessage contactsArrayMessage, HighlyStructuredMessage highlyStructuredMessage, SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage, SendPaymentMessage sendPaymentMessage, LiveLocationMessage liveLocationMessage, RequestPaymentMessage requestPaymentMessage, DeclinePaymentRequestMessage declinePaymentRequestMessage, CancelPaymentRequestMessage cancelPaymentRequestMessage, TemplateMessage templateMessage, StickerMessage stickerMessage, GroupInviteMessage groupInviteMessage, TemplateButtonReplyMessage templateButtonReplyMessage, ProductMessage productMessage, DeviceSentMessage deviceSentMessage, ChatMessageContextInfo messageContextInfo, ListMessage listMessage, FutureProofMessage viewOnceMessage, OrderMessage orderMessage, ListResponseMessage listResponseMessage, FutureProofMessage ephemeralMessage, InvoiceMessage invoiceMessage, ButtonsMessage buttonsMessage, ButtonsResponseMessage buttonsResponseMessage, PaymentInviteMessage paymentInviteMessage, InteractiveMessage interactiveMessage, ReactionMessage reactionMessage, StickerSyncRMRMessage stickerSyncRmrMessage, InteractiveResponseMessage interactiveResponseMessage, PollCreationMessage pollCreationMessage, PollUpdateMessage pollUpdateMessage, KeepInChatMessage keepInChatMessage, FutureProofMessage documentWithCaptionMessage, RequestPhoneNumberMessage requestPhoneNumberMessage, FutureProofMessage viewOnceMessageV2, EncReactionMessage encReactionMessage, FutureProofMessage editedMessage, FutureProofMessage viewOnceMessageV2Extension, PollCreationMessage pollCreationMessageV2, ScheduledCallCreationMessage scheduledCallCreationMessage, FutureProofMessage groupMentionedMessage, PinInChatMessage pinInChatMessage, PollCreationMessage pollCreationMessageV3, ScheduledCallEditMessage scheduledCallEditMessage, VideoMessage ptvMessage, FutureProofMessage botInvokeMessage, CallLogMessage callLogMesssage, MessageHistoryBundle messageHistoryBundle, EncCommentMessage encCommentMessage, BCallMessage bcallMessage, FutureProofMessage lottieStickerMessage, EventMessage eventMessage, EncEventResponseMessage encEventResponseMessage, CommentMessage commentMessage, NewsletterAdminInviteMessage newsletterAdminInviteMessage, PlaceholderMessage placeholderMessage, SecretEncMessage secretEncryptedMessage, AlbumMessage albumMessage, FutureProofMessage eventCoverImage, StickerPackMessage stickerPackMessage, FutureProofMessage statusMentionMessage, PollResultSnapshotMessage pollResultSnapshotMessage, FutureProofMessage pollCreationOptionImageMessage, FutureProofMessage associatedChildMessage, FutureProofMessage groupStatusMentionMessage, FutureProofMessage pollCreationMessageV4, FutureProofMessage statusAddYours, FutureProofMessage groupStatusMessage, AIRichResponseMessage richResponseMessage, StatusNotificationMessage statusNotificationMessage, FutureProofMessage limitSharingMessage, FutureProofMessage botTaskMessage, FutureProofMessage questionMessage, MessageHistoryNotice messageHistoryNotice, FutureProofMessage groupStatusMessageV2, FutureProofMessage botForwardedMessage, StatusQuestionAnswerMessage statusQuestionAnswerMessage, FutureProofMessage questionReplyMessage, QuestionResponseMessage questionResponseMessage, StatusQuotedMessage statusQuotedMessage, StatusStickerInteractionMessage statusStickerInteractionMessage, PollCreationMessage pollCreationMessageV5, NewsletterFollowerInviteMessage newsletterFollowerInviteMessageV2, PollResultSnapshotMessage pollResultSnapshotMessageV3, FutureProofMessage newsletterAdminProfileMessage, FutureProofMessage newsletterAdminProfileMessageV2) {
        this.conversation = conversation;
        this.senderKeyDistributionMessage = senderKeyDistributionMessage;
        this.imageMessage = imageMessage;
        this.contactMessage = contactMessage;
        this.locationMessage = locationMessage;
        this.extendedTextMessage = extendedTextMessage;
        this.documentMessage = documentMessage;
        this.audioMessage = audioMessage;
        this.videoMessage = videoMessage;
        this.call = call;
        this.chat = chat;
        this.protocolMessage = protocolMessage;
        this.contactsArrayMessage = contactsArrayMessage;
        this.highlyStructuredMessage = highlyStructuredMessage;
        this.fastRatchetKeySenderKeyDistributionMessage = fastRatchetKeySenderKeyDistributionMessage;
        this.sendPaymentMessage = sendPaymentMessage;
        this.liveLocationMessage = liveLocationMessage;
        this.requestPaymentMessage = requestPaymentMessage;
        this.declinePaymentRequestMessage = declinePaymentRequestMessage;
        this.cancelPaymentRequestMessage = cancelPaymentRequestMessage;
        this.templateMessage = templateMessage;
        this.stickerMessage = stickerMessage;
        this.groupInviteMessage = groupInviteMessage;
        this.templateButtonReplyMessage = templateButtonReplyMessage;
        this.productMessage = productMessage;
        this.deviceSentMessage = deviceSentMessage;
        this.messageContextInfo = messageContextInfo;
        this.listMessage = listMessage;
        this.viewOnceMessage = viewOnceMessage;
        this.orderMessage = orderMessage;
        this.listResponseMessage = listResponseMessage;
        this.ephemeralMessage = ephemeralMessage;
        this.invoiceMessage = invoiceMessage;
        this.buttonsMessage = buttonsMessage;
        this.buttonsResponseMessage = buttonsResponseMessage;
        this.paymentInviteMessage = paymentInviteMessage;
        this.interactiveMessage = interactiveMessage;
        this.reactionMessage = reactionMessage;
        this.stickerSyncRmrMessage = stickerSyncRmrMessage;
        this.interactiveResponseMessage = interactiveResponseMessage;
        this.pollCreationMessage = pollCreationMessage;
        this.pollUpdateMessage = pollUpdateMessage;
        this.keepInChatMessage = keepInChatMessage;
        this.documentWithCaptionMessage = documentWithCaptionMessage;
        this.requestPhoneNumberMessage = requestPhoneNumberMessage;
        this.viewOnceMessageV2 = viewOnceMessageV2;
        this.encReactionMessage = encReactionMessage;
        this.editedMessage = editedMessage;
        this.viewOnceMessageV2Extension = viewOnceMessageV2Extension;
        this.pollCreationMessageV2 = pollCreationMessageV2;
        this.scheduledCallCreationMessage = scheduledCallCreationMessage;
        this.groupMentionedMessage = groupMentionedMessage;
        this.pinInChatMessage = pinInChatMessage;
        this.pollCreationMessageV3 = pollCreationMessageV3;
        this.scheduledCallEditMessage = scheduledCallEditMessage;
        this.ptvMessage = ptvMessage;
        this.botInvokeMessage = botInvokeMessage;
        this.callLogMesssage = callLogMesssage;
        this.messageHistoryBundle = messageHistoryBundle;
        this.encCommentMessage = encCommentMessage;
        this.bcallMessage = bcallMessage;
        this.lottieStickerMessage = lottieStickerMessage;
        this.eventMessage = eventMessage;
        this.encEventResponseMessage = encEventResponseMessage;
        this.commentMessage = commentMessage;
        this.newsletterAdminInviteMessage = newsletterAdminInviteMessage;
        this.placeholderMessage = placeholderMessage;
        this.secretEncryptedMessage = secretEncryptedMessage;
        this.albumMessage = albumMessage;
        this.eventCoverImage = eventCoverImage;
        this.stickerPackMessage = stickerPackMessage;
        this.statusMentionMessage = statusMentionMessage;
        this.pollResultSnapshotMessage = pollResultSnapshotMessage;
        this.pollCreationOptionImageMessage = pollCreationOptionImageMessage;
        this.associatedChildMessage = associatedChildMessage;
        this.groupStatusMentionMessage = groupStatusMentionMessage;
        this.pollCreationMessageV4 = pollCreationMessageV4;
        this.statusAddYours = statusAddYours;
        this.groupStatusMessage = groupStatusMessage;
        this.richResponseMessage = richResponseMessage;
        this.statusNotificationMessage = statusNotificationMessage;
        this.limitSharingMessage = limitSharingMessage;
        this.botTaskMessage = botTaskMessage;
        this.questionMessage = questionMessage;
        this.messageHistoryNotice = messageHistoryNotice;
        this.groupStatusMessageV2 = groupStatusMessageV2;
        this.botForwardedMessage = botForwardedMessage;
        this.statusQuestionAnswerMessage = statusQuestionAnswerMessage;
        this.questionReplyMessage = questionReplyMessage;
        this.questionResponseMessage = questionResponseMessage;
        this.statusQuotedMessage = statusQuotedMessage;
        this.statusStickerInteractionMessage = statusStickerInteractionMessage;
        this.pollCreationMessageV5 = pollCreationMessageV5;
        this.newsletterFollowerInviteMessageV2 = newsletterFollowerInviteMessageV2;
        this.pollResultSnapshotMessageV3 = pollResultSnapshotMessageV3;
        this.newsletterAdminProfileMessage = newsletterAdminProfileMessage;
        this.newsletterAdminProfileMessageV2 = newsletterAdminProfileMessageV2;
    }

    /**
     * Returns an empty message container with no fields populated.
     *
     * @return a non-null empty container
     */
    public static MessageContainer empty() {
        return EMPTY;
    }

    /**
     * Constructs a new container wrapping the given text as a plain
     * {@code conversation} message (protobuf field 1). For rich text with
     * context info, link previews, or formatting, construct an
     * {@link ExtendedTextMessage} and pass it to {@link #of(Message)}.
     *
     * @param text the plain text content
     * @return a non-null container
     */
    public static MessageContainer of(String text) {
        return new MessageContainerBuilder()
                .conversation(text)
                .build();
    }

    /**
     * Constructs a new container wrapping the given message in its
     * corresponding protobuf field. The correct field is selected via
     * pattern matching on the message's concrete type.
     *
     * <p>Message types that do not correspond to any top-level
     * {@code MessageContainer} field (such as sub-messages embedded in
     * {@link ProtocolMessage}) are silently ignored, producing an empty
     * container.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer of(T message) {
        var builder = new MessageContainerBuilder();
        switch (message) {
            case ImageMessage m -> builder.imageMessage(m);
            case ContactMessage m -> builder.contactMessage(m);
            case LocationMessage m -> builder.locationMessage(m);
            case ExtendedTextMessage m -> builder.extendedTextMessage(m);
            case DocumentMessage m -> builder.documentMessage(m);
            case AudioMessage m -> builder.audioMessage(m);
            case VideoMessage m -> builder.videoMessage(m);
            case StickerMessage m -> builder.stickerMessage(m);
            case StickerPackMessage m -> builder.stickerPackMessage(m);
            case CallOfferMessage m -> builder.call(m);
            case ChatProtocolMessage m -> builder.chat(m);
            case ProtocolMessage m -> builder.protocolMessage(m);
            case ContactsArrayMessage m -> builder.contactsArrayMessage(m);
            case HighlyStructuredMessage m -> builder.highlyStructuredMessage(m);
            case SenderKeyDistributionMessage m -> builder.senderKeyDistributionMessage(m);
            case SendPaymentMessage m -> builder.sendPaymentMessage(m);
            case LiveLocationMessage m -> builder.liveLocationMessage(m);
            case RequestPaymentMessage m -> builder.requestPaymentMessage(m);
            case DeclinePaymentRequestMessage m -> builder.declinePaymentRequestMessage(m);
            case CancelPaymentRequestMessage m -> builder.cancelPaymentRequestMessage(m);
            case TemplateMessage m -> builder.templateMessage(m);
            case GroupInviteMessage m -> builder.groupInviteMessage(m);
            case TemplateButtonReplyMessage m -> builder.templateButtonReplyMessage(m);
            case ProductMessage m -> builder.productMessage(m);
            case DeviceSentMessage m -> builder.deviceSentMessage(m);
            case ListMessage m -> builder.listMessage(m);
            case OrderMessage m -> builder.orderMessage(m);
            case ListResponseMessage m -> builder.listResponseMessage(m);
            case InvoiceMessage m -> builder.invoiceMessage(m);
            case ButtonsMessage m -> builder.buttonsMessage(m);
            case ButtonsResponseMessage m -> builder.buttonsResponseMessage(m);
            case PaymentInviteMessage m -> builder.paymentInviteMessage(m);
            case InteractiveMessage m -> builder.interactiveMessage(m);
            case ReactionMessage m -> builder.reactionMessage(m);
            case StickerSyncRMRMessage m -> builder.stickerSyncRmrMessage(m);
            case InteractiveResponseMessage m -> builder.interactiveResponseMessage(m);
            case PollCreationMessage m -> builder.pollCreationMessageV5(m);
            case PollUpdateMessage m -> builder.pollUpdateMessage(m);
            case KeepInChatMessage m -> builder.keepInChatMessage(m);
            case RequestPhoneNumberMessage m -> builder.requestPhoneNumberMessage(m);
            case EncReactionMessage m -> builder.encReactionMessage(m);
            case ScheduledCallCreationMessage m -> builder.scheduledCallCreationMessage(m);
            case PinInChatMessage m -> builder.pinInChatMessage(m);
            case ScheduledCallEditMessage m -> builder.scheduledCallEditMessage(m);
            case CallLogMessage m -> builder.callLogMesssage(m);
            case MessageHistoryBundle m -> builder.messageHistoryBundle(m);
            case EncCommentMessage m -> builder.encCommentMessage(m);
            case BCallMessage m -> builder.bcallMessage(m);
            case EventMessage m -> builder.eventMessage(m);
            case EncEventResponseMessage m -> builder.encEventResponseMessage(m);
            case CommentMessage m -> builder.commentMessage(m);
            case NewsletterAdminInviteMessage m -> builder.newsletterAdminInviteMessage(m);
            case NewsletterFollowerInviteMessage m -> builder.newsletterFollowerInviteMessageV2(m);
            case PlaceholderMessage m -> builder.placeholderMessage(m);
            case SecretEncMessage m -> builder.secretEncryptedMessage(m);
            case AlbumMessage m -> builder.albumMessage(m);
            case PollResultSnapshotMessage m -> builder.pollResultSnapshotMessageV3(m);
            case StatusNotificationMessage m -> builder.statusNotificationMessage(m);
            case MessageHistoryNotice m -> builder.messageHistoryNotice(m);
            case StatusQuestionAnswerMessage m -> builder.statusQuestionAnswerMessage(m);
            case QuestionResponseMessage m -> builder.questionResponseMessage(m);
            case StatusQuotedMessage m -> builder.statusQuotedMessage(m);
            case StatusStickerInteractionMessage m -> builder.statusStickerInteractionMessage(m);
            default -> {}
        }
        return builder.build();
    }

    /**
     * Wraps the given message inside an ephemeral (disappearing)
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to make ephemeral
     * @param <T>     the compile-time message type
     * @return a non-null ephemeral container
     */
    public static <T extends Message> MessageContainer ofEphemeral(T message) {
        return new MessageContainerBuilder()
                .ephemeralMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a view-once
     * {@link FutureProofMessage} envelope.
     *
     * <p>Internally uses the newest view-once format.
     *
     * @param message the message to make view-once
     * @param <T>     the compile-time message type
     * @return a non-null view-once container
     */
    public static <T extends Message> MessageContainer ofViewOnce(T message) {
        return new MessageContainerBuilder()
                .viewOnceMessageV2Extension(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside an edited
     * {@link FutureProofMessage} envelope.
     *
     * @param message the edited message content
     * @param <T>     the compile-time message type
     * @return a non-null edited container
     */
    public static <T extends Message> MessageContainer ofEdited(T message) {
        return new MessageContainerBuilder()
                .editedMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a document-with-caption
     * {@link FutureProofMessage} envelope.
     *
     * @param message the document message with caption
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofDocumentWithCaption(T message) {
        return new MessageContainerBuilder()
                .documentWithCaptionMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a group-mentioned
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofGroupMentioned(T message) {
        return new MessageContainerBuilder()
                .groupMentionedMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a bot-invoke
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofBotInvoke(T message) {
        return new MessageContainerBuilder()
                .botInvokeMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a Lottie sticker
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofLottieSticker(T message) {
        return new MessageContainerBuilder()
                .lottieStickerMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside an event cover image
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofEventCoverImage(T message) {
        return new MessageContainerBuilder()
                .eventCoverImage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a status mention
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofStatusMention(T message) {
        return new MessageContainerBuilder()
                .statusMentionMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a poll creation option image
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofPollCreationOptionImage(T message) {
        return new MessageContainerBuilder()
                .pollCreationOptionImageMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside an associated child
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofAssociatedChild(T message) {
        return new MessageContainerBuilder()
                .associatedChildMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a group status mention
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofGroupStatusMention(T message) {
        return new MessageContainerBuilder()
                .groupStatusMentionMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a status "Add Yours"
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofStatusAddYours(T message) {
        return new MessageContainerBuilder()
                .statusAddYours(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a group status
     * {@link FutureProofMessage} envelope.
     *
     * <p>Internally uses the newest group status format.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null group status container
     */
    public static <T extends Message> MessageContainer ofGroupStatus(T message) {
        return new MessageContainerBuilder()
                .groupStatusMessageV2(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a limit-sharing
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofLimitSharing(T message) {
        return new MessageContainerBuilder()
                .limitSharingMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a bot task
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofBotTask(T message) {
        return new MessageContainerBuilder()
                .botTaskMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a question
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofQuestion(T message) {
        return new MessageContainerBuilder()
                .questionMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a bot-forwarded
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofBotForwarded(T message) {
        return new MessageContainerBuilder()
                .botForwardedMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a question reply
     * {@link FutureProofMessage} envelope.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null container
     */
    public static <T extends Message> MessageContainer ofQuestionReply(T message) {
        return new MessageContainerBuilder()
                .questionReplyMessage(wrapFutureProof(message))
                .build();
    }

    /**
     * Wraps the given message inside a newsletter admin profile
     * {@link FutureProofMessage} envelope.
     *
     * <p>Internally uses the newest newsletter admin profile format.
     *
     * @param message the message to wrap
     * @param <T>     the compile-time message type
     * @return a non-null newsletter admin profile container
     */
    public static <T extends Message> MessageContainer ofNewsletterAdminProfile(T message) {
        return new MessageContainerBuilder()
                .newsletterAdminProfileMessageV2(wrapFutureProof(message))
                .build();
    }

    /**
     * Creates a {@link FutureProofMessage} wrapping the given message.
     */
    private static <T extends Message> FutureProofMessage wrapFutureProof(T message) {
        return new FutureProofMessageBuilder()
                .messageContainer(MessageContainer.of(message))
                .build();
    }

    /**
     * Returns the first populated message inside this container,
     * recursively unwrapping all {@link FutureProofMessage} and
     * {@link DeviceSentMessage} envelopes.
     *
     * <p>Side-channel fields ({@link #messageContextInfo()},
     * {@link #senderKeyDistributionMessage()}, and
     * {@link #fastRatchetKeySenderKeyDistributionMessage()}) are not
     * considered payload and are skipped.
     *
     * <p>If no payload field is populated, returns an empty
     * {@code Message}.
     *
     * @return a {@code Message} describing the innermost {@link Message}
     */
    public Message content() {
        // FutureProofMessage wrappers — WhatsApp priority order first
        if (groupMentionedMessage != null) return unboxFutureProof(groupMentionedMessage).content();
        if (documentWithCaptionMessage != null) return unboxFutureProof(documentWithCaptionMessage).content();
        if (viewOnceMessage != null) return unboxFutureProof(viewOnceMessage).content();
        if (viewOnceMessageV2 != null) return unboxFutureProof(viewOnceMessageV2).content();
        if (viewOnceMessageV2Extension != null) return unboxFutureProof(viewOnceMessageV2Extension).content();
        if (ephemeralMessage != null) return unboxFutureProof(ephemeralMessage).content();
        if (editedMessage != null) return unboxFutureProof(editedMessage).content();
        if (botInvokeMessage != null) return unboxFutureProof(botInvokeMessage).content();
        // Remaining FutureProofMessage wrappers in field index order
        if (lottieStickerMessage != null) return unboxFutureProof(lottieStickerMessage).content();
        if (eventCoverImage != null) return unboxFutureProof(eventCoverImage).content();
        if (statusMentionMessage != null) return unboxFutureProof(statusMentionMessage).content();
        if (pollCreationOptionImageMessage != null) return unboxFutureProof(pollCreationOptionImageMessage).content();
        if (associatedChildMessage != null) return unboxFutureProof(associatedChildMessage).content();
        if (groupStatusMentionMessage != null) return unboxFutureProof(groupStatusMentionMessage).content();
        if (pollCreationMessageV4 != null) return unboxFutureProof(pollCreationMessageV4).content();
        if (statusAddYours != null) return unboxFutureProof(statusAddYours).content();
        if (groupStatusMessage != null) return unboxFutureProof(groupStatusMessage).content();
        if (limitSharingMessage != null) return unboxFutureProof(limitSharingMessage).content();
        if (botTaskMessage != null) return unboxFutureProof(botTaskMessage).content();
        if (questionMessage != null) return unboxFutureProof(questionMessage).content();
        if (groupStatusMessageV2 != null) return unboxFutureProof(groupStatusMessageV2).content();
        if (botForwardedMessage != null) return unboxFutureProof(botForwardedMessage).content();
        if (questionReplyMessage != null) return unboxFutureProof(questionReplyMessage).content();
        if (newsletterAdminProfileMessage != null) return unboxFutureProof(newsletterAdminProfileMessage).content();
        if (newsletterAdminProfileMessageV2 != null) return unboxFutureProof(newsletterAdminProfileMessageV2).content();
        // DeviceSentMessage wrapping
        if (deviceSentMessage != null && deviceSentMessage.message().isPresent()) return deviceSentMessage.message().get().content();
        // Direct message fields in protobuf index order
        if (conversation != null) return new ExtendedTextMessageBuilder().text(conversation).build();
        if (imageMessage != null) return imageMessage;
        if (contactMessage != null) return contactMessage;
        if (locationMessage != null) return locationMessage;
        if (extendedTextMessage != null) return extendedTextMessage;
        if (documentMessage != null) return documentMessage;
        if (audioMessage != null) return audioMessage;
        if (videoMessage != null) return videoMessage;
        if (call != null) return call;
        if (chat != null) return chat;
        if (protocolMessage != null) return protocolMessage;
        if (contactsArrayMessage != null) return contactsArrayMessage;
        if (highlyStructuredMessage != null) return highlyStructuredMessage;
        if (sendPaymentMessage != null) return sendPaymentMessage;
        if (liveLocationMessage != null) return liveLocationMessage;
        if (requestPaymentMessage != null) return requestPaymentMessage;
        if (declinePaymentRequestMessage != null) return declinePaymentRequestMessage;
        if (cancelPaymentRequestMessage != null) return cancelPaymentRequestMessage;
        if (templateMessage != null) return templateMessage;
        if (stickerMessage != null) return stickerMessage;
        if (groupInviteMessage != null) return groupInviteMessage;
        if (templateButtonReplyMessage != null) return templateButtonReplyMessage;
        if (productMessage != null) return productMessage;
        if (listMessage != null) return listMessage;
        if (orderMessage != null) return orderMessage;
        if (listResponseMessage != null) return listResponseMessage;
        if (invoiceMessage != null) return invoiceMessage;
        if (buttonsMessage != null) return buttonsMessage;
        if (buttonsResponseMessage != null) return buttonsResponseMessage;
        if (paymentInviteMessage != null) return paymentInviteMessage;
        if (interactiveMessage != null) return interactiveMessage;
        if (reactionMessage != null) return reactionMessage;
        if (stickerSyncRmrMessage != null) return stickerSyncRmrMessage;
        if (interactiveResponseMessage != null) return interactiveResponseMessage;
        if (pollCreationMessage != null) return pollCreationMessage;
        if (pollUpdateMessage != null) return pollUpdateMessage;
        if (keepInChatMessage != null) return keepInChatMessage;
        if (requestPhoneNumberMessage != null) return requestPhoneNumberMessage;
        if (encReactionMessage != null) return encReactionMessage;
        if (pollCreationMessageV2 != null) return pollCreationMessageV2;
        if (scheduledCallCreationMessage != null) return scheduledCallCreationMessage;
        if (pinInChatMessage != null) return pinInChatMessage;
        if (pollCreationMessageV3 != null) return pollCreationMessageV3;
        if (scheduledCallEditMessage != null) return scheduledCallEditMessage;
        if (ptvMessage != null) return ptvMessage;
        if (callLogMesssage != null) return callLogMesssage;
        if (messageHistoryBundle != null) return messageHistoryBundle;
        if (encCommentMessage != null) return encCommentMessage;
        if (bcallMessage != null) return bcallMessage;
        if (eventMessage != null) return eventMessage;
        if (encEventResponseMessage != null) return encEventResponseMessage;
        if (commentMessage != null) return commentMessage;
        if (newsletterAdminInviteMessage != null) return newsletterAdminInviteMessage;
        if (placeholderMessage != null) return placeholderMessage;
        if (secretEncryptedMessage != null) return secretEncryptedMessage;
        if (albumMessage != null) return albumMessage;
        if (stickerPackMessage != null) return stickerPackMessage;
        if (pollResultSnapshotMessage != null) return pollResultSnapshotMessage;
        if (richResponseMessage != null) return richResponseMessage;
        if (statusNotificationMessage != null) return statusNotificationMessage;
        if (messageHistoryNotice != null) return messageHistoryNotice;
        if (statusQuestionAnswerMessage != null) return statusQuestionAnswerMessage;
        if (questionResponseMessage != null) return questionResponseMessage;
        if (statusQuotedMessage != null) return statusQuotedMessage;
        if (statusStickerInteractionMessage != null) return statusStickerInteractionMessage;
        if (pollCreationMessageV5 != null) return pollCreationMessageV5;
        if (newsletterFollowerInviteMessageV2 != null) return newsletterFollowerInviteMessageV2;
        if (pollResultSnapshotMessageV3 != null) return pollResultSnapshotMessageV3;
        return EmptyMessage.INSTANCE;
    }

    /**
     * Returns the first populated {@link ContextualMessage} inside this
     * container, unwrapping all envelopes. If the innermost message does
     * not implement {@code ContextualMessage}, returns an empty
     * {@code Optional}.
     *
     * @return an {@code Optional} describing the {@link ContextualMessage}
     */
    public Optional<ContextualMessage> contextualContent() {
        return content() instanceof ContextualMessage contextualMessage
                ? Optional.of(contextualMessage)
                : Optional.empty();
    }

    /**
     * Returns the {@link FutureProofMessageType} identifying which wrapper
     * field is populated in this container, or {@link FutureProofMessageType#NONE}
     * if no wrapper is present.
     *
     * @return the wrapper type, never {@code null}
     */
    public FutureProofMessageType futureProofContentType() {
        if (groupMentionedMessage != null) return FutureProofMessageType.GROUP_MENTIONED;
        if (documentWithCaptionMessage != null) return FutureProofMessageType.DOCUMENT_WITH_CAPTION;
        if (viewOnceMessage != null || viewOnceMessageV2 != null || viewOnceMessageV2Extension != null) return FutureProofMessageType.VIEW_ONCE;
        if (ephemeralMessage != null) return FutureProofMessageType.EPHEMERAL;
        if (editedMessage != null) return FutureProofMessageType.EDITED;
        if (botInvokeMessage != null) return FutureProofMessageType.BOT_INVOKE;
        if (lottieStickerMessage != null) return FutureProofMessageType.LOTTIE_STICKER;
        if (eventCoverImage != null) return FutureProofMessageType.EVENT_COVER_IMAGE;
        if (statusMentionMessage != null) return FutureProofMessageType.STATUS_MENTION;
        if (pollCreationOptionImageMessage != null) return FutureProofMessageType.POLL_CREATION_OPTION_IMAGE;
        if (associatedChildMessage != null) return FutureProofMessageType.ASSOCIATED_CHILD;
        if (groupStatusMentionMessage != null) return FutureProofMessageType.GROUP_STATUS_MENTION;
        if (pollCreationMessageV4 != null) return FutureProofMessageType.POLL_CREATION;
        if (statusAddYours != null) return FutureProofMessageType.STATUS_ADD_YOURS;
        if (groupStatusMessage != null || groupStatusMessageV2 != null) return FutureProofMessageType.GROUP_STATUS;
        if (limitSharingMessage != null) return FutureProofMessageType.LIMIT_SHARING;
        if (botTaskMessage != null) return FutureProofMessageType.BOT_TASK;
        if (questionMessage != null) return FutureProofMessageType.QUESTION;
        if (botForwardedMessage != null) return FutureProofMessageType.BOT_FORWARDED;
        if (questionReplyMessage != null) return FutureProofMessageType.QUESTION_REPLY;
        if (newsletterAdminProfileMessage != null || newsletterAdminProfileMessageV2 != null) return FutureProofMessageType.NEWSLETTER_ADMIN_PROFILE;
        if (deviceSentMessage != null) return FutureProofMessageType.DEVICE_SENT;
        return FutureProofMessageType.NONE;
    }

    /**
     * Extracts the inner container from a {@link FutureProofMessage},
     * falling back to an empty container if the wrapper is empty.
     */
    private static MessageContainer unboxFutureProof(FutureProofMessage wrapper) {
        return wrapper.message().orElseGet(MessageContainer::empty);
    }

    /**
     * Returns {@code true} if this container holds no payload message.
     *
     * <p>Side-channel metadata ({@link #messageContextInfo()},
     * {@link #senderKeyDistributionMessage()},
     * {@link #fastRatchetKeySenderKeyDistributionMessage()}) alone does
     * not count as content.
     *
     * @return {@code true} if this container is empty
     */
    public boolean isEmpty() {
        return content() == EmptyMessage.INSTANCE;
    }

    /**
     * Returns a human-readable string representation of this container,
     * delegating to the innermost message's {@code toString}.
     *
     * @return a non-null string
     */
    @Override
    public String toString() {
        var message = content();
        if(message == EmptyMessage.INSTANCE) {
            return "[Empty]";
        }

        return message.toString();
    }

    /**
     * Returns the per-message transport metadata, if present.
     *
     * <p>This is a side-channel field that coexists with the message
     * payload. It carries device list information, encryption secrets,
     * bot metadata, and limit-sharing data.
     *
     * @return an {@code Optional} describing the {@link ChatMessageContextInfo}
     */
    public Optional<ChatMessageContextInfo> messageContextInfo() {
        return Optional.ofNullable(messageContextInfo);
    }

    /**
     * Returns the sender key distribution message, if present.
     *
     * <p>This is a side-channel field that coexists with the message
     * payload. It distributes group Signal encryption sender keys to
     * group participants.
     *
     * @return an {@code Optional} describing the {@link SenderKeyDistributionMessage}
     */
    public Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage() {
        return Optional.ofNullable(senderKeyDistributionMessage);
    }

    /**
     * Returns the fast ratchet key sender key distribution message, if present.
     *
     * <p>This is a side-channel field that coexists with the message
     * payload. It distributes fast ratchet sender keys for group
     * encryption.
     *
     * @return an {@code Optional} describing the {@link SenderKeyDistributionMessage}
     */
    public Optional<SenderKeyDistributionMessage> fastRatchetKeySenderKeyDistributionMessage() {
        return Optional.ofNullable(fastRatchetKeySenderKeyDistributionMessage);
    }

    /**
     * Returns a new container with the given per-message transport
     * metadata, preserving the current message and all other sidecar
     * fields.
     *
     * @param value the message context info to attach, or {@code null}
     *              to clear
     * @return a new container with the updated value
     */
    public MessageContainer withMessageContextInfo(ChatMessageContextInfo value) {
        return wrapAs(b -> b.messageContextInfo(value));
    }

    /**
     * Returns a new container with the given sender key distribution
     * message, preserving the current message and all other sidecar
     * fields.
     *
     * @param value the sender key distribution message to attach, or
     *              {@code null} to clear
     * @return a new container with the updated value
     */
    public MessageContainer withSenderKeyDistributionMessage(SenderKeyDistributionMessage value) {
        return wrapAs(b -> b.senderKeyDistributionMessage(value));
    }

    /**
     * Returns a new container with the given fast ratchet key sender key
     * distribution message, preserving the current message and all other
     * sidecar fields.
     *
     * @param value the fast ratchet key sender key distribution message
     *              to attach, or {@code null} to clear
     * @return a new container with the updated value
     */
    public MessageContainer withFastRatchetKeySenderKeyDistributionMessage(SenderKeyDistributionMessage value) {
        return wrapAs(b -> b.fastRatchetKeySenderKeyDistributionMessage(value));
    }

    /**
     * Converts this container into a view-once message.
     * If already view-once (any version), returns {@code this}.
     *
     * <p>Internally uses the newest view-once format.
     *
     * @return a non-null view-once container
     */
    public MessageContainer toViewOnce() {
        if (viewOnceMessage != null || viewOnceMessageV2 != null || viewOnceMessageV2Extension != null) return this;
        return wrapAs(b -> b.viewOnceMessageV2Extension(wrapInner()));
    }

    /**
     * Converts this container into an ephemeral (disappearing) message.
     * If already ephemeral, returns {@code this}.
     *
     * @return a non-null ephemeral container
     */
    public MessageContainer toEphemeral() {
        if (ephemeralMessage != null) return this;
        return wrapAs(b -> b.ephemeralMessage(wrapInner()));
    }

    /**
     * Converts this container into a document-with-caption message.
     * If already document-with-caption, returns {@code this}.
     *
     * @return a non-null document-with-caption container
     */
    public MessageContainer toDocumentWithCaption() {
        if (documentWithCaptionMessage != null) return this;
        return wrapAs(b -> b.documentWithCaptionMessage(wrapInner()));
    }

    /**
     * Converts this container into an edited message.
     * If already an edit, returns {@code this}.
     *
     * @return a non-null edited container
     */
    public MessageContainer toEdited() {
        if (editedMessage != null) return this;
        return wrapAs(b -> b.editedMessage(wrapInner()));
    }

    /**
     * Converts this container into a group-mentioned message.
     * If already group-mentioned, returns {@code this}.
     *
     * @return a non-null group-mentioned container
     */
    public MessageContainer toGroupMentioned() {
        if (groupMentionedMessage != null) return this;
        return wrapAs(b -> b.groupMentionedMessage(wrapInner()));
    }

    /**
     * Converts this container into a bot-invoke message.
     * If already bot-invoke, returns {@code this}.
     *
     * @return a non-null bot-invoke container
     */
    public MessageContainer toBotInvoke() {
        if (botInvokeMessage != null) return this;
        return wrapAs(b -> b.botInvokeMessage(wrapInner()));
    }

    /**
     * Converts this container into a Lottie sticker message.
     * If already a Lottie sticker, returns {@code this}.
     *
     * @return a non-null Lottie sticker container
     */
    public MessageContainer toLottieSticker() {
        if (lottieStickerMessage != null) return this;
        return wrapAs(b -> b.lottieStickerMessage(wrapInner()));
    }

    /**
     * Converts this container into an event cover image message.
     * If already an event cover image, returns {@code this}.
     *
     * @return a non-null event cover image container
     */
    public MessageContainer toEventCoverImage() {
        if (eventCoverImage != null) return this;
        return wrapAs(b -> b.eventCoverImage(wrapInner()));
    }

    /**
     * Converts this container into a status mention message.
     * If already a status mention, returns {@code this}.
     *
     * @return a non-null status mention container
     */
    public MessageContainer toStatusMention() {
        if (statusMentionMessage != null) return this;
        return wrapAs(b -> b.statusMentionMessage(wrapInner()));
    }

    /**
     * Converts this container into a poll creation option image message.
     * If already a poll creation option image, returns {@code this}.
     *
     * @return a non-null poll creation option image container
     */
    public MessageContainer toPollCreationOptionImage() {
        if (pollCreationOptionImageMessage != null) return this;
        return wrapAs(b -> b.pollCreationOptionImageMessage(wrapInner()));
    }

    /**
     * Converts this container into an associated child message.
     * If already an associated child, returns {@code this}.
     *
     * @return a non-null associated child container
     */
    public MessageContainer toAssociatedChild() {
        if (associatedChildMessage != null) return this;
        return wrapAs(b -> b.associatedChildMessage(wrapInner()));
    }

    /**
     * Converts this container into a group status mention message.
     * If already a group status mention, returns {@code this}.
     *
     * @return a non-null group status mention container
     */
    public MessageContainer toGroupStatusMention() {
        if (groupStatusMentionMessage != null) return this;
        return wrapAs(b -> b.groupStatusMentionMessage(wrapInner()));
    }

    /**
     * Converts this container into a status "Add Yours" message.
     * If already a status add-yours, returns {@code this}.
     *
     * @return a non-null status add-yours container
     */
    public MessageContainer toStatusAddYours() {
        if (statusAddYours != null) return this;
        return wrapAs(b -> b.statusAddYours(wrapInner()));
    }

    /**
     * Converts this container into a group status message.
     * If already a group status (any version), returns {@code this}.
     *
     * <p>Internally uses the newest group status format.
     *
     * @return a non-null group status container
     */
    public MessageContainer toGroupStatus() {
        if (groupStatusMessage != null || groupStatusMessageV2 != null) return this;
        return wrapAs(b -> b.groupStatusMessageV2(wrapInner()));
    }

    /**
     * Converts this container into a limit-sharing message.
     * If already a limit-sharing, returns {@code this}.
     *
     * @return a non-null limit-sharing container
     */
    public MessageContainer toLimitSharing() {
        if (limitSharingMessage != null) return this;
        return wrapAs(b -> b.limitSharingMessage(wrapInner()));
    }

    /**
     * Converts this container into a bot task message.
     * If already a bot task, returns {@code this}.
     *
     * @return a non-null bot task container
     */
    public MessageContainer toBotTask() {
        if (botTaskMessage != null) return this;
        return wrapAs(b -> b.botTaskMessage(wrapInner()));
    }

    /**
     * Converts this container into a question message.
     * If already a question, returns {@code this}.
     *
     * @return a non-null question container
     */
    public MessageContainer toQuestion() {
        if (questionMessage != null) return this;
        return wrapAs(b -> b.questionMessage(wrapInner()));
    }

    /**
     * Converts this container into a bot-forwarded message.
     * If already bot-forwarded, returns {@code this}.
     *
     * @return a non-null bot-forwarded container
     */
    public MessageContainer toBotForwarded() {
        if (botForwardedMessage != null) return this;
        return wrapAs(b -> b.botForwardedMessage(wrapInner()));
    }

    /**
     * Converts this container into a question reply message.
     * If already a question reply, returns {@code this}.
     *
     * @return a non-null question reply container
     */
    public MessageContainer toQuestionReply() {
        if (questionReplyMessage != null) return this;
        return wrapAs(b -> b.questionReplyMessage(wrapInner()));
    }

    /**
     * Converts this container into a newsletter admin profile message.
     * If already a newsletter admin profile (any version), returns
     * {@code this}.
     *
     * <p>Internally uses the newest newsletter admin profile format.
     *
     * @return a non-null newsletter admin profile container
     */
    public MessageContainer toNewsletterAdminProfile() {
        if (newsletterAdminProfileMessage != null || newsletterAdminProfileMessageV2 != null) return this;
        return wrapAs(b -> b.newsletterAdminProfileMessageV2(wrapInner()));
    }

    /**
     * Creates a {@link FutureProofMessage} wrapping the content
     * of this container.
     */
    private FutureProofMessage wrapInner() {
        return new FutureProofMessageBuilder()
                .messageContainer(MessageContainer.of(content()))
                .build();
    }

    /**
     * Builds a new container by applying the given wrapper configuration
     * to a fresh builder, then copying all three sidecar fields from this
     * container.
     */
    private MessageContainer wrapAs(Consumer<MessageContainerBuilder> wrapperSetter) {
        var builder = new MessageContainerBuilder();
        wrapperSetter.accept(builder);
        return builder
                .messageContextInfo(messageContextInfo)
                .senderKeyDistributionMessage(senderKeyDistributionMessage)
                .fastRatchetKeySenderKeyDistributionMessage(fastRatchetKeySenderKeyDistributionMessage)
                .build();
    }
}
