package com.github.auties00.cobalt.client.linked;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.listener.MessageDeletedListener;
import com.github.auties00.cobalt.listener.MessageStatusListener;
import com.github.auties00.cobalt.listener.DisconnectedListener;
import com.github.auties00.cobalt.listener.LoggedInListener;
import com.github.auties00.cobalt.listener.NewMessageListener;
import com.github.auties00.cobalt.client.WhatsAppClientDisconnectReason;

import com.github.auties00.cobalt.listener.linked.*;

import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.model.call.JoinableCallLink;
import com.github.auties00.cobalt.model.call.IncomingCall;
import com.github.auties00.cobalt.model.call.CallPeerState;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.model.business.BusinessDataSharingConsent;
import com.github.auties00.cobalt.model.business.ctwa.CtwaAccessTokenSession;
import com.github.auties00.cobalt.model.business.webgraphql.WhatsAppWebGraphQlSession;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.group.GroupPastParticipant;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.privacy.AccountDisappearingMode;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.StatusPrivacySetting;
import com.github.auties00.cobalt.model.setting.privacy.OptOutEntry;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.node.Node;

import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;
import java.util.Set;

/**
 * A callback interface that observes events emitted by a
 * {@link LinkedWhatsAppClient} during its lifecycle.
 *
 * <p>Cobalt funnels every notable event (socket traffic, login, logout,
 * message reception, contact updates, sync progress, call offers, identity
 * changes, and so on) through listener callbacks so that application code
 * can react without having to poll the store. This interface aggregates one
 * single-method functional interface per event and supplies an empty default
 * implementation for each, so integrators only need to override the events
 * they are interested in; an application that cares about a single event can
 * instead implement the matching per-event interface (for example
 * {@link NewMessageListener}) directly with a lambda.
 *
 * <p>Listeners are registered via
 * {@link LinkedWhatsAppClient#addListener(LinkedListener)} and can be
 * removed with
 * {@link LinkedWhatsAppClient#removeListener(LinkedListener)}. Each
 * invocation runs on a virtual thread, so a long-running listener does not
 * block the client socket or stanza pipeline.
 *
 * @see WhatsAppListener
 * @see LinkedWhatsAppClient#addListener(LinkedListener)
 * @see LinkedWhatsAppClient#removeListener(LinkedListener)
 */
