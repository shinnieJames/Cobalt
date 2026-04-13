package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerMetadata;
import com.github.auties00.cobalt.model.newsletter.NewsletterViewerRole;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.util.FastRandomUtils;

import java.util.Set;

/**
 * Handles linking-related and miscellaneous notification stanzas dispatched by
 * {@link NotificationDeviceDispatcher}.
 * <p>
 * Each {@code handle*} method corresponds to one or more WA Web notification
 * handler modules: companion registration refresh, waffle account linking,
 * hosted coexistence, growth invite, PSA campaign, and newsletter live updates.
 *
 * @implNote WAWebHandleCompanionReqRefreshNotification,
 *     WAWebAccountLinkingNotificationHandler,
 *     WAWebHandleHostedNotification,
 *     WAWebHandleGrowthNotification,
 *     WAWebHandlePsa,
 *     WAWebHandleNewsletterNotification
 */
final class NotificationLinkingStreamHandler implements SocketStream.Handler {

    /**
     * Logger instance for this handler.
     *
     * @implNote NO_WA_BASIS
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationLinkingStreamHandler.class.getName());

    /**
     * Set of notification type attribute values routed to this handler by the dispatcher.
     *
     * @implNote WAWebCommsHandleLoggedInStanza.handleLoggedInStanza
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
     * WA Web notification_metadata event integer for {@code ACCOUNT_LINKED}.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkingNotificationEvent.ACCOUNT_LINKED
     */
    private static final int WAFFLE_EVENT_ACCOUNT_LINKED = 1;

    /**
     * WA Web notification_metadata event integer for {@code ACCOUNT_UNLINKED}.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkingNotificationEvent.ACCOUNT_UNLINKED
     */
    private static final int WAFFLE_EVENT_ACCOUNT_UNLINKED = 2;

    /**
     * WA Web notification_metadata event integer for {@code STATE_DELETED}.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkingNotificationEvent.STATE_DELETED
     */
    private static final int WAFFLE_EVENT_STATE_DELETED = 4;

    /**
     * WA Web notification_metadata event integer for {@code STATE_SUSPENDED}.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkingNotificationEvent.STATE_SUSPENDED
     */
    private static final int WAFFLE_EVENT_STATE_SUSPENDED = 5;

    /**
     * WA Web notification_metadata event integer for {@code CLIENT_RESYNC}.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkingNotificationEvent.CLIENT_RESYNC
     */
    private static final int WAFFLE_EVENT_CLIENT_RESYNC = 6;

    /**
     * The WhatsApp client instance used for store access, queries, and sending nodes.
     *
     * @implNote NO_WA_BASIS
     */
    private final WhatsAppClient whatsapp;

    /**
     * Optional web verification handler for link-code pairing notifications.
     *
     * @implNote NO_WA_BASIS
     */
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;

