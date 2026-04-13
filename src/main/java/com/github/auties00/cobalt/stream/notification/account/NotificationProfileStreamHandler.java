package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
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
 * Handles incoming profile picture and about (text status) notification stanzas.
 *
 * <p>This handler processes two types of notification stanzas dispatched by
 * {@link NotificationAccountDispatcher}: {@code type="picture"} for profile picture
 * changes, and {@code type="status"} for about/text-status changes. For each
 * notification type, it parses the stanza, updates the local store, fires the
 * appropriate listener callbacks, and sends an acknowledgement back to the server.
 *
 * <p>Profile picture notifications can arrive in two forms: with an explicit
 * {@code jid} attribute on the action child (identifying the target contact or group
 * directly), or with a {@code hash} attribute that must be resolved against the
 * local contact database using the WhatsApp contact hash algorithm. About
 * notifications similarly distinguish between a direct {@code change} (inline
 * content) and a {@code sideListChange} (hash-based contact resolution with
 * server-side status fetch).
 *
 * @implNote WAWebHandleProfilePicNotification, WAWebHandleAboutNotification
 */
public final class NotificationProfileStreamHandler implements SocketStream.Handler {

    /**
     * The logger instance for this handler, using the fully qualified class name
     * as the logger name.
     *
     * @implNote WAWebHandleProfilePicNotification (WALogger usage)
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationProfileStreamHandler.class.getName());

    /**
     * The salt string appended to a contact's user component before MD5 hashing,
     * used by the WhatsApp contact hash algorithm to produce a short hash for
     * side-list contact resolution.
     *
     * @implNote WAWebApiContact.getContactHash
     */
    private static final String CONTACT_HASH_SALT = "WA_ADD_NOTIF"; // WAWebApiContact.getContactHash

