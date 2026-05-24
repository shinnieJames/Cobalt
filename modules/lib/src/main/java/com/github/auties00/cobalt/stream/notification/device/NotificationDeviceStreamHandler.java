package com.github.auties00.cobalt.stream.notification.device;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@code type="devices"} notifications carrying device-list
 * mutations for a user.
 *
 * @apiNote
 * Dispatched by {@link NotificationDeviceDispatcher}. Each notification
 * carries one of three action children: {@code <add>} (a new companion
 * device linked), {@code <remove>} (an existing companion unlinked), or
 * {@code <update>} (the user's device list changed and a USync refresh
 * is required). The handler registers LID-PN mappings carried by the
 * {@code lid} attribute and processes the action against both the
 * primary user and the alternate (LID/PN) identity when available.
 *
 * @implNote
 * This implementation processes both the PN-keyed and the LID-keyed
 * record for every notification, matching WA Web's
 * {@code WAWebHandleDeviceNotification.handleDevicesNotification} which
 * builds the dual entry list {@code [{wid: pn, ...}, {wid: lid, ...}]}
 * before fanning out. The {@code update} branch re-queries via
 * {@link DeviceService#getDeviceLists}; {@code add}/{@code remove}
 * normalise the action node (wrapping inline {@code <device>} children
 * in a {@code <device-list>} node when the stanza lacks one) before
 * delegating to {@link DeviceService#handleDeviceNotification}.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleDeviceNotification")
public final class NotificationDeviceStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about malformed stanzas and errors
     * surfaced by the per-entry processing loop.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationDeviceStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and LID-PN
     * mapping registration.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link DeviceService} used to apply the parsed device action
     * (add/remove/update) to the cached device list.
     */
    private final DeviceService deviceService;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification">} stanza (without a
     * {@code type} attribute, with {@code to} set to the user-level
     * form of the inbound device JID).
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * @apiNote
     * Called once by {@link NotificationDeviceDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp      the {@link WhatsAppClient}
     * @param deviceService the {@link DeviceService}
     * @param ackSender     the {@link AckSender}
     */
    public NotificationDeviceStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.deviceService = deviceService;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, processes each entry (PN and optional
     * LID), and always sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationDeviceDispatcher}. Stanzas with no
     * {@code from} attribute are debug-logged and dropped; stanzas
     * without a known action child are warned about and dropped.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "devices")) {
            return;
        }

        var userJid = node.getAttributeAsJid("from")
                .map(Jid::toUserJid)
                .orElse(null);
        if (userJid == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping devices notification without from attribute");
            return;
        }

        var lidUser = node.getAttributeAsJid("lid")
                .map(Jid::toUserJid)
                .orElse(null);

        var actionNode = node.getChild("remove")
                .or(() -> node.getChild("add"))
                .or(() -> node.getChild("update"))
                .orElse(null);
        if (actionNode == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[devices] notif missing \"remove\" or \"add\" node");
            return;
        }
        var actionType = actionNode.description();

        var hash = actionType.equals("update")
                ? actionNode.getAttributeAsString("hash", null)
                : null;

        var secondaryJid = userJid.hasLidServer()
                ? whatsapp.store().findPhoneByLid(userJid).orElse(null)
                : lidUser;

        if (lidUser != null) {
            whatsapp.store().registerLidMapping(userJid, lidUser);
        }

        var entries = new ArrayList<Jid>();
        entries.add(userJid);
        if (secondaryJid != null) {
            entries.add(secondaryJid);
        }

        for (var entryJid : entries) {
            try {
                processDeviceEntry(entryJid, actionType, actionNode, hash);
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleDevicesNotification - {0} error: {1}",
                        actionType, throwable.getMessage());
            }
        }

        sendNotificationAck(node, userJid);
    }

    /**
     * Routes the action to {@link DeviceService} based on its type.
     *
     * @apiNote
     * {@code add} and {@code remove} delegate to
     * {@link DeviceService#handleDeviceNotification(Node, String, Jid)};
     * {@code update} delegates to
     * {@link DeviceService#getDeviceLists(List, String, Object, boolean)}
     * to refresh from USync.
     *
     * @implNote
     * This implementation warns and drops {@code update} notifications
     * with no {@code hash} attribute because WA Web's parser asserts
     * the hash via {@code n.attrString("hash")} and throws otherwise.
     *
     * @param entryJid   the user JID being processed (PN or LID)
     * @param actionType the action description ({@code "add"}, {@code "remove"}, {@code "update"})
     * @param actionNode the action child node
     * @param hash       the {@code hash} attribute value for {@code update}, or {@code null}
     */
    private void processDeviceEntry(Jid entryJid, String actionType, Node actionNode, String hash) {
        switch (actionType) {
            case "add", "remove" -> {
                deviceService.handleDeviceNotification(
                        normalizeDeviceActionNode(actionNode),
                        actionType,
                        entryJid
                );
            }
            case "update" -> {
                if (hash == null) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "[devices] update notification missing hash for {0}", entryJid);
                    return;
                }
                deviceService.getDeviceLists(List.of(entryJid), "notification", null, false);
            }
            default -> LOGGER.log(System.Logger.Level.WARNING,
                    "handleDevicesNotification - unknown notification type: {0}", actionType);
        }
    }

    /**
     * Wraps inline {@code <device>} children in a synthetic
     * {@code <device-list>} child when the action node lacks one.
     *
     * @apiNote
     * {@link DeviceService#handleDeviceNotification(Node, String, Jid)}
     * expects an action node with a {@code <device-list>} child and a
     * {@code <key-index-list>} sibling; this helper synthesises the
     * {@code <device-list>} from inline {@code <device>} children when
     * a stanza ships them flat (a server compatibility quirk).
     *
     * @implNote
     * This implementation returns the action node unchanged when
     * {@code <device-list>} is already present or when
     * {@code <key-index-list>} is absent (no normalisation needed).
     *
     * @param actionNode the action child node
     * @return the normalised action node
     */
    private Node normalizeDeviceActionNode(Node actionNode) {
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
     * Sends the {@code <ack class="notification" to="<user>"/>} stanza
     * (no {@code type} attribute) for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleDeviceNotification.handleDevicesNotification}
     * ack-builder which uses
     * {@code USER_JID(a.user)} (not the raw device JID from
     * {@code from}) as the {@code to} target.
     *
     * @param node    the original {@code <notification>} stanza
     * @param userJid the user-level JID extracted from {@code from}
     */
    private void sendNotificationAck(Node node, Jid userJid) {
        ackSender.ack(AckClass.NOTIFICATION, node).to(userJid).type(null).send();
    }
}
