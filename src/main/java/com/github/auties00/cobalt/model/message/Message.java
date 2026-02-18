package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.message.call.BCallMessage;
import com.github.auties00.cobalt.model.message.call.CallLogMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallCreationMessage;
import com.github.auties00.cobalt.model.message.call.ScheduledCallEditMessage;
import com.github.auties00.cobalt.model.message.commerce.InvoiceMessage;
import com.github.auties00.cobalt.model.message.event.EncEventResponseMessage;
import com.github.auties00.cobalt.model.message.event.EventResponseMessage;
import com.github.auties00.cobalt.model.message.payment.*;
import com.github.auties00.cobalt.model.message.poll.*;
import com.github.auties00.cobalt.model.message.security.EncCommentMessage;
import com.github.auties00.cobalt.model.message.security.EncReactionMessage;
import com.github.auties00.cobalt.model.message.security.PlaceholderMessage;
import com.github.auties00.cobalt.model.message.security.SecretEncryptedMessage;
import com.github.auties00.cobalt.model.message.status.StatusNotificationMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuestionAnswerMessage;
import com.github.auties00.cobalt.model.message.status.StatusQuotedMessage;
import com.github.auties00.cobalt.model.message.status.StatusStickerInteractionMessage;
import com.github.auties00.cobalt.model.message.system.*;
import com.github.auties00.cobalt.model.message.system.appstate.*;
import com.github.auties00.cobalt.model.message.system.history.*;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.message.text.CommentMessage;
import com.github.auties00.cobalt.model.message.text.HighlyStructuredMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.util.LinkPreviewMetadata;
import com.github.auties00.cobalt.model.message.util.MMSThumbnailMetadata;
import com.github.auties00.cobalt.model.message.util.URLMetadata;
import com.github.auties00.cobalt.model.message.util.VideoEndCard;
import com.github.auties00.cobalt.model.message.group.SenderKeyDistributionMessage;

public sealed interface Message permits
    ContextualMessage,
    AppStateFatalExceptionNotification,
    AppStateSyncKey,
    AppStateSyncKeyData,
    AppStateSyncKeyFingerprint,
    AppStateSyncKeyId,
    AppStateSyncKeyRequest,
    AppStateSyncKeyShare,
    BCallMessage,
    CallLogMessage,
    CancelPaymentRequestMessage,
    ChatProtocolMessage,
    CloudAPIThreadControlNotification,
    CommentMessage,
    DeclinePaymentRequestMessage,
    DeviceSentMessage,
    EncCommentMessage,
    EncEventResponseMessage,
    EncReactionMessage,
    EventResponseMessage,
    FullHistorySyncOnDemandRequestMetadata,
    FutureProofMessage,
    HighlyStructuredMessage,
    HistorySyncMessageAccessStatus,
    HistorySyncNotification,
    InitialSecurityNotificationSettingSync,
    InvoiceMessage,
    KeepInChatMessage,
    LinkPreviewMetadata,
    MMSThumbnailMetadata,
    MessageHistoryMetadata,
    PaymentExtendedMetadata,
    PaymentInviteMessage,
    PaymentLinkMetadata,
    PeerDataOperationRequestMessage,
    PeerDataOperationRequestResponseMessage,
    PinInChatMessage,
    PlaceholderMessage,
    PollEncValue,
    PollUpdateMessage,
    PollUpdateMessageMetadata,
    PollVoteMessage,
    ProtocolMessage,
    QuestionResponseMessage,
    ReactionMessage,
    RequestPaymentMessage,
    RequestWelcomeMessageMetadata,
    ScheduledCallCreationMessage,
    ScheduledCallEditMessage,
    SecretEncryptedMessage,
    SendPaymentMessage,
    SenderKeyDistributionMessage,
    StatusNotificationMessage,
    StatusQuestionAnswerMessage,
    StatusQuotedMessage,
    StatusStickerInteractionMessage,
    StickerSyncRMRMessage,
    URLMetadata,
    VideoEndCard {
}
