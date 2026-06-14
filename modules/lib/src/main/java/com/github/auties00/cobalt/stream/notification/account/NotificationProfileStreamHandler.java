package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedContactTextStatusListener;
import com.github.auties00.cobalt.listener.linked.LinkedProfilePictureChangedListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Objects;

/**
 * Handles {@code type="picture"} and {@code type="status"} notifications announcing profile-picture or
 * about-text changes on a peer contact, a group, or self.
 *
 * <p>Profile-picture notifications carry one of four action children ({@code delete}, {@code set},
 * {@code request}, {@code set_avatar}) and identify the target either inline via {@code jid} or
 * indirectly via {@code hash}; the hash form is the WA contact-hash truncation used by the side-list
 * refresh path and is resolved by {@link #resolveContactByHash(String)}. About notifications split into
 * a direct {@code <set>} with inline content, a hash-based {@code <set hash=.../>} requiring server-side
 * resolution, and an unknown fall-through.</p>
 *
 * @implNote This implementation merges WA Web's separate profile-picture and about handler modules into
 * one Cobalt handler because they share enough structure (jid-vs-hash resolution, ack format,
 * contact-store fan-out) that the consolidation halves the branch count.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleProfilePicNotification")
@WhatsAppWebModule(moduleName = "WAWebHandleAboutNotification")
public final class NotificationProfileStreamHandler extends SocketStreamHandler.Concurrent {

    /**
     * Logs warnings about malformed action children and debug messages about unhandled types.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationProfileStreamHandler.class.getName());

    /**
     * Holds the salt appended to a contact's user component before the truncated MD5 hash used by the WA
     * contact-hash side-list lookup.
     *
     * <p>The server uses the truncated, base64-encoded digest as a privacy-preserving identifier in
     * side-list notifications.</p>
     *
     * @implNote This implementation hard-codes the literal {@code "WA_ADD_NOTIF"} taken from WA Web's
     * contact-hash constant.
     */
    private static final String CONTACT_HASH_SALT = "WA_ADD_NOTIF";

    /**
     * Holds the client used for store reads and server queries (picture, about).
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Holds the ack sender used to ship the post-processing {@code <ack class="notification">} stanza for
     * both picture and status notifications.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @param whatsapp  the non-{@code null} client
     * @param ackSender the non-{@code null} ack sender
     */
    public NotificationProfileStreamHandler(LinkedWhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Routes the incoming stanza to {@link #handlePicture(Node)} or {@link #handleAbout(Node)} based on
     * the stanza's {@code type} attribute.
     *
     * <p>Stanzas whose type is neither {@code "picture"} nor {@code "status"} return without
     * side-effects.</p>
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
     * Processes a profile-picture notification by resolving the target JID, applying the action, and
     * sending the ACK.
     *
     * <p>Locates the {@code delete}, {@code set}, {@code request}, or {@code set_avatar} action child,
     * resolves the target from the action's inline {@code jid} or, failing that, from its {@code hash}
     * via {@link #resolveContactByHash(String)}, and applies the {@code set}/{@code delete} action. The
     * {@code request} action is a no-op and the {@code set_avatar} action is logged without effect. Group
     * picture changes are debug-logged but do not synthesise an in-thread system message. The ACK is
     * always sent in the {@code finally} block.</p>
     *
     * @implNote This implementation always ACKs in the {@code finally} block, matching WA Web which
     * returns the ack from the parser's promise resolution.
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
     * Applies a profile-picture change for the resolved target JID, persisting the new URI for self and
     * firing the profile-picture-changed listener for all targets.
     *
     * <p>For the local account a {@code delete} clears the stored URI and a {@code set} re-queries and
     * stores the new URI, so the cached value survives without a later server round-trip; non-self
     * changes only fire the listener so the UI may re-query lazily. Group targets log the author and
     * timestamp at {@code DEBUG} but do not synthesise an in-thread system message.</p>
     *
     * @implNote This implementation persists the URI only for the local account; WA Web caches the thumb
     * bytes per-target, but Cobalt has no thumb cache and lets callers re-query on demand via
     * {@link LinkedWhatsAppClient#queryPicture}.
     *
     * @param targetJid  the non-{@code null} JID of the entity whose picture changed
     * @param actionType either {@code "set"} or {@code "delete"}
     * @param node       the notification stanza, used for timestamp extraction on group targets
     * @param actionNode the action child, used for author extraction on group targets
     */
    private void handlePictureSetOrDelete(Jid targetJid, String actionType, Node node, Node actionNode) {
        if (isSelf(targetJid)) {
            if ("delete".equals(actionType)) {
                whatsapp.store().accountStore().setProfilePicture((URI) null);
            } else {
                var picture = whatsapp.queryPicture(targetJid).orElse(null);
                whatsapp.store().accountStore().setProfilePicture(picture);
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
     * Processes an about/text-status notification by classifying the {@code <set>} child as inline
     * change, hash-based side-list change, or unknown.
     *
     * <p>A {@code <set>} child without a {@code hash} attribute is an inline change applied by
     * {@link #handleAboutChange(Node, Node, String)}; a {@code <set hash=.../>} child is a side-list
     * change applied by {@link #handleAboutSideListChange(Node, String)}; any other shape is logged and
     * ignored. The ACK is always sent in the {@code finally} block.</p>
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
     * Applies an inline about-text change to the primary JID and to its alternate JID (LID-PN
     * counterpart) when one is registered.
     *
     * <p>Resolves the originating JID from {@code from}, writes the stanza's {@code notify} pushname to
     * the contact's chosen-name field, then updates the text-status text for both the primary JID and its
     * alternate JID, firing the text-status listener for each updated record. A JID with no existing
     * text-status record is logged and skipped.</p>
     *
     * @implNote This implementation also reads the stanza's {@code notify} attribute and writes it to the
     * contact's chosen-name field; WA Web does not propagate the pushname here because its contact
     * push-name pipeline runs from a different stanza category, but Cobalt's listener API needs the
     * chosen-name fresh for the about-text affordance.
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
            var existingStatus = whatsapp.store().contactStore().findContactTextStatus(jid)
                    .orElse(null);
            if (existingStatus != null && content != null) {
                existingStatus.setText(content);
                whatsapp.store().contactStore().addContactTextStatus(jid.toUserJid(), existingStatus);
                notifyContactTextStatusChanged(jid.toUserJid(), existingStatus);
            } else {
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleAboutNotification: unknown contact {0}", jid);
            }
        }
    }

    /**
     * Applies a hash-based about-text change by resolving the target contact and re-querying the about
     * text from the server.
     *
     * <p>Resolves the contact via {@link #resolveContactByHash(String)}, and when an existing text-status
     * record exists for the resolved JID, re-queries the about text and writes the refreshed value back,
     * firing the text-status listener.</p>
     *
     * @implNote This implementation applies the new about text only when an existing text-status record
     * exists for the resolved JID; WA Web unconditionally fires the refresh and lets the response handler
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

        var existingStatus = whatsapp.store().contactStore().findContactTextStatus(userJid)
                .orElse(null);
        if (existingStatus != null) {
            var refreshed = whatsapp.queryAbout(userJid).orElse(null);
            if (refreshed != null) {
                existingStatus.setText(refreshed);
                whatsapp.store().contactStore().addContactTextStatus(userJid, existingStatus);
                notifyContactTextStatusChanged(userJid, existingStatus);
            }
        }
    }

    /**
     * Returns the JID whose computed hash matches the target hash, checking the local account first and
     * then the contact store.
     *
     * <p>Matches the logged-in account's own PN and LID JIDs before performing a linear scan over the
     * in-memory contacts, computing each candidate's hash via {@link #computeContactHash(String)} and
     * returning the first JID that matches. Including the local account lets a side-list notification
     * about the user's own profile resolve, since the account is not stored as a contact.</p>
     *
     * @implNote This implementation re-computes the hash for the self JIDs and for every contact in
     * {@link com.github.auties00.cobalt.store.ContactStore#contacts}; for small directories (a
     * few thousand entries) this is cheap, and a hash-keyed cache would help only at much higher contact
     * counts.
     *
     * @param targetHash the base64-encoded 3-byte hash to resolve
     * @return the matching JID, or {@code null} if neither the local account nor any contact hashes to
     *         {@code targetHash}
     */
    private Jid resolveContactByHash(String targetHash) {
        var selfPn = whatsapp.store().accountStore().jid().map(Jid::withoutData).orElse(null);
        if (selfPn != null && selfPn.user() != null && targetHash.equals(computeContactHash(selfPn.user()))) {
            return selfPn;
        }
        var selfLid = whatsapp.store().accountStore().lid().map(Jid::withoutData).orElse(null);
        if (selfLid != null && selfLid.user() != null && targetHash.equals(computeContactHash(selfLid.user()))) {
            return selfLid;
        }

        for (var contact : whatsapp.store().contactStore().contacts()) {
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
     * <p>Used by {@link #resolveContactByHash(String)}. The 3-byte truncation matches the WA server's
     * published format for side-list notifications.</p>
     *
     * @implNote This implementation throws {@link AssertionError} when MD5 is unavailable;
     * {@code MessageDigest.getInstance("MD5")} is guaranteed to succeed on every JRE shipping the
     * standard cryptography providers.
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
     * Returns the LID-counterpart for a PN JID, or the PN-counterpart for a LID JID, or {@code null} when
     * no mapping is registered.
     *
     * <p>Used by {@link #handleAboutChange(Node, Node, String)} to fan the about-text update out to both
     * JID forms a contact may appear under.</p>
     *
     * @param jid the source JID
     * @return the alternate JID, or {@code null} when no mapping exists
     */
    private Jid getAlternateUserJid(Jid jid) {
        if (jid.hasUserServer()) {
            return whatsapp.store().contactStore().findLidByPhone(jid).orElse(null);
        } else if (jid.hasLidServer()) {
            return whatsapp.store().contactStore().findPhoneByLid(jid).orElse(null);
        }
        return null;
    }

    /**
     * Returns whether the given JID identifies the authenticated account.
     *
     * <p>Used by {@link #handlePictureSetOrDelete(Jid, String, Node, Node)} to distinguish self-picture
     * changes (persist the URI) from peer changes (listener-only).</p>
     *
     * @param jid the JID to check
     * @return {@code true} when {@code jid} matches the local account, {@code false} otherwise
     */
    private boolean isSelf(Jid jid) {
        return whatsapp.store().accountStore().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    /**
     * Writes the chosen-name field on the contact, creating a new contact record when none exists.
     *
     * <p>Used by {@link #handleAboutChange(Node, Node, String)} to consume the stanza's {@code notify}
     * attribute (the pushname) alongside the about-text update. A {@code null} or blank name is ignored.</p>
     *
     * @param contactJid the JID of the contact being updated
     * @param chosenName the new chosen name, ignored when {@code null} or blank
     */
    private void updateContactChosenName(Jid contactJid, String chosenName) {
        if (contactJid == null || chosenName == null || chosenName.isBlank()) {
            return;
        }

        var contact = whatsapp.store().contactStore().findContactByJid(contactJid)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(contactJid.toUserJid()));
        contact.setChosenName(chosenName);
        whatsapp.store().contactStore().addContact(contact);
    }

    /**
     * Fans the profile-picture-changed callback out to every registered listener on its own virtual
     * thread.
     *
     * <p>Used by {@link #handlePictureSetOrDelete(Jid, String, Node, Node)}.</p>
     *
     * @param jid the JID of the entity whose picture changed
     */
    private void fireProfilePictureChanged(Jid jid) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedProfilePictureChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onProfilePictureChanged(whatsapp, jid));
            }
        }
    }

    /**
     * Fans the contact-text-status callback out to every registered listener on its own virtual thread.
     *
     * <p>Used by {@link #handleAboutChange(Node, Node, String)} and
     * {@link #handleAboutSideListChange(Node, String)}.</p>
     *
     * @param contactJid the JID whose text status changed
     * @param status     the updated text-status record
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedContactTextStatusListener typed) {
                Thread.startVirtualThread(() -> typed.onContactTextStatus(whatsapp, contactJid, status));
            }
        }
    }

    /**
     * Sends the {@code <ack class="notification" type="picture"/>} stanza for the processed notification.
     *
     * <p>The ack is fire-and-forget.</p>
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendPictureNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("picture").send();
    }

    /**
     * Sends the {@code <ack class="notification" type="status"/>} stanza for the processed notification.
     *
     * <p>The ack is fire-and-forget.</p>
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendStatusNotificationAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("status").send();
    }
}
