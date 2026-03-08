package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

final class NotificationDisappearingModeStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationDisappearingModeStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    NotificationDisappearingModeStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "disappearing_mode")) {
            return;
        }

        try {
            var from = node.getAttributeAsJid("from")
                    .map(jid -> jid.toUserJid())
                    .orElse(null);
            var disappearingMode = node.getChild("disappearing_mode").orElse(null);
            if (from == null || disappearingMode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring malformed disappearing_mode notification {0}",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var duration = disappearingMode.getAttributeAsInt("duration", (Integer) null);
            var rawTimestamp = disappearingMode.getAttributeAsLong("t", (Long) null);
            var timestamp = rawTimestamp == null ? null : java.time.Instant.ofEpochSecond(rawTimestamp);
            if (duration == null) {
                return;
            }

            var chat = whatsapp.store()
                    .findChatByJid(from)
                    .orElseGet(() -> whatsapp.store().addNewChat(from));
            chat.setEphemeralExpiration(ChatEphemeralTimer.of(duration));
            if (timestamp != null) {
                chat.setEphemeralSettingTimestamp(timestamp);
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle disappearing_mode notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
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
