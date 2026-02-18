package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.bot.response.AIRichResponseMessage;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoContext;
import com.github.auties00.cobalt.model.message.call.BCallMessage;
import com.github.auties00.cobalt.model.message.call.CallOfferMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallCreationMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallEditMessage;
import com.github.auties00.cobalt.model.message.commerce.*;
import com.github.auties00.cobalt.model.message.contact.ContactMessage;
import com.github.auties00.cobalt.model.message.contact.ContactsArrayMessage;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
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
import com.github.auties00.cobalt.model.message.security.SecretEncryptedMessage;
import com.github.auties00.cobalt.model.message.status.StatusNotificationMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuestionAnswerMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuotedMessage;
import com.github.auties00.cobalt.model.message.status.StatusStickerInteractionMessage;
import com.github.auties00.cobalt.model.message.system.*;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryBundle;
import com.github.auties00.cobalt.model.message.system.history.MessageHistoryNotice;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessage;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message")
public final class MessageContainer {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String conversation;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    SenderKeyDistributionMessage senderKeyDistributionMessage;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ImageMessage imageMessage;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ContactMessage contactMessage;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    LocationMessage locationMessage;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    ExtendedTextMessage extendedTextMessage;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    DocumentMessage documentMessage;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    AudioMessage audioMessage;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    VideoMessage videoMessage;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    CallOfferMessage call;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    ChatProtocolMessage chat;

    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    ProtocolMessage protocolMessage;

    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    ContactsArrayMessage contactsArrayMessage;

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    HighlyStructuredMessage highlyStructuredMessage;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    SendPaymentMessage sendPaymentMessage;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    LiveLocationMessage liveLocationMessage;

    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    RequestPaymentMessage requestPaymentMessage;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    DeclinePaymentRequestMessage declinePaymentRequestMessage;

    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    CancelPaymentRequestMessage cancelPaymentRequestMessage;

    @ProtobufProperty(index = 25, type = ProtobufType.MESSAGE)
    TemplateMessage templateMessage;

    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    StickerMessage stickerMessage;

    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    GroupInviteMessage groupInviteMessage;

    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    TemplateButtonReplyMessage templateButtonReplyMessage;

    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    ProductMessage productMessage;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    DeviceSentMessage deviceSentMessage;

    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    ChatMessageInfoContext messageContextInfo;

    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    ListMessage listMessage;

