package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

public final class NotificationProfileStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER =
            System.getLogger(NotificationProfileStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public NotificationProfileStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (node.hasDescription("notification") && node.hasAttribute("type", "picture")) {
            handlePicture(node);
            return;
        }

        if (node.hasDescription("notification") && node.hasAttribute("type", "status")) {
            handleAbout(node);
        }
    }

    private void handlePicture(Node node) {
        try {
            var actionNode = node.getChild("delete", "set", "request", "set_avatar")
                    .orElse(null);
            if (actionNode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Picture notification {0} has no known action child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var targetJid = actionNode.getAttributeAsJid("jid")
                    .or(() -> node.getAttributeAsJid("from"))
                    .map(Jid::withoutData)
                    .orElse(null);
            if (targetJid == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Picture notification {0} could not resolve a target jid",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var shouldNotify = false;
            if ("delete".equals(actionNode.description()) && isSelf(targetJid)) {
                whatsapp.store().setProfilePicture((URI) null);
                shouldNotify = true;
            } else if ("set".equals(actionNode.description())) {
                var picture = whatsapp.queryPicture(targetJid).orElse(null);
                if (isSelf(targetJid)) {
                    whatsapp.store().setProfilePicture(picture);
                }
                shouldNotify = true;
            } else if ("request".equals(actionNode.description())) {
                // WA Web acks this branch without mutating local profile state.
            } else if ("set_avatar".equals(actionNode.description())) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported set_avatar picture notification for {0}", targetJid);
            }

            if (shouldNotify) {
                fireProfilePictureChanged(targetJid);
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle picture notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleAbout(Node node) {
        try {
            var setNode = node.getChild("set").orElse(null);
            if (setNode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Status notification {0} has no set child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var from = node.getAttributeAsJid("from").map(Jid::toUserJid).orElse(null);
            if (from == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Status notification {0} is missing from",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            node.getAttributeAsString("notify")
                    .ifPresent(pushName -> updateContactChosenName(from, pushName));

            if (setNode.hasAttribute("hash")) {
                if (isSelf(from)) {
                    var refreshed = whatsapp.queryAbout(from).orElse(null);
                    var oldAbout = whatsapp.store().about().orElse(null);
                    if (!Objects.equals(oldAbout, refreshed)) {
                        whatsapp.store().setAbout(refreshed);
                        fireAboutChanged(oldAbout, refreshed);
                    }
                } else {
                    var refreshed = whatsapp.queryAbout(from).orElse(null);
                    upsertContactTextStatus(from, refreshed, null, null, null);
                }
                return;
            }

            var content = setNode.toContentString().orElse(null);
            if (content == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Status notification {0} for {1} has no content",
                        node.getAttributeAsString("id", "[missing-id]"), from);
                return;
            }

            var statusTimestamp = node.getAttributeAsLong("t", (Long) null);
            var statusInstant = statusTimestamp == null ? null : Instant.ofEpochSecond(statusTimestamp);
            if (!isSelf(from)) {
                upsertContactTextStatus(from, content, null, null, statusInstant);
                return;
            }

            var oldAbout = whatsapp.store().about().orElse(null);
            if (Objects.equals(oldAbout, content)) {
                return;
            }

            whatsapp.store().setAbout(content);
            fireAboutChanged(oldAbout, content);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle status notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    private void updateContactChosenName(Jid contactJid, String chosenName) {
        if (contactJid == null || chosenName == null || chosenName.isBlank()) {
            return;
        }

        var contact = whatsapp.store().findContactByJid(contactJid)
                .orElseGet(() -> whatsapp.store().addNewContact(contactJid.toUserJid()));
        contact.setChosenName(chosenName);
        whatsapp.store().addContact(contact);
    }

    private void fireProfilePictureChanged(Jid jid) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onProfilePictureChanged(whatsapp, jid));
        }
    }

    private void fireAboutChanged(String oldAbout, String newAbout) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onAboutChanged(whatsapp, oldAbout, newAbout));
        }
    }

    private ContactTextStatus upsertContactTextStatus(
            Jid contactJid,
            String text,
            String emoji,
            Integer ephemeralDurationSeconds,
            Instant lastUpdateTime
    ) {
        var canonicalJid = contactJid.toUserJid();
        var current = whatsapp.store()
                .findContactTextStatus(canonicalJid)
                .orElseGet(() -> new ContactTextStatusBuilder().build());
        current.setText(text)
                .setEmoji(emoji)
                .setEphemeralDurationSeconds(ephemeralDurationSeconds)
                .setLastUpdateTime(lastUpdateTime);
        whatsapp.store().addContactTextStatus(canonicalJid, current);
        notifyContactTextStatusChanged(canonicalJid, current);
        return current;
    }

    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
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
