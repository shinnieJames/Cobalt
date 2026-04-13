package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class NotificationBusinessDispatcher implements SocketStream.Handler {
    private final NotificationBusinessStreamHandler businessHandler;
    private final NotificationMexStreamHandler mexHandler;
    private final NotificationPaymentStreamHandler paymentHandler;

    public NotificationBusinessDispatcher(WhatsAppClient whatsapp, LidMigrationService lidMigrationService) {
        this.businessHandler = new NotificationBusinessStreamHandler(whatsapp);
        this.mexHandler = new NotificationMexStreamHandler(whatsapp, lidMigrationService);
        this.paymentHandler = new NotificationPaymentStreamHandler(whatsapp);
    }

    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "business", "digital_commerce_subscription", "fb:update" -> businessHandler.handle(node);
            case "mex" -> mexHandler.handle(node);
            case "pay" -> paymentHandler.handle(node);
            default -> {
            }
        }
    }

    @Override
    public void reset() {
        businessHandler.reset();
        mexHandler.reset();
        paymentHandler.reset();
    }
}