public non-sealed interface LinkedWhatsAppClientListener extends LinkedListener, LinkedNodeSentListener,
        LinkedNodeReceivedListener,
        LoggedInListener,
        DisconnectedListener,
        LinkedWebAppStateActionListener,
        LinkedWebAppPrimaryFeaturesListener,
        LinkedContactsListener,
        LinkedContactPresenceListener,
        LinkedChatsListener,
        LinkedNewslettersListener,
        LinkedGroupsListener,
        LinkedWebHistorySyncMessagesListener,
        LinkedWebHistorySyncPastParticipantsListener,
        LinkedWebHistorySyncProgressListener,
        NewMessageListener,
        MessageDeletedListener,
        MessageStatusListener,
        LinkedStatusListener,
        LinkedNewStatusListener,
        LinkedMessageReplyListener,
        LinkedProfilePictureChangedListener,
        LinkedNameChangedListener,
        LinkedAboutChangedListener,
        LinkedContactTextStatusListener,
        LinkedLocaleChangedListener,
        LinkedContactBlockedListener,
        LinkedOptOutListListener,
        LinkedContactBlacklistListener,
        LinkedDevicesListener,
        LinkedStatusPrivacyChangedListener,
        LinkedDisappearingModeChangedListener,
        LinkedBlockedContactsListener,
        LinkedNewContactListener,
        LinkedPrivacySettingChangedListener,
        LinkedRegistrationCodeListener,
        LinkedCallListener,
        LinkedCallEndedListener,
        LinkedCallPreacceptListener,
        LinkedCallMuteChangedListener,
        LinkedCallVideoStateChangedListener,
        LinkedCallVideoUpgradeRequestListener,
        LinkedCallLinkLobbyJoinRequestListener,
        LinkedCallLinkAdmittedListener,
        LinkedCallLinkDeniedListener,
        LinkedCallInteractionListener,
        LinkedCallParticipantsChangedListener,
        LinkedCallPeerStateChangedListener,
        LinkedCallOfferNoticeListener,
        LinkedDeviceIdentityChangedListener,
        LinkedAccountTypeChangedListener,
        LinkedGraphQlSessionChangedListener,
        LinkedFacebookGraphQlSessionChangedListener,
        LinkedBusinessPrivacySettingChangedListener,
        LinkedTosNoticesChangedListener {
    @Override
    default void onNodeSent(LinkedWhatsAppClient whatsapp, Node outgoing) {
    }

    @Override
    default void onNodeReceived(LinkedWhatsAppClient whatsapp, Node incoming) {
    }

    @Override
    default void onLoggedIn(WhatsAppClient whatsapp) {
    }

    @Override
    default void onDisconnected(WhatsAppClient whatsapp, WhatsAppClientDisconnectReason reason) {
    }

    @Override
    default void onWebAppStateAction(LinkedWhatsAppClient whatsapp, SyncAction action, String index) {
    }

    @Override
    default void onWebAppPrimaryFeatures(LinkedWhatsAppClient whatsapp, List<String> features) {
    }

    @Override
    default void onContacts(LinkedWhatsAppClient whatsapp, Collection<Contact> contacts) {
    }

    @Override
    default void onContactPresence(LinkedWhatsAppClient whatsapp, Jid conversation, Jid participant) {
    }

    @Override
    default void onChats(LinkedWhatsAppClient whatsapp, Collection<Chat> chats) {
    }

    @Override
    default void onNewsletters(LinkedWhatsAppClient whatsapp, Collection<Newsletter> newsletters) {
    }

    @Override
    default void onGroups(LinkedWhatsAppClient whatsapp, Collection<Chat> groups) {
    }

    @Override
    default void onWebHistorySyncMessages(LinkedWhatsAppClient whatsapp, SequencedCollection<Chat> chats, boolean last) {
    }

    @Override
    default void onWebHistorySyncPastParticipants(LinkedWhatsAppClient whatsapp, Jid chatJid, Collection<GroupPastParticipant> groupPastParticipants) {
    }

    @Override
    default void onWebHistorySyncProgress(LinkedWhatsAppClient whatsapp, int percentage, boolean recent) {
    }

    @Override
    default void onNewMessage(WhatsAppClient whatsapp, MessageInfo info) {
    }

    @Override
    default void onMessageDeleted(WhatsAppClient whatsapp, MessageInfo info, boolean everyone) {
    }

    @Override
    default void onMessageStatus(WhatsAppClient whatsapp, MessageInfo info) {
    }

    @Override
    default void onStatus(LinkedWhatsAppClient whatsapp, Collection<ChatMessageInfo> status) {
    }

    @Override
    default void onNewStatus(LinkedWhatsAppClient whatsapp, ChatMessageInfo status) {
    }

    @Override
    default void onMessageReply(LinkedWhatsAppClient whatsapp, MessageInfo response, MessageInfo quoted) {
    }

    @Override
    default void onProfilePictureChanged(LinkedWhatsAppClient whatsapp, Jid jid) {
    }

    @Override
    default void onNameChanged(LinkedWhatsAppClient whatsapp, String oldName, String newName) {
    }

    @Override
    default void onAboutChanged(LinkedWhatsAppClient whatsapp, String oldAbout, String newAbout) {
    }

    @Override
    default void onContactTextStatus(LinkedWhatsAppClient whatsapp, Jid contact, ContactTextStatus status) {
    }

    @Override
    default void onLocaleChanged(LinkedWhatsAppClient whatsapp, String oldLocale, String newLocale) {
    }

    @Override
    default void onContactBlocked(LinkedWhatsAppClient whatsapp, Jid contact) {
    }

    @Override
    default void onOptOutList(LinkedWhatsAppClient whatsapp, String category, List<OptOutEntry> entries) {
    }

    @Override
    default void onContactBlacklist(LinkedWhatsAppClient whatsapp, String category, Collection<Jid> blockedContacts) {
    }

    @Override
    default void onLinkedDevices(LinkedWhatsAppClient whatsapp, Collection<Jid> linkedDevices) {
    }

    @Override
    default void onStatusPrivacyChanged(LinkedWhatsAppClient whatsapp, StatusPrivacySetting statusPrivacy) {
    }

    @Override
    default void onDisappearingModeChanged(LinkedWhatsAppClient whatsapp, AccountDisappearingMode disappearingMode) {
    }

    @Override
    default void onBlockedContacts(LinkedWhatsAppClient whatsapp, Collection<Jid> blockedContacts) {
    }

    @Override
    default void onNewContact(LinkedWhatsAppClient whatsapp, Contact contact) {
    }

    @Override
    default void onPrivacySettingChanged(LinkedWhatsAppClient whatsapp, PrivacySettingEntry newPrivacyEntry) {
    }

    @Override
    default void onBusinessPrivacySettingChanged(LinkedWhatsAppClient whatsapp, BusinessDataSharingConsent consent) {
    }

    @Override
    default void onGraphQlSessionChanged(LinkedWhatsAppClient whatsapp, WhatsAppWebGraphQlSession session) {
    }

    @Override
    default void onFacebookGraphQlSessionChanged(LinkedWhatsAppClient whatsapp, CtwaAccessTokenSession session) {
    }

    @Override
    default void onTosNoticesChanged(LinkedWhatsAppClient whatsapp, Set<String> noticeIds) {
    }

    @Override
    default void onRegistrationCode(LinkedWhatsAppClient whatsapp, long code) {
    }

    @Override
    default void onCall(LinkedWhatsAppClient whatsapp, IncomingCall incoming) {
    }

    @Override
    default void onCallEnded(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid, CallEndReason reason) {
    }

    @Override
    default void onCallPreaccept(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid) {
    }

    @Override
    default void onCallMuteChanged(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid, boolean muted) {
    }

    @Override
    default void onCallVideoStateChanged(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid, boolean enabled) {
    }

    @Override
    default void onCallVideoUpgradeRequest(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid) {
    }

    @Override
    default void onCallLinkLobbyJoinRequest(LinkedWhatsAppClient whatsapp, JoinableCallLink link, Jid peer) {
    }

    @Override
    default void onCallLinkAdmitted(LinkedWhatsAppClient whatsapp, JoinableCallLink link) {
    }

    @Override
    default void onCallLinkDenied(LinkedWhatsAppClient whatsapp, JoinableCallLink link) {
    }

    @Override
    default void onCallInteraction(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid, CallInteraction interaction) {
    }

    @Override
    default void onCallParticipantsChanged(LinkedWhatsAppClient whatsapp, String callId, Jid groupJid, List<Jid> participants, boolean added) {
    }

    @Override
    default void onCallPeerStateChanged(LinkedWhatsAppClient whatsapp, String callId, Jid fromJid, CallPeerState state) {
    }

    @Override
    default void onCallOfferNotice(LinkedWhatsAppClient whatsapp, IncomingCall call) {
    }

    @Override
    default void onDeviceIdentityChanged(LinkedWhatsAppClient whatsapp, Jid userJid, Set<Jid> changedDevices) {
    }

    @Override
    default void onAccountTypeChanged(LinkedWhatsAppClient whatsapp, Jid userJid, ADVEncryptionType oldType, ADVEncryptionType newType) {
    }
}
