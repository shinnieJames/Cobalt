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
 * Dispatch table that maps every supported sync action name onto the
 * {@link WebAppStateActionHandler} that processes incoming mutations of
 * that action.
 *
 * @apiNote Owned by {@link WebAppStateService} and used by every incoming
 * patch the server delivers: the syncd response parser pulls each
 * decrypted mutation's action name out of its
 * {@code WAWebProtobufSyncAction.pb SyncActionValueSpec}, looks up the
 * handler here, and invokes its {@code applyMutation} hook. Outgoing
 * mutation generation does NOT live here; per-handler factories under
 * {@code com.github.auties00.cobalt.sync.factory} build the WAM-side
 * payloads that callers like
 * {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient}
 * enqueue for upload, so the registry stays a pure incoming-mutation
 * router. {@link #maxSupportedVersion()} is consumed by the response
 * parser to drop mutations whose declared version exceeds any handler's
 * capability, mirroring WA Web's
 * {@code WAWebSyncdGetActionHandler.maxSupportedVersion} fast-path.
 *
 * @implNote This implementation eagerly registers every default handler in
 * the constructor, matching the once-only initialisation pattern of WA
 * Web's {@code WAWebSyncdGetActionHandler.setActionHandlers}, which is
 * called by the success handler in {@code WAWebHandleSuccess} with the
 * full {@code WAWebCollectionHandlerActions.ActionHandlers} array.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdGetActionHandler")
public final class WebAppStateHandlerRegistry {
    /**
     * The action-name to handler map populated at construction time and
     * mutable through {@link #registerHandler(WebAppStateActionHandler)}.
     */
    private final Map<String, WebAppStateActionHandler> handlers;

    /**
     * Builds a registry pre-populated with every default handler.
     *
     * @apiNote Called once by {@link WebAppStateService} during its own
     * construction; the dependencies are forwarded to the handlers that
     * need them. Callers that want a custom dispatch table should
     * register additional or replacement handlers via
     * {@link #registerHandler(WebAppStateActionHandler)} after
     * construction.
     *
     * @param abPropsService      the AB-prop service forwarded to every
     *                            handler that gates behaviour on remote
     *                            configuration
     * @param lidMigrationService the LID migration service forwarded to
     *                            handlers that observe LID 1:1 migration
     *                            state
     * @param wamService          the WAM telemetry service forwarded to
     *                            handlers that emit per-mutation events
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "setActionHandlers", adaptation = WhatsAppAdaptation.ADAPTED)
    public WebAppStateHandlerRegistry(ABPropsService abPropsService, LidMigrationService lidMigrationService, WamService wamService) {
        this.handlers = new HashMap<>();
        registerDefaultHandlers(abPropsService, lidMigrationService, wamService);
    }

    /**
     * Instantiates every handler in the
     * {@code WAWebCollectionHandlerActions.ActionHandlers} catalog and
     * registers it under its declared action name.
     *
     * @apiNote Internal helper invoked exactly once from the constructor;
     * not exposed because callers that want to swap a handler should
     * call {@link #registerHandler(WebAppStateActionHandler)} after
     * construction instead of going through this method.
     *
     * @param abPropsService      the AB-prop service injected into every
     *                            feature-gated handler
     * @param lidMigrationService the LID migration service injected into
     *                            handlers that observe LID 1:1 migration
     * @param wamService          the WAM telemetry service injected into
     *                            handlers that emit per-mutation events
     */
    private void registerDefaultHandlers(ABPropsService abPropsService, LidMigrationService lidMigrationService, WamService wamService) {
        var userStatusMuteHandler = new UserStatusMuteHandler();

        registerHandler(new ArchiveChatHandler());
        registerHandler(new PinChatHandler(wamService));
        registerHandler(new MuteChatHandler(abPropsService));
        registerHandler(new MarkChatAsReadHandler());
        registerHandler(new ClearChatHandler());
        registerHandler(new DeleteChatHandler());
        registerHandler(new LockChatHandler());

        registerHandler(new StarMessageHandler());
        registerHandler(new DeleteMessageForMeHandler());
        registerHandler(new InteractiveMessageHandler());

        registerHandler(new ContactActionHandler(abPropsService, userStatusMuteHandler));
        registerHandler(new LidContactHandler(abPropsService, userStatusMuteHandler));
        registerHandler(new PnForLidChatHandler(abPropsService));
        registerHandler(new ShareOwnPnHandler(abPropsService));

        registerHandler(new LabelEditHandler());
        registerHandler(new LabelAssociationHandler());
        registerHandler(new LabelReorderingHandler());

        registerHandler(new QuickReplyHandler());
        registerHandler(new AgentActionHandler());
        registerHandler(new ChatAssignmentHandler());
        registerHandler(new ChatAssignmentOpenedStatusHandler());
        registerHandler(new MarketingMessageHandler());
        registerHandler(new MarketingMessageBroadcastHandler());
        registerHandler(new BusinessBroadcastListHandler());
        registerHandler(new BusinessBroadcastCampaignHandler());
        registerHandler(new BusinessBroadcastInsightsHandler());
        registerHandler(new CustomerDataHandler());
        registerHandler(new MerchantPaymentPartnerHandler(abPropsService));
        registerHandler(new NoteEditHandler());

        registerHandler(new FavoriteStickerHandler());
        registerHandler(new RemoveRecentStickerHandler());
        registerHandler(new AvatarUpdatedHandler(abPropsService));

        registerHandler(new AiThreadDeleteHandler());
        registerHandler(new AiThreadRenameHandler());

        registerHandler(new PaymentInfoHandler(abPropsService));
        registerHandler(new PaymentTosHandler(abPropsService));
        registerHandler(new CustomPaymentMethodsHandler(abPropsService));

        registerHandler(userStatusMuteHandler);
        registerHandler(new TimeFormatHandler());
        registerHandler(new FavoritesHandler());

        registerHandler(new NuxActionHandler());
        registerHandler(new PrimaryVersionHandler());
        registerHandler(new SentinelHandler());
        registerHandler(new PrimaryFeatureHandler());
        registerHandler(new AndroidUnsupportedActionsHandler());
        registerHandler(new DeviceCapabilitiesHandler(lidMigrationService, wamService));
        registerHandler(new BotWelcomeRequestHandler());
        registerHandler(new DetectedOutcomesStatusHandler());
        registerHandler(new WaffleAccountLinkStateHandler(abPropsService, wamService));
        registerHandler(new CtwaPerCustomerDataSharingHandler());

        registerHandler(new PushNameSettingHandler(wamService));
        registerHandler(new LocaleSettingHandler());
        registerHandler(new UnarchiveChatsSettingHandler());
        registerHandler(new StatusPrivacyHandler());
        registerHandler(new DisableLinkPreviewsHandler());
        registerHandler(new VoipRelayAllCallsHandler());
        registerHandler(new ChatLockSettingsHandler());
        registerHandler(new ExternalWebBetaHandler(abPropsService));
        registerHandler(new SettingsSyncHandler(abPropsService));
        registerHandler(new NctSaltSyncHandler());
        registerHandler(new CallLogHandler());
        registerHandler(new SubscriptionHandler());
        registerHandler(new OutContactHandler(abPropsService));
    }

    /**
     * Stores {@code handler} under its declared
     * {@link WebAppStateActionHandler#actionName()} key, replacing any
     * existing entry.
     *
     * @apiNote Public so test suites and integration cycles can register a
     * custom handler over a default one (the registry behaves like a
     * map: last writer wins per action name, mirroring WA Web's
     * {@code new Map(u.map(...))} pattern in
     * {@code WAWebSyncdGetActionHandler.getActionHandler}).
     *
     * @param handler the handler to register
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "setActionHandlers", adaptation = WhatsAppAdaptation.ADAPTED)
    public void registerHandler(WebAppStateActionHandler handler) {
        handlers.put(handler.actionName(), handler);
    }

    /**
     * Looks up a handler by action name.
     *
     * @apiNote Called by the syncd response parser for every decrypted
     * mutation; an empty result means the action name is unknown to
     * this version of Cobalt and the mutation is recorded as orphan,
     * matching WA Web's
     * {@code WAWebSyncdGetActionHandler.getActionHandler} miss path.
     *
     * @param actionName the action name read from the mutation's
     *                   {@code SyncActionValueSpec}
     * @return the registered handler wrapped in {@link Optional}, or
     *         {@link Optional#empty()} when no handler was registered
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "getActionHandler", adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<WebAppStateActionHandler> findHandler(String actionName) {
        return Optional.ofNullable(handlers.get(actionName));
    }

    /**
     * Returns the maximum
     * {@link WebAppStateActionHandler#version()} over every registered
     * handler.
     *
     * @apiNote Read by the syncd response parser to drop mutations whose
     * declared version exceeds any registered handler's capability,
     * matching WA Web's
     * {@code WAWebSyncdGetActionHandler.maxSupportedVersion} fast-path
     * filter. Returns {@code 0} when no handlers are registered.
     *
     * @return the highest supported action version across the registry
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdGetActionHandler", exports = "maxSupportedVersion", adaptation = WhatsAppAdaptation.ADAPTED)
    public int maxSupportedVersion() {
        return handlers.values().stream()
                .mapToInt(WebAppStateActionHandler::version)
                .max()
                .orElse(0);
    }
}
