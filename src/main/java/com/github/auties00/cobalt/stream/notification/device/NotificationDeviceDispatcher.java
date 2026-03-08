package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;

public final class NotificationDeviceDispatcher implements SocketStream.Handler {
    private final NotificationDeviceStreamHandler notificationDeviceHandler;
    private final NotificationLinkingStreamHandler notificationLinkingHandler;
    private final NotificationServerCryptoStreamHandler notificationServerCryptoHandler;
    private final NotificationSyncStreamHandler notificationSyncHandler;

    public NotificationDeviceDispatcher(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler,
            ABPropsService abPropsService,
            DeviceService deviceService
    ) {
        this.notificationDeviceHandler = new NotificationDeviceStreamHandler(whatsapp, deviceService);
        this.notificationLinkingHandler = new NotificationLinkingStreamHandler(whatsapp, webVerificationHandler);
        this.notificationServerCryptoHandler = new NotificationServerCryptoStreamHandler(whatsapp, abPropsService);
        this.notificationSyncHandler = new NotificationSyncStreamHandler(whatsapp);
    }

    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "devices" -> notificationDeviceHandler.handle(node);
            case "companion_reg_refresh", "hosted", "link_code_companion_reg", "newsletter", "psa", "w:growth", "waffle" ->
                    notificationLinkingHandler.handle(node);
            case "encrypt", "mediaretry", "registration", "server" ->
                    notificationServerCryptoHandler.handle(node);
            case "server_sync" ->
                    notificationSyncHandler.handle(node);
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        notificationDeviceHandler.reset();
        notificationLinkingHandler.reset();
        notificationServerCryptoHandler.reset();
        notificationSyncHandler.reset();
    }
}
