package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.call.CallInteraction;
import com.github.auties00.cobalt.call.CallLink;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.group.GroupPastParticipant;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.privacy.AccountDisappearingMode;
import com.github.auties00.cobalt.model.privacy.StatusPrivacySetting;
import com.github.auties00.cobalt.model.setting.privacy.OptOutEntry;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.internal.signaling.CallPeerState;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.node.Node;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A callback interface that observes events emitted by a
 * {@link WhatsAppClient} during its lifecycle.
 *
 * <p>Cobalt funnels every notable event (socket traffic, login, logout,
 * message reception, contact updates, sync progress, call offers, identity
 * changes, and so on) through listener callbacks so that application code
 * can react without having to poll the store. All callback methods carry
 * empty default implementations, so integrators only need to override the
 * events they are interested in.
 *
 * <p>Listeners are registered via
 * {@link WhatsAppClient#addListener(WhatsAppClientListener)} and can be
 * removed with
 * {@link WhatsAppClient#removeListener(WhatsAppClientListener)}. Each
 * invocation runs on a virtual thread, so a long-running listener does not
 * block the client socket or stanza pipeline.
 *
 * @see WhatsAppClient#addListener(WhatsAppClientListener)
 * @see WhatsAppClient#removeListener(WhatsAppClientListener)
 */
public interface WhatsAppClientListener {
    /**
     * Notifies the listener that a node has been sent to the WhatsApp
     * server.
     *
     * @param whatsapp the client emitting the event
     * @param outgoing the node that was sent
     */
    default void onNodeSent(WhatsAppClient whatsapp, Node outgoing) {
    }

    /**
     * Notifies the listener that a node has been received from the
     * WhatsApp server.
     *
     * @param whatsapp the client emitting the event
     * @param incoming the node that was received
     */
    default void onNodeReceived(WhatsAppClient whatsapp, Node incoming) {
    }

    /**
     * Notifies the listener that a successful connection and login to a
     * WhatsApp account has been established.
     *
     * <p>When this event fires, data such as chats and contacts may not
     * yet be loaded into memory. Use the corresponding event handlers for
     * specific data types, such as
     * {@link #onChats(WhatsAppClient, Collection)} and
     * {@link #onContacts(WhatsAppClient, Collection)}.
     *
     * @param whatsapp the client emitting the event
     */
    default void onLoggedIn(WhatsAppClient whatsapp) {
    }

    /**
     * Notifies the listener that the connection to WhatsApp has been
     * terminated.
     *
     * @param whatsapp the client emitting the event
     * @param reason   the reason for disconnection
     * @see WhatsAppClientDisconnectReason
     */
    default void onDisconnected(WhatsAppClient whatsapp, WhatsAppClientDisconnectReason reason) {
    }

    /**
     * Notifies the listener that an app-state action has been received
     * from WhatsApp Web.
     *
     * <p>This event is only triggered for web client connections.
     *
     * @param whatsapp the client emitting the event
     * @param action   the action that was executed
     * @param index    the data associated with this action
     */
    default void onWebAppStateAction(WhatsAppClient whatsapp, SyncAction action, String index) {
    }

    /**
     * Notifies the listener that the primary feature flags have been
     * received from WhatsApp Web.
     *
     * <p>This event is only triggered for web client connections.
     *
     * @param whatsapp the client emitting the event
     * @param features the collection of feature flags that were sent
     */
    default void onWebAppPrimaryFeatures(WhatsAppClient whatsapp, List<String> features) {
    }

    /**
     * Notifies the listener that the full contact list has been received
     * from WhatsApp.
     *
     * <p>Fires exactly once per login. On a fresh session it fires after
     * the initial-bootstrap history-sync chunk has been processed; on a
     * reconnect it fires from the cached store immediately after the
     * {@code <success>} stanza. The callback fires even when the
     * contact list is empty.
     *
     * @param whatsapp the client emitting the event
     * @param contacts the collection of contacts
     */
    default void onContacts(WhatsAppClient whatsapp, Collection<Contact> contacts) {
    }

    /**
     * Notifies the listener that a contact's presence status has been
     * updated.
     *
     * @param whatsapp     the client emitting the event
     * @param conversation the chat related to this presence update
     * @param participant  the contact whose presence status changed
     */
    default void onContactPresence(WhatsAppClient whatsapp, Jid conversation, Jid participant) {
    }

    /**
     * Notifies the listener that the full chat list has been received
     * from WhatsApp.
     *
     * <p>Fires exactly once per login, after the chat metadata is
     * complete and independently of message backfill. On a fresh
     * session it fires after the initial-bootstrap history-sync chunk
     * has been processed; on a reconnect it fires from the cached
     * store immediately after the {@code <success>} stanza. Message
     * content for each chat continues to stream in through
     * {@link #onWebHistorySyncMessages(WhatsAppClient, Chat, boolean)}
     * after this callback returns; particularly old chats may also be
     * discovered later through the history-sync process. The callback
     * fires even when the chat list is empty.
     *
     * @param whatsapp the client emitting the event
     * @param chats    the collection of chats
     */
    default void onChats(WhatsAppClient whatsapp, Collection<Chat> chats) {
    }

    /**
     * Notifies the listener that the full newsletter list has been
     * received from WhatsApp.
     *
     * <p>Fires exactly once per login. On a fresh session it fires
     * after {@link WhatsAppClient#refreshNewsletters()} completes
     * during the post-success bootstrap; on a reconnect it fires from
     * the cached store immediately after the {@code <success>} stanza.
     * The callback fires even when the newsletter list is empty.
     *
     * @param whatsapp    the client emitting the event
     * @param newsletters the collection of newsletters
     */
    default void onNewsletters(WhatsAppClient whatsapp, Collection<Newsletter> newsletters) {
    }

    /**
     * Notifies the listener that the full groups list has been
     * refreshed against the server.
     *
     * @apiNote
     * Fires each time {@link WhatsAppClient#refreshGroups()} commits
     * a fresh server-authoritative view of the groups this account
     * participates in. Use to redraw the groups section of the chat
     * list against the new authoritative set.
     *
     * @param whatsapp the client emitting the event
     * @param groups   the collection of groups
     */
    default void onGroups(WhatsAppClient whatsapp, Collection<Chat> groups) {
    }

    /**
     * Notifies the listener that messages for a chat have been received
     * during history synchronization.
     *
     * <p>This event is only triggered during initial QR code scanning and
     * history syncing. On subsequent connections messages are already
     * loaded in the chats.
     *
     * @param whatsapp the client emitting the event
     * @param chat     the chat being synchronized
     * @param last     {@code true} if these are the final messages for
     *                 this chat, {@code false} if more are coming
     */
    default void onWebHistorySyncMessages(WhatsAppClient whatsapp, Chat chat, boolean last) {
    }

    /**
     * Notifies the listener that past participants for a group have been
     * received during history synchronization.
     *
     * @param whatsapp              the client emitting the event
     * @param chatJid               the group chat JID
     * @param groupPastParticipants the collection of past participants
     */
    default void onWebHistorySyncPastParticipants(WhatsAppClient whatsapp, Jid chatJid, Collection<GroupPastParticipant> groupPastParticipants) {
    }

    /**
     * Notifies the listener of progress made by the history-synchronization
     * process.
     *
     * <p>This event is only triggered during initial QR code scanning and
     * history syncing.
     *
     * @param whatsapp   the client emitting the event
     * @param percentage the percentage of synchronization completed
     * @param recent     {@code true} if syncing recent messages,
     *                   {@code false} if syncing older messages
     */
    default void onWebHistorySyncProgress(WhatsAppClient whatsapp, int percentage, boolean recent) {
    }

    /**
     * Notifies the listener that a new message has been received.
     *
     * @param whatsapp the client emitting the event
     * @param info     the message that was received
     */
    default void onNewMessage(WhatsAppClient whatsapp, MessageInfo info) {
    }

    /**
     * Notifies the listener that a message has been deleted.
     *
     * @param whatsapp the client emitting the event
     * @param info     the message that was deleted
     * @param everyone {@code true} if the message was deleted for
     *                 everyone, {@code false} if deleted only for the user
     */
    default void onMessageDeleted(WhatsAppClient whatsapp, MessageInfo info, boolean everyone) {
    }

    /**
     * Notifies the listener that a message's status has changed (sent,
     * delivered, read).
     *
     * @param whatsapp the client emitting the event
     * @param info     the message whose status changed
     */
    default void onMessageStatus(WhatsAppClient whatsapp, MessageInfo info) {
    }

    /**
     * Notifies the listener that the full status feed has been received
     * from WhatsApp.
     *
     * <p>Fires exactly once per login. On a fresh session it fires
     * after the initial-status-v3 history-sync chunk has been
     * processed; on a reconnect it fires from the cached store
     * immediately after the {@code <success>} stanza. The callback
     * fires even when the status feed is empty.
     *
     * @param whatsapp the client emitting the event
     * @param status   the collection of status updates
     */
    default void onStatus(WhatsAppClient whatsapp, Collection<ChatMessageInfo> status) {
    }

    /**
     * Notifies the listener that a new status update has been received.
     *
     * @param whatsapp the client emitting the event
     * @param status   the new status message
     */
    default void onNewStatus(WhatsAppClient whatsapp, ChatMessageInfo status) {
    }

    /**
     * Notifies the listener that a message has been sent in reply to a
     * previous message.
     *
     * @param whatsapp the client emitting the event
     * @param response the reply message
     * @param quoted   the message being replied to
     */
    default void onMessageReply(WhatsAppClient whatsapp, MessageInfo response, MessageInfo quoted) {
    }

    /**
     * Notifies the listener that a contact's profile picture has changed.
     *
     * @param whatsapp the client emitting the event
     * @param jid      the contact whose profile picture changed
     */
    default void onProfilePictureChanged(WhatsAppClient whatsapp, Jid jid) {
    }

    /**
     * Notifies the listener that the user's display name has changed.
     *
     * @param whatsapp the client emitting the event
     * @param oldName  the previous name
     * @param newName  the new name
     */
    default void onNameChanged(WhatsAppClient whatsapp, String oldName, String newName) {
    }

    /**
     * Notifies the listener that the user's about/status text has
     * changed.
     *
     * @param whatsapp the client emitting the event
     * @param oldAbout the previous about text
     * @param newAbout the new about text
     */
    default void onAboutChanged(WhatsAppClient whatsapp, String oldAbout, String newAbout) {
    }

    /**
     * Notifies the listener that a contact's text status metadata has
     * changed.
     *
     * @param whatsapp the client emitting the event
     * @param contact  the contact whose text status changed
     * @param status   the new text status
     */
    default void onContactTextStatus(WhatsAppClient whatsapp, Jid contact, ContactTextStatus status) {
    }

    /**
     * Notifies the listener that the user's locale settings have changed.
     *
     * @param whatsapp  the client emitting the event
     * @param oldLocale the previous locale
     * @param newLocale the new locale
     */
    default void onLocaleChanged(WhatsAppClient whatsapp, String oldLocale, String newLocale) {
    }

    /**
     * Notifies the listener that a single contact's blocked state was
     * toggled.
     *
     * @apiNote
     * Fires after a local {@link WhatsAppClient#blockContact(JidProvider)}
     * or {@link WhatsAppClient#unblockContact(JidProvider)} succeeds, so
     * the UI can give immediate feedback on the action the user just
     * took. Bulk reconciliations of the Blocked Contacts privacy list
     * surface through
     * {@link #onBlockedContacts(WhatsAppClient, Collection)} instead.
     *
     * @param whatsapp the client emitting the event
     * @param contact  the contact that was blocked or unblocked
     */
    default void onContactBlocked(WhatsAppClient whatsapp, Jid contact) {
    }

    /**
     * Notifies the listener that the marketing-message opt-out list
     * for one category was refreshed against the server.
     *
     * @apiNote
     * Fires each time
     * {@link WhatsAppClient#refreshOptOutList(String)} commits a
     * fresh server-authoritative view of that category. Use to redraw
     * the marketing-message opt-out settings surface against the new
     * authoritative set.
     *
     * @param whatsapp the client emitting the event
     * @param category the opt-out category that was refreshed
     * @param entries  the new authoritative entries for that
     *                 category; may be empty
     */
    default void onOptOutList(WhatsAppClient whatsapp, String category, List<OptOutEntry> entries) {
    }

    /**
     * Notifies the listener that the per-axis privacy contact
     * blacklist for one category was refreshed against the server.
     *
     * @apiNote
     * Fires each time
     * {@link WhatsAppClient#refreshContactBlacklist(String, com.github.auties00.cobalt.model.setting.privacy.ContactBlacklistAddressingMode)}
     * commits a fresh server-authoritative view of that category. Use
     * to redraw the privacy settings surface against the new
     * authoritative set.
     *
     * @param whatsapp the client emitting the event
     * @param category the privacy axis category that was refreshed
     * @param blockedContacts the new authoritative entries for that
     *                        category; may be empty
     */
    default void onContactBlacklist(WhatsAppClient whatsapp, String category, Collection<Jid> blockedContacts) {
    }

    /**
     * Notifies the listener that the list of devices linked to this
     * account was refreshed against the server.
     *
     * @apiNote
     * Fires each time {@link WhatsAppClient#refreshLinkedDevices()}
     * commits a fresh server-authoritative copy of the paired-device
     * list. Use to redraw the Linked Devices settings surface.
     *
     * @param whatsapp       the client emitting the event
     * @param linkedDevices  the new authoritative set of device JIDs;
     *                       may be empty
     */
    default void onLinkedDevices(WhatsAppClient whatsapp, Collection<Jid> linkedDevices) {
    }

    /**
     * Notifies the listener that the Status story privacy setting
     * was refreshed against the server.
     *
     * @apiNote
     * Fires each time {@link WhatsAppClient#refreshStatusPrivacy()}
     * commits a fresh server-authoritative value, regardless of
     * whether the value changed.
     *
     * @param whatsapp      the client emitting the event
     * @param statusPrivacy the new authoritative status privacy setting
     */
    default void onStatusPrivacyChanged(WhatsAppClient whatsapp, StatusPrivacySetting statusPrivacy) {
    }

    /**
     * Notifies the listener that the account-level Disappearing
     * Messages setting was refreshed against the server.
     *
     * @apiNote
     * Fires each time
     * {@link WhatsAppClient#refreshDisappearingMode()} commits a
     * fresh server-authoritative value, regardless of whether the
     * value changed.
     *
     * @param whatsapp         the client emitting the event
     * @param disappearingMode the new authoritative disappearing mode
     */
    default void onDisappearingModeChanged(WhatsAppClient whatsapp, AccountDisappearingMode disappearingMode) {
    }

    /**
     * Notifies the listener that the Blocked Contacts privacy list was
     * refreshed against the server.
     *
     * @apiNote
     * Fires once each time
     * {@link WhatsAppClient#refreshBlockList()} commits a fresh
     * server-authoritative copy of the list, regardless of whether
     * individual entries changed. Use to redraw the Blocked Contacts
     * settings surface against the new authoritative set. The
     * collection is the same view returned by
     * {@link WhatsAppStore#blockedContacts()} after the refresh.
     *
     * @param whatsapp        the client emitting the event
     * @param blockedContacts the new authoritative set of blocked
     *                        contacts; may be empty
     */
    default void onBlockedContacts(WhatsAppClient whatsapp, Collection<Jid> blockedContacts) {
    }

    /**
     * Notifies the listener that a new contact has been added to the
     * contact list.
     *
     * @param whatsapp the client emitting the event
     * @param contact  the new contact
     */
    default void onNewContact(WhatsAppClient whatsapp, Contact contact) {
    }

    /**
     * Notifies the listener that a privacy setting has been changed.
     *
     * @param whatsapp        the client emitting the event
     * @param newPrivacyEntry the new privacy setting
     */
    default void onPrivacySettingChanged(WhatsAppClient whatsapp, PrivacySettingEntry newPrivacyEntry) {
    }

    /**
     * Notifies the listener that a registration code (OTP) has been
     * requested from a new device.
     *
     * <p>This event is only triggered for the mobile API.
     *
     * @param whatsapp the client emitting the event
     * @param code     the registration code
     */
    default void onRegistrationCode(WhatsAppClient whatsapp, long code) {
    }

    /**
     * Notifies the listener that an inbound call offer has arrived.
     * The listener must respond by calling
     * {@link IncomingCall#accept} or {@link IncomingCall#reject} on
     * the supplied handle within the WhatsApp-imposed offer timeout
     * (~30 s); otherwise the offer expires.
     *
     * <p>The listener is invoked from the call layer's signaling
     * thread; long-running decisions should hand off to a virtual
     * thread to avoid stalling signaling.
     *
     * @param whatsapp the client emitting the event
     * @param incoming the inbound offer with metadata + accept/reject
     */
    default void onCall(WhatsAppClient whatsapp, IncomingCall incoming) {
    }

    /**
     * Notifies the listener that a call has terminated.
     *
     * <p>Fired by the {@link com.github.auties00.cobalt.call.internal.signaling.CallReceiver}
     * when a {@code <call><terminate>} stanza is parsed. The
     * {@code reason} carries the {@code reason} attribute the peer sent on
     * the wire (for example {@code "timeout"} or {@code "hangup"}); it is
     * {@code null} when the stanza did not carry the attribute.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call that ended
     * @param fromJid  the JID of the party that ended the call
     * @param reason   the parsed reason; {@link CallEndReason#UNKNOWN}
     *                 if the peer did not supply one or the wire literal
     *                 was unrecognised
     */
    default void onCallEnded(WhatsAppClient whatsapp, String callId, Jid fromJid, CallEndReason reason) {
    }

    /**
     * Notifies the listener that the local pre-acceptance phase of an
     * incoming call has begun.
     *
     * <p>Fired when a {@code <call><preaccept>} stanza is received,
     * meaning the peer confirms our device is alerting the user.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call
     * @param fromJid  the JID of the peer that sent the preaccept
     */
    default void onCallPreaccept(WhatsAppClient whatsapp, String callId, Jid fromJid) {
    }

    /**
     * Notifies the listener that a call participant has muted or unmuted
     * their microphone.
     *
     * <p>Fired when a {@code <call><mute>} stanza is received with a
     * {@code state} attribute of {@code "muted"} or {@code "unmuted"}.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call
     * @param fromJid  the JID of the participant whose mic state changed
     * @param muted    {@code true} if announcing a mute, {@code false} for
     *                 an unmute
     */
    default void onCallMuteChanged(WhatsAppClient whatsapp, String callId, Jid fromJid, boolean muted) {
    }

    /**
     * Notifies the listener that a call participant has turned video on or
     * off.
     *
     * <p>Fired when a {@code <call><video_state>} stanza is received with
     * a {@code state} attribute of {@code "on"} or {@code "off"}.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call
     * @param fromJid  the JID of the participant whose video state changed
     * @param enabled  {@code true} if announcing video-on, {@code false}
     *                 for video-off
     */
    default void onCallVideoStateChanged(WhatsAppClient whatsapp, String callId, Jid fromJid, boolean enabled) {
    }

    /**
     * Notifies the listener that the peer is asking to upgrade an
     * audio-only call to audio plus video; this is the M4
     * video-upgrade flow.
     *
     * <p>The application can call
     * {@link com.github.auties00.cobalt.call.ActiveCall#acceptVideoUpgrade}
     * or
     * {@link com.github.auties00.cobalt.call.ActiveCall#rejectVideoUpgrade}
     * in response. Acceptance triggers the local side to start its
     * own video track; rejection sends a denial signal back to the
     * peer.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call
     * @param fromJid  the JID of the peer requesting the upgrade
     */
    default void onCallVideoUpgradeRequest(WhatsAppClient whatsapp, String callId, Jid fromJid) {
    }

    /**
     * Notifies the listener that someone has clicked a call-link the
     * local user owns and is now waiting in the lobby for the host
     * to admit them; this is the M6 lobby-flow signal.
     *
     * <p>The application can call
     * {@code whatsapp.admitCallLinkParticipant(token, peer)} or
     * {@code whatsapp.denyCallLinkParticipant(token, peer)} in
     * response.
     *
     * @param whatsapp the client emitting the event
     * @param link     the link the joiner is waiting on
     * @param peer     the JID of the joiner waiting in the lobby
     */
    default void onCallLinkLobbyJoinRequest(WhatsAppClient whatsapp,
                                            CallLink link, Jid peer) {
    }

    /**
     * Notifies the listener that the host of a call-link they
     * clicked has admitted them out of the lobby; the call is now
     * starting. Followed by a regular {@code onCall} once the
     * underlying call session is created.
     *
     * @param whatsapp the client emitting the event
     * @param link     the link that was admitted
     */
    default void onCallLinkAdmitted(WhatsAppClient whatsapp,
                                    CallLink link) {
    }

    /**
     * Notifies the listener that the host of a call-link declined
     * the local user's join request; terminal for that link
     * attempt.
     *
     * @param whatsapp the client emitting the event
     * @param link     the link that was denied
     */
    default void onCallLinkDenied(WhatsAppClient whatsapp,
                                  CallLink link) {
    }

    /**
     * Notifies the listener that a peer broadcast an in-call
     * interaction (emoji reaction, raise/lower hand, peer-mute
     * request, or keyframe request); this is the M8 in-call UX surface.
     *
     * @param whatsapp    the client emitting the event
     * @param callId      the identifier of the call
     * @param fromJid     the JID of the participant that sent the
     *                    interaction
     * @param interaction the typed interaction payload
     */
    default void onCallInteraction(WhatsAppClient whatsapp, String callId, Jid fromJid,
                                   CallInteraction interaction) {
    }

    /**
     * Notifies the listener that participants have been added to or removed
     * from an in-progress group call.
     *
     * <p>Fired when a {@code <call><group_update>} stanza is received with
     * an {@code action} attribute of {@code "add"} or {@code "remove"}.
     *
     * @param whatsapp     the client emitting the event
     * @param callId       the identifier of the group call
     * @param groupJid     the group JID that owns the call
     * @param participants the participants that were added or removed
     * @param added        {@code true} if the participants were added,
     *                     {@code false} if they were removed
     */
    default void onCallParticipantsChanged(WhatsAppClient whatsapp, String callId, Jid groupJid,
                                           List<Jid> participants, boolean added) {
    }

    /**
     * Notifies the listener that a peer-state update was received during a
     * call.
     *
     * <p>Fired when a {@code <call><peer_state>} stanza is received.
     * The wire {@code state} attribute is parsed into a typed
     * {@link CallPeerState}; values not in the enum surface as
     * {@link CallPeerState#UNKNOWN}.
     *
     * @param whatsapp the client emitting the event
     * @param callId   the identifier of the call
     * @param fromJid  the JID of the peer whose state changed
     * @param state    the parsed peer state
     */
    default void onCallPeerStateChanged(WhatsAppClient whatsapp, String callId, Jid fromJid, CallPeerState state) {
    }

    /**
     * Notifies the listener of an offer-notice stanza, which the relay
     * sends to inform the device about a call offer that arrived while it
     * was offline.
     *
     * <p>Mirrors {@code WAWebHandleVoipOfferNotice}'s entry point. The call
     * itself is also propagated through the regular {@link #onCall} flow
     * so most listeners do not need to override this method.
     *
     * @param whatsapp the client emitting the event
     * @param call     the offer-notice call descriptor
     */
    default void onCallOfferNotice(WhatsAppClient whatsapp, IncomingCall call) {
    }

    /**
     * Notifies the listener that a device's identity key has changed.
     *
     * <p>This indicates that the device was reset, reinstalled, or
     * potentially compromised. Applications should display a security
     * warning to users.
     *
     * @param whatsapp       the client emitting the event
     * @param userJid        the user whose device changed
     * @param changedDevices the devices with new identity keys
     */
    default void onDeviceIdentityChanged(WhatsAppClient whatsapp, Jid userJid, Set<Jid> changedDevices) {
    }

    /**
     * Notifies the listener that a contact's account type has changed
     * between {@code E2EE} and {@code HOSTED}.
     *
     * <p>This indicates a transition in the contact's encryption
     * configuration.
     *
     * @param whatsapp the client emitting the event
     * @param userJid  the user whose account type changed
     * @param oldType  the previous account type
     * @param newType  the new account type
     */
    default void onAccountTypeChanged(WhatsAppClient whatsapp, Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType) {
    }
}
