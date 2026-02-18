package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.UGCBot;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastAction;
import com.github.auties00.cobalt.model.sync.action.chat.*;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.misc.*;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.setting.*;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingChannelsPersonalisedRecommendationAction;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.setting.privacy.PrivateProcessingSettingAction;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue")
public final class SyncActionValue {
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    StarAction starAction;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContactAction contactAction;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    MuteAction muteAction;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    PinAction pinAction;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    PushNameSetting pushNameSetting;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    QuickReplyAction quickReplyAction;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    RecentEmojiWeightsAction recentEmojiWeightsAction;

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    LabelEditAction labelEditAction;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    LabelAssociationAction labelAssociationAction;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    LocaleSetting localeSetting;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ArchiveChatAction archiveChatAction;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    DeleteMessageForMeAction deleteMessageForMeAction;

    @ProtobufProperty(index = 19, type = ProtobufType.MESSAGE)
    KeyExpiration keyExpiration;

    @ProtobufProperty(index = 20, type = ProtobufType.MESSAGE)
    MarkChatAsReadAction markChatAsReadAction;

    @ProtobufProperty(index = 21, type = ProtobufType.MESSAGE)
    ClearChatAction clearChatAction;

    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    DeleteChatAction deleteChatAction;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    UnarchiveChatsSetting unarchiveChatsSetting;

    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    PrimaryFeature primaryFeature;

    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    AndroidUnsupportedActions androidUnsupportedActions;

    @ProtobufProperty(index = 27, type = ProtobufType.MESSAGE)
    AgentAction agentAction;

    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    SubscriptionAction subscriptionAction;

    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    UserStatusMuteAction userStatusMuteAction;

    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    TimeFormatAction timeFormatAction;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    NuxAction nuxAction;

    @ProtobufProperty(index = 32, type = ProtobufType.MESSAGE)
    PrimaryVersionAction primaryVersionAction;

    @ProtobufProperty(index = 33, type = ProtobufType.MESSAGE)
    StickerAction stickerAction;

    @ProtobufProperty(index = 34, type = ProtobufType.MESSAGE)
    RemoveRecentStickerAction removeRecentStickerAction;

    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    ChatAssignmentAction chatAssignment;

    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    ChatAssignmentOpenedStatusAction chatAssignmentOpenedStatus;

    @ProtobufProperty(index = 37, type = ProtobufType.MESSAGE)
    PnForLidChatAction pnForLidChatAction;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    MarketingMessageAction marketingMessageAction;

    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    MarketingMessageBroadcastAction marketingMessageBroadcastAction;

    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    ExternalWebBetaAction externalWebBetaAction;

    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    PrivacySettingRelayAllCalls privacySettingRelayAllCalls;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    CallLogAction callLogAction;

    @ProtobufProperty(index = 43, type = ProtobufType.MESSAGE)
    UGCBot ugcBot;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    StatusPrivacyAction statusPrivacy;

    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    BotWelcomeRequestAction botWelcomeRequestAction;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    DeleteIndividualCallLogAction deleteIndividualCallLog;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    LabelReorderingAction labelReorderingAction;

    @ProtobufProperty(index = 48, type = ProtobufType.MESSAGE)
    PaymentInfoAction paymentInfoAction;

    @ProtobufProperty(index = 49, type = ProtobufType.MESSAGE)
    CustomPaymentMethodsAction customPaymentMethodsAction;

    @ProtobufProperty(index = 50, type = ProtobufType.MESSAGE)
    LockChatAction lockChatAction;

    @ProtobufProperty(index = 51, type = ProtobufType.MESSAGE)
    ChatLockSettings chatLockSettings;

    @ProtobufProperty(index = 52, type = ProtobufType.MESSAGE)
    WamoUserIdentifierAction wamoUserIdentifierAction;

    @ProtobufProperty(index = 53, type = ProtobufType.MESSAGE)
    PrivacySettingDisableLinkPreviewsAction privacySettingDisableLinkPreviewsAction;

    @ProtobufProperty(index = 54, type = ProtobufType.MESSAGE)
    DeviceCapabilities deviceCapabilities;

    @ProtobufProperty(index = 55, type = ProtobufType.MESSAGE)
    NoteEditAction noteEditAction;

    @ProtobufProperty(index = 56, type = ProtobufType.MESSAGE)
    FavoritesAction favoritesAction;

    @ProtobufProperty(index = 57, type = ProtobufType.MESSAGE)
    MerchantPaymentPartnerAction merchantPaymentPartnerAction;

    @ProtobufProperty(index = 58, type = ProtobufType.MESSAGE)
    WaffleAccountLinkStateAction waffleAccountLinkStateAction;

