package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.List;

public final class NotificationDeviceStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER =
            System.getLogger(NotificationDeviceStreamHandler.class.getName());

    private final WhatsAppClient whatsapp;
    private final DeviceService deviceService;

    public NotificationDeviceStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.deviceService = deviceService;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "devices")) {
            return;
        }

        try {
            var userJid = node.getAttributeAsJid("from")
                    .map(Jid::toUserJid)
                    .orElse(null);
            if (userJid == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Skipping devices notification without from attribute");
                return;
            }

            node.getAttributeAsJid("lid")
                    .map(Jid::toUserJid)
                    .filter(lid -> !lid.equals(userJid))
                    .ifPresent(lid -> {
                        if (userJid.hasLidServer()) {
                            whatsapp.store().registerLidMapping(lid, userJid);
                        } else {
                            whatsapp.store().registerLidMapping(userJid, lid);
                        }
                    });

            var actionNode = node.getChild("add", "remove", "update")
                    .orElse(null);
            if (actionNode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Devices notification {0} has no action child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            switch (actionNode.description()) {
                case "add", "remove" -> deviceService.handleDeviceNotification(
                        normalizeDeviceActionNode(actionNode),
                        actionNode.description(),
                        userJid
                );
                case "update" -> deviceService.getDeviceLists(List.of(userJid), "notification", null, false);
                default -> LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unknown devices notification action {0}", actionNode.description());
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle devices notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private Node normalizeDeviceActionNode(Node actionNode) {
        if (actionNode.getChild("device-list").isPresent()
                || actionNode.getChild("key-index-list").isEmpty()) {
            return actionNode;
        }

        var deviceChildren = actionNode.getChildren("device");
        if (deviceChildren.isEmpty()) {
            return actionNode;
        }

        var deviceListNode = new com.github.auties00.cobalt.node.NodeBuilder()
                .description("device-list")
                .content(deviceChildren)
                .build();

        var rebuiltChildren = new java.util.ArrayList<Node>();
        rebuiltChildren.add(deviceListNode);
        actionNode.getChild("key-index-list").ifPresent(rebuiltChildren::add);

        return new com.github.auties00.cobalt.node.NodeBuilder()
                .description(actionNode.description())
                .content(rebuiltChildren)
                .build();
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
