package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

/**
 * Handles {@code type="contacts"} notifications carrying server-side mutations to the user's contact
 * graph.
 *
 * <p>The first recognised child tag selects the branch: {@code update} (profile changed for a single
 * contact), {@code modify} (one contact changed phone number, with optional LID counterpart), or
 * {@code sync} (full contact resync requested). The {@code add} and {@code remove} children are
 * recognised by the parser but produce no Cobalt-visible mutation and are merely acknowledged.</p>
 *
 * @implNote This implementation fires only the per-contact refresh side-effects Cobalt's listener API
 * can carry; WA Web additionally drives frontend events such as {@code resetPresence} and
 * {@code refreshTextStatus} which Cobalt's chat-presence pipeline handles via separate stanzas.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleContactNotification")
final class NotificationContactStreamHandler extends SocketStreamHandler.Concurrent {

    /**
     * Logs warnings about malformed stanzas and debug messages about unhandled child types.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationContactStreamHandler.class.getName());

    /**
     * Holds the client used for store reads and name queries.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the ack sender used to ship the post-processing
     * {@code <ack class="notification" type="contacts"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @param whatsapp  the non-{@code null} client
     * @param ackSender the non-{@code null} ack sender
     */
    NotificationContactStreamHandler(LinkedWhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, dispatches to {@link #handleNotification(Node)}, logs any thrown
     * exception, and always sends the protocol-level ACK.
     *
     * <p>Stanzas whose description is not {@code notification} or whose {@code type} is not
     * {@code contacts} are dropped without ACK; valid stanzas are always ACKed even when handling throws.</p>
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "contacts")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle contacts notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    /**
     * Locates the first action child ({@code update}, {@code add}, {@code remove}, {@code modify}, or
     * {@code sync}) and routes by description.
     *
     * <p>The {@code sync} branch flips
     * {@link com.github.auties00.cobalt.store.SyncStore#setSyncedContacts} to {@code false}
     * so the next reconnect re-runs the contact bootstrap. The {@code add} and {@code remove} children,
     * although recognised when scanning for an action child, fall through to the {@code default} branch
     * and are debug-logged.</p>
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleNotification(Node node) {
        Node actionNode = null;
        for (var child : node.children()) {
            var desc = child.description();
            if ("update".equals(desc) || "add".equals(desc) || "remove".equals(desc)
                    || "modify".equals(desc) || "sync".equals(desc)) {
                actionNode = child;
                break;
            }
        }
        if (actionNode == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Contacts notification {0} has no supported action child",
                    node.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        switch (actionNode.description()) {
            case "update" -> handleUpdate(node, actionNode);
            case "modify" -> handleModify(node, actionNode);
            case "sync" -> {
                LOGGER.log(System.Logger.Level.DEBUG, "Received contact sync notification");
                whatsapp.store().syncStore().setSyncedContacts(false);
            }
            default ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring unhandled contacts notification type {0} for notification {1}",
                            actionNode.description(),
                            node.getAttributeAsString("id", "[missing-id]"));
        }
    }

    /**
     * Resets the contact's presence to {@link ContactStatus#UNAVAILABLE} and refreshes the push name
     * when the {@code <update>} child carries a {@code jid}; logs and returns otherwise.
     *
     * <p>Resolves the {@code jid} to user form, loads or creates the contact, resets its last-known
     * presence, and refreshes the push name via {@link #refreshContact(Jid, Contact)}. An {@code <update>}
     * child carrying only a {@code hash} attribute, or neither {@code jid} nor {@code hash}, is
     * debug-logged and dropped.</p>
     *
     * @implNote This implementation does not handle the {@code hash}-attribute form because Cobalt does
     * not maintain a per-contact userhash to resolve against.
     *
     * @param notificationNode the parent {@code <notification>} stanza, used for logging
     * @param updateNode       the {@code <update>} child
     */
    private void handleUpdate(Node notificationNode, Node updateNode) {
        if (updateNode.hasAttribute("jid")) {
            var targetJid = updateNode.getAttributeAsJid("jid")
                    .map(Jid::toUserJid)
                    .orElse(null);
            if (targetJid == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "handleContactsNotification: update cmd missing jid");
                return;
            }

            var contact = whatsapp.store().contactStore().findContactByJid(targetJid)
                    .orElseGet(() -> whatsapp.store().contactStore().addNewContact(targetJid));

            contact.setLastKnownPresence(ContactStatus.UNAVAILABLE);

            refreshContact(targetJid, contact);
            return;
        }

        if (updateNode.hasAttribute("hash")) {
            // TODO: resolve the hash-only update by walking the contact store and matching the WA Web userhash truncation. Today the hash-only path is silently skipped.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring hash-only contacts update notification {0}",
                    notificationNode.getAttributeAsString("id", "[missing-id]"));
            return;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Contacts update notification {0} has neither jid nor hash",
                notificationNode.getAttributeAsString("id", "[missing-id]"));
    }

    /**
     * Records the old-to-new phone-number mapping carried by a {@code <modify>} child against the chats
     * and the contact record.
     *
     * <p>Tags the old chat with its new JID and the new chat with its old JID so the UI can show the
     * migration arrow in either direction, registers the LID-PN mapping when both {@code old_lid} and
     * {@code new_lid} are present, and updates the contact record under the new JID.</p>
     *
     * @implNote This implementation mirrors only the store-level side effects (chat record migration and
     * LID-PN mapping registration); the synthesized {@code change_number} notification-template message
     * that WA Web generates is not produced, because Cobalt's message generation is driven from the
     * chat-message stream rather than the contacts notification handler.
     *
     * @param notificationNode the parent {@code <notification>} stanza
     * @param modifyNode       the {@code <modify>} child carrying {@code old}, {@code new}, and optional {@code old_lid}/{@code new_lid}
     */
    private void handleModify(Node notificationNode, Node modifyNode) {
        var oldJid = modifyNode.getAttributeAsJid("old")
                .map(Jid::toUserJid)
                .orElse(null);
        var newJid = modifyNode.getAttributeAsJid("new")
                .map(Jid::toUserJid)
                .orElse(null);
        if (oldJid == null || newJid == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "modify notification missing old or new jid");
            return;
        }

        var newLid = modifyNode.getAttributeAsJid("new_lid")
                .map(Jid::toUserJid)
                .orElse(null);
        var oldLid = modifyNode.getAttributeAsJid("old_lid")
                .map(Jid::toUserJid)
                .orElse(null);

        whatsapp.store().chatStore().findChatByJid(oldJid).ifPresent(chat -> {
            chat.setNewJid(newJid);
        });

        whatsapp.store().chatStore().findChatByJid(newJid).ifPresent(chat -> {
            chat.setOldJid(oldJid);
        });

        if (oldLid != null && newLid != null) {
            whatsapp.store().contactStore().registerLidMapping(oldJid, oldLid);
            whatsapp.store().contactStore().registerLidMapping(newJid, newLid);
        }

        var updated = whatsapp.store().contactStore().findContactByJid(newJid)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(newJid));
        if (newLid != null) {
            updated.setLid(newLid);
        }
        whatsapp.store().contactStore().addContact(updated);
    }

    /**
     * Queries the contact's push name from the server and writes it to the local contact record.
     *
     * <p>Used by the {@code update} branch only. A failed name query is logged at {@code DEBUG} and the
     * contact is still persisted, so the presence reset performed by the caller remains durable.</p>
     *
     * @param targetJid the JID of the contact being refreshed
     * @param contact   the local contact record being mutated
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

        whatsapp.store().contactStore().addContact(contact);
    }

    /**
     * Sends the {@code <ack class="notification" type="contacts"/>} stanza for the processed
     * notification.
     *
     * <p>The ack is fire-and-forget.</p>
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("contacts").send();
    }
}