    /**
     * Creates a new linking notification stream handler.
     *
     * @param whatsapp               the WhatsApp client instance, must not be {@code null}
     * @param webVerificationHandler the web verification handler, may be {@code null}
     * @implNote NO_WA_BASIS
     */
    NotificationLinkingStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = webVerificationHandler;
    }

    /**
     * Dispatches a notification stanza to the appropriate handler method based on
     * the notification {@code type} attribute.
     *
     * @param node the notification stanza node, must not be {@code null}
     * @implNote WAWebCommsHandleLoggedInStanza.handleLoggedInStanza
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
                    // Exhaustive for SUPPORTED_TYPES.
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
     * Handles {@code companion_reg_refresh} and {@code link_code_companion_reg}
     * notification stanzas.
     * <p>
     * For {@code companion_reg_refresh}, regenerates the ADV secret key
     * (32 random bytes stored in the session store). For
     * {@code link_code_companion_reg}, extracts and forwards any
     * verification/pairing value to the web verification handler.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleCompanionReqRefreshNotification.handleCompanionReqRefreshNotification,
     *     ADAPTED: WAWebAltDeviceLinkingHandleNotification.handleAltDeviceLinkingNotification
     */
    private void handleLinkCodeRefresh(Node node) {
        // WAWebHandleCompanionReqRefreshNotification: regenerate ADV secret key
        if (node.hasDescription("notification") && node.hasAttribute("type", "companion_reg_refresh")) {
            whatsapp.store().setAdvSecretKey(FastRandomUtils.randomByteArray(32)); // ADAPTED: WA Web stores base64, Cobalt stores raw bytes
        }

        // ADAPTED: WAWebAltDeviceLinkingHandleNotification - Cobalt extracts verification value
        var verificationValue = findVerificationValue(node);
        if (verificationValue != null && webVerificationHandler != null) {
            webVerificationHandler.handle(verificationValue);
        }
    }

    /**
     * Handles {@code waffle} account-linking notification stanzas.
     * <p>
     * Parses the {@code notification_metadata} child and reads the integer
     * {@code event} attribute, then maps the event to the appropriate account
     * link state transition. Events {@code ACCOUNT_LINKED} and
     * {@code ACCOUNT_UNLINKED} conditionally trigger a resync when the
     * {@code client_resync} attribute is {@code "true"}.
     *
     * @param node the notification stanza node
     * @implNote WAWebAccountLinkingNotificationHandler.handleAccountLinkingNotification
     */
    private void handleWaffle(Node node) {
        // WAWebAccountLinkingNotificationHandler: parse notification_metadata child
        var metadata = node.getChild("notification_metadata").orElse(null);
        if (metadata == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring waffle notification without notification_metadata: {0}",
                    node.getAttributeAsString("id", "<missing>"));
            return;
        }

        // WAWebSmaxInWaffleWFNotificationRequest: event attr is int 1-7
        var event = metadata.getAttributeAsInt("event", -1);
        // WAWebSmaxInWaffleWFNotificationRequest: client_resync attr is optional string "true"/"false"
        var clientResync = "true".equals(metadata.getAttributeAsString("client_resync", null));

        // WAWebAccountLinkingNotificationHandler: switch on AccountLinkingNotificationEvent
        switch (event) {
            case WAFFLE_EVENT_STATE_SUSPENDED ->
                    // WAWebAccountLinkingHandler.handlePausedState
                    whatsapp.store().setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState.PAUSED);
            case WAFFLE_EVENT_STATE_DELETED ->
                    // WAWebAccountLinkingHandler.handleUnlinkedState
                    whatsapp.store().setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState.UNLINKED);
            case WAFFLE_EVENT_CLIENT_RESYNC ->
                    // WAWebAccountLinkingHandler.handleResyncState
                    whatsapp.store().setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
            case WAFFLE_EVENT_ACCOUNT_UNLINKED -> {
                // WAWebAccountLinkingNotificationHandler: only resync if client_resync === "true"
                if (clientResync) {
                    whatsapp.store().setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            case WAFFLE_EVENT_ACCOUNT_LINKED -> {
                // WAWebAccountLinkingNotificationHandler: only resync if client_resync === "true"
                if (clientResync) {
                    whatsapp.store().setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState.ACTIVE);
                }
            }
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unhandled waffle notification event {0}",
                    event);
        }
    }

    /**
     * Handles {@code hosted} coexistence notification stanzas.
     * <p>
     * For {@code onboarding_status} children, checks that the {@code status}
     * attribute equals {@code "completed"} and the {@code product_surface}
     * attribute equals {@code "automation"} before enabling detected outcomes.
     * For {@code offboarding} children, checks that the {@code product_surface}
     * attribute equals {@code "automation"} before disabling detected outcomes.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleHostedNotification.handleHostedNotification
     */
    private void handleHosted(Node node) {
        // WAWebHandleHostedNotification: try onboarding_status child first
        var onboardingStatus = node.getChild("onboarding_status").orElse(null);
        if (onboardingStatus != null) {
            // WASmaxInCoexistenceOnboardingStatusNotificationRequest: check status and product_surface
            var status = onboardingStatus.getAttributeAsString("status", null);
            var productSurface = onboardingStatus.getAttributeAsString("product_surface", null);
            // WAWebHandleHostedNotification: only act if status === "completed" and productSurface === "automation"
            if ("completed".equals(status) && "automation".equals(productSurface)) {
                // WAWebCTWADetectedOutcomeOnboardingStatusNotification.handleCTWADetectedOutcomeOnboardingStatusNotification(true)
                whatsapp.store().setHostedAutomationOnboarded(true);
                whatsapp.store().setDetectedOutcomesEnabled(true);
            }
            return;
        }

        // WAWebHandleHostedNotification: try offboarding child
        var offboarding = node.getChild("offboarding").orElse(null);
        if (offboarding != null) {
            // WASmaxInCoexistenceOffboardingNotificationRequest: check product_surface
            var productSurface = offboarding.getAttributeAsString("product_surface", null);
            // WAWebHandleHostedNotification: only act if productSurface === "automation"
            if ("automation".equals(productSurface)) {
                // WAWebCTWADetectedOutcomeOnboardingStatusNotification.handleCTWADetectedOutcomeOnboardingStatusNotification(false)
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
     * Handles {@code w:growth} notification stanzas by creating a contact and
     * chat for the invite receiver, if present.
     * <p>
     * Extracts the receiver JID from the {@code invite > receiver} child's
     * {@code user} attribute. If a new contact is created, fires the
     * {@code onNewContact} listener callback.
     *
     * @param node the notification stanza node
     * @implNote ADAPTED: WAWebHandleGrowthNotification.default
     */
    private void handleGrowth(Node node) {
        // WAWebHandleGrowthNotification: extract invite > receiver > user JID
        var receiver = node.getChild("invite")
                .flatMap(invite -> invite.getChild("receiver"))
                .flatMap(receiverNode -> receiverNode.getAttributeAsJid("user"))
                .map(Jid::toUserJid)
                .orElse(null);
        if (receiver == null) {
            return;
        }

        // ADAPTED: WAWebHandleGrowthNotification - Cobalt creates contact/chat directly
        var existed = whatsapp.store().findContactByJid(receiver).isPresent();
        var contact = whatsapp.store()
                .findContactByJid(receiver)
                .orElseGet(() -> whatsapp.store().addNewContact(receiver));
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

        // NO_WA_BASIS: Cobalt fires listener for new contact notification
        if (!existed) {
            fireListeners(listener -> listener.onNewContact(whatsapp, contact));
        }
    }

    /**
     * Handles {@code psa} (public service announcement) notification stanzas.
     * <p>
     * The WA Web handler parses campaign metadata (participant, campaign ID,
     * duration, messages) but only returns an acknowledgment without performing
     * any behavioral action. Cobalt mirrors this by logging the notification
     * and relying on the ack sent in the {@code finally} block of
     * {@link #handle(Node)}.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandlePsa.default
     */
    private void handlePsa(Node node) {
        // WAWebHandlePsa: parses but only returns ack, no behavioral action
        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring psa notification: {0}",
                firstChildDescription(node));
    }

    /**
     * Handles {@code newsletter} notification stanzas.
     * <p>
     * Validates that the {@code from} attribute is a newsletter JID, then
     * checks for a {@code live_updates} child. When present, refreshes
     * newsletter metadata and fetches recent messages.
     * <p>
     * The WA Web handler uses a per-newsletter promise queue for
     * serialization; Cobalt processes synchronously on a virtual thread
     * instead.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleNewsletterNotification.default
     */
    private void handleNewsletter(Node node) {
        // WAWebHandleNewsletterNotification: validate from is newsletter JID
        var newsletterJid = node.getAttributeAsJid("from").orElse(null);
        if (newsletterJid == null || !newsletterJid.hasNewsletterServer()) {
            return;
        }

        // WAWebHandleNewsletterNotification: only live_updates is supported
        if (node.hasChild("live_updates")) {
            // ADAPTED: Cobalt refreshes metadata then queries messages
            try {
                refreshNewsletter(newsletterJid);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Cannot refresh newsletter metadata for {0}: {1}",
                        newsletterJid,
                        throwable.getMessage());
            }

            // WAWebNewsletterHandleLiveUpdatesNotification.handleNewsletterLiveUpdatesNotification
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
     * Recursively searches the node tree for a verification/pairing code value.
     * <p>
     * Checks known attribute keys, then content text, then recurses into
     * children. Returns the first value that passes the
     * {@link #isLikelyVerificationValue(String)} heuristic.
     *
     * @param node the node to search
     * @return the verification value, or {@code null} if not found
     * @implNote ADAPTED: WAWebAltDeviceLinkingHandleNotification.handleAltDeviceLinkingNotification
     */
    private String findVerificationValue(Node node) {
        for (var key : new String[]{"code", "pairing_code", "ref", "pair-device-ref", "pair_device_ref", "link_code", "value"}) {
            var value = node.getAttributeAsString(key, null);
            if (isLikelyVerificationValue(value)) {
                return value;
            }
        }

        var text = node.toContentString().orElse(null);
        if (isLikelyVerificationValue(text)) {
            return text;
        }

        for (var child : node.children()) {
            var nested = findVerificationValue(child);
            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    /**
     * Returns whether the given string looks like a verification/pairing value.
     * <p>
     * A value is considered likely if it is non-null, non-blank, and between
     * 4 and 1024 characters in length.
     *
     * @param value the string to check, may be {@code null}
     * @return {@code true} if the value passes the heuristic
     * @implNote NO_WA_BASIS
     */
    private boolean isLikelyVerificationValue(String value) {
        return value != null
                && !value.isBlank()
                && value.length() >= 4
                && value.length() <= 1024;
    }

    /**
     * Returns the description (tag name) of the first child node, or
     * {@code null} if the node has no children.
     *
     * @param node the parent node
     * @return the first child's description, or {@code null}
     * @implNote NO_WA_BASIS
     */
    private String firstChildDescription(Node node) {
        return node.getChild().map(Node::description).orElse(null);
    }

    /**
     * Returns the newsletter for the given JID, creating a new one in the
     * store if it does not already exist.
     *
     * @param newsletterJid the newsletter JID
     * @return the existing or newly created newsletter
     * @implNote ADAPTED: WAWebHandleNewsletterNotification.default
     */
    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

    /**
     * Refreshes newsletter metadata by querying the server and updating the
     * local store with the returned state, metadata, viewer metadata,
     * unread count, and timestamp.
     *
     * @param newsletterJid the newsletter JID to refresh
     * @implNote ADAPTED: WAWebHandleNewsletterNotification.default
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
     * Fires a callback on all registered listeners, each on a separate virtual
     * thread.
     *
     * @param consumer the listener callback to fire
     * @implNote NO_WA_BASIS
     */
    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    /**
     * Sends a generic notification acknowledgment for the given stanza.
     * <p>
     * Constructs an {@code ack} node with the stanza's {@code id},
     * {@code class}, {@code type}, {@code to}, and {@code participant}
     * attributes.
     *
     * @param node the notification stanza to acknowledge
     * @implNote WAWebHandleCompanionReqRefreshNotification.handleCompanionReqRefreshNotification,
     *     WAWebHandlePsa.default,
     *     WAWebHandleGrowthNotification.default
     */
    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification") // WAWebHandleCompanionReqRefreshNotification, WAWebHandlePsa, WAWebHandleGrowthNotification: hardcoded "notification"
                .attribute("to", stanzaFrom)
                .attribute("type", node.getAttributeAsString("type", null)) // Each WA Web handler hardcodes its own type; since this handler dispatches multiple types, we read dynamically
                .build());
    }
}