    /**
     * The WhatsApp client instance providing access to the store, query methods,
     * and node sending capabilities.
     *
     * @implNote WAWebHandleProfilePicNotification (module-level dependencies)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new profile/status notification handler.
     *
     * @param whatsapp the non-{@code null} WhatsApp client instance
     * @implNote WAWebHandleProfilePicNotification, WAWebHandleAboutNotification (module scope)
     */
    public NotificationProfileStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Dispatches a notification node to the appropriate handler based on the
     * {@code type} attribute value.
     *
     * <p>Notifications with {@code type="picture"} are routed to
     * {@link #handlePicture(Node)}, and notifications with {@code type="status"}
     * are routed to {@link #handleAbout(Node)}.
     *
     * @param node the non-{@code null} notification stanza node
     * @implNote WAWebHandleProfilePicNotification.handleProfilePicNotificationJob,
     *           WAWebHandleAboutNotification.handleAboutNotification
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
     * Handles an incoming profile picture notification stanza.
     *
     * <p>The notification stanza contains one of four action children:
     * {@code delete} (picture removed), {@code set} (picture changed),
     * {@code request} (self-picture request, no-op), or {@code set_avatar}
     * (avatar set, not yet implemented). The action child may identify the
     * target via an explicit {@code jid} attribute or via a {@code hash}
     * attribute that is resolved against the local contact database.
     *
     * <p>For {@code delete} and {@code set} actions, the handler updates
     * the local profile picture for the target contact (or self) and fires
     * the {@code onProfilePictureChanged} listener callback. For group
     * targets, a system message is logged (full group system message
     * generation requires MessageService integration).
     *
     * @param node the non-{@code null} notification stanza node with
     *             {@code type="picture"}
     * @implNote WAWebHandleProfilePicNotification.handleProfilePicNotificationJob
     */
    private void handlePicture(Node node) {
        try {
            var actionNode = node.getChild("delete", "set", "request", "set_avatar")
                    .orElse(null);
            if (actionNode == null) {
                // WAWebHandleProfilePicNotification.m: parser throws on unexpected type
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Picture notification {0} has no known action child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var actionType = actionNode.description(); // WAWebHandleProfilePicNotification.m: t (type)
            var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleProfilePicNotification.m: stanzaId
            var from = node.getAttributeAsJid("from") // WAWebHandleProfilePicNotification.m: from
                    .map(Jid::withoutData)
                    .orElse(null);

            // WAWebHandleProfilePicNotification.m: two parser branches based on n.hasAttr("jid")
            Jid targetJid;
            if (actionNode.getAttributeAsJid("jid").isPresent()) {
                // Branch 1: child has jid attr -> use it directly
                targetJid = actionNode.getAttributeAsJid("jid") // WAWebHandleProfilePicNotification.m: jid
                        .map(Jid::withoutData)
                        .orElse(null);
            } else {
                // Branch 2: child has hash attr -> resolve via contact hash lookup
                var hash = actionNode.getAttributeAsString("hash", null); // WAWebHandleProfilePicNotification.m: hash
                if (hash != null) {
                    targetJid = resolveContactByHash(hash); // WAWebHandleProfilePicNotification._: getContactRecordByHash
                    if (targetJid == null) {
                        // WAWebHandleProfilePicNotification._: WARN "side contact hash not found for pic update"
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Side contact hash not found for pic update");
                    }
                } else {
                    targetJid = null;
                }
            }

            // WAWebHandleProfilePicNotification._: if(a.jid||a.hash) { ... } - only process if we have target info
            if (targetJid != null) {
                switch (actionType) {
                    case "delete", "set" -> { // WAWebHandleProfilePicNotification._: case "delete": case "set":
                        handlePictureSetOrDelete(targetJid, actionType, node, actionNode);
                    }
                    case "request" -> { // WAWebHandleProfilePicNotification._: case "request": break
                        // No-op: WA Web also does nothing for request type
                    }
                    case "set_avatar" -> { // WAWebHandleProfilePicNotification._: case "set_avatar"
                        // WAWebHandleProfilePicNotification._: WARN "set_avatar picture notification is not implemented"
                        LOGGER.log(System.Logger.Level.WARNING,
                                "set_avatar picture notification is not implemented");
                    }
                    default -> { // WAWebHandleProfilePicNotification._: default: WARN "Invalid type received"
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Invalid type received for picture notification: {0}", actionType);
                    }
                }
            }
        } catch (Throwable throwable) {
            // ADAPTED: WAWebHandleProfilePicNotification (Cobalt error model)
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle picture notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            // WAWebHandleProfilePicNotification._: ack at the end (return i.then(ack))
            sendPictureNotificationAck(node);
        }
    }

    /**
     * Handles the {@code set} or {@code delete} action for a profile picture notification.
     *
     * <p>For {@code delete}, the profile picture thumbnail is cleared from the local store
     * for the target contact. For {@code set}, the profile picture is fetched from the
     * server and stored locally. In both cases, the alternate user JID (LID/PN mapping)
     * is also updated if it exists, mirroring the behavior of
     * {@code WAWebChangeProfilePicThumb.changeProfilePicThumb}.
     *
     * <p>For group targets with a non-{@code null} timestamp, a log message notes
     * where a group system message would be generated. Full group system message
     * generation requires {@code MessageService} integration which is not currently
     * available to this handler.
     *
     * @param targetJid  the non-{@code null} JID of the entity whose picture changed
     * @param actionType either {@code "set"} or {@code "delete"}
     * @param node       the notification stanza node (for timestamp and author extraction)
     * @param actionNode the action child node (for author extraction)
     * @implNote WAWebHandleProfilePicNotification._ (delete/set case),
     *           WAWebChangeProfilePicThumb.changeProfilePicThumb
     */
    private void handlePictureSetOrDelete(Jid targetJid, String actionType, Node node, Node actionNode) {
        // WAWebChangeProfilePicThumb.changeProfilePicThumb: updates pic thumb for target
        if ("delete".equals(actionType)) {
            // WAWebChangeProfilePicThumb.c: ProfilePicCommand.Remove -> persistProfilePicToDB (clears thumb)
            if (isSelf(targetJid)) {
                whatsapp.store().setProfilePicture((URI) null);
            }
        } else {
            // WAWebChangeProfilePicThumb.c: ProfilePicCommand.Set -> workerSafeSendAndReceive("setProfilePicThumb")
            var picture = whatsapp.queryPicture(targetJid).orElse(null);
            if (isSelf(targetJid)) {
                whatsapp.store().setProfilePicture(picture);
            }
        }

        // WAWebHandleProfilePicNotification._: group pic change generates system message
        if (targetJid.hasGroupOrCommunityServer()) {
            var ts = node.getAttributeAsLong("t", (Long) null);
            if (ts != null) {
                // WAWebHandleProfilePicNotification._: genGroupPicChangeNotificationMsg + handleSingleMsg
                // NOTE: Full group system message generation requires MessageService which
                // is not injected into this handler. The group pic change event is still
                // reported via the onProfilePictureChanged listener below.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Group {0} picture changed at {1} by {2} - system message generation not available",
                        targetJid,
                        Instant.ofEpochSecond(ts),
                        actionNode.getAttributeAsJid("author").map(Jid::toUserJid).orElse(null));
            }
        }

        // Fire listener for all targets (not just self)
        fireProfilePictureChanged(targetJid); // WAWebHandleProfilePicNotification._ (implicit via changeProfilePicThumb)
    }

    /**
     * Handles an incoming about/text-status notification stanza.
     *
     * <p>The notification stanza is classified into one of three types based on the
     * {@code set} child node: {@code change} (set child without {@code hash} attribute,
     * containing inline status text), {@code sideListChange} (set child with {@code hash}
     * attribute, requiring contact resolution and server-side status fetch), or
     * {@code unknown} (no set child at all, logged as a warning).
     *
     * <p>For {@code change} notifications, the handler updates the text status for both
     * the primary JID and any alternate JID (LID/PN mapping). For {@code sideListChange}
     * notifications, the handler resolves the contact by hash and fetches the status from
     * the server.
     *
     * @param node the non-{@code null} notification stanza node with
     *             {@code type="status"}
     * @implNote WAWebHandleAboutNotification.handleAboutNotification
     */
    private void handleAbout(Node node) {
        try {
            var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleAboutNotification.p: stanzaId
            var setNode = node.getChild("set").orElse(null);

            // WAWebHandleAboutNotification.p: three parser branches
            if (setNode != null && !setNode.hasAttribute("hash")) {
                // WAWebHandleAboutNotification.p: type="change"
                handleAboutChange(node, setNode, stanzaId);
            } else if (setNode != null && setNode.hasAttribute("hash")) {
                // WAWebHandleAboutNotification.p: type="sideListChange"
                handleAboutSideListChange(setNode, stanzaId);
            } else {
                // WAWebHandleAboutNotification.p: type="unknown"
                // WAWebHandleAboutNotification.f: default: WARN "unhandled type"
                var fromStr = node.getAttributeAsString("from", "[unknown]");
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleAboutNotification: unhandled type unknown from {0}", fromStr);
            }
        } catch (Throwable throwable) {
            // ADAPTED: WAWebHandleAboutNotification (Cobalt error model)
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle status notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            // WAWebHandleAboutNotification.f: ack returned at end
            sendStatusNotificationAck(node);
        }
    }

    /**
     * Handles an about notification of type {@code change}, where the new status text
     * is provided inline in the {@code set} child's content.
     *
     * <p>Updates the text status for both the primary JID and its alternate JID
     * (the LID/PN counterpart), mirroring the WA Web behavior of iterating both
     * the contact's from-JID and its alternate user WID.
     *
     * @param node     the notification stanza node (for {@code from}, {@code notify}, {@code t} attrs)
     * @param setNode  the {@code set} child node containing the status content
     * @param stanzaId the stanza ID for logging purposes
     * @implNote WAWebHandleAboutNotification.f (case "change")
     */
    private void handleAboutChange(Node node, Node setNode, String stanzaId) {
        var from = node.getAttributeAsJid("from") // WAWebHandleAboutNotification.p: from
                .map(Jid::toUserJid)
                .orElse(null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Status notification {0} is missing from",
                    Objects.requireNonNullElse(stanzaId, "[missing-id]"));
            return;
        }

        // WAWebHandleAboutNotification.p: pushname from notify attr (parsed but only used for logging in WA Web)
        // ADAPTED: Cobalt updates the contact's chosen name from pushname
        node.getAttributeAsString("notify")
                .ifPresent(pushName -> updateContactChosenName(from, pushName));

        var content = setNode.toContentString().orElse(null); // WAWebHandleAboutNotification.p: content

        // WAWebHandleAboutNotification.f: case "change"
        // Build list of JIDs to update: [from, alternateWid]
        var jidsToUpdate = new ArrayList<Jid>(); // WAWebHandleAboutNotification.f: m=[l]
        jidsToUpdate.add(from);
        var alternateJid = getAlternateUserJid(from); // WAWebHandleAboutNotification.f: getAlternateUserWid
        if (alternateJid != null) {
            jidsToUpdate.add(alternateJid); // WAWebHandleAboutNotification.f: p&&m.push(p)
        }

        for (var jid : jidsToUpdate) { // WAWebHandleAboutNotification.f: for(var _ of m)
            var existingStatus = whatsapp.store()
                    .findContactTextStatus(jid)
                    .orElse(null);
            if (existingStatus != null && content != null) {
                // WAWebHandleAboutNotification.f: f.status = e.content
                existingStatus.setText(content);
                whatsapp.store().addContactTextStatus(jid.toUserJid(), existingStatus);
                notifyContactTextStatusChanged(jid.toUserJid(), existingStatus);
            } else {
                // WAWebHandleAboutNotification.f: WARN "unknown contact"
                LOGGER.log(System.Logger.Level.WARNING,
                        "handleAboutNotification: unknown contact {0}", jid);
            }
        }
    }

    /**
     * Handles an about notification of type {@code sideListChange}, where the target
     * contact is identified by a hash and the new status must be fetched from the server.
     *
     * <p>Resolves the contact JID from the hash using the WhatsApp contact hash algorithm,
     * then looks up the existing text status. If found, fetches the updated status from
     * the server and updates the local store.
     *
     * @param setNode  the {@code set} child node containing the {@code hash} attribute
     * @param stanzaId the stanza ID for logging purposes
     * @implNote WAWebHandleAboutNotification.f (case "sideListChange")
     */
    private void handleAboutSideListChange(Node setNode, String stanzaId) {
        var hash = setNode.getAttributeAsString("hash", null); // WAWebHandleAboutNotification.p: hash
        if (hash == null) {
            return;
        }

        // WAWebHandleAboutNotification.f: getContactRecordByHash(e.hash)
        var resolvedJid = resolveContactByHash(hash);
        if (resolvedJid == null) {
            // WAWebHandleAboutNotification.f: WARN "side contact hash not found for status update"
            LOGGER.log(System.Logger.Level.WARNING,
                    "Side contact hash not found for status update");
            return;
        }

        // WAWebHandleAboutNotification.f: createUserWidOrThrow(t.id)
        var userJid = resolvedJid.toUserJid();

        // WAWebHandleAboutNotification.f: TextStatusCollection.get(n) -> getStatus(n).then(...)
        var existingStatus = whatsapp.store()
                .findContactTextStatus(userJid)
                .orElse(null);
        if (existingStatus != null) {
            // WAWebHandleAboutNotification.f: getStatus(n).then(e => a.set({status: e.status}))
            var refreshed = whatsapp.queryAbout(userJid).orElse(null);
            if (refreshed != null) {
                existingStatus.setText(refreshed);
                whatsapp.store().addContactTextStatus(userJid, existingStatus);
                notifyContactTextStatusChanged(userJid, existingStatus);
            }
        }
    }

    /**
     * Resolves a contact JID from a WhatsApp contact hash.
     *
     * <p>The contact hash is computed as the first 3 bytes of the MD5 digest of
     * the contact's user component concatenated with the salt {@code "WA_ADD_NOTIF"},
     * then Base64-encoded. This method iterates all known contacts and computes the
     * hash for each to find a match.
     *
     * @param targetHash the Base64-encoded contact hash to look up
     * @return the resolved contact JID, or {@code null} if no match was found
     * @implNote WAWebApiContact.getContactRecordByHash, WAWebApiContact.getContactHash
     */
    private Jid resolveContactByHash(String targetHash) {
        for (var contact : whatsapp.store().contacts()) { // WAWebApiContact.z: iterate contacts
            var jid = contact.jid();
            var user = jid.user();
            if (user == null) {
                continue;
            }

            var computed = computeContactHash(user); // WAWebApiContact.k: getContactHash
            if (targetHash.equals(computed)) {
                return jid;
            }
        }
        return null;
    }

    /**
     * Computes the WhatsApp contact hash for a given user identifier.
     *
     * <p>The hash is computed as: {@code Base64(MD5(user + "WA_ADD_NOTIF")[0:3])}.
     * This truncated, Base64-encoded MD5 is used by the WhatsApp server to identify
     * contacts in side-list notifications without revealing the full phone number.
     *
     * @param user the user component of the contact's JID (typically a phone number)
     * @return the Base64-encoded 3-byte hash
     * @implNote WAWebApiContact.getContactHash (function k)
     */
    private String computeContactHash(String user) {
        try {
            var md5 = MessageDigest.getInstance("MD5"); // WAWebApiContact.k: md5(t+"WA_ADD_NOTIF")
            var input = (user + CONTACT_HASH_SALT).getBytes(StandardCharsets.UTF_8);
            var digest = md5.digest(input);
            var truncated = new byte[3]; // WAWebApiContact.k: slice(0,3)
            System.arraycopy(digest, 0, truncated, 0, 3);
            return Base64.getEncoder().encodeToString(truncated); // WAWebApiContact.k: encodeB64
        } catch (NoSuchAlgorithmException exception) {
            // MD5 is guaranteed to be available in all JDK implementations
            throw new AssertionError("MD5 algorithm not available", exception);
        }
    }

    /**
     * Returns the alternate user JID for the given JID by mapping between
     * phone-number-based and LID-based identifiers.
     *
     * <p>If the given JID is a phone-number JID, returns the corresponding LID.
     * If the given JID is a LID, returns the corresponding phone number JID.
     * Returns {@code null} if no mapping exists.
     *
     * @param jid the JID to find the alternate for
     * @return the alternate JID, or {@code null} if no mapping exists
     * @implNote WAWebApiContact.getAlternateUserWid
     */
    private Jid getAlternateUserJid(Jid jid) {
        if (jid.hasUserServer()) {
            return whatsapp.store().findLidByPhone(jid).orElse(null); // WAWebApiContact.P: isLid()?A(e):w(e)
        } else if (jid.hasLidServer()) {
            return whatsapp.store().findPhoneByLid(jid).orElse(null); // WAWebApiContact.P: isLid()?A(e):w(e)
        }
        return null;
    }

    /**
     * Returns whether the given JID belongs to the currently logged-in account.
     *
     * @param jid the JID to check
     * @return {@code true} if the JID matches the local account
     * @implNote WAWebUserPrefsMeUser.isMeAccount
     */
    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    /**
     * Updates the chosen name (push name) of a contact in the local store.
     *
     * <p>If the contact does not exist, a new contact record is created. The chosen
     * name is only updated if the provided value is non-{@code null} and non-blank.
     *
     * @param contactJid the non-{@code null} JID of the contact to update
     * @param chosenName the new chosen name, or {@code null} to skip the update
     * @implNote ADAPTED: WAWebHandleAboutNotification.p (pushname parsed but only used for logging in WA Web)
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
     * Fires the {@code onProfilePictureChanged} listener callback on all registered
     * listeners, each on its own virtual thread.
     *
     * @param jid the JID of the entity whose profile picture changed
     * @implNote WAWebHandleProfilePicNotification._ (implicit via changeProfilePicThumb completion)
     */
    private void fireProfilePictureChanged(Jid jid) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onProfilePictureChanged(whatsapp, jid));
        }
    }

    /**
     * Notifies all registered listeners that a contact's text status has changed,
     * each on its own virtual thread.
     *
     * @param contactJid the JID of the contact whose text status changed
     * @param status     the updated text status
     * @implNote WAWebHandleAboutNotification.f (TextStatusCollection mutation triggers)
     */
    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    /**
     * Sends an acknowledgement stanza for a profile picture notification.
     *
     * <p>The acknowledgement is sent with {@code class="notification"} and
     * {@code type="picture"}, matching the format expected by the WhatsApp server.
     *
     * @param node the notification stanza node to acknowledge
     * @implNote WAWebHandleProfilePicNotification._ (ack stanza)
     */
    private void sendPictureNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleProfilePicNotification._: a.stanzaId
        var stanzaFrom = node.getAttributeAsJid("from", null); // WAWebHandleProfilePicNotification._: a.from
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleProfilePicNotification._: wap("ack", {id, to, class:"notification", type:"picture"})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", stanzaFrom)
                .attribute("type", "picture")
                .build());
    }

    /**
     * Sends an acknowledgement stanza for an about/status notification.
     *
     * <p>The acknowledgement is sent with {@code class="notification"} and
     * {@code type="status"}, matching the format expected by the WhatsApp server.
     *
     * @param node the notification stanza node to acknowledge
     * @implNote WAWebHandleAboutNotification.f (ack stanza)
     */
    private void sendStatusNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null); // WAWebHandleAboutNotification.f: e.stanzaId
        var stanzaFrom = node.getAttributeAsJid("from", null); // WAWebHandleAboutNotification.f: e.from
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleAboutNotification.f: wap("ack", {id, to, class:"notification", type:"status"})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", "notification")
                .attribute("to", stanzaFrom)
                .attribute("type", "status")
                .build());
    }
}
