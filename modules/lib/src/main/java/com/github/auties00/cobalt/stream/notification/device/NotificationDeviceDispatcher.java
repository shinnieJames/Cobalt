package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Routes inbound device-category notification stanzas to specialised
 * handlers based on the stanza's {@code type} attribute.
 *
 * <p>This dispatcher owns one instance of each concrete device-category
 * handler: device list changes, companion linking, server-issued crypto
 * rotations, and server-driven app-state sync notifications.
 *
 * @implNote Adapts the WhatsApp Web dispatch that routes device
 *     notifications to {@code WAWebHandleDeviceNotification},
 *     {@code WAWebHandleLinkingDeviceNotification},
 *     {@code WAWebHandleServerCryptoNotification} and
 *     {@code WAWebHandleServerSyncNotification}.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleNotification")
public final class NotificationDeviceDispatcher implements SocketStream.Handler {
    /**
     * Handler for {@code devices} notifications that update the cached
     * device list for a user.
     */
    private final NotificationDeviceStreamHandler notificationDeviceHandler;

    /**
     * Handler for companion linking related notifications such as
     * {@code companion_reg_refresh}, {@code hosted}, {@code link_code_companion_reg},
     * {@code newsletter}, {@code psa}, {@code w:growth} and {@code waffle}.
     */
    private final NotificationLinkingStreamHandler notificationLinkingHandler;

    /**
     * Handler for server-issued crypto and error notifications such as
     * {@code encrypt}, {@code mediaretry}, {@code registration} and {@code server}.
     */
    private final NotificationServerCryptoStreamHandler notificationServerCryptoHandler;

    /**
     * Handler for {@code server_sync} notifications that request an
     * application-state sync from the primary device.
     */
    private final NotificationSyncStreamHandler notificationSyncHandler;

    /**
     * Constructs a new dispatcher and instantiates every sub-handler with
     * the shared dependencies.
     *
     * @param whatsapp                the non-{@code null} client providing store and network access
     * @param deviceLinkingService the alt-device-linking service consumed by the linking handler
     * @param abPropsService          the A/B props service consumed by the server-crypto handler
     * @param deviceService           the device service consumed by the device-list handler
     */
    public NotificationDeviceDispatcher(
            WhatsAppClient whatsapp,
            CompanionPairingService deviceLinkingService,
            ABPropsService abPropsService,
            DeviceService deviceService
    ) {
        this.notificationDeviceHandler = new NotificationDeviceStreamHandler(whatsapp, deviceService);
        this.notificationLinkingHandler = new NotificationLinkingStreamHandler(whatsapp, deviceLinkingService);
        this.notificationServerCryptoHandler = new NotificationServerCryptoStreamHandler(whatsapp, abPropsService);
        this.notificationSyncHandler = new NotificationSyncStreamHandler(whatsapp);
    }

    /**
     * Dispatches the incoming node to the appropriate device-category
     * handler based on the stanza's {@code type} attribute.
     *
     * @param node the incoming notification stanza
     * @implNote Mirrors the {@code type}-based switch in
     *     {@code WAWebHandleNotification.handleNotification} for the device
     *     category.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleNotification", exports = "handleNotification", adaptation = WhatsAppAdaptation.ADAPTED)
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

    /**
     * Fans out a reset call to every sub-handler so that any cached state
     * is discarded on a socket reconnect.
     *
     * @implNote Cobalt-specific lifecycle hook; WhatsApp Web handles this
     *     via module-level reset calls.
     */
    @Override
    public void reset() {
        notificationDeviceHandler.reset();
        notificationLinkingHandler.reset();
        notificationServerCryptoHandler.reset();
        notificationSyncHandler.reset();
    }
}
