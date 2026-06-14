package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
import com.github.auties00.cobalt.stream.SocketStreamHandler;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedContactPresenceListener;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.NodeStreamService;

/**
 * Updates the {@link Contact#lastKnownPresence()} of the contact identified by a {@code <chatstate>} stanza.
 *
 * <p>This handler drives the "typing..." and "recording audio..." indicator shown in a conversation header. The server
 * pushes one {@code <chatstate>} stanza per transition between composing states. For a one-to-one chat the {@code from}
 * attribute names the peer and the update fans out with that JID as both conversation and participant. For a group the
 * {@code from} attribute carries the group JID and a {@code participant} attribute names the device currently composing
 * inside the group, so the update fans out with the group JID as the conversation and the participant JID as the
 * participant. The first child of the stanza encodes the composing state and is mapped to a {@link ContactStatus} by
 * {@link #resolveState(Node)}.
 *
 * @implNote
 * This implementation collapses WhatsApp Web's three-stage flow into one method: the factory that splits per source
 * kind, the per-source handlers, and the action that mutates the presence collection. The split is performed inline in
 * {@link #handle(Node)} by inspecting the {@code participant} attribute instead of by reading a {@code stateSource}
 * field on a parsed RPC. WhatsApp Web additionally maintains a per-typing-indicator auto-expiry timer of 25 seconds;
 * Cobalt exposes only the raw {@link ContactStatus} transition to its listeners and leaves expiry policy to the
 * embedder.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleChatState")
@WhatsAppWebModule(moduleName = "WACreateHandleChatState")
@WhatsAppWebModule(moduleName = "WAHandleChatStateProtocol")
@WhatsAppWebModule(moduleName = "WAWebChangePresenceHandlerAction")
public final class ChatStateStreamHandler extends SocketStreamHandler.Concurrent {
    /**
     * Reports {@code <chatstate>} stanzas with missing or unsupported children at debug level.
     */
    private static final System.Logger LOGGER = System.getLogger(ChatStateStreamHandler.class.getName());

    /**
     * Owns the store this handler mutates and the listeners that receive the resulting presence notifications.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Constructs a new {@link ChatStateStreamHandler} bound to the given {@link LinkedWhatsAppClient}.
     *
     * <p>The handler is constructed by the {@link NodeStreamService} wiring; embedders do not call this directly.
     *
     * @param whatsapp the non-{@code null} client whose store is updated and whose listeners are notified
     */
    public ChatStateStreamHandler(LinkedWhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Consumes one {@code <chatstate>} stanza and routes it to either
     * {@link #handleIndividualChatState(Jid, ContactStatus)} when the stanza carries no {@code participant} attribute,
     * or {@link #handleGroupChatState(Jid, Jid, ContactStatus)} when it does. Stanzas without a {@code from} attribute,
     * and stanzas whose child does not resolve to a recognized composing state, are dropped after a debug log entry.
     *
     * @implNote
     * This implementation merges the WhatsApp Web factory that returns a closure first parsing the stanza into a
     * {@code FromUser} or {@code FromGroup} source. The discriminator here is the presence of the {@code participant}
     * attribute, which is equivalent on the wire because the WhatsApp Web RPC parser ultimately reads the same
     * attribute when building its {@code stateSource} field.
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
     * Applies an individual one-to-one chatstate update for the sender identified by {@code from}.
     *
     * <p>The {@code from} attribute on a one-to-one {@code <chatstate>} stanza names the peer, so the notification fans
     * out with the same JID as both the conversation and the participant because there is no group context. Stanzas
     * about the current account are silently dropped.
     *
     * @implNote
     * This implementation diverges from WhatsApp Web in two places. WhatsApp Web dispatches twice (once for the
     * resolved id and once for the phone-number counterpart) when the client is in pre-migration LID mode and the
     * sender is a LID-server JID; Cobalt instead canonicalizes the JID to its phone-number form via
     * {@link #getOrCreateContact(Jid)} and dispatches once. WhatsApp Web runs the action handler asynchronously through
     * a fire-and-forget backend call; Cobalt invokes the listener inline through {@link #notifyPresence(Jid, Jid)}.
     *
     * @param from  the {@code from} attribute of the chatstate stanza
     * @param state the {@link ContactStatus} previously resolved from the stanza child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleChatState", exports = "handleIndividualChatState",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleIndividualChatState(Jid from, ContactStatus state) {
        var meJid = whatsapp.store().accountStore().jid().orElse(null);
        if (meJid != null && isSelf(from, meJid)) {
            return;
        }

        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        contact.setLastKnownPresence(state);
        whatsapp.store().contactStore().addContact(contact);
        notifyPresence(contact.toJid(), contact.toJid());
    }

    /**
     * Applies a per-participant chatstate update inside the group identified by {@code from}.
     *
     * <p>The notification fans out with the group JID as the conversation and the participant's user JID as the
     * participant, so listeners can scope the typing indicator to a single conversation header without polluting other
     * surfaces.
     *
     * @implNote
     * This implementation diverges from WhatsApp Web in two places. WhatsApp Web's downstream action handler maintains
     * a per-group chatstates sub-collection keyed by participant and an auto-expiry timer of 25 seconds for typing or
     * audio-recording states; Cobalt models neither and simply overwrites the participant's
     * {@link Contact#lastKnownPresence()} for as long as the server keeps pushing updates. WhatsApp Web additionally
     * drops the update when the participant has no phone-number mapping (a pre-migration safety check); Cobalt forwards
     * the update unconditionally because its contact store always holds a canonical phone-number entry after
     * {@link #getOrCreateContact(Jid)} runs.
     *
     * @param from        the group JID from the stanza
     * @param participant the participant JID that produced the composing update
     * @param state       the {@link ContactStatus} previously resolved from the stanza child
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleChatState", exports = "handleGroupChatState",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void handleGroupChatState(Jid from, Jid participant, ContactStatus state) {
        var contact = getOrCreateContact(participant);
        if (contact == null) {
            return;
        }

        contact.setLastKnownPresence(state);
        whatsapp.store().contactStore().addContact(contact);
        notifyPresence(from, contact.toJid());
    }

    /**
     * Returns the {@link ContactStatus} encoded by the single child of a {@code <chatstate>} stanza, or {@code null}
     * when the child is absent or unsupported.
     *
     * <p>A {@code <composing>} child maps to {@link ContactStatus#COMPOSING}, a {@code <composing media="audio">} child
     * maps to {@link ContactStatus#RECORDING}, and a {@code <paused>} child maps to {@link ContactStatus#AVAILABLE}.
     * Any other child tag, and a stanza with no child at all, yields {@code null} after a debug log entry.
     *
     * @implNote
     * This implementation fuses WhatsApp Web's classification of the child as a composing, composing-media or paused
     * type and the mapping of that classification to a status string. WhatsApp Web further refines the paused outcome
     * into either {@code "available"} or {@code "unavailable"} depending on the participant's {@code isOnline} flag in
     * its presence collection. Cobalt has no per-contact {@code isOnline} tracker (presence arrives exclusively through
     * {@link PresenceStreamHandler}), so the paused branch resolves to {@link ContactStatus#AVAILABLE}, matching the
     * common case where the peer was composing and then stopped without going offline.
     *
     * @param node the {@code <chatstate>} stanza whose first child encodes the composing state
     * @return the resolved {@link ContactStatus}, or {@code null} when the child is missing or carries an unsupported
     *         tag
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
     * Returns {@code true} when the given {@code from} JID refers to the client's own account.
     *
     * <p>This check suppresses self-echoes during a one-to-one chatstate update. The group path does not need it
     * because a group {@code from} JID is never the local user's JID.
     *
     * @implNote
     * This implementation accepts a {@link Jid} on either the phone-number server or the LID server. The LID-server
     * branch consults the store's LID-to-phone-number cache via {@code findPhoneByLid}; WhatsApp Web instead caches
     * both ids on its current-user record and compares them directly without a cache lookup.
     *
     * @param from  the {@code from} attribute of the chatstate stanza
     * @param meJid the client's own JID from {@code store().jid()}
     * @return {@code true} when {@code from} and {@code meJid} resolve to the same user; {@code false} otherwise
     */
    private boolean isSelf(Jid from, Jid meJid) {
        var fromUser = from.toUserJid();
        var meUser = meJid.toUserJid();
        if (fromUser.user().equals(meUser.user())) {
            return true;
        }
        if (fromUser.hasLidServer()) {
            var phoneJid = whatsapp.store().contactStore().findPhoneByLid(fromUser).orElse(null);
            return phoneJid != null && phoneJid.user().equals(meUser.user());
        }
        return false;
    }

    /**
     * Returns the {@link Contact} stored under the canonical phone-number form of {@code jid}, creating a fresh entry
     * when none exists.
     *
     * <p>This method centralizes the LID-to-phone-number normalization every chatstate update needs. Embedders that
     * consume the presence notification can therefore assume the {@link Contact#toJid()} they receive is on the
     * phone-number server even when the wire stanza carried a LID-server {@code from} or {@code participant}. A
     * LID-server input that has no cached phone-number mapping falls back to the LID form.
     *
     * @implNote
     * This implementation collapses WhatsApp Web's two-step JID-to-WID and phone-number lookup into one store call
     * because the LID-to-phone-number cache is the only source of truth for the mapping in Cobalt.
     *
     * @param jid the {@link Jid} read off the stanza, possibly {@code null}
     * @return the resolved {@link Contact}, or {@code null} when {@code jid} is {@code null}
     */
    private Contact getOrCreateContact(Jid jid) {
        if (jid == null) {
            return null;
        }

        var canonical = jid.toUserJid().hasLidServer()
                ? whatsapp.store().contactStore().findPhoneByLid(jid.toUserJid()).orElse(jid.toUserJid())
                : jid.toUserJid();
        return whatsapp.store().contactStore().findContactByJid(canonical)
                .orElseGet(() -> whatsapp.store().contactStore().addNewContact(canonical));
    }

    /**
     * Fans out a presence notification to every registered
     * {@link LinkedWhatsAppClientListener}.
     *
     * <p>For a one-to-one chatstate update {@code conversation} and {@code participant} are equal; for a group update
     * {@code conversation} is the group JID and {@code participant} is the device that produced the composing state.
     *
     * @implNote
     * This implementation starts one virtual thread per listener so that a blocking listener cannot stall the
     * {@link NodeStreamService} dispatch loop.
     *
     * @param conversation the JID of the conversation that experienced the composing transition
     * @param participant  the JID of the participant whose composing state changed
     */
    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            if (listener instanceof LinkedContactPresenceListener typed) {
                Thread.startVirtualThread(() -> typed.onContactPresence(whatsapp, conversation, participant));
            }
        }
    }
}
