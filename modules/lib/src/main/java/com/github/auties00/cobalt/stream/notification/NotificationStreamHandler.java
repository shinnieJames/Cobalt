package com.github.auties00.cobalt.stream.notification;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.control.OfflineNotificationsReporter;
import com.github.auties00.cobalt.stream.notification.account.NotificationAccountDispatcher;
import com.github.auties00.cobalt.stream.notification.business.NotificationBusinessDispatcher;
import com.github.auties00.cobalt.stream.notification.device.NotificationDeviceDispatcher;
import com.github.auties00.cobalt.stream.notification.group.NotificationGroupStreamHandler;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.wam.WamService;

/**
 * Dispatches inbound {@code <notification>} stanzas to the appropriate
 * category-specific handler based on their {@code type} attribute.
 *
 * @apiNote
 * Surfaces the {@code "notification"} arm of
 * {@code WAWebCommsHandleLoggedInStanza.handleLoggedInStanza}, where
 * every server-to-client out-of-band event (contact sync, profile
 * picture changes, device registration churn, payments, business
 * profile updates, group membership churn, app-state replication, etc.)
 * is routed by the value of {@code type}. Cobalt groups the 21 WA Web
 * notification types into four functional families and forwards each
 * family to its dedicated dispatcher:
 * <ul>
 *   <li>account-scoped events ({@code account_sync}, {@code contacts},
 *       {@code disappearing_mode}, {@code picture},
 *       {@code privacy_token}, {@code status}) go to
 *       {@link NotificationAccountDispatcher};</li>
 *   <li>business-scoped events ({@code business},
 *       {@code digital_commerce_subscription}, {@code fb:update},
 *       {@code mex}, {@code pay}) go to
 *       {@link NotificationBusinessDispatcher};</li>
 *   <li>device-scoped events ({@code companion_reg_refresh},
 *       {@code devices}, {@code encrypt}, {@code hosted},
 *       {@code link_code_companion_reg}, {@code mediaretry},
 *       {@code newsletter}, {@code psa}, {@code registration},
 *       {@code server}, {@code server_sync}, {@code w:growth},
 *       {@code waffle}) go to
 *       {@link NotificationDeviceDispatcher};</li>
 *   <li>group-scoped events ({@code w:gp2}) go to
 *       {@link NotificationGroupStreamHandler}.</li>
 * </ul>
 *
 * @implNote
 * This implementation collapses WA Web's flat 21-arm {@code switch}
 * into a four-family family tree to keep each sub-handler at a
 * manageable size and to share dependencies across thematically
 * related notification types. Unknown {@code type} values are silently
 * dropped here; WA Web instead routes the unknown stanza through a
 * generic NACK path which Cobalt centralises in its socket-stream
 * error model.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class NotificationStreamHandler implements SocketStream.Handler {
    /**
     * Sub-dispatcher handling notifications that affect the current
     * account's contact list, privacy settings, profile picture, or
     * status broadcasts.
     */
    private final NotificationAccountDispatcher accountHandler;

    /**
     * Sub-dispatcher handling notifications that target the business
     * profile, MEX payloads, Facebook-side updates, and payment events.
     */
    private final NotificationBusinessDispatcher businessHandler;

    /**
     * Sub-dispatcher handling notifications that affect device-level
     * state: companion registration, prekey churn, server sync,
     * newsletter membership, waffle account-linking, and the residual
     * miscellaneous WA Web events.
     */
    private final NotificationDeviceDispatcher deviceHandler;

    /**
     * Sub-dispatcher handling {@code w:gp2} notifications that report
     * group membership, subject, description, and settings changes.
     */
    private final NotificationGroupStreamHandler groupHandler;

    /**
     * Constructs the dispatcher and wires its four sub-dispatchers with
     * their respective dependencies.
     *
     * @apiNote
     * Invoked by the socket-stream wiring at client construction time;
     * downstream code never instantiates this handler directly.
     *
     * @implNote
     * This implementation eagerly creates each sub-dispatcher so that
     * routing in {@link #handle(Node)} is a single
     * {@code switch}-and-delegate; the constructor is the single place
     * where per-family dependency wiring lives.
     *
     * @param whatsapp                     the {@link WhatsAppClient}
     *                                     shared by every sub-dispatcher
     * @param deviceLinkingService         the
     *                                     {@link CompanionPairingService}
     *                                     that owns the pairing-code
     *                                     handshake state
     * @param lidMigrationService          the {@link LidMigrationService}
     *                                     used to reconcile LID and PN
     *                                     addressing during business
     *                                     notifications
     * @param abPropsService               the {@link ABPropsService} used
     *                                     to retrieve feature flags from
     *                                     the device sub-dispatcher
     * @param deviceService                the {@link DeviceService} used
     *                                     to reconcile linked-device
     *                                     state on the device and
     *                                     account sub-dispatchers
     * @param offlineNotificationsReporter the shared
     *                                     {@link OfflineNotificationsReporter}
     *                                     that accumulates per-collection
     *                                     offline {@code server_sync}
     *                                     counts for the
     *                                     {@code MdAppStateOfflineNotifications}
     *                                     WAM event; forwarded to the
     *                                     device sub-dispatcher
     * @param wamService                   the {@link WamService}
     *                                     telemetry sink forwarded to
     *                                     sub-dispatchers that commit
     *                                     notification-driven events
     * @param ackSender                    the {@link AckSender}
     *                                     forwarded to every
     *                                     sub-dispatcher for emitting
     *                                     the per-notification outbound
     *                                     {@code <ack>} stanza
     */
    public NotificationStreamHandler(
            WhatsAppClient whatsapp,
            CompanionPairingService deviceLinkingService,
            LidMigrationService lidMigrationService,
            ABPropsService abPropsService,
            DeviceService deviceService,
            OfflineNotificationsReporter offlineNotificationsReporter,
            WamService wamService,
            AckSender ackSender
    ) {
        this.accountHandler = new NotificationAccountDispatcher(whatsapp, deviceService, ackSender);
        this.businessHandler = new NotificationBusinessDispatcher(whatsapp, lidMigrationService, ackSender);
        this.deviceHandler = new NotificationDeviceDispatcher(whatsapp, deviceLinkingService, abPropsService, deviceService, offlineNotificationsReporter, wamService, ackSender);
        this.groupHandler = new NotificationGroupStreamHandler(whatsapp, wamService, ackSender);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Routes the {@code <notification>} stanza to the sub-dispatcher
     * whose family includes the value of its {@code type} attribute.
     * Stanzas lacking a {@code type} attribute or carrying an
     * unrecognised value are silently dropped.
     *
     * @implNote
     * This implementation handles {@code w:gp2} in this same dispatcher
     * even though WA Web wires that path through a separate handler;
     * Cobalt consolidates routing here for symmetry. Unknown type
     * values are dropped without emitting a NACK because the unrecognised
     * stanza NACK policy is enforced centrally by
     * {@link SocketStream}'s error model.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebCommsHandleLoggedInStanza",
            exports = "handleLoggedInStanza",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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
     * {@inheritDoc}
     *
     * @apiNote
     * Invoked by the socket-stream when the underlying connection is
     * torn down so that each sub-dispatcher's per-connection caches
     * and pending operations are cleared before the next connection
     * starts.
     *
     * @implNote
     * This implementation fans the call out to every sub-dispatcher in
     * a fixed order; sub-dispatchers are responsible for tolerating
     * being reset while a stanza-handling call is still mid-flight.
     */
    @Override
    public void reset() {
        accountHandler.reset();
        businessHandler.reset();
        deviceHandler.reset();
        groupHandler.reset();
    }
}
