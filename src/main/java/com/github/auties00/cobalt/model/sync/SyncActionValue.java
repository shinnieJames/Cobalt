package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.UGCBotAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastInsightsAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastAssociationAction;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageBroadcastAction;
import com.github.auties00.cobalt.model.sync.action.call.*;
import com.github.auties00.cobalt.model.sync.action.chat.*;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.device.*;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethodsAction;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentInfoAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingChannelsPersonalisedRecommendationAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.*;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue")
public final class SyncActionValue {
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    final StarAction starAction;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    final ContactAction contactAction;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    final MuteAction muteAction;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    final PinAction pinAction;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    final PushNameSetting pushNameSetting;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    final QuickReplyAction quickReplyAction;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    final RecentEmojiWeightsAction recentEmojiWeightsAction;

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    final LabelEditAction labelEditAction;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    final LabelAssociationAction labelAssociationAction;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    final LocaleSetting localeSetting;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    final ArchiveChatAction archiveChatAction;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    final DeleteMessageForMeAction deleteMessageForMeAction;

    @ProtobufProperty(index = 19, type = ProtobufType.MESSAGE)
    final KeyExpirationAction keyExpirationAction;

    @ProtobufProperty(index = 20, type = ProtobufType.MESSAGE)
    final MarkChatAsReadAction markChatAsReadAction;

    @ProtobufProperty(index = 21, type = ProtobufType.MESSAGE)
    final ClearChatAction clearChatAction;

    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    final DeleteChatAction deleteChatAction;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    final UnarchiveChatsSetting unarchiveChatsSetting;

    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    final PrimaryFeatureAction primaryFeatureAction;

    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    final AndroidUnsupportedActions androidUnsupportedActions;

    @ProtobufProperty(index = 27, type = ProtobufType.MESSAGE)
    final AgentAction agentAction;

    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    final SubscriptionAction subscriptionAction;

    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    final UserStatusMuteAction userStatusMuteAction;

    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    final TimeFormatAction timeFormatAction;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    final NuxAction nuxAction;

    @ProtobufProperty(index = 32, type = ProtobufType.MESSAGE)
    final PrimaryVersionAction primaryVersionAction;

    @ProtobufProperty(index = 33, type = ProtobufType.MESSAGE)
    final StickerAction stickerAction;

    @ProtobufProperty(index = 34, type = ProtobufType.MESSAGE)
    final RemoveRecentStickerAction removeRecentStickerAction;

    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    final ChatAssignmentAction chatAssignment;

    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    final ChatAssignmentOpenedStatusAction chatAssignmentOpenedStatus;

    @ProtobufProperty(index = 37, type = ProtobufType.MESSAGE)
    final PnForLidChatAction pnForLidChatAction;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    final MarketingMessageAction marketingMessageAction;

    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    final MarketingMessageBroadcastAction marketingMessageBroadcastAction;

    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    final ExternalWebBetaAction externalWebBetaAction;

    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    final PrivacySettingRelayAllCalls privacySettingRelayAllCalls;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    final CallLogAction callLogAction;

    @ProtobufProperty(index = 43, type = ProtobufType.MESSAGE)
    final UGCBotAction ugcBotAction;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    final StatusPrivacyAction statusPrivacy;

    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    final BotWelcomeRequestAction botWelcomeRequestAction;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    final DeleteIndividualCallLogAction deleteIndividualCallLog;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    final LabelReorderingAction labelReorderingAction;

    @ProtobufProperty(index = 48, type = ProtobufType.MESSAGE)
    final PaymentInfoAction paymentInfoAction;

    @ProtobufProperty(index = 49, type = ProtobufType.MESSAGE)
    final CustomPaymentMethodsAction customPaymentMethodsAction;

    @ProtobufProperty(index = 50, type = ProtobufType.MESSAGE)
    final LockChatAction lockChatAction;

    @ProtobufProperty(index = 51, type = ProtobufType.MESSAGE)
    ChatLockSettings chatLockSettings;

    @ProtobufProperty(index = 52, type = ProtobufType.MESSAGE)
    final WamoUserIdentifierAction wamoUserIdentifierAction;

    @ProtobufProperty(index = 53, type = ProtobufType.MESSAGE)
    final PrivacySettingDisableLinkPreviewsAction privacySettingDisableLinkPreviewsAction;

    @ProtobufProperty(index = 54, type = ProtobufType.MESSAGE)
    DeviceCapabilities deviceCapabilities;

    @ProtobufProperty(index = 55, type = ProtobufType.MESSAGE)
    final NoteEditAction noteEditAction;

    @ProtobufProperty(index = 56, type = ProtobufType.MESSAGE)
    final FavoritesAction favoritesAction;

