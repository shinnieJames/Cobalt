
package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.bot.AiThreadTitle;
import com.github.auties00.cobalt.model.bot.BotWelcomeRequestState;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreference;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataEdit;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.OutContact;
import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.device.DeviceCapabilitiesEntry;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterPin;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotification;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.OnboardingHintState;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.model.sync.action.media.MusicUserIdAction;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethod;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.client.proxy.WhatsAppProxy;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.SignalProtocolStore;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.key.SignalSignedKeyPair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Public interface representing the persistent and transient state of a
 * WhatsApp client session. Conceptually this is everything that the official
 * WhatsApp app keeps on disk plus everything the app keeps in memory while
 * the user is logged in.
 *
 * <p>For readers not familiar with WhatsApp internals, the data exposed here
 * roughly maps to the following user-visible features:
 *
 * <ul>
 *   <li><b>Identity and pairing.</b> The phone number, the WhatsApp JID
 *       (for example {@code 1234567890@s.whatsapp.net}), the LID (the
 *       hidden-phone-number variant introduced for privacy), the device
 *       record shown under <i>Linked devices</i>, the Noise/Signal key
 *       material and the ADV (Auth Device V2) signed identity that makes
 *       this client a recognised companion of the primary phone.
 *   <li><b>Profile.</b> Display name, profile picture, about/status text,
 *       and, for WhatsApp Business accounts, the verified business name,
 *       address, location, description, website, email and category shown
 *       on the business profile card.
 *   <li><b>Address book.</b> Contacts (your saved or synced WhatsApp
 *       contacts), out-contacts (contacts you have invited but who have not
 *       yet joined), the bidirectional phone-number/LID mapping table, and
 *       the block list.
 *   <li><b>Conversations.</b> Chats (one-to-one, groups, communities,
 *       broadcast lists), newsletters (Cobalt's name, mirroring WA Web,
 *       for what end users see as <b>Channels</b> in the Updates tab),
 *       status updates (the 24-hour disappearing posts shown in the
 *       Status tab), call history and group/community metadata.
 *   <li><b>Settings.</b> Privacy settings (last-seen, profile photo, about,
 *       groups), chat lock, archived-chat behaviour, link-preview and
 *       call-relay toggles, time-format preference, default disappearing
 *       message timer, locale and the favourite-chats list.
 *   <li><b>Stickers, labels and quick replies.</b> Recent and favourite
 *       stickers, business labels and quick-reply shortcuts.
 *   <li><b>Sync state.</b> The Web/Desktop history-sync policy, the syncd
 *       app-state machine (per-collection version, LtHash, MAC mismatch
 *       flag and pending/orphan mutations), missing sync keys, sync-action
 *       entries and the AB-props feature-flag bundle delivered by the
 *       server.
 *   <li><b>Signal protocol storage.</b> Identity keys, sessions, pre-keys,
 *       signed pre-keys, sender keys, the X3DH base-key dedup table and
 *       sender-key distribution tracking — all the cryptographic state
 *       that makes WhatsApp's end-to-end encryption work.
 *   <li><b>Business and payments.</b> Subscription status, marketing
 *       broadcast lists, broadcast campaigns and insights, custom payment
 *       methods, merchant-payment partner state and orphan payment
 *       notifications.
 * </ul>
 *
 * <p>WhatsApp Web stores all of this across roughly a dozen IndexedDB
 * databases, around a hundred IDB tables, dozens of in-memory reactive
 * collections and a key-value preferences store. Cobalt collapses that
 * fan-out into this single abstraction.
 *
 * <p>Implementations control how and where session data is stored, ranging
 * from fully in-memory with protobuf file persistence to tiered approaches
 * that trade memory for lazy decoding.
 *
 * <p>Instances are obtained exclusively through {@link WhatsAppStoreFactory}.
 *
 * @see SignalProtocolStore
 */
