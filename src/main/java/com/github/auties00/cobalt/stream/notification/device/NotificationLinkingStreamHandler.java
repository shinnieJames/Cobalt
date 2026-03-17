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

final class NotificationLinkingStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationLinkingStreamHandler.class.getName());
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "link_code_companion_reg",
            "companion_reg_refresh",
            "waffle",
            "hosted",
            "w:growth",
            "psa",
            "newsletter"
    );

    private final WhatsAppClient whatsapp;
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;

    NotificationLinkingStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = webVerificationHandler;
    }

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

    private void handleLinkCodeRefresh(Node node) {
        if (node.hasDescription("notification") && node.hasAttribute("type", "companion_reg_refresh")) {
            whatsapp.store().setAdvSecretKey(FastRandomUtils.randomByteArray(32));
        }

        var verificationValue = findVerificationValue(node);
        if (verificationValue != null && webVerificationHandler != null) {
            webVerificationHandler.handle(verificationValue);
        }
    }

    private void handleWaffle(Node node) {
        var firstChild = node.getChild().map(Node::description).orElse(null);
        if (firstChild == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported waffle notification {0}",
                    node.getAttributeAsString("id", "<missing>"));
            return;
        }

        var state = switch (firstChild) {
            case "state_suspended", "paused" -> WaffleAccountLinkStateAction.AccountLinkState.PAUSED;
            case "state_deleted", "unlinked" -> WaffleAccountLinkStateAction.AccountLinkState.UNLINKED;
            case "client_resync", "active" -> WaffleAccountLinkStateAction.AccountLinkState.ACTIVE;
            default -> null;
        };
        if (state != null) {
            whatsapp.store().setWaffleAccountLinkState(state);
        }
    }

    private void handleHosted(Node node) {
        var branch = firstChildDescription(node);
        switch (branch) {
            case "onboarding_status" -> {
                whatsapp.store().setHostedAutomationOnboarded(true);
                whatsapp.store().setDetectedOutcomesEnabled(true);
            }
            case "offboarding" -> {
                whatsapp.store().setHostedAutomationOnboarded(false);
                whatsapp.store().setDetectedOutcomesEnabled(false);
            }
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring hosted notification branch {0}",
                    branch);
        }
    }

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
    }

    private void handlePsa(Node node) {
        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring psa notification branch {0}",
                firstChildDescription(node));
    }

    private void handleNewsletter(Node node) {
        var newsletterJid = node.getAttributeAsJid("from").orElse(null);
        if (newsletterJid == null || !newsletterJid.hasNewsletterServer()) {
            return;
        }

        try {
            refreshNewsletter(newsletterJid);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh newsletter metadata for {0}: {1}",
                    newsletterJid,
                    throwable.getMessage());
        }

        if (node.hasChild("live_updates")) {
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

    private boolean isLikelyVerificationValue(String value) {
        return value != null
                && !value.isBlank()
                && value.length() >= 4
                && value.length() <= 1024;
    }

    private String firstChildDescription(Node node) {
        return node.getChild().map(Node::description).orElse(null);
    }

    private Newsletter ensureNewsletter(Jid newsletterJid) {
        return whatsapp.store()
                .findNewsletterByJid(newsletterJid)
                .orElseGet(() -> whatsapp.store().addNewNewsletter(newsletterJid));
    }

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

    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", node.description())
                .attribute("to", stanzaFrom)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant", null))
                .build());
    }
}
