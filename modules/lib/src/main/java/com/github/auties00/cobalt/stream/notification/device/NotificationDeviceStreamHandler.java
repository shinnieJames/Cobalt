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
 * Handles {@code type="devices"} notifications carrying device-list mutations for a user.
 *
 * <p>Dispatched by {@link NotificationDeviceDispatcher}. Each notification carries one of three
 * action children: {@code <add>} (a new companion device linked), {@code <remove>} (an existing
 * companion unlinked), or {@code <update>} (the user's device list changed and a USync refresh is
 * required). The handler registers the LID-PN mapping carried by the {@code lid} attribute and
 * processes the action against both the primary user JID and the alternate (LID or PN) identity
 * when one is available, then always emits the protocol-level ACK.
 *
 * @implNote This implementation processes both the PN-keyed and the LID-keyed record for every
 * notification, matching WA Web's {@code WAWebHandleDeviceNotification.handleDevicesNotification}
 * which builds the dual entry list {@code [{wid: pn, ...}, {wid: lid, ...}]} before fanning out.
 * The {@code update} branch re-queries via {@link DeviceService#getDeviceLists(java.util.Collection, String, String, boolean)};
 * {@code add} and {@code remove} normalise the action node (wrapping inline {@code <device>}
 * children in a {@code <device-list>} node when the stanza lacks one) before delegating to
 * {@link DeviceService#handleDeviceNotification(Node, String, Jid)}.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleDeviceNotification")
public final class NotificationDeviceStreamHandler implements SocketStream.Handler {

    /**
     * Logs warnings about malformed stanzas and errors surfaced by the per-entry processing loop.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationDeviceStreamHandler.class.getName());

    /**
     * Provides store reads and LID-PN mapping registration.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Applies the parsed device action (add, remove, or update) to the cached device list.
     */
    private final DeviceService deviceService;

    /**
     * Ships the post-processing {@code <ack class="notification">} stanza, omitting the {@code type}
     * attribute and targeting the user-level form of the inbound device JID.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with shared dependencies.
     *
     * <p>Called once by {@link NotificationDeviceDispatcher}.
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
     * Validates the stanza shape, processes each entry, and always sends the protocol-level ACK.
     *
     * <p>Stanzas that are not a {@code <notification type="devices">} return without side-effects.
     * A stanza with no {@code from} attribute is warning-logged and dropped, and a stanza without a
     * known action child ({@code <remove>}, {@code <add>}, or {@code <update>}) is warning-logged
     * and dropped. When present, the {@code lid} attribute is registered as a LID-PN mapping. The
     * action is then processed once for the primary user JID and once for the secondary identity
     * (the PN looked up from a LID-server JID, or the LID otherwise) when one resolves; per-entry
     * failures are caught and logged so a single failure does not skip the remaining entry. The ACK
     * is sent unconditionally at the end.
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
     * <p>The {@code add} and {@code remove} actions normalise the action node and delegate to
     * {@link DeviceService#handleDeviceNotification(Node, String, Jid)}. The {@code update} action
     * delegates to {@link DeviceService#getDeviceLists(java.util.Collection, String, String, boolean)}
     * with context {@code "notification"} to refresh the list from USync. Any other action
     * description is warning-logged and ignored.
     *
     * @implNote This implementation warns and drops {@code update} notifications with no
     * {@code hash} attribute because WA Web's parser asserts the hash via {@code n.attrString("hash")}
     * and throws otherwise.
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
     * Wraps inline {@code <device>} children in a synthetic {@code <device-list>} child when the
     * action node lacks one.
     *
     * <p>{@link DeviceService#handleDeviceNotification(Node, String, Jid)} expects an action node
     * with a {@code <device-list>} child and a {@code <key-index-list>} sibling. This helper
     * synthesises the {@code <device-list>} from inline {@code <device>} children when a stanza
     * ships them flat. The action node is returned unchanged when a {@code <device-list>} is
     * already present, when no {@code <key-index-list>} is present, or when there are no inline
     * {@code <device>} children to wrap.
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
     * Sends the {@code <ack class="notification" to="<user>"/>} stanza for the processed
     * notification.
     *
     * <p>The ACK carries no {@code type} attribute and targets the user-level JID extracted from
     * {@code from} rather than the raw device JID.
     *
     * @param node    the original {@code <notification>} stanza
     * @param userJid the user-level JID extracted from {@code from}
     */
    private void sendNotificationAck(Node node, Jid userJid) {
        ackSender.ack(AckClass.NOTIFICATION, node).to(userJid).type(null).send();
    }
}
