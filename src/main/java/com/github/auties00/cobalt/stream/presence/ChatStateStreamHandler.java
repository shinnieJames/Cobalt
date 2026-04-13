package com.github.auties00.cobalt.stream.presence;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming {@code <chatstate>} stanzas from the WhatsApp server.
 *
 * <p>Each chatstate stanza carries the composing state of a contact in a conversation,
 * indicating whether the contact is typing a text message, recording an audio message,
 * or has paused composing. The stanza's {@code from} attribute identifies the
 * conversation (a 1:1 chat JID for individual chatstates or a group JID for group
 * chatstates), and an optional {@code participant} attribute identifies the specific
 * user composing within a group.
 *
 * <p>The WA Web architecture splits chatstate handling into two paths created by the
 * {@code WACreateHandleChatState.createHandleChatState} factory: one for individual
 * chatstates ({@code handleIndividualChatState}) triggered when the stanza source is
 * {@code FromUser}, and one for group chatstates ({@code handleGroupChatState})
 * triggered when the source is {@code FromGroup}. In Cobalt, both paths are unified
 * into a single {@link #handle(Node)} method that branches on the presence of the
 * {@code participant} attribute.
 *
 * @implNote WAWebHandleChatState, WACreateHandleChatState.createHandleChatState,
 *           WAWebChangePresenceHandlerAction.default
 */
public final class ChatStateStreamHandler implements SocketStream.Handler {
    /**
     * The logger for diagnostic messages related to chatstate handling.
     *
     * @implNote WAWebHandleChatState -- WALogger usage in error paths
     */
    private static final System.Logger LOGGER = System.getLogger(ChatStateStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used to access the store and notify listeners.
     *
     * @implNote WAWebHandleChatState -- constructor DI replaces module-level imports of
     *           WAWebPresenceCollection, WAWebChatCollection, and WAWebApiContact
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new chatstate stream handler with the given WhatsApp client.
     *
     * @param whatsapp the non-{@code null} WhatsApp client instance
     * @implNote WACreateHandleChatState.createHandleChatState -- module-level handler
     *           creation; the factory receives individualMessage and groupMessage
     *           handlers and returns a unified function
     */
    public ChatStateStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles an incoming {@code <chatstate>} stanza by parsing the composing state
     * and dispatching to either the individual or group chatstate path.
     *
     * <p>The handler performs the following steps:
     * <ol>
     *   <li>Extracts the {@code from} attribute as a JID (the conversation identifier)</li>
     *   <li>Extracts the optional {@code participant} attribute (present for group chatstates)</li>
     *   <li>Resolves the composing state from the stanza's child element</li>
     *   <li>Dispatches to the group path if {@code participant} is present, or the
     *       individual path otherwise</li>
     *   <li>Updates the contact's presence in the store and notifies listeners</li>
     * </ol>
     *
     * <p>In WA Web, the factory {@code createHandleChatState} first parses the stanza via
     * {@code WASmaxChatstateServerNotificationRPC.receiveServerNotificationRPC} to extract
     * {@code stateSource} (FromUser or FromGroup) and {@code stateTypes} (Composing or
     * Paused), then dispatches to the appropriate handler. Cobalt performs equivalent
     * parsing inline using the stanza's XML structure directly.
     *
     * @param node the non-{@code null} chatstate stanza node
     * @implNote WACreateHandleChatState.createHandleChatState -- the returned function
     *           that routes to handleIndividualChatState or handleGroupChatState
     */
    @Override
    public void handle(Node node) {
        // WACreateHandleChatState: stateSource determines FromUser vs FromGroup
        // WASmaxInChatstateFromUserMixin: from attr is a user JID
        // WASmaxInChatstateFromGroupMixin: from attr is a group JID, participant attr is a user JID
        var from = node.getAttributeAsJid("from", null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring chatstate stanza without from: {0}", node);
            return;
        }

        // WACreateHandleChatState: stateSource.name === "FromGroup" has participant
        var participant = node.getAttributeAsJid("participant", null);

        // WACreateHandleChatState: parseChatStatus(stateTypes)
        var state = resolveState(node);
        if (state == null) {
            return;
        }

        if (participant != null) {
            // WAWebHandleChatState.handleGroupChatState
            handleGroupChatState(from, participant, state);
        } else {
            // WAWebHandleChatState.handleIndividualChatState
            handleIndividualChatState(from, state);
        }
    }

    /**
     * Handles an individual (1:1) chatstate update by resolving the sender contact
     * and updating their composing state.
     *
     * <p>In WA Web, this function receives {@code {jid, status}} and:
     * <ol>
     *   <li>Converts the JID to a user WID via {@code userJidToUserWid}</li>
     *   <li>Performs LID migration resolution (if migrated, looks up chat by account
     *       LID; if not migrated and sender is LID, also dispatches to PN)</li>
     *   <li>Calls {@code WAWebChangePresenceHandlerAction} with the resolved ID and status</li>
     * </ol>
     *
     * <p>Cobalt adapts the LID resolution by using {@code findPhoneByLid} on the store,
     * which is architecturally equivalent since Cobalt stores contacts by PN JID.
     *
     * @param from  the JID of the sender (user JID)
     * @param state the resolved composing state
     * @implNote WAWebHandleChatState.handleIndividualChatState
     */
    private void handleIndividualChatState(Jid from, ContactStatus state) {
        // WAWebChangePresenceHandlerAction.default: if (!isMeAccount(a))
        // Skip self-chatstates for individual chats
        var meJid = whatsapp.store().jid().orElse(null);
        if (meJid != null && isSelf(from, meJid)) {
            return; // WAWebChangePresenceHandlerAction.default -- skip self-chatstate
        }

        // WAWebHandleChatState.handleIndividualChatState: var a = userJidToUserWid(jid)
        // ADAPTED: LID migration logic (isLidMigrated check, toUserLid, getChatRecordByAccountLid)
        // collapsed into findPhoneByLid since Cobalt stores contacts by PN
        var contact = getOrCreateContact(from);
        if (contact == null) {
            return;
        }

        // WAWebChangePresenceHandlerAction.default -> m(presence, {id, type})
        contact.setLastKnownPresence(state);
        whatsapp.store().addContact(contact); // ADAPTED: WAWebPresenceCollection.set operations
        notifyPresence(contact.toJid(), contact.toJid()); // WAWebChangePresenceHandlerAction -- non-group path
    }

    /**
     * Handles a group chatstate update by resolving the participant contact and
     * updating their composing state within the context of the group conversation.
     *
     * <p>In WA Web, this function receives {@code {jid, participant, status}} and:
     * <ol>
     *   <li>Converts the group JID via {@code chatJidToChatWid}</li>
     *   <li>Converts the participant JID via {@code userJidToUserWid}</li>
     *   <li>Calls {@code WAWebChangePresenceHandlerAction} with the group as
     *       {@code id}, status as {@code type}, and participant as {@code participant}</li>
     * </ol>
     *
     * <p>The action handler ({@code m(e, t)}) for groups then:
     * <ol>
     *   <li>Verifies {@code toPn(participant)} is not {@code null} (participant has a PN mapping)</li>
     *   <li>Gets the chat from {@code ChatCollection}</li>
     *   <li>Sets the chatstate on the participant's sub-entry within the group's presence model</li>
     *   <li>Sets an expiry timer (25s) for typing/recording states</li>
     * </ol>
     *
     * @param from        the JID of the group
     * @param participant the JID of the group participant who is composing
     * @param state       the resolved composing state
     * @implNote WAWebHandleChatState.handleGroupChatState,
     *           WAWebChangePresenceHandlerAction.default -- group path in m(e, t)
     */
    private void handleGroupChatState(Jid from, Jid participant, ContactStatus state) {
        // WAWebHandleChatState.handleGroupChatState: var i = chatJidToChatWid(jid), var l = userJidToUserWid(participant)
        // WAWebChangePresenceHandlerAction.default -> m(presence, {id: i, type: status, participant: l})
        var contact = getOrCreateContact(participant);
        if (contact == null) {
            return;
        }

        // WAWebChangePresenceHandlerAction.m: group path sets chatstate on participant sub-entry
        contact.setLastKnownPresence(state);
        whatsapp.store().addContact(contact); // ADAPTED: WAWebPresenceCollection chatstate set
        notifyPresence(from, contact.toJid()); // WAWebChangePresenceHandlerAction -- group path: id=group, participant=user
    }

    /**
     * Resolves the composing state from the chatstate stanza's child element.
     *
     * <p>This method mirrors the combined parsing of
     * {@code WASmaxInChatstateStateTypes.parseStateTypes} and
     * {@code WAHandleChatStateProtocol.parseChatStatus}:
     * <ul>
     *   <li>{@code <composing>} without {@code media="audio"} maps to
     *       {@link ContactStatus#COMPOSING} (WA Web: {@code "typing"})</li>
     *   <li>{@code <composing media="audio">} maps to {@link ContactStatus#RECORDING}
     *       (WA Web: {@code "recording_audio"})</li>
     *   <li>{@code <paused>} maps to {@link ContactStatus#AVAILABLE}
     *       (WA Web: {@code "idle"}, which is then resolved to {@code "available"} or
     *       {@code "unavailable"} based on the contact's {@code isOnline} state in the
     *       PresenceCollection. Since Cobalt does not maintain a per-contact
     *       PresenceCollection with {@code isOnline} tracking, the paused state is
     *       mapped to AVAILABLE, reflecting the most common scenario where a user
     *       who was typing and then paused is still online)</li>
     * </ul>
     *
     * @param node the chatstate stanza node
     * @return the resolved {@link ContactStatus}, or {@code null} if the child element
     *         is missing or unsupported
     * @implNote WASmaxInChatstateStateTypes.parseStateTypes,
     *           WASmaxInChatstateComposingMixin.parseComposingMixin,
     *           WASmaxInChatstatePausedMixin.parsePausedMixin,
     *           WAHandleChatStateProtocol.parseChatStatus
     */
    private ContactStatus resolveState(Node node) {
        // WASmaxInChatstateStateTypes: tries parseComposingMixin, then parsePausedMixin
        var child = node.getChild().orElse(null);
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring empty chatstate stanza: {0}", node);
            return null;
        }

        return switch (child.description()) {
            // WASmaxInChatstateComposingMixin: flattenedChildWithTag(e, "composing")
            // WAHandleChatStateProtocol.parseChatStatus: composingMedia === "audio" ? "recording_audio" : "typing"
            case "composing" -> "audio".equals(child.getAttributeAsString("media", null))
                    ? ContactStatus.RECORDING
                    : ContactStatus.COMPOSING;
            // WASmaxInChatstatePausedMixin: flattenedChildWithTag(e, "paused")
            // WAHandleChatStateProtocol.parseChatStatus: "Paused" -> "idle"
            // ADAPTED: WAWebChangePresenceHandlerAction.m converts "idle" to
            // e.isOnline ? "available" : "unavailable"; Cobalt defaults to AVAILABLE
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
     * Determines whether the given {@code from} JID represents the current user's
     * own account.
     *
     * <p>This check mirrors WA Web's {@code isMeAccount} which compares both the
     * phone-number user and the LID user. In Cobalt, the PN-based check is direct;
     * LID-based from JIDs are resolved through the LID-to-PN cache.
     *
     * @param from  the JID from the chatstate stanza
     * @param meJid the current user's device JID
     * @return {@code true} if the chatstate is for the current user's own account
     * @implNote WAWebUserPrefsMeUser.isMeAccount -- function $(e)
     */
    private boolean isSelf(Jid from, Jid meJid) {
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
     * <p>If the JID has a LID server, it is first resolved to a phone-number JID via
     * the store's LID-to-PN cache. This mirrors the WA Web behavior where
     * {@code WAWebJidToWid.userJidToUserWid} converts the JID and
     * {@code WAWebLidMigrationUtils.toUserLid} / {@code WAWebApiContact.getPhoneNumber}
     * perform LID-to-PN resolution before looking up the contact in the presence
     * collection.
     *
     * @param jid the JID from the chatstate stanza
     * @return the resolved contact, or {@code null} if the JID cannot be resolved
     * @implNote ADAPTED: WAWebHandleChatState.handleIndividualChatState -- LID migration
     *           logic via WAWebLid1X1MigrationGating.isLidMigrated,
     *           WAWebLidMigrationUtils.toUserLid, WAWebApiChat.getChatRecordByAccountLid;
     *           Cobalt collapses all LID resolution into findPhoneByLid
     */
    private Contact getOrCreateContact(Jid jid) {
        if (jid == null) {
            return null; // NO_WA_BASIS -- defensive null check
        }

        // ADAPTED: WAWebHandleChatState -- userJidToUserWid + LID migration resolution
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
     *                     (same as participant for 1:1 chats, group JID for groups)
     * @param participant  the JID of the participant whose presence changed
     * @implNote ADAPTED: WAWebChangePresenceHandlerAction.default -- in WA Web, presence
     *           changes propagate through model observation on the PresenceCollection;
     *           Cobalt uses explicit listener notification instead
     */
    private void notifyPresence(Jid conversation, Jid participant) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactPresence(whatsapp, conversation, participant));
        }
    }
}
