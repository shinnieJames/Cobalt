package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class NotificationAccountDispatcher implements SocketStream.Handler {
    private final NotificationAccountStreamHandler accountHandler;
    private final NotificationContactStreamHandler contactHandler;
    private final NotificationDisappearingModeStreamHandler disappearingModeHandler;
    private final NotificationPrivacyStreamHandler privacyHandler;
    private final NotificationProfileStreamHandler profileHandler;

    public NotificationAccountDispatcher(WhatsAppClient whatsapp, DeviceService deviceService) {
        this.accountHandler = new NotificationAccountStreamHandler(whatsapp, deviceService);
        this.contactHandler = new NotificationContactStreamHandler(whatsapp);
        this.disappearingModeHandler = new NotificationDisappearingModeStreamHandler(whatsapp);
        this.privacyHandler = new NotificationPrivacyStreamHandler(whatsapp);
        this.profileHandler = new NotificationProfileStreamHandler(whatsapp);
    }

    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "account_sync" -> accountHandler.handle(node);
            case "contacts" -> contactHandler.handle(node);
            case "disappearing_mode" -> disappearingModeHandler.handle(node);
            case "privacy_token" -> privacyHandler.handle(node);
            case "picture", "status" -> profileHandler.handle(node);
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        accountHandler.reset();
        contactHandler.reset();
        disappearingModeHandler.reset();
        privacyHandler.reset();
        profileHandler.reset();
    }
}