    @ProtobufProperty(index = 59, type = ProtobufType.MESSAGE)
    UsernameChatStartModeAction usernameChatStartMode;

    @ProtobufProperty(index = 60, type = ProtobufType.MESSAGE)
    NotificationActivitySettingAction notificationActivitySettingAction;

    @ProtobufProperty(index = 61, type = ProtobufType.MESSAGE)
    LidContactAction lidContactAction;

    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    CtwaPerCustomerDataSharingAction ctwaPerCustomerDataSharingAction;

    @ProtobufProperty(index = 63, type = ProtobufType.MESSAGE)
    PaymentTosAction paymentTosAction;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    PrivacySettingChannelsPersonalisedRecommendationAction privacySettingChannelsPersonalisedRecommendationAction;

    @ProtobufProperty(index = 66, type = ProtobufType.MESSAGE)
    DetectedOutcomesStatusAction detectedOutcomesStatusAction;

    @ProtobufProperty(index = 68, type = ProtobufType.MESSAGE)
    MaibaAIFeaturesControlAction maibaAiFeaturesControlAction;

    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    BusinessBroadcastListAction businessBroadcastListAction;

    @ProtobufProperty(index = 70, type = ProtobufType.MESSAGE)
    MusicUserIdAction musicUserIdAction;

    @ProtobufProperty(index = 71, type = ProtobufType.MESSAGE)
    StatusPostOptInNotificationPreferencesAction statusPostOptInNotificationPreferencesAction;

    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    AvatarUpdatedAction avatarUpdatedAction;

    @ProtobufProperty(index = 74, type = ProtobufType.MESSAGE)
    PrivateProcessingSettingAction privateProcessingSettingAction;

    @ProtobufProperty(index = 75, type = ProtobufType.MESSAGE)
    NewsletterSavedInterestsAction newsletterSavedInterestsAction;

    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    AiThreadRenameAction aiThreadRenameAction;

    @ProtobufProperty(index = 77, type = ProtobufType.MESSAGE)
    InteractiveMessageAction interactiveMessageAction;

    @ProtobufProperty(index = 78, type = ProtobufType.MESSAGE)
    SettingsSyncAction settingsSyncAction;


