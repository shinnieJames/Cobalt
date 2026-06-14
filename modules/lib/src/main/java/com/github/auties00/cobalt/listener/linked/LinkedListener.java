package com.github.auties00.cobalt.listener.linked;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.listener.linked.internal.InternalLinkedListener;

/**
 * The sealed marker for every event a {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient}
 * emits.
 *
 * <p>Each single-method Linked listener in this package extends this marker, and the aggregator
 * {@link LinkedWhatsAppClientListener} extends them all. Registering a listener of this type observes
 * the Linked event stream; the concrete event is recovered by the dispatcher through a pattern-match.
 * The {@link InternalLinkedListener} branch is the client's own always-registered, application-hidden
 * listener family, kept in the same hierarchy so it shares the dispatch path. Events that both client
 * flavours emit (new message, message status, message deletion, login, disconnect) extend the root
 * {@link WhatsAppListener} directly instead of this marker.
 */
public sealed interface LinkedListener extends WhatsAppListener permits
        LinkedAboutChangedListener,
        LinkedAccountTypeChangedListener,
        LinkedBlockedContactsListener,
        LinkedBusinessPrivacySettingChangedListener,
        LinkedCallEndedListener,
        LinkedCallInteractionListener,
        LinkedCallLinkAdmittedListener,
        LinkedCallLinkDeniedListener,
        LinkedCallLinkLobbyJoinRequestListener,
        LinkedCallListener,
        LinkedCallMuteChangedListener,
        LinkedCallOfferNoticeListener,
        LinkedCallParticipantsChangedListener,
        LinkedCallPeerStateChangedListener,
        LinkedCallPreacceptListener,
        LinkedCallVideoStateChangedListener,
        LinkedCallVideoUpgradeRequestListener,
        LinkedChatsListener,
        LinkedContactBlacklistListener,
        LinkedContactBlockedListener,
        LinkedContactPresenceListener,
        LinkedContactsListener,
        LinkedContactTextStatusListener,
        LinkedDeviceIdentityChangedListener,
        LinkedDevicesListener,
        LinkedDisappearingModeChangedListener,
        LinkedFacebookGraphQlSessionChangedListener,
        LinkedGroupsListener,
        LinkedLocaleChangedListener,
        LinkedMessageReplyListener,
        LinkedNameChangedListener,
        LinkedNewContactListener,
        LinkedNewslettersListener,
        LinkedNewStatusListener,
        LinkedNodeReceivedListener,
        LinkedNodeSentListener,
        LinkedOptOutListListener,
        LinkedPrivacySettingChangedListener,
        LinkedProfilePictureChangedListener,
        LinkedRegistrationCodeListener,
        LinkedStatusListener,
        LinkedStatusPrivacyChangedListener,
        LinkedTosNoticesChangedListener,
        LinkedWebAppPrimaryFeaturesListener,
        LinkedWebAppStateActionListener,
        LinkedWebHistorySyncMessagesListener,
        LinkedWebHistorySyncPastParticipantsListener,
        LinkedWebHistorySyncProgressListener,
        LinkedGraphQlSessionChangedListener,
        LinkedWhatsAppClientListener,
        InternalLinkedListener {
}
