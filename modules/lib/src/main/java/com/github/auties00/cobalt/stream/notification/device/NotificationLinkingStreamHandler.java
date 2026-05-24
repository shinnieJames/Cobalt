package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.coexistence.SmaxCoexistenceOffboardingNotificationResponse;
import com.github.auties00.cobalt.node.smax.coexistence.SmaxCoexistenceOnboardingStatusNotificationResponse;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerMetadata;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.ChatMessageCountsEventBuilder;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles the companion-linking and per-feature push notification family
 * dispatched by {@link NotificationDeviceDispatcher}.
 *
 * @apiNote
 * One Cobalt handler covers seven WA Web modules:
 * {@code WAWebHandleCompanionReqRefreshNotification},
 * {@code WAWebAltDeviceLinkingHandleNotification},
 * {@code WAWebAccountLinkingNotificationHandler},
 * {@code WAWebHandleHostedNotification},
 * {@code WAWebHandleGrowthNotification},
 * {@code WAWebHandlePsa},
 * and {@code WAWebHandleNewsletterNotification} (with the QP-surface and
 * wa_chat sub-cases reachable from {@code WAWebCommsHandleLoggedInStanza}
 * dispatching to {@code WAWebHandlePsa}). The {@code type} attribute
 * selects the branch.
 *
 * @implNote
 * This implementation collapses the seven WA Web modules into one
 * Cobalt class because they share the same per-stanza ack pattern
 * (read {@code type}, branch, ACK in {@code finally}) and so live more
 * comfortably under one type than under seven near-empty ones.
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
final class NotificationLinkingStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about parse failures and debug messages
     * about ignored sub-types.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationLinkingStreamHandler.class.getName());

    /**
     * Set of notification {@code type} values routed to this handler by
     * the dispatcher.
     *
     * @apiNote
     * Used by {@link #handle(Node)} as the first-line filter; any
     * stanza whose type is outside this set returns without
     * side-effects.
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
     * The {@code notification_metadata.event} integer that WA Web's
     * {@code WAWebAccountLinkingConstants.AccountLinkingNotificationEvent}
     * enum names {@code ACCOUNT_LINKED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_ACCOUNT_LINKED = 1;

    /**
     * The {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names
     * {@code ACCOUNT_UNLINKED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_ACCOUNT_UNLINKED = 2;

    /**
     * The {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names
     * {@code STATE_DELETED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_STATE_DELETED = 4;

    /**
     * The {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names
     * {@code STATE_SUSPENDED}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_STATE_SUSPENDED = 5;

    /**
     * The {@code notification_metadata.event} integer that WA Web's
     * {@code AccountLinkingNotificationEvent} enum names
     * {@code CLIENT_RESYNC}.
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkingNotificationEvent", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final int WAFFLE_EVENT_CLIENT_RESYNC = 6;

    /**
     * The {@link WhatsAppClient} used for store reads, newsletter
     * queries, message queries, and ack sends.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link CompanionPairingService} used by
     * {@link #handleLinkCodeRefresh(Node)} to drive the
     * {@code primary_hello} and {@code refresh_code} steps of the
     * pairing-code handshake on the companion side.
     */
    private final CompanionPairingService deviceLinkingService;

    /**
     * The {@link WamService} used to commit the
     * {@code ChatMessageCounts} event after a successful invite-driven
     * new-chat creation in {@link #handleGrowth(Node)}.
     */
    private final WamService wamService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification">} stanza; the {@code type}
     * attribute is inherited from the inbound stanza since this
     * handler covers seven different notification types.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once by {@link NotificationDeviceDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp             the {@link WhatsAppClient}
     * @param deviceLinkingService the {@link CompanionPairingService}, may be {@code null} when the local account is not a pairing companion
     * @param wamService           the {@link WamService}
     * @param ackSender            the {@link AckSender}
     */
    NotificationLinkingStreamHandler(
            WhatsAppClient whatsapp,
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
     * Routes the notification to its per-type branch and always sends
     * the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationDeviceDispatcher}. Stanzas whose
     * type is outside {@link #SUPPORTED_TYPES} return without
     * side-effects; valid stanzas always get an ACK even when the
     * mutation throws.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (!SUPPORTED_TYPES.contains(type)) {
            return;
        }

        try {
            switch (type) {
                case "link_code_companion_reg", "companion_reg_refresh" -> handleLinkCodeRefresh(node);
                case "waffle" -> handleWaffle(node);
                case "hosted" -> handleHosted(node);
                case "w:growth" -> handleGrowth(node);
                case "psa" -> handlePsa(node);
                case "newsletter" -> handleNewsletter(node);
                default -> {
                }
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle notification {0}/{1}: {2}",
                    type,
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Handles {@code companion_reg_refresh} (regenerate ADV secret) and
     * {@code link_code_companion_reg} (process pairing-code handshake)
     * notifications.
     *
     * @apiNote
     * The {@code companion_reg_refresh} branch generates a fresh
     * 32-byte ADV secret on the companion side, mirroring WA Web's
     * {@code WAWebHandleCompanionReqRefreshNotification.d}. The
     * {@code link_code_companion_reg} branch reads the stage attribute
     * and drives the pairing-code handshake step
     * ({@code primary_hello} ships the finish IQ via
     * {@link CompanionPairingService#handlePrimaryHello(byte[], byte[], byte[])};
     * {@code refresh_code} updates the cached ref via
     * {@link CompanionPairingService#handleRefreshCode(byte[])}).
     *
     * @implNote
     * This implementation skips the pairing-code branch when the
     * embedder did not supply a pairing service, matching WA Web's
     * own short-circuit on missing handler state.
     *
     * @param node the notification stanza
     */
    private void handleLinkCodeRefresh(Node node) {
        if (node.hasAttribute("type", "companion_reg_refresh")) {
            whatsapp.store().setAdvSecretKey(DataUtils.randomByteArray(32));
            return;
        }

        if (deviceLinkingService == null) {
            return;
        }

        var child = node.getChild("link_code_companion_reg").orElse(null);
        if (child == null) {
            return;
        }

        var stage = child.getAttributeAsString("stage", null);
        if (stage == null) {
            return;
        }

        var ref = child.getChild("link_code_pairing_ref")
                .flatMap(Node::toContentBytes)
                .orElse(null);

        switch (stage) {
            case "primary_hello" -> {
                var wrappedPrimaryEphemeralPub = child.getChild("link_code_pairing_wrapped_primary_ephemeral_pub")
                        .flatMap(Node::toContentBytes)
                        .orElse(null);
                var primaryIdentityPublic = child.getChild("primary_identity_pub")
                        .flatMap(Node::toContentBytes)
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
     * Applies the parsed {@code waffle} (Meta account-linking) event to
     * the local linked-account state.
     *
     * @apiNote
     * Drives the in-app affordance for the
     * "your WhatsApp account is linked to a Meta account" UI surface.
     * State transitions: STATE_SUSPENDED -> PAUSED, STATE_DELETED ->
     * UNLINKED, CLIENT_RESYNC -> ACTIVE. ACCOUNT_LINKED and
     * ACCOUNT_UNLINKED conditionally transition to ACTIVE when the
     * {@code client_resync} attribute is {@code "true"}, matching WA
     * Web's
     * {@code WAWebAccountLinkingNotificationHandler.handleAccountLinkingNotification}
     * which only re-runs
     * {@code WAWebAccountLinkingHandler.handleResyncState} on the
     * resync hint.
     *
     * @param node the notification stanza
     */
    private void handleWaffle(Node node) {
        var metadata = node.getChild("notification_metadata").orElse(null);
        if (metadata == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring waffle notification without notification_metadata: {0}",
                    node.getAttributeAsString("id", "<missing>"));
            return;
        }

        var event = metadata.getAttributeAsInt("event", -1);
        var clientResync = "true".equals(metadata.getAttributeAsString("client_resync", null));

        switch (event) {
            case WAFFLE_EVENT_STATE_SUSPENDED ->
                    whatsapp.store().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.PAUSED);
            case WAFFLE_EVENT_STATE_DELETED ->
                    whatsapp.store().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.UNLINKED);
            case WAFFLE_EVENT_CLIENT_RESYNC ->
                    whatsapp.store().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
            case WAFFLE_EVENT_ACCOUNT_UNLINKED -> {
                if (clientResync) {
                    whatsapp.store().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            case WAFFLE_EVENT_ACCOUNT_LINKED -> {
                if (clientResync) {
                    whatsapp.store().setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unhandled waffle notification event {0}",
                    event);
        }
    }

    /**
     * Applies the parsed {@code hosted} CTWA coexistence notification
     * to the local hosted-automation flags.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleHostedNotification.handleHostedNotification}
     * which fires the
     * {@code ctwaDetectedOutcomeOnboardingStatusUpdate} frontend
     * event with {@code true} on
     * {@code <onboarding_status status="completed" product_surface="automation"/>}
     * and with {@code false} on
     * {@code <offboarding product_surface="automation"/>}.
     *
     * @implNote
     * This implementation routes both child cases through the typed
     * SMAX parsers
     * ({@link SmaxCoexistenceOnboardingStatusNotificationResponse},
     * {@link SmaxCoexistenceOffboardingNotificationResponse}) so the
     * SMAX exports remain the single source of truth for envelope and
     * field validation. WA Web throws
     * {@code SmaxParsingFailure} on an unsupported child; Cobalt
     * debug-logs and lets the outer ACK fire.
     *
     * @param node the notification stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHostedNotification",
            exports = "handleHostedNotification",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleHosted(Node node) {
        if (node.hasChild("onboarding_status")) {
            var onboardingStatus = SmaxCoexistenceOnboardingStatusNotificationResponse.of(node).orElse(null);
            if (onboardingStatus != null
                    && "completed".equals(onboardingStatus.onboardingStatusStatus())
                    && "automation".equals(onboardingStatus.onboardingStatusProductSurface())) {
                whatsapp.store().setHostedAutomationOnboarded(true);
                whatsapp.store().setDetectedOutcomesEnabled(true);
            }
            return;
        }

        if (node.hasChild("offboarding")) {
            var offboarding = SmaxCoexistenceOffboardingNotificationResponse.of(node).orElse(null);
            if (offboarding != null && "automation".equals(offboarding.offboardingProductSurface())) {
                whatsapp.store().setHostedAutomationOnboarded(false);
                whatsapp.store().setDetectedOutcomesEnabled(false);
            }
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring unsupported hosted notification: {0}",
                firstChildDescription(node));
    }

    /**
     * Materialises a contact and a chat for the invite recipient
     * carried by a {@code w:growth} notification.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleGrowthNotification._(receiverId, reason === "clicked_invite_link")}
     * which calls
     * {@code WAWebChatFindBridge.findLocal} / {@code WAWebCreateChat.createChat}
     * then synthesises a {@code sender_invite} stub message. Cobalt
     * persists the contact and the chat but does not synthesise the
     * stub message because Cobalt's message-generation pipeline runs
     * from a different stream. Fires
     * {@link WhatsAppClientListener#onNewContact} when the contact
     * was created.
     *
     * @implNote
     * This implementation also commits a
     * {@code ChatMessageCounts} WAM event with
     * {@code isInviteCreatedThread=true} when a new chat was created,
     * matching WA Web's commit after the
     * {@code handleSingleMsg} call.
     *
     * @param node the notification stanza
     */
    private void handleGrowth(Node node) {
        var receiver = node.getChild("invite")
                .flatMap(invite -> invite.getChild("receiver"))
                .flatMap(receiverNode -> receiverNode.getAttributeAsJid("user"))
                .map(Jid::toUserJid)
                .orElse(null);
        if (receiver == null) {
            return;
        }

        var existed = whatsapp.store().findContactByJid(receiver).isPresent();
        var contact = whatsapp.store()
                .findContactByJid(receiver)
                .orElseGet(() -> whatsapp.store().addNewContact(receiver));
        var chatCreated = whatsapp.store().findChatByJid(receiver).isEmpty();
        whatsapp.store()
                .findChatByJid(receiver)
                .orElseGet(() -> whatsapp.store().addNewChat(receiver));
        try {
            whatsapp.queryName(receiver).ifPresent(contact::setChosenName);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh invited contact metadata for {0}: {1}",
                    receiver,
                    throwable.getMessage());
        }
        whatsapp.store().addContact(contact);

        if (!existed) {
            fireListeners(listener -> listener.onNewContact(whatsapp, contact));
        }

        if (chatCreated) {
            wamService.commit(new ChatMessageCountsEventBuilder()
                    .isInviteCreatedThread(true)
                    .build());
        }
    }

    /**
     * Logs and drops {@code psa} notifications because Cobalt has no
     * local quick-promotion or in-app PSA campaign pipeline.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandlePsa} which only sends the ACK and lets the
     * frontend-side QP and PSA pipelines consume the stanza via
     * their own queues. When the {@code from} attribute is the
     * announcements account, WA Web inspects the first child tag to
     * route to {@code WAWebHandleQPSurfacesNotification},
     * {@code WAWebHandleQPPrefetchTimestampNotification}, or
     * {@code WAWebHandleWaChat}; Cobalt logs the chosen branch and
     * acknowledges.
     *
     * @implNote
     * This implementation never produces a side-effect because none
     * of the three sub-cases (QP surfaces, QP prefetch timestamp,
     * wa_chat) have a Cobalt store equivalent.
     *
     * @param node the notification stanza
     */
    private void handlePsa(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from != null && from.equals(Jid.announcementsAccount())) {
            var firstChild = node.getChild().orElse(null);
            var firstChildTag = firstChild == null ? null : firstChild.description();
            if ("surfaces".equals(firstChildTag)) {
                // TODO: implement the quick-promotion surfaces pipeline once Cobalt has the in-app QP model.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring QP surfaces psa notification: {0}",
                        node.getAttributeAsString("id", "<missing>"));
                return;
            }
            if ("reset_smb_last_qp_prefetch_timestamp".equals(firstChildTag)) {
                // TODO: implement the QP prefetch timestamp reset once Cobalt has the in-app QP model.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring QP prefetch timestamp psa notification: {0}",
                        node.getAttributeAsString("id", "<missing>"));
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
                firstChildDescription(node));
    }

    /**
     * Refreshes newsletter metadata and recent messages when a
     * {@code newsletter} notification carries the {@code live_updates}
     * child.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleNewsletterNotification} parser + dispatch to
     * {@code WAWebNewsletterHandleLiveUpdatesNotification.handleNewsletterLiveUpdatesNotification}.
     *
     * @implNote
     * This implementation processes synchronously on the calling
     * virtual thread; WA Web serialises via
     * {@code WAWebNewsletterNotificationQueue.enqueue} so a slow
     * refresh cannot stack notifications on top of each other. The
     * Cobalt store's per-newsletter merge is idempotent and serial,
     * so the queue is not strictly required.
     *
     * @param node the notification stanza
     */
    private void handleNewsletter(Node node) {
        var newsletterJid = node.getAttributeAsJid("from").orElse(null);
        if (newsletterJid == null || !newsletterJid.hasNewsletterServer()) {
            return;
        }

        if (node.hasChild("live_updates")) {
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
     * Returns the description (tag name) of the first child, or
     * {@code null} when the node has no children.
     *
     * @apiNote
     * Internal helper used by {@link #handleHosted(Node)} and
     * {@link #handlePsa(Node)} to log the unsupported child without
     * pulling its full content.
     *
     * @param node the parent node
     * @return the first child's description, or {@code null}
     */
    private String firstChildDescription(Node node) {
        return node.getChild().map(Node::description).orElse(null);
    }

    /**
     * Returns the newsletter for the given JID, creating a blank record
     * when none exists.
     *
     * @apiNote
     * Internal helper used only by {@link #refreshNewsletter(Jid)}.
     *
     * @param newsletterJid the newsletter JID
     * @return the matching {@link Newsletter}
     */
    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

    /**
     * Refreshes a newsletter's state, metadata, viewer metadata,
     * unread count, and timestamp by re-querying it from the server.
     *
     * @apiNote
     * Preserves the existing viewer role across the refresh by
     * passing it as the query parameter so a non-guest viewer is not
     * downgraded to guest on stale server responses.
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
     * Fans the given callback out to every registered listener on its
     * own virtual thread.
     *
     * @apiNote
     * Internal helper used only by {@link #handleGrowth(Node)} for
     * the {@code onNewContact} listener fan-out.
     *
     * @param consumer the callback to invoke against each listener
     */
    private void fireListeners(Consumer<WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Sends the protocol-level ACK for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; the {@code type} attribute is reflected from
     * the original stanza because this handler covers seven
     * different notification types and the WA Web ack-builders all
     * pin the type to their own module's value.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.sendAck(AckClass.NOTIFICATION, node);
    }
}
