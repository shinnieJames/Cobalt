
package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.OutContact;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotification;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction;
import com.github.auties00.cobalt.model.sync.action.media.NoteEditAction;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastCampaignAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastInsightsAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.business.MarketingMessageAction;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.action.device.AgentAction;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.model.sync.action.media.MusicUserIdAction;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethod;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.SignalProtocolStore;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.key.SignalSignedKeyPair;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * A interface representing the persistent and transient state of a
 * WhatsApp client session.
 *
 * <p>This interface unifies data access and persistence into a single
 * abstraction. Implementations control how and where session data is stored,
 * ranging from fully in-memory with protobuf file persistence to tiered
 * approaches that trade memory for lazy decoding.
 *
 * <p>Instances are obtained exclusively through the static factory methods
 * on this interface. All implementations are transparent to callers.
 *
 * @see SignalProtocolStore
 *
 * @implNote This interface is the public counterpart of
 * {@code AbstractWhatsAppStore} and re-exposes most of the WA Web
 * store surface: {@code WAWebModelStorageInitialize},
 * {@code WAWebCollections}, {@code WAWebSignalStorage},
 * {@code WAWebUserPrefsBase}. Each field/accessor pair on this interface
 * carries its own {@code @implNote} pointing at the originating WA Web
 * schema, collection or prefs module.
 */