@WhatsAppWebModule(moduleName = "WAWebModelStorageInitialize")
@WhatsAppWebModule(moduleName = "WAWebCollections")
@WhatsAppWebModule(moduleName = "WAWebSignalStorage")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsBase")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsStore")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsKeys")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsLoginKeys")
@WhatsAppWebModule(moduleName = "WAWebGetSyncKey")
@WhatsAppWebModule(moduleName = "WAWebGetSyncAction")
@WhatsAppWebModule(moduleName = "WAWebSyncActionStore")
@WhatsAppWebModule(moduleName = "WAWebGetCollectionVersion")
@WhatsAppWebModule(moduleName = "WAWebGetMissingKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdOrphan")
@WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionsStateMachine")
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface WhatsAppStore extends SignalProtocolStore {
    /**
     * Blocks until all asynchronous initialization operations for this
     * store are complete (e.g., background deserialization of chats and
     * newsletters).
     *
     * <p>Implementations that perform all initialization synchronously
     * may provide an empty implementation of this method.
     * @throws IOException if the store cannot be deserialized completely and/or correctly
     */
    void await() throws IOException;

    /**
     * Flushes all pending changes to the underlying storage.
     *
     * @return this store instance for method chaining
     * @throws IOException if the session cannot be saved
     */
    WhatsAppStore save() throws IOException;

    /**
     * Permanently deletes this session from storage. After this method
     * returns, the session data cannot be recovered.
     *
     * @throws IOException if the session cannot be deleted
     */
    void delete() throws IOException;

    /**
     * Returns the unique identifier for this store instance.
     *
     * @return the UUID, never {@code null}
     */
    UUID uuid();

    /**
     * Returns the client type for this session.
     *
     * @return the client type, never {@code null}
     */
    WhatsAppClientType clientType();

    /**
     * Returns the Unix timestamp (seconds) when this store was created.
     *
     * @return the initialization timestamp in seconds since epoch, never {@code null}
     */
    Instant initializationTimeStamp();

    /**
     * Returns the Signal-protocol registration id assigned to this device.
     * This is the {@code registrationId} field in every Signal pre-key
     * bundle this client publishes; servers and peers use it to tell
     * apart different installs of the same WhatsApp account.
     *
     * @return the registration id, a value between 1 and 16380
     */
    int registrationId();

    /**
     * Returns the long-term Noise key pair this client uses to negotiate
     * the encrypted transport with the WhatsApp servers. The Noise XX
     * handshake at the start of every WebSocket connection authenticates
     * this client by proving possession of the corresponding private key.
     *
     * @return the noise key pair, never {@code null}
     */
    SignalIdentityKeyPair noiseKeyPair();

    /**
     * Returns the long-term Signal identity key pair used for end-to-end
     * encryption with other WhatsApp users. This key pair is what
     * underlies the "Verify security code" QR/number pair that two users
     * can compare in the contact info screen.
     *
     * @return the identity key pair, never {@code null}
     */
    SignalIdentityKeyPair identityKeyPair();

    /**
     * Returns the currently active signed pre-key. WhatsApp rotates this
     * pair periodically; it is the pair every peer uses to bootstrap a
     * new Signal session with this device when no one-time pre-key is
     * available.
     *
     * @return the signed key pair, never {@code null}
     */
    SignalSignedKeyPair signedKeyPair();

    /**
     * Returns the FDID (Facebook Device Id) used by mobile WhatsApp during
     * registration. This is the cross-Meta device fingerprint that the
     * WhatsApp/Facebook/Instagram apps share to identify the same physical
     * device across the family of apps; the WhatsApp registration server
     * checks it for anti-abuse signals.
     *
     * @return the FDID, never {@code null}
     */
    UUID fdid();

    /**
     * Returns the random per-installation device id sent to the WhatsApp
     * registration server. Mobile WhatsApp generates this once at first
     * launch and persists it; it disambiguates re-registrations of the
     * same phone number.
     *
     * @return the device id bytes, never {@code null}
     */
    byte[] deviceId();

    /**
     * Returns the OS-level advertising identifier (Android AAID / iOS
     * IDFA) reported to WhatsApp's analytics for opt-in conversion
     * attribution (notably click-to-WhatsApp ads).
     *
     * @return the advertising id, never {@code null}
     */
    UUID advertisingId();

    /**
     * Returns the per-installation identity id sent during the
     * registration handshake. Used together with the noise and Signal
     * identity key pairs to bind this client install to its phone-number
     * registration record.
     *
     * @return the identity id bytes, never {@code null}
     */
    byte[] identityId();

    /**
     * Returns the backup/recovery token for mobile WhatsApp. This token is
     * what powers the "transfer your account" / chat backup restore flow
     * by binding the encrypted backup blob to the new install.
     *
     * @return the backup token bytes, never {@code null}
     */
    byte[] backupToken();

    /**
     * Returns the phone number associated with this WhatsApp account.
     *
     * <p>The phone number is in international format without the {@code +} prefix
     * (e.g., {@code 1234567890} for +1-234-567-890). It may not be present during
     * initial Web client setup before QR code authentication completes.
     *
     * @return an {@code OptionalLong} containing the phone number, or an empty
     *         {@code OptionalLong} if not yet set
     */
    OptionalLong phoneNumber();

    /**
     * Sets the phone number for this account.
     *
     * @param phoneNumber the phone number in international format, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setPhoneNumber(Long phoneNumber);

    /**
     * Returns the device information identifying this client.
     *
     * @return the device info, never {@code null}
     */
    WhatsAppDevice device();

    /**
     * Sets the device information.
     *
     * @param device the device info, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setDevice(WhatsAppDevice device);

    /**
     * Returns the release channel for this connection.
     *
     * @return the release channel, never {@code null}
     */
    ClientReleaseChannel releaseChannel();

    /**
     * Sets the release channel.
     *
     * @param releaseChannel the release channel, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setReleaseChannel(ClientReleaseChannel releaseChannel);

    /**
     * Returns whether this account appears online to other users.
     *
     * @return {@code true} if online, {@code false} otherwise
     */
    boolean online();

    /**
     * Sets whether this account appears online.
     *
     * @param online {@code true} to appear online, {@code false} otherwise
     * @return this store instance for method chaining
     */
    WhatsAppStore setOnline(boolean online);

    /**
     * Returns the locale/language code for this account.
     *
     * @return an {@code Optional} containing the locale, or empty if not set
     */
    Optional<String> locale();

    /**
     * Sets the locale code.
     *
     * @param locale the locale in format "language_COUNTRY", may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setLocale(String locale);

    /**
     * Returns the display name shown to other WhatsApp users.
     *
     * @return the name, defaults to "User" if not set
     */
    String name();

    /**
     * Sets the display name.
     *
     * @param name the display name, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setName(String name);

    /**
     * Returns the verified business name for verified business accounts.
     *
     * @return an {@code Optional} containing the verified name, or empty for
     *         non-business accounts
     */
    Optional<String> verifiedName();

    /**
     * Sets the verified business name.
     *
     * @param verifiedName the verified name, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setVerifiedName(String verifiedName);

    /**
     * Returns the URL of this account's profile picture.
     *
     * @return an {@code Optional} containing the profile picture URI, or empty
     *         if not set
     */
    Optional<URI> profilePicture();

    /**
     * Sets the profile picture URI.
     *
     * @param profilePicture the profile picture URI, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setProfilePicture(URI profilePicture);

    /**
     * Returns the authenticated user's own text status (the "about" line
     * shown on their profile, optionally with an emoji and an ephemeral
     * expiration). This is the self-account counterpart of
     * {@link #findContactTextStatus(JidProvider)} for other contacts.
     *
     * @return an {@code Optional} containing the self text status, or empty
     *         if not set
     */
    Optional<ContactTextStatus> selfTextStatus();

    /**
     * Sets the authenticated user's own text status.
     *
     * @param selfTextStatus the new text status, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setSelfTextStatus(ContactTextStatus selfTextStatus);

    /**
     * Returns the WhatsApp JID that uniquely identifies this user. A user
     * JID is the international phone number plus
     * {@code @s.whatsapp.net}, with a device suffix appended on
     * companion-device traffic ({@code phoneNumber:deviceId@s.whatsapp.net}).
     * It is the address every other WhatsApp client uses to send messages
     * to this account.
     *
     * @return an {@code Optional} containing the JID, or empty before
     *         authentication completes
     */
    Optional<Jid> jid();

    /**
     * Sets the WhatsApp JID for this account.
     *
     * @param jid the JID, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setJid(Jid jid);

    /**
     * Returns the LID (Local Identifier) for this user. WhatsApp issues
     * each account a phone-number-free shadow id of the form
     * {@code <opaqueId>@lid}; this id is used inside groups, communities
     * and Channels so participants do not see each other's real phone
     * numbers. A bidirectional mapping between the JID and the LID is
     * maintained server-side.
     *
     * @return an {@code Optional} containing the LID, or empty if not set
     */
    Optional<Jid> lid();

    /**
     * Sets the user's LID.
     *
     * @param lid the LID, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setLid(Jid lid);

    /**
     * Returns the physical address of the business.
     *
     * @return an {@code Optional} containing the address, or empty for
     *         non-business accounts
     */
    Optional<String> businessAddress();

    /**
     * Sets the business address.
     *
     * @param businessAddress the business address, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessAddress(String businessAddress);

    /**
     * Returns the geographic longitude of the business location.
     *
     * @return an {@code OptionalDouble} containing the longitude, or empty for
     *         non-business accounts
     */
    OptionalDouble businessLongitude();

    /**
     * Sets the business longitude.
     *
     * @param businessLongitude the longitude (-180.0 to 180.0), may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessLongitude(Double businessLongitude);

    /**
     * Returns the geographic latitude of the business location.
     *
     * @return an {@code OptionalDouble} containing the latitude, or empty for
     *         non-business accounts
     */
    OptionalDouble businessLatitude();

    /**
     * Sets the business latitude.
     *
     * @param businessLatitude the latitude (-90.0 to 90.0), may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessLatitude(Double businessLatitude);

    /**
     * Returns the business description.
     *
     * @return an {@code Optional} containing the description, or empty for
     *         non-business accounts
     */
    Optional<String> businessDescription();

    /**
     * Sets the business description.
     *
     * @param businessDescription the description, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessDescription(String businessDescription);

    /**
     * Returns the business website URLs.
     *
     * <p>A business profile supports multiple website URLs; the field is
     * empty for non-business accounts and for accounts that never published
     * a website on their profile.
     *
     * @return an unmodifiable list of website URLs, or an empty list
     */
    List<URI> businessWebsites();

    /**
     * Sets the business websites.
     *
     * @param businessWebsites the list of website URLs, may be {@code null}
     *                         which is treated as an empty list
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessWebsites(List<URI> businessWebsites);

    /**
     * Returns the business email address.
     *
     * @return an {@code Optional} containing the email, or empty for
     *         non-business accounts
     */
    Optional<String> businessEmail();

    /**
     * Sets the business email.
     *
     * @param businessEmail the email, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessEmail(String businessEmail);

    /**
     * Returns the business category classifications.
     *
     * <p>A business profile supports multiple categories; the field is
     * empty for non-business accounts.
     *
     * @return an unmodifiable list of categories, or an empty list
     */
    List<BusinessCategory> businessCategories();

    /**
     * Sets the business categories.
     *
     * @param businessCategories the list of categories, may be {@code null}
     *                           which is treated as an empty list
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessCategories(List<BusinessCategory> businessCategories);

    /**
     * Returns whether the "Keep chats archived" toggle is off — that is,
     * whether an archived chat should automatically pop back into the main
     * list when a new message arrives. This is the inverse of the
     * <i>Settings → Chats → Keep chats archived</i> switch in the
     * WhatsApp app.
     *
     * @return {@code true} if archived chats unarchive automatically on
     *         new messages
     */
    boolean unarchiveChats();

    /**
     * Sets whether archived chats automatically unarchive on new messages.
     *
     * @param unarchiveChats {@code true} to enable automatic unarchiving
     * @return this store instance for method chaining
     */
    WhatsAppStore setUnarchiveChats(boolean unarchiveChats);

    /**
     * Returns whether 24-hour time format is used.
     *
     * @return {@code true} if using 24-hour format
     */
    boolean twentyFourHourFormat();

    /**
     * Sets whether to use 24-hour time format.
     *
     * @param twentyFourHourFormat {@code true} for 24-hour format
     * @return this store instance for method chaining
     */
    WhatsAppStore setTwentyFourHourFormat(boolean twentyFourHourFormat);

    /**
     * Returns the default ephemeral timer for new chats.
     *
     * @return the ephemeral timer, never {@code null}
     */
    ChatEphemeralTimer newChatsEphemeralTimer();

    /**
     * Sets the default ephemeral timer for new chats.
     *
     * @param timer the ephemeral timer, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setNewChatsEphemeralTimer(ChatEphemeralTimer timer);

    /**
     * Returns the history-sync policy for Web/Desktop sessions.
     *
     * <p>When a companion device first pairs with the primary phone, the
     * primary uploads an encrypted snapshot of recent chat history (the
     * "history sync") so the new device is not blank. This setting
     * controls how much history the primary should send: nothing, recent,
     * or full archive.
     *
     * @return an {@code Optional} containing the history policy, or empty
     *         if not configured
     */
    Optional<WhatsAppWebClientHistory> webHistoryPolicy();

    /**
     * Sets the Web/Desktop history-sync policy.
     *
     * @param policy the history policy, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setWebHistoryPolicy(WhatsAppWebClientHistory policy);

    /**
     * Returns whether the client should automatically broadcast its
     * presence ({@code available} / {@code unavailable} and typing /
     * recording indicators) to the server.
     *
     * <p>When on, contacts will see the standard "online" / "typing…"
     * indicators while the client is connected; when off, the client is
     * effectively invisible. This is the library-level equivalent of
     * keeping the WhatsApp app running in the foreground.
     *
     * @return {@code true} if automatic presence broadcasting is on
     */
    boolean automaticPresenceUpdates();

    /**
     * Sets whether the client automatically broadcasts presence and
     * typing indicators.
     *
     * @param enabled {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setAutomaticPresenceUpdates(boolean enabled);

    /**
     * Returns whether the client automatically sends delivered/read
     * receipts for incoming messages.
     *
     * <p>This is roughly the library-side equivalent of WhatsApp's
     * <i>Settings → Privacy → Read receipts</i> switch combined with
     * acting on the messages: when off, peers will not see the second
     * tick or the blue ticks for messages this client has received.
     *
     * @return {@code true} if automatic receipts are on
     */
    boolean automaticMessageReceipts();

    /**
     * Sets whether automatic delivered/read receipts are sent.
     *
     * @param enabled {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setAutomaticMessageReceipts(boolean enabled);

    /**
     * Returns whether incoming app-state patches must have their snapshot
     * MACs verified before being applied.
     *
     * <p>App-state patches are the encrypted records that propagate
     * settings, archived/starred/muted state and similar metadata between
     * the primary phone and companions. Each patch carries a MAC that
     * proves it has not been tampered with; turning verification off is
     * an interop escape hatch and weakens integrity guarantees.
     *
     * @return {@code true} if patch MACs are verified
     */
    boolean checkPatchMacs();

    /**
     * Sets whether app-state patch MACs are verified.
     *
     * @param enabled {@code true} to enable verification
     * @return this store instance for method chaining
     */
    WhatsAppStore setCheckPatchMacs(boolean enabled);

    /**
     * Returns whether the primary device supports syncd snapshot recovery.
     * The flag is set when the primary device advertises support via peer
     * data operations.
     *
     * @return {@code true} if the primary device supports syncd recovery
     */
    boolean primaryDeviceSupportsSyncdRecovery();

    /**
     * Sets whether the primary device supports syncd snapshot recovery.
     *
     * @param supported {@code true} if supported
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrimaryDeviceSupportsSyncdRecovery(boolean supported);

    /**
     * Returns whether chat data has been synchronized from the server.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedChats();

    /**
     * Sets whether chats have been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedChats(boolean synced);

    /**
     * Returns whether contact data has been synchronized from the server.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedContacts();

    /**
     * Sets whether contacts have been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedContacts(boolean synced);

    /**
     * Returns whether newsletter data has been synchronized from the server.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedNewsletters();

    /**
     * Sets whether newsletters have been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedNewsletters(boolean synced);

    /**
     * Returns whether status updates have been synchronized from the server.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedStatus();

    /**
     * Sets whether status updates have been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedStatus(boolean synced);

    /**
     * Returns whether the business certificate has been synchronized.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedBusinessCertificate();

    /**
     * Sets whether the business certificate has been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedBusinessCertificate(boolean synced);

    /**
     * Returns whether the client has completed registration with WhatsApp.
     *
     * @return {@code true} if registered
     */
    boolean registered();

    /**
     * Sets the registration status.
     *
     * @param registered {@code true} if registered
     * @return this store instance for method chaining
     */
    WhatsAppStore setRegistered(boolean registered);

    /**
     * Returns whether to show security notifications when chatting.
     *
     * @return {@code true} if security notifications are shown
     */
    boolean showSecurityNotifications();

    /**
     * Sets whether to show security notifications.
     *
     * @param show {@code true} to show
     * @return this store instance for method chaining
     */
    WhatsAppStore setShowSecurityNotifications(boolean show);

    /**
     * Returns the ADV (Auth Device V2) signed device identity issued by
     * the primary phone when this companion device was paired.
     *
     * <p>ADV is the protocol behind WhatsApp's multi-device feature: each
     * companion (Web, Desktop, second phone) is endorsed by a signature
     * from the primary phone's identity key, and that signature is what
     * makes the companion a recognised participant on the account.
     * Losing this record forces a re-pairing through the QR-code or phone-
     * number flow.
     *
     * @return an {@code Optional} containing the identity, or empty if not
     *         set
     */
    Optional<ADVSignedDeviceIdentity> signedDeviceIdentity();

    /**
     * Sets the ADV (Auth Device V2) signed device identity.
     *
     * @param identity the signed device identity, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setSignedDeviceIdentity(ADVSignedDeviceIdentity identity);

    /**
     * Returns the 32-byte ADV (Auth Device V2) secret used to HMAC the
     * pairing handshake with the primary phone. Together with
     * {@link #signedDeviceIdentity()} this is what binds a companion's
     * key material to the primary's signature.
     *
     * @return an {@code Optional} containing the 32-byte key, or empty if
     *         not set
     */
    Optional<byte[]> advSecretKey();

    /**
     * Sets the ADV pairing-handshake secret key.
     *
     * @param key the ADV secret key (should be 32 bytes), or {@code null}
     *            to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAdvSecretKey(byte[] key);

    /**
     * Returns all registered pre-keys in insertion order.
     *
     * @return a non-null sequenced collection of pre-key pairs
     */
    SequencedCollection<SignalPreKeyPair> preKeys();

    /**
     * Checks whether any pre-keys are currently available.
     *
     * @return {@code true} if pre-keys are available
     */
    boolean hasPreKeys();

    /**
     * Removes a Signal protocol session by address.
     *
     * @param address the address of the session to remove
     * @return {@code true} if a session was removed
     */
    boolean removeSession(SignalProtocolAddress address);

    /**
     * Removes all sender key records where the given device JID is the sender.
     *
     * @param deviceJid the device JID whose sender keys should be removed
     */
    void removeSenderKeys(Jid deviceJid);

    /**
     * Removes the sender keys for a sender key name.
     *
     * @param senderKeyName the sender key name
     */
    void removeSenderKeys(SignalSenderKeyName senderKeyName);

    /**
     * Cleans up all Signal sessions and sender keys for a device.
     *
     * @param deviceJid the device JID to clean up
     */
    void cleanupSignalSessions(Jid deviceJid);

    /**
     * Persists Alice's X3DH base key for a pre-key message so the receive
     * path can dedupe replays of the same {@code originalMsgId}.
     *
     * @param address       the peer Signal address that initiated the
     *                      session
     * @param originalMsgId the {@code originalMsgId} carried by the pre-key
     *                      stanza
     * @param baseKey       the 32-byte X3DH ephemeral public key extracted
     *                      from the pre-key message
     * @throws NullPointerException if any argument is {@code null}
     */
    void saveSessionBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] baseKey);

    /**
     * Returns the previously-saved Alice base key for a
     * {@code (address, originalMsgId)} pair, if any.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @return an {@link Optional} containing the 32-byte base key, or
     *         {@link Optional#empty()} if no entry exists
     * @throws NullPointerException if any argument is {@code null}
     */
    Optional<byte[]> findSessionBaseKey(SignalProtocolAddress address, String originalMsgId);

    /**
     * Reports whether a stored base key matches the candidate one for the
     * given {@code (address, originalMsgId)} pair.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @param candidate     the candidate base key extracted from the
     *                      newly-received pre-key message
     * @return {@code true} if a base key was previously stored for this
     *         pair and equals {@code candidate}, {@code false} otherwise
     * @throws NullPointerException if any argument is {@code null}
     */
    boolean hasSameBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] candidate);

    /**
     * Removes the persisted base key for a {@code (address, originalMsgId)}
     * pair, if any.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @return {@code true} if an entry was removed
     * @throws NullPointerException if any argument is {@code null}
     */
    boolean removeSessionBaseKey(SignalProtocolAddress address, String originalMsgId);

    /**
     * Returns all contacts stored in this session.
     *
     * @return an unmodifiable collection of all contacts
     */
    Collection<Contact> contacts();

    /**
     * Returns every cached "About" text status. In the WhatsApp app each
     * contact can publish a short personal status (the line under the
     * profile name, for example "At work"); this method exposes the cache
     * of those values for contacts known to this session.
     *
     * @return an unmodifiable collection of cached text statuses, never
     *         {@code null}
     */
    Collection<ContactTextStatus> contactTextStatuses();

    /**
     * Finds a contact by either phone number JID or LID.
     *
     * <p>WhatsApp identifies users by JID. A regular user JID is the phone
     * number plus {@code @s.whatsapp.net} (for example
     * {@code 1234567890@s.whatsapp.net}). A LID is a privacy-preserving
     * alternate identifier ({@code <id>@lid}) that WhatsApp uses in groups,
     * communities and Channels so the real phone number is not exposed.
     * This lookup accepts either form.
     *
     * @param jid the JID to search for (phone or LID)
     * @return an {@code Optional} containing the contact if found
     */
    Optional<Contact> findContactByJid(JidProvider jid);

    /**
     * Adds or updates a contact in the store. A contact represents a
     * WhatsApp user the local account is aware of — typically through the
     * device address book sync or by receiving a message from them.
     *
     * @param contact the contact to add or update, must not be {@code null}
     * @return the contact that was added
     */
    Contact addContact(Contact contact);

    /**
     * Finds the cached "About" text status for the given contact.
     *
     * @param jid the contact JID, may be {@code null}
     * @return an {@code Optional} containing the cached text status if
     *         known
     */
    Optional<ContactTextStatus> findContactTextStatus(JidProvider jid);

    /**
     * Caches an "About" text status for a contact. Called when the server
     * pushes an update to a contact's about line.
     *
     * @param contactJid the contact JID, must not be {@code null}
     * @param status     the cached status, must not be {@code null}
     */
    void addContactTextStatus(Jid contactJid, ContactTextStatus status);

    /**
     * Removes the cached "About" text status for a contact.
     *
     * @param jid the contact JID, may be {@code null}
     * @return an {@code Optional} containing the removed entry if present
     */
    Optional<ContactTextStatus> removeContactTextStatus(JidProvider jid);

    /**
     * Returns the current state of the link between this WhatsApp account
     * and the user's Meta (Facebook/Instagram) account.
     *
     * <p>This corresponds to Meta's <i>Accounts Center</i> integration:
     * the user can link WhatsApp with their Facebook/Instagram identity
     * for shared sign-in, cross-app posting and federated profile data.
     * The value reflects whether that link is active, paused or absent.
     * (The underlying WA Web codename for this feature is "Waffle".)
     *
     * @return an {@code Optional} containing the link state if the server
     *         has ever reported it
     */
    Optional<WaffleAccountLinkStateAction.AccountLinkState> linkedMetaAccountState();

    /**
     * Sets the linked-Meta-account state.
     *
     * @param state the link state, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState state);

    /**
     * Returns the timestamp of the most recent linked-Meta-account
     * change. Used to ignore older updates that arrive out of order.
     *
     * @return an {@code Optional} containing the timestamp if known
     */
    Optional<Instant> linkedMetaAccountStateTimestamp();

    /**
     * Sets the timestamp of the most recent linked-Meta-account change.
     *
     * @param timestamp the timestamp, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setLinkedMetaAccountStateTimestamp(Instant timestamp);

    /**
     * Returns whether this WhatsApp Business account has completed the
     * "hosted automation" onboarding wizard. Hosted automation lets a
     * business automate replies and broadcasts through WhatsApp's hosted
     * tooling rather than running their own Cloud API integration.
     *
     * @return {@code true} if the wizard has been completed
     */
    boolean hostedAutomationOnboarded();

    /**
     * Sets whether the hosted-automation onboarding wizard is complete.
     *
     * @param onboarded {@code true} if completed
     * @return this store instance for method chaining
     */
    WhatsAppStore setHostedAutomationOnboarded(boolean onboarded);

    /**
     * Looks up an orphan WhatsApp Pay notification by message id.
     *
     * <p>Payment notifications can arrive before the chat message they
     * belong to (for example after a fresh history sync). The orphan store
     * holds them until the parent message lands, then attaches them.
     *
     * @param messageId the parent message id, may be {@code null}
     * @return an {@code Optional} containing the orphan notification if one
     *         is buffered
     */
    Optional<OrphanPaymentNotification> findOrphanPaymentNotification(String messageId);

    /**
     * Buffers a WhatsApp Pay notification whose parent message has not yet
     * arrived.
     *
     * @param notification the notification to buffer, must not be
     *                     {@code null}
     */
    void addOrphanPaymentNotification(OrphanPaymentNotification notification);

    /**
     * Removes a buffered WhatsApp Pay orphan notification, typically once
     * its parent message has been attached.
     *
     * @param messageId the parent message id, may be {@code null}
     * @return an {@code Optional} containing the removed notification if it
     *         existed
     */
    Optional<OrphanPaymentNotification> removeOrphanPaymentNotification(String messageId);

    /**
     * Returns the opaque server-routing token issued by WhatsApp.
     *
     * <p>WhatsApp returns this token from the routing-info HTTP endpoint;
     * the client echoes it on every subsequent reconnect so that the load
     * balancer can pin the session to a specific edge cluster.
     *
     * @return an {@code Optional} containing the routing-info bytes if
     *         known
     */
    Optional<byte[]> routingInfo();

    /**
     * Stores the opaque server-routing token.
     *
     * @param routingInfo the routing token, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setRoutingInfo(byte[] routingInfo);

    /**
     * Returns the routing domain hint paired with {@link #routingInfo()},
     * which selects the WhatsApp edge cluster (for example
     * {@code mmg-fna.whatsapp.net}) for the next handshake.
     *
     * @return an {@code Optional} containing the routing domain if known
     */
    Optional<String> routingDomain();

    /**
     * Sets the routing domain hint.
     *
     * @param routingDomain the routing domain, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setRoutingDomain(String routingDomain);

    /**
     * Returns the moment at which this companion device's pairing is set
     * to expire. WhatsApp force-unpairs companion devices that go offline
     * for too long (the "Linked devices" screen lists this as the device
     * being inactive); this is the deadline.
     *
     * @return an {@code Optional} containing the expiration instant if the
     *         server has communicated one
     */
    Optional<Instant> clientExpiration();

    /**
     * Sets the companion-device expiration deadline.
     *
     * @param clientExpiration the expiration instant, may be {@code null}
     *                         to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setClientExpiration(Instant clientExpiration);

    /**
     * Returns the identifiers of the Terms-of-Service notices the user has
     * already acknowledged. WhatsApp shows in-app banners for ToS or
     * privacy-policy updates and remembers which ones the user dismissed
     * so they are not shown again.
     *
     * @return an unmodifiable set of acknowledged notice ids, never
     *         {@code null}
     */
    Set<String> tosNoticeIds();

    /**
     * Replaces the set of acknowledged Terms-of-Service notices.
     *
     * @param noticeIds the new set of notice ids, may be {@code null} to
     *                  clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setTosNoticeIds(Set<String> noticeIds);

    /**
     * Returns whether the Meta AI assistant features are enabled for this
     * account by the server. Meta AI is the conversational assistant
     * available in WhatsApp ("Ask Meta AI") in supported regions.
     *
     * @return {@code true} when Meta AI features are enabled
     */
    boolean aiAvailable();

    /**
     * Sets whether the Meta AI features are enabled for this account.
     *
     * @param aiAvailable {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setAiAvailable(boolean aiAvailable);

    /**
     * Returns the hash of the marketing-message opt-out list, used by
     * WhatsApp Business to detect that the locally cached opt-out roster is
     * up to date with the server. Customers who opt out of marketing
     * messages from a business are added to that list.
     *
     * @return an {@code Optional} containing the hash if it has been
     *         received from the server
     */
    Optional<String> businessOptOutListHash();

    /**
     * Sets the marketing-message opt-out list hash.
     *
     * @param hash the new hash, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessOptOutListHash(String hash);

    /**
     * Returns every per-account WhatsApp Business feature flag.
     *
     * <p>WhatsApp Business uses these flags to gradually roll out features
     * such as catalogs, carts, payments and broadcast variants. Each entry
     * pairs a feature name with whether it is enabled for this account.
     *
     * @return an unmodifiable collection of feature flags, never
     *         {@code null}
     */
    Collection<BusinessFeatureFlag> businessFeatureFlags();

    /**
     * Finds the feature flag with the given name.
     *
     * @param name the feature name, may be {@code null}
     * @return an {@code Optional} containing the flag if found
     */
    Optional<BusinessFeatureFlag> findBusinessFeatureFlag(String name);

    /**
     * Adds or replaces a WhatsApp Business feature flag.
     *
     * @param flag the flag to add or replace, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessFeatureFlag(BusinessFeatureFlag flag);

    /**
     * Removes the feature flag with the given name.
     *
     * @param name the feature name, may be {@code null}
     * @return an {@code Optional} containing the removed flag if it
     *         existed
     */
    Optional<BusinessFeatureFlag> removeBusinessFeatureFlag(String name);

    /**
     * Removes every WhatsApp Business feature flag.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessFeatureFlags();

    /**
     * Returns the lifecycle status of every WhatsApp Business broadcast
     * campaign known to this account. A campaign is a scheduled bulk send
     * to a marketing list; each entry pairs a campaign identifier with its
     * lifecycle status string (for example {@code "DRAFT"},
     * {@code "SCHEDULED"}, {@code "SENDING"} or {@code "DONE"}).
     *
     * @return an unmodifiable collection of campaign statuses, never
     *         {@code null}
     */
    Collection<BusinessCampaignStatus> businessCampaignStatuses();

    /**
     * Finds the campaign status entry for the given campaign id.
     *
     * @param campaignId the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the entry if found
     */
    Optional<BusinessCampaignStatus> findBusinessCampaignStatus(String campaignId);

    /**
     * Adds or replaces a WhatsApp Business campaign status entry.
     *
     * @param status the entry to add or replace, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessCampaignStatus(BusinessCampaignStatus status);

    /**
     * Removes the campaign status entry for the given campaign id.
     *
     * @param campaignId the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the removed entry if it
     *         existed
     */
    Optional<BusinessCampaignStatus> removeBusinessCampaignStatus(String campaignId);

    /**
     * Removes every WhatsApp Business campaign status entry.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessCampaignStatuses();

    /**
     * Returns every click-to-WhatsApp (CTWA) per-customer data sharing
     * preference. Each entry pairs a customer's account LID with the
     * enabled flag chosen by the user.
     *
     * @return an unmodifiable collection of CTWA preferences, never
     *         {@code null}
     */
    Collection<CtwaDataSharingPreference> ctwaDataSharingPreferences();

    /**
     * Finds the CTWA per-customer data sharing preference for the given
     * account LID.
     *
     * @param accountLid the account LID raw string identifying the
     *                   customer, may be {@code null}
     * @return an {@code Optional} containing the preference if recorded
     */
    Optional<CtwaDataSharingPreference> findCtwaDataSharing(String accountLid);

    /**
     * Adds or replaces a CTWA per-customer data sharing preference.
     *
     * @param preference the preference to add or replace, must not be
     *                   {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putCtwaDataSharing(CtwaDataSharingPreference preference);

    /**
     * Removes the CTWA per-customer data sharing preference for the given
     * account LID.
     *
     * @param accountLid the account LID raw string identifying the
     *                   customer, may be {@code null}
     * @return an {@code Optional} containing the removed preference if it
     *         existed
     */
    Optional<CtwaDataSharingPreference> removeCtwaDataSharing(String accountLid);

    /**
     * Removes every CTWA per-customer data sharing preference.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearCtwaDataSharing();

    /**
     * Returns the user's own SMB data sharing with Meta consent value, if
     * known. This is the global single-value tri-state consent surfaced by
     * the {@code <smb_data_sharing_with_meta_consent value="..."/>} child
     * of the privacy notification, with valid wire literals
     * {@code "true"} / {@code "false"} / {@code "notset"}.
     *
     * @return an {@link Optional} carrying the wire literal, or empty when
     *         the value has never been observed or the relay cleared it
     */
    Optional<String> smbDataSharingConsent();

    /**
     * Stores the user's own SMB data sharing with Meta consent value.
     *
     * @param consent the wire literal; one of {@code "true"} /
     *                {@code "false"} / {@code "notset"} / {@code null} to
     *                clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setSmbDataSharingConsent(String consent);

    /**
     * Returns every WhatsApp Business paid subscription this account
     * holds. WhatsApp Business sells paid add-ons (for example catalog
     * hosting or the marketing-messages billing tier); each entry exposes
     * the subscription's lifecycle status, expiration and creation
     * timestamps as a single coherent record.
     *
     * @return an unmodifiable collection of subscriptions, never
     *         {@code null}
     */
    Collection<BusinessSubscription> businessSubscriptions();

    /**
     * Finds the WhatsApp Business subscription with the given identifier.
     *
     * @param id the subscription identifier, may be {@code null}
     * @return an {@code Optional} containing the subscription if found
     */
    Optional<BusinessSubscription> findBusinessSubscription(String id);

    /**
     * Adds or replaces a WhatsApp Business subscription record.
     *
     * @param subscription the subscription to add or replace, must not be
     *                     {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessSubscription(BusinessSubscription subscription);

    /**
     * Removes the WhatsApp Business subscription with the given
     * identifier.
     *
     * @param id the subscription identifier, may be {@code null}
     * @return an {@code Optional} containing the removed subscription if
     *         it existed
     */
    Optional<BusinessSubscription> removeBusinessSubscription(String id);

    /**
     * Removes every WhatsApp Business subscription record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessSubscriptions();

    /**
     * Returns the opaque nonce returned by the server when this WhatsApp
     * Business account was last identified. The nonce ties subsequent
     * Business-API calls back to the same account session.
     *
     * @return an {@code Optional} containing the nonce if it has been
     *         issued
     */
    Optional<String> businessAccountNonce();

    /**
     * Sets the WhatsApp Business account nonce.
     *
     * @param nonce the nonce, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessAccountNonce(String nonce);

    /**
     * Returns whether the "detected outcomes" telemetry signal is enabled.
     * WhatsApp records aggregate, on-device signals about the outcome of
     * actions (for example whether a click-to-WhatsApp ad led to a reply)
     * and uploads them in privacy-preserving form when this flag is on.
     *
     * @return {@code true} if detected-outcomes reporting is enabled
     */
    boolean detectedOutcomesEnabled();

    /**
     * Sets whether the detected-outcomes telemetry signal is enabled.
     *
     * @param enabled {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setDetectedOutcomesEnabled(boolean enabled);

    /**
     * Adds a new contact with only its JID populated.
     *
     * @param jid the JID of the contact to add, must not be {@code null}
     * @return the newly created contact
     */
    Contact addNewContact(Jid jid);

    /**
     * Removes a contact from the store.
     *
     * @param contactJid the JID of the contact to remove, may be {@code null}
     * @return an {@code Optional} containing the removed contact if it existed
     */
    Optional<Contact> removeContact(JidProvider contactJid);

    /**
     * Returns every outgoing contact stored in this session. Outgoing
     * contacts power the invite-by-contact feature and are persisted
     * independently from regular {@link Contact} records.
     *
     * @return an unmodifiable collection of outgoing contacts, never
     *         {@code null}
     */
    Collection<OutContact> outContacts();

    /**
     * Finds an outgoing contact by its phone-number JID.
     *
     * @param jid the JID of the outgoing contact to look up, may be
     *            {@code null}
     * @return an {@code Optional} containing the outgoing contact if it
     *         exists
     */
    Optional<OutContact> findOutContact(Jid jid);

    /**
     * Adds or merges an outgoing contact in the store. If an entry with
     * the same {@linkplain OutContact#jid() JID} is already present, its
     * {@code fullName} and {@code firstName} fields are overwritten with
     * the values supplied by the new record.
     *
     * @param outContact the outgoing contact to add or merge, must not be
     *                   {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore addOutContact(OutContact outContact);

    /**
     * Removes an outgoing contact from the store by its phone-number JID.
     *
     * @param jid the JID of the outgoing contact to remove, may be
     *            {@code null}
     * @return an {@code Optional} containing the removed outgoing contact
     *         if it existed
     */
    Optional<OutContact> removeOutContact(Jid jid);

    /**
     * Removes every outgoing contact from the store.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearOutContacts();

    /**
     * Registers a bidirectional LID mapping for a contact.
     *
     * @param phoneJid the phone number JID
     * @param lidJid   the LID JID
     */
    void registerLidMapping(Jid phoneJid, Jid lidJid);

    /**
     * Registers a bidirectional LID mapping for a contact with a timestamp guard.
     *
     * <p>If the provided {@code timestamp} is non-null and an existing mapping for the
     * same LID was registered with a more recent timestamp, the mapping is not updated.
     * A {@code null} timestamp unconditionally overwrites (equivalent to
     * {@link #registerLidMapping(Jid, Jid)}).
     *
     * @param phoneJid  the phone number JID
     * @param lidJid    the LID JID
     * @param timestamp the mapping creation timestamp, or {@code null} to always accept
     */
    void registerLidMapping(Jid phoneJid, Jid lidJid, Instant timestamp);

    /**
     * Finds the phone number JID for a given LID.
     *
     * @param lidJid the LID to look up
     * @return an {@code Optional} containing the phone number JID if found
     */
    Optional<Jid> findPhoneByLid(Jid lidJid);

    /**
     * Finds the LID for a given phone number JID.
     *
     * @param phoneJid the phone number JID to look up
     * @return an {@code Optional} containing the LID if found
     */
    Optional<Jid> findLidByPhone(Jid phoneJid);

    /**
     * Converts a LID to its phone number equivalent by searching contacts.
     *
     * @param lidJid the LID JID
     * @return an {@code Optional} containing the phone number JID
     */
    Optional<Jid> getPhoneNumberByLid(Jid lidJid);

    /**
     * Converts a phone number JID to its LID equivalent.
     *
     * @param phoneNumberJid the phone number JID
     * @return an {@code Optional} containing the LID JID
     */
    Optional<Jid> getLidByPhoneNumber(Jid phoneNumberJid);

    /**
     * Returns every chat stored in this session.
     *
     * <p>"Chat" covers every conversation row visible in the Chats tab of
     * the WhatsApp app: one-to-one chats with another user, group chats,
     * communities (a parent of multiple group chats) and broadcast lists.
     * Channels (newsletters) are stored separately, see
     * {@link #newsletters()}.
     *
     * @return an unmodifiable collection of all chats
     */
    Collection<Chat> chats();

    /**
     * Finds a chat by its JID. The chat JID is the JID of the
     * counterparty for one-to-one chats, or the group/community JID for
     * group chats.
     *
     * @param jid the JID to search for, may be {@code null}
     * @return an {@code Optional} containing the chat if found
     */
    Optional<Chat> findChatByJid(JidProvider jid);

    /**
     * Creates an empty chat record for the given JID, typically as a
     * placeholder before the first message arrives or before the chat
     * metadata is fetched.
     *
     * @param chatJid the chat JID, must not be {@code null}
     * @return the newly created chat
     */
    Chat addNewChat(Jid chatJid);

    /**
     * Removes a chat from the store. Equivalent to "Delete chat" in the
     * WhatsApp UI: drops the conversation row but does not delete the
     * underlying contact record.
     *
     * @param chatJid the JID of the chat to remove, may be {@code null}
     * @return an {@code Optional} containing the removed chat if it
     *         existed
     */
    Optional<Chat> removeChat(JidProvider chatJid);

    /**
     * Queries the first message whose id matches the one provided in the
     * specified chat or newsletter.
     *
     * @param provider the chat or newsletter to search in
     * @param id       the message id to search
     * @return an {@code Optional} containing the message if found
     */
    Optional<? extends MessageInfo> findMessageById(JidProvider provider, String id);

    /**
     * Queries the first message whose key matches the one provided in the
     * specified chat or newsletter.
     *
     * @param key the message key to search
     * @return an {@code Optional} containing the message if found
     */
    Optional<? extends MessageInfo> findMessageByKey(MessageKey key);

    /**
     * Queries the first message whose id matches the one provided in the
     * specified chat.
     *
     * @param chat the chat to search in
     * @param id   the message id to search
     * @return an {@code Optional} containing the message if found
     */
    Optional<ChatMessageInfo> findMessageById(Chat chat, String id);

    /**
     * Queries the first message whose id matches the one provided in the
     * specified newsletter.
     *
     * @param newsletter the newsletter to search in
     * @param id         the message id to search
     * @return an {@code Optional} containing the message if found
     */
    Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id);

    /**
     * Finds the message that the given message is quoting, if any.
     *
     * @param info the message whose quoted message should be resolved
     * @return an {@code Optional} containing the quoted message if found
     */
    Optional<? extends MessageInfo> findQuotedMessage(MessageInfo info);

    /**
     * Returns every status update stored in this session.
     *
     * <p>Statuses are the 24-hour disappearing photo, video and text posts
     * shown in WhatsApp's "Status" tab (called "Updates" in newer
     * versions). Each entry here represents one such post, either
     * authored by the local user or received from a contact.
     *
     * @return an unmodifiable collection of status updates
     */
    Collection<ChatMessageInfo> status();

    /**
     * Adds a 24-hour status update to the store.
     *
     * @param messageInfo the status message, must not be {@code null}
     * @return the status message that was added
     */
    ChatMessageInfo addStatus(ChatMessageInfo messageInfo);

    /**
     * Removes a 24-hour status update from the store, typically because
     * its TTL expired or the author revoked it.
     *
     * @param id the status message id to remove
     * @return an {@code Optional} containing the removed status if it existed
     */
    Optional<ChatMessageInfo> removeStatus(String id);

    /**
     * Returns the cached status update with the given message id, if
     * present.
     *
     * <p>Equivalent to scanning {@link #status()} for an entry whose
     * {@code key().id()} matches, but answers in {@code O(log n)} for
     * persistent stores and {@code O(1)} for transient ones.
     *
     * @param id the status message id to look up
     * @return the matching status update, or empty if no such entry exists
     */
    Optional<ChatMessageInfo> findStatusById(String id);

    /**
     * Returns every cached VoIP call offer.
     *
     * <p>Each entry corresponds to a row in WhatsApp's Calls tab — one of
     * the audio or video calls placed to or received by this device. The
     * "offer" terminology comes from the WebRTC handshake the call rides
     * on: the offer carries the media-negotiation parameters and stays
     * around in the store so the call can be answered, declined or shown
     * as missed.
     *
     * @return an unmodifiable collection of all calls
     */
    Collection<IncomingCall> calls();

    /**
     * Finds a call by its ID.
     *
     * @param callId the call ID to search for
     * @return an {@code Optional} containing the call if found
     */
    Optional<IncomingCall> findCallById(String callId);

    /**
     * Adds a call to the store.
     *
     * @param call the call to add, must not be {@code null}
     * @return the call that was added
     */
    IncomingCall addCall(IncomingCall call);

    /**
     * Removes a call from the store.
     *
     * @param id the call ID to remove
     * @return an {@code Optional} containing the removed call if it existed
     */
    Optional<IncomingCall> removeCall(String id);

    /**
     * Returns every WhatsApp Channel this account follows or owns.
     *
     * <p>"Newsletter" is WhatsApp's internal name for what end users see
     * as a <b>Channel</b>: a one-to-many broadcast surface where an admin
     * publishes posts and followers can react but cannot reply. Channels
     * appear in the Updates tab.
     *
     * @return an unmodifiable collection of all newsletters (channels)
     */
    Collection<Newsletter> newsletters();

    /**
     * Finds a WhatsApp Channel by its newsletter JID. Channel JIDs use
     * the {@code @newsletter} suffix.
     *
     * @param jid the JID to search for, may be {@code null}
     * @return an {@code Optional} containing the newsletter if found
     */
    Optional<Newsletter> findNewsletterByJid(JidProvider jid);

    /**
     * Creates a new empty newsletter (channel) record for the given JID,
     * typically as the first step of following a channel before the
     * server-side metadata arrives.
     *
     * @param newsletterJid the newsletter JID, must not be {@code null}
     * @return the newly created newsletter
     */
    Newsletter addNewNewsletter(Jid newsletterJid);

    /**
     * Removes a WhatsApp Channel from the store, typically because the
     * user unfollowed or deleted it.
     *
     * @param newsletterJid the JID of the newsletter to remove, may be
     *                      {@code null}
     * @return an {@code Optional} containing the removed newsletter if it
     *         existed
     */
    Optional<Newsletter> removeNewsletter(JidProvider newsletterJid);

    /**
     * Returns every privacy setting controlling who can see this
     * account's profile fields.
     *
     * <p>These are the toggles surfaced under <i>Settings → Privacy</i>:
     * who can see last-seen and online status, profile photo, the
     * "About" line, statuses, who can add the user to groups, who can
     * call, read receipts and so on. Each entry pairs a category (the
     * {@link PrivacySettingType}) with a visibility scope.
     *
     * @return an unmodifiable collection of privacy settings
     */
    Collection<PrivacySettingEntry> privacySettings();

    /**
     * Finds the privacy setting for a single category (last-seen, profile
     * photo, status, etc.).
     *
     * @param type the privacy setting type
     * @return an {@code Optional} containing the setting if found
     */
    Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type);

    /**
     * Adds or updates a single privacy setting category.
     *
     * @param entry the privacy setting entry, must not be {@code null}
     */
    void addPrivacySetting(PrivacySettingEntry entry);

    /**
     * Finds a recent sticker by its hash.
     *
     * @param stickerHash the sticker hash
     * @return an {@code Optional} containing the sticker if found
     */
    Optional<Sticker> findRecentSticker(String stickerHash);

    /**
     * Adds a recent sticker to the store.
     *
     * @param stickerHash the sticker hash, must not be {@code null}
     * @param sticker     the sticker, must not be {@code null}
     */
    void addRecentSticker(String stickerHash, Sticker sticker);

    /**
     * Removes a recent sticker from the store.
     *
     * @param stickerHash the sticker hash
     * @return an {@code Optional} containing the removed sticker if it existed
     */
    Optional<Sticker> removeRecentSticker(String stickerHash);

    /**
     * Finds a favourite sticker by its hash.
     *
     * @param stickerHash the sticker hash
     * @return an {@code Optional} containing the sticker if found
     */
    Optional<Sticker> findFavouriteSticker(String stickerHash);

    /**
     * Adds a favourite sticker to the store.
     *
     * @param stickerHash the sticker hash, must not be {@code null}
     * @param sticker     the sticker, must not be {@code null}
     */
    void addFavouriteSticker(String stickerHash, Sticker sticker);

    /**
     * Removes a favourite sticker from the store.
     *
     * @param stickerHash the sticker hash
     * @return an {@code Optional} containing the removed sticker if it existed
     */
    Optional<Sticker> removeFavouriteSticker(String stickerHash);

    /**
     * Finds a quick reply by its stable identifier. The {@code id} parameter
     * is the same value used as the second element of the sync index
     * ({@code indexParts[1]}) and as the primary key of the underlying
     * {@code quick-reply} IndexedDB table.
     *
     * @param id the quick reply identifier
     * @return an {@code Optional} containing the quick reply if found
     */
    Optional<QuickReply> findQuickReply(String id);

    /**
     * Adds or replaces a quick reply in the store. The quick reply is keyed
     * by its {@link QuickReply#id() id} field, so mutations that change the
     * shortcut while preserving the id replace the existing entry.
     *
     * @param quickReply the quick reply, must not be {@code null} and must
     *                   have a non-{@code null} id
     */
    void addQuickReply(QuickReply quickReply);

    /**
     * Removes a quick reply from the store by its stable identifier.
     *
     * @param id the quick reply identifier
     * @return an {@code Optional} containing the removed quick reply if it existed
     */
    Optional<QuickReply> removeQuickReply(String id);

    /**
     * Returns an unmodifiable snapshot of every quick reply currently held
     * in the store, in no particular order.
     *
     * @return the quick replies
     */
    List<QuickReply> quickReplies();

    /**
     * Finds a label by its ID.
     *
     * @param labelId the label ID
     * @return an {@code Optional} containing the label if found
     */
    Optional<Label> findLabel(String labelId);

    /**
     * Adds a label to the store.
     *
     * @param label the label, must not be {@code null}
     */
    void addLabel(Label label);

    /**
     * Returns all labels in the store.
     *
     * @return an unmodifiable collection of labels
     */
    Collection<Label> labels();

    /**
     * Removes a label from the store.
     *
     * @param labelId the label ID
     * @return an {@code Optional} containing the removed label if it existed
     */
    Optional<Label> removeLabel(String labelId);

    /**
     * Returns all app state sync keys currently held in the store, in their
     * original insertion order.
     *
     * @return an unmodifiable sequenced collection of every stored sync key,
     *         empty if no keys have been stored
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getAllSyncKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    SequencedCollection<AppStateSyncKey> appStateKeys();

    /**
     * Returns the app state sync key with the given raw {@code keyId}, if
     * one is stored. Direct, non-validated read intended for low-level
     * subsystems only.
     *
     * @param id the raw sync key identifier bytes, must not be {@code null}
     * @return an {@link Optional} containing the matching key, or
     *         {@link Optional#empty()} if no key with this id is stored
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getSyncKeyInTransaction_DO_NOT_USE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id);

    /**
     * Inserts or replaces a batch of app state sync keys. Cobalt accepts a
     * {@link Collection} per call to amortise the rotation and key-share
     * batched store paths. Implementations skip keys whose
     * {@code keyData.keyData} payload is absent or empty.
     *
     * @param keys the collection of keys to add or update, must not be
     *             {@code null}
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "setSyncKeyInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addWebAppStateKeys(Collection<AppStateSyncKey> keys);

    /**
     * Marks every app state sync key whose generation timestamp is at or
     * before the given instant as expired by zeroing its timestamp.
     *
     * @param threshold the cutoff instant. Keys with timestamps at or before
     *                  this value have their timestamp set to
     *                  {@link Instant#EPOCH}
     */
    void expireAppStateKeys(Instant threshold);

    /**
     * Marks every app state sync key whose derived epoch equals the provided
     * value as expired by zeroing its embedded timestamp. Soft-mark only,
     * keys remain in the store.
     *
     * @param epoch the sync key epoch whose keys should be expired
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "expireSyncKeyInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void expireAppStateKeysByEpoch(int epoch);

    /**
     * Finds a hash state by patch type.
     *
     * @param patchType the patch type to query
     * @return an {@code Optional} containing the hash state if found
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "getCollectionVersionLtHashInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<SyncHashValue> findWebAppHashStateByName(SyncPatchType patchType);

    /**
     * Adds or updates a hash state for app state synchronization.
     *
     * @param state the hash state to add, must not be {@code null}
     */
    void addWebAppHashState(SyncHashValue state);

    /**
     * Finds a sync action entry by collection and index MAC.
     *
     * @param patchType the collection type
     * @param indexMac  the index MAC identifying the entry
     * @return an {@code Optional} containing the entry if found
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByIndexMacsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<SyncActionEntry> findSyncActionEntry(SyncPatchType patchType, byte[] indexMac);

    /**
     * Finds a sync action entry by collection and plaintext action index.
     * The plaintext index is key-independent, unlike the index MAC, which is
     * required for REMOVE operations where the encrypting key may differ
     * from the key used for the original SET.
     *
     * @param patchType    the collection type
     * @param actionIndex  the plaintext action index string
     * @return an {@code Optional} containing the entry if found
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<SyncActionEntry> findSyncActionEntryByActionIndex(SyncPatchType patchType, String actionIndex);

    /**
     * Stores or updates a sync action entry for the specified collection.
     *
     * @param patchType the collection type
     * @param indexMac  the index MAC identifying the entry
     * @param entry     the entry to store
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSyncActionStore",
            exports = "WAWebSyncActionStore",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void putSyncActionEntry(SyncPatchType patchType, byte[] indexMac, SyncActionEntry entry);

    /**
     * Removes a sync action entry from the specified collection.
     *
     * @param patchType the collection type
     * @param indexMac  the index MAC identifying the entry to remove
     * @return an {@code Optional} containing the removed entry if it existed
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByIndexMacsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<SyncActionEntry> removeSyncActionEntry(SyncPatchType patchType, byte[] indexMac);

    /**
     * Clears all sync action entries for the specified collection.
     *
     * <p>Used when a full snapshot is received and the state must be rebuilt from scratch.
     *
     * @param patchType the collection type
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByCollectionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void clearSyncActionEntries(SyncPatchType patchType);

    /**
     * Returns all sync action entries for the specified collection.
     *
     * @param patchType the collection type
     * @return an unmodifiable collection of entries, or an empty collection if none exist
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByCollectionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Collection<SyncActionEntry> getSyncActionEntries(SyncPatchType patchType);

    /**
     * Returns the total number of sync action entries currently held in the
     * store across every {@link SyncPatchType} collection. WA Web stores the
     * entries in an unpartitioned table keyed by plaintext {@code index};
     * Cobalt partitions storage by patch type and therefore sums the
     * per-patch-type entry counts.
     *
     * @return the total number of sync action entries currently stored
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "countSyncActionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    int countSyncActionEntries();

    /**
     * Returns every sync action entry currently held in the store across
     * every {@link SyncPatchType} collection.
     *
     * @return an unmodifiable collection of every stored entry, empty if no
     *         entries are stored
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getAllSyncActions",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Collection<SyncActionEntry> getAllSyncActionEntries();

    /**
     * Returns every missing sync key entry currently being tracked.
     *
     * @return an unmodifiable collection of every stored missing-key entry,
     *         empty when no keys are tracked
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getAllMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Collection<MissingDeviceSyncKey> missingSyncKeys();

    /**
     * Finds the missing sync key entry whose primary key matches the given
     * raw {@code keyId} bytes. Single-element form of WA Web's bulk-get,
     * encoding the input bytes with {@link HexFormat} to recover the
     * {@code keyHex} primary key.
     *
     * @param keyId the raw key identifier bytes to look up
     * @return an {@link Optional} containing the entry if present,
     *         {@link Optional#empty()} otherwise
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkGetMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId);

    /**
     * Returns the number of missing sync keys currently being tracked.
     *
     * @return the number of missing-key entries, {@code 0} when none are
     *         tracked
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getMissingKeyCountTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    int missingSyncKeyCount();

    /**
     * Adds or updates a single missing sync key entry. The entry is keyed by
     * its raw {@code keyId} bytes encoded as a hex string, replacing any
     * existing record with the same key.
     *
     * @param missingKey the missing-key entry to add or replace
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addMissingSyncKey(MissingDeviceSyncKey missingKey);

    /**
     * Adds or updates the given missing sync key entries in bulk. Each entry
     * is keyed by its raw {@code keyId} bytes encoded as a hex string,
     * replacing any existing record with the same key. Passing an empty
     * collection is a no-op, {@code null} is not accepted.
     *
     * @param missingKeys the missing-key entries to upsert
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addMissingSyncKeys(Collection<MissingDeviceSyncKey> missingKeys);

    /**
     * Removes the missing sync key entry whose primary key matches the given
     * raw {@code keyId} bytes. Used by the missing-key workflow to evict a
     * key once a companion device has supplied it.
     *
     * @param keyId the raw key identifier bytes to remove
     */
    void removeMissingSyncKey(byte[] keyId);

    /**
     * Stores a peer message for offline retry.
     *
     * @param id      the message id
     * @param message the peer message to store
     */
    void addPeerMessage(String id, ChatMessageInfo message);

    /**
     * Removes a peer message after successful delivery.
     *
     * @param id the message id to remove
     */
    void removePeerMessage(String id);

    /**
     * Gets metadata for a web app state collection.
     *
     * @param collectionName the collection name
     * @return the collection metadata
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdCollectionsStateMachine",
            exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    SyncCollectionMetadata findWebAppState(SyncPatchType collectionName);

    /**
     * Updates a collection's version and LT-Hash.
     *
     * @param collectionName the collection name
     * @param newVersion     the new version
     * @param newLtHash      the new LT-Hash
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "updateCollectionVersionAndLtHashInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void updateWebAppStateVersion(SyncPatchType collectionName, long newVersion, byte[] newLtHash);

    /**
     * Marks a web app state collection as dirty.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateDirty(SyncPatchType collectionName);

    /**
     * Marks a web app state collection as in-flight.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateInFlight(SyncPatchType collectionName);

    /**
     * Marks a web app state collection as up-to-date.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateUpToDate(SyncPatchType collectionName);

    /**
     * Marks a web app state collection as pending.
     *
     * @param collectionName the collection name
     */
    void markWebAppStatePending(SyncPatchType collectionName);

    /**
     * Marks a web app state collection as blocked.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateBlocked(SyncPatchType collectionName);

    /**
     * Marks a web app state collection in error retry state.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateErrorRetry(SyncPatchType collectionName);

    /**
     * Marks a web app state collection in fatal error state.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateErrorFatal(SyncPatchType collectionName);

    /**
     * Returns whether the specified collection is currently flagged as having
     * suffered a fatal MAC mismatch. Once set, the flag persists across all
     * collection state transitions until cleared by a successful resync.
     *
     * @param collectionName the collection name
     * @return {@code true} if the collection is in the fatal MAC mismatch
     *         state, {@code false} otherwise
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "getIsCollectionInMacMismatchFatalInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    boolean isCollectionInMacMismatchFatal(SyncPatchType collectionName);

    /**
     * Marks a web app state collection in MAC mismatch state. When a patch
     * snapshot MAC does not match the locally computed value, the collection
     * enters this degraded state rather than failing fatally. Processing
     * continues but integrity may be compromised.
     *
     * @param collectionName the collection name
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "updateIsCollectionInMacMismatchFatalInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void markWebAppStateMacMismatch(SyncPatchType collectionName);

    /**
     * Adds pending mutations to the queue for the specified collection.
     *
     * @param collectionName the collection name
     * @param patch          the patches to queue
     */
    void addPendingMutations(SyncPatchType collectionName, Collection<? extends SyncPendingMutation> patch);

    /**
     * Gets all pending mutations for the specified collection.
     *
     * @param collectionName the collection name
     * @return an unmodifiable sequenced collection of pending mutations
     */
    SequencedCollection<SyncPendingMutation> findPendingMutations(SyncPatchType collectionName);

    /**
     * Removes pending mutations for a collection.
     *
     * @param collectionName the collection name
     */
    void removePendingMutations(SyncPatchType collectionName);

    /**
     * Removes the pending mutations with the specified IDs from a collection.
     *
     * @param collectionName the collection name
     * @param mutationIds the mutation IDs to remove
     */
    void removePendingMutations(SyncPatchType collectionName, Collection<String> mutationIds);

    /**
     * Clears all pending mutations for a collection.
     *
     * @param collectionName the collection name
     */
    void clearPendingMutations(SyncPatchType collectionName);

    /**
     * Adds an orphan mutation for the specified collection. Orphan mutations
     * reference entities that do not yet exist locally and are retried when
     * the referenced entities arrive.
     *
     * @param collectionName the collection name
     * @param mutation       the orphan mutation entry
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "checkOrphanMutations",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addOrphanMutation(SyncPatchType collectionName, OrphanMutationEntry mutation);

    /**
     * Returns all orphan mutations for the specified collection.
     *
     * @param collectionName the collection name
     * @return the list of orphan mutation entries, never {@code null}
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getOrphanSyncActionsByModelTypeInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    List<OrphanMutationEntry> findOrphanMutations(SyncPatchType collectionName);

    /**
     * Returns orphan mutations matching the specified model identifier
     * within a collection. Enables targeted orphan lookups by entity
     * (for example by chat JID) instead of a full scan.
     *
     * @param collectionName the collection name
     * @param modelId        the model identifier to match (for example a JID string)
     * @return the list of matching orphan mutation entries, never {@code null}
     */
    List<OrphanMutationEntry> findOrphanMutationsByModel(SyncPatchType collectionName, String modelId);

    /**
     * Removes all orphan mutations for the specified collection.
     *
     * @param collectionName the collection name
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "applyAllOrphansAndUnsupported",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void removeOrphanMutations(SyncPatchType collectionName);

    /**
     * Removes specific orphan mutation entries from the specified collection.
     *
     * @param collectionName the collection name
     * @param entries        the entries to remove
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "applyAllOrphansAndUnsupported",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void removeOrphanMutations(SyncPatchType collectionName, Collection<OrphanMutationEntry> entries);

    /**
     * Marks a participant as having received the sender key for a group.
     *
     * @param groupJid       the group JID
     * @param participantJid the participant device JID
     */
    void markSenderKeyDistributed(Jid groupJid, Jid participantJid);

    /**
     * Checks if a participant has received the sender key for a group.
     *
     * @param groupJid       the group JID
     * @param participantJid the participant device JID
     * @return {@code true} if the participant has received the sender key
     */
    boolean hasSenderKeyDistributed(Jid groupJid, Jid participantJid);

    /**
     * Clears the sender key distribution status for all participants in a group.
     *
     * @param groupJid the group JID
     */
    void clearSenderKeyDistribution(Jid groupJid);

    /**
     * Clears the sender key distribution status for a participant across all groups.
     *
     * @param participantJid the participant JID
     */
    void clearSenderKeyDistributionForParticipant(Jid participantJid);

    /**
     * Marks the sender key as forgotten for a specific participant in a group.
     *
     * @param groupJid       the group JID
     * @param participantJid the participant JID
     */
    void forgetSenderKeyDistributed(Jid groupJid, Jid participantJid);

    /**
     * Marks a user as needing sender key rotation.
     *
     * @param userJid the user JID whose device list changed
     */
    void markKeyRotation(Jid userJid);

    /**
     * Checks if a user needs sender key rotation and clears the flag.
     *
     * @param userJid the user JID to check
     * @return {@code true} if the user needed rotation (flag is cleared)
     */
    boolean clearKeyRotation(Jid userJid);

    /**
     * Checks if any of the provided users need sender key rotation.
     *
     * @param userJids the user JIDs to check
     * @return {@code true} if any user needs rotation
     */
    boolean isKeyRotated(Collection<Jid> userJids);

    /**
     * Allocates a new send sequence number and records it against
     * each device's identity.
     *
     * @param devices the device JIDs that were encrypted to
     * @return the allocated sequence number
     */
    long updateIdentityRange(Collection<Jid> devices);

    /**
     * Checks whether a device's identity key may have changed since
     * the given send sequence.
     *
     * @param sendSequence the sequence returned by {@link #updateIdentityRange}
     * @param device       the device JID to check
     * @return {@code true} if the identity may have changed
     */
    boolean hasIdentityChanged(long sendSequence, Jid device);

    /**
     * Clears the identity range entry for a device.
     *
     * @param device the device JID whose range should be cleared
     */
    void clearIdentityRange(Jid device);

    /**
     * Saves an identity key for a remote user.
     *
     * @param address     the signal address for the user
     * @param identityKey the identity key to save
     */
    void saveIdentity(SignalProtocolAddress address, SignalIdentityPublicKey identityKey);

    /**
     * Finds a stored identity key for a user.
     *
     * @param address the signal address for the user
     * @return an {@code Optional} containing the identity key if found
     */
    Optional<SignalIdentityPublicKey> findIdentityByAddress(SignalProtocolAddress address);

    /**
     * Returns the recipient device JIDs recorded for a sent message.
     *
     * @param messageId the message ID
     * @return an unmodifiable copy of the recipient set
     */
    Set<Jid> findReceiptRecords(String messageId);

    /**
     * Creates or merges receipt records for a sent message.
     *
     * @param messageId     the message ID
     * @param recipientJids the recipient device JIDs
     */
    void createOrMergeReceiptRecords(String messageId, Collection<Jid> recipientJids);

    /**
     * Removes receipt records for a message.
     *
     * @param messageId the message ID to remove records for
     */
    void removeReceiptRecords(String messageId);

    /**
     * Returns the client version.
     *
     * @return the client version, never {@code null}
     */
    ClientAppVersion clientVersion();

    /**
     * Sets the client version.
     *
     * @param version the client version, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setClientVersion(ClientAppVersion version);

    /**
     * Returns the companion version.
     *
     * @return an {@code Optional} containing the companion version, or empty
     *         if not set
     */
    Optional<ClientAppVersion> companionVersion();

    /**
     * Sets the companion version.
     *
     * @param version the companion version, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setCompanionVersion(ClientAppVersion version);

    /**
     * Gets the device list for a user with full metadata.
     *
     * @param userJid the user JID
     * @return an {@code Optional} containing the device list, or empty
     *         if not cached
     */
    Optional<DeviceList> findDeviceList(Jid userJid);

    /**
     * Returns all cached device lists.
     *
     * @return an unmodifiable collection of device lists
     */
    Collection<DeviceList> deviceLists();

    /**
     * Stores a device list for a user with LRU eviction.
     *
     * @param deviceList the device list to store
     */
    void addDeviceList(DeviceList deviceList);

    /**
     * Clears the device list cache for a user.
     *
     * @param userJid the user JID
     */
    void removeDeviceList(Jid userJid);

    /**
     * Clears all device lists from cache.
     */
    void clearDeviceLists();

    /**
     * Gets the last ADV check time.
     *
     * @return an {@code Optional} containing the last check time, or empty
     *         if never checked
     */
    Optional<Instant> lastAdvCheckTime();

    /**
     * Updates the last ADV check time to now.
     */
    void updateAdvCheckTime();

    /**
     * Gets all pending device sync requests.
     *
     * @return an unmodifiable list of pending syncs
     */
    List<PendingDeviceSync> pendingDevicesSyncs();

    /**
     * Adds a pending device sync request for retry.
     *
     * @param sync the pending sync
     */
    void addPendingDeviceSync(PendingDeviceSync sync);

    /**
     * Removes a pending device sync request.
     *
     * @param sync the sync to remove
     */
    void removePendingDeviceSync(PendingDeviceSync sync);

    /**
     * Clears all pending device sync requests.
     */
    void clearPendingDeviceSyncs();

    /**
     * Gets all devices with unconfirmed identity changes.
     *
     * @return an unmodifiable set of device JIDs
     */
    Set<Jid> unconfirmedIdentityChanges();

    /**
     * Marks a device as having an unconfirmed identity change.
     *
     * @param deviceJid the device JID
     */
    void markIdentityChange(Jid deviceJid);

    /**
     * Confirms an identity change for a device.
     *
     * @param deviceJid the device JID
     */
    void confirmIdentityChange(Jid deviceJid);

    /**
     * Clears all unconfirmed identity changes.
     */
    void clearUnconfirmedIdentityChanges();

    /**
     * Records that the given peer JID has been verified as a hosted (i.e.
     * server-side, not on-device) WhatsApp interop user.
     *
     * <p>WhatsApp interoperability — required by the EU Digital Markets
     * Act — lets WhatsApp users exchange messages with users on other
     * messengers (Signal, Messenger, etc.). Hosted-mode interop runs the
     * encryption on Meta's servers under E2EE attestation rather than on
     * the third-party app; this cache memoizes which peers have already
     * been verified as hosted so the verification handshake is not
     * re-run on every message. (The underlying WA Web codename is
     * "coex", short for "coexistence".)
     *
     * @param userJid the user JID to record
     */
    void addToInteropHostedVerificationCache(Jid userJid);

    /**
     * Returns whether the given peer JID has previously been verified as a
     * hosted WhatsApp interop user.
     *
     * @param userJid the user JID to check
     * @return {@code true} if the user has been verified as hosted
     */
    boolean isInInteropHostedVerificationCache(Jid userJid);

    /**
     * Clears the interop hosted-verification cache, forcing every peer to
     * be re-verified.
     */
    void clearInteropHostedVerificationCache();

    /**
     * Records that the stored click-to-WhatsApp (CTWA) UTM payload for the
     * given chat has been consumed, so that {@link #hasReadUtmForChat(Jid)}
     * returns {@code true} on subsequent reads until the entry is evicted
     * via {@link #deleteUtmReadChatId(Jid)} or {@link #clearUtmReadChatIds()}.
     *
     * @param chatJid the chat JID whose UTM payload has just been read;
     *                {@code null} is ignored
     */
    void markUtmReadForChat(Jid chatJid);

    /**
     * Returns whether the CTWA UTM payload for the given chat has already
     * been consumed during this session.
     *
     * @param chatJid the chat JID to check
     * @return {@code true} if the chat's UTM payload was read during this
     *         session and the entry has not since been evicted
     */
    boolean hasReadUtmForChat(Jid chatJid);

    /**
     * Evicts the given chat from the UTM-read cache, so that its UTM payload
     * will be re-read the next time it is requested. Invoked when a fresh
     * UTM value is persisted for the chat, matching WA Web's
     * {@code WAWebUpdateUtmAction.addUtmToChat} flow.
     *
     * @param chatJid the chat JID to forget; {@code null} is ignored
     */
    void deleteUtmReadChatId(Jid chatJid);

    /**
     * Clears every entry from the UTM-read cache. Mirrors the
     * {@code clearAll} lifecycle hook of {@code WAWebChatUtmCache}, which
     * is called during logout / account-swap.
     */
    void clearUtmReadChatIds();

    /**
     * Returns the set of contacts currently blocked by this account.
     *
     * @return an unmodifiable view of the blocked-contact set
     */
    Set<Jid> blockedContacts();

    /**
     * Marks the given contact as blocked. The JID is normalized to its
     * user JID form so duplicate device variants do not accumulate.
     *
     * @param contact the contact to block; {@code null} is ignored
     */
    void addBlockedContact(Jid contact);

    /**
     * Removes the given contact from the block list.
     *
     * @param contact the contact to unblock; {@code null} is ignored
     */
    void removeBlockedContact(Jid contact);

    /**
     * Replaces the entire block list with the given collection.
     *
     * @param contacts the new block list; {@code null} clears it
     */
    void setBlockedContacts(Collection<Jid> contacts);

    /**
     * Finds the verified business name record for the given JID.
     *
     * @param jid the user JID
     * @return an {@code Optional} containing the record if found
     */
    Optional<BusinessVerifiedName> findVerifiedBusinessName(Jid jid);

    /**
     * Adds or replaces a verified business name record.
     *
     * @param jid the jid that owns the record
     * @param record the record to store
     */
    void addVerifiedBusinessName(Jid jid, BusinessVerifiedName record);

    /**
     * Removes the verified business name record for the given JID.
     *
     * @param jid the user JID
     */
    void removeVerifiedBusinessName(Jid jid);

    /**
     * Returns the metadata for the group or community identified by the
     * given JID, if it has been stored.
     *
     * @param groupJid the group or community JID
     * @return an {@code Optional} containing the metadata if found
     */
    Optional<ChatMetadata> findChatMetadata(Jid groupJid);

    /**
     * Stores the metadata for a group or community, replacing any
     * previously stored metadata for the same JID.
     *
     * @param metadata the non-{@code null} metadata to store
     */
    void addChatMetadata(ChatMetadata metadata);

    /**
     * Removes the stored metadata for the group or community identified
     * by the given JID.
     *
     * @param groupJid the group or community JID
     */
    void removeChatMetadata(Jid groupJid);

    /**
     * Applies the local-only fields of a {@link GroupMetadataEdit} to
     * the in-memory {@link GroupMetadata} row for {@code groupJid},
     * returning the mutated row when present.
     *
     * <p>Only fields that have a defined local merge are honoured —
     * today that is exclusively
     * {@link GroupMetadataEdit#statusMuted() statusMuted}, which the
     * {@code WAWebUserStatusMuteSync.applyMutations} sync action drives
     * directly into the store without a network round-trip. Other
     * fields on the edit (subject, description, picture, settings
     * flags) are not merged here because they are server-authoritative
     * and only become visible to the local store via a subsequent
     * {@code group_metadata} notification.
     *
     * <p>When the target group has no metadata row in the store, this
     * method returns {@link Optional#empty()} without applying any
     * mutation.
     *
     * @param groupJid the group or community JID; must not be
     *                 {@code null}
     * @param edit     the edit packet whose local-only fields are
     *                 merged into the stored row; must not be
     *                 {@code null}
     * @return an {@link Optional} carrying the mutated
     *         {@link GroupMetadata} row, or empty when the group is
     *         not known to the store
     */
    Optional<GroupMetadata> applyGroupMetadataEdit(Jid groupJid, GroupMetadataEdit edit);

    /**
     * Adds an event listener to this session.
     *
     * @param listener the listener to add
     * @return the listener that was added
     */
    WhatsAppClientListener addListener(WhatsAppClientListener listener);

    /**
     * Removes an event listener from this session.
     *
     * @param listener the listener to remove
     * @return {@code true} if the listener was removed
     */
    boolean removeListener(WhatsAppClientListener listener);

    /**
     * Returns all registered event listeners.
     *
     * @return an unmodifiable collection of listeners
     */
    Collection<WhatsAppClientListener> listeners();

    /**
     * Returns the configured proxy.
     *
     * @return an {@code Optional} containing the proxy, or empty if not configured
     */
    Optional<WhatsAppProxy> proxy();

    /**
     * Sets the proxy for network connections.
     *
     * @param proxy the proxy, may be {@code null} to disable proxy
     * @return this store instance for method chaining
     */
    WhatsAppStore setProxy(WhatsAppProxy proxy);

    /**
     * Blocks until a media connection is available.
     *
     * @return the media connection, never {@code null}
     * @throws InterruptedException if the current thread is interrupted
     */
    MediaConnection awaitMediaConnection() throws InterruptedException;

    /**
     * Sets the media connection.
     *
     * @param mediaConnection the media connection, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setMediaConnection(MediaConnection mediaConnection);

    /**
     * Returns the current offline resume state.
     *
     * <p>The state is driven by the offline-resume info bulletins
     * dispatched in {@code InfoBulletinStreamHandler}: the
     * {@code offline_preview} IB advances {@link WhatsAppClientOfflineResumeState#INIT}
     * to {@link WhatsAppClientOfflineResumeState#RESUME_ON_RESTART} (cold
     * start) or any past-restart state to
     * {@link WhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB} (live
     * disconnect), and the {@code offline} IB closes the resume out by
     * transitioning to {@link WhatsAppClientOfflineResumeState#COMPLETE}.
     *
     * @return the current state, never {@code null}
     */
    WhatsAppClientOfflineResumeState offlineResumeState();

    /**
     * Sets the offline resume state, transitioning the latch that gates
     * {@link #waitForOfflineDeliveryEnd()}.
     *
     * <p>This setter is the only authoritative writer of the resume
     * state. Setting {@link WhatsAppClientOfflineResumeState#COMPLETE}
     * counts the latch down so that any thread blocked in
     * {@link #waitForOfflineDeliveryEnd()} unblocks; setting
     * {@link WhatsAppClientOfflineResumeState#INIT} re-creates the latch
     * so a subsequent reconnect can re-block waiters.
     *
     * @param state the new state, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setOfflineResumeState(WhatsAppClientOfflineResumeState state);

    /**
     * Checks if the offline resume from restart is complete.
     *
     * <p>Mirrors WA Web's {@code isResumeFromRestartComplete} on both the
     * blocking and non-blocking offline resume managers: returns
     * {@code true} as long as the state is neither
     * {@link WhatsAppClientOfflineResumeState#INIT} nor
     * {@link WhatsAppClientOfflineResumeState#RESUME_ON_RESTART}, i.e.
     * the cold-start backlog has finished delivering. Live-tab
     * reconnects ({@link WhatsAppClientOfflineResumeState#RESUME_WITH_OPEN_TAB})
     * therefore still report {@code true} so that subsystems already
     * past the cold-start replay continue running real-time logic.
     *
     * @return {@code true} if offline resume is complete
     */
    boolean isResumeFromRestartComplete();

    /**
     * Blocks until offline delivery is complete, or the timeout expires.
     *
     * <p>Returns immediately when the state is already
     * {@link WhatsAppClientOfflineResumeState#COMPLETE}; otherwise waits
     * up to five minutes for {@link #setOfflineResumeState(WhatsAppClientOfflineResumeState)}
     * to be called with {@code COMPLETE}. The latch is one-shot per
     * connection and is recycled when the state is reset to
     * {@link WhatsAppClientOfflineResumeState#INIT} on reconnect.
     */
    void waitForOfflineDeliveryEnd();

    /**
     * Returns whether link previews are disabled.
     *
     * @return {@code true} if link previews are disabled
     */
    boolean disableLinkPreviews();

    /**
     * Sets whether link previews are disabled.
     *
     * @param disableLinkPreviews {@code true} to disable link previews
     * @return this store instance for method chaining
     */
    WhatsAppStore setDisableLinkPreviews(boolean disableLinkPreviews);

    /**
     * Returns whether all VoIP calls are relayed through WhatsApp servers
     * to hide the user's IP address.
     *
     * @return {@code true} if relay is enabled
     */
    boolean relayAllCalls();

    /**
     * Sets whether all VoIP calls should be relayed.
     *
     * @param relayAllCalls {@code true} to relay all calls
     * @return this store instance for method chaining
     */
    WhatsAppStore setRelayAllCalls(boolean relayAllCalls);

    /**
     * Returns whether the user has opted into the external web beta program.
     *
     * @return {@code true} if opted in
     */
    boolean externalWebBeta();

    /**
     * Sets whether the user has opted into the external web beta program.
     *
     * @param externalWebBeta {@code true} if opted in
     * @return this store instance for method chaining
     */
    WhatsAppStore setExternalWebBeta(boolean externalWebBeta);

    /**
     * Returns the chat lock settings (hide locked chats flag, secret code).
     *
     * @return an {@code Optional} containing the chat lock settings, or empty
     *         if not configured
     */
    Optional<ChatLockSettings> chatLockSettings();

    /**
     * Sets the chat lock settings.
     *
     * @param chatLockSettings the settings, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setChatLockSettings(ChatLockSettings chatLockSettings);

    /**
     * Returns the user's "Favourites" pinned chat list, in display order.
     *
     * <p>These are the chats the user has marked as favourites in the
     * Favourites section at the top of the Chats tab; the list ordering
     * is what the UI renders.
     *
     * @return an unmodifiable list of favourite chat JIDs, never
     *         {@code null}
     */
    List<Jid> favoriteChats();

    /**
     * Replaces the Favourites pinned chat list.
     *
     * @param favoriteChats the list of JIDs, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setFavoriteChats(List<Jid> favoriteChats);

    /**
     * Returns the list of feature names the primary device has reported
     * supporting.
     *
     * <p>Distinct from the AB-props bundle: AB-props are server-driven
     * rollout flags, while these are protocol-level capabilities that the
     * primary phone advertises (for example "supports new disappearing-
     * messages timer values"). Companion devices use the list to decide
     * whether they may use a feature in this account.
     *
     * @return an unmodifiable list of feature names, never {@code null}
     */
    List<String> primaryFeatures();

    /**
     * Replaces the primary-device advertised feature list.
     *
     * @param primaryFeatures the list of feature names, must not be
     *                        {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrimaryFeatures(List<String> primaryFeatures);

    /**
     * Returns whether the primary device has authorised this companion
     * device to apply every category of app-state mutation (chat archives,
     * stars, mutes, settings, and so on). Some primary devices restrict
     * the kinds of mutations a companion may push; this flag is the
     * authoritative answer.
     *
     * @return {@code true} if the primary allows the full mutation set
     */
    boolean primaryAllowsAllMutations();

    /**
     * Sets whether the primary device authorises full mutation coverage.
     *
     * @param primaryAllowsAllMutations {@code true} to authorise all
     *                                  mutations
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrimaryAllowsAllMutations(boolean primaryAllowsAllMutations);

    /**
     * Returns the synced state of every WhatsApp Business "agent" — a
     * support-team member who handles incoming chats.
     *
     * @return an unmodifiable collection of agent states, never
     *         {@code null}
     */
    Collection<AgentState> agentStates();

    /**
     * Finds the agent state for the given agent identifier.
     *
     * @param agentId the agent identifier, may be {@code null}
     * @return an {@code Optional} containing the agent state if found
     */
    Optional<AgentState> findAgentState(String agentId);

    /**
     * Adds or replaces an agent state record.
     *
     * @param state the agent state to add or replace, must not be
     *              {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putAgentState(AgentState state);

    /**
     * Removes the agent state record with the given identifier.
     *
     * @param agentId the agent identifier, may be {@code null}
     * @return an {@code Optional} containing the removed agent state if it
     *         existed
     */
    Optional<AgentState> removeAgentState(String agentId);

    /**
     * Removes every agent state record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearAgentStates();

    /**
     * Returns the WhatsApp Business team-inbox assignment for every
     * customer chat. Each entry collapses the previously-separate
     * assigned-agent and open-tab maps into a single coherent record
     * keyed by chat JID.
     *
     * @return an unmodifiable collection of chat assignments, never
     *         {@code null}
     */
    Collection<ChatAssignment> chatAssignments();

    /**
     * Finds the team-inbox assignment for the given customer chat.
     *
     * @param chatJid the chat JID, may be {@code null}
     * @return an {@code Optional} containing the assignment if found
     */
    Optional<ChatAssignment> findChatAssignment(Jid chatJid);

    /**
     * Adds or replaces a chat assignment record.
     *
     * @param assignment the assignment to add or replace, must not be
     *                   {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putChatAssignment(ChatAssignment assignment);

    /**
     * Removes the team-inbox assignment for the given chat JID.
     *
     * @param chatJid the chat JID, may be {@code null}
     * @return an {@code Optional} containing the removed assignment if it
     *         existed
     */
    Optional<ChatAssignment> removeChatAssignment(Jid chatJid);

    /**
     * Removes every chat assignment record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearChatAssignments();

    /**
     * Returns the cached payment-instruction CPI string. The CPI (Customer
     * Payment Instruction) is an opaque token issued by the WhatsApp Pay
     * backend to identify the user's currently-selected payment
     * configuration.
     *
     * @return an {@code Optional} containing the CPI if known
     */
    Optional<String> paymentInstructionCpi();

    /**
     * Sets the cached payment-instruction CPI string.
     *
     * @param cpi the CPI, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPaymentInstructionCpi(String cpi);

    /**
     * Returns every custom WhatsApp Pay payment method the merchant has
     * configured (for example bank transfer instructions or third-party
     * payment links shown alongside the order summary).
     *
     * @return an unmodifiable list of payment methods, never {@code null}
     */
    List<CustomPaymentMethod> customPaymentMethods();

    /**
     * Replaces the list of custom WhatsApp Pay payment methods.
     *
     * @param methods the new list, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setCustomPaymentMethods(List<CustomPaymentMethod> methods);

    /**
     * Returns the merchant's selected WhatsApp Pay third-party payment
     * partner (the PSP that processes their payments), if one has been
     * chosen.
     *
     * @return an {@code Optional} containing the partner action if set
     */
    Optional<MerchantPaymentPartnerAction> merchantPaymentPartner();

    /**
     * Sets the merchant's WhatsApp Pay payment partner.
     *
     * @param partner the partner action, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setMerchantPaymentPartner(MerchantPaymentPartnerAction partner);

    /**
     * Returns whether the user has accepted the WhatsApp Pay
     * Terms-of-Service banner. WhatsApp Pay requires an explicit
     * acceptance before the first transaction in supported regions
     * (Brazil, India, Singapore).
     *
     * @return an {@code Optional} containing the acceptance action if the
     *         user has interacted with the banner
     */
    Optional<PaymentTosAction> paymentTos();

    /**
     * Sets the WhatsApp Pay Terms-of-Service acceptance state.
     *
     * @param tos the acceptance action, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPaymentTos(PaymentTosAction tos);

    /**
     * Returns every marketing-message template this WhatsApp Business
     * account has authored. Marketing messages are the pre-approved
     * promotional templates a business sends to opted-in customers via
     * campaigns.
     *
     * @return an unmodifiable collection of marketing messages, never
     *         {@code null}
     */
    Collection<MarketingMessage> marketingMessages();

    /**
     * Finds the marketing-message template with the given identifier.
     *
     * @param templateId the template identifier, may be {@code null}
     * @return an {@code Optional} containing the template if found
     */
    Optional<MarketingMessage> findMarketingMessage(String templateId);

    /**
     * Adds or replaces a marketing-message template.
     *
     * @param message the template to add or replace, must not be
     *                {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putMarketingMessage(MarketingMessage message);

    /**
     * Removes the marketing-message template with the given identifier.
     *
     * @param templateId the template identifier, may be {@code null}
     * @return an {@code Optional} containing the removed template if it
     *         existed
     */
    Optional<MarketingMessage> removeMarketingMessage(String templateId);

    /**
     * Removes every marketing-message template.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearMarketingMessages();

    /**
     * Returns the broadcast lifecycle status of every marketing-message
     * template (for example {@code SCHEDULED}, {@code SENT} or
     * {@code FAILED}).
     *
     * @return an unmodifiable collection of broadcast status entries,
     *         never {@code null}
     */
    Collection<MarketingMessageBroadcast> marketingMessageBroadcasts();

    /**
     * Finds the broadcast status entry for the given template identifier.
     *
     * @param templateId the template identifier, may be {@code null}
     * @return an {@code Optional} containing the entry if found
     */
    Optional<MarketingMessageBroadcast> findMarketingMessageBroadcast(String templateId);

    /**
     * Adds or replaces a marketing-message broadcast status entry.
     *
     * @param broadcast the entry to add or replace, must not be
     *                  {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putMarketingMessageBroadcast(MarketingMessageBroadcast broadcast);

    /**
     * Removes the broadcast status entry for the given template
     * identifier.
     *
     * @param templateId the template identifier, may be {@code null}
     * @return an {@code Optional} containing the removed entry if it
     *         existed
     */
    Optional<MarketingMessageBroadcast> removeMarketingMessageBroadcast(String templateId);

    /**
     * Removes every marketing-message broadcast status entry.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearMarketingMessageBroadcasts();

    /**
     * Returns every WhatsApp Business broadcast list owned by this account.
     * A broadcast list is a saved audience the business can re-target with
     * marketing messages (a lightweight one-to-many distribution channel,
     * distinct from regular group chats).
     *
     * @return an unmodifiable collection of broadcast lists, never
     *         {@code null}
     */
    Collection<BusinessBroadcastList> businessBroadcastLists();

    /**
     * Finds the broadcast list with the given identifier.
     *
     * @param id the broadcast list identifier, may be {@code null}
     * @return an {@code Optional} containing the broadcast list if found
     */
    Optional<BusinessBroadcastList> findBusinessBroadcastList(String id);

    /**
     * Adds or replaces a broadcast list record.
     *
     * @param list the broadcast list to add or replace, must not be
     *             {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessBroadcastList(BusinessBroadcastList list);

    /**
     * Removes the broadcast list with the given identifier.
     *
     * @param id the broadcast list identifier, may be {@code null}
     * @return an {@code Optional} containing the removed broadcast list if
     *         it existed
     */
    Optional<BusinessBroadcastList> removeBusinessBroadcastList(String id);

    /**
     * Removes every broadcast list record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessBroadcastLists();

    /**
     * Returns the JIDs of every stored business broadcast list. The stored
     * broadcast list identifiers are projected into JIDs on the broadcast
     * server so callers can address the broadcast targets directly.
     *
     * @return a snapshot collection of broadcast list JIDs in the insertion
     *         order of the underlying map, empty if no broadcast lists are
     *         known
     */
    SequencedCollection<Jid> broadcasts();

    /**
     * Returns every WhatsApp Business broadcast campaign known to this
     * account. A campaign is a scheduled send of a specific marketing
     * template to a specific broadcast list at a specific time.
     *
     * @return an unmodifiable collection of broadcast campaigns, never
     *         {@code null}
     */
    Collection<BusinessBroadcastCampaign> businessBroadcastCampaigns();

    /**
     * Finds the broadcast campaign with the given identifier.
     *
     * @param id the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the broadcast campaign if found
     */
    Optional<BusinessBroadcastCampaign> findBusinessBroadcastCampaign(String id);

    /**
     * Adds or replaces a broadcast campaign record.
     *
     * @param campaign the campaign to add or replace, must not be
     *                 {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessBroadcastCampaign(BusinessBroadcastCampaign campaign);

    /**
     * Removes the broadcast campaign with the given identifier.
     *
     * @param id the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the removed campaign if it
     *         existed
     */
    Optional<BusinessBroadcastCampaign> removeBusinessBroadcastCampaign(String id);

    /**
     * Removes every broadcast campaign record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessBroadcastCampaigns();

    /**
     * Returns the post-send analytics ("insights") for every WhatsApp
     * Business broadcast campaign — delivered, opened and replied counts
     * that drive the campaign report screen.
     *
     * @return an unmodifiable collection of broadcast insights, never
     *         {@code null}
     */
    Collection<BusinessBroadcastInsight> businessBroadcastInsights();

    /**
     * Finds the broadcast insights for the given campaign identifier.
     *
     * @param id the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the insights if found
     */
    Optional<BusinessBroadcastInsight> findBusinessBroadcastInsight(String id);

    /**
     * Adds or replaces a broadcast insights record.
     *
     * @param insight the insights to add or replace, must not be
     *                {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBusinessBroadcastInsight(BusinessBroadcastInsight insight);

    /**
     * Removes the broadcast insights for the given campaign identifier.
     *
     * @param id the campaign identifier, may be {@code null}
     * @return an {@code Optional} containing the removed insights if they
     *         existed
     */
    Optional<BusinessBroadcastInsight> removeBusinessBroadcastInsight(String id);

    /**
     * Removes every broadcast insights record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBusinessBroadcastInsights();

    /**
     * Returns the Notification Content Token salt issued by the server.
     * WhatsApp uses this salt to derive opaque per-notification tokens
     * that let the OS push-notification provider (APNs/FCM) deliver a
     * notice without learning the chat content. (Originally exposed under
     * the WA Web codename "NCT".)
     *
     * @return an {@code Optional} containing the salt if known
     */
    Optional<byte[]> notificationContentTokenSalt();

    /**
     * Sets the Notification Content Token salt.
     *
     * @param salt the salt, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setNotificationContentTokenSalt(byte[] salt);

    /**
     * Returns the dismissed state of every WhatsApp onboarding hint. These
     * are the one-shot tooltips and banners WhatsApp shows the first time
     * a user hits a new feature; an entry whose flag is {@code true} means
     * the hint has already been dismissed and should not appear again.
     * (Originally exposed under the WA Web codename "NUX", short for
     * "New User Experience".)
     *
     * @return an unmodifiable collection of onboarding-hint states, never
     *         {@code null}
     */
    Collection<OnboardingHintState> onboardingHintStates();

    /**
     * Finds the onboarding-hint state for the given hint identifier.
     *
     * @param hintId the hint identifier, may be {@code null}
     * @return an {@code Optional} containing the hint state if found
     */
    Optional<OnboardingHintState> findOnboardingHintState(String hintId);

    /**
     * Adds or replaces an onboarding-hint state record.
     *
     * @param state the hint state to add or replace, must not be
     *              {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putOnboardingHintState(OnboardingHintState state);

    /**
     * Removes the onboarding-hint state for the given identifier.
     *
     * @param hintId the hint identifier, may be {@code null}
     * @return an {@code Optional} containing the removed hint state if it
     *         existed
     */
    Optional<OnboardingHintState> removeOnboardingHintState(String hintId);

    /**
     * Removes every onboarding-hint state record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearOnboardingHintStates();

    /**
     * Returns the capability set advertised by the primary device — the
     * phone that owns this account. WhatsApp uses these flags to gate
     * features that the companion can only enable when the primary
     * supports them (for example LID-based messaging or new sync-action
     * categories).
     *
     * @return an {@code Optional} containing the capability set if it has
     *         been received
     */
    Optional<DeviceCapabilities> primaryDeviceCapabilities();

    /**
     * Sets the capability set advertised by the primary device.
     *
     * @param capabilities the capability set, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrimaryDeviceCapabilities(DeviceCapabilities capabilities);

    /**
     * Returns the capability set advertised by every other companion
     * device on the account. Mirrors {@link #primaryDeviceCapabilities()}
     * but for siblings (other linked devices visible under "Linked
     * devices"); each entry pairs the device JID with its capability bag.
     *
     * @return an unmodifiable collection of device capability entries,
     *         never {@code null}
     */
    Collection<DeviceCapabilitiesEntry> deviceCapabilitiesStates();

    /**
     * Finds the device-capability entry for the given device JID.
     *
     * @param deviceJid the device JID, may be {@code null}
     * @return an {@code Optional} containing the capability entry if found
     */
    Optional<DeviceCapabilitiesEntry> findDeviceCapabilitiesEntry(Jid deviceJid);

    /**
     * Adds or replaces a device-capability entry.
     *
     * @param entry the capability entry to add or replace, must not be
     *              {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putDeviceCapabilitiesEntry(DeviceCapabilitiesEntry entry);

    /**
     * Removes the device-capability entry for the given device JID.
     *
     * @param deviceJid the device JID, may be {@code null}
     * @return an {@code Optional} containing the removed capability entry
     *         if it existed
     */
    Optional<DeviceCapabilitiesEntry> removeDeviceCapabilitiesEntry(Jid deviceJid);

    /**
     * Removes every device-capability entry.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearDeviceCapabilitiesStates();

    /**
     * Returns the persisted state for every interactive-message thread —
     * the rich card menus, list pickers and replies-to-quick-reply buttons
     * the user has interacted with.
     *
     * @return an unmodifiable collection of interactive-message states,
     *         never {@code null}
     */
    Collection<InteractiveMessageState> interactiveMessageStates();

    /**
     * Finds the interactive-message state for the given message identifier.
     *
     * @param messageId the message identifier, may be {@code null}
     * @return an {@code Optional} containing the state if found
     */
    Optional<InteractiveMessageState> findInteractiveMessageState(String messageId);

    /**
     * Adds or replaces an interactive-message state record.
     *
     * @param state the state to add or replace, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putInteractiveMessageState(InteractiveMessageState state);

    /**
     * Removes the interactive-message state for the given message
     * identifier.
     *
     * @param messageId the message identifier, may be {@code null}
     * @return an {@code Optional} containing the removed state if it
     *         existed
     */
    Optional<InteractiveMessageState> removeInteractiveMessageState(String messageId);

    /**
     * Removes every interactive-message state record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearInteractiveMessageStates();

    /**
     * Returns the contents of every chat-attached note ("Add note to
     * customer" feature in WhatsApp Business).
     *
     * @return an unmodifiable collection of note states, never {@code null}
     */
    Collection<NoteState> noteStates();

    /**
     * Finds the note state with the given identifier.
     *
     * @param id the note identifier, may be {@code null}
     * @return an {@code Optional} containing the note state if found
     */
    Optional<NoteState> findNoteState(String id);

    /**
     * Adds or replaces a note state record.
     *
     * @param state the note state to add or replace, must not be
     *              {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putNoteState(NoteState state);

    /**
     * Removes the note state with the given identifier.
     *
     * @param id the note identifier, may be {@code null}
     * @return an {@code Optional} containing the removed note state if it
     *         existed
     */
    Optional<NoteState> removeNoteState(String id);

    /**
     * Removes every note state record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearNoteStates();

    /**
     * Returns the pin records for every WhatsApp Channel pinned at the
     * top of the Updates tab. Each entry pairs a Channel (newsletter) JID
     * with the instant at which the pin was applied.
     *
     * @return an unmodifiable collection of newsletter pins, never
     *         {@code null}
     */
    Collection<NewsletterPin> newsletterPinStates();

    /**
     * Finds the pin record for the given newsletter JID.
     *
     * @param newsletterJid the newsletter JID, may be {@code null}
     * @return an {@code Optional} containing the pin record if found
     */
    Optional<NewsletterPin> findNewsletterPin(Jid newsletterJid);

    /**
     * Adds or replaces a newsletter pin record.
     *
     * @param pin the pin to add or replace, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putNewsletterPin(NewsletterPin pin);

    /**
     * Removes the pin record for the given newsletter JID.
     *
     * @param newsletterJid the newsletter JID, may be {@code null}
     * @return an {@code Optional} containing the removed pin record if it
     *         existed
     */
    Optional<NewsletterPin> removeNewsletterPin(Jid newsletterJid);

    /**
     * Removes every newsletter pin record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearNewsletterPins();

    /**
     * Returns whether this account has set a profile avatar (the cartoon
     * avatar exposed via WhatsApp's avatar feature). Distinct from the
     * regular profile picture.
     *
     * @return an {@code Optional} containing {@code true}/{@code false} if
     *         the server has communicated the flag
     */
    Optional<Boolean> hasAvatar();

    /**
     * Sets whether this account has a profile avatar.
     *
     * @param hasAvatar the avatar flag, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setHasAvatar(Boolean hasAvatar);

    /**
     * Returns the call-log state for every recent VoIP call known to this
     * companion (used to reconstruct the Calls tab on Web/Desktop).
     *
     * @return an unmodifiable collection of call-log entries, never
     *         {@code null}
     */
    Collection<CallLog> callLogStates();

    /**
     * Finds the call-log entry for the given call identifier.
     *
     * @param callId the call identifier, may be {@code null}
     * @return an {@code Optional} containing the call log if found
     */
    Optional<CallLog> findCallLog(String callId);

    /**
     * Adds or replaces a call-log entry.
     *
     * @param callLog the call log to add or replace, must not be
     *                {@code null} and must carry a non-empty call id
     * @return this store instance for method chaining
     */
    WhatsAppStore addCallLog(CallLog callLog);

    /**
     * Removes the call-log entry for the given call identifier.
     *
     * @param callId the call identifier, may be {@code null}
     * @return an {@code Optional} containing the removed call log if it
     *         existed
     */
    Optional<CallLog> removeCallLog(String callId);

    /**
     * Removes every call-log entry.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearCallLogs();

    /**
     * Returns whether the welcome message has been requested for each AI
     * bot conversation. WhatsApp shows a one-shot welcome blurb the first
     * time the user opens a chat with an AI bot; an entry whose
     * {@code requested} flag is {@code true} means the request has already
     * been made.
     *
     * @return an unmodifiable collection of bot welcome-request states,
     *         never {@code null}
     */
    Collection<BotWelcomeRequestState> botWelcomeRequestStates();

    /**
     * Finds the bot welcome-request state for the given bot JID.
     *
     * @param botJid the bot JID, may be {@code null}
     * @return an {@code Optional} containing the welcome-request state if
     *         found
     */
    Optional<BotWelcomeRequestState> findBotWelcomeRequestState(Jid botJid);

    /**
     * Adds or replaces a bot welcome-request state record.
     *
     * @param state the welcome-request state to add or replace, must not
     *              be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putBotWelcomeRequestState(BotWelcomeRequestState state);

    /**
     * Removes the bot welcome-request state for the given bot JID.
     *
     * @param botJid the bot JID, may be {@code null}
     * @return an {@code Optional} containing the removed state if it
     *         existed
     */
    Optional<BotWelcomeRequestState> removeBotWelcomeRequestState(Jid botJid);

    /**
     * Removes every bot welcome-request state record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearBotWelcomeRequestStates();

    /**
     * Returns the user-set titles for each Meta AI thread. The Meta AI
     * assistant lets users keep multiple labelled conversation threads,
     * equivalent to ChatGPT-style chat tabs.
     *
     * @return an unmodifiable collection of AI thread titles, never
     *         {@code null}
     */
    Collection<AiThreadTitle> aiThreadTitles();

    /**
     * Finds the AI thread title with the given thread identifier.
     *
     * @param threadId the thread identifier, may be {@code null}
     * @return an {@code Optional} containing the title record if found
     */
    Optional<AiThreadTitle> findAiThreadTitle(String threadId);

    /**
     * Adds or replaces an AI thread title record.
     *
     * @param title the title record to add or replace, must not be
     *              {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore putAiThreadTitle(AiThreadTitle title);

    /**
     * Removes the AI thread title with the given thread identifier.
     *
     * @param threadId the thread identifier, may be {@code null}
     * @return an {@code Optional} containing the removed title record if
     *         it existed
     */
    Optional<AiThreadTitle> removeAiThreadTitle(String threadId);

    /**
     * Removes every AI thread title record.
     *
     * @return this store instance for method chaining
     */
    WhatsAppStore clearAiThreadTitles();

    /**
     * Returns the identifiers of every persisted WAM buffer awaiting a
     * retry on the next session.
     *
     * <p>A persisted buffer is a file written by
     * {@link com.github.auties00.cobalt.wam.WamService} just before an
     * upload attempt; if the process exits before the upload succeeds
     * (or before the retry budget is exhausted) the file remains and is
     * picked up here on the next start.
     *
     * @return an unmodifiable collection of save keys, never
     *         {@code null}
     */
    Collection<String> wamPendingBufferKeys();

    /**
     * Opens an output stream that streams a new persisted WAM buffer to
     * disk under the given save key.
     *
     * <p>Implementations must commit the file atomically when the
     * stream is {@linkplain OutputStream#close() closed}. A file with
     * the same save key already on disk is overwritten.
     *
     * @param saveKey the unique identifier for the buffer, must not be
     *                {@code null} or contain path separators
     * @return a new output stream; the caller owns it and must close it
     * @throws IOException if the underlying directory cannot be created
     */
    OutputStream openWamPendingBufferWriter(String saveKey) throws IOException;

    /**
     * Opens an input stream over a previously-persisted WAM buffer.
     *
     * @param saveKey the buffer identifier, must not be {@code null}
     * @return an {@code Optional} containing a fresh input stream, or
     *         empty if no file exists for {@code saveKey}
     * @throws IOException if the file exists but cannot be opened
     */
    Optional<InputStream> openWamPendingBufferReader(String saveKey) throws IOException;

    /**
     * Removes a single persisted WAM buffer, typically after a
     * successful upload.
     *
     * @param saveKey the buffer identifier, must not be {@code null}
     * @return {@code true} if a file was deleted, {@code false} if no
     *         file existed
     * @throws IOException if the file cannot be deleted
     */
    boolean removeWamPendingBuffer(String saveKey) throws IOException;

    /**
     * Removes every persisted WAM buffer.
     *
     * @return this store instance for method chaining
     * @throws IOException if any file cannot be deleted
     */
    WhatsAppStore clearWamPendingBuffers() throws IOException;

    /**
     * Returns the persistent WAM sequence number for the given channel.
     *
     * <p>WAM buffers carry a per-stream uint16 sequence number in their
     * 8-byte header. Persisting it across sessions ensures the server
     * does not see a reset to {@code 1} after every restart, matching
     * WhatsApp Web's {@code WAWebWamStorage.getNextSequenceNumberForStream}
     * semantics.
     *
     * @param channel the transport channel, must not be {@code null}
     * @return an {@code OptionalInt} containing the persisted next
     *         sequence number, or empty if none has been recorded yet
     */
    OptionalInt findWamSequenceNumber(WamChannel channel);

    /**
     * Sets the persistent WAM sequence number for the given channel.
     *
     * @param channel        the transport channel, must not be {@code null}
     * @param sequenceNumber the next sequence number to use
     * @return this store instance for method chaining
     */
    WhatsAppStore putWamSequenceNumber(WamChannel channel, int sequenceNumber);

    /**
     * Returns the user's preferred default for starting a chat with a
     * username (rather than a phone number) when WhatsApp's username
     * feature is enabled. Controls whether the "New chat" entry point
     * defaults to the username flow.
     *
     * @return an {@code Optional} containing the chat-start mode if it has
     *         been set
     */
    Optional<UsernameChatStartModeAction.ChatStartMode> usernameChatStartMode();

    /**
     * Sets the username-based chat-start preference.
     *
     * @param mode the chat-start mode, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode mode);

    /**
     * Returns the user's per-account "Notify me about" setting (the
     * coarse switch under Notifications that selects which activity
     * categories — messages, statuses, channels — should produce push
     * notifications).
     *
     * @return an {@code Optional} containing the setting if it has been
     *         configured
     */
    Optional<NotificationActivitySettingAction.NotificationActivitySetting> notificationActivitySetting();

    /**
     * Sets the per-account notification-activity preference.
     *
     * @param setting the setting, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setNotificationActivitySetting(NotificationActivitySettingAction.NotificationActivitySetting setting);

    /**
     * Returns the per-emoji weight table that drives the "Recent emojis"
     * row in the emoji and reaction picker. WhatsApp ranks recents by
     * decayed usage frequency rather than chronological order.
     *
     * @return an unmodifiable list of weight entries, never {@code null}
     */
    List<RecentEmojiWeight> recentEmojiWeights();

    /**
     * Replaces the recent-emoji weight list.
     *
     * @param weights the new weight list, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setRecentEmojiWeights(List<RecentEmojiWeight> weights);

    /**
     * Returns the user's identifier for the paid newsletter
     * (WhatsApp Channel) subscription subsystem.
     *
     * <p>This is the back-end behind paid newsletter subscriptions — the
     * feature that lets a newsletter admin gate posts behind a recurring
     * fee and lets followers buy access. The id here ties the account to
     * its subscription record (plan id, billing status, ToS acceptance,
     * link with the Meta Accounts Center record), so both consumer and
     * admin flows can resolve the right subscription profile. (The
     * underlying WA Web codename is "WAMO", short for "WhatsApp
     * Monetisation".)
     *
     * @return an {@code Optional} containing the user id if the server
     *         has issued one
     */
    Optional<String> newsletterSubscriptionUserIdentifier();

    /**
     * Sets the paid-newsletter-subscription user identifier.
     *
     * @param identifier the user id, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setNewsletterSubscriptionUserIdentifier(String identifier);

    /**
     * Returns the most recent {@code MusicUserIdAction} sync action received
     * for this account, if any. The value is stored verbatim for round-trip
     * fidelity but is not currently consumed by any read path.
     *
     * @return an {@code Optional} containing the action if one has been received
     */
    Optional<MusicUserIdAction> musicUserIdState();

    /**
     * Sets the most recent {@code MusicUserIdAction} sync action.
     *
     * @param action the action to store, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setMusicUserIdState(MusicUserIdAction action);

    /**
     * Returns the user's saved interests for WhatsApp Channel
     * recommendations — the categories the user picked in the
     * "Find channels you'll love" onboarding screen, encoded as the
     * server-supplied opaque string.
     *
     * @return an {@code Optional} containing the saved interests if any
     *         have been recorded
     */
    Optional<String> newsletterSavedInterests();

    /**
     * Sets the user's saved Channel interests string.
     *
     * @param interests the saved interests, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setNewsletterSavedInterests(String interests);

    /**
     * Returns whether the user has opted into "Friend posted a status"
     * notifications. WhatsApp's Status feature is the 24-hour disappearing
     * post tab; users can opt to be alerted whenever specific contacts
     * post.
     *
     * @return an {@code Optional} containing the opt-in flag if it has
     *         been configured
     */
    Optional<Boolean> statusPostOptInNotificationPreferencesEnabled();

    /**
     * Sets the Status post opt-in notification preference.
     *
     * @param enabled the opt-in flag, may be {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setStatusPostOptInNotificationPreferencesEnabled(Boolean enabled);

    /**
     * Returns the user's "Advanced chat privacy" / Private Processing
     * preference. Private Processing routes Meta-AI prompts through a
     * confidential-compute attestation path so neither WhatsApp nor Meta
     * can read the contents; this status records whether the user has
     * enabled or disabled that mode.
     *
     * @return an {@code Optional} containing the private-processing
     *         status if set
     */
    Optional<PrivateProcessingSettingAction.PrivateProcessingStatus> privateProcessingStatus();

    /**
     * Sets the Private Processing preference.
     *
     * @param status the private-processing status, may be {@code null} to
     *               clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrivateProcessingStatus(PrivateProcessingSettingAction.PrivateProcessingStatus status);

    /**
     * Returns the user's opt-out preference for personalised channel
     * recommendations, if it has been set by a sync action. The value is
     * stored verbatim for round-trip fidelity but is not currently consumed
     * by any read path.
     *
     * @return an {@code Optional} containing the opt-out flag if it has been set
     */
    Optional<Boolean> channelsPersonalisedRecommendationOptOut();

    /**
     * Sets the user's opt-out preference for personalised channel
     * recommendations.
     *
     * @param optOut the opt-out flag, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setChannelsPersonalisedRecommendationOptOut(Boolean optOut);

    /**
     * Returns the binary definition (encoded protobuf) of the custom AI
     * bot this account has authored, if any. WhatsApp's AI Studio lets
     * users create custom AI personas; the definition blob holds the
     * persona prompt, name and avatar. (The underlying WA Web codename
     * for these bots is "UGC", short for "user-generated content".)
     *
     * @return an {@code Optional} containing the encoded bot definition
     */
    Optional<byte[]> userCreatedBotDefinition();

    /**
     * Sets the user-created AI bot definition.
     *
     * @param definition the encoded bot definition, may be {@code null}
     *                   to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setUserCreatedBotDefinition(byte[] definition);

    /**
     * Returns the on/off status of the AI Business Agent for this
     * account.
     *
     * <p>The AI Business Agent (referred to internally by the WA Web
     * codename "Maiba" / "Maiba AI Hub") is the platform a WhatsApp
     * Business owner uses to put an AI assistant in front of their
     * customer chats: the owner uploads knowledge sources (websites,
     * files, past chat history), configures auto-reply behaviour and
     * lead-generation flows, and the AI then answers customers on their
     * behalf via a Cloud-API thread takeover. This status records
     * whether the AI agent is currently enabled, disabled or pending
     * server confirmation for this account; it is updated by the
     * {@code maiba_ai_features_control} sync action. The value is stored
     * verbatim for round-trip fidelity but is not currently consumed by
     * any read path.
     *
     * @return an {@code Optional} containing the status if one has been
     *         received
     */
    Optional<MaibaAIFeaturesControlAction.MaibaAIFeatureStatus> aiBusinessAgentStatus();

    /**
     * Sets the AI Business Agent on/off status.
     *
     * @param status the status to store, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus status);

    /**
     * Returns the timestamp at which this companion device was paired with
     * the primary device. Used to filter events that predate pairing such as
     * avatar updates and call log entries that arrive during initial history
     * sync but actually originated before this device joined the account.
     *
     * @return an {@code Optional} containing the pairing timestamp if known
     */
    Optional<Instant> pairingTimestamp();

    /**
     * Sets the timestamp at which this companion device was paired with the
     * primary device.
     *
     * @param pairingTimestamp the pairing timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPairingTimestamp(Instant pairingTimestamp);

    /**
     * Returns the clock skew between the server and the local clock, in
     * seconds. Computed as the difference between the server timestamp
     * carried by the {@code <success>} stanza's {@code t} attribute and the
     * local epoch seconds, then persisted so every timestamp-aware module
     * can rebase comparisons against server time.
     *
     * @return the stored clock skew in seconds, or {@code 0} when never set
     */
    long clockSkewSeconds();

    /**
     * Sets the clock skew between server and local time, in seconds.
     *
     * @param clockSkewSeconds the difference in seconds between the server
     *                         timestamp and the local clock, positive when
     *                         the server is ahead
     * @return this store instance for method chaining
     */
    WhatsAppStore setClockSkewSeconds(long clockSkewSeconds);

    /**
     * Returns the timestamp of the last group AB-props emergency push
     * signalled by the server through the {@code <success>} stanza's
     * {@code group_abprops} attribute. Used on subsequent syncs to decide
     * whether the delta push has already been applied.
     *
     * @return an {@link Optional} containing the last emergency push
     *         timestamp, or empty when none has been recorded
     */
    Optional<Instant> groupAbPropsEmergencyPushTimestamp();

    /**
     * Sets the timestamp of the last group AB-props emergency push.
     *
     * @param timestamp the emergency push timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setGroupAbPropsEmergencyPushTimestamp(Instant timestamp);

    /**
     * Returns the AB-props {@code abKey} string most recently received
     * from the server.
     *
     * <p>"AB-props" is WhatsApp's experiment / feature-flag bundle: a
     * server-controlled blob of key/value pairs that gates rollouts and
     * tunes per-user limits. The bundle is delta-synced and the
     * {@code abKey} identifies the variant assignment for this account.
     *
     * @return an {@link Optional} containing the AB key, or empty when
     *         none has been received yet
     */
    Optional<String> abPropsAbKey();

    /**
     * Sets the AB-props {@code abKey} string.
     *
     * @param abKey the AB key, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsAbKey(String abKey);

    /**
     * Returns the AB-props {@code hash} string used for delta-update
     * negotiation with the server.
     *
     * @return an {@link Optional} containing the AB-props hash, or empty
     *         when no sync has completed
     */
    Optional<String> abPropsHash();

    /**
     * Sets the AB-props {@code hash} string.
     *
     * @param hash the AB-props hash, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsHash(String hash);

    /**
     * Returns the AB-props refresh interval, in seconds
     *
     * @return a {@code Optional} containing the AB-props refresh interval in seconds if it was set, otherwise an empty {@code Optional}
     */
    OptionalLong abPropsRefresh();

    /**
     * Sets the AB-props refresh interval, in seconds.
     *
     * @param refreshSeconds the desired refresh interval in seconds
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsRefresh(long refreshSeconds);

    /**
     * Returns the timestamp of the most recent successful AB-props sync.
     *
     * @return an {@link Optional} containing the last sync timestamp, or
     *         empty when no sync has been recorded
     */
    Optional<Instant> abPropsLastSyncTime();

    /**
     * Sets the timestamp of the most recent successful AB-props sync.
     *
     * @param lastSyncTime the sync completion instant, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsLastSyncTime(Instant lastSyncTime);

    /**
     * Returns the AB-props refresh id received from the server on the most
     * recent sync.
     *
     * @return the AB-props refresh id, or {@code 0} when never set
     */
    long abPropsRefreshId();

    /**
     * Sets the AB-props refresh id.
     *
     * @param refreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsRefreshId(long refreshId);

    /**
     * Returns the web-only AB-props refresh id used to gate the emergency
     * push request.
     *
     * @return the web AB-props refresh id, or {@code 0} when never set
     */
    long abPropsWebRefreshId();

    /**
     * Sets the web-only AB-props refresh id.
     *
     * @param webRefreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsWebRefreshId(long webRefreshId);

    /**
     * Returns the group AB-props refresh id received from the server on the
     * most recent sync.
     *
     * @return the group AB-props refresh id, or {@code 0} when never set
     */
    long groupAbPropsRefreshId();

    /**
     * Sets the group AB-props refresh id.
     *
     * @param groupRefreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setGroupAbPropsRefreshId(long groupRefreshId);

    /**
     * Removes every recent sticker flagged as an avatar sticker and returns
     * the number of entries removed.
     *
     * @return the number of recent avatar stickers that were removed
     */
    int removeAllRecentAvatarStickers();

    /**
     * Returns the mention-everyone mute expiration for a chat. Stored
     * separately from the regular mute state and controls whether
     * mention-everyone notifications are suppressed for the chat.
     *
     * @param chatJid the chat JID to look up
     * @return an {@code Optional} containing the mute state if set
     */
    Optional<ChatMute> mentionEveryoneMuteExpiration(Jid chatJid);

    /**
     * Sets the mention-everyone mute expiration for a chat.
     *
     * @param chatJid the chat JID
     * @param mute    the mute state to set
     */
    void setMentionEveryoneMuteExpiration(Jid chatJid, ChatMute mute);

    /**
     * Converts this object to a six-parts format representation
     *
     * @return an {@code Optional} containing the six-parts keys if a phone number was set, otherwise an empty {@code Optional}
     */
    default Optional<WhatsAppClientSixPartsKeys> toSixPartsKeys() {
        var phoneNumber = phoneNumber();
        if(phoneNumber.isEmpty()) {
            return Optional.empty();
        }

        var noiseKeyPair = noiseKeyPair();
        var identityKeyPair = identityKeyPair();
        var identityId = identityId();
        var result = new WhatsAppClientSixPartsKeys(phoneNumber.getAsLong(), noiseKeyPair, identityKeyPair, identityId);
        return Optional.of(result);
    }
}
