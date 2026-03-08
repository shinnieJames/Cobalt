package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

final class NotificationPrivacyStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationPrivacyStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    NotificationPrivacyStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "privacy_token")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle privacy_token notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleNotification(Node node) {
        var senderPn = getUserJid(node, "from");
        var senderLid = node.getAttributeAsJid("sender_lid")
                .map(Jid::toUserJid)
                .orElse(null);
        if (senderPn == null) {
            return;
        }

        var tokensNode = node.getChild("tokens").orElse(null);
        if (tokensNode == null) {
            return;
        }

        var senderTimestamp = getInstantAttribute(tokensNode, "t");
        if (senderLid != null) {
            whatsapp.store().registerLidMapping(senderPn, senderLid, senderTimestamp);
        }

        for (var tokenNode : tokensNode.getChildren("token")) {
            var type = tokenNode.getAttributeAsString("type", "");
            switch (type) {
                case "trusted_contact" -> handleTrustedContactToken(senderPn, senderLid, tokenNode);
                default -> LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported privacy token type {0}", type);
            }
        }
    }

    private void handleTrustedContactToken(Jid senderPn, Jid senderLid, Node tokenNode) {
        var content = tokenNode.toContentBytes().orElse(null);
        if (content == null || content.length == 0) {
            return;
        }

        updateChatToken(senderPn, content, tokenNode);
        if (senderLid != null) {
            updateChatToken(senderLid, content, tokenNode);
        }

        try {
            whatsapp.subscribeToPresence(senderLid != null ? senderLid : senderPn);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot resubscribe to presence for tc token sender {0}: {1}",
                    senderPn,
                    throwable.getMessage());
        }
    }

    private void updateChatToken(Jid chatJid, byte[] token, Node tokenNode) {
        var chat = whatsapp.store()
                .findChatByJid(chatJid)
                .orElseGet(() -> whatsapp.store().addNewChat(chatJid));
        var senderTimestamp = getInstantAttribute(tokenNode, "t");
        chat.setTcToken(token)
                .setTcTokenTimestamp(Instant.now());
        if (senderTimestamp != null) {
            chat.setTcTokenSenderTimestamp(senderTimestamp);
        }
    }

    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    private Instant getInstantAttribute(Node node, String key) {
        var seconds = node.getAttributeAsLong(key, (Long) null);
        return seconds == null || seconds <= 0 ? null : Instant.ofEpochSecond(seconds);
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
