package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

final class NotificationContactStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationContactStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    NotificationContactStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "contacts")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle contacts notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleNotification(Node node) {
        Node actionNode = null;
        for (var child : node.children()) {
            switch (child.description()) {
                case "invite" -> {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring contact invite notification {0}",
                            node.getAttributeAsString("id", "[missing-id]"));
                    return;
                }
                case "update", "add", "remove", "modify", "sync" -> {
                    actionNode = child;
                    break;
                }
                default -> {
                }
            }

            if (actionNode != null) {
                break;
            }
        }
        if (actionNode == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Contacts notification {0} has no supported action child",
                    node.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        switch (actionNode.description()) {
            case "update" -> handleUpdate(node, actionNode);
            case "add", "remove" -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported contacts action {0} for notification {1}",
                    actionNode.description(),
                    node.getAttributeAsString("id", "[missing-id]"));
            case "modify" -> handleModify(actionNode);
            case "sync" -> whatsapp.store().setSyncedContacts(false);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported contacts action {0}", actionNode.description());
        }
    }

    private void handleUpdate(Node notificationNode, Node updateNode) {
        if (updateNode.hasAttribute("hash") && !updateNode.hasAttribute("jid")) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring hash-only contacts update notification {0}",
                    notificationNode.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        var targetJid = updateNode.getAttributeAsJid("jid")
                .or(() -> notificationNode.getAttributeAsJid("from"))
                .map(Jid::toUserJid)
                .orElse(null);
        if (targetJid == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Contacts update notification {0} could not resolve target jid",
                    notificationNode.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        var contact = whatsapp.store()
                .findContactByJid(targetJid)
                .orElseGet(() -> whatsapp.store().addNewContact(targetJid));
        refreshContact(targetJid, contact);
    }

    private void handleModify(Node modifyNode) {
        var oldJid = modifyNode.getAttributeAsJid("old")
                .map(Jid::toUserJid)
                .orElse(null);
        var newJid = modifyNode.getAttributeAsJid("new")
                .map(Jid::toUserJid)
                .orElse(null);
        if (oldJid == null || newJid == null) {
            return;
        }

        var oldContact = whatsapp.store().findContactByJid(oldJid).orElse(null);
        var updated = whatsapp.store()
                .findContactByJid(newJid)
                .orElseGet(() -> whatsapp.store().addNewContact(newJid));
        if (oldContact != null) {
            updated.setChosenName(oldContact.chosenName().orElse(null))
                    .setFullName(oldContact.fullName().orElse(null))
                    .setShortName(oldContact.shortName().orElse(null))
                    .setUsername(oldContact.username().orElse(null));
        }

        modifyNode.getAttributeAsJid("new_lid")
                .map(Jid::toUserJid)
                .ifPresent(lid -> {
                    whatsapp.store().registerLidMapping(newJid, lid);
                    updated.setLid(lid);
                });
        whatsapp.store().addContact(updated);
        whatsapp.store().removeContact(oldJid);

        whatsapp.store().findChatByJid(oldJid).ifPresent(chat -> {
            chat.setNewJid(newJid);
            chat.setOldJid(oldJid);
        });

        fireContacts(java.util.List.of(updated));
    }

    private void refreshContact(Jid targetJid, Contact contact) {
        try {
            whatsapp.queryName(targetJid).ifPresent(contact::setChosenName);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh contact name for {0}: {1}",
                    targetJid,
                    throwable.getMessage());
        }

        whatsapp.store().addContact(contact);
    }

    private void fireContacts(java.util.SequencedCollection<Contact> contacts) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContacts(whatsapp, contacts));
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
