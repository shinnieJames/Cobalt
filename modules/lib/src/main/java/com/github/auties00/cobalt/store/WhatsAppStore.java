
package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
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
 *
 * <p>The three lifecycle exports of {@code WAWebModelStorageInitialize}
 * are absorbed as follows:
 * <ul>
 *   <li>{@code initializeWithoutGKs}: WA Web's per-table {@code addTable()}
 *       fan-out plus {@code WAWebModelStorageUtils.createStorage(...)}
 *       and {@code .initialize()} are replaced by the
 *       {@link WhatsAppStoreFactory#create(com.github.auties00.cobalt.client.WhatsAppClientType, java.util.UUID)
 *       create} / {@link WhatsAppStoreFactory#load(com.github.auties00.cobalt.client.WhatsAppClientType, java.util.UUID)
 *       load} factory entry points invoked by the {@code WhatsAppClient}
 *       constructor. Cobalt has no schema-rollout step because every WA
 *       Web {@code WAWebSchema*} table maps to a {@code ConcurrentHashMap}
 *       field that is unconditionally present. The column-packing /
 *       {@code WAWebDbRolloutUtil} / {@code WAWebStorageGatingUtils}
 *       branches are not modelled because they only choose between
 *       IndexedDB schema layouts, which Cobalt does not have.</li>
 *   <li>{@code destroy}: WA Web's combination of
 *       {@code WAWebModelStorageUtils.destroyStorage()} (drop all tables)
 *       and the fall-back {@code new Dexie(DATABASE_NAME).delete()}
 *       collapses into {@link #delete()}, which is invoked by
 *       {@code WhatsAppClient.disconnect0} when the session reason is
 *       {@code LOGGED_OUT} or {@code BANNED}. The
 *       {@code .finally(s = null)} step that resets WA Web's cached
 *       init promise has no Cobalt counterpart because Cobalt does not
 *       cache an init promise at the module level &mdash; each new
 *       client instantiates a fresh store via the factory.</li>
 *   <li>{@code clearInitializePromise}: no Cobalt analog. WA Web exposes
 *       this as a test-only reset of the module-level {@code s} promise
 *       cache that {@code initializeWithoutGKs} memoizes. Cobalt has no
 *       such cache because {@link WhatsAppStoreFactory} is stateless and
 *       each call materializes a new aggregate.</li>
 * </ul>
 */
@WhatsAppWebModule(moduleName = "WAWebModelStorageInitialize")
@WhatsAppWebModule(moduleName = "WAWebCollections")
@WhatsAppWebModule(moduleName = "WAWebSignalStorage")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsBase")
@WhatsAppWebModule(moduleName = "WAWebGetSyncKey")
@WhatsAppWebModule(moduleName = "WAWebGetSyncAction")
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
     * Permanently deletes this session from storage.
     *
     * <p>After this method returns, the session data cannot be recovered.
     *
     * @implNote {@code WAWebModelStorageInitialize.destroy} performs
     *           {@code WAWebModelStorageUtils.destroyStorage().catch(() ->
     *           dexieCastToPromise(new Dexie(DATABASE_NAME).delete())).finally(() ->
     *           s = null)} to drop every IndexedDB table on logout and
     *           reset the cached init promise. Cobalt collapses both the
     *           soft-drop and the hard-delete branches into this single
     *           method because all WA Web schema tables map to in-memory
     *           {@code ConcurrentHashMap} fields that are released along
     *           with the store instance, so only the persistent backing
     *           directory must be removed; the {@code s = null} step has
     *           no analog because Cobalt does not memoize an init promise.
     *           Called by {@code WhatsAppClient.disconnect0} when the
     *           disconnect reason is {@code LOGGED_OUT} or {@code BANNED},
     *           matching the WA Web call-site that runs at logout.
     *
     * @throws IOException if the session cannot be deleted
     */
    @WhatsAppWebExport(moduleName = "WAWebModelStorageInitialize",
            exports = "destroy",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSignalStorage",
            exports = "destroy",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
     * Returns an unmodifiable snapshot of every quick reply currently held
     * in the store.
     *
     * <p>WhatsApp Web queries the full list through
     * {@code WAWebSchemaQuickReply.getQuickReplyTable().getAll()}, which
     * returns the whole contents of the {@code quick-reply} IndexedDB table.
     * Cobalt collapses that table into a {@link java.util.concurrent.ConcurrentMap}
     * in {@link AbstractWhatsAppStore} and exposes the same listing through
     * this accessor.
     *
     * @return the quick replies, in no particular order
     * @implNote WAWebSchemaQuickReply.getQuickReplyTable().getAll()
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
     * Returns all app state sync keys currently held in the store.
     *
     * <p>Per WhatsApp Web {@code WAWebGetSyncKey.getAllSyncKeysInTransaction}:
     * runs {@code SyncKeyStore.getAll()} inside a {@code SyncKeyStore}
     * transaction, which delegates to
     * {@code WAWebSyncdDb.getAllSyncKeys() ->
     * getSyncKeysTable().all().map(convertToSyncKeyFromRow)}.
     *
     * <p>Cobalt collapses the IndexedDB cursor into a direct
     * iteration over the in-memory map preserving insertion order.
     *
     * @return an unmodifiable {@link SequencedCollection} of every
     *         {@link AppStateSyncKey} known to the store; empty if no
     *         keys have been stored yet
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getAllSyncKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    SequencedCollection<AppStateSyncKey> appStateKeys();

    /**
     * Returns the app state sync key with the given raw {@code keyId},
     * if one is stored.
     *
     * <p>Per WhatsApp Web {@code WAWebGetSyncKey.getSyncKeyInTransaction_DO_NOT_USE}:
     * runs {@code SyncKeyStore.get(keyId)} inside a transaction, which
     * delegates to {@code WAWebSyncdDb.getSyncKey(keyId) ->
     * getSyncKeysTable().get(new Uint8Array(fromSyncKeyId(keyId)))}.
     * The {@code _DO_NOT_USE} suffix marks this lookup as a direct,
     * non-validated read intended only for low-level subsystems —
     * callers should normally consult
     * {@code WAWebSyncdKeyManagement.getNewestKeyPair} or the
     * higher-level helpers instead.
     *
     * @param id the raw 6-byte sync key identifier; must not be
     *           {@code null}
     * @return an {@link Optional} containing the matching
     *         {@link AppStateSyncKey}, or {@link Optional#empty()} if
     *         no key with this id is stored
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getSyncKeyInTransaction_DO_NOT_USE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id);

    /**
     * Inserts or replaces a batch of app state sync keys.
     *
     * <p>Per WhatsApp Web {@code WAWebGetSyncKey.setSyncKeyInTransaction}:
     * runs {@code SyncKeyStore.set(key)} inside a transaction, which
     * delegates to {@code WAWebSyncdDb.createSyncKey(key) ->
     * getSyncKeysTable().createOrReplace(convertFromSyncKeyToRow(key))}.
     *
     * <p>Cobalt accepts a {@link Collection} per call rather than a
     * single key because the sync-key rotation and key-share paths
     * ({@code SyncKeyRotationService}) batch their store writes; WA
     * Web instead invokes {@code setSyncKeyInTransaction} once per
     * key from {@code WAWebSyncdHandleKeyShare.handleKeyShare} and
     * {@code WAWebSyncdKeyManagement}. Implementations skip keys
     * whose {@code keyData.keyData} payload is absent or empty —
     * a defensive guard not present in WA Web because its callers
     * always supply fully-populated keys.
     *
     * @param keys the collection of keys to add or update; must not be
     *             {@code null}
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "setSyncKeyInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addWebAppStateKeys(Collection<AppStateSyncKey> keys);

    /**
     * Marks every app state sync key whose generation timestamp is at
     * or before the given instant as expired by zeroing its timestamp.
     *
     * <p>This is a Cobalt-only helper without a direct WhatsApp Web
     * basis: WA Web's {@code SyncKeyStore} only exposes
     * {@code expire(epoch)} (mapped to
     * {@link #expireAppStateKeysByEpoch(int)}) and
     * {@code clear()}. The method is currently unused and is
     * retained pending cleanup by the phantom-sweep agent.
     *
     * @param threshold the cutoff instant; keys with timestamps at or
     *                  before this value have their timestamp set to
     *                  {@link java.time.Instant#EPOCH}
     * @implNote NO_WA_BASIS — phantom helper, not invoked anywhere in
     *           the current codebase.
     */
    void expireAppStateKeys(Instant threshold);

    /**
     * Marks every app state sync key whose derived epoch equals the
     * provided value as expired by zeroing its
     * {@link AppStateSyncKey} timestamp.
     *
     * <p>Per WhatsApp Web {@code WAWebGetSyncKey.expireSyncKeyInTransaction}:
     * runs {@code SyncKeyStore.expire(epoch)} inside a transaction,
     * which delegates to {@code WAWebSyncdDb.expireSyncKey(epoch)}:
     * <pre>{@code
     *   const t = yield getSyncKeysTable().equals(["keyEpoch"], epoch);
     *   t.forEach(e => getSyncKeysTable().merge({keyId: e.keyId},
     *                                          {timestamp: 0}));
     * }</pre>
     *
     * <p>This is a soft-mark expiration, NOT a deletion — keys remain
     * in the store but their timestamp is reset to
     * {@link java.time.Instant#EPOCH} so subsequent freshness checks
     * treat them as expired.
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
     * store across every {@link SyncPatchType} collection.
     *
     * <p>Mirrors WhatsApp Web's
     * {@code WAWebGetSyncAction.countSyncActionsInTransaction}, which performs
     * {@code SyncActionStore.count()} (an IndexedDB {@code count()} on the
     * {@code sync-actions} object store) inside a {@code RunInTransaction}
     * envelope. The WA Web table is unpartitioned because its primary key is
     * the plaintext {@code index} which spans all collections; Cobalt
     * partitions storage by {@link SyncPatchType} and therefore returns the
     * sum of per-patch-type entry counts.
     *
     * @return the total number of sync action entries currently stored
     * @implNote {@code WAWebSyncActionStore.count} delegates to
     *           {@code WAWebSchemaSyncActions.getSyncActionsTable().count()};
     *           Cobalt sums {@link Collection#size()} over each per-patch-type
     *           map.
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
     * <p>Mirrors WhatsApp Web's {@code WAWebGetSyncAction.getAllSyncActions},
     * which performs {@code SyncActionStore.getAll()} (an IndexedDB
     * {@code .all()} on the {@code sync-actions} object store) inside a
     * {@code RunInTransaction} envelope and returns every row converted to a
     * {@code SyncAction}.
     *
     * @return an unmodifiable collection of every stored entry, never
     *         {@code null}; empty if no entries are stored
     * @implNote {@code WAWebSyncActionStore.getAll} flattens the unpartitioned
     *           {@code sync-actions} table; Cobalt concatenates the values of
     *           each per-patch-type map.
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
     * <p>Mirrors {@code WAWebGetMissingKey.getAllMissingKeysInTransaction},
     * which wraps {@code WAWebMissingKeyStore.getAll} in a
     * {@code MissingKeyStore} {@code RunInTransaction} envelope and ultimately
     * returns every row of the {@code missing-keys} IndexedDB table.
     *
     * @return an unmodifiable collection of every stored missing-key entry,
     *         never {@code null}; empty when no keys are tracked
     * @implNote {@code WAWebMissingKeyStore.getAll} delegates to
     *           {@code WAWebSyncdDb.getAllMissingKeys}; Cobalt simply exposes
     *           the values of the in-memory map.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getAllMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    Collection<MissingDeviceSyncKey> missingSyncKeys();

    /**
     * Finds the missing sync key entry whose primary key matches the given
     * raw {@code keyId} bytes.
     *
     * <p>Single-element form of WA Web's
     * {@code bulkGetMissingKeysInTransaction}: that JS export accepts a list
     * of {@code keyHex} strings and returns the matching rows out of the
     * {@code missing-keys} table; Cobalt instead takes the raw key bytes and
     * encodes them with {@link java.util.HexFormat} to obtain the same
     * {@code keyHex} primary key.
     *
     * @param keyId the raw key identifier bytes to look up
     * @return an {@link Optional} containing the entry if present,
     *         {@link Optional#empty()} otherwise
     * @implNote {@code WAWebMissingKeyStore.bulkGet} delegates to
     *           {@code WAWebSyncdDb.bulkGetMissingKeys}; the missing-keys
     *           table uses {@code keyHex} as primary key
     *           ({@code WAWebSchemaMissingKeys.addTable}).
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
     * <p>Mirrors {@code WAWebGetMissingKey.getMissingKeyCountTransaction},
     * which wraps {@code WAWebMissingKeyStore.count} in a
     * {@code MissingKeyStore} {@code RunInTransaction} envelope.
     *
     * @return the number of missing-key entries; {@code 0} when none are
     *         tracked
     * @implNote {@code WAWebMissingKeyStore.count} delegates to
     *           {@code WAWebSyncdDb.getMissingKeyCount}; Cobalt simply
     *           returns the size of the in-memory map.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getMissingKeyCountTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    int missingSyncKeyCount();

    /**
     * Adds or updates a single missing sync key entry.
     *
     * <p>The entry is keyed by its raw {@code keyId} bytes encoded as a hex
     * string, mirroring the {@code keyHex} primary-key column declared by
     * {@code WAWebSchemaMissingKeys} on the {@code missing-keys} IndexedDB
     * table; an existing record with the same {@code keyHex} is replaced.
     *
     * <p>This is the single-element form of
     * {@link #addMissingSyncKeys(Collection)} and corresponds to the JS
     * primitive {@code WAWebGetMissingKey.bulkUpdateMissingKeysInTransaction}
     * invoked with a one-element list. WA Web's higher-level
     * {@code WAWebSyncdStoreMissingKeys.addMissingKeys} workflow reaches this
     * primitive after building the {@code {keyHex, keyId, timestamp,
     * deviceResponses}} record list.
     *
     * @param missingKey the missing-key entry to add or replace
     * @implNote Single-element delegation to
     *           {@code WAWebGetMissingKey.bulkUpdateMissingKeysInTransaction},
     *           which forwards to {@code WAWebMissingKeyStore.bulkUpdate} and
     *           ultimately to {@code WAWebSyncdDb.createOrUpdateMissingKeys}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addMissingSyncKey(MissingDeviceSyncKey missingKey);

    /**
     * Adds or updates the given missing sync key entries in bulk.
     *
     * <p>Each entry is keyed by its raw {@code keyId} bytes encoded as a hex
     * string, replacing any existing record with the same {@code keyHex}.
     * This is the direct counterpart of WA Web's
     * {@code bulkUpdateMissingKeysInTransaction}, which forwards the supplied
     * list to {@code WAWebMissingKeyStore.bulkUpdate} inside a single
     * {@code MissingKeyStore} transaction.
     *
     * <p>Passing an empty collection is a no-op; {@code null} is not
     * accepted.
     *
     * @param missingKeys the missing-key entries to upsert
     * @implNote Mirrors
     *           {@code WAWebGetMissingKey.bulkUpdateMissingKeysInTransaction},
     *           which delegates to {@code WAWebMissingKeyStore.bulkUpdate} and
     *           ultimately to {@code WAWebSyncdDb.createOrUpdateMissingKeys}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    void addMissingSyncKeys(Collection<MissingDeviceSyncKey> missingKeys);

    /**
     * Removes the missing sync key entry whose primary key matches the given
     * raw {@code keyId} bytes.
     *
     * <p>Single-element form of {@code WAWebMissingKeyStore.bulkRemove}, the
     * IDB-level primitive that
     * {@code WAWebSyncdStoreMissingKeys.updateMissingKeys} uses to evict a
     * key once a companion device has supplied it.
     *
     * @param keyId the raw key identifier bytes to remove
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
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdCollectionsStateMachine",
            exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED
    ) // WAWebSyncdCollectionsStateMachine.getCollectionState
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
     * suffered a fatal MAC mismatch.
     *
     * <p>Mirrors WA Web's {@code getIsCollectionInMacMismatchFatalInTransaction}
     * which reads the persistent {@code isCollectionInMacMismatchFatal} boolean
     * from the {@code CollectionVersionStore} entry. Once set, the flag persists
     * across all collection state transitions.
     *
     * @implNote WAWebGetCollectionVersion.getIsCollectionInMacMismatchFatalInTransaction
     *           — {@code n.get(e).then(e => e?.isCollectionInMacMismatchFatal)}.
     *           The {@code undefined} fallback (when no entry exists) is handled by
     *           {@link #findWebAppState(SyncPatchType)} which returns a default
     *           {@link SyncCollectionMetadata} with {@code macMismatch == false}.
     * @param collectionName the collection name
     * @return {@code true} if the collection is in the fatal MAC mismatch state,
     *         {@code false} otherwise
     */
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "getIsCollectionInMacMismatchFatalInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    boolean isCollectionInMacMismatchFatal(SyncPatchType collectionName);

    /**
     * Marks a web app state collection in MAC mismatch state.
     *
     * <p>Per WhatsApp Web: when a patch snapshot MAC doesn't match the locally
     * computed value, the collection enters this degraded state rather than
     * failing fatally. Processing continues but integrity may be compromised.
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
     * Adds an orphan mutation for the specified collection.
     *
     * <p>Orphan mutations reference entities that do not yet exist locally.
     * They are persisted and retried when the referenced entities arrive.
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

    /**
     * Returns the JIDs of every stored business broadcast list.
     *
     * <p>The stored broadcast list identifiers are projected into
     * JIDs on the broadcast server so callers can address the broadcast
     * targets directly.
     *
     * @return a snapshot collection of broadcast list JIDs, in the
     *         insertion order of the underlying map; empty if no
     *         broadcast lists are known
     *
     * @implNote WAWebBroadcastListStorageUtils.getAllBroadcastLists
     */
    SequencedCollection<Jid> broadcasts();

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
     * Returns the clock skew between the server and the local clock, in
     * seconds.
     *
     * <p>WhatsApp Web computes this as the difference between the server
     * timestamp carried by the {@code <success>} stanza's {@code t} attribute
     * and the local epoch seconds, then persists it so every timestamp-aware
     * module can rebase {@code Date} comparisons against server time.
     *
     * @implNote WAWebUpdateClockSkewUtils.updateClockSkew writes the value via
     *           {@code WATimeUtils.setClockSkew(n)} and forwards it to the
     *           frontend via {@code frontendFireAndForget("setWebClockSkew", ...)}.
     * @return the stored clock skew in seconds ({@code 0} if never set)
     */
    long clockSkewSeconds(); // WAWebUpdateClockSkewUtils.updateClockSkew

    /**
     * Sets the clock skew between server and local time, expressed in
     * seconds.
     *
     * @implNote WAWebUpdateClockSkewUtils.updateClockSkew
     * @param clockSkewSeconds the difference in seconds between the server
     *                         timestamp and the local clock, positive when
     *                         the server is ahead
     * @return this store instance for method chaining
     */
    WhatsAppStore setClockSkewSeconds(long clockSkewSeconds); // WAWebUpdateClockSkewUtils.updateClockSkew

    /**
     * Returns the timestamp of the last group AB-props emergency push the
     * server signalled via the {@code <success>} stanza's
     * {@code group_abprops} attribute.
     *
     * <p>WhatsApp Web persists this timestamp in {@code localStorage} and
     * reads it back on subsequent AB-props syncs to detect whether a delta
     * push has already been applied, avoiding redundant full syncs.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     * @return an {@link Optional} containing the last emergency push
     *         timestamp, or empty if none has been recorded yet
     */
    Optional<Instant> groupAbPropsEmergencyPushTimestamp(); // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp

    /**
     * Sets the timestamp of the last group AB-props emergency push.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     * @param timestamp the emergency push timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setGroupAbPropsEmergencyPushTimestamp(Instant timestamp); // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp

    /**
     * Returns the AB-props {@code abKey} string most recently received from
     * the server.
     *
     * <p>WhatsApp Web persists this value as the {@code abKey} field of the
     * JSON object stored under the {@code ABPROPS} {@code localStorage} key,
     * and surfaces it as the WAM {@code abKey2} global (field {@code 4473}).
     *
     * @implNote WAWebABPropsLocalStorage.getABKey
     * @return an {@link Optional} containing the AB key, or empty if none has
     *         been received yet
     */
    Optional<String> abPropsAbKey(); // WAWebABPropsLocalStorage.getABKey

    /**
     * Sets the AB-props {@code abKey} string.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     * @param abKey the AB key, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsAbKey(String abKey); // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * Returns the AB-props {@code hash} string used for delta-update
     * negotiation with the server.
     *
     * <p>WhatsApp Web persists this value as the {@code hash} field of the
     * JSON object stored under the {@code ABPROPS} {@code localStorage} key.
     *
     * @implNote WAWebABPropsLocalStorage.getHash
     * @return an {@link Optional} containing the AB-props hash, or empty if
     *         no sync has completed yet
     */
    Optional<String> abPropsHash(); // WAWebABPropsLocalStorage.getHash

    /**
     * Sets the AB-props {@code hash} string.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     * @param hash the AB-props hash, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsHash(String hash); // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * Returns the AB-props refresh interval, in seconds.
     *
     * <p>WhatsApp Web persists this value as the {@code refresh} field of the
     * JSON object stored under the {@code ABPROPS} {@code localStorage} key
     * and parses it as an integer when retrieved. The server-supplied value
     * is clamped into the inclusive range {@code [600, 604800]} when written
     * by {@code updateAttributesLocalStorage}, and falls back to one day
     * ({@code 86400}) when no value has been persisted.
     *
     * @implNote WAWebABPropsLocalStorage.getRefresh — when no value has been
     *           recorded, the JS export returns {@code parseInt(86400, 10)};
     *           Cobalt returns the same constant via {@link #abPropsRefresh()}
     *           after defaulting through the underlying field.
     * @return the AB-props refresh interval in seconds, or {@code 86400} when
     *         no value has been recorded
     */
    long abPropsRefresh(); // WAWebABPropsLocalStorage.getRefresh

    /**
     * Sets the AB-props refresh interval, in seconds.
     *
     * <p>The supplied value is clamped into the inclusive range
     * {@code [600, 604800]} to mirror the bounds enforced by
     * {@code updateAttributesLocalStorage}.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     * @param refreshSeconds the desired refresh interval in seconds
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsRefresh(long refreshSeconds); // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * Returns the timestamp at which the most recent successful AB-props
     * sync occurred.
     *
     * <p>WhatsApp Web persists this value as the {@code lastSyncTime} field
     * of the JSON object stored under the {@code ABPROPS} {@code localStorage}
     * key, expressed as milliseconds since the Unix epoch.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage —
     *           {@code lastSyncTime} parameter
     * @return an {@link Optional} containing the last sync timestamp, or
     *         empty if no sync has been recorded
     */
    Optional<Instant> abPropsLastSyncTime(); // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * Sets the timestamp of the most recent successful AB-props sync.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     * @param lastSyncTime the sync completion instant, or {@code null} to clear
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsLastSyncTime(Instant lastSyncTime); // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * Returns the AB-props refresh id received from the server on the most
     * recent sync.
     *
     * <p>WhatsApp Web persists this value under the
     * {@code ABPROPS_REFRESH_ID} {@code localStorage} key and uses it as the
     * {@code propsRefreshId} request attribute when justknobx {@code 3330}
     * is enabled. The JS export {@code getRefreshId} initialises the slot to
     * {@code 0} the first time it is read and never returns a negative value.
     *
     * @implNote WAWebABPropsLocalStorage.getRefreshId
     * @return the AB-props refresh id ({@code 0} if never set)
     */
    long abPropsRefreshId(); // WAWebABPropsLocalStorage.getRefreshId

    /**
     * Sets the AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setRefreshId
     * @param refreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsRefreshId(long refreshId); // WAWebABPropsLocalStorage.setRefreshId

    /**
     * Returns the web-only AB-props refresh id used to gate the justknobx
     * {@code 2086} emergency push request.
     *
     * <p>WhatsApp Web persists this value under the
     * {@code UserPrefs.AbpropsWebRefreshId} key. The JS export
     * {@code getWebRefreshId} initialises the slot to {@code 0} the first
     * time it is read.
     *
     * @implNote WAWebABPropsLocalStorage.getWebRefreshId
     * @return the web AB-props refresh id ({@code 0} if never set)
     */
    long abPropsWebRefreshId(); // WAWebABPropsLocalStorage.getWebRefreshId

    /**
     * Sets the web-only AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setWebRefreshId
     * @param webRefreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setAbPropsWebRefreshId(long webRefreshId); // WAWebABPropsLocalStorage.setWebRefreshId

    /**
     * Returns the group AB-props refresh id received from the server on the
     * most recent sync.
     *
     * <p>WhatsApp Web persists this value under the
     * {@code GROUP_ABPROPS_REFRESH_ID} {@code localStorage} key. The JS
     * export {@code getGroupAbPropsRefreshId} returns {@code 0} when the
     * value has never been set.
     *
     * @implNote WAWebABPropsLocalStorage.getGroupAbPropsRefreshId
     * @return the group AB-props refresh id ({@code 0} if never set)
     */
    long groupAbPropsRefreshId(); // WAWebABPropsLocalStorage.getGroupAbPropsRefreshId

    /**
     * Sets the group AB-props refresh id.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
     * @param groupRefreshId the refresh id received from the server
     * @return this store instance for method chaining
     */
    WhatsAppStore setGroupAbPropsRefreshId(long groupRefreshId); // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId

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
