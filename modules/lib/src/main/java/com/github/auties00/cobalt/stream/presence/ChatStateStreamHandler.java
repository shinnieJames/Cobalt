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

/**
 * Updates the {@link Contact#lastKnownPresence()} of the contact identified
 * by a {@code <chatstate>} stanza.
 *
 * @apiNote
 * Drives the "typing..." / "recording audio..." indicator shown in the
 * conversation header. The server pushes one {@code <chatstate>} stanza per
 * transition between composing states; for groups, the stanza carries a
 * {@code participant} attribute naming the device currently composing
 * inside the group, and {@code from} carries the group JID.
 *
 * @implNote
 * This implementation collapses WA Web's three-stage flow into one method:
 * {@code WACreateHandleChatState.createHandleChatState} (the factory that
 * splits per source kind), {@code WAWebHandleChatState} (the per-source
 * handlers) and {@code WAWebChangePresenceHandlerAction} (the action that
 * mutates {@code PresenceCollection}). The split is performed inline in
 * {@link #handle(Node)} by inspecting the {@code participant} attribute
 * instead of by reading {@code stateSource} on a parsed RPC. WA Web
 * additionally maintains a 25-second auto-expiry timer per typing
 * indicator inside {@code WAWebChangePresenceHandlerAction}; Cobalt
 * exposes only the raw {@link ContactStatus} transition to its listeners
 * and leaves expiry policy to the embedder.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleChatState")
@WhatsAppWebModule(moduleName = "WACreateHandleChatState")
@WhatsAppWebModule(moduleName = "WAHandleChatStateProtocol")
@WhatsAppWebModule(moduleName = "WAWebChangePresenceHandlerAction")
public final class ChatStateStreamHandler implements SocketStream.Handler {
    /**
     * The {@link System.Logger} used to report stanzas with missing or
     * unsupported children.
     */
    private static final System.Logger LOGGER = System.getLogger(ChatStateStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} that owns the store this handler mutates and
     * whose listeners receive the resulting {@code onContactPresence}
     * notifications.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new {@link ChatStateStreamHandler} bound to the given
     * {@link WhatsAppClient}.
     *
     * @apiNote
     * Constructed by the {@link SocketStream} wiring; embedders do not
     * call this directly.
     *
     * @param whatsapp the non-{@code null} client whose store is updated
     *                 and whose listeners are notified
     */
    public ChatStateStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Consumes one {@code <chatstate>} stanza and routes it to either
     * {@link #handleIndividualChatState(Jid, ContactStatus)} (no
     * {@code participant} attribute) or
     * {@link #handleGroupChatState(Jid, Jid, ContactStatus)} (when the
     * stanza carries {@code participant}). Stanzas without a {@code from}
     * attribute or with no recognized composing child are dropped after a
     * debug log entry.
     *
     * @implNote
     * This implementation merges {@code WACreateHandleChatState.createHandleChatState},
     * which returns a closure that first parses the stanza via
     * {@code WASmaxChatstateServerNotificationRPC.receiveServerNotificationRPC}
     * to discriminate {@code FromUser} versus {@code FromGroup} sources.
     * The discriminator here is the presence of the {@code participant}
     * attribute, which is equivalent on the wire because WA Web's RPC
     * parser ultimately reads the same attribute when building
     * {@code stateSource}.
     *
     * @param node {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WACreateHandleChatState", exports = "createHandleChatState",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring chatstate stanza without from: {0}", node);
            return;
        }

        var participant = node.getAttributeAsJid("participant", null);

        var state = resolveState(node);
        if (state == null) {
            return;
        }

        if (participant != null) {
            handleGroupChatState(from, participant, state);
        } else {
            handleIndividualChatState(from, state);
        }
    }

    /**
     * Applies an individual (one-to-one) chatstate update for the sender
     * identified by {@code from}.
     *
     * @apiNote
     * The {@code from} attribute on a 1:1 {@code <chatstate>} stanza names
     * the peer; the notification fans out with the same JID as both the
     * conversation and the participant because there is no group context.
     * Stanzas about the current account are silently dropped.
     *
     * @implNote
     * This implementation diverges from WA Web's
     * {@code handleIndividualChatState} in two places. WA Web dispatches
     * twice (once for the resolved id and once for the PN counterpart)
     * when the client is in pre-migration LID mode and the sender is a
     * LID-server JID; Cobalt instead canonicalizes the JID to its PN form
     * via {@link #getOrCreateContact(Jid)} and dispatches once. WA Web
     * runs the action handler asynchronously through
     * {@code WAWebBackendApi.frontendFireAndForget("changePresenceHandler", ...)};
     * Cobalt invokes the listener inline through
     * {@link #notifyPresence(Jid, Jid)}.
     *
     * @param from  the {@code from} attribute of the chatstate stanza
     * @param state the {@link ContactStatus} previously resolved from the
     *              stanza child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleChatState", exports = "handleIndividualChatState",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleIndividualChatState(Jid from, ContactStatus state) {
        var meJid = whatsapp.store().jid().orElse(null);
        if (meJid != null && isSelf(from, meJid)) {
            return;
        }

        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        contact.setLastKnownPresence(state);
        whatsapp.store().addContact(contact);
        notifyPresence(contact.toJid(), contact.toJid());
    }

    /**
     * Applies a per-participant chatstate update inside the group identified
     * by {@code from}.
     *
     * @apiNote
     * The notification fans out with the group JID as the conversation and
     * the participant's user JID as the participant, so listeners can
     * scope the typing indicator to a single conversation header without
     * polluting other surfaces.
     *
     * @implNote
     * This implementation diverges from WA Web's
     * {@code handleGroupChatState} in two places. WA Web's downstream
     * {@code WAWebChangePresenceHandlerAction} action handler maintains a
     * per-group {@code chatstates} sub-collection keyed by participant
     * and an auto-expiry timer of 25 seconds for typing or audio-recording
     * states; Cobalt does not model either and simply overwrites the
     * participant's {@link Contact#lastKnownPresence()} for as long as the
     * server keeps pushing updates. WA Web additionally drops the update
     * when the participant has no PN mapping (a pre-migration safety
     * check); Cobalt forwards the update unconditionally because its
     * contact store always holds a canonical PN entry after
     * {@link #getOrCreateContact(Jid)} runs.
     *
     * @param from        the group JID from the stanza
     * @param participant the participant JID that produced the composing
     *                    update
     * @param state       the {@link ContactStatus} previously resolved from
     *                    the stanza child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleChatState", exports = "handleGroupChatState",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleGroupChatState(Jid from, Jid participant, ContactStatus state) {
        var contact = getOrCreateContact(participant);
        if (contact == null) {
            return;
        }

        contact.setLastKnownPresence(state);
        whatsapp.store().addContact(contact);
        notifyPresence(from, contact.toJid());
    }

    /**
     * Returns the {@link ContactStatus} encoded by the single child of a
     * {@code <chatstate>} stanza, or {@code null} when the child is absent
     * or unsupported.
     *
     * @apiNote
     * Caller-facing semantics: {@code <composing>} maps to
     * {@link ContactStatus#COMPOSING}, {@code <composing media="audio">} to
     * {@link ContactStatus#RECORDING}, and {@code <paused>} to
     * {@link ContactStatus#AVAILABLE}.
     *
     * @implNote
     * This implementation fuses the parsing of
     * {@code WASmaxInChatstateStateTypes.parseStateTypes} (which classifies
     * the child as {@code Composing}, {@code ComposingMedia} or
     * {@code Paused}) and {@code WAHandleChatStateProtocol.parseChatStatus}
     * (which maps the classification to {@code "typing"},
     * {@code "recording_audio"} or {@code "idle"}). The {@code "idle"}
     * outcome is then refined inside
     * {@code WAWebChangePresenceHandlerAction.s} to either
     * {@code "available"} or {@code "unavailable"} depending on the
     * participant's {@code isOnline} flag in {@code PresenceCollection}.
     * Cobalt has no per-contact {@code isOnline} tracker (presence comes
     * exclusively through {@link PresenceStreamHandler}), so the paused
     * branch resolves to {@link ContactStatus#AVAILABLE}, matching the
     * common case where the peer was composing and then stopped without
     * going offline.
     *
     * @param node the {@code <chatstate>} stanza whose first child encodes
     *             the composing state
     * @return the resolved {@link ContactStatus}, or {@code null} when the
     *         child is missing or carries an unsupported tag
     */
    @WhatsAppWebExport(moduleName = "WAHandleChatStateProtocol", exports = "parseChatStatus",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInChatstateStateTypes", exports = "parseStateTypes",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private ContactStatus resolveState(Node node) {
        var child = node.getChild().orElse(null);
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring empty chatstate stanza: {0}", node);
            return null;
        }

        return switch (child.description()) {
            case "composing" -> "audio".equals(child.getAttributeAsString("media", null))
                    ? ContactStatus.RECORDING
                    : ContactStatus.COMPOSING;
            case "paused" -> ContactStatus.AVAILABLE;
            default -> {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported chatstate child {0} in {1}",
                        child.description(), node);
                yield null;
            }
        };
    }

    /**
     * Returns {@code true} when the given {@code from} JID refers to the
     * client's own account.
     *
     * @apiNote
     * Suppresses self-echoes during a 1:1 chatstate update; the group path
     * does not need this check because the group {@code from} JID is never
     * the local user's JID.
     *
     * @implNote
     * This implementation accepts a {@link Jid} on either the PN server or
     * the LID server. The LID-server branch consults the store's
     * LID-to-PN cache via {@code findPhoneByLid}; WA Web's
     * {@code WAWebUserPrefsMeUser.isMeAccount} caches both ids on the
     * {@code MeUser} record and compares them directly.
     *
     * @param from  the {@code from} attribute of the chatstate stanza
     * @param meJid the client's own JID from {@code store().jid()}
     * @return {@code true} when {@code from} and {@code meJid} resolve to
     *         the same user; {@code false} otherwise
     */
    private boolean isSelf(Jid from, Jid meJid) {
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
     * Centralizes the LID-to-PN normalization every chatstate update needs.
     * Embedders that consume {@code onContactPresence} can therefore
     * assume the {@link Contact#toJid()} they receive is on the PN server
     * even when the wire stanza carried a LID-server {@code from} or
     * {@code participant}.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebJidToWid.userJidToUserWid} followed by
     * {@code WAWebApiContact.getPhoneNumber} for LID-server inputs, but
     * collapses the lookup into one store call because the LID-to-PN cache
     * is the only source of truth for the mapping in Cobalt.
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
     * For a 1:1 chatstate update {@code conversation} and {@code participant}
     * are equal; for a group update {@code conversation} is the group JID
     * and {@code participant} is the device that produced the composing
     * state.
     *
     * @implNote
     * This implementation starts one virtual thread per listener so that a
     * blocking listener cannot stall the {@link SocketStream} dispatch loop.
     *
     * @param conversation the JID of the conversation that experienced the
     *                     composing transition
     * @param participant  the JID of the participant whose composing state
     *                     changed
     */
    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, conversation, participant));
        }
    }
}
