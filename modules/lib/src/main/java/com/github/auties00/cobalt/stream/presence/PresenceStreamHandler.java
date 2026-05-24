package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.Set;

/**
 * Updates the {@link Contact#lastKnownPresence()} and {@link Contact#lastSeen()}
 * of the contact identified by a {@code <presence>} stanza.
 *
 * @apiNote
 * Drives the "online/last seen" surface shown next to a contact's name in
 * WhatsApp clients. The handler runs whenever the server pushes a presence
 * update for any subscribed contact (presence subscription is requested
 * elsewhere via {@code subscribePresence}). The privacy sentinels
 * {@code "deny"}, {@code "none"} and {@code "error"} carried in the
 * {@code last} attribute mean the peer's privacy settings hide their
 * last-seen timestamp; in that case the existing timestamp is left untouched.
 *
 * @implNote
 * This implementation merges {@code WAWebHandlePresence} (the per-stanza
 * router) with the {@code WAWebChangePresenceHandlerAction} default export
 * (the action handler that mutates {@code PresenceCollection}). WA Web
 * distinguishes a "GroupAvailable" / "GroupUnavailable" presence variant
 * that updates the per-group viewer count; that path is not implemented
 * here. WA Web also surfaces {@code deny} as a {@code forceDisplay} UI hint
 * on the {@code Presence} model. Cobalt has no such UI hint, so the
 * {@code last="deny"} sentinel is consumed by {@link #resolveLastSeen(String, ContactStatus)}
 * and discarded.
 */
@WhatsAppWebModule(moduleName = "WAWebHandlePresence")
@WhatsAppWebModule(moduleName = "WAWebChangePresenceHandlerAction")
public final class PresenceStreamHandler implements SocketStream.Handler {
    /**
     * The {@link System.Logger} used to report stanzas that cannot be parsed
     * or that carry malformed {@code last} timestamps.
     */
    private static final System.Logger LOGGER = System.getLogger(PresenceStreamHandler.class.getName());

    /**
     * The set of {@code last}-attribute sentinel strings that mean the peer
     * has hidden their last-seen timestamp via privacy settings.
     *
     * @implNote
     * This implementation mirrors the WA Web module-local constant
     * {@code c = ["deny","none","error"]} in {@code WAWebHandlePresence}.
     * When the {@code last} value matches one of these strings, the contact's
     * existing {@link Contact#lastSeen()} is left untouched rather than
     * overwritten with {@code null}.
     */
    private static final Set<String> HIDDEN_LAST_VALUES = Set.of("deny", "none", "error");