@WhatsAppWebModule(moduleName = "WAWebModelStorageInitialize")
@WhatsAppWebModule(moduleName = "WAWebCollections")
@WhatsAppWebModule(moduleName = "WAWebSignalStorage")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsBase")
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
     * Permanently deletes this session from storage.
     *
     * <p>After this method returns, the session data cannot be recovered.
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
     * Returns the Signal protocol registration ID.
     *
     * @return the registration ID, a value between 1 and 16380
     */
    int registrationId();

    /**
     * Returns the Noise protocol key pair for secure channel establishment.
     *
     * @return the noise key pair, never {@code null}
     */
    SignalIdentityKeyPair noiseKeyPair();

    /**
     * Returns the Signal protocol identity key pair for end-to-end encryption.
     *
     * @return the identity key pair, never {@code null}
     */
    SignalIdentityKeyPair identityKeyPair();

    /**
     * Returns the currently active signed pre-key pair.
     *
     * @return the signed key pair, never {@code null}
     */
    SignalSignedKeyPair signedKeyPair();

    /**
     * Returns the FDID used during mobile registration.
     *
     * @return the FDID, never {@code null}
     */
    UUID fdid();

    /**
     * Returns the unique device identifier for mobile clients.
     *
     * @return the device ID bytes, never {@code null}
     */
    byte[] deviceId();

    /**
     * Returns the advertising identifier for mobile analytics.
     *
     * @return the advertising ID, never {@code null}
     */
    UUID advertisingId();

    /**
     * Returns the unique identity identifier for this installation.
     *
     * @return the identity ID bytes, never {@code null}
     */
    byte[] identityId();

    /**
     * Returns the backup/recovery token for mobile clients.
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
     * Returns the personal status message (about text) displayed on profile.
     *
     * @return an {@code Optional} containing the about text, or empty if not set
     */
    Optional<String> about();

    /**
     * Sets the about text.
     *
     * @param about the about text, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbout(String about);

    /**
     * Returns the WhatsApp JID uniquely identifying this user.
     *
     * @return an {@code Optional} containing the JID, or empty before
     *         authentication completes
     */
    Optional<Jid> jid();

    /**
     * Sets the WhatsApp JID.
     *
     * @param jid the JID, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setJid(Jid jid);

    /**
     * Returns the LID used when real phone number is not advertised.
     *
     * @return an {@code Optional} containing the LID, or empty if not set
     */
    Optional<Jid> lid();

    /**
     * Sets the LID.
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
     * Returns the business website URL.
     *
     * @return an {@code Optional} containing the website URL, or empty for
     *         non-business accounts
     */
    Optional<String> businessWebsite();

    /**
     * Sets the business website.
     *
     * @param businessWebsite the website URL, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessWebsite(String businessWebsite);

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
     * Returns the business category classification.
     *
     * @return an {@code Optional} containing the category, or empty for
     *         non-business accounts
     */
    Optional<BusinessCategory> businessCategory();

    /**
     * Sets the business category.
     *
     * @param businessCategory the category, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setBusinessCategory(BusinessCategory businessCategory);

    /**
     * Returns whether archived chats automatically unarchive on new messages.
     *
     * @return {@code true} if chats unarchive automatically
     */
    boolean unarchiveChats();

    /**
     * Sets whether chats unarchive automatically.
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
     * Returns the history sync policy for Web clients.
     *
     * @return an {@code Optional} containing the history policy, or empty
     *         if not configured
     */
    Optional<WhatsAppWebClientHistory> webHistoryPolicy();

    /**
     * Sets the history sync policy.
     *
     * @param policy the history policy, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setWebHistoryPolicy(WhatsAppWebClientHistory policy);

    /**
     * Returns whether automatic presence updates are enabled.
     *
     * @return {@code true} if enabled
     */
    boolean automaticPresenceUpdates();

    /**
     * Sets whether to send automatic presence updates.
     *
     * @param enabled {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setAutomaticPresenceUpdates(boolean enabled);

    /**
     * Returns whether automatic message receipts are enabled.
     *
     * @return {@code true} if enabled
     */
    boolean automaticMessageReceipts();

    /**
     * Sets whether to send automatic message receipts.
     *
     * @param enabled {@code true} to enable
     * @return this store instance for method chaining
     */
    WhatsAppStore setAutomaticMessageReceipts(boolean enabled);

    /**
     * Returns whether patch MAC verification is enabled.
     *
     * @return {@code true} if enabled
     */
    boolean checkPatchMacs();

    /**
     * Sets whether to verify patch MACs.
     *
     * @param enabled {@code true} to enable verification
     * @return this store instance for method chaining
     */
    WhatsAppStore setCheckPatchMacs(boolean enabled);

    /**
     * Returns whether the primary device supports syncd snapshot recovery.
     *
     * <p>Per WhatsApp Web {@code WAPrimaryDeviceSupportsSyncdRecovery}:
     * this flag is set when the primary device indicates support for
     * snapshot recovery via peer data operations.
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
     * Returns whether web app state has been synchronized.
     *
     * @return {@code true} if synchronized
     */
    boolean syncedWebAppState();

    /**
     * Sets whether web app state has been synchronized.
     *
     * @param synced {@code true} if synchronized
     * @return this store instance for method chaining
     */
    WhatsAppStore setSyncedWebAppState(boolean synced);

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
     * Returns the signed device identity from the primary device.
     *
     * @return an {@code Optional} containing the identity, or empty if not set
     */
    Optional<ADVSignedDeviceIdentity> signedDeviceIdentity();

    /**
     * Sets the signed device identity.
     *
     * @param identity the signed device identity, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setSignedDeviceIdentity(ADVSignedDeviceIdentity identity);

    /**
     * Returns the ADV secret key used for HMAC verification during pairing.
     *
     * @return an {@code Optional} containing the 32-byte key, or empty if not set
     */
    Optional<byte[]> advSecretKey();

    /**
     * Sets the ADV secret key.
     *
     * @param key the ADV secret key (should be 32 bytes), or {@code null} to clear
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
     * Returns all contacts stored in this session.
     *
     * @return an unmodifiable collection of all contacts
     */
    Collection<Contact> contacts();

    Collection<ContactTextStatus> contactTextStatuses();

    /**
     * Finds a contact by either phone number JID or LID.
     *
     * @param jid the JID to search for (phone or LID)
     * @return an {@code Optional} containing the contact if found
     */
    Optional<Contact> findContactByJid(JidProvider jid);

    /**
     * Adds or updates a contact in the store.
     *
     * @param contact the contact to add or update, must not be {@code null}
     * @return the contact that was added
     */
    Contact addContact(Contact contact);

    Optional<ContactTextStatus> findContactTextStatus(JidProvider jid);

    void addContactTextStatus(Jid contactJid, ContactTextStatus status);

    Optional<ContactTextStatus> removeContactTextStatus(JidProvider jid);

    Optional<WaffleAccountLinkStateAction.AccountLinkState> waffleAccountLinkState();

    WhatsAppStore setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState state);

    Optional<Instant> waffleAccountLinkStateTimestamp();

    WhatsAppStore setWaffleAccountLinkStateTimestamp(Instant timestamp);

    boolean hostedAutomationOnboarded();

    WhatsAppStore setHostedAutomationOnboarded(boolean onboarded);

    Optional<OrphanPaymentNotification> findOrphanPaymentNotification(String messageId);

    void addOrphanPaymentNotification(OrphanPaymentNotification notification);

    Optional<OrphanPaymentNotification> removeOrphanPaymentNotification(String messageId);

    Optional<byte[]> routingInfo();

    WhatsAppStore setRoutingInfo(byte[] routingInfo);

    Optional<String> routingDomain();

    WhatsAppStore setRoutingDomain(String routingDomain);

    Optional<Instant> clientExpiration();

    WhatsAppStore setClientExpiration(Instant clientExpiration);

    Set<String> tosNoticeIds();

    WhatsAppStore setTosNoticeIds(Set<String> noticeIds);

    boolean aiAvailable();

    WhatsAppStore setAiAvailable(boolean aiAvailable);

    Optional<String> businessOptOutListHash();

    WhatsAppStore setBusinessOptOutListHash(String hash);

    Map<String, Boolean> businessFeatureFlags();

    WhatsAppStore setBusinessFeatureFlags(Map<String, Boolean> flags);

    Map<String, String> businessCampaignStatuses();

    WhatsAppStore setBusinessCampaignStatuses(Map<String, String> statuses);

    /**
     * Returns the per-customer data sharing preferences keyed by account LID
     * raw string.
     *
     * <p>Each entry indicates whether click-to-WhatsApp (CTWA) per-customer
     * data sharing is enabled for the business account identified by the LID.
     * The map is an unmodifiable snapshot view of the underlying live store.
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           the WA Web schema persists one row per {@code lidRawString}
     *           with the {@code dataSharing3pdEnabled} boolean
     * @return an unmodifiable map of account LID raw strings to enabled flags,
     *         never {@code null}
     */
    Map<String, Boolean> ctwaPerCustomerDataSharing();

    /**
     * Returns the CTWA per-customer data sharing preference for the given
     * account LID, if any.
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code get(lidRawString)} read on the underlying
     *           IndexedDB table
     * @param accountLid the account LID raw string identifying the customer,
     *                   may be {@code null}
     * @return an {@link Optional} containing the enabled flag if a preference
     *         is recorded for the given LID, otherwise {@link Optional#empty()}
     */
    Optional<Boolean> findCtwaDataSharing(String accountLid);

    /**
     * Stores or updates the CTWA per-customer data sharing preference for the
     * given account LID.
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code createOrReplace} write on the underlying
     *           IndexedDB table with the row {@code (lidRawString, dataSharing3pdEnabled)}
     * @param accountLid the account LID raw string identifying the customer,
     *                   must not be {@code null}
     * @param enabled    the enabled flag, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setCtwaDataSharing(String accountLid, Boolean enabled);

    /**
     * Removes the CTWA per-customer data sharing preference for the given
     * account LID.
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code remove(lidRawString)} delete on the
     *           underlying IndexedDB table
     * @param accountLid the account LID raw string identifying the customer,
     *                   may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore removeCtwaDataSharing(String accountLid);

    Map<String, String> businessSubscriptionStatuses();

    WhatsAppStore setBusinessSubscriptionStatuses(Map<String, String> statuses);

    Map<String, Long> businessSubscriptionExpirations();

    WhatsAppStore setBusinessSubscriptionExpirations(Map<String, Long> expirations);

    Map<String, Long> businessSubscriptionCreationTimes();

    WhatsAppStore setBusinessSubscriptionCreationTimes(Map<String, Long> creationTimes);

    Optional<String> businessAccountNonce();

    WhatsAppStore setBusinessAccountNonce(String nonce);

    boolean detectedOutcomesEnabled();

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
     * Returns all outgoing contacts stored in this session.
     *
     * <p>Outgoing contacts power the WhatsApp Web "invite by contact" feature
     * and are persisted independently from regular {@link Contact} records.
     * The returned map is keyed by phone-number JID and is read-only.
     *
     * @return an unmodifiable view of the outgoing contact store keyed by JID
     * @implNote WAWebDBOutContactDatabaseApi — replaces the {@code out-contact}
     *           IndexedDB table accessor exposed by the WA Web database API
     */
    Map<Jid, OutContact> outContacts();

    /**
     * Finds an outgoing contact by its phone-number JID.
     *
     * @param jid the JID of the outgoing contact to look up, may be {@code null}
     * @return an {@code Optional} containing the outgoing contact if it exists
     * @implNote WAWebDBOutContactDatabaseApi — mirrors the lookup performed by
     *           the {@code getOutContact} accessor on the WA Web database API
     */
    Optional<OutContact> findOutContact(Jid jid);

    /**
     * Adds or updates an outgoing contact in the store.
     *
     * <p>If an entry with the same {@linkplain OutContact#jid() JID} is already
     * present, its {@code fullName} and {@code firstName} fields are merged
     * from the supplied record. This mirrors WhatsApp Web's batched
     * {@code putOutContactBatch} upsert semantics, where the latest record for
     * a given JID overwrites the previous one.
     *
     * @param outContact the outgoing contact to add or merge, must not be
     *                   {@code null}
     * @return this store instance for method chaining
     * @implNote WAWebDBOutContactDatabaseApi.putOutContactBatch — bulk upsert
     *           into the {@code out-contact} table
     */
    WhatsAppStore addOutContact(OutContact outContact);

    /**
     * Removes an outgoing contact from the store by its phone-number JID.
     *
     * @param jid the JID of the outgoing contact to remove, may be {@code null}
     * @return this store instance for method chaining
     * @implNote WAWebDBOutContactDatabaseApi.removeOutContactBatch — bulk
     *           removal from the {@code out-contact} table
     */
    WhatsAppStore removeOutContact(Jid jid);

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
     * Returns all chats stored in this session.
     *
     * @return an unmodifiable collection of all chats
     */
    Collection<Chat> chats();

    /**
     * Finds a chat by its JID.
     *
     * @param jid the JID to search for, may be {@code null}
     * @return an {@code Optional} containing the chat if found
     */
    Optional<Chat> findChatByJid(JidProvider jid);

    /**
     * Adds a new empty chat for the given JID.
     *
     * @param chatJid the chat JID, must not be {@code null}
     * @return the newly created chat
     */
    Chat addNewChat(Jid chatJid);

    /**
     * Removes a chat from the store.
     *
     * @param chatJid the JID of the chat to remove, may be {@code null}
     * @return an {@code Optional} containing the removed chat if it existed
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
     * Records a reaction (or reaction withdrawal) that the current account
     * has just sent to the given target message.
     *
     * <p>An empty {@code emoji} records a withdrawal matching WA Web's
     * {@code WAWebReactionsBEUtils.REVOKED_REACTION_TEXT}. This is
     * invoked eagerly during
     * {@link com.github.auties00.cobalt.client.WhatsAppClient#addReaction}
     * so local views reflect the in-flight state without waiting for the
     * server ack.
     *
     * @param targetKey the key of the message being reacted to
     * @param emoji     the reaction emoji, empty string to remove
     * @throws NullPointerException if any argument is {@code null}
     *
     * @implNote WAWebReactionsCollection.addOrUpdateReaction: writes the
     *           sender's current reaction into the in-memory reactions
     *           collection so the UI reflects the change instantly.
     */
    void trackSentReaction(MessageKey targetKey, String emoji);

    /**
     * Returns the reaction emoji the current account is currently showing
     * on the given target message, if any.
     *
     * @param targetKey the key of the message whose reaction is queried
     * @return an {@link Optional} containing the emoji, or empty if the
     *         account has not reacted to this message
     *
     * @implNote WAWebReactionsCollection.getExistingSenderModelFromReactionDetails:
     *           looks up the sender's current reaction on a message.
     */
    Optional<String> findSentReaction(MessageKey targetKey);

    /**
     * Returns all status updates stored in this session.
     *
     * @return an unmodifiable collection of status updates
     */
    Collection<ChatMessageInfo> status();

    /**
     * Adds a status update to the store.
     *
     * @param messageInfo the status message, must not be {@code null}
     * @return the status message that was added
     */
    ChatMessageInfo addStatus(ChatMessageInfo messageInfo);

    /**
     * Removes a status update from the store.
     *
     * @param id the status message id to remove
     * @return an {@code Optional} containing the removed status if it existed
     */
    Optional<ChatMessageInfo> removeStatus(String id);

    /**
     * Returns all calls stored in this session.
     *
     * @return an unmodifiable collection of all calls
     */
    Collection<CallOffer> calls();

    /**
     * Finds a call by its ID.
     *
     * @param callId the call ID to search for
     * @return an {@code Optional} containing the call if found
     */
    Optional<CallOffer> findCallById(String callId);

    /**
     * Adds a call to the store.
     *
     * @param call the call to add, must not be {@code null}
     * @return the call that was added
     */
    CallOffer addCall(CallOffer call);

    /**
     * Removes a call from the store.
     *
     * @param id the call ID to remove
     * @return an {@code Optional} containing the removed call if it existed
     */
    Optional<CallOffer> removeCall(String id);

    /**
     * Returns all newsletters stored in this session.
     *
     * @return an unmodifiable collection of all newsletters
     */
    Collection<Newsletter> newsletters();

    /**
     * Finds a newsletter by its JID.
     *
     * @param jid the JID to search for, may be {@code null}
     * @return an {@code Optional} containing the newsletter if found
     */
    Optional<Newsletter> findNewsletterByJid(JidProvider jid);

    /**
     * Adds a new empty newsletter for the given JID.
     *
     * @param newsletterJid the newsletter JID, must not be {@code null}
     * @return the newly created newsletter
     */
    Newsletter addNewNewsletter(Jid newsletterJid);

    /**
     * Removes a newsletter from the store.
     *
     * @param newsletterJid the JID of the newsletter to remove, may be {@code null}
     * @return an {@code Optional} containing the removed newsletter if it existed
     */
    Optional<Newsletter> removeNewsletter(JidProvider newsletterJid);

    /**
     * Returns the privacy settings.
     *
     * @return an unmodifiable collection of privacy settings
     */
    Collection<PrivacySettingEntry> privacySettings();

    /**
     * Finds a privacy setting by its type.
     *
     * @param type the privacy setting type
     * @return an {@code Optional} containing the setting if found
     */
    Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type);

    /**
     * Adds or updates a privacy setting.
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
     * Finds a quick reply by its stable identifier.
     *
     * <p>The {@code id} parameter is the same value used as the second element
     * of the sync index ({@code indexParts[1]}) and as the primary key of
     * WhatsApp Web's {@code WAWebSchemaQuickReply} IndexedDB table.
     *
     * @param id the quick reply identifier
     * @return an {@code Optional} containing the quick reply if found
     * @implNote WAWebSchemaQuickReply.getQuickReplyTable().get(id)
     */
    Optional<QuickReply> findQuickReply(String id);

    /**
     * Adds or replaces a quick reply in the store.
     *
     * <p>The quick reply is keyed by its {@link QuickReply#id() id} field, so
     * mutations that change the {@code shortcut} while preserving the
     * {@code id} replace the existing entry rather than leaking duplicates.
     *
     * @param quickReply the quick reply, must not be {@code null} and must have a non-{@code null} id
     * @implNote WAWebSchemaQuickReply.getQuickReplyTable().createOrReplace
     */
    void addQuickReply(QuickReply quickReply);

    /**
     * Removes a quick reply from the store by its stable identifier.
     *
     * @param id the quick reply identifier
     * @return an {@code Optional} containing the removed quick reply if it existed
     * @implNote WAWebSchemaQuickReply.getQuickReplyTable().remove(id)
     */
    Optional<QuickReply> removeQuickReply(String id);

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
     * Returns all app state sync keys.
     *
     * @return an unmodifiable sequenced collection of sync keys
     */
    SequencedCollection<AppStateSyncKey> appStateKeys();

    /**
     * Finds an app state sync key by its key ID.
     *
     * @param id the key ID, must not be {@code null}
     * @return an {@code Optional} containing the key if found
     */
    Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id);

    /**
     * Adds multiple app state sync keys.
     *
     * @param keys the collection of keys to add, must not be {@code null}
     */
    void addWebAppStateKeys(Collection<AppStateSyncKey> keys);

    /**
     * Removes all app state sync keys whose timestamp is at or before the given instant.
     *
     * <p>Per WhatsApp Web {@code SyncKeyStore.expire}: marks keys as expired
     * by removing them from the store. This is called when receiving sentinel
     * mutations signaling key expiration.
     *
     * @param threshold the cutoff instant; keys with timestamps at or before
     *                  this value are removed
     */
    void expireAppStateKeys(Instant threshold);

    /**
     * Expires all app state sync keys whose derived epoch matches the provided epoch.
     *
     * @param epoch the sync key epoch to expire
     */
    void expireAppStateKeysByEpoch(int epoch);

    /**
     * Finds a hash state by patch type.
     *
     * @param patchType the patch type to query
     * @return an {@code Optional} containing the hash state if found
     */
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
    Optional<SyncActionEntry> findSyncActionEntry(SyncPatchType patchType, byte[] indexMac);

    /**
     * Finds a sync action entry by collection and plaintext action index.
     *
     * <p>Per WhatsApp Web {@code WAWebGetSyncAction.getSyncActionInTransaction}: the sync
     * action store supports lookup by plaintext index string, which is key-independent
     * unlike the index MAC. This is needed for REMOVE operations where the encrypting
     * key may differ from the key used for the original SET.
     *
     * @implNote WAWebGetSyncAction.getSyncActionInTransaction
     * @param patchType    the collection type
     * @param actionIndex  the plaintext action index string
     * @return an {@code Optional} containing the entry if found
     */
    Optional<SyncActionEntry> findSyncActionEntryByActionIndex(SyncPatchType patchType, String actionIndex);

    /**
     * Stores or updates a sync action entry for the specified collection.
     *
     * @param patchType the collection type
     * @param indexMac  the index MAC identifying the entry
     * @param entry     the entry to store
     */
    void putSyncActionEntry(SyncPatchType patchType, byte[] indexMac, SyncActionEntry entry);

    /**
     * Removes a sync action entry from the specified collection.
     *
     * @param patchType the collection type
     * @param indexMac  the index MAC identifying the entry to remove
     * @return an {@code Optional} containing the removed entry if it existed
     */
    Optional<SyncActionEntry> removeSyncActionEntry(SyncPatchType patchType, byte[] indexMac);

    /**
     * Clears all sync action entries for the specified collection.
     *
     * <p>Used when a full snapshot is received and the state must be rebuilt from scratch.
     *
     * @param patchType the collection type
     */
    void clearSyncActionEntries(SyncPatchType patchType);

    /**
     * Returns all sync action entries for the specified collection.
     *
     * @param patchType the collection type
     * @return an unmodifiable collection of entries, or an empty collection if none exist
     */
    Collection<SyncActionEntry> getSyncActionEntries(SyncPatchType patchType);

    /**
     * Returns all missing sync keys being tracked.
     *
     * @return an unmodifiable collection of missing sync keys
     */
    Collection<MissingDeviceSyncKey> missingSyncKeys();

    /**
     * Finds a missing sync key by its ID.
     *
     * @param keyId the key ID
     * @return an {@code Optional} containing the missing key entry if found
     */
    Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId);

    /**
     * Adds or updates a missing sync key entry.
     *
     * @param missingKey the missing key entry to add
     */
    void addMissingSyncKey(MissingDeviceSyncKey missingKey);

    /**
     * Removes a missing sync key entry.
     *
     * @param keyId the key ID to remove
     */
    void removeMissingSyncKey(byte[] keyId);

    /**
     * Stores a peer message for offline retry.
     *
     * @implNote WAWebApiPeerMessageStore.storePeerMessages
     * @param id the message ID
     * @param message the peer message to store
     */
    void addPeerMessage(String id, ChatMessageInfo message);

    /**
     * Removes a peer message after successful delivery.
     *
     * @implNote WAWebApiPeerMessageStore.deletePeerMessage
     * @param id the message ID to remove
     */
    void removePeerMessage(String id);

    /**
     * Gets metadata for a web app state collection.
     *
     * @param collectionName the collection name
     * @return the collection metadata
     */
    SyncCollectionMetadata findWebAppState(SyncPatchType collectionName);

    /**
     * Updates a collection's version and LT-Hash.
     *
     * @param collectionName the collection name
     * @param newVersion     the new version
     * @param newLtHash      the new LT-Hash
     */
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
     * Marks a web app state collection in MAC mismatch state.
     *
     * <p>Per WhatsApp Web: when a patch snapshot MAC doesn't match the locally
     * computed value, the collection enters this degraded state rather than
     * failing fatally. Processing continues but integrity may be compromised.
     *
     * @param collectionName the collection name
     */
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
     * Adds an orphan mutation for the specified collection.
     *
     * <p>Orphan mutations reference entities that do not yet exist locally.
     * They are persisted and retried when the referenced entities arrive.
     *
     * @param collectionName the collection name
     * @param mutation       the orphan mutation entry
     */
    void addOrphanMutation(SyncPatchType collectionName, OrphanMutationEntry mutation);

    /**
     * Returns all orphan mutations for the specified collection.
     *
     * @param collectionName the collection name
     * @return the list of orphan mutation entries, never {@code null}
     */
    List<OrphanMutationEntry> findOrphanMutations(SyncPatchType collectionName);

    /**
     * Returns orphan mutations matching the specified model identifier within a collection.
     *
     * <p>This enables targeted orphan lookups by entity (e.g. by chat JID) instead of
     * requiring a full scan of all orphan mutations for the collection.
     *
     * @param collectionName the collection name
     * @param modelId        the model identifier to match (e.g. a JID string)
     * @return the list of matching orphan mutation entries, never {@code null}
     */
    List<OrphanMutationEntry> findOrphanMutationsByModel(SyncPatchType collectionName, String modelId);

    /**
     * Removes all orphan mutations for the specified collection.
     *
     * @param collectionName the collection name
     */
    void removeOrphanMutations(SyncPatchType collectionName);

    /**
     * Removes specific orphan mutation entries from the specified collection.
     *
     * @param collectionName the collection name
     * @param entries        the entries to remove
     */
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
     * Adds a user JID to the coex hosted verification cache.
     *
     * @param userJid the user JID to add
     */
    void addToCoexHostedVerificationCache(Jid userJid);

    /**
     * Checks if a user JID is in the coex hosted verification cache.
     *
     * @param userJid the user JID to check
     * @return {@code true} if the user has been verified as hosted
     */
    boolean isInCoexHostedVerificationCache(Jid userJid);

    /**
     * Clears the coex hosted verification cache.
     */
    void clearCoexHostedVerificationCache();

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
    Optional<WhatsAppClientProxy> proxy();

    /**
     * Sets the proxy for network connections.
     *
     * @param proxy the proxy, may be {@code null} to disable proxy
     * @return this store instance for method chaining
     */
    WhatsAppStore setProxy(WhatsAppClientProxy proxy);

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
     * @return the current state, never {@code null}
     */
    WhatsAppClientOfflineResumeState offlineResumeState();

    /**
     * Sets the offline resume state.
     *
     * @param state the new state, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setOfflineResumeState(WhatsAppClientOfflineResumeState state);

    /**
     * Checks if the offline resume from restart is complete.
     *
     * @return {@code true} if offline resume is complete
     */
    boolean isResumeFromRestartComplete();

    /**
     * Blocks until offline delivery is complete, or the timeout expires.
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
     * Returns the ordered list of favorite chat JIDs.
     *
     * @return an unmodifiable list of favorite chat JIDs, never {@code null}
     */
    List<Jid> favoriteChats();

    /**
     * Sets the ordered list of favorite chat JIDs.
     *
     * @param favoriteChats the list of JIDs, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setFavoriteChats(List<Jid> favoriteChats);

    /**
     * Returns the primary device feature flags.
     *
     * @return an unmodifiable list of feature flag names, never {@code null}
     */
    List<String> primaryFeatures();

    /**
     * Sets the primary device feature flags.
     *
     * @param primaryFeatures the list of feature flag names, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setPrimaryFeatures(List<String> primaryFeatures);

    boolean primaryAllowsAllMutations();

    WhatsAppStore setPrimaryAllowsAllMutations(boolean primaryAllowsAllMutations);

    Map<String, AgentAction> agentStates();

    WhatsAppStore setAgentStates(Map<String, AgentAction> states);

    Map<String, String> chatAssignmentStates();

    WhatsAppStore setChatAssignmentStates(Map<String, String> states);

    Map<String, Boolean> chatAssignmentOpenedStates();

    WhatsAppStore setChatAssignmentOpenedStates(Map<String, Boolean> states);

    Optional<String> paymentInstructionCpi();

    WhatsAppStore setPaymentInstructionCpi(String cpi);

    List<CustomPaymentMethod> customPaymentMethods();

    WhatsAppStore setCustomPaymentMethods(List<CustomPaymentMethod> methods);

    Optional<MerchantPaymentPartnerAction> merchantPaymentPartner();

    WhatsAppStore setMerchantPaymentPartner(MerchantPaymentPartnerAction partner);

    Optional<PaymentTosAction> paymentTos();

    WhatsAppStore setPaymentTos(PaymentTosAction tos);

    Map<String, MarketingMessageAction> marketingMessages();

    WhatsAppStore setMarketingMessages(Map<String, MarketingMessageAction> messages);

    Map<String, String> marketingMessageBroadcasts();

    WhatsAppStore setMarketingMessageBroadcasts(Map<String, String> broadcasts);

    Map<String, BusinessBroadcastListAction> businessBroadcastLists();

    WhatsAppStore setBusinessBroadcastLists(Map<String, BusinessBroadcastListAction> lists);

    Map<String, BusinessBroadcastCampaignAction> businessBroadcastCampaigns();

    WhatsAppStore setBusinessBroadcastCampaigns(Map<String, BusinessBroadcastCampaignAction> campaigns);

    Map<String, BusinessBroadcastInsightsAction> businessBroadcastInsights();

    WhatsAppStore setBusinessBroadcastInsights(Map<String, BusinessBroadcastInsightsAction> insights);

    Optional<byte[]> nctSalt();

    WhatsAppStore setNctSalt(byte[] salt);

    Map<String, Boolean> nuxStates();

    WhatsAppStore setNuxStates(Map<String, Boolean> states);

    Optional<com.github.auties00.cobalt.model.device.DeviceCapabilities> primaryDeviceCapabilities();

    WhatsAppStore setPrimaryDeviceCapabilities(com.github.auties00.cobalt.model.device.DeviceCapabilities capabilities);

    Map<String, com.github.auties00.cobalt.model.device.DeviceCapabilities> deviceCapabilitiesStates();

    WhatsAppStore setDeviceCapabilitiesStates(
            Map<String, com.github.auties00.cobalt.model.device.DeviceCapabilities> states
    );

    Map<String, InteractiveMessageAction> interactiveMessageStates();

    WhatsAppStore setInteractiveMessageStates(
            Map<String, InteractiveMessageAction> states
    );

    Map<String, NoteEditAction> noteStates();

    WhatsAppStore setNoteStates(Map<String, NoteEditAction> states);

    Map<String, Instant> newsletterPinStates();

    WhatsAppStore setNewsletterPinStates(Map<String, Instant> states);

    Optional<Boolean> hasAvatar();

    WhatsAppStore setHasAvatar(Boolean hasAvatar);

    Map<String, com.github.auties00.cobalt.model.call.CallLog> callLogStates();

    WhatsAppStore setCallLogStates(Map<String, com.github.auties00.cobalt.model.call.CallLog> states);

    Map<String, Boolean> botWelcomeRequestStates();

    WhatsAppStore setBotWelcomeRequestStates(Map<String, Boolean> states);

    Map<String, String> aiThreadTitles();

    WhatsAppStore setAiThreadTitles(Map<String, String> titles);

    Optional<UsernameChatStartModeAction.ChatStartMode> usernameChatStartMode();

    WhatsAppStore setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode mode);

    Optional<NotificationActivitySettingAction.NotificationActivitySetting> notificationActivitySetting();

    WhatsAppStore setNotificationActivitySetting(NotificationActivitySettingAction.NotificationActivitySetting setting);

    List<RecentEmojiWeight> recentEmojiWeights();

    WhatsAppStore setRecentEmojiWeights(List<RecentEmojiWeight> weights);

    Optional<String> wamoUserIdentifier();

    WhatsAppStore setWamoUserIdentifier(String identifier);

    /**
     * Returns the most recent {@code MusicUserIdAction} sync action received
     * for this account, if any.
     *
     * <p>WhatsApp Web persists this action via the {@code MusicUserIdAction}
     * sync action protobuf. The value is currently forward-looking: no WA Web
     * read path consumes it yet, so it is stored verbatim for round-trip
     * fidelity.
     *
     * @implNote WAWebProtobufSyncAction.pb.MusicUserIdAction
     * @return an {@code Optional} containing the action if one has been received
     */
    Optional<MusicUserIdAction> musicUserIdState();

    /**
     * Sets the most recent {@code MusicUserIdAction} sync action.
     *
     * @implNote WAWebProtobufSyncAction.pb.MusicUserIdAction
     * @param action the action to store, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setMusicUserIdState(MusicUserIdAction action);

    Optional<String> newsletterSavedInterests();

    WhatsAppStore setNewsletterSavedInterests(String interests);

    Optional<Boolean> statusPostOptInNotificationPreferencesEnabled();

    WhatsAppStore setStatusPostOptInNotificationPreferencesEnabled(Boolean enabled);

    Optional<PrivateProcessingSettingAction.PrivateProcessingStatus> privateProcessingStatus();

    WhatsAppStore setPrivateProcessingStatus(PrivateProcessingSettingAction.PrivateProcessingStatus status);

    /**
     * Returns the user's opt-out preference for personalised channel
     * recommendations, if it has been set by a sync action.
     *
     * <p>WhatsApp Web persists this preference via the
     * {@code PrivacySettingChannelsPersonalisedRecommendationAction} sync
     * action. The value is currently forward-looking: no WA Web read path
     * consumes it yet, so it is stored verbatim for round-trip fidelity.
     *
     * @implNote WAWebProtobufSyncAction.pb.PrivacySettingChannelsPersonalisedRecommendationAction
     * @return an {@code Optional} containing the opt-out flag if it has been set
     */
    Optional<Boolean> channelsPersonalisedRecommendationOptOut();

    /**
     * Sets the user's opt-out preference for personalised channel
     * recommendations.
     *
     * @implNote WAWebProtobufSyncAction.pb.PrivacySettingChannelsPersonalisedRecommendationAction
     * @param optOut the opt-out flag, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setChannelsPersonalisedRecommendationOptOut(Boolean optOut);

    Optional<byte[]> ugcBotDefinition();

    WhatsAppStore setUgcBotDefinition(byte[] definition);

    /**
     * Returns the most recent Maiba AI feature control state received via the
     * {@code MaibaAIFeaturesControlAction} sync action, if any.
     *
     * <p>WhatsApp Web persists this status via the
     * {@code MaibaAIFeaturesControlAction} sync action protobuf. The value is
     * currently forward-looking: no WA Web read path consumes it yet, so it is
     * stored verbatim for round-trip fidelity.
     *
     * @implNote WAWebProtobufSyncAction.pb.MaibaAIFeaturesControlAction
     * @return an {@code Optional} containing the status if one has been received
     */
    Optional<MaibaAIFeaturesControlAction.MaibaAIFeatureStatus> maibaAiFeatureStatus();

    /**
     * Sets the Maiba AI feature control status.
     *
     * @implNote WAWebProtobufSyncAction.pb.MaibaAIFeaturesControlAction
     * @param status the status to store, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setMaibaAiFeatureStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus status);

    /**
     * Returns the timestamp at which this companion device was paired with
     * the primary device.
     *
     * <p>WhatsApp Web persists this timestamp in the multi-device user
     * preferences and uses it to filter events that predate pairing — e.g.
     * avatar updates and call log entries that arrive during initial history
     * sync but actually originated before this device became part of the
     * account.
     *
     * @implNote WAWebUserPrefsMultiDevice.getPairingTimestamp
     * @return an {@code Optional} containing the pairing timestamp if known
     */
    Optional<Instant> pairingTimestamp();

    /**
     * Sets the timestamp at which this companion device was paired with the
     * primary device.
     *
     * @implNote WAWebUserPrefsMultiDevice.getPairingTimestamp
     * @param pairingTimestamp the pairing timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setPairingTimestamp(Instant pairingTimestamp);

    /**
     * Removes every recent sticker that is flagged as an avatar sticker and
     * returns the number of entries removed.
     *
     * <p>WhatsApp Web exposes this operation on
     * {@code WAWebRecentStickerCollectionMd} as
     * {@code removeAllRecentAvatarStickers}, which iterates the recent sticker
     * collection and drops every entry whose {@code isAvatar} attribute is
     * truthy. Cobalt mirrors that semantic against the in-memory recent
     * sticker map.
     *
     * @implNote WAWebRecentStickerCollectionMd.removeAllRecentAvatarStickers
     * @return the number of recent avatar stickers that were removed
     */
    int removeAllRecentAvatarStickers();

    /**
     * Returns the mention-everyone mute expiration for a chat.
     *
     * <p>Per WhatsApp Web, this is the {@code mentionAllMuteExpiration}
     * field stored separately from the regular mute state. It controls
     * whether mention-everyone notifications are suppressed for a chat.
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
}
