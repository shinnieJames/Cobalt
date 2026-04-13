package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming contacts notification stanzas dispatched by
 * {@link NotificationAccountDispatcher} when the notification type is
 * {@code "contacts"}.
 *
 * <p>This handler processes four action types parsed from the notification
 * children: {@code update} (contact profile changed), {@code modify} (phone
 * number change), {@code sync} (full contact re-sync needed), and a
 * default path that simply acknowledges unhandled notification types such as
 * {@code add} or {@code remove}.
 *
 * @implNote WAWebHandleContactNotification.default
 */
final class NotificationContactStreamHandler implements SocketStream.Handler {

    /**
     * Logger for diagnostic output related to contacts notification handling.
     *
     * @implNote WAWebHandleContactNotification.default -- WALogger usage
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationContactStreamHandler.class.getName());

    /**
     * The WhatsApp client used to send acknowledgements and access the store.
     *
     * @implNote WAWebHandleContactNotification.default -- constructor DI
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new handler for contacts notifications.
     *
     * @param whatsapp the non-{@code null} WhatsApp client
     * @implNote WAWebHandleContactNotification.default
     */
    NotificationContactStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming contacts notification node.
     *
     * <p>Validates the node, dispatches to the appropriate action handler, and
     * sends an acknowledgement stanza back to the server. Errors during
     * handling are logged; the acknowledgement is always sent.
     *
     * @param node the incoming notification node
     * @implNote WAWebHandleContactNotification.default (function k / L / E)
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "contacts")) {
            return; // ADAPTED: defensive guard; dispatcher already checks this
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle contacts notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node); // WAWebHandleContactNotification.E -- inner function y
        }
    }

    /**
     * Parses the notification node and dispatches to the appropriate case handler.
     *
     * <p>The notification node is expected to contain one of the following
     * children: {@code update}, {@code modify}, or {@code sync}. Any other
     * child (such as {@code add} or {@code remove}) is logged and acknowledged
     * without further processing.
     *
     * @param node the notification node
     * @implNote WAWebHandleContactNotification.E -- main switch on r.type
     */
    private void handleNotification(Node node) {
        // WAWebHandleContactNotification.f -- parser: check children for action type
        Node actionNode = null;
        for (var child : node.children()) {
            switch (child.description()) {
                case "update", "add", "remove", "modify", "sync" -> {
                    actionNode = child;
                    break;
                }
                default -> {
                }
            }

            if (actionNode != null) {
                break;
            }
        }
        if (actionNode == null) {
            // WAWebHandleContactNotification.f -- fallback: returns {type:"empty"}
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Contacts notification {0} has no supported action child",
                    node.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        // WAWebHandleContactNotification.E -- switch (r.type)
        switch (actionNode.description()) {
            case "update" -> handleUpdate(node, actionNode); // WAWebHandleContactNotification.E case "update"
            case "modify" -> handleModify(node, actionNode); // WAWebHandleContactNotification.E case "modify" -> S(r)
            case "sync" -> { // WAWebHandleContactNotification.E case "sync"
                LOGGER.log(System.Logger.Level.DEBUG, "Received contact sync notification");
                whatsapp.store().setSyncedContacts(false); // ADAPTED: WAWebContactSyncBridge.doFullContactSync
            }
            default -> // WAWebHandleContactNotification.E default case (add, remove, etc.)
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring unhandled contacts notification type {0} for notification {1}",
                            actionNode.description(),
                            node.getAttributeAsString("id", "[missing-id]"));
        }
    }

    /**
     * Handles the {@code "update"} contact notification case.
     *
     * <p>When the update child has a {@code jid} attribute, this method resolves the
     * target contact, resets its presence to {@link ContactStatus#UNAVAILABLE},
     * and refreshes the contact's push name. When the update child has only a
     * {@code hash} attribute (with no {@code jid}), the handler logs and returns
     * since hash-based contact lookup is not supported in Cobalt. When neither
     * attribute is present, the notification is treated as empty.
     *
     * @param notificationNode the parent notification node
     * @param updateNode       the {@code "update"} child node
     * @implNote WAWebHandleContactNotification.E case "update"
     */
    private void handleUpdate(Node notificationNode, Node updateNode) {
        // WAWebHandleContactNotification.f -- parser: update child
        // If jid attribute present, use it directly
        if (updateNode.hasAttribute("jid")) {
            var targetJid = updateNode.getAttributeAsJid("jid")
                    .map(Jid::toUserJid)
                    .orElse(null);
            if (targetJid == null) {
                // WAWebHandleContactNotification.E -- if (!a) return ... y(r)
                LOGGER.log(System.Logger.Level.DEBUG,
                        "handleContactsNotification: update cmd missing jid");
                return;
            }

            var contact = whatsapp.store()
                    .findContactByJid(targetJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(targetJid));

            // WAWebHandleContactNotification.E -- PresenceCollection.get(a).reset()
            contact.setLastKnownPresence(ContactStatus.UNAVAILABLE);

            // ADAPTED: WAWebHandleContactNotification.E -- changeProfilePicThumb + status refresh
            // Cobalt refreshes the push name as an adapted equivalent
            refreshContact(targetJid, contact);
            return;
        }

        // WAWebHandleContactNotification.f -- if hash attr present, try hash lookup
        if (updateNode.hasAttribute("hash")) {
            // ADAPTED: WAWebHandleContactNotification.f -- hash-based contact lookup
            // WA Web searches ContactCollection by userhash prefix; Cobalt does not
            // maintain userhash, so this falls through to the empty case
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring hash-only contacts update notification {0}",
                    notificationNode.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        // WAWebHandleContactNotification.f -- no jid, no hash -> {type:"empty"}
        LOGGER.log(System.Logger.Level.DEBUG,
                "Contacts update notification {0} has neither jid nor hash",
                notificationNode.getAttributeAsString("id", "[missing-id]"));
    }

    /**
     * Handles the {@code "modify"} contact notification case (phone number change).
     *
     * <p>Processes the phone number change by updating both the old and new chat
     * records with the appropriate {@code changeNumberNewJid} and
     * {@code changeNumberOldJid} fields. If LID attributes are present, LID-to-phone
     * mappings are registered for both old and new identifiers.
     *
     * @param notificationNode the parent notification node (unused but available
     *                         for context)
     * @param modifyNode       the {@code "modify"} child node containing
     *                         {@code old} and {@code new} jid attributes
     * @implNote WAWebHandleContactNotification.R (handleModifyAction)
     */
    private void handleModify(Node notificationNode, Node modifyNode) {
        // WAWebHandleContactNotification.R -- if (e.oldJid) { ... }
        var oldJid = modifyNode.getAttributeAsJid("old")
                .map(Jid::toUserJid)
                .orElse(null);
        var newJid = modifyNode.getAttributeAsJid("new")
                .map(Jid::toUserJid)
                .orElse(null);
        if (oldJid == null || newJid == null) {
            // WAWebHandleContactNotification.R -- else LOG("notification.oldJid is null")
            LOGGER.log(System.Logger.Level.DEBUG,
                    "modify notification missing old or new jid");
            return;
        }

        // WAWebHandleContactNotification.R -- resolve LIDs from modify node
        var newLid = modifyNode.getAttributeAsJid("new_lid")
                .map(Jid::toUserJid)
                .orElse(null);
        var oldLid = modifyNode.getAttributeAsJid("old_lid")
                .map(Jid::toUserJid)
                .orElse(null);

        // WAWebHandleContactNotification.R -- update old chat: changeNumberNewJid = newJid
        whatsapp.store().findChatByJid(oldJid).ifPresent(chat -> {
            chat.setNewJid(newJid); // WAWebHandleContactNotification.R -- changeNumberNewJid: r.toString()
        });

        // WAWebHandleContactNotification.R -- update new chat: changeNumberOldJid = oldJid
        whatsapp.store().findChatByJid(newJid).ifPresent(chat -> {
            chat.setOldJid(oldJid); // WAWebHandleContactNotification.R -- changeNumberOldJid: t.toString()
        });

        // WAWebHandleContactNotification.R -- createLidPnMappings for both old and new
        if (oldLid != null && newLid != null) {
            whatsapp.store().registerLidMapping(oldJid, oldLid); // WAWebHandleContactNotification.R -- {lid:a, pn:t}
            whatsapp.store().registerLidMapping(newJid, newLid); // WAWebHandleContactNotification.R -- {lid:i, pn:r}
        }

        // ADAPTED: WAWebHandleContactNotification.R -- ensure new contact exists in store
        var updated = whatsapp.store()
                .findContactByJid(newJid)
                .orElseGet(() -> whatsapp.store().addNewContact(newJid));
        if (newLid != null) {
            updated.setLid(newLid);
        }
        whatsapp.store().addContact(updated);
    }

    /**
     * Refreshes a contact's push name by querying the server.
     *
     * @param targetJid the JID of the contact to refresh
     * @param contact   the contact model to update
     * @implNote ADAPTED: WAWebHandleContactNotification.E case "update" -- changeProfilePicThumb + status updates
     */
    private void refreshContact(Jid targetJid, Contact contact) {
        try {
            whatsapp.queryName(targetJid).ifPresent(contact::setChosenName);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Cannot refresh contact name for {0}: {1}",
                    targetJid,
                    throwable.getMessage());
        }

        whatsapp.store().addContact(contact);
    }

    /**
     * Sends an acknowledgement stanza for a contacts notification.
     *
     * <p>The ack stanza uses the notification's {@code id} and {@code from}
     * attributes, with {@code class} set to {@code "notification"} and
     * {@code type} set to {@code "contacts"}.
     *
     * @param node the notification node to acknowledge
     * @implNote WAWebHandleContactNotification.E -- inner function y(e, t)
     */
    private void sendNotificationAck(Node node) {
        // WAWebHandleContactNotification.E -- y(e, t): wap("ack", {id, to, class, type})
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId) // WAWebHandleContactNotification.E -- id: CUSTOM_STRING(e.stanzaId)
                .attribute("to", stanzaFrom) // WAWebHandleContactNotification.E -- to: e.from
                .attribute("class", "notification") // WAWebHandleContactNotification.E -- class: "notification"
                .attribute("type", "contacts") // WAWebHandleContactNotification.E -- type: "contacts"
                .build());
    }
}
