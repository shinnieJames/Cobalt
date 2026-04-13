package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles incoming device notification stanzas (type="devices").
 * <p>
 * Parses add, remove, and update device notifications, registers LID-PN mappings,
 * and dispatches to {@link DeviceService} for add/remove or device list sync for updates.
 * Processes notifications for both primary and secondary (LID/PN) user identities,
 * mirroring WA Web's dual-wid processing pattern.
 *
 * @implNote WAWebHandleDeviceNotification.handleDevicesNotification: main entry point for
 * device notification processing. Parses the notification stanza, builds an ack, registers
 * LID-PN mappings, and dispatches to WAWebAdvHandlerApi.handleADVDeviceNotification (for
 * add/remove) or WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob (for update).
 */
public final class NotificationDeviceStreamHandler implements SocketStream.Handler {

    /**
     * Logger for device notification operations.
     *
     * @implNote WAWebHandleDeviceNotification: uses WALogger for WARN/ERROR logging.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationDeviceStreamHandler.class.getName());

    /**
     * The WhatsApp client used for sending ack stanzas and accessing the store.
     *
     * @implNote WAWebHandleDeviceNotification: uses WAWap, WAWebCommsWapMd, WAWebDBCreateLidPnMappings
     * via module-level imports.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The device service used for handling add/remove notifications and syncing device lists.
     *
     * @implNote WAWebHandleDeviceNotification: delegates to WAWebAdvHandlerApi.handleADVDeviceNotification
     * and WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob.
     */
    private final DeviceService deviceService;

