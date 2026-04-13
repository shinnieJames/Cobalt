package com.github.auties00.cobalt.stream.notification;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.notification.account.NotificationAccountDispatcher;
import com.github.auties00.cobalt.stream.notification.business.NotificationBusinessDispatcher;
import com.github.auties00.cobalt.stream.notification.device.NotificationDeviceDispatcher;
import com.github.auties00.cobalt.stream.notification.group.NotificationGroupStreamHandler;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.node.Node;

public final class NotificationStreamHandler implements SocketStream.Handler {
    private final NotificationAccountDispatcher accountHandler;
    private final NotificationBusinessDispatcher businessHandler;
    private final NotificationDeviceDispatcher deviceHandler;
    private final NotificationGroupStreamHandler groupHandler;

    public NotificationStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            DeviceService deviceService
    ) {
        this.accountHandler = new NotificationAccountDispatcher(whatsapp, deviceService);
        this.businessHandler = new NotificationBusinessDispatcher(whatsapp, lidMigrationService);
        this.deviceHandler = new NotificationDeviceDispatcher(whatsapp, webVerificationHandler, abPropsService, deviceService);
        this.groupHandler = new NotificationGroupStreamHandler(whatsapp);
    }

    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "account_sync", "contacts", "disappearing_mode", "picture", "privacy_token", "status" ->
                    accountHandler.handle(node);
            case "business", "digital_commerce_subscription", "fb:update", "mex", "pay" ->
                    businessHandler.handle(node);
            case "companion_reg_refresh", "devices", "encrypt", "hosted", "link_code_companion_reg",
                    "mediaretry", "newsletter", "psa", "registration", "server", "server_sync",
                    "w:growth", "waffle" ->
                    deviceHandler.handle(node);
            case "w:gp2" ->
                    groupHandler.handle(node);
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        accountHandler.reset();
        businessHandler.reset();
        deviceHandler.reset();
        groupHandler.reset();
    }
}
