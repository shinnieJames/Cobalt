package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.handler.*;
import com.github.auties00.cobalt.wam.WamService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry mapping action names to their handlers.
 *
 * <p>Per WhatsApp Web {@code WAWebSyncdGetActionHandler}: the registry
 * stores all action handlers and provides lookup by action name and a
 * global max-supported-version query for version gating.
 *
 * <p>This registry owns the incoming-mutation routing only. Outgoing
 * mutation generation lives in the
 * {@code com.github.auties00.cobalt.sync.factory} package, where each
 * factory is instantiated independently by the caller that needs it.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdGetActionHandler")
public final class WebAppStateHandlerRegistry {
    /**
     * Map of action names to their registered handlers, used by the
     * incoming-mutation router.
     */
    private final Map<String, WebAppStateActionHandler> handlers;

    /**
     * Constructs a new handler registry and registers the default
     * handlers.
     *
     * @param abPropsService      the AB-props service injected into
     *                            handlers that gate behaviour on feature
     *                            flags
     * @param lidMigrationService the LID migration service injected into
     *                            handlers that observe LID 1:1 migration
     *                            state
     * @param wamService          the WAM telemetry service shared with
     *                            handlers that commit per-mutation
     *                            telemetry events
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "setActionHandlers", adaptation = WhatsAppAdaptation.ADAPTED)
    public WebAppStateHandlerRegistry(ABPropsService abPropsService, LidMigrationService lidMigrationService, WamService wamService) {
        this.handlers = new HashMap<>();
        registerDefaultHandlers(abPropsService, lidMigrationService, wamService);
    }

    /**
     * Registers all default action handlers that correspond to the WA Web
     * {@code WAWebCollectionHandlerActions.ActionHandlers} array, which
     * is passed to {@code WAWebSyncdGetActionHandler.setActionHandlers}
     * during the success handler flow in {@code WAWebHandleSuccess}.
     *
     * @param abPropsService      the AB-props service injected into
     *                            feature-gated handlers
     * @param lidMigrationService the LID migration service injected into
     *                            handlers that observe LID 1:1 migration
     * @param wamService          the WAM telemetry service to inject
     *                            into handlers that require it
     */
    private void registerDefaultHandlers(ABPropsService abPropsService, LidMigrationService lidMigrationService, WamService wamService) {
        var userStatusMuteHandler = new UserStatusMuteHandler();

        // Chat actions
        registerHandler(new ArchiveChatHandler()); // WAWebArchiveChatSync
        registerHandler(new PinChatHandler(wamService)); // WAWebPinChatSync
        registerHandler(new MuteChatHandler(abPropsService)); // WAWebMuteChatSync
        registerHandler(new MarkChatAsReadHandler()); // WAWebMarkChatAsReadSync
        registerHandler(new ClearChatHandler()); // WAWebClearChatSync
        registerHandler(new DeleteChatHandler()); // WAWebDeleteChatSync
        registerHandler(new LockChatHandler()); // WAWebLockChatSync

        // Message actions
        registerHandler(new StarMessageHandler()); // WAWebStarMessageSync
        registerHandler(new DeleteMessageForMeHandler()); // WAWebDeleteMessageForMeSync
        registerHandler(new InteractiveMessageHandler()); // WAWebInteractiveMessageSync

        // Contact actions
        registerHandler(new ContactActionHandler(abPropsService, userStatusMuteHandler)); // WAWebContactSync
        registerHandler(new LidContactHandler(abPropsService, userStatusMuteHandler)); // WAWebLidContactSync
        registerHandler(new PnForLidChatHandler(abPropsService)); // WAWebPnForLidChatSync
        registerHandler(new ShareOwnPnHandler(abPropsService)); // WAWebShareOwnPnSync

        // Label actions
        registerHandler(new LabelEditHandler()); // WAWebLabelSync
        registerHandler(new LabelAssociationHandler()); // WAWebLabelJidSync
        registerHandler(new LabelReorderingHandler()); // WAWebLabelReorderingSync

        // Business actions
        registerHandler(new QuickReplyHandler()); // WAWebQuickRepliesSync
        registerHandler(new AgentActionHandler()); // WAWebAgentSync
        registerHandler(new ChatAssignmentHandler()); // WAWebChatAssignmentSync
        registerHandler(new ChatAssignmentOpenedStatusHandler()); // WAWebChatAssignmentOpenedStatusSync
        registerHandler(new MarketingMessageHandler()); // WAWebPremiumMessageSync
        registerHandler(new MarketingMessageBroadcastHandler()); // WAWebPremiumMessageBroadcastSync
        registerHandler(new BusinessBroadcastListHandler()); // WAWebBroadcastListSync
        registerHandler(new BusinessBroadcastCampaignHandler()); // WAWebBroadcastCampaignSync
        registerHandler(new BusinessBroadcastInsightsHandler()); // WAWebBusinessBroadcastInsightsSync
        registerHandler(new CustomerDataHandler()); // WAWebCustomerDataSync
        registerHandler(new MerchantPaymentPartnerHandler(abPropsService)); // WAWebMerchantPaymentPartnerSync
        registerHandler(new NoteEditHandler()); // WAWebNoteSync

        // Sticker and avatar actions
        registerHandler(new FavoriteStickerHandler()); // WAWebStickersFavoriteSyncAction
        registerHandler(new RemoveRecentStickerHandler()); // WAWebStickersRemoveRecentSyncAction
        registerHandler(new AvatarUpdatedHandler(abPropsService)); // WAWebStickersAvatarUpdatedSyncAction

        // AI actions
        registerHandler(new AiThreadDeleteHandler()); // WAWebAiThreadDeleteSync
        registerHandler(new AiThreadRenameHandler()); // WAWebAiThreadRenameSync

        // Payment actions
        registerHandler(new PaymentInfoHandler(abPropsService)); // WAWebPaymentInfoSync
        registerHandler(new PaymentTosHandler(abPropsService)); // WAWebPaymentTosSync
        registerHandler(new CustomPaymentMethodsHandler(abPropsService)); // WAWebCustomPaymentMethodsSync

        // User preference actions
        registerHandler(userStatusMuteHandler); // WAWebUserStatusMuteSync
        registerHandler(new TimeFormatHandler()); // WAWebTimeFormatSync
        registerHandler(new FavoritesHandler()); // WAWebFavoritesSync

        // System actions
        registerHandler(new NuxActionHandler()); // WAWebNuxSync
        registerHandler(new PrimaryVersionHandler()); // WAWebPrimaryVersionSync
        registerHandler(new SentinelHandler()); // WAWebSentinelMutationSync
        registerHandler(new PrimaryFeatureHandler()); // WAWebPrimaryFeatureSync
        registerHandler(new AndroidUnsupportedActionsHandler()); // WAWebAndroidUnsupportedActionsSync
        registerHandler(new DeviceCapabilitiesHandler(lidMigrationService, wamService)); // WAWebDeviceCapabilitiesSync
        registerHandler(new BotWelcomeRequestHandler()); // WAWebBotWelcomeRequestSync
        registerHandler(new DetectedOutcomesStatusHandler()); // WAWebDetectedOutcomesStatusSync
        registerHandler(new WaffleAccountLinkStateHandler(abPropsService, wamService)); // WAWebWaffleAccountLinkStateSync
        registerHandler(new CtwaPerCustomerDataSharingHandler()); // WAWebCtwaPerCustomerDataSharingSync

        // Settings
        registerHandler(new PushNameSettingHandler(wamService)); // WAWebPushNameSync
        registerHandler(new LocaleSettingHandler()); // WAWebLocaleSettingSync
        registerHandler(new UnarchiveChatsSettingHandler()); // WAWebArchiveSettingSync
        registerHandler(new StatusPrivacyHandler()); // WAWebStatusPrivacySettingSync
        registerHandler(new DisableLinkPreviewsHandler()); // WAWebDisableLinkPreviewsSync
        registerHandler(new VoipRelayAllCallsHandler()); // WAWebVoipRelayAllCallsSettingSync
        registerHandler(new ChatLockSettingsHandler()); // WAWebChatLockSettingsSync
        registerHandler(new ExternalWebBetaHandler(abPropsService)); // WAWebExternalWebBetaSync
        registerHandler(new SettingsSyncHandler(abPropsService)); // WAWebSettingsSync
        registerHandler(new NctSaltSyncHandler()); // WAWebNctSaltSync
        registerHandler(new CallLogHandler()); // WAWebCallLogSync
        registerHandler(new SubscriptionHandler()); // WAWebSubscriptionsSyncV2Sync
        registerHandler(new OutContactHandler(abPropsService)); // WAWebOutContactSync
    }

