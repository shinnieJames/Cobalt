package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.stream.control.OfflineNotificationsReporter;
import com.github.auties00.cobalt.wam.WamService;

/**
 * Routes inbound {@code <notification>} stanzas covering device-fanout,
 * companion linking, server-issued cryptographic notifications, and
 * server-driven app-state sync to the matching per-type handler.
 *
 * @apiNote
 * Cobalt's {@code NotificationStreamHandler} forwards every stanza whose
 * {@code type} attribute falls in the device branch
 * ({@code devices}, {@code companion_reg_refresh},
 * {@code link_code_companion_reg}, {@code waffle}, {@code hosted},
 * {@code w:growth}, {@code psa}, {@code newsletter}, {@code encrypt},
 * {@code mediaretry}, {@code server}, {@code registration},
 * {@code server_sync}) to this dispatcher. Embedders do not invoke it
 * directly.
 *
 * @implNote
 * This implementation groups four otherwise-unrelated sub-handlers
 * because the dispatcher in {@code NotificationStreamHandler} maps all
 * thirteen notification types to one of these four handlers; WA Web's
 * {@code WAWebCommsHandleLoggedInStanza} fans them out to thirteen
 * separate module-scoped handler functions.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class NotificationDeviceDispatcher implements SocketStream.Handler {
    /**
     * Handler for {@code type="devices"} notifications carrying device
     * add, remove, or update actions for a user's device list.
     */
    private final NotificationDeviceStreamHandler notificationDeviceHandler;

    /**
     * Handler for the companion-linking notification family
     * ({@code companion_reg_refresh}, {@code link_code_companion_reg},
     * {@code waffle}, {@code hosted}, {@code w:growth}, {@code psa},
     * {@code newsletter}).
     */
    private final NotificationLinkingStreamHandler notificationLinkingHandler;

    /**
     * Handler for the server-issued cryptographic notification family
     * ({@code encrypt}, {@code mediaretry}, {@code registration},
     * {@code server}).
     */
    private final NotificationServerCryptoStreamHandler notificationServerCryptoHandler;

    /**
     * Handler for {@code type="server_sync"} notifications announcing
     * app-state collection updates the client needs to pull.
     */
    private final NotificationSyncStreamHandler notificationSyncHandler;

    /**
     * Constructs the dispatcher and eagerly instantiates every
     * sub-handler with the shared dependencies.
     *
     * @apiNote
     * Called once during
     * {@link SocketStream} setup;
     * embedders do not construct it directly.
     *
     * @param whatsapp                     the {@link WhatsAppClient} forwarded to every sub-handler for store and node access
     * @param deviceLinkingService         the {@link CompanionPairingService} consumed by the linking handler for the pairing-code handshake
     * @param abPropsService               the {@link ABPropsService} consumed by the server-crypto handler for {@code server/abprops} resync
     * @param deviceService                the {@link DeviceService} consumed by the device-list handler for {@code add}/{@code remove}/{@code update} dispatch
     * @param offlineNotificationsReporter the {@link OfflineNotificationsReporter} consumed by the sync handler for the {@code MdAppStateOfflineNotifications} WAM event
     * @param wamService                   the {@link WamService} consumed by the linking and server-crypto handlers for the {@code GroupJoinC}, {@code WaOldCode}, and {@code ChatMessageCounts} events
     * @param ackSender                    the {@link AckSender} forwarded
     *                                     to every sub-handler for
     *                                     emitting the per-notification
     *                                     outbound {@code <ack>} stanza
     */
    public NotificationDeviceDispatcher(
            WhatsAppClient whatsapp,
            CompanionPairingService deviceLinkingService,
            ABPropsService abPropsService,
            DeviceService deviceService,
            OfflineNotificationsReporter offlineNotificationsReporter,
            WamService wamService,
            AckSender ackSender
    ) {
        this.notificationDeviceHandler = new NotificationDeviceStreamHandler(whatsapp, deviceService, ackSender);
        this.notificationLinkingHandler = new NotificationLinkingStreamHandler(whatsapp, deviceLinkingService, wamService, ackSender);
        this.notificationServerCryptoHandler = new NotificationServerCryptoStreamHandler(whatsapp, abPropsService, wamService, ackSender);
        this.notificationSyncHandler = new NotificationSyncStreamHandler(whatsapp, offlineNotificationsReporter, ackSender);
    }

    /**
     * Forwards {@code node} to the sub-handler whose category matches the
     * stanza's {@code type} attribute; drops stanzas this dispatcher does
     * not own.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler}.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanza", exports = "handleLoggedInStanza", adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Propagates {@link SocketStream.Handler#reset()} to every sub-handler.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler} when the
     * stream reset signal fires. Embedders do not call this directly.
     */
    @Override
    public void reset() {
        notificationDeviceHandler.reset();
        notificationLinkingHandler.reset();
        notificationServerCryptoHandler.reset();
        notificationSyncHandler.reset();
    }
}