    @ProtobufProperty(index = 37, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessage;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    OrderMessage orderMessage;

    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    ListResponseMessage listResponseMessage;

    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    FutureProofMessage ephemeralMessage;

    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    InvoiceMessage invoiceMessage;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    ButtonsMessage buttonsMessage;

    @ProtobufProperty(index = 43, type = ProtobufType.MESSAGE)
    ButtonsResponseMessage buttonsResponseMessage;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    PaymentInviteMessage paymentInviteMessage;

    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    InteractiveMessage interactiveMessage;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    ReactionMessage reactionMessage;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    StickerSyncRMRMessage stickerSyncRmrMessage;

    @ProtobufProperty(index = 48, type = ProtobufType.MESSAGE)
    InteractiveResponseMessage interactiveResponseMessage;

    @ProtobufProperty(index = 49, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessage;

    @ProtobufProperty(index = 50, type = ProtobufType.MESSAGE)
    PollUpdateMessage pollUpdateMessage;

    @ProtobufProperty(index = 51, type = ProtobufType.MESSAGE)
    KeepInChatMessage keepInChatMessage;

    @ProtobufProperty(index = 53, type = ProtobufType.MESSAGE)
    FutureProofMessage documentWithCaptionMessage;

    @ProtobufProperty(index = 54, type = ProtobufType.MESSAGE)
    RequestPhoneNumberMessage requestPhoneNumberMessage;

    @ProtobufProperty(index = 55, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessageV2;

    @ProtobufProperty(index = 56, type = ProtobufType.MESSAGE)
    EncReactionMessage encReactionMessage;

    @ProtobufProperty(index = 58, type = ProtobufType.MESSAGE)
    FutureProofMessage editedMessage;

    @ProtobufProperty(index = 59, type = ProtobufType.MESSAGE)
    FutureProofMessage viewOnceMessageV2Extension;

    @ProtobufProperty(index = 60, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV2;

    @ProtobufProperty(index = 61, type = ProtobufType.MESSAGE)
    ScheduledCallCreationMessage scheduledCallCreationMessage;

    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    FutureProofMessage groupMentionedMessage;

    @ProtobufProperty(index = 63, type = ProtobufType.MESSAGE)
    PinInChatMessage pinInChatMessage;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV3;

    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    ScheduledCallEditMessage scheduledCallEditMessage;

    @ProtobufProperty(index = 66, type = ProtobufType.MESSAGE)
    VideoMessage ptvMessage;

    @ProtobufProperty(index = 67, type = ProtobufType.MESSAGE)
    FutureProofMessage botInvokeMessage;

    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    CallLogMessage callLogMesssage;

    @ProtobufProperty(index = 70, type = ProtobufType.MESSAGE)
    MessageHistoryBundle messageHistoryBundle;

    @ProtobufProperty(index = 71, type = ProtobufType.MESSAGE)
    EncCommentMessage encCommentMessage;

    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    BCallMessage bcallMessage;

    @ProtobufProperty(index = 74, type = ProtobufType.MESSAGE)
    FutureProofMessage lottieStickerMessage;

    @ProtobufProperty(index = 75, type = ProtobufType.MESSAGE)
    EventMessage eventMessage;

    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    EncEventResponseMessage encEventResponseMessage;

    @ProtobufProperty(index = 77, type = ProtobufType.MESSAGE)
    CommentMessage commentMessage;

    @ProtobufProperty(index = 78, type = ProtobufType.MESSAGE)
    NewsletterAdminInviteMessage newsletterAdminInviteMessage;

    @ProtobufProperty(index = 80, type = ProtobufType.MESSAGE)
    PlaceholderMessage placeholderMessage;

    @ProtobufProperty(index = 82, type = ProtobufType.MESSAGE)
    SecretEncryptedMessage secretEncryptedMessage;

    @ProtobufProperty(index = 83, type = ProtobufType.MESSAGE)
    AlbumMessage albumMessage;

    @ProtobufProperty(index = 85, type = ProtobufType.MESSAGE)
    FutureProofMessage eventCoverImage;

    @ProtobufProperty(index = 86, type = ProtobufType.MESSAGE)
    StickerPackMessage stickerPackMessage;

    @ProtobufProperty(index = 87, type = ProtobufType.MESSAGE)
    FutureProofMessage statusMentionMessage;

    @ProtobufProperty(index = 88, type = ProtobufType.MESSAGE)
    PollResultSnapshotMessage pollResultSnapshotMessage;

    @ProtobufProperty(index = 90, type = ProtobufType.MESSAGE)
    FutureProofMessage pollCreationOptionImageMessage;

    @ProtobufProperty(index = 91, type = ProtobufType.MESSAGE)
    FutureProofMessage associatedChildMessage;

    @ProtobufProperty(index = 92, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMentionMessage;

    @ProtobufProperty(index = 93, type = ProtobufType.MESSAGE)
    FutureProofMessage pollCreationMessageV4;

    @ProtobufProperty(index = 95, type = ProtobufType.MESSAGE)
    FutureProofMessage statusAddYours;

    @ProtobufProperty(index = 96, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMessage;

    @ProtobufProperty(index = 97, type = ProtobufType.MESSAGE)
    AIRichResponseMessage richResponseMessage;

    @ProtobufProperty(index = 98, type = ProtobufType.MESSAGE)
    StatusNotificationMessage statusNotificationMessage;

    @ProtobufProperty(index = 99, type = ProtobufType.MESSAGE)
    FutureProofMessage limitSharingMessage;

    @ProtobufProperty(index = 100, type = ProtobufType.MESSAGE)
    FutureProofMessage botTaskMessage;

    @ProtobufProperty(index = 101, type = ProtobufType.MESSAGE)
    FutureProofMessage questionMessage;

    @ProtobufProperty(index = 102, type = ProtobufType.MESSAGE)
    MessageHistoryNotice messageHistoryNotice;

    @ProtobufProperty(index = 103, type = ProtobufType.MESSAGE)
    FutureProofMessage groupStatusMessageV2;

    @ProtobufProperty(index = 104, type = ProtobufType.MESSAGE)
    FutureProofMessage botForwardedMessage;

    @ProtobufProperty(index = 105, type = ProtobufType.MESSAGE)
    StatusQuestionAnswerMessage statusQuestionAnswerMessage;

    @ProtobufProperty(index = 106, type = ProtobufType.MESSAGE)
    FutureProofMessage questionReplyMessage;

    @ProtobufProperty(index = 107, type = ProtobufType.MESSAGE)
    QuestionResponseMessage questionResponseMessage;

    @ProtobufProperty(index = 109, type = ProtobufType.MESSAGE)
    StatusQuotedMessage statusQuotedMessage;

    @ProtobufProperty(index = 110, type = ProtobufType.MESSAGE)
    StatusStickerInteractionMessage statusStickerInteractionMessage;

    @ProtobufProperty(index = 111, type = ProtobufType.MESSAGE)
    PollCreationMessage pollCreationMessageV5;

    @ProtobufProperty(index = 113, type = ProtobufType.MESSAGE)
    NewsletterFollowerInviteMessage newsletterFollowerInviteMessageV2;

    @ProtobufProperty(index = 115, type = ProtobufType.MESSAGE)
    PollResultSnapshotMessage pollResultSnapshotMessageV3;

    @ProtobufProperty(index = 116, type = ProtobufType.MESSAGE)
    FutureProofMessage newsletterAdminProfileMessage;

    @ProtobufProperty(index = 117, type = ProtobufType.MESSAGE)
    FutureProofMessage newsletterAdminProfileMessageV2;


    MessageContainer(String conversation, SenderKeyDistributionMessage senderKeyDistributionMessage, ImageMessage imageMessage, ContactMessage contactMessage, LocationMessage locationMessage, ExtendedTextMessage extendedTextMessage, DocumentMessage documentMessage, AudioMessage audioMessage, VideoMessage videoMessage, CallOfferMessage call, ChatProtocolMessage chat, ProtocolMessage protocolMessage, ContactsArrayMessage contactsArrayMessage, HighlyStructuredMessage highlyStructuredMessage, SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage, SendPaymentMessage sendPaymentMessage, LiveLocationMessage liveLocationMessage, RequestPaymentMessage requestPaymentMessage, DeclinePaymentRequestMessage declinePaymentRequestMessage, CancelPaymentRequestMessage cancelPaymentRequestMessage, TemplateMessage templateMessage, StickerMessage stickerMessage, GroupInviteMessage groupInviteMessage, TemplateButtonReplyMessage templateButtonReplyMessage, ProductMessage productMessage, DeviceSentMessage deviceSentMessage, ChatMessageInfoContext messageContextInfo, ListMessage listMessage, FutureProofMessage viewOnceMessage, OrderMessage orderMessage, ListResponseMessage listResponseMessage, FutureProofMessage ephemeralMessage, InvoiceMessage invoiceMessage, ButtonsMessage buttonsMessage, ButtonsResponseMessage buttonsResponseMessage, PaymentInviteMessage paymentInviteMessage, InteractiveMessage interactiveMessage, ReactionMessage reactionMessage, StickerSyncRMRMessage stickerSyncRmrMessage, InteractiveResponseMessage interactiveResponseMessage, PollCreationMessage pollCreationMessage, PollUpdateMessage pollUpdateMessage, KeepInChatMessage keepInChatMessage, FutureProofMessage documentWithCaptionMessage, RequestPhoneNumberMessage requestPhoneNumberMessage, FutureProofMessage viewOnceMessageV2, EncReactionMessage encReactionMessage, FutureProofMessage editedMessage, FutureProofMessage viewOnceMessageV2Extension, PollCreationMessage pollCreationMessageV2, ScheduledCallCreationMessage scheduledCallCreationMessage, FutureProofMessage groupMentionedMessage, PinInChatMessage pinInChatMessage, PollCreationMessage pollCreationMessageV3, ScheduledCallEditMessage scheduledCallEditMessage, VideoMessage ptvMessage, FutureProofMessage botInvokeMessage, CallLogMessage callLogMesssage, MessageHistoryBundle messageHistoryBundle, EncCommentMessage encCommentMessage, BCallMessage bcallMessage, FutureProofMessage lottieStickerMessage, EventMessage eventMessage, EncEventResponseMessage encEventResponseMessage, CommentMessage commentMessage, NewsletterAdminInviteMessage newsletterAdminInviteMessage, PlaceholderMessage placeholderMessage, SecretEncryptedMessage secretEncryptedMessage, AlbumMessage albumMessage, FutureProofMessage eventCoverImage, StickerPackMessage stickerPackMessage, FutureProofMessage statusMentionMessage, PollResultSnapshotMessage pollResultSnapshotMessage, FutureProofMessage pollCreationOptionImageMessage, FutureProofMessage associatedChildMessage, FutureProofMessage groupStatusMentionMessage, FutureProofMessage pollCreationMessageV4, FutureProofMessage statusAddYours, FutureProofMessage groupStatusMessage, AIRichResponseMessage richResponseMessage, StatusNotificationMessage statusNotificationMessage, FutureProofMessage limitSharingMessage, FutureProofMessage botTaskMessage, FutureProofMessage questionMessage, MessageHistoryNotice messageHistoryNotice, FutureProofMessage groupStatusMessageV2, FutureProofMessage botForwardedMessage, StatusQuestionAnswerMessage statusQuestionAnswerMessage, FutureProofMessage questionReplyMessage, QuestionResponseMessage questionResponseMessage, StatusQuotedMessage statusQuotedMessage, StatusStickerInteractionMessage statusStickerInteractionMessage, PollCreationMessage pollCreationMessageV5, NewsletterFollowerInviteMessage newsletterFollowerInviteMessageV2, PollResultSnapshotMessage pollResultSnapshotMessageV3, FutureProofMessage newsletterAdminProfileMessage, FutureProofMessage newsletterAdminProfileMessageV2) {
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

    public Optional<String> conversation() {
        return Optional.ofNullable(conversation);
    }

    public Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage() {
        return Optional.ofNullable(senderKeyDistributionMessage);
    }

    public Optional<ImageMessage> imageMessage() {
        return Optional.ofNullable(imageMessage);
    }

    public Optional<ContactMessage> contactMessage() {
        return Optional.ofNullable(contactMessage);
    }

    public Optional<LocationMessage> locationMessage() {
        return Optional.ofNullable(locationMessage);
    }

    public Optional<ExtendedTextMessage> extendedTextMessage() {
        return Optional.ofNullable(extendedTextMessage);
    }

    public Optional<DocumentMessage> documentMessage() {
        return Optional.ofNullable(documentMessage);
    }

    public Optional<AudioMessage> audioMessage() {
        return Optional.ofNullable(audioMessage);
    }

    public Optional<VideoMessage> videoMessage() {
        return Optional.ofNullable(videoMessage);
    }

    public Optional<CallOfferMessage> call() {
        return Optional.ofNullable(call);
    }

    public Optional<ChatProtocolMessage> chat() {
        return Optional.ofNullable(chat);
    }

    public Optional<ProtocolMessage> protocolMessage() {
        return Optional.ofNullable(protocolMessage);
    }

    public Optional<ContactsArrayMessage> contactsArrayMessage() {
        return Optional.ofNullable(contactsArrayMessage);
    }

    public Optional<HighlyStructuredMessage> highlyStructuredMessage() {
        return Optional.ofNullable(highlyStructuredMessage);
    }

    public Optional<SenderKeyDistributionMessage> fastRatchetKeySenderKeyDistributionMessage() {
        return Optional.ofNullable(fastRatchetKeySenderKeyDistributionMessage);
    }

    public Optional<SendPaymentMessage> sendPaymentMessage() {
        return Optional.ofNullable(sendPaymentMessage);
    }

    public Optional<LiveLocationMessage> liveLocationMessage() {
        return Optional.ofNullable(liveLocationMessage);
    }

    public Optional<RequestPaymentMessage> requestPaymentMessage() {
        return Optional.ofNullable(requestPaymentMessage);
    }

    public Optional<DeclinePaymentRequestMessage> declinePaymentRequestMessage() {
        return Optional.ofNullable(declinePaymentRequestMessage);
    }

    public Optional<CancelPaymentRequestMessage> cancelPaymentRequestMessage() {
        return Optional.ofNullable(cancelPaymentRequestMessage);
    }

    public Optional<TemplateMessage> templateMessage() {
        return Optional.ofNullable(templateMessage);
    }

    public Optional<StickerMessage> stickerMessage() {
        return Optional.ofNullable(stickerMessage);
    }

    public Optional<GroupInviteMessage> groupInviteMessage() {
        return Optional.ofNullable(groupInviteMessage);
    }

    public Optional<TemplateButtonReplyMessage> templateButtonReplyMessage() {
        return Optional.ofNullable(templateButtonReplyMessage);
    }

    public Optional<ProductMessage> productMessage() {
        return Optional.ofNullable(productMessage);
    }

    public Optional<DeviceSentMessage> deviceSentMessage() {
        return Optional.ofNullable(deviceSentMessage);
    }

    public Optional<ChatMessageInfoContext> messageContextInfo() {
        return Optional.ofNullable(messageContextInfo);
    }

    public Optional<ListMessage> listMessage() {
        return Optional.ofNullable(listMessage);
    }

    public Optional<FutureProofMessage> viewOnceMessage() {
        return Optional.ofNullable(viewOnceMessage);
    }

    public Optional<OrderMessage> orderMessage() {
        return Optional.ofNullable(orderMessage);
    }

    public Optional<ListResponseMessage> listResponseMessage() {
        return Optional.ofNullable(listResponseMessage);
    }

    public Optional<FutureProofMessage> ephemeralMessage() {
        return Optional.ofNullable(ephemeralMessage);
    }

    public Optional<InvoiceMessage> invoiceMessage() {
        return Optional.ofNullable(invoiceMessage);
    }

    public Optional<ButtonsMessage> buttonsMessage() {
        return Optional.ofNullable(buttonsMessage);
    }

    public Optional<ButtonsResponseMessage> buttonsResponseMessage() {
        return Optional.ofNullable(buttonsResponseMessage);
    }

    public Optional<PaymentInviteMessage> paymentInviteMessage() {
        return Optional.ofNullable(paymentInviteMessage);
    }

    public Optional<InteractiveMessage> interactiveMessage() {
        return Optional.ofNullable(interactiveMessage);
    }

    public Optional<ReactionMessage> reactionMessage() {
        return Optional.ofNullable(reactionMessage);
    }

    public Optional<StickerSyncRMRMessage> stickerSyncRmrMessage() {
        return Optional.ofNullable(stickerSyncRmrMessage);
    }

    public Optional<InteractiveResponseMessage> interactiveResponseMessage() {
        return Optional.ofNullable(interactiveResponseMessage);
    }

    public Optional<PollCreationMessage> pollCreationMessage() {
        return Optional.ofNullable(pollCreationMessage);
    }

    public Optional<PollUpdateMessage> pollUpdateMessage() {
        return Optional.ofNullable(pollUpdateMessage);
    }

    public Optional<KeepInChatMessage> keepInChatMessage() {
        return Optional.ofNullable(keepInChatMessage);
    }

    public Optional<FutureProofMessage> documentWithCaptionMessage() {
        return Optional.ofNullable(documentWithCaptionMessage);
    }

    public Optional<RequestPhoneNumberMessage> requestPhoneNumberMessage() {
        return Optional.ofNullable(requestPhoneNumberMessage);
    }

    public Optional<FutureProofMessage> viewOnceMessageV2() {
        return Optional.ofNullable(viewOnceMessageV2);
    }

    public Optional<EncReactionMessage> encReactionMessage() {
        return Optional.ofNullable(encReactionMessage);
    }

    public Optional<FutureProofMessage> editedMessage() {
        return Optional.ofNullable(editedMessage);
    }

    public Optional<FutureProofMessage> viewOnceMessageV2Extension() {
        return Optional.ofNullable(viewOnceMessageV2Extension);
    }

    public Optional<PollCreationMessage> pollCreationMessageV2() {
        return Optional.ofNullable(pollCreationMessageV2);
    }

    public Optional<ScheduledCallCreationMessage> scheduledCallCreationMessage() {
        return Optional.ofNullable(scheduledCallCreationMessage);
    }

    public Optional<FutureProofMessage> groupMentionedMessage() {
        return Optional.ofNullable(groupMentionedMessage);
    }

    public Optional<PinInChatMessage> pinInChatMessage() {
        return Optional.ofNullable(pinInChatMessage);
    }

    public Optional<PollCreationMessage> pollCreationMessageV3() {
        return Optional.ofNullable(pollCreationMessageV3);
    }

    public Optional<ScheduledCallEditMessage> scheduledCallEditMessage() {
        return Optional.ofNullable(scheduledCallEditMessage);
    }

    public Optional<VideoMessage> ptvMessage() {
        return Optional.ofNullable(ptvMessage);
    }

    public Optional<FutureProofMessage> botInvokeMessage() {
        return Optional.ofNullable(botInvokeMessage);
    }

    public Optional<CallLogMessage> callLogMesssage() {
        return Optional.ofNullable(callLogMesssage);
    }

    public Optional<MessageHistoryBundle> messageHistoryBundle() {
        return Optional.ofNullable(messageHistoryBundle);
    }

    public Optional<EncCommentMessage> encCommentMessage() {
        return Optional.ofNullable(encCommentMessage);
    }

    public Optional<BCallMessage> bcallMessage() {
        return Optional.ofNullable(bcallMessage);
    }

    public Optional<FutureProofMessage> lottieStickerMessage() {
        return Optional.ofNullable(lottieStickerMessage);
    }

    public Optional<EventMessage> eventMessage() {
        return Optional.ofNullable(eventMessage);
    }

    public Optional<EncEventResponseMessage> encEventResponseMessage() {
        return Optional.ofNullable(encEventResponseMessage);
    }

    public Optional<CommentMessage> commentMessage() {
        return Optional.ofNullable(commentMessage);
    }

    public Optional<NewsletterAdminInviteMessage> newsletterAdminInviteMessage() {
        return Optional.ofNullable(newsletterAdminInviteMessage);
    }

    public Optional<PlaceholderMessage> placeholderMessage() {
        return Optional.ofNullable(placeholderMessage);
    }

    public Optional<SecretEncryptedMessage> secretEncryptedMessage() {
        return Optional.ofNullable(secretEncryptedMessage);
    }

    public Optional<AlbumMessage> albumMessage() {
        return Optional.ofNullable(albumMessage);
    }

    public Optional<FutureProofMessage> eventCoverImage() {
        return Optional.ofNullable(eventCoverImage);
    }

    public Optional<StickerPackMessage> stickerPackMessage() {
        return Optional.ofNullable(stickerPackMessage);
    }

    public Optional<FutureProofMessage> statusMentionMessage() {
        return Optional.ofNullable(statusMentionMessage);
    }

    public Optional<PollResultSnapshotMessage> pollResultSnapshotMessage() {
        return Optional.ofNullable(pollResultSnapshotMessage);
    }

    public Optional<FutureProofMessage> pollCreationOptionImageMessage() {
        return Optional.ofNullable(pollCreationOptionImageMessage);
    }

    public Optional<FutureProofMessage> associatedChildMessage() {
        return Optional.ofNullable(associatedChildMessage);
    }

    public Optional<FutureProofMessage> groupStatusMentionMessage() {
        return Optional.ofNullable(groupStatusMentionMessage);
    }

    public Optional<FutureProofMessage> pollCreationMessageV4() {
        return Optional.ofNullable(pollCreationMessageV4);
    }

    public Optional<FutureProofMessage> statusAddYours() {
        return Optional.ofNullable(statusAddYours);
    }

    public Optional<FutureProofMessage> groupStatusMessage() {
        return Optional.ofNullable(groupStatusMessage);
    }

    public Optional<AIRichResponseMessage> richResponseMessage() {
        return Optional.ofNullable(richResponseMessage);
    }

    public Optional<StatusNotificationMessage> statusNotificationMessage() {
        return Optional.ofNullable(statusNotificationMessage);
    }

    public Optional<FutureProofMessage> limitSharingMessage() {
        return Optional.ofNullable(limitSharingMessage);
    }

    public Optional<FutureProofMessage> botTaskMessage() {
        return Optional.ofNullable(botTaskMessage);
    }

    public Optional<FutureProofMessage> questionMessage() {
        return Optional.ofNullable(questionMessage);
    }

    public Optional<MessageHistoryNotice> messageHistoryNotice() {
        return Optional.ofNullable(messageHistoryNotice);
    }

    public Optional<FutureProofMessage> groupStatusMessageV2() {
        return Optional.ofNullable(groupStatusMessageV2);
    }

    public Optional<FutureProofMessage> botForwardedMessage() {
        return Optional.ofNullable(botForwardedMessage);
    }

    public Optional<StatusQuestionAnswerMessage> statusQuestionAnswerMessage() {
        return Optional.ofNullable(statusQuestionAnswerMessage);
    }

    public Optional<FutureProofMessage> questionReplyMessage() {
        return Optional.ofNullable(questionReplyMessage);
    }

    public Optional<QuestionResponseMessage> questionResponseMessage() {
        return Optional.ofNullable(questionResponseMessage);
    }

    public Optional<StatusQuotedMessage> statusQuotedMessage() {
        return Optional.ofNullable(statusQuotedMessage);
    }

    public Optional<StatusStickerInteractionMessage> statusStickerInteractionMessage() {
        return Optional.ofNullable(statusStickerInteractionMessage);
    }

    public Optional<PollCreationMessage> pollCreationMessageV5() {
        return Optional.ofNullable(pollCreationMessageV5);
    }

    public Optional<NewsletterFollowerInviteMessage> newsletterFollowerInviteMessageV2() {
        return Optional.ofNullable(newsletterFollowerInviteMessageV2);
    }

    public Optional<PollResultSnapshotMessage> pollResultSnapshotMessageV3() {
        return Optional.ofNullable(pollResultSnapshotMessageV3);
    }

    public Optional<FutureProofMessage> newsletterAdminProfileMessage() {
        return Optional.ofNullable(newsletterAdminProfileMessage);
    }

    public Optional<FutureProofMessage> newsletterAdminProfileMessageV2() {
        return Optional.ofNullable(newsletterAdminProfileMessageV2);
    }

    public MessageContainer setConversation(String conversation) {
        this.conversation = conversation;
        return this;
    }

    public MessageContainer setSenderKeyDistributionMessage(SenderKeyDistributionMessage senderKeyDistributionMessage) {
        this.senderKeyDistributionMessage = senderKeyDistributionMessage;
        return this;
    }

    public MessageContainer setImageMessage(ImageMessage imageMessage) {
        this.imageMessage = imageMessage;
        return this;
    }

    public MessageContainer setContactMessage(ContactMessage contactMessage) {
        this.contactMessage = contactMessage;
        return this;
    }

    public MessageContainer setLocationMessage(LocationMessage locationMessage) {
        this.locationMessage = locationMessage;
        return this;
    }

    public MessageContainer setExtendedTextMessage(ExtendedTextMessage extendedTextMessage) {
        this.extendedTextMessage = extendedTextMessage;
        return this;
    }

    public MessageContainer setDocumentMessage(DocumentMessage documentMessage) {
        this.documentMessage = documentMessage;
        return this;
    }

    public MessageContainer setAudioMessage(AudioMessage audioMessage) {
        this.audioMessage = audioMessage;
        return this;
    }

    public MessageContainer setVideoMessage(VideoMessage videoMessage) {
        this.videoMessage = videoMessage;
        return this;
    }

    public MessageContainer setCall(CallOfferMessage call) {
        this.call = call;
        return this;
    }

    public MessageContainer setChat(ChatProtocolMessage chat) {
        this.chat = chat;
        return this;
    }

    public MessageContainer setProtocolMessage(ProtocolMessage protocolMessage) {
        this.protocolMessage = protocolMessage;
        return this;
    }

    public MessageContainer setContactsArrayMessage(ContactsArrayMessage contactsArrayMessage) {
        this.contactsArrayMessage = contactsArrayMessage;
        return this;
    }

    public MessageContainer setHighlyStructuredMessage(HighlyStructuredMessage highlyStructuredMessage) {
        this.highlyStructuredMessage = highlyStructuredMessage;
        return this;
    }

    public MessageContainer setFastRatchetKeySenderKeyDistributionMessage(SenderKeyDistributionMessage fastRatchetKeySenderKeyDistributionMessage) {
        this.fastRatchetKeySenderKeyDistributionMessage = fastRatchetKeySenderKeyDistributionMessage;
        return this;
    }

    public MessageContainer setSendPaymentMessage(SendPaymentMessage sendPaymentMessage) {
        this.sendPaymentMessage = sendPaymentMessage;
        return this;
    }

    public MessageContainer setLiveLocationMessage(LiveLocationMessage liveLocationMessage) {
        this.liveLocationMessage = liveLocationMessage;
        return this;
    }

    public MessageContainer setRequestPaymentMessage(RequestPaymentMessage requestPaymentMessage) {
        this.requestPaymentMessage = requestPaymentMessage;
        return this;
    }

    public MessageContainer setDeclinePaymentRequestMessage(DeclinePaymentRequestMessage declinePaymentRequestMessage) {
        this.declinePaymentRequestMessage = declinePaymentRequestMessage;
        return this;
    }

    public MessageContainer setCancelPaymentRequestMessage(CancelPaymentRequestMessage cancelPaymentRequestMessage) {
        this.cancelPaymentRequestMessage = cancelPaymentRequestMessage;
        return this;
    }

    public MessageContainer setTemplateMessage(TemplateMessage templateMessage) {
        this.templateMessage = templateMessage;
        return this;
    }

    public MessageContainer setStickerMessage(StickerMessage stickerMessage) {
        this.stickerMessage = stickerMessage;
        return this;
    }

    public MessageContainer setGroupInviteMessage(GroupInviteMessage groupInviteMessage) {
        this.groupInviteMessage = groupInviteMessage;
        return this;
    }

    public MessageContainer setTemplateButtonReplyMessage(TemplateButtonReplyMessage templateButtonReplyMessage) {
        this.templateButtonReplyMessage = templateButtonReplyMessage;
        return this;
    }

    public MessageContainer setProductMessage(ProductMessage productMessage) {
        this.productMessage = productMessage;
        return this;
    }

    public MessageContainer setDeviceSentMessage(DeviceSentMessage deviceSentMessage) {
        this.deviceSentMessage = deviceSentMessage;
        return this;
    }

    public MessageContainer setMessageContextInfo(ChatMessageInfoContext messageContextInfo) {
        this.messageContextInfo = messageContextInfo;
        return this;
    }

    public MessageContainer setListMessage(ListMessage listMessage) {
        this.listMessage = listMessage;
        return this;
    }

    public MessageContainer setViewOnceMessage(FutureProofMessage viewOnceMessage) {
        this.viewOnceMessage = viewOnceMessage;
        return this;
    }

    public MessageContainer setOrderMessage(OrderMessage orderMessage) {
        this.orderMessage = orderMessage;
        return this;
    }

    public MessageContainer setListResponseMessage(ListResponseMessage listResponseMessage) {
        this.listResponseMessage = listResponseMessage;
        return this;
    }

    public MessageContainer setEphemeralMessage(FutureProofMessage ephemeralMessage) {
        this.ephemeralMessage = ephemeralMessage;
        return this;
    }

    public MessageContainer setInvoiceMessage(InvoiceMessage invoiceMessage) {
        this.invoiceMessage = invoiceMessage;
        return this;
    }

    public MessageContainer setButtonsMessage(ButtonsMessage buttonsMessage) {
        this.buttonsMessage = buttonsMessage;
        return this;
    }

    public MessageContainer setButtonsResponseMessage(ButtonsResponseMessage buttonsResponseMessage) {
        this.buttonsResponseMessage = buttonsResponseMessage;
        return this;
    }

    public MessageContainer setPaymentInviteMessage(PaymentInviteMessage paymentInviteMessage) {
        this.paymentInviteMessage = paymentInviteMessage;
        return this;
    }

    public MessageContainer setInteractiveMessage(InteractiveMessage interactiveMessage) {
        this.interactiveMessage = interactiveMessage;
        return this;
    }

    public MessageContainer setReactionMessage(ReactionMessage reactionMessage) {
        this.reactionMessage = reactionMessage;
        return this;
    }

    public MessageContainer setStickerSyncRmrMessage(StickerSyncRMRMessage stickerSyncRmrMessage) {
        this.stickerSyncRmrMessage = stickerSyncRmrMessage;
        return this;
    }

    public MessageContainer setInteractiveResponseMessage(InteractiveResponseMessage interactiveResponseMessage) {
        this.interactiveResponseMessage = interactiveResponseMessage;
        return this;
    }

    public MessageContainer setPollCreationMessage(PollCreationMessage pollCreationMessage) {
        this.pollCreationMessage = pollCreationMessage;
        return this;
    }

    public MessageContainer setPollUpdateMessage(PollUpdateMessage pollUpdateMessage) {
        this.pollUpdateMessage = pollUpdateMessage;
        return this;
    }

    public MessageContainer setKeepInChatMessage(KeepInChatMessage keepInChatMessage) {
        this.keepInChatMessage = keepInChatMessage;
        return this;
    }

    public MessageContainer setDocumentWithCaptionMessage(FutureProofMessage documentWithCaptionMessage) {
        this.documentWithCaptionMessage = documentWithCaptionMessage;
        return this;
    }

    public MessageContainer setRequestPhoneNumberMessage(RequestPhoneNumberMessage requestPhoneNumberMessage) {
        this.requestPhoneNumberMessage = requestPhoneNumberMessage;
        return this;
    }

    public MessageContainer setViewOnceMessageV2(FutureProofMessage viewOnceMessageV2) {
        this.viewOnceMessageV2 = viewOnceMessageV2;
        return this;
    }

    public MessageContainer setEncReactionMessage(EncReactionMessage encReactionMessage) {
        this.encReactionMessage = encReactionMessage;
        return this;
    }

    public MessageContainer setEditedMessage(FutureProofMessage editedMessage) {
        this.editedMessage = editedMessage;
        return this;
    }

    public MessageContainer setViewOnceMessageV2Extension(FutureProofMessage viewOnceMessageV2Extension) {
        this.viewOnceMessageV2Extension = viewOnceMessageV2Extension;
        return this;
    }

    public MessageContainer setPollCreationMessageV2(PollCreationMessage pollCreationMessageV2) {
        this.pollCreationMessageV2 = pollCreationMessageV2;
        return this;
    }

    public MessageContainer setScheduledCallCreationMessage(ScheduledCallCreationMessage scheduledCallCreationMessage) {
        this.scheduledCallCreationMessage = scheduledCallCreationMessage;
        return this;
    }

    public MessageContainer setGroupMentionedMessage(FutureProofMessage groupMentionedMessage) {
        this.groupMentionedMessage = groupMentionedMessage;
        return this;
    }

    public MessageContainer setPinInChatMessage(PinInChatMessage pinInChatMessage) {
        this.pinInChatMessage = pinInChatMessage;
        return this;
    }

    public MessageContainer setPollCreationMessageV3(PollCreationMessage pollCreationMessageV3) {
        this.pollCreationMessageV3 = pollCreationMessageV3;
        return this;
    }

    public MessageContainer setScheduledCallEditMessage(ScheduledCallEditMessage scheduledCallEditMessage) {
        this.scheduledCallEditMessage = scheduledCallEditMessage;
        return this;
    }

    public MessageContainer setPtvMessage(VideoMessage ptvMessage) {
        this.ptvMessage = ptvMessage;
        return this;
    }

    public MessageContainer setBotInvokeMessage(FutureProofMessage botInvokeMessage) {
        this.botInvokeMessage = botInvokeMessage;
        return this;
    }

    public MessageContainer setCallLogMesssage(CallLogMessage callLogMesssage) {
        this.callLogMesssage = callLogMesssage;
        return this;
    }

    public MessageContainer setMessageHistoryBundle(MessageHistoryBundle messageHistoryBundle) {
        this.messageHistoryBundle = messageHistoryBundle;
        return this;
    }

    public MessageContainer setEncCommentMessage(EncCommentMessage encCommentMessage) {
        this.encCommentMessage = encCommentMessage;
        return this;
    }

    public MessageContainer setBcallMessage(BCallMessage bcallMessage) {
        this.bcallMessage = bcallMessage;
        return this;
    }

    public MessageContainer setLottieStickerMessage(FutureProofMessage lottieStickerMessage) {
        this.lottieStickerMessage = lottieStickerMessage;
        return this;
    }

    public MessageContainer setEventMessage(EventMessage eventMessage) {
        this.eventMessage = eventMessage;
        return this;
    }

    public MessageContainer setEncEventResponseMessage(EncEventResponseMessage encEventResponseMessage) {
        this.encEventResponseMessage = encEventResponseMessage;
        return this;
    }

    public MessageContainer setCommentMessage(CommentMessage commentMessage) {
        this.commentMessage = commentMessage;
        return this;
    }

    public MessageContainer setNewsletterAdminInviteMessage(NewsletterAdminInviteMessage newsletterAdminInviteMessage) {
        this.newsletterAdminInviteMessage = newsletterAdminInviteMessage;
        return this;
    }

    public MessageContainer setPlaceholderMessage(PlaceholderMessage placeholderMessage) {
        this.placeholderMessage = placeholderMessage;
        return this;
    }

    public MessageContainer setSecretEncryptedMessage(SecretEncryptedMessage secretEncryptedMessage) {
        this.secretEncryptedMessage = secretEncryptedMessage;
        return this;
    }

    public MessageContainer setAlbumMessage(AlbumMessage albumMessage) {
        this.albumMessage = albumMessage;
        return this;
    }

    public MessageContainer setEventCoverImage(FutureProofMessage eventCoverImage) {
        this.eventCoverImage = eventCoverImage;
        return this;
    }

    public MessageContainer setStickerPackMessage(StickerPackMessage stickerPackMessage) {
        this.stickerPackMessage = stickerPackMessage;
        return this;
    }

    public MessageContainer setStatusMentionMessage(FutureProofMessage statusMentionMessage) {
        this.statusMentionMessage = statusMentionMessage;
        return this;
    }

    public MessageContainer setPollResultSnapshotMessage(PollResultSnapshotMessage pollResultSnapshotMessage) {
        this.pollResultSnapshotMessage = pollResultSnapshotMessage;
        return this;
    }

    public MessageContainer setPollCreationOptionImageMessage(FutureProofMessage pollCreationOptionImageMessage) {
        this.pollCreationOptionImageMessage = pollCreationOptionImageMessage;
        return this;
    }

    public MessageContainer setAssociatedChildMessage(FutureProofMessage associatedChildMessage) {
        this.associatedChildMessage = associatedChildMessage;
        return this;
    }

    public MessageContainer setGroupStatusMentionMessage(FutureProofMessage groupStatusMentionMessage) {
        this.groupStatusMentionMessage = groupStatusMentionMessage;
        return this;
    }

    public MessageContainer setPollCreationMessageV4(FutureProofMessage pollCreationMessageV4) {
        this.pollCreationMessageV4 = pollCreationMessageV4;
        return this;
    }

    public MessageContainer setStatusAddYours(FutureProofMessage statusAddYours) {
        this.statusAddYours = statusAddYours;
        return this;
    }

    public MessageContainer setGroupStatusMessage(FutureProofMessage groupStatusMessage) {
        this.groupStatusMessage = groupStatusMessage;
        return this;
    }

    public MessageContainer setRichResponseMessage(AIRichResponseMessage richResponseMessage) {
        this.richResponseMessage = richResponseMessage;
        return this;
    }

    public MessageContainer setStatusNotificationMessage(StatusNotificationMessage statusNotificationMessage) {
        this.statusNotificationMessage = statusNotificationMessage;
        return this;
    }

    public MessageContainer setLimitSharingMessage(FutureProofMessage limitSharingMessage) {
        this.limitSharingMessage = limitSharingMessage;
        return this;
    }

    public MessageContainer setBotTaskMessage(FutureProofMessage botTaskMessage) {
        this.botTaskMessage = botTaskMessage;
        return this;
    }

    public MessageContainer setQuestionMessage(FutureProofMessage questionMessage) {
        this.questionMessage = questionMessage;
        return this;
    }

    public MessageContainer setMessageHistoryNotice(MessageHistoryNotice messageHistoryNotice) {
        this.messageHistoryNotice = messageHistoryNotice;
        return this;
    }

    public MessageContainer setGroupStatusMessageV2(FutureProofMessage groupStatusMessageV2) {
        this.groupStatusMessageV2 = groupStatusMessageV2;
        return this;
    }

    public MessageContainer setBotForwardedMessage(FutureProofMessage botForwardedMessage) {
        this.botForwardedMessage = botForwardedMessage;
        return this;
    }

    public MessageContainer setStatusQuestionAnswerMessage(StatusQuestionAnswerMessage statusQuestionAnswerMessage) {
        this.statusQuestionAnswerMessage = statusQuestionAnswerMessage;
        return this;
    }

    public MessageContainer setQuestionReplyMessage(FutureProofMessage questionReplyMessage) {
        this.questionReplyMessage = questionReplyMessage;
        return this;
    }

    public MessageContainer setQuestionResponseMessage(QuestionResponseMessage questionResponseMessage) {
        this.questionResponseMessage = questionResponseMessage;
        return this;
    }

    public MessageContainer setStatusQuotedMessage(StatusQuotedMessage statusQuotedMessage) {
        this.statusQuotedMessage = statusQuotedMessage;
        return this;
    }

    public MessageContainer setStatusStickerInteractionMessage(StatusStickerInteractionMessage statusStickerInteractionMessage) {
        this.statusStickerInteractionMessage = statusStickerInteractionMessage;
        return this;
    }

    public MessageContainer setPollCreationMessageV5(PollCreationMessage pollCreationMessageV5) {
        this.pollCreationMessageV5 = pollCreationMessageV5;
        return this;
    }

    public MessageContainer setNewsletterFollowerInviteMessageV2(NewsletterFollowerInviteMessage newsletterFollowerInviteMessageV2) {
        this.newsletterFollowerInviteMessageV2 = newsletterFollowerInviteMessageV2;
        return this;
    }

    public MessageContainer setPollResultSnapshotMessageV3(PollResultSnapshotMessage pollResultSnapshotMessageV3) {
        this.pollResultSnapshotMessageV3 = pollResultSnapshotMessageV3;
        return this;
    }

    public MessageContainer setNewsletterAdminProfileMessage(FutureProofMessage newsletterAdminProfileMessage) {
        this.newsletterAdminProfileMessage = newsletterAdminProfileMessage;
        return this;
    }

    public MessageContainer setNewsletterAdminProfileMessageV2(FutureProofMessage newsletterAdminProfileMessageV2) {
        this.newsletterAdminProfileMessageV2 = newsletterAdminProfileMessageV2;
        return this;
    }
}