    @ProtobufProperty(index = 57, type = ProtobufType.MESSAGE)
    final MerchantPaymentPartnerAction merchantPaymentPartnerAction;

    @ProtobufProperty(index = 58, type = ProtobufType.MESSAGE)
    final WaffleAccountLinkStateAction waffleAccountLinkStateAction;

    @ProtobufProperty(index = 59, type = ProtobufType.MESSAGE)
    final UsernameChatStartModeAction usernameChatStartMode;

    @ProtobufProperty(index = 60, type = ProtobufType.MESSAGE)
    final NotificationActivitySettingAction notificationActivitySettingAction;

    @ProtobufProperty(index = 61, type = ProtobufType.MESSAGE)
    final LidContactAction lidContactAction;

    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    final CtwaPerCustomerDataSharingAction ctwaPerCustomerDataSharingAction;

    @ProtobufProperty(index = 63, type = ProtobufType.MESSAGE)
    final PaymentTosAction paymentTosAction;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    final PrivacySettingChannelsPersonalisedRecommendationAction privacySettingChannelsPersonalisedRecommendationAction;

    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    final BusinessBroadcastAssociationAction businessBroadcastAssociationAction;

    @ProtobufProperty(index = 66, type = ProtobufType.MESSAGE)
    final DetectedOutcomesStatusAction detectedOutcomesStatusAction;

    @ProtobufProperty(index = 68, type = ProtobufType.MESSAGE)
    final MaibaAIFeaturesControlAction maibaAiFeaturesControlAction;

    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    final BusinessBroadcastListAction businessBroadcastListAction;

    @ProtobufProperty(index = 70, type = ProtobufType.MESSAGE)
    final MusicUserIdAction musicUserIdAction;

    @ProtobufProperty(index = 71, type = ProtobufType.MESSAGE)
    final StatusPostOptInNotificationPreferencesAction statusPostOptInNotificationPreferencesAction;

    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    final AvatarUpdatedAction avatarUpdatedAction;

    @ProtobufProperty(index = 74, type = ProtobufType.MESSAGE)
    final PrivateProcessingSettingAction privateProcessingSettingAction;

    @ProtobufProperty(index = 75, type = ProtobufType.MESSAGE)
    final NewsletterSavedInterestsAction newsletterSavedInterestsAction;

    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    final AiThreadRenameAction aiThreadRenameAction;

    @ProtobufProperty(index = 77, type = ProtobufType.MESSAGE)
    final InteractiveMessageAction interactiveMessageAction;

    @ProtobufProperty(index = 78, type = ProtobufType.MESSAGE)
    final SettingsSyncAction settingsSyncAction;

    @ProtobufProperty(index = 79, type = ProtobufType.MESSAGE)
    final OutContactAction outContactAction;

    @ProtobufProperty(index = 80, type = ProtobufType.MESSAGE)
    final NctSaltSyncAction nctSaltSyncAction;

    @ProtobufProperty(index = 81, type = ProtobufType.MESSAGE)
    final BusinessBroadcastCampaignAction businessBroadcastCampaignAction;

    @ProtobufProperty(index = 82, type = ProtobufType.MESSAGE)
    final BusinessBroadcastInsightsAction businessBroadcastInsightsAction;