    /**
     * Constructs a new device notification stream handler.
     *
     * @param whatsapp      the WhatsApp client
     * @param deviceService the device service for device list operations
     * @implNote WAWebHandleDeviceNotification: module-level dependencies include WAWebAdvHandlerApi,
     * WAWebApiContact, WAWebApiPendingDeviceSync, WAWebDBCreateLidPnMappings, WAWebOfflineHandler,
     * WAWebSyncDeviceAdvDeviceListJob, etc.
     */
    public NotificationDeviceStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.deviceService = deviceService;
    }

    /**
     * Handles an incoming device notification stanza.
     * <p>
     * Validates the stanza is a notification with type="devices", extracts the user JID from the
     * {@code from} attribute and the optional LID from the {@code lid} attribute, registers
     * LID-PN mappings, and dispatches to the appropriate handler based on the action type
     * (add, remove, or update). Both the primary user and secondary (LID/PN) identity are
     * processed when available. An ack stanza is always sent to the server.
     *
     * @param node the incoming notification stanza node
     * @implNote WAWebHandleDeviceNotification.handleDevicesNotification: parses stanza via
     * WADeprecatedWapParser, builds ack with USER_JID(from), registers lid-pn mappings via
     * WAWebDBCreateLidPnMappings.createLidPnMappings, then dispatches each wid entry to
     * WAWebAdvHandlerApi.handleADVDeviceNotification (add/remove) or
     * WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob (update).
     */
    @Override
    public void handle(Node node) {
        // WAWebHandleDeviceNotification.h: assertTag("notification"), assertAttr("type", "devices")
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "devices")) {
            return;
        }

        // WAWebHandleDeviceNotification.h: extract user from "from" attribute
        // WAWebJidToWid.deviceJidToUserWid: converts device JID to user-level JID
        var userJid = node.getAttributeAsJid("from")
                .map(Jid::toUserJid)
                .orElse(null);
        if (userJid == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping devices notification without from attribute"); // WAWebHandleDeviceNotification.h: parse error
            return;
        }

        // WAWebHandleDeviceNotification.h: extract lidUser from "lid" attribute
        // WAWebJidToWid.lidDeviceJidToUserLid: converts lid device JID to user LID
        var lidUser = node.getAttributeAsJid("lid")
                .map(Jid::toUserJid)
                .orElse(null);

        // WAWebHandleDeviceNotification.h: determine action type from child node
        var actionNode = node.getChild("remove")
                .or(() -> node.getChild("add"))
                .or(() -> node.getChild("update"))
                .orElse(null);
        if (actionNode == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[devices] notif missing \"remove\" or \"add\" node"); // WAWebHandleDeviceNotification.h: parse error
            return;
        }
        var actionType = actionNode.description(); // WAWebHandleDeviceNotification.h: r (type)

        // WAWebHandleDeviceNotification.h: extract hash for update type
        var hash = actionType.equals("update")
                ? actionNode.getAttributeAsString("hash", null)
                : null;

        // WAWebHandleDeviceNotification.C: build list of wids to process
        // l = {wid: a.user, ...}
        // s = a.user.isLid() ? toPn(a.user) : a.lidUser
        var secondaryJid = userJid.hasLidServer() // WAWebHandleDeviceNotification.C: a.user.isLid()
                ? whatsapp.store().getPhoneNumberByLid(userJid).orElse(null) // WAWebLidMigrationUtils.toPn
                : lidUser;

        // WAWebHandleDeviceNotification.C: register LID-PN mappings
        // b = []; if (a.lidUser != null && a.user != null) b.push({lid: a.lidUser, pn: a.user})
        if (lidUser != null && userJid != null) { // WAWebHandleDeviceNotification.C
            whatsapp.store().registerLidMapping(userJid, lidUser); // WAWebDBCreateLidPnMappings.createLidPnMappings({lid: a.lidUser, pn: a.user})
        }

        // WAWebHandleDeviceNotification.C: build entries [l, y].filter(Boolean)
        var entries = new ArrayList<Jid>();
        entries.add(userJid); // WAWebHandleDeviceNotification.C: l = {wid: a.user, ...}
        if (secondaryJid != null) {
            entries.add(secondaryJid); // WAWebHandleDeviceNotification.C: y = {wid: s, ...}
        }

        // WAWebHandleDeviceNotification.C: process each entry
        for (var entryJid : entries) {
            try {
                processDeviceEntry(entryJid, actionType, actionNode, hash); // WAWebHandleDeviceNotification.C: Promise.all(C.map(...))
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleDevicesNotification - {0} error: {1}",
                        actionType, throwable.getMessage()); // WAWebHandleDeviceNotification.C: catch(e) WARN
            }
        }

        // WAWebHandleDeviceNotification.C: return ack stanza i
        sendNotificationAck(node, userJid); // WAWebHandleDeviceNotification.C: return i (ack)
    }

    /**
     * Processes a single device notification entry for a given user JID.
     * <p>
     * For add/remove actions, delegates to {@link DeviceService#handleDeviceNotification}.
     * For update actions, triggers a device list sync via {@link DeviceService#getDeviceLists}.
     *
     * @param entryJid   the user JID to process the notification for
     * @param actionType the action type ("add", "remove", or "update")
     * @param actionNode the action child node from the notification stanza
     * @param hash       the hash attribute value for update notifications, or {@code null}
     * @implNote WAWebHandleDeviceNotification.C: inner async function that dispatches based on
     * a.type. For add/remove: calls WAWebAdvHandlerApi.handleADVDeviceNotification({wid, devices, type}).
     * For update: calls WAWebApiContact.getContactRecordByHash(hash) then
     * WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob([wid], "notification", null).
     */
    private void processDeviceEntry(Jid entryJid, String actionType, Node actionNode, String hash) {
        switch (actionType) {
            case "add", "remove" -> { // WAWebHandleDeviceNotification.C: if (a.type === g.add) / if (a.type === g.remove)
                deviceService.handleDeviceNotification(
                        normalizeDeviceActionNode(actionNode),
                        actionType,
                        entryJid
                ); // WAWebAdvHandlerApi.handleADVDeviceNotification({wid, devices, type})
            }
            case "update" -> { // WAWebHandleDeviceNotification.C: if (a.type === g.update)
                // WAWebHandleDeviceNotification.C: var s = getContactRecordByHash(nullthrows(n))
                // Cobalt does not maintain a contact hash index; instead, we sync the device list
                // directly for the user JID, which is functionally equivalent since the from
                // attribute identifies the user whose devices changed.
                if (hash == null) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "[devices] update notification missing hash for {0}", entryJid); // ADAPTED: nullthrows(n) in WA Web would throw
                    return;
                }
                deviceService.getDeviceLists(List.of(entryJid), "notification", null, false); // WAWebSyncDeviceAdvDeviceListJob.syncDeviceListJob([wid], "notification", null)
            }
            default -> LOGGER.log(System.Logger.Level.WARNING,
                    "handleDevicesNotification - unknown notification type: {0}", actionType); // WAWebHandleDeviceNotification.C: S++
        }
    }

    /**
     * Normalizes an action node for consumption by {@link DeviceService#handleDeviceNotification}.
     * <p>
     * If the action node already has a {@code device-list} child or is missing a
     * {@code key-index-list} child, it is returned unchanged. Otherwise, wraps the device
     * children in a {@code device-list} node and rebuilds the action node structure.
     *
     * @param actionNode the original action node from the notification stanza
     * @return the normalized action node
     * @implNote ADAPTED: WAWebHandleDeviceNotification.h: the WA Web parser extracts device and
     * key-index-list as separate fields. DeviceService.handleDeviceNotification expects them as
     * child nodes of the action node, so this method adapts the stanza structure.
     */
    private Node normalizeDeviceActionNode(Node actionNode) { // ADAPTED: WAWebHandleDeviceNotification.h
        if (actionNode.getChild("device-list").isPresent()
                || actionNode.getChild("key-index-list").isEmpty()) {
            return actionNode;
        }

        var deviceChildren = actionNode.getChildren("device");
        if (deviceChildren.isEmpty()) {
            return actionNode;
        }

        var deviceListNode = new NodeBuilder()
                .description("device-list")
                .content(deviceChildren)
                .build();

        var rebuiltChildren = new ArrayList<Node>();
        rebuiltChildren.add(deviceListNode);
        actionNode.getChild("key-index-list").ifPresent(rebuiltChildren::add);

        return new NodeBuilder()
                .description(actionNode.description())
                .content(rebuiltChildren)
                .build();
    }

    /**
     * Sends an ack stanza for a device notification.
     * <p>
     * Builds and sends an ack with the user JID (not the raw device JID from the
     * notification), the stanza id, and class="notification".
     *
     * @param node    the original notification stanza node
     * @param userJid the user-level JID extracted from the {@code from} attribute
     * @implNote WAWebHandleDeviceNotification.C: builds ack as
     * wap("ack", {to: USER_JID(a.user), id: CUSTOM_STRING(a.stanzaId), class: "notification"}).
     * Note: WA Web's ack only has to, id, and class -- no type or participant attributes.
     */
    private void sendNotificationAck(Node node, Jid userJid) { // WAWebHandleDeviceNotification.C
        var stanzaId = node.getAttributeAsString("id", null);
        if (stanzaId == null) {
            return;
        }

        // WAWebHandleDeviceNotification.C: wap("ack", {to: USER_JID(a.user), id: CUSTOM_STRING(a.stanzaId), class: "notification"})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", userJid)
                .build());
    }
}
