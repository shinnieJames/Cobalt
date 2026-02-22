package com.github.auties00.cobalt.model.sync;

import com.alibaba.fastjson2.JSONArray;
import com.github.auties00.cobalt.model.sync.action.bot.AiThreadRenameAction;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.bot.UGCBotAction;
import com.github.auties00.cobalt.model.sync.action.business.*;
import com.github.auties00.cobalt.model.sync.action.call.CallLogAction;
import com.github.auties00.cobalt.model.sync.action.call.DeleteIndividualCallLogAction;
import com.github.auties00.cobalt.model.sync.action.chat.*;
import com.github.auties00.cobalt.model.sync.action.contact.*;
import com.github.auties00.cobalt.model.sync.action.device.*;
import com.github.auties00.cobalt.model.sync.action.media.*;
import com.github.auties00.cobalt.model.sync.action.payment.*;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingChannelsPersonalisedRecommendationAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.*;

import java.util.Collections;

/**
 * Represents a WhatsApp Web app-state action identified by a sync index.
 *
 * <p>Every implementing subtype defines a canonical action name through
 * an {@code ACTION_NAME} constant, and can serialize its index metadata
 * using {@link #toIndex(SyncActionArgs)}.
 *
 * @param <A> the type of index arguments required by this action
 */
public sealed interface SyncAction<A extends SyncActionArgs> permits
    AgentAction,
    AiThreadRenameAction,
    AndroidUnsupportedActions,
    ArchiveChatAction,
    AvatarUpdatedAction,
    BotWelcomeRequestAction,
    BroadcastListParticipantAction,
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
    KeyExpirationAction,
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
    PrimaryFeatureAction,
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
    UGCBotAction,
    UnarchiveChatsSetting,
    UserStatusMuteAction,
    UsernameChatStartModeAction,
    WaffleAccountLinkStateAction,
    WamoUserIdentifierAction {

    /**
     * Returns the canonical WhatsApp Web action name for this action type.
     *
     * <p>The name corresponds to the key used in the
     * {@code WASyncdConst.Actions} enumeration on the web client.
     *
     * @return the action name string
     */
    String actionName();

    /**
     * Returns the canonical WhatsApp Web action version for this action type.
     *
     * <p>The version corresponds to the value returned by
     * {@code getVersion()} on the web client sync action.
     *
     * @return the action version number
     */
    int actionVersion();

    /**
     * Serializes this action's full index to a JSON array string.
     *
     * <p>The index format follows WhatsApp Web conventions:
     * {@code ["actionName", ...indexArgs]}. The action name is always the
     * first element, followed by zero or more trailing arguments provided
     * by the given {@link SyncActionArgs}.
     *
     * @param args the index arguments to append after the action name
     * @return the serialized index payload as a JSON array string
     */
    default String toIndex(A args) {
        var array = new JSONArray();
        array.add(actionName());
        Collections.addAll(array, args.toIndexArgs());
        return array.toJSONString();
    }
}