    SyncActionValue(Instant timestamp, StarAction starAction, ContactAction contactAction, MuteAction muteAction, PinAction pinAction, PushNameSetting pushNameSetting, QuickReplyAction quickReplyAction, RecentEmojiWeightsAction recentEmojiWeightsAction, LabelEditAction labelEditAction, LabelAssociationAction labelAssociationAction, LocaleSetting localeSetting, ArchiveChatAction archiveChatAction, DeleteMessageForMeAction deleteMessageForMeAction, KeyExpirationAction keyExpirationAction, MarkChatAsReadAction markChatAsReadAction, ClearChatAction clearChatAction, DeleteChatAction deleteChatAction, UnarchiveChatsSetting unarchiveChatsSetting, PrimaryFeatureAction primaryFeatureAction, AndroidUnsupportedActions androidUnsupportedActions, AgentAction agentAction, SubscriptionAction subscriptionAction, UserStatusMuteAction userStatusMuteAction, TimeFormatAction timeFormatAction, NuxAction nuxAction, PrimaryVersionAction primaryVersionAction, StickerAction stickerAction, RemoveRecentStickerAction removeRecentStickerAction, ChatAssignmentAction chatAssignment, ChatAssignmentOpenedStatusAction chatAssignmentOpenedStatus, PnForLidChatAction pnForLidChatAction, MarketingMessageAction marketingMessageAction, MarketingMessageBroadcastAction marketingMessageBroadcastAction, ExternalWebBetaAction externalWebBetaAction, PrivacySettingRelayAllCalls privacySettingRelayAllCalls, CallLogAction callLogAction, UGCBotAction ugcBotAction, StatusPrivacyAction statusPrivacy, BotWelcomeRequestAction botWelcomeRequestAction, DeleteIndividualCallLogAction deleteIndividualCallLog, LabelReorderingAction labelReorderingAction, PaymentInfoAction paymentInfoAction, CustomPaymentMethodsAction customPaymentMethodsAction, LockChatAction lockChatAction, ChatLockSettings chatLockSettings, WamoUserIdentifierAction wamoUserIdentifierAction, PrivacySettingDisableLinkPreviewsAction privacySettingDisableLinkPreviewsAction, DeviceCapabilities deviceCapabilities, NoteEditAction noteEditAction, FavoritesAction favoritesAction, MerchantPaymentPartnerAction merchantPaymentPartnerAction, WaffleAccountLinkStateAction waffleAccountLinkStateAction, UsernameChatStartModeAction usernameChatStartMode, NotificationActivitySettingAction notificationActivitySettingAction, LidContactAction lidContactAction, CtwaPerCustomerDataSharingAction ctwaPerCustomerDataSharingAction, PaymentTosAction paymentTosAction, PrivacySettingChannelsPersonalisedRecommendationAction privacySettingChannelsPersonalisedRecommendationAction, BusinessBroadcastAssociationAction businessBroadcastAssociationAction, DetectedOutcomesStatusAction detectedOutcomesStatusAction, MaibaAIFeaturesControlAction maibaAiFeaturesControlAction, BusinessBroadcastListAction businessBroadcastListAction, MusicUserIdAction musicUserIdAction, StatusPostOptInNotificationPreferencesAction statusPostOptInNotificationPreferencesAction, AvatarUpdatedAction avatarUpdatedAction, PrivateProcessingSettingAction privateProcessingSettingAction, NewsletterSavedInterestsAction newsletterSavedInterestsAction, AiThreadRenameAction aiThreadRenameAction, InteractiveMessageAction interactiveMessageAction, SettingsSyncAction settingsSyncAction, OutContactAction outContactAction, NctSaltSyncAction nctSaltSyncAction, BusinessBroadcastCampaignAction businessBroadcastCampaignAction, BusinessBroadcastInsightsAction businessBroadcastInsightsAction) {
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
        this.keyExpirationAction = keyExpirationAction;
        this.markChatAsReadAction = markChatAsReadAction;
        this.clearChatAction = clearChatAction;
        this.deleteChatAction = deleteChatAction;
        this.unarchiveChatsSetting = unarchiveChatsSetting;
        this.primaryFeatureAction = primaryFeatureAction;
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
        this.ugcBotAction = ugcBotAction;
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
        this.businessBroadcastAssociationAction = businessBroadcastAssociationAction;
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
        this.outContactAction = outContactAction;
        this.nctSaltSyncAction = nctSaltSyncAction;
        this.businessBroadcastCampaignAction = businessBroadcastCampaignAction;
        this.businessBroadcastInsightsAction = businessBroadcastInsightsAction;
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public Optional<? extends SyncAction<?>> action() {
        if (starAction != null) return Optional.of(starAction);
        if (contactAction != null) return Optional.of(contactAction);
        if (muteAction != null) return Optional.of(muteAction);
        if (pinAction != null) return Optional.of(pinAction);
        if (pushNameSetting != null) return Optional.of(pushNameSetting);
        if (quickReplyAction != null) return Optional.of(quickReplyAction);
        if (recentEmojiWeightsAction != null) return Optional.of(recentEmojiWeightsAction);
        if (labelEditAction != null) return Optional.of(labelEditAction);
        if (labelAssociationAction != null) return Optional.of(labelAssociationAction);
        if (localeSetting != null) return Optional.of(localeSetting);
        if (archiveChatAction != null) return Optional.of(archiveChatAction);
        if (deleteMessageForMeAction != null) return Optional.of(deleteMessageForMeAction);
        if (keyExpirationAction != null) return Optional.of(keyExpirationAction);
        if (markChatAsReadAction != null) return Optional.of(markChatAsReadAction);
        if (clearChatAction != null) return Optional.of(clearChatAction);
        if (deleteChatAction != null) return Optional.of(deleteChatAction);
        if (unarchiveChatsSetting != null) return Optional.of(unarchiveChatsSetting);
        if (primaryFeatureAction != null) return Optional.of(primaryFeatureAction);
        if (androidUnsupportedActions != null) return Optional.of(androidUnsupportedActions);
        if (agentAction != null) return Optional.of(agentAction);
        if (subscriptionAction != null) return Optional.of(subscriptionAction);
        if (userStatusMuteAction != null) return Optional.of(userStatusMuteAction);
        if (timeFormatAction != null) return Optional.of(timeFormatAction);
        if (nuxAction != null) return Optional.of(nuxAction);
        if (primaryVersionAction != null) return Optional.of(primaryVersionAction);
        if (stickerAction != null) return Optional.of(stickerAction);
        if (removeRecentStickerAction != null) return Optional.of(removeRecentStickerAction);
        if (chatAssignment != null) return Optional.of(chatAssignment);
        if (chatAssignmentOpenedStatus != null) return Optional.of(chatAssignmentOpenedStatus);
        if (pnForLidChatAction != null) return Optional.of(pnForLidChatAction);
        if (marketingMessageAction != null) return Optional.of(marketingMessageAction);
        if (marketingMessageBroadcastAction != null) return Optional.of(marketingMessageBroadcastAction);
        if (externalWebBetaAction != null) return Optional.of(externalWebBetaAction);
        if (privacySettingRelayAllCalls != null) return Optional.of(privacySettingRelayAllCalls);
        if (callLogAction != null) return Optional.of(callLogAction);
        if (ugcBotAction != null) return Optional.of(ugcBotAction);
        if (statusPrivacy != null) return Optional.of(statusPrivacy);
        if (botWelcomeRequestAction != null) return Optional.of(botWelcomeRequestAction);
        if (deleteIndividualCallLog != null) return Optional.of(deleteIndividualCallLog);
        if (labelReorderingAction != null) return Optional.of(labelReorderingAction);
        if (paymentInfoAction != null) return Optional.of(paymentInfoAction);
        if (customPaymentMethodsAction != null) return Optional.of(customPaymentMethodsAction);
        if (lockChatAction != null) return Optional.of(lockChatAction);
        if (chatLockSettings != null) return Optional.of(chatLockSettings);
        if (wamoUserIdentifierAction != null) return Optional.of(wamoUserIdentifierAction);
        if (privacySettingDisableLinkPreviewsAction != null) return Optional.of(privacySettingDisableLinkPreviewsAction);
        if (deviceCapabilities != null) return Optional.of(deviceCapabilities);
        if (noteEditAction != null) return Optional.of(noteEditAction);
        if (favoritesAction != null) return Optional.of(favoritesAction);
        if (merchantPaymentPartnerAction != null) return Optional.of(merchantPaymentPartnerAction);
        if (waffleAccountLinkStateAction != null) return Optional.of(waffleAccountLinkStateAction);
        if (usernameChatStartMode != null) return Optional.of(usernameChatStartMode);
        if (notificationActivitySettingAction != null) return Optional.of(notificationActivitySettingAction);
        if (lidContactAction != null) return Optional.of(lidContactAction);
        if (ctwaPerCustomerDataSharingAction != null) return Optional.of(ctwaPerCustomerDataSharingAction);
        if (paymentTosAction != null) return Optional.of(paymentTosAction);
        if (privacySettingChannelsPersonalisedRecommendationAction != null) return Optional.of(privacySettingChannelsPersonalisedRecommendationAction);
        if (businessBroadcastAssociationAction != null) return Optional.of(businessBroadcastAssociationAction);
        if (detectedOutcomesStatusAction != null) return Optional.of(detectedOutcomesStatusAction);
        if (maibaAiFeaturesControlAction != null) return Optional.of(maibaAiFeaturesControlAction);
        if (businessBroadcastListAction != null) return Optional.of(businessBroadcastListAction);
        if (musicUserIdAction != null) return Optional.of(musicUserIdAction);
        if (statusPostOptInNotificationPreferencesAction != null) return Optional.of(statusPostOptInNotificationPreferencesAction);
        if (avatarUpdatedAction != null) return Optional.of(avatarUpdatedAction);
        if (privateProcessingSettingAction != null) return Optional.of(privateProcessingSettingAction);
        if (newsletterSavedInterestsAction != null) return Optional.of(newsletterSavedInterestsAction);
        if (aiThreadRenameAction != null) return Optional.of(aiThreadRenameAction);
        if (interactiveMessageAction != null) return Optional.of(interactiveMessageAction);
        if (settingsSyncAction != null) return Optional.of(settingsSyncAction);
        if (outContactAction != null) return Optional.of(outContactAction);
        if (nctSaltSyncAction != null) return Optional.of(nctSaltSyncAction);
        if (businessBroadcastCampaignAction != null) return Optional.of(businessBroadcastCampaignAction);
        if (businessBroadcastInsightsAction != null) return Optional.of(businessBroadcastInsightsAction);
        return Optional.empty();
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setChatLockSettings(ChatLockSettings chatLockSettings) {
        this.chatLockSettings = chatLockSettings;
    }

    public void setDeviceCapabilities(DeviceCapabilities deviceCapabilities) {
        this.deviceCapabilities = deviceCapabilities;
    }
}
