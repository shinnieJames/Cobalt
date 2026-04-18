package com.github.auties00.cobalt.stream.notification;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.notification.account.NotificationAccountDispatcher;
import com.github.auties00.cobalt.stream.notification.business.NotificationBusinessDispatcher;
import com.github.auties00.cobalt.stream.notification.device.NotificationDeviceDispatcher;
import com.github.auties00.cobalt.stream.notification.group.NotificationGroupStreamHandler;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.node.Node;

/**
 * Routes incoming {@code <notification>} stanzas to the appropriate
 * category-specific dispatcher based on their {@code type} attribute.
 *
 * <p>WhatsApp multiplexes many distinct server-to-client notification flows
 * onto the single {@code <notification>} tag and distinguishes them via the
 * {@code type} attribute. Cobalt groups the known notification types into
 * four functional families and delegates to one dispatcher per family:
 * <ul>
 *   <li>Account family: {@code account_sync}, {@code contacts},
 *       {@code disappearing_mode}, {@code picture}, {@code privacy_token},
 *       {@code status}</li>
 *   <li>Business family: {@code business},
 *       {@code digital_commerce_subscription}, {@code fb:update},
 *       {@code mex}, {@code pay}</li>
 *   <li>Device family: {@code companion_reg_refresh}, {@code devices},
 *       {@code encrypt}, {@code hosted}, {@code link_code_companion_reg},
 *       {@code mediaretry}, {@code newsletter}, {@code psa},
 *       {@code registration}, {@code server}, {@code server_sync},
 *       {@code w:growth}, {@code waffle}</li>
 *   <li>Group family: {@code w:gp2}</li>
 * </ul>
 *
 * <p>Notifications whose {@code type} is not recognised are silently
 * discarded, matching WhatsApp Web's behaviour of ignoring unknown
 * notification categories rather than treating them as errors.
 *
 * @implNote WA Web dispatches {@code <notification>} stanzas inside
 * {@code WAWebHandleNotification} by switching on the {@code type} attribute
 * and invoking the matching per-type handler. Cobalt groups the per-type
 * handlers into four dispatchers (account, business, device, group) for
 * testability and to keep each file small.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleNotification")
public final class NotificationStreamHandler implements SocketStream.Handler {
    /**
     * Dispatcher for account-related notifications (contacts sync, profile
     * picture changes, privacy settings, disappearing mode, status updates).
     */
    private final NotificationAccountDispatcher accountHandler;

    /**
     * Dispatcher for business-related notifications (business profile,
     * digital commerce, Facebook updates, MEX payloads, payments).
     */
    private final NotificationBusinessDispatcher businessHandler;

    /**
     * Dispatcher for device-related notifications (companion registration,
     * linked-device list changes, pre-key bundles, newsletter updates,
     * server sync, waffle, and other administrative events).
     */
    private final NotificationDeviceDispatcher deviceHandler;

    /**
     * Dispatcher for group-related notifications ({@code w:gp2} stanzas
     * covering membership, subject, description, and settings changes).
     */
    private final NotificationGroupStreamHandler groupHandler;

    /**
     * Constructs a new notification dispatcher and wires each of the four
     * sub-dispatchers with their own dependencies.
     *
     * @param whatsapp                the WhatsApp client used by every
     *                                sub-dispatcher
     * @param deviceLinkingService the alt-device-linking service that owns
     *                                the pairing-code handshake state
     * @param lidMigrationService     service used to reconcile LID/PN
     *                                addressing during business-related
     *                                notifications
     * @param abPropsService          service used to retrieve feature flags
     *                                during device notification handling
     * @param deviceService           service used to reconcile linked-device
     *                                state during device and account
     *                                notifications
     */
    public NotificationStreamHandler(
            WhatsAppClient whatsapp,
            CompanionPairingService deviceLinkingService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            DeviceService deviceService
    ) {
        this.accountHandler = new NotificationAccountDispatcher(whatsapp, deviceService);
        this.businessHandler = new NotificationBusinessDispatcher(whatsapp, lidMigrationService);
        this.deviceHandler = new NotificationDeviceDispatcher(whatsapp, deviceLinkingService, abPropsService, deviceService);
        this.groupHandler = new NotificationGroupStreamHandler(whatsapp);
    }

    /**
     * Routes an incoming {@code <notification>} stanza to the dispatcher
     * whose family includes the stanza's {@code type} attribute.
     *
     * <p>Stanzas missing the {@code type} attribute or carrying an
     * unrecognised value are silently discarded.
     *
     * @param node the incoming {@code <notification>} stanza
     */
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

    /**
     * Resets the per-connection state of every sub-dispatcher. Invoked by
     * the socket stream when the underlying connection is torn down so that
     * the next connection starts from a clean slate.
     */
    @Override
    public void reset() {
        accountHandler.reset();
        businessHandler.reset();
        deviceHandler.reset();
        groupHandler.reset();
    }
}