    /**
     * Registers a handler for its declared action name.
     *
     * <p>Stores the handler keyed by its {@code actionName()}.
     *
     * @param handler the handler to register
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "setActionHandlers", adaptation = WhatsAppAdaptation.ADAPTED)
    public void registerHandler(WebAppStateActionHandler handler) {
        handlers.put(handler.actionName(), handler);
    }

    /**
     * Finds a handler by action name.
     *
     * <p>Looks up the handler map by action string and returns
     * {@code Optional.empty()} when no handler is registered for the
     * given action.
     *
     * @param actionName the action name to look up
     * @return the handler, or empty when no handler is registered
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "getActionHandler", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<WebAppStateActionHandler> findHandler(String actionName) {
        return Optional.ofNullable(handlers.get(actionName));
    }

    /**
     * Returns the maximum version supported by any registered handler.
     *
     * <p>Computes the maximum of all handler versions, used for fast
     * pre-filtering of mutations whose version exceeds any handler's
     * capability.
     *
     * @return the maximum supported version across all handlers
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "maxSupportedVersion", adaptation = WhatsAppAdaptation.ADAPTED)
    public int maxSupportedVersion() {
        return handlers.values().stream()
                .mapToInt(WebAppStateActionHandler::version)
                .max()
                .orElse(0);
    }
}
