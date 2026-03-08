package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.LinkedHashSet;

public final class NotificationSyncStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER =
            System.getLogger(NotificationSyncStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public NotificationSyncStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "server_sync")) {
            return;
        }

        try {
            var changedCollections = new LinkedHashSet<SyncPatchType>();
            for (var collectionNode : node.getChildren("collection")) {
                var collectionName = collectionNode.getAttributeAsString("name", null);
                var collectionType = SyncPatchType.of(collectionName).orElse(null);
                if (collectionType == null) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring unknown server_sync collection {0}", collectionName);
                    continue;
                }

                changedCollections.add(collectionType);
            }

            if (!changedCollections.isEmpty()) {
                whatsapp.store().setSyncedWebAppState(false);
                whatsapp.pullWebAppState(changedCollections.toArray(SyncPatchType[]::new));
            } else {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "server_sync notification {0} did not include a known collection",
                        node.getAttributeAsString("id", "[missing-id]"));
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle server_sync notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
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