    /**
     * The {@link WhatsAppClient} that owns the store this handler mutates and
     * whose listeners receive the resulting {@code onContactPresence}
     * notifications.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@link PresenceStreamHandler} bound to the given
     * {@link WhatsAppClient}.
     *
     * @apiNote
     * Constructed by the {@link SocketStream} wiring; embedders do not call
     * this directly. The supplied client must have an initialized
     * {@link com.github.auties00.cobalt.store.AbstractWhatsAppStore store}.
     *
     * @param whatsapp the non-{@code null} client whose store is updated
     *                 and whose listeners are notified
     */
    @WhatsAppWebExport(moduleName = "WAWebHandlePresence", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public PresenceStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Consumes one {@code <presence>} stanza for a non-self contact, sets
     * {@link ContactStatus#AVAILABLE} or {@link ContactStatus#UNAVAILABLE}
     * on the matching {@link Contact}, updates {@link Contact#lastSeen()}
     * via {@link #resolveLastSeen(String, ContactStatus)}, persists the
     * contact and dispatches {@code onContactPresence} to every registered
     * listener on a fresh virtual thread.
     *
     * @implNote
     * This implementation diverges from WA Web in three places.
     * <ul>
     * <li>WA Web's {@code WAWebHandlePresence} default export dispatches
     * {@code GroupAvailable} / {@code GroupUnavailable} variants to
     * {@code WAWebChangeGroupPresenceHandlerAction}; Cobalt does not
     * implement that group-viewer-count surface, so those variants flow
     * through to the per-contact path here.</li>
     * <li>WA Web gates the entire path behind
     * {@code Lid1X1MigrationUtils.isLidMigrated()} and logs an error if a
     * migrated client receives a PN presence; Cobalt does not maintain
     * that gate and accepts both PN and LID {@code from} attributes.</li>
     * <li>The {@code deny} flag that WA Web propagates as a
     * {@code forceDisplay} UI hint on {@code Presence} is dropped, because
     * Cobalt is headless and has no equivalent display state.</li>
     * </ul>
     *
     * @param node {@inheritDoc}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandlePresence", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebChangePresenceHandlerAction", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring presence stanza without from: {0}", node);
            return;
        }

        var meJid = whatsapp.store().jid().orElse(null);
        if (meJid != null && isSelfPresence(from, meJid)) {
            return;
        }

        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        var type = node.getAttributeAsString("type", "available");
        var status = "unavailable".equals(type) ? ContactStatus.UNAVAILABLE : ContactStatus.AVAILABLE;
        contact.setLastKnownPresence(status);

        var lastValue = node.getAttributeAsString("last", null);
        var lastSeen = resolveLastSeen(lastValue, status);
        if (lastSeen != null) {
            contact.setLastSeen(lastSeen);
        }
        whatsapp.store().addContact(contact);
        notifyPresence(contact.toJid(), contact.toJid());
    }

    /**
     * Returns the {@link Instant} to store on {@link Contact#lastSeen()}
     * given the raw {@code last} attribute value and the resolved presence
     * {@code status}, or {@code null} when the existing timestamp should be
     * preserved.
     *
     * @apiNote
     * Encodes the four-way classifier that callers from
     * {@link #handle(Node)} use to drive {@link Contact#setLastSeen(Instant)}.
     * Returns {@code null} for an online contact (last-seen is only relevant
     * to offline contacts), {@link Instant#now()} when an offline contact
     * has no {@code last} attribute at all (the contact just went offline),
     * {@code null} for any of the {@link #HIDDEN_LAST_VALUES} (privacy
     * settings hide the timestamp), and otherwise the unix epoch second
     * parsed from the attribute.
     *
     * @implNote
     * This implementation is the bit-for-bit equivalent of WA Web's helper
     * {@code d(e)} inside {@code WAWebHandlePresence}, which calls
     * {@code WATimeUtils.castToUnixTime(Number(e))} for numeric values and
     * {@code WATimeUtils.unixTime()} when the attribute is absent. Cobalt
     * additionally swallows {@link NumberFormatException} on malformed
     * timestamps; WA Web's wrapping {@code Number(e)} would produce
     * {@code NaN} and propagate. Returning {@code null} instead is
     * consistent with the WA Web "leave existing timestamp untouched"
     * semantic encoded in {@link #handle(Node)} above.
     *
     * @param lastValue the raw {@code last} attribute, or {@code null} when
     *                  the stanza did not carry one
     * @param status    the {@link ContactStatus} just derived from the
     *                  {@code type} attribute
     * @return the timestamp to store, or {@code null} to leave the existing
     *         {@link Contact#lastSeen()} untouched
     */
    private Instant resolveLastSeen(String lastValue, ContactStatus status) {
        if (status != ContactStatus.UNAVAILABLE) {
            return null;
        }

        if (lastValue == null) {
            return Instant.now();
        }

        if (HIDDEN_LAST_VALUES.contains(lastValue)) {
            return null;
        }

        try {
            return Instant.ofEpochSecond(Long.parseLong(lastValue));
        } catch (NumberFormatException exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring malformed presence last value {0}", lastValue);
            return null;
        }
    }

    /**
     * Returns {@code true} when the given {@code from} JID refers to the
     * client's own account.
     *
     * @apiNote
     * Suppresses self-echoes: WA Web's {@code WAWebUserPrefsMeUser.isMeAccount}
     * filters presence updates for the local user out of the presence-driven
     * UI, and this method is the {@link #handle(Node)} equivalent.
     *
     * @implNote
     * This implementation accepts a {@link Jid} on either the PN server or
     * the LID server. The LID-server branch consults the store's
     * LID-to-PN cache via {@code findPhoneByLid}; WA Web instead carries
     * both ids inside {@code WAWebUserPrefsMeUser} and compares them
     * directly without a cache lookup.
     *
     * @param from  the {@code from} attribute of the {@code <presence>} stanza
     * @param meJid the client's own JID from {@code store().jid()}
     * @return {@code true} when {@code from} and {@code meJid} resolve to the
     *         same user; {@code false} otherwise
     */
    private boolean isSelfPresence(Jid from, Jid meJid) {
        var fromUser = from.toUserJid();
        var meUser = meJid.toUserJid();
        if (fromUser.user().equals(meUser.user())) {
            return true;
        }
        if (fromUser.hasLidServer()) {
            var phoneJid = whatsapp.store().findPhoneByLid(fromUser).orElse(null);
            return phoneJid != null && phoneJid.user().equals(meUser.user());
        }
        return false;
    }

    /**
     * Returns the {@link Contact} stored under the canonical PN form of
     * {@code jid}, creating a fresh entry when none exists.
     *
     * @apiNote
     * Centralizes the LID-to-PN normalization every presence-driven contact
     * lookup needs. Embedders that consume {@code onContactPresence} can
     * therefore assume the {@link Contact#toJid()} they receive is on the
     * PN server even when the wire stanza carried a LID-server {@code from}.
     *
     * @implNote
     * This implementation maps a LID-server {@link Jid} to its PN
     * counterpart via the store's LID-to-PN cache and falls back to the LID
     * form when the cache misses. WA Web's equivalent path goes through
     * {@code WAWebJidToWid.userJidToUserWid} followed by
     * {@code WAWebApiContact.getPhoneNumber}; Cobalt collapses the lookup
     * into one store call because the LID-to-PN cache is the only source of
     * truth for the mapping.
     *
     * @param jid the {@link Jid} read off the stanza, possibly {@code null}
     * @return the resolved {@link Contact}, or {@code null} when
     *         {@code jid} is {@code null}
     */
    private Contact getOrCreateContact(Jid jid) {
        if (jid == null) {
            return null;
        }

        var canonical = jid.toUserJid().hasLidServer()
                ? whatsapp.store().findPhoneByLid(jid.toUserJid()).orElse(jid.toUserJid())
                : jid.toUserJid();
        return whatsapp.store()
                .findContactByJid(canonical)
                .orElseGet(() -> whatsapp.store().addNewContact(canonical));
    }

    /**
     * Fans out an {@code onContactPresence} notification to every registered
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener}.
     *
     * @apiNote
     * The {@code conversation} and {@code participant} arguments are equal
     * for {@code <presence>} stanzas (presence is always per-contact, never
     * per-group); they are kept distinct in the listener signature so the
     * same callback can also be invoked from {@code <chatstate>} handlers
     * where the conversation is a group JID.
     *
     * @implNote
     * This implementation starts one virtual thread per listener so that a
     * blocking listener cannot stall the {@link SocketStream} dispatch loop.
     *
     * @param conversation the JID of the conversation that experienced the
     *                     presence change
     * @param participant  the JID of the participant whose presence changed
     */
    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, conversation, participant));
        }
    }
}