    SyncActionValue(Instant timestamp, StarAction starAction, ContactAction contactAction, MuteAction muteAction, PinAction pinAction, PushNameSetting pushNameSetting, QuickReplyAction quickReplyAction, RecentEmojiWeightsAction recentEmojiWeightsAction, LabelEditAction labelEditAction, LabelAssociationAction labelAssociationAction, LocaleSetting localeSetting, ArchiveChatAction archiveChatAction, DeleteMessageForMeAction deleteMessageForMeAction, KeyExpiration keyExpiration, MarkChatAsReadAction markChatAsReadAction, ClearChatAction clearChatAction, DeleteChatAction deleteChatAction, UnarchiveChatsSetting unarchiveChatsSetting, PrimaryFeature primaryFeature, AndroidUnsupportedActions androidUnsupportedActions, AgentAction agentAction, SubscriptionAction subscriptionAction, UserStatusMuteAction userStatusMuteAction, TimeFormatAction timeFormatAction, NuxAction nuxAction, PrimaryVersionAction primaryVersionAction, StickerAction stickerAction, RemoveRecentStickerAction removeRecentStickerAction, ChatAssignmentAction chatAssignment, ChatAssignmentOpenedStatusAction chatAssignmentOpenedStatus, PnForLidChatAction pnForLidChatAction, MarketingMessageAction marketingMessageAction, MarketingMessageBroadcastAction marketingMessageBroadcastAction, ExternalWebBetaAction externalWebBetaAction, PrivacySettingRelayAllCalls privacySettingRelayAllCalls, CallLogAction callLogAction, UGCBot ugcBot, StatusPrivacyAction statusPrivacy, BotWelcomeRequestAction botWelcomeRequestAction, DeleteIndividualCallLogAction deleteIndividualCallLog, LabelReorderingAction labelReorderingAction, PaymentInfoAction paymentInfoAction, CustomPaymentMethodsAction customPaymentMethodsAction, LockChatAction lockChatAction, ChatLockSettings chatLockSettings, WamoUserIdentifierAction wamoUserIdentifierAction, PrivacySettingDisableLinkPreviewsAction privacySettingDisableLinkPreviewsAction, DeviceCapabilities deviceCapabilities, NoteEditAction noteEditAction, FavoritesAction favoritesAction, MerchantPaymentPartnerAction merchantPaymentPartnerAction, WaffleAccountLinkStateAction waffleAccountLinkStateAction, UsernameChatStartModeAction usernameChatStartMode, NotificationActivitySettingAction notificationActivitySettingAction, LidContactAction lidContactAction, CtwaPerCustomerDataSharingAction ctwaPerCustomerDataSharingAction, PaymentTosAction paymentTosAction, PrivacySettingChannelsPersonalisedRecommendationAction privacySettingChannelsPersonalisedRecommendationAction, DetectedOutcomesStatusAction detectedOutcomesStatusAction, MaibaAIFeaturesControlAction maibaAiFeaturesControlAction, BusinessBroadcastListAction businessBroadcastListAction, MusicUserIdAction musicUserIdAction, StatusPostOptInNotificationPreferencesAction statusPostOptInNotificationPreferencesAction, AvatarUpdatedAction avatarUpdatedAction, PrivateProcessingSettingAction privateProcessingSettingAction, NewsletterSavedInterestsAction newsletterSavedInterestsAction, AiThreadRenameAction aiThreadRenameAction, InteractiveMessageAction interactiveMessageAction, SettingsSyncAction settingsSyncAction) {
        this.timestamp = timestamp;
        this.starAction = starAction;
        this.contactAction = contactAction;
        this.muteAction = muteAction;
        this.pinAction = pinAction;
        this.pushNameSetting = pushNameSetting;
        this.quickReplyAction = quickReplyAction;
        this.recentEmojiWeightsAction = recentEmojiWeightsAction;
        this.labelEditAction = labelEditAction;
        this.labelAssociationAction = labelAssociationAction;
        this.localeSetting = localeSetting;
        this.archiveChatAction = archiveChatAction;
        this.deleteMessageForMeAction = deleteMessageForMeAction;
        this.keyExpiration = keyExpiration;
        this.markChatAsReadAction = markChatAsReadAction;
        this.clearChatAction = clearChatAction;
        this.deleteChatAction = deleteChatAction;
        this.unarchiveChatsSetting = unarchiveChatsSetting;
        this.primaryFeature = primaryFeature;
        this.androidUnsupportedActions = androidUnsupportedActions;
        this.agentAction = agentAction;
        this.subscriptionAction = subscriptionAction;
        this.userStatusMuteAction = userStatusMuteAction;
        this.timeFormatAction = timeFormatAction;
        this.nuxAction = nuxAction;
        this.primaryVersionAction = primaryVersionAction;
        this.stickerAction = stickerAction;
        this.removeRecentStickerAction = removeRecentStickerAction;
        this.chatAssignment = chatAssignment;
        this.chatAssignmentOpenedStatus = chatAssignmentOpenedStatus;
        this.pnForLidChatAction = pnForLidChatAction;
        this.marketingMessageAction = marketingMessageAction;
        this.marketingMessageBroadcastAction = marketingMessageBroadcastAction;
        this.externalWebBetaAction = externalWebBetaAction;
        this.privacySettingRelayAllCalls = privacySettingRelayAllCalls;
        this.callLogAction = callLogAction;
        this.ugcBot = ugcBot;
        this.statusPrivacy = statusPrivacy;
        this.botWelcomeRequestAction = botWelcomeRequestAction;
        this.deleteIndividualCallLog = deleteIndividualCallLog;
        this.labelReorderingAction = labelReorderingAction;
        this.paymentInfoAction = paymentInfoAction;
        this.customPaymentMethodsAction = customPaymentMethodsAction;
        this.lockChatAction = lockChatAction;
        this.chatLockSettings = chatLockSettings;
        this.wamoUserIdentifierAction = wamoUserIdentifierAction;
        this.privacySettingDisableLinkPreviewsAction = privacySettingDisableLinkPreviewsAction;
        this.deviceCapabilities = deviceCapabilities;
        this.noteEditAction = noteEditAction;
        this.favoritesAction = favoritesAction;
        this.merchantPaymentPartnerAction = merchantPaymentPartnerAction;
        this.waffleAccountLinkStateAction = waffleAccountLinkStateAction;
        this.usernameChatStartMode = usernameChatStartMode;
        this.notificationActivitySettingAction = notificationActivitySettingAction;
        this.lidContactAction = lidContactAction;
        this.ctwaPerCustomerDataSharingAction = ctwaPerCustomerDataSharingAction;
        this.paymentTosAction = paymentTosAction;
        this.privacySettingChannelsPersonalisedRecommendationAction = privacySettingChannelsPersonalisedRecommendationAction;
        this.detectedOutcomesStatusAction = detectedOutcomesStatusAction;
        this.maibaAiFeaturesControlAction = maibaAiFeaturesControlAction;
        this.businessBroadcastListAction = businessBroadcastListAction;
        this.musicUserIdAction = musicUserIdAction;
        this.statusPostOptInNotificationPreferencesAction = statusPostOptInNotificationPreferencesAction;
        this.avatarUpdatedAction = avatarUpdatedAction;
        this.privateProcessingSettingAction = privateProcessingSettingAction;
        this.newsletterSavedInterestsAction = newsletterSavedInterestsAction;
        this.aiThreadRenameAction = aiThreadRenameAction;
        this.interactiveMessageAction = interactiveMessageAction;
        this.settingsSyncAction = settingsSyncAction;
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public Optional<StarAction> starAction() {
        return Optional.ofNullable(starAction);
    }

    public Optional<ContactAction> contactAction() {
        return Optional.ofNullable(contactAction);
    }

    public Optional<MuteAction> muteAction() {
        return Optional.ofNullable(muteAction);
    }

    public Optional<PinAction> pinAction() {
        return Optional.ofNullable(pinAction);
    }

    public Optional<PushNameSetting> pushNameSetting() {
        return Optional.ofNullable(pushNameSetting);
    }

    public Optional<QuickReplyAction> quickReplyAction() {
        return Optional.ofNullable(quickReplyAction);
    }

    public Optional<RecentEmojiWeightsAction> recentEmojiWeightsAction() {
        return Optional.ofNullable(recentEmojiWeightsAction);
    }

    public Optional<LabelEditAction> labelEditAction() {
        return Optional.ofNullable(labelEditAction);
    }

    public Optional<LabelAssociationAction> labelAssociationAction() {
        return Optional.ofNullable(labelAssociationAction);
    }

    public Optional<LocaleSetting> localeSetting() {
        return Optional.ofNullable(localeSetting);
    }

    public Optional<ArchiveChatAction> archiveChatAction() {
        return Optional.ofNullable(archiveChatAction);
    }

    public Optional<DeleteMessageForMeAction> deleteMessageForMeAction() {
        return Optional.ofNullable(deleteMessageForMeAction);
    }

    public Optional<KeyExpiration> keyExpiration() {
        return Optional.ofNullable(keyExpiration);
    }

    public Optional<MarkChatAsReadAction> markChatAsReadAction() {
        return Optional.ofNullable(markChatAsReadAction);
    }

    public Optional<ClearChatAction> clearChatAction() {
        return Optional.ofNullable(clearChatAction);
    }

    public Optional<DeleteChatAction> deleteChatAction() {
        return Optional.ofNullable(deleteChatAction);
    }

    public Optional<UnarchiveChatsSetting> unarchiveChatsSetting() {
        return Optional.ofNullable(unarchiveChatsSetting);
    }

    public Optional<PrimaryFeature> primaryFeature() {
        return Optional.ofNullable(primaryFeature);
    }

    public Optional<AndroidUnsupportedActions> androidUnsupportedActions() {
        return Optional.ofNullable(androidUnsupportedActions);
    }

    public Optional<AgentAction> agentAction() {
        return Optional.ofNullable(agentAction);
    }

    public Optional<SubscriptionAction> subscriptionAction() {
        return Optional.ofNullable(subscriptionAction);
    }

    public Optional<UserStatusMuteAction> userStatusMuteAction() {
        return Optional.ofNullable(userStatusMuteAction);
    }

    public Optional<TimeFormatAction> timeFormatAction() {
        return Optional.ofNullable(timeFormatAction);
    }

    public Optional<NuxAction> nuxAction() {
        return Optional.ofNullable(nuxAction);
    }

    public Optional<PrimaryVersionAction> primaryVersionAction() {
        return Optional.ofNullable(primaryVersionAction);
    }

    public Optional<StickerAction> stickerAction() {
        return Optional.ofNullable(stickerAction);
    }

    public Optional<RemoveRecentStickerAction> removeRecentStickerAction() {
        return Optional.ofNullable(removeRecentStickerAction);
    }

    public Optional<ChatAssignmentAction> chatAssignment() {
        return Optional.ofNullable(chatAssignment);
    }

    public Optional<ChatAssignmentOpenedStatusAction> chatAssignmentOpenedStatus() {
        return Optional.ofNullable(chatAssignmentOpenedStatus);
    }

    public Optional<PnForLidChatAction> pnForLidChatAction() {
        return Optional.ofNullable(pnForLidChatAction);
    }

    public Optional<MarketingMessageAction> marketingMessageAction() {
        return Optional.ofNullable(marketingMessageAction);
    }

    public Optional<MarketingMessageBroadcastAction> marketingMessageBroadcastAction() {
        return Optional.ofNullable(marketingMessageBroadcastAction);
    }

    public Optional<ExternalWebBetaAction> externalWebBetaAction() {
        return Optional.ofNullable(externalWebBetaAction);
    }

    public Optional<PrivacySettingRelayAllCalls> privacySettingRelayAllCalls() {
        return Optional.ofNullable(privacySettingRelayAllCalls);
    }

    public Optional<CallLogAction> callLogAction() {
        return Optional.ofNullable(callLogAction);
    }

    public Optional<UGCBot> ugcBot() {
        return Optional.ofNullable(ugcBot);
    }

    public Optional<StatusPrivacyAction> statusPrivacy() {
        return Optional.ofNullable(statusPrivacy);
    }

    public Optional<BotWelcomeRequestAction> botWelcomeRequestAction() {
        return Optional.ofNullable(botWelcomeRequestAction);
    }

    public Optional<DeleteIndividualCallLogAction> deleteIndividualCallLog() {
        return Optional.ofNullable(deleteIndividualCallLog);
    }

    public Optional<LabelReorderingAction> labelReorderingAction() {
        return Optional.ofNullable(labelReorderingAction);
    }

    public Optional<PaymentInfoAction> paymentInfoAction() {
        return Optional.ofNullable(paymentInfoAction);
    }

    public Optional<CustomPaymentMethodsAction> customPaymentMethodsAction() {
        return Optional.ofNullable(customPaymentMethodsAction);
    }

    public Optional<LockChatAction> lockChatAction() {
        return Optional.ofNullable(lockChatAction);
    }

    public Optional<ChatLockSettings> chatLockSettings() {
        return Optional.ofNullable(chatLockSettings);
    }

    public Optional<WamoUserIdentifierAction> wamoUserIdentifierAction() {
        return Optional.ofNullable(wamoUserIdentifierAction);
    }

    public Optional<PrivacySettingDisableLinkPreviewsAction> privacySettingDisableLinkPreviewsAction() {
        return Optional.ofNullable(privacySettingDisableLinkPreviewsAction);
    }

    public Optional<DeviceCapabilities> deviceCapabilities() {
        return Optional.ofNullable(deviceCapabilities);
    }

    public Optional<NoteEditAction> noteEditAction() {
        return Optional.ofNullable(noteEditAction);
    }

    public Optional<FavoritesAction> favoritesAction() {
        return Optional.ofNullable(favoritesAction);
    }

    public Optional<MerchantPaymentPartnerAction> merchantPaymentPartnerAction() {
        return Optional.ofNullable(merchantPaymentPartnerAction);
    }

    public Optional<WaffleAccountLinkStateAction> waffleAccountLinkStateAction() {
        return Optional.ofNullable(waffleAccountLinkStateAction);
    }

    public Optional<UsernameChatStartModeAction> usernameChatStartMode() {
        return Optional.ofNullable(usernameChatStartMode);
    }

    public Optional<NotificationActivitySettingAction> notificationActivitySettingAction() {
        return Optional.ofNullable(notificationActivitySettingAction);
    }

    public Optional<LidContactAction> lidContactAction() {
        return Optional.ofNullable(lidContactAction);
    }

    public Optional<CtwaPerCustomerDataSharingAction> ctwaPerCustomerDataSharingAction() {
        return Optional.ofNullable(ctwaPerCustomerDataSharingAction);
    }

    public Optional<PaymentTosAction> paymentTosAction() {
        return Optional.ofNullable(paymentTosAction);
    }

    public Optional<PrivacySettingChannelsPersonalisedRecommendationAction> privacySettingChannelsPersonalisedRecommendationAction() {
        return Optional.ofNullable(privacySettingChannelsPersonalisedRecommendationAction);
    }

    public Optional<DetectedOutcomesStatusAction> detectedOutcomesStatusAction() {
        return Optional.ofNullable(detectedOutcomesStatusAction);
    }

    public Optional<MaibaAIFeaturesControlAction> maibaAiFeaturesControlAction() {
        return Optional.ofNullable(maibaAiFeaturesControlAction);
    }

    public Optional<BusinessBroadcastListAction> businessBroadcastListAction() {
        return Optional.ofNullable(businessBroadcastListAction);
    }

    public Optional<MusicUserIdAction> musicUserIdAction() {
        return Optional.ofNullable(musicUserIdAction);
    }

    public Optional<StatusPostOptInNotificationPreferencesAction> statusPostOptInNotificationPreferencesAction() {
        return Optional.ofNullable(statusPostOptInNotificationPreferencesAction);
    }

    public Optional<AvatarUpdatedAction> avatarUpdatedAction() {
        return Optional.ofNullable(avatarUpdatedAction);
    }

    public Optional<PrivateProcessingSettingAction> privateProcessingSettingAction() {
        return Optional.ofNullable(privateProcessingSettingAction);
    }

    public Optional<NewsletterSavedInterestsAction> newsletterSavedInterestsAction() {
        return Optional.ofNullable(newsletterSavedInterestsAction);
    }

    public Optional<AiThreadRenameAction> aiThreadRenameAction() {
        return Optional.ofNullable(aiThreadRenameAction);
    }

    public Optional<InteractiveMessageAction> interactiveMessageAction() {
        return Optional.ofNullable(interactiveMessageAction);
    }

    public Optional<SettingsSyncAction> settingsSyncAction() {
        return Optional.ofNullable(settingsSyncAction);
    }

    public SyncActionValue setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public SyncActionValue setStarAction(StarAction starAction) {
        this.starAction = starAction;
        return this;
    }

    public SyncActionValue setContactAction(ContactAction contactAction) {
        this.contactAction = contactAction;
        return this;
    }

    public SyncActionValue setMuteAction(MuteAction muteAction) {
        this.muteAction = muteAction;
        return this;
    }

    public SyncActionValue setPinAction(PinAction pinAction) {
        this.pinAction = pinAction;
        return this;
    }

    public SyncActionValue setPushNameSetting(PushNameSetting pushNameSetting) {
        this.pushNameSetting = pushNameSetting;
        return this;
    }

    public SyncActionValue setQuickReplyAction(QuickReplyAction quickReplyAction) {
        this.quickReplyAction = quickReplyAction;
        return this;
    }

    public SyncActionValue setRecentEmojiWeightsAction(RecentEmojiWeightsAction recentEmojiWeightsAction) {
        this.recentEmojiWeightsAction = recentEmojiWeightsAction;
        return this;
    }

    public SyncActionValue setLabelEditAction(LabelEditAction labelEditAction) {
        this.labelEditAction = labelEditAction;
        return this;
    }

    public SyncActionValue setLabelAssociationAction(LabelAssociationAction labelAssociationAction) {
        this.labelAssociationAction = labelAssociationAction;
        return this;
    }

    public SyncActionValue setLocaleSetting(LocaleSetting localeSetting) {
        this.localeSetting = localeSetting;
        return this;
    }

    public SyncActionValue setArchiveChatAction(ArchiveChatAction archiveChatAction) {
        this.archiveChatAction = archiveChatAction;
        return this;
    }

    public SyncActionValue setDeleteMessageForMeAction(DeleteMessageForMeAction deleteMessageForMeAction) {
        this.deleteMessageForMeAction = deleteMessageForMeAction;
        return this;
    }

    public SyncActionValue setKeyExpiration(KeyExpiration keyExpiration) {
        this.keyExpiration = keyExpiration;
        return this;
    }

    public SyncActionValue setMarkChatAsReadAction(MarkChatAsReadAction markChatAsReadAction) {
        this.markChatAsReadAction = markChatAsReadAction;
        return this;
    }

    public SyncActionValue setClearChatAction(ClearChatAction clearChatAction) {
        this.clearChatAction = clearChatAction;
        return this;
    }

    public SyncActionValue setDeleteChatAction(DeleteChatAction deleteChatAction) {
        this.deleteChatAction = deleteChatAction;
        return this;
    }

    public SyncActionValue setUnarchiveChatsSetting(UnarchiveChatsSetting unarchiveChatsSetting) {
        this.unarchiveChatsSetting = unarchiveChatsSetting;
        return this;
    }

    public SyncActionValue setPrimaryFeature(PrimaryFeature primaryFeature) {
        this.primaryFeature = primaryFeature;
        return this;
    }

    public SyncActionValue setAndroidUnsupportedActions(AndroidUnsupportedActions androidUnsupportedActions) {
        this.androidUnsupportedActions = androidUnsupportedActions;
        return this;
    }

    public SyncActionValue setAgentAction(AgentAction agentAction) {
        this.agentAction = agentAction;
        return this;
    }

    public SyncActionValue setSubscriptionAction(SubscriptionAction subscriptionAction) {
        this.subscriptionAction = subscriptionAction;
        return this;
    }

    public SyncActionValue setUserStatusMuteAction(UserStatusMuteAction userStatusMuteAction) {
        this.userStatusMuteAction = userStatusMuteAction;
        return this;
    }

    public SyncActionValue setTimeFormatAction(TimeFormatAction timeFormatAction) {
        this.timeFormatAction = timeFormatAction;
        return this;
    }

    public SyncActionValue setNuxAction(NuxAction nuxAction) {
        this.nuxAction = nuxAction;
        return this;
    }

    public SyncActionValue setPrimaryVersionAction(PrimaryVersionAction primaryVersionAction) {
        this.primaryVersionAction = primaryVersionAction;
        return this;
    }

    public SyncActionValue setStickerAction(StickerAction stickerAction) {
        this.stickerAction = stickerAction;
        return this;
    }

    public SyncActionValue setRemoveRecentStickerAction(RemoveRecentStickerAction removeRecentStickerAction) {
        this.removeRecentStickerAction = removeRecentStickerAction;
        return this;
    }

    public SyncActionValue setChatAssignment(ChatAssignmentAction chatAssignment) {
        this.chatAssignment = chatAssignment;
        return this;
    }

    public SyncActionValue setChatAssignmentOpenedStatus(ChatAssignmentOpenedStatusAction chatAssignmentOpenedStatus) {
        this.chatAssignmentOpenedStatus = chatAssignmentOpenedStatus;
        return this;
    }

    public SyncActionValue setPnForLidChatAction(PnForLidChatAction pnForLidChatAction) {
        this.pnForLidChatAction = pnForLidChatAction;
        return this;
    }

    public SyncActionValue setMarketingMessageAction(MarketingMessageAction marketingMessageAction) {
        this.marketingMessageAction = marketingMessageAction;
        return this;
    }

    public SyncActionValue setMarketingMessageBroadcastAction(MarketingMessageBroadcastAction marketingMessageBroadcastAction) {
        this.marketingMessageBroadcastAction = marketingMessageBroadcastAction;
        return this;
    }

    public SyncActionValue setExternalWebBetaAction(ExternalWebBetaAction externalWebBetaAction) {
        this.externalWebBetaAction = externalWebBetaAction;
        return this;
    }

    public SyncActionValue setPrivacySettingRelayAllCalls(PrivacySettingRelayAllCalls privacySettingRelayAllCalls) {
        this.privacySettingRelayAllCalls = privacySettingRelayAllCalls;
        return this;
    }

    public SyncActionValue setCallLogAction(CallLogAction callLogAction) {
        this.callLogAction = callLogAction;
        return this;
    }

    public SyncActionValue setUgcBot(UGCBot ugcBot) {
        this.ugcBot = ugcBot;
        return this;
    }

    public SyncActionValue setStatusPrivacy(StatusPrivacyAction statusPrivacy) {
        this.statusPrivacy = statusPrivacy;
        return this;
    }

    public SyncActionValue setBotWelcomeRequestAction(BotWelcomeRequestAction botWelcomeRequestAction) {
        this.botWelcomeRequestAction = botWelcomeRequestAction;
        return this;
    }

    public SyncActionValue setDeleteIndividualCallLog(DeleteIndividualCallLogAction deleteIndividualCallLog) {
        this.deleteIndividualCallLog = deleteIndividualCallLog;
        return this;
    }

    public SyncActionValue setLabelReorderingAction(LabelReorderingAction labelReorderingAction) {
        this.labelReorderingAction = labelReorderingAction;
        return this;
    }

    public SyncActionValue setPaymentInfoAction(PaymentInfoAction paymentInfoAction) {
        this.paymentInfoAction = paymentInfoAction;
        return this;
    }

    public SyncActionValue setCustomPaymentMethodsAction(CustomPaymentMethodsAction customPaymentMethodsAction) {
        this.customPaymentMethodsAction = customPaymentMethodsAction;
        return this;
    }

    public SyncActionValue setLockChatAction(LockChatAction lockChatAction) {
        this.lockChatAction = lockChatAction;
        return this;
    }

    public SyncActionValue setChatLockSettings(ChatLockSettings chatLockSettings) {
        this.chatLockSettings = chatLockSettings;
        return this;
    }

    public SyncActionValue setWamoUserIdentifierAction(WamoUserIdentifierAction wamoUserIdentifierAction) {
        this.wamoUserIdentifierAction = wamoUserIdentifierAction;
        return this;
    }

    public SyncActionValue setPrivacySettingDisableLinkPreviewsAction(PrivacySettingDisableLinkPreviewsAction privacySettingDisableLinkPreviewsAction) {
        this.privacySettingDisableLinkPreviewsAction = privacySettingDisableLinkPreviewsAction;
        return this;
    }

    public SyncActionValue setDeviceCapabilities(DeviceCapabilities deviceCapabilities) {
        this.deviceCapabilities = deviceCapabilities;
        return this;
    }

    public SyncActionValue setNoteEditAction(NoteEditAction noteEditAction) {
        this.noteEditAction = noteEditAction;
        return this;
    }

    public SyncActionValue setFavoritesAction(FavoritesAction favoritesAction) {
        this.favoritesAction = favoritesAction;
        return this;
    }

    public SyncActionValue setMerchantPaymentPartnerAction(MerchantPaymentPartnerAction merchantPaymentPartnerAction) {
        this.merchantPaymentPartnerAction = merchantPaymentPartnerAction;
        return this;
    }

    public SyncActionValue setWaffleAccountLinkStateAction(WaffleAccountLinkStateAction waffleAccountLinkStateAction) {
        this.waffleAccountLinkStateAction = waffleAccountLinkStateAction;
        return this;
    }

    public SyncActionValue setUsernameChatStartMode(UsernameChatStartModeAction usernameChatStartMode) {
        this.usernameChatStartMode = usernameChatStartMode;
        return this;
    }

    public SyncActionValue setNotificationActivitySettingAction(NotificationActivitySettingAction notificationActivitySettingAction) {
        this.notificationActivitySettingAction = notificationActivitySettingAction;
        return this;
    }

    public SyncActionValue setLidContactAction(LidContactAction lidContactAction) {
        this.lidContactAction = lidContactAction;
        return this;
    }

    public SyncActionValue setCtwaPerCustomerDataSharingAction(CtwaPerCustomerDataSharingAction ctwaPerCustomerDataSharingAction) {
        this.ctwaPerCustomerDataSharingAction = ctwaPerCustomerDataSharingAction;
        return this;
    }

    public SyncActionValue setPaymentTosAction(PaymentTosAction paymentTosAction) {
        this.paymentTosAction = paymentTosAction;
        return this;
    }

    public SyncActionValue setPrivacySettingChannelsPersonalisedRecommendationAction(PrivacySettingChannelsPersonalisedRecommendationAction privacySettingChannelsPersonalisedRecommendationAction) {
        this.privacySettingChannelsPersonalisedRecommendationAction = privacySettingChannelsPersonalisedRecommendationAction;
        return this;
    }

    public SyncActionValue setDetectedOutcomesStatusAction(DetectedOutcomesStatusAction detectedOutcomesStatusAction) {
        this.detectedOutcomesStatusAction = detectedOutcomesStatusAction;
        return this;
    }

    public SyncActionValue setMaibaAiFeaturesControlAction(MaibaAIFeaturesControlAction maibaAiFeaturesControlAction) {
        this.maibaAiFeaturesControlAction = maibaAiFeaturesControlAction;
        return this;
    }

    public SyncActionValue setBusinessBroadcastListAction(BusinessBroadcastListAction businessBroadcastListAction) {
        this.businessBroadcastListAction = businessBroadcastListAction;
        return this;
    }

    public SyncActionValue setMusicUserIdAction(MusicUserIdAction musicUserIdAction) {
        this.musicUserIdAction = musicUserIdAction;
        return this;
    }

    public SyncActionValue setStatusPostOptInNotificationPreferencesAction(StatusPostOptInNotificationPreferencesAction statusPostOptInNotificationPreferencesAction) {
        this.statusPostOptInNotificationPreferencesAction = statusPostOptInNotificationPreferencesAction;
        return this;
    }

    public SyncActionValue setAvatarUpdatedAction(AvatarUpdatedAction avatarUpdatedAction) {
        this.avatarUpdatedAction = avatarUpdatedAction;
        return this;
    }

    public SyncActionValue setPrivateProcessingSettingAction(PrivateProcessingSettingAction privateProcessingSettingAction) {
        this.privateProcessingSettingAction = privateProcessingSettingAction;
        return this;
    }

    public SyncActionValue setNewsletterSavedInterestsAction(NewsletterSavedInterestsAction newsletterSavedInterestsAction) {
        this.newsletterSavedInterestsAction = newsletterSavedInterestsAction;
        return this;
    }

    public SyncActionValue setAiThreadRenameAction(AiThreadRenameAction aiThreadRenameAction) {
        this.aiThreadRenameAction = aiThreadRenameAction;
        return this;
    }

    public SyncActionValue setInteractiveMessageAction(InteractiveMessageAction interactiveMessageAction) {
        this.interactiveMessageAction = interactiveMessageAction;
        return this;
    }

    public SyncActionValue setSettingsSyncAction(SettingsSyncAction settingsSyncAction) {
        this.settingsSyncAction = settingsSyncAction;
        return this;
    }
}
