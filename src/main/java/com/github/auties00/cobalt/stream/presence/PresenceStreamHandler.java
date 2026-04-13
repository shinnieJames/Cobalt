package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.Set;

/**
 * Handles incoming {@code <presence>} stanzas from the WhatsApp server.
 *
 * <p>Each presence stanza carries the online/offline status of a contact, along
 * with an optional last-seen timestamp. When the contact's privacy settings hide
 * the last-seen information, the stanza carries a {@code last="deny"} attribute
 * instead of a numeric timestamp.
 *
 * <p>This handler updates the contact's {@linkplain Contact#lastKnownPresence()
 * presence state} and {@linkplain Contact#lastSeen() last-seen timestamp} in the
 * local store, then notifies registered listeners.
 *
 * @implNote WAWebHandlePresence.default and WAWebChangePresenceHandlerAction.default
 */
public final class PresenceStreamHandler implements SocketStream.Handler {
    /**
     * The logger for diagnostic messages related to presence handling.
     *
     * @implNote WAWebHandlePresence -- WALogger.ERROR usage
     */
    private static final System.Logger LOGGER = System.getLogger(PresenceStreamHandler.class.getName());

    /**
     * The set of {@code last} attribute values that indicate the contact has hidden
     * their last-seen timestamp through privacy settings. When the {@code last}
     * attribute matches one of these values, the last-seen timestamp is not updated
     * and is instead cleared.
     *
     * @implNote WAWebHandlePresence -- var c = ["deny", "none", "error"]
     */
    private static final Set<String> HIDDEN_LAST_VALUES = Set.of("deny", "none", "error");

    /**
     * The WhatsApp client instance used to access the store and notify listeners.
     *
     * @implNote WAWebHandlePresence -- constructor DI replaces module-level store access
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new presence stream handler with the given WhatsApp client.
     *
     * @param whatsapp the non-{@code null} WhatsApp client instance
     * @implNote WAWebHandlePresence -- module-level initialization
     */
    public PresenceStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming {@code <presence>} stanza by extracting the contact's
     * online/offline status and optional last-seen timestamp, updating the
     * corresponding {@link Contact} in the store, and notifying listeners.
     *
     * <p>The handler performs the following steps:
     * <ol>
     *   <li>Extracts the {@code from} attribute as a JID</li>
     *   <li>Skips the update if the JID belongs to the current user's own account</li>
     *   <li>Resolves the JID to a canonical contact identifier (LID to PN resolution)</li>
     *   <li>Reads the {@code type} attribute (defaulting to {@code "available"})</li>
     *   <li>Updates the contact's presence state and last-seen timestamp</li>
     *   <li>Persists the contact and fires a presence change notification</li>
     * </ol>
     *
     * @param node the non-{@code null} presence stanza node
     * @implNote WAWebHandlePresence.default -- async function m(t)
     */
    @Override
    public void handle(Node node) {
        // WAWebHandlePresence.default: var l = chatJidToChatWid(i.value.from)
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring presence stanza without from: {0}", node);
            return;
        }

        // WAWebChangePresenceHandlerAction.default: if (!isMeAccount(a))
        // isMeAccount checks both PN and LID user; Cobalt checks the PN user
        // directly and resolves LID-based from JIDs via findPhoneByLid
        var meJid = whatsapp.store().jid().orElse(null);
        if (meJid != null && isSelfPresence(from, meJid)) {
            return; // WAWebChangePresenceHandlerAction.default -- skip self-presence
        }

        // ADAPTED: WAWebHandlePresence.default -- LID/PN resolution
        // WA Web checks isLidMigrated() and has two paths:
        //   - Not migrated: convert LID to PN via getPhoneNumber
        //   - Migrated: reject PN presence, resolve LID via getChatByAccountLid
        // Cobalt treats isLidMigrated() as always true and resolves LIDs to PNs
        // via findPhoneByLid, which is architecturally equivalent since Cobalt
        // stores contacts by PN JID rather than using a PresenceCollection keyed by wid.
        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        // WAWebHandlePresence.default: type: i.value.type || "available"
        var type = node.getAttributeAsString("type", "available");
        var status = "unavailable".equals(type) ? ContactStatus.UNAVAILABLE : ContactStatus.AVAILABLE;
        contact.setLastKnownPresence(status); // WAWebChangePresenceHandlerAction.default -- r.set(t)

        // WAWebHandlePresence.default: deny: i.value.last === "deny" || void 0
        // WAWebHandlePresence.default: t: i.value.type === "unavailable" ? d(i.value.last) : void 0
        var lastValue = node.getAttributeAsString("last", null);
        var deny = "deny".equals(lastValue); // WAWebHandlePresence.default -- deny flag
        var lastSeen = resolveLastSeen(lastValue, status);
        if (lastSeen != null) {
            contact.setLastSeen(lastSeen); // WAWebPresenceModel.Chatstate -- t field
        } else if (deny || status == ContactStatus.AVAILABLE) {
            // WAWebPresenceModel.Chatstate -- deny: true clears last-seen display,
            // and available status also clears the timestamp.
            // Setting to Instant.EPOCH (0) causes Contact.lastSeen() to return
            // Optional.empty() since lastSeenSeconds <= 0 is treated as absent.
            contact.setLastSeen(Instant.EPOCH); // WAWebChangePresenceHandlerAction.default -- chatstate.set(t) with t=undefined
        }

