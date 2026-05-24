package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles {@code type="contacts"} notifications carrying server-side
 * mutations to the user's contact graph.
 *
 * @apiNote
 * Dispatched by {@link NotificationAccountDispatcher}. The first
 * recognised child tag selects the branch: {@code update} (profile
 * changed for a single contact), {@code modify} (one contact changed
 * phone number, with optional LID counterpart), or {@code sync} (full
 * contact resync requested). {@code add} and {@code remove} children
 * are recognised by WA Web's parser but produce no Cobalt-visible
 * mutation and are merely acknowledged.
 *
 * @implNote
 * This implementation only fires the per-contact refresh side-effects
 * Cobalt's listener API can carry; WA Web additionally drives
 * frontend events such as {@code resetPresence} and
 * {@code refreshTextStatus} which Cobalt's chat-presence pipeline
 * handles via separate stanzas.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleContactNotification")
final class NotificationContactStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about malformed stanzas and debug messages
     * about unhandled child types.
     */
    private static final System.Logger LOGGER = System.getLogger(NotificationContactStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and name queries.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification" type="contacts"/>} stanza.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @apiNote
     * Called once by {@link NotificationAccountDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp  the non-{@code null} client
     * @param ackSender the non-{@code null} ack sender
     */
    NotificationContactStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, dispatches to {@link #handleNotification(Node)},
     * logs any thrown exception, and always sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationAccountDispatcher}. Stanzas with the
     * wrong description or type are silently dropped; valid stanzas
     * always get an ACK even when handling throws.
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
     * Locates the first action child ({@code update}, {@code add},
     * {@code remove}, {@code modify}, or {@code sync}) and routes by
     * description.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code incomingContactsNotification} parser ordering. The
     * {@code sync} branch flips
     * {@link com.github.auties00.cobalt.store.AbstractWhatsAppStore#setSyncedContacts}
     * to {@code false} so the next reconnect re-runs the contact
     * bootstrap.
     *
     * @implNote
     * This implementation handles only {@code update}, {@code modify},
     * and {@code sync}; {@code add} and {@code remove} fall through to
     * the {@code default} branch and are debug-logged. WA Web's parser
     * extracts {@code add.contentBytes()} and {@code remove.jid} but
     * the dispatch function {@code L} only switches on {@code update},
     * {@code modify}, and {@code sync}, with {@code add} and
     * {@code remove} hitting the same {@code default} log-and-ack path.
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
                whatsapp.store().setSyncedContacts(false);
            }
            default ->
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "Ignoring unhandled contacts notification type {0} for notification {1}",
                            actionNode.description(),
                            node.getAttributeAsString("id", "[missing-id]"));
        }
    }

    /**
     * Resets the contact's presence to {@link ContactStatus#UNAVAILABLE}
     * and refreshes the push name when the {@code <update>} child carries
     * a {@code jid}; logs and returns otherwise.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code "update"} branch which fires {@code resetPresence} and
     * {@code refreshTextStatus} on the contact and then re-fetches the
     * profile-picture thumb. Cobalt resets the presence and refreshes
     * the push name; the picture thumb is fetched on demand by the
     * UI's first read.
     *
     * @implNote
     * This implementation does not handle the {@code hash}-attribute
     * form because Cobalt does not maintain a per-contact userhash to
     * resolve against. The {@code hash} branch is debug-logged and
     * dropped.
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

            var contact = whatsapp.store()
                    .findContactByJid(targetJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(targetJid));

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
     * Records the old-to-new phone number mapping carried by a
     * {@code <modify>} child against the chats and the contact record.
     *
     * @apiNote
     * Drives the "phone number changed" system message in the chat
     * thread once a follow-up message uses the new JID. Both the old
     * and new chat records are tagged so the UI can show the migration
     * arrow in either direction.
     *
     * @implNote
     * This implementation only mirrors the store-level side effects of
     * WA Web's {@code S} function (chat record migration and LID-PN
     * mapping registration); the synthesized
     * {@code change_number} notification-template message that WA Web
     * generates via {@code genContactChangeNotificationMsg} is not
     * produced because Cobalt's message generation is driven from the
     * chat-message stream, not from the contacts notification handler.
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

        whatsapp.store().findChatByJid(oldJid).ifPresent(chat -> {
            chat.setNewJid(newJid);
        });

        whatsapp.store().findChatByJid(newJid).ifPresent(chat -> {
            chat.setOldJid(oldJid);
        });

        if (oldLid != null && newLid != null) {
            whatsapp.store().registerLidMapping(oldJid, oldLid);
            whatsapp.store().registerLidMapping(newJid, newLid);
        }

        var updated = whatsapp.store()
                .findContactByJid(newJid)
                .orElseGet(() -> whatsapp.store().addNewContact(newJid));
        if (newLid != null) {
            updated.setLid(newLid);
        }
        whatsapp.store().addContact(updated);
    }

    /**
     * Queries the contact's push name from the server and writes it to
     * the local contact record.
     *
     * @apiNote
     * Shared helper used by the {@code update} branch only. Failures
     * to query are logged at {@code DEBUG} and the contact is still
     * persisted so the presence reset above remains durable.
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

        whatsapp.store().addContact(contact);
    }

    /**
     * Sends the {@code <ack class="notification" type="contacts"/>}
     * stanza for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code u(stanzaId, from)} ack-builder inside
     * {@code incomingContactsNotification}.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("contacts").send();
    }
}
