package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.UGCBot;
import com.github.auties00.cobalt.model.sync.action.business.*;
import com.github.auties00.cobalt.model.sync.action.chat.*;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.misc.*;
import com.github.auties00.cobalt.model.sync.action.payment.*;
import com.github.auties00.cobalt.model.sync.setting.*;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingChannelsPersonalisedRecommendationAction;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivateProcessingSettingAction;

public sealed interface SyncAction permits
    AgentAction,
    AiThreadRenameAction,
    AndroidUnsupportedActions,
    ArchiveChatAction,
    AvatarUpdatedAction,
    BotWelcomeRequestAction,
    BroadcastListParticipant,
    BusinessBroadcastAssociationAction,
    BusinessBroadcastListAction,
    CallLogAction,
    ChatAssignmentAction,
    ChatAssignmentOpenedStatusAction,
    ClearChatAction,
    ContactAction,
    CtwaPerCustomerDataSharingAction,
    CustomPaymentMethod,
    CustomPaymentMethodMetadata,
    CustomPaymentMethodsAction,
    DeleteChatAction,
    DeleteIndividualCallLogAction,
    DeleteMessageForMeAction,
    DetectedOutcomesStatusAction,
    ExternalWebBetaAction,
    FavoritesAction,
    InteractiveMessageAction,
    KeyExpiration,
    LabelAssociationAction,
    LabelEditAction,
    LabelReorderingAction,
    LidContactAction,
    LocaleSetting,
    LockChatAction,
    MaibaAIFeaturesControlAction,
    MarkChatAsReadAction,
    MarketingMessageAction,
    MarketingMessageBroadcastAction,
    MerchantPaymentPartnerAction,
    MusicUserIdAction,
    MuteAction,
    NewsletterSavedInterestsAction,
    NoteEditAction,
    NotificationActivitySettingAction,
    NuxAction,
    PaymentInfoAction,
    PaymentTosAction,
    PinAction,
    PnForLidChatAction,
    PrimaryFeature,
    PrimaryVersionAction,
    PrivacySettingChannelsPersonalisedRecommendationAction,
    PrivacySettingDisableLinkPreviewsAction,
    PrivacySettingRelayAllCalls,
    PrivateProcessingSettingAction,
    PushNameSetting,
    QuickReplyAction,
    RecentEmojiWeightsAction,
    RemoveRecentStickerAction,
    SettingsSyncAction,
    StarAction,
    StatusPostOptInNotificationPreferencesAction,
    StatusPrivacyAction,
    StickerAction,
    SubscriptionAction,
    SyncActionMessage,
    SyncActionMessageRange,
    TimeFormatAction,
    UGCBot,
    UnarchiveChatsSetting,
    UserStatusMuteAction,
    UsernameChatStartModeAction,
    WaffleAccountLinkStateAction,
    WamoUserIdentifierAction {
}