        whatsapp.store().addContact(contact); // ADAPTED: WAWebPresenceCollection store operations
        notifyPresence(contact.toJid(), contact.toJid()); // ADAPTED: WAWebChangePresenceHandlerAction.default -- non-group path
    }

    /**
     * Resolves the last-seen timestamp from the {@code last} attribute value and the
     * presence status.
     *
     * <p>The resolution logic mirrors the WA Web {@code d(e)} helper function:
     * <ul>
     *   <li>If the status is not {@link ContactStatus#UNAVAILABLE}, returns {@code null}
     *       (last-seen is only meaningful for offline contacts)</li>
     *   <li>If the {@code last} value is {@code null}, returns the current time
     *       (the contact just went offline)</li>
     *   <li>If the {@code last} value is one of the hidden values ({@code "deny"},
     *       {@code "none"}, {@code "error"}), returns {@code null}</li>
     *   <li>Otherwise, parses the value as a unix epoch second</li>
     * </ul>
     *
     * @param lastValue the raw {@code last} attribute value from the stanza, or {@code null}
     * @param status    the resolved presence status
     * @return the last-seen instant, or {@code null} if not applicable
     * @implNote WAWebHandlePresence -- function d(e)
     */
    private Instant resolveLastSeen(String lastValue, ContactStatus status) {
        // WAWebHandlePresence.default: t: i.value.type === "unavailable" ? d(i.value.last) : void 0
        if (status != ContactStatus.UNAVAILABLE) {
            return null;
        }

        // WAWebHandlePresence -- d(e): if (e != null) { if (!c.includes(e)) return castToUnixTime(Number(e)) } else return unixTime()
        if (lastValue == null) {
            return Instant.now(); // WAWebHandlePresence -- d(e): else return unixTime()
        }

        if (HIDDEN_LAST_VALUES.contains(lastValue)) {
            return null; // WAWebHandlePresence -- d(e): if (!c.includes(e)) -- deny/none/error return undefined
        }

        try {
            return Instant.ofEpochSecond(Long.parseLong(lastValue)); // WAWebHandlePresence -- d(e): castToUnixTime(Number(e))
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring malformed presence last value {0}", lastValue);
            return null;
        }
    }

    /**
     * Determines whether the given {@code from} JID represents the current user's
     * own account.
     *
     * <p>This check mirrors WA Web's {@code isMeAccount} which compares both the
     * phone-number user and the LID user. In Cobalt, the PN-based check is direct;
     * LID-based from JIDs are resolved through the LID-to-PN cache.
     *
     * @param from  the JID from the presence stanza
     * @param meJid the current user's device JID
     * @return {@code true} if the presence is for the current user's own account
     * @implNote WAWebUserPrefsMeUser.isMeAccount -- function $(e)
     */
    private boolean isSelfPresence(Jid from, Jid meJid) {
        var fromUser = from.toUserJid();
        var meUser = meJid.toUserJid();
        // WAWebUserPrefsMeUser.isMePnUser: checks PN user part match
        if (fromUser.user().equals(meUser.user())) {
            return true;
        }
        // WAWebUserPrefsMeUser.isMeAccount: also checks LID match via getMaybeMeLidUser
        if (fromUser.hasLidServer()) {
            var phoneJid = whatsapp.store().findPhoneByLid(fromUser).orElse(null);
            return phoneJid != null && phoneJid.user().equals(meUser.user());
        }
        return false;
    }

    /**
     * Resolves the given JID to a canonical contact, creating a new contact if none
     * exists in the store.
     *
     * <p>If the JID has a LID server, it is first resolved to a phone-number JID
     * via the store's LID-to-PN cache. This mirrors the WA Web behavior where
     * {@code WAWebApiContact.getPhoneNumber} converts LID JIDs to phone-number JIDs
     * before looking up the contact.
     *
     * @param jid the JID from the presence stanza
     * @return the resolved contact, or {@code null} if the JID cannot be resolved
     * @implNote ADAPTED: WAWebHandlePresence.default -- LID/PN resolution via
     *           WAWebApiContact.getPhoneNumber and WAWebChatCollection.getChatByAccountLid
     */
    private Contact getOrCreateContact(Jid jid) {
        if (jid == null) {
            return null; // NO_WA_BASIS -- defensive null check
        }

        // ADAPTED: WAWebHandlePresence.default -- var p = getPhoneNumber(l); if (p == null) return; l = p
        var canonical = jid.toUserJid().hasLidServer()
                ? whatsapp.store().findPhoneByLid(jid.toUserJid()).orElse(jid.toUserJid())
                : jid.toUserJid();
        return whatsapp.store()
                .findContactByJid(canonical)
                .orElseGet(() -> whatsapp.store().addNewContact(canonical));
    }

    /**
     * Notifies all registered listeners about a presence change for the given
     * conversation and participant.
     *
     * <p>Each listener is invoked on a separate virtual thread to avoid blocking
     * the handler.
     *
     * @param conversation the JID of the conversation where the presence changed
     * @param participant  the JID of the participant whose presence changed
     * @implNote ADAPTED: WAWebChangePresenceHandlerAction.default -- non-group
     *           presence update triggers UI refresh via model observation; Cobalt
     *           uses an explicit listener notification instead
     */
    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, conversation, participant));
        }
    }
}
