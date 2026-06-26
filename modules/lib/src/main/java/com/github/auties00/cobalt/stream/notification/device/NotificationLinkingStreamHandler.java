package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedNewContactListener;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.stanza.smax.coexistence.SmaxCoexistenceOffboardingNotificationResponse;
import com.github.auties00.cobalt.stanza.smax.coexistence.SmaxCoexistenceOnboardingStatusNotificationResponse;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerMetadata;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.ChatMessageCountsEventBuilder;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles the companion-linking and per-feature push notification family dispatched by
 * {@link NotificationDeviceDispatcher}.
 *
 * <p>The {@code type} attribute selects the branch. The handler covers the companion pairing-code
 * refresh ({@code link_code_companion_reg}, {@code companion_reg_refresh}), the Meta account-linking
 * {@code waffle} event, the {@code hosted} CTWA coexistence notification, the {@code w:growth}
 * invite notification, the {@code psa} announcement notification, and the {@code newsletter}
 * live-update notification. Every supported stanza is acknowledged at the end of {@link #handle(Stanza)}
 * even when its mutation throws; unsupported types return without side-effects.
 *
 * @implNote This implementation collapses ten WA Web modules into one Cobalt class because they
 * share the same per-stanza ack pattern (read {@code type}, branch, ACK in {@code finally}) and so
 * live more comfortably under one type than under ten near-empty ones.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleCompanionReqRefreshNotification")
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingHandleNotification")
@WhatsAppWebModule(moduleName = "WAWebAccountLinkingNotificationHandler")
@WhatsAppWebModule(moduleName = "WAWebHandleHostedNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleGrowthNotification")
@WhatsAppWebModule(moduleName = "WAWebHandlePsa")
@WhatsAppWebModule(moduleName = "WAWebHandleQPSurfacesNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleQPPrefetchTimestampNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleWaChat")
@WhatsAppWebModule(moduleName = "WAWebHandleNewsletterNotification")
final class NotificationLinkingStreamHandler extends SocketStreamHandler.Concurrent {

    /**
     * Logs warnings about parse failures and debug messages about ignored sub-types.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationLinkingStreamHandler.class.getName());

    /**
     * Holds the notification {@code type} values routed to this handler by the dispatcher.
     *
     * <p>Consulted by {@link #handle(Stanza)} as the first-line filter; any stanza whose type is
     * outside this set returns without side-effects.
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "link_code_companion_reg",
            "companion_reg_refresh",
            "waffle",
            "hosted",
            "w:growth",
            "psa",
            "newsletter"
    );

    /**
     * Holds the {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names {@code ACCOUNT_LINKED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_ACCOUNT_LINKED = 1;

    /**
     * Holds the {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names {@code ACCOUNT_UNLINKED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_ACCOUNT_UNLINKED = 2;

    /**
     * Holds the {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names {@code STATE_DELETED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_STATE_DELETED = 4;

    /**
     * Holds the {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names {@code STATE_SUSPENDED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_STATE_SUSPENDED = 5;

    /**
     * Holds the {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names {@code CLIENT_RESYNC}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_CLIENT_RESYNC = 6;

    /**
     * Provides store reads, newsletter queries, message queries, and ack sends.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Drives the {@code primary_hello} and {@code refresh_code} steps of the pairing-code handshake
     * on the companion side.
     *
     * <p>May be {@code null} when the local account is not a pairing companion, in which case
     * {@link #handleLinkCodeRefresh(Stanza)} short-circuits the pairing-code branch.
     */
    private final CompanionPairingService deviceLinkingService;

    /**
     * Commits the {@code ChatMessageCounts} event after a successful invite-driven new-chat
     * creation in {@link #handleGrowth(Stanza)}.
     */
    private final WamService wamService;

    /**
     * Ships the post-processing {@code <ack class="notification">} stanza, reflecting the inbound
     * stanza's {@code type} attribute because this handler covers several notification types.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * <p>Called once by {@link NotificationDeviceDispatcher}.
     *
     * @param whatsapp             the {@link LinkedWhatsAppClient}
     * @param deviceLinkingService the {@link CompanionPairingService}, may be {@code null} when the local account is not a pairing companion
     * @param wamService           the {@link WamService}
     * @param ackSender            the {@link AckSender}
     */
    NotificationLinkingStreamHandler(
            LinkedWhatsAppClient whatsapp,
            CompanionPairingService deviceLinkingService,
            WamService wamService,
            AckSender ackSender
    ) {
        this.whatsapp = whatsapp;
        this.deviceLinkingService = deviceLinkingService;
        this.wamService = wamService;
        this.ackSender = ackSender;
    }

    /**
     * Routes the notification to its per-type branch and always sends the protocol-level ACK.
     *
     * <p>Stanzas whose type is outside {@link #SUPPORTED_TYPES} return without side-effects. For a
     * supported stanza the matching branch handler runs inside a {@code try} block, any failure is
     * caught and warning-logged, and the ACK is sent in the {@code finally} block so a valid stanza
     * is always acknowledged even when its mutation throws.
     *
     * @param stanza the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Stanza stanza) {
        var type = stanza.getAttributeAsString("type", null);
        if (!SUPPORTED_TYPES.contains(type)) {
            return;
        }

        try {
            switch (type) {
                case "link_code_companion_reg", "companion_reg_refresh" -> handleLinkCodeRefresh(stanza);
                case "waffle" -> handleWaffle(stanza);
                case "hosted" -> handleHosted(stanza);
                case "w:growth" -> handleGrowth(stanza);
                case "psa" -> handlePsa(stanza);
                case "newsletter" -> handleNewsletter(stanza);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle notification {0}/{1}: {2}",
                    type,
                    stanza.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(stanza);
        }
    }

    /**
     * Handles the companion-registration refresh and pairing-code handshake notifications.
     *
     * <p>The {@code companion_reg_refresh} branch generates a fresh 32-byte ADV secret on the
     * companion side and returns. The {@code link_code_companion_reg} branch reads the {@code stage}
     * attribute of the {@code <link_code_companion_reg>} child and drives the pairing-code handshake
     * step: {@code primary_hello} ships the finish IQ via
     * {@link CompanionPairingService#handlePrimaryHello(byte[], byte[], byte[])} once the wrapped
     * primary ephemeral public key, primary identity public key, and pairing ref are all present;
     * {@code refresh_code} updates the cached ref via
     * {@link CompanionPairingService#handleRefreshCode(byte[])}. Any other stage is debug-logged and
     * ignored, and the branch returns early when no pairing service, no child, or no stage is
     * available.
     *
     * @param stanza the notification stanza
     */
    private void handleLinkCodeRefresh(Stanza stanza) {
        if (stanza.hasAttribute("type", "companion_reg_refresh")) {
            whatsapp.store().signalStore().setAdvSecretKey(DataUtils.randomByteArray(32));
            return;
        }

        if (deviceLinkingService == null) {
            return;
        }

        var child = stanza.getChild("link_code_companion_reg").orElse(null);
        if (child == null) {
            return;
        }

        var stage = child.getAttributeAsString("stage", null);
        if (stage == null) {
            return;
        }

        var ref = child.getChild("link_code_pairing_ref")
                .flatMap(Stanza::toContentBytes)
                .orElse(null);

        switch (stage) {
            case "primary_hello" -> {
                var wrappedPrimaryEphemeralPub = child.getChild("link_code_pairing_wrapped_primary_ephemeral_pub")
                        .flatMap(Stanza::toContentBytes)
                        .orElse(null);
                var primaryIdentityPublic = child.getChild("primary_identity_pub")
                        .flatMap(Stanza::toContentBytes)
                        .orElse(null);
                if (wrappedPrimaryEphemeralPub == null || primaryIdentityPublic == null || ref == null) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Rejecting primary_hello notification missing required fields");
                    return;
                }
                try {
                    deviceLinkingService.handlePrimaryHello(wrappedPrimaryEphemeralPub, primaryIdentityPublic, ref);
                } catch (Throwable throwable) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Cannot complete alt-device-linking handshake: {0}", throwable.getMessage());
                }
            }
            case "refresh_code" -> deviceLinkingService.handleRefreshCode(ref);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring link_code_companion_reg notification with stage {0}", stage);
        }
    }

    /**
     * Applies the parsed {@code waffle} Meta account-linking event to the local linked-account
     * state.
     *
     * <p>Reads the {@code event} integer and the {@code client_resync} flag from the
     * {@code <notification_metadata>} child and transitions
     * {@link WaffleAccountLinkStateAction.AccountLinkState} accordingly:
     * {@link #WAFFLE_EVENT_STATE_SUSPENDED} maps to {@code PAUSED},
     * {@link #WAFFLE_EVENT_STATE_DELETED} maps to {@code UNLINKED}, and
     * {@link #WAFFLE_EVENT_CLIENT_RESYNC} maps to {@code ACTIVE}.
     * {@link #WAFFLE_EVENT_ACCOUNT_LINKED} and {@link #WAFFLE_EVENT_ACCOUNT_UNLINKED} transition to
     * {@code ACTIVE} only when the {@code client_resync} attribute is {@code "true"}, matching WA
     * Web which only re-runs the resync-state handler on the resync hint. A stanza with no
     * {@code <notification_metadata>} child, and any unhandled event integer, is debug-logged and
     * ignored.
     *
     * @param stanza the notification stanza
     */
    private void handleWaffle(Stanza stanza) {
        var metadata = stanza.getChild("notification_metadata").orElse(null);
        if (metadata == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring waffle notification without notification_metadata: {0}",
                    stanza.getAttributeAsString("id", "<missing>"));
            return;
        }

        var event = metadata.getAttributeAsInt("event", -1);
        var clientResync = "true".equals(metadata.getAttributeAsString("client_resync", null));

        switch (event) {
            case WAFFLE_EVENT_STATE_SUSPENDED ->
                    whatsapp.store().accountStore().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.PAUSED);
            case WAFFLE_EVENT_STATE_DELETED ->
                    whatsapp.store().accountStore().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.UNLINKED);
            case WAFFLE_EVENT_CLIENT_RESYNC ->
                    whatsapp.store().accountStore().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
            case WAFFLE_EVENT_ACCOUNT_UNLINKED -> {
                if (clientResync) {
                    whatsapp.store().accountStore().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            case WAFFLE_EVENT_ACCOUNT_LINKED -> {
                if (clientResync) {
                    whatsapp.store().accountStore().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unhandled waffle notification event {0}",
                    event);
        }
    }

    /**
     * Applies the parsed {@code hosted} CTWA coexistence notification to the local hosted-automation
     * flags.
     *
     * <p>An {@code <onboarding_status status="completed" product_surface="automation"/>} child
     * marks hosted automation as onboarded and enables detected outcomes; an
     * {@code <offboarding product_surface="automation"/>} child clears both flags. Any other child
     * is debug-logged and ignored.
     *
     * @implNote This implementation routes both child cases through the typed SMAX parsers
     * ({@link SmaxCoexistenceOnboardingStatusNotificationResponse},
     * {@link SmaxCoexistenceOffboardingNotificationResponse}) so the SMAX exports remain the single
     * source of truth for envelope and field validation. WA Web throws {@code SmaxParsingFailure} on
     * an unsupported child; Cobalt debug-logs and lets the outer ACK fire.
     *
     * @param stanza the notification stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHostedNotification",
            exports = "handleHostedNotification",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleHosted(Stanza stanza) {
        if (stanza.hasChild("onboarding_status")) {
            var onboardingStatus = SmaxCoexistenceOnboardingStatusNotificationResponse.of(stanza).orElse(null);
            if (onboardingStatus != null
                    && "completed".equals(onboardingStatus.onboardingStatusStatus())
                    && "automation".equals(onboardingStatus.onboardingStatusProductSurface())) {
                whatsapp.store().businessStore().setHostedAutomationOnboarded(true);
                whatsapp.store().businessStore().setDetectedOutcomesEnabled(true);
            }
            return;
        }

        if (stanza.hasChild("offboarding")) {
            var offboarding = SmaxCoexistenceOffboardingNotificationResponse.of(stanza).orElse(null);
            if (offboarding != null && "automation".equals(offboarding.offboardingProductSurface())) {
                whatsapp.store().businessStore().setHostedAutomationOnboarded(false);
                whatsapp.store().businessStore().setDetectedOutcomesEnabled(false);
            }
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring unsupported hosted notification: {0}",
                firstChildDescription(stanza));
    }

    /**
     * Materialises a contact and a chat for the invite recipient carried by a {@code w:growth}
     * notification.
     *
     * <p>Resolves the recipient JID from {@code <invite><receiver user="..."/></invite>} and
     * returns early when it is absent. An existing contact and chat are reused; otherwise a new
     * contact and chat are created. The contact's chosen name is refreshed from a name query (best
     * effort; failures are debug-logged), and {@link LinkedNewContactListener#onNewContact} fires when
     * the contact did not previously exist.
     *
     * @implNote This implementation commits a {@code ChatMessageCounts} WAM event with
     * {@code isInviteCreatedThread=true} when a new chat was created, matching WA Web's commit after
     * its {@code handleSingleMsg} call. Unlike WA Web, Cobalt does not synthesise the
     * {@code sender_invite} stub message because Cobalt's message-generation pipeline runs from a
     * different stream.
     *
     * @param stanza the notification stanza
     */
    private void handleGrowth(Stanza stanza) {
        var receiver = stanza.getChild("invite")
                .flatMap(invite -> invite.getChild("receiver"))
                .flatMap(receiverNode -> receiverNode.getAttributeAsJid("user"))
                .map(Jid::toUserJid)
                .orElse(null);
        if (receiver == null) {
            return;
        }

        var existed = whatsapp.store().contactStore().findContactByJid(receiver).isPresent();
        var contact = whatsapp.store().contactStore().findContactByJid(receiver)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(receiver));
        var chatCreated = whatsapp.store().chatStore().findChatByJid(receiver).isEmpty();
        whatsapp.store().chatStore().findChatByJid(receiver)
                .orElseGet(() -> whatsapp.store().chatStore().addNewChat(receiver));
        try {
            whatsapp.queryName(receiver).ifPresent(contact::setChosenName);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh invited contact metadata for {0}: {1}",
                    receiver,
                    throwable.getMessage());
        }
        whatsapp.store().contactStore().addContact(contact);

        if (!existed) {
            fireListeners(LinkedNewContactListener.class, listener -> listener.onNewContact(whatsapp, contact));
        }

        if (chatCreated) {
            wamService.commit(new ChatMessageCountsEventBuilder()
                    .isInviteCreatedThread(true)
                    .build());
        }
    }

    /**
     * Logs and drops {@code psa} notifications because Cobalt has no local quick-promotion or in-app
     * PSA campaign pipeline.
     *
     * <p>When the {@code from} attribute is the announcements account, the first child tag selects a
     * sub-case ({@code surfaces} for QP surfaces, {@code reset_smb_last_qp_prefetch_timestamp} for
     * the QP prefetch timestamp reset, or any other tag for the PSA campaign and wa_chat path); each
     * sub-case is debug-logged and dropped. Stanzas from any other sender are also debug-logged and
     * dropped.
     *
     * @implNote This implementation never produces a side-effect because none of the three sub-cases
     * (QP surfaces, QP prefetch timestamp, wa_chat) have a Cobalt store equivalent.
     *
     * @param stanza the notification stanza
     */
    private void handlePsa(Stanza stanza) {
        var from = stanza.getAttributeAsJid("from").orElse(null);
        if (from != null && from.equals(Jid.announcementsAccount())) {
            var firstChild = stanza.getChild().orElse(null);
            var firstChildTag = firstChild == null ? null : firstChild.description();
            if ("surfaces".equals(firstChildTag)) {
                // TODO: implement the quick-promotion surfaces pipeline once Cobalt has the in-app QP model.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring QP surfaces psa notification: {0}",
                        stanza.getAttributeAsString("id", "<missing>"));
                return;
            }
            if ("reset_smb_last_qp_prefetch_timestamp".equals(firstChildTag)) {
                // TODO: implement the QP prefetch timestamp reset once Cobalt has the in-app QP model.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring QP prefetch timestamp psa notification: {0}",
                        stanza.getAttributeAsString("id", "<missing>"));
                return;
            }
            // TODO: implement the in-app PSA campaign / wa_chat message pipeline.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring wa_chat/psa notification from PSA_JID: {0}",
                    firstChildTag);
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring psa notification: {0}",
                firstChildDescription(stanza));
    }

    /**
     * Refreshes newsletter metadata and recent messages when a {@code newsletter} notification
     * carries the {@code live_updates} child.
     *
     * <p>Returns early when the {@code from} JID is absent or is not a newsletter-server JID. When
     * the {@code <live_updates>} child is present, the newsletter metadata is refreshed and the most
     * recent twenty messages are re-queried; both steps are best effort and their failures are
     * debug-logged.
     *
     * @implNote This implementation processes synchronously on the calling virtual thread, whereas
     * WA Web serialises via a notification queue so a slow refresh cannot stack notifications on top
     * of each other. The Cobalt store's per-newsletter merge is idempotent and serial, so the queue
     * is not strictly required.
     *
     * @param stanza the notification stanza
     */
    private void handleNewsletter(Stanza stanza) {
        var newsletterJid = stanza.getAttributeAsJid("from").orElse(null);
        if (newsletterJid == null || !newsletterJid.hasNewsletterServer()) {
            return;
        }

        if (stanza.hasChild("live_updates")) {
            try {
                refreshNewsletter(newsletterJid);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh newsletter metadata for {0}: {1}",
                        newsletterJid,
                        throwable.getMessage());
            }

            try {
                whatsapp.queryNewsletterMessages(newsletterJid, 20);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh newsletter live updates for {0}: {1}",
                        newsletterJid,
                        throwable.getMessage());
            }
        }
    }

    /**
     * Returns the description (tag name) of the first child, or {@code null} when the stanza has no
     * children.
     *
     * <p>Used by {@link #handleHosted(Stanza)} and {@link #handlePsa(Stanza)} to log the unsupported
     * child without pulling its full content.
     *
     * @param stanza the parent stanza
     * @return the first child's description, or {@code null}
     */
    private String firstChildDescription(Stanza stanza) {
        return stanza.getChild().map(Stanza::description).orElse(null);
    }

    /**
     * Returns the newsletter for the given JID, creating a blank record when none exists.
     *
     * <p>Used only by {@link #refreshNewsletter(Jid)}.
     *
     * @param newsletterJid the newsletter JID
     * @return the matching {@link Newsletter}
     */
    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store().chatStore().findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().chatStore().addNewNewsletter(newsletterJid));
    }

    /**
     * Refreshes a newsletter's state, metadata, viewer metadata, unread count, and timestamp by
     * re-querying it from the server.
     *
     * <p>The existing viewer role is preserved across the refresh by passing it as the query
     * parameter (defaulting to {@link NewsletterViewerRole#GUEST} when the current role is
     * {@link NewsletterViewerRole#UNKNOWN}) so a non-guest viewer is not downgraded to guest on a
     * stale server response. When the re-query yields no newsletter, the cached record is left
     * unchanged.
     *
     * @param newsletterJid the newsletter JID to refresh
     */
    private void refreshNewsletter(Jid newsletterJid) {
        var newsletter = ensureNewsletter(newsletterJid);
        var role = newsletter.viewerMetadata()
                .map(NewsletterViewerMetadata::role)
                .filter(existingRole -> existingRole != NewsletterViewerRole.UNKNOWN)
                .orElse(NewsletterViewerRole.GUEST);
        var refreshed = whatsapp.queryNewsletter(newsletterJid, role).orElse(null);
        if (refreshed == null) {
            return;
        }

        newsletter.setState(refreshed.state().orElse(null));
        newsletter.setMetadata(refreshed.metadata().orElse(null));
        newsletter.setViewerMetadata(refreshed.viewerMetadata().orElse(null));
        newsletter.setUnreadMessagesCount(refreshed.unreadMessagesCount());
        newsletter.setTimestamp(refreshed.timestamp().orElse(null));
    }

    /**
     * Fans the given callback out to every registered listener of the given
     * type on its own virtual thread.
     *
     * <p>Used only by {@link #handleGrowth(Stanza)} for the {@code onNewContact} listener fan-out.
     *
     * @param type     the per-event listener interface to dispatch against
     * @param consumer the callback to invoke against each matching listener
     * @param <L>      the per-event listener interface
     */
    private <L extends WhatsAppListener> void fireListeners(Class<L> type, Consumer<L> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            if (type.isInstance(listener)) {
                var typed = type.cast(listener);
                Thread.startVirtualThread(() -> consumer.accept(typed));
            }
        }
    }

    /**
     * Sends the protocol-level ACK for the processed notification.
     *
     * <p>The ACK reflects the {@code type} attribute from the original stanza because this handler
     * covers several different notification types.
     *
     * @param stanza the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Stanza stanza) {
        ackSender.sendAck(AckClass.NOTIFICATION, stanza);
    }
}
