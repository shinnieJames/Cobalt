package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

/**
 * Handles {@code type="picture"} and {@code type="status"} notifications
 * announcing profile-picture or about-text changes on a peer contact, a
 * group, or self.
 *
 * @apiNote
 * Dispatched by {@link NotificationAccountDispatcher}. Profile-picture
 * notifications carry one of four action children ({@code delete},
 * {@code set}, {@code request}, {@code set_avatar}) and may identify
 * the target either inline via {@code jid} or indirectly via {@code hash};
 * the hash form is the WA contact-hash truncation
 * ({@code Base64(MD5(user + "WA_ADD_NOTIF")[0:3])}) used by the
 * sidelist refresh path. About notifications similarly split into a
 * direct {@code <set>} with inline content, a hash-based
 * {@code <set hash=.../>} requiring server-side resolution, and an
 * {@code unknown} fall-through.
 *
 * @implNote
 * This implementation merges WA Web's separate
 * {@code WAWebHandleProfilePicNotification} and
 * {@code WAWebHandleAboutNotification} modules into one Cobalt handler
 * because they share enough structure (jid-vs-hash resolution, ack
 * format, contact-store fan-out) that the consolidation halves the
 * branch count.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleProfilePicNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleAboutNotification")
public final class NotificationProfileStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about malformed action children and debug
     * messages about unhandled types.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationProfileStreamHandler.class.getName());

    /**
     * The salt appended to a contact's user component before the
     * truncated MD5 hash used by the WA contact-hash side-list lookup.
     *
     * @apiNote
     * Mirrors WA Web's hard-coded {@code "WA_ADD_NOTIF"} constant used
     * by both
     * {@code WAWebContactGetters.getUserhash} (consumer) and
     * {@code WAWebApiContact.getContactRecordByHash} (lookup). The
     * server uses the truncated, base64-encoded digest as a privacy
     * preserving identifier in side-list notifications.
     */
    private static final String CONTACT_HASH_SALT = "WA_ADD_NOTIF";

    /**
     * The {@link WhatsAppClient} used for store reads and server queries
     * (picture, about).
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification">} stanza for both picture and
     * status notifications.
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
    public NotificationProfileStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Routes the incoming stanza to {@link #handlePicture(Node)} or
     * {@link #handleAbout(Node)} based on the stanza's {@code type}
     * attribute.
     *
     * @apiNote
     * Invoked by {@link NotificationAccountDispatcher}. Stanzas whose
     * type is neither {@code "picture"} nor {@code "status"} return
     * without side-effects.
     *
     * @param node the {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (node.hasDescription("notification") && node.hasAttribute("type", "picture")) {
            handlePicture(node);
            return;
        }

        if (node.hasDescription("notification") && node.hasAttribute("type", "status")) {
            handleAbout(node);
        }
    }

    /**
     * Processes a profile-picture notification by resolving the target
     * JID, applying the action, and sending the ACK.
     *
     * @apiNote
     * Drives the profile-picture-change affordance for both self
     * (own profile picture refreshed) and any peer or group. Group
     * picture changes log the author and timestamp at {@code DEBUG}
     * but do not synthesise the in-thread system message; the next
     * UI render fetches the new image.
     *
     * @implNote
     * This implementation always ACKs in the {@code finally} block,
     * matching WA Web's
     * {@code WAWebHandleProfilePicNotification.handleProfilePicNotificationJob}
     * which returns the ack from the parser's promise resolution.
     *
     * @param node the {@code <notification type="picture"/>} stanza
     */
    private void handlePicture(Node node) {
        try {
            var actionNode = node.getChild("delete", "set", "request", "set_avatar")
                    .orElse(null);
            if (actionNode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Picture notification {0} has no known action child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var actionType = actionNode.description();
            var stanzaId = node.getAttributeAsString("id", null);
            var from = node.getAttributeAsJid("from")
                    .map(Jid::withoutData)
                    .orElse(null);

            Jid targetJid;
            if (actionNode.getAttributeAsJid("jid").isPresent()) {
                targetJid = actionNode.getAttributeAsJid("jid")
                        .map(Jid::withoutData)
                        .orElse(null);
            } else {
                var hash = actionNode.getAttributeAsString("hash", null);
                if (hash != null) {
                    targetJid = resolveContactByHash(hash);
                    if (targetJid == null) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Side contact hash not found for pic update");
                    }
                } else {
                    targetJid = null;
                }
            }

            if (targetJid != null) {
                switch (actionType) {
                    case "delete", "set" -> {
                        handlePictureSetOrDelete(targetJid, actionType, node, actionNode);
                    }
                    case "request" -> {
                    }
                    case "set_avatar" -> {
                        // TODO: implement the set_avatar branch once Cobalt has avatar metadata; today the stanza is logged and ACKed without effect.
                        LOGGER.log(System.Logger.Level.WARNING,
                                "set_avatar picture notification is not implemented");
                    }
                    default -> {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Invalid type received for picture notification: {0}", actionType);
                    }
                }
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle picture notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendPictureNotificationAck(node);
        }
    }

    /**
     * Applies a profile-picture change for the resolved target JID,
     * persisting the new URI for self and firing
     * {@code onProfilePictureChanged} for all targets.
     *
     * @apiNote
     * Self-picture changes are persisted on the store so the cached
     * URI survives without a server round-trip; non-self changes
     * trigger the listener so the UI may re-query lazily.
     *
     * @implNote
     * This implementation only persists the URI for the local account.
     * WA Web's
     * {@code WAWebChangeProfilePicThumb.changeProfilePicThumb} caches
     * the thumb bytes per-target in IndexedDB; Cobalt has no thumb
     * cache and lets callers re-query on demand via
     * {@link WhatsAppClient#queryPicture}.
     *
     * @param targetJid  the non-{@code null} JID of the entity whose picture changed
     * @param actionType either {@code "set"} or {@code "delete"}
     * @param node       the notification stanza, used for timestamp extraction on group targets
     * @param actionNode the action child, used for author extraction on group targets
     */
    private void handlePictureSetOrDelete(Jid targetJid, String actionType, Node node, Node actionNode) {
        if (isSelf(targetJid)) {
            if ("delete".equals(actionType)) {
                whatsapp.store().setProfilePicture((URI) null);
            } else {
                var picture = whatsapp.queryPicture(targetJid).orElse(null);
                whatsapp.store().setProfilePicture(picture);
            }
        }

        if (targetJid.hasGroupOrCommunityServer()) {
            var ts = node.getAttributeAsLong("t", (Long) null);
            if (ts != null) {
                // TODO: synthesize the group-pic-change system message via MessageService once it is injected here; today the change is only delivered via the listener fan-out below.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Group {0} picture changed at {1} by {2} - system message generation not available",
                        targetJid,
                        Instant.ofEpochSecond(ts),
                        actionNode.getAttributeAsJid("author").map(Jid::toUserJid).orElse(null));
            }
        }

        fireProfilePictureChanged(targetJid);
    }

    /**
     * Processes an about/text-status notification by classifying the
     * {@code <set>} child as inline change, hash-based side-list change,
     * or unknown.
     *
     * @apiNote
     * Drives the about/text-status update for the originating user.
     * Inline changes apply to both the primary JID and its alternate
     * (LID or PN counterpart); hash-based changes resolve the target
     * via {@link #resolveContactByHash(String)} and re-query the about
     * text from the server.
     *
     * @param node the {@code <notification type="status"/>} stanza
     */
    private void handleAbout(Node node) {
        try {
            var stanzaId = node.getAttributeAsString("id", null);
            var setNode = node.getChild("set").orElse(null);

            if (setNode != null && !setNode.hasAttribute("hash")) {
                handleAboutChange(node, setNode, stanzaId);
            } else if (setNode != null && setNode.hasAttribute("hash")) {
                handleAboutSideListChange(setNode, stanzaId);
            } else {
                var fromStr = node.getAttributeAsString("from", "[unknown]");
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleAboutNotification: unhandled type unknown from {0}", fromStr);
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle status notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendStatusNotificationAck(node);
        }
    }

    /**
     * Applies an inline about-text change to the primary JID and to its
     * alternate JID (LID-PN counterpart) when one is registered.
     *
     * @apiNote
     * Mirrors WA Web's {@code "change"} branch which builds the JID
     * list as {@code [from, getAlternateUserWid(from)]} and dispatches
     * {@code frontendFireAndForget("updateTextStatuses", {ids, content})}
     * to update both at once. Cobalt iterates the same list and
     * updates the local text-status records directly.
     *
     * @implNote
     * This implementation also reads the stanza's {@code notify}
     * attribute and writes it to the contact's chosen-name field; WA
     * Web does not propagate the pushname here because its contact
     * push-name pipeline runs from a different stanza category.
     * Cobalt's listener API needs the chosen-name fresh for the
     * about-text affordance.
     *
     * @param node     the {@code <notification>} stanza (for {@code from}, {@code notify}, {@code t})
     * @param setNode  the {@code <set>} child carrying the inline content
     * @param stanzaId the stanza id, used for logging
     */
    private void handleAboutChange(Node node, Node setNode, String stanzaId) {
        var from = node.getAttributeAsJid("from")
                .map(Jid::toUserJid)
                .orElse(null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Status notification {0} is missing from",
                    Objects.requireNonNullElse(stanzaId, "[missing-id]"));
            return;
        }

        node.getAttributeAsString("notify")
                .ifPresent(pushName -> updateContactChosenName(from, pushName));

        var content = setNode.toContentString().orElse(null);

        var jidsToUpdate = new ArrayList<Jid>();
        jidsToUpdate.add(from);
        var alternateJid = getAlternateUserJid(from);
        if (alternateJid != null) {
            jidsToUpdate.add(alternateJid);
        }

        for (var jid : jidsToUpdate) {
            var existingStatus = whatsapp.store()
                    .findContactTextStatus(jid)
                    .orElse(null);
            if (existingStatus != null && content != null) {
                existingStatus.setText(content);
                whatsapp.store().addContactTextStatus(jid.toUserJid(), existingStatus);
                notifyContactTextStatusChanged(jid.toUserJid(), existingStatus);
            } else {
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleAboutNotification: unknown contact {0}", jid);
            }
        }
    }

    /**
     * Applies a hash-based about-text change by resolving the target
     * contact and re-querying the about text from the server.
     *
     * @apiNote
     * Mirrors WA Web's {@code "sideListChange"} branch which calls
     * {@code WAWebApiContact.getContactRecordByHash} followed by a
     * {@code frontendFireAndForget("refreshTextStatus", ...)} request.
     * Cobalt performs the equivalent {@code queryAbout} and writes the
     * result to the local text-status record.
     *
     * @implNote
     * This implementation only applies the new about text when an
     * existing text-status record exists for the resolved JID; WA Web
     * unconditionally fires the refresh and lets the response handler
     * create the record on demand.
     *
     * @param setNode  the {@code <set hash=.../>} child
     * @param stanzaId the stanza id, used for logging
     */
    private void handleAboutSideListChange(Node setNode, String stanzaId) {
        var hash = setNode.getAttributeAsString("hash", null);
        if (hash == null) {
            return;
        }

        var resolvedJid = resolveContactByHash(hash);
        if (resolvedJid == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Side contact hash not found for status update");
            return;
        }

        var userJid = resolvedJid.toUserJid();

        var existingStatus = whatsapp.store()
                .findContactTextStatus(userJid)
                .orElse(null);
        if (existingStatus != null) {
            var refreshed = whatsapp.queryAbout(userJid).orElse(null);
            if (refreshed != null) {
                existingStatus.setText(refreshed);
                whatsapp.store().addContactTextStatus(userJid, existingStatus);
                notifyContactTextStatusChanged(userJid, existingStatus);
            }
        }
    }

    /**
     * Walks the local contact store and returns the JID whose computed
     * hash matches the target hash.
     *
     * @apiNote
     * Equivalent to WA Web's
     * {@code WAWebApiContact.getContactRecordByHash} which performs the
     * same linear scan over the in-memory contact collection.
     *
     * @implNote
     * This implementation iterates {@link com.github.auties00.cobalt.store.AbstractWhatsAppStore#contacts}
     * and re-computes the hash for every contact; for small directories
     * (a few thousand entries) this is cheap. A hash-keyed cache would
     * help only at much higher contact counts.
     *
     * @param targetHash the base64-encoded 3-byte hash to resolve
     * @return the matching JID, or {@code null} if no contact hashes to {@code targetHash}
     */
    private Jid resolveContactByHash(String targetHash) {
        for (var contact : whatsapp.store().contacts()) {
            var jid = contact.jid();
            var user = jid.user();
            if (user == null) {
                continue;
            }

            var computed = computeContactHash(user);
            if (targetHash.equals(computed)) {
                return jid;
            }
        }
        return null;
    }

    /**
     * Computes the WA contact hash for the given user component as
     * {@snippet :
     * Base64(MD5(user + "WA_ADD_NOTIF")[0:3])
     * }
     *
     * @apiNote
     * Used only by {@link #resolveContactByHash(String)}. The 3-byte
     * truncation matches the WA server's published format for
     * side-list notifications.
     *
     * @implNote
     * This implementation throws {@link AssertionError} if MD5 is
     * unavailable; {@code MessageDigest.getInstance("MD5")} is
     * guaranteed to succeed on every JRE shipping the standard
     * cryptography providers.
     *
     * @param user the user component of a JID (typically a phone number or LID)
     * @return the base64-encoded 3-byte hash
     */
    private String computeContactHash(String user) {
        try {
            var md5 = MessageDigest.getInstance("MD5");
            var input = (user + CONTACT_HASH_SALT).getBytes(StandardCharsets.UTF_8);
            var digest = md5.digest(input);
            var truncated = new byte[3];
            System.arraycopy(digest, 0, truncated, 0, 3);
            return Base64.getEncoder().encodeToString(truncated);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("MD5 algorithm not available", exception);
        }
    }

    /**
     * Returns the LID-counterpart for a PN JID, or the PN-counterpart
     * for a LID JID, or {@code null} when no mapping is registered.
     *
     * @apiNote
     * Internal helper used by {@link #handleAboutChange(Node, Node, String)}
     * to fan the about-text update out to both JID forms a contact may
     * appear under.
     *
     * @param jid the source JID
     * @return the alternate JID, or {@code null} when no mapping exists
     */
    private Jid getAlternateUserJid(Jid jid) {
        if (jid.hasUserServer()) {
            return whatsapp.store().findLidByPhone(jid).orElse(null);
        } else if (jid.hasLidServer()) {
            return whatsapp.store().findPhoneByLid(jid).orElse(null);
        }
        return null;
    }

    /**
     * Returns whether the given JID identifies the authenticated account.
     *
     * @apiNote
     * Internal helper used by {@link #handlePictureSetOrDelete} to
     * distinguish self-picture changes (persist the URI) from peer
     * changes (listener-only).
     *
     * @param jid the JID to check
     * @return {@code true} when {@code jid} matches the local account, {@code false} otherwise
     */
    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    /**
     * Writes the chosen-name field on the contact, creating a new
     * contact record when none exists.
     *
     * @apiNote
     * Internal helper used by {@link #handleAboutChange} to consume
     * the stanza's {@code notify} attribute (the pushname) alongside
     * the about text update.
     *
     * @param contactJid the JID of the contact being updated
     * @param chosenName the new chosen name, ignored when {@code null} or blank
     */
    private void updateContactChosenName(Jid contactJid, String chosenName) {
        if (contactJid == null || chosenName == null || chosenName.isBlank()) {
            return;
        }

        var contact = whatsapp.store().findContactByJid(contactJid)
                .orElseGet(() -> whatsapp.store().addNewContact(contactJid.toUserJid()));
        contact.setChosenName(chosenName);
        whatsapp.store().addContact(contact);
    }

    /**
     * Fans {@code onProfilePictureChanged} out to every registered
     * listener on its own virtual thread.
     *
     * @apiNote
     * Internal helper used by {@link #handlePictureSetOrDelete}.
     *
     * @param jid the JID of the entity whose picture changed
     */
    private void fireProfilePictureChanged(Jid jid) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onProfilePictureChanged(whatsapp, jid));
        }
    }

    /**
     * Fans {@code onContactTextStatus} out to every registered listener
     * on its own virtual thread.
     *
     * @apiNote
     * Internal helper used by {@link #handleAboutChange} and
     * {@link #handleAboutSideListChange}.
     *
     * @param contactJid the JID whose text status changed
     * @param status     the updated text-status record
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    /**
     * Sends the {@code <ack class="notification" type="picture"/>}
     * stanza for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleProfilePicNotification} ack-builder.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendPictureNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("picture").send();
    }

    /**
     * Sends the {@code <ack class="notification" type="status"/>}
     * stanza for the processed notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleAboutNotification} ack-builder.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendStatusNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("status").send();
    }
}
