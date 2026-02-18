
package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentity;
import com.github.auties00.cobalt.model.auth.UserAgent.ReleaseChannel;
import com.github.auties00.cobalt.model.auth.Version;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.business.VerifiedBusinessName;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.info.ChatMessageInfo;
import com.github.auties00.cobalt.model.info.MessageInfo;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.message.ChatMessageKey;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.SignalProtocolStore;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.key.SignalSignedKeyPair;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
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
 */
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
    JidDevice device();

    /**
     * Sets the device information.
     *
     * @param device the device info, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setDevice(JidDevice device);

    /**
     * Returns the release channel for this connection.
     *
     * @return the release channel, never {@code null}
     */
    ReleaseChannel releaseChannel();

    /**
     * Sets the release channel.
     *
     * @param releaseChannel the release channel, must not be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setReleaseChannel(ReleaseChannel releaseChannel);

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
    Optional<SignedDeviceIdentity> signedDeviceIdentity();

    /**
     * Sets the signed device identity.
     *
     * @param identity the signed device identity, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setSignedDeviceIdentity(SignedDeviceIdentity identity);

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
    void removeSenderKeysForDevice(Jid deviceJid);

    /**
     * Removes the sender keys for a sender key name.
     *
     * @param senderKeyName the sender key name
     */
    void removeSenderKeysForDevice(SignalSenderKeyName senderKeyName);

    /**
     * Cleans up all Signal sessions and sender keys for a device.
     *
     * @param deviceJid the device JID to clean up
     */
    void cleanupSignalSessionsForDevice(Jid deviceJid);

    /**
     * Returns all contacts stored in this session.
     *
     * @return an unmodifiable collection of all contacts
     */
    Collection<Contact> contacts();

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
     * Registers a bidirectional LID mapping for a contact.
     *
     * @param phoneJid the phone number JID
     * @param lidJid   the LID JID
     */
    void registerLidMapping(Jid phoneJid, Jid lidJid);

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
     * Adds or updates a chat in the store.
     *
     * @param chat the chat to add or update, must not be {@code null}
     * @return the chat that was added
     */
    Chat addChat(Chat chat);

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
     * Finds a chat message by its key.
     *
     * @param key the message key to search
     * @return an {@code Optional} containing the message if found
     */
    Optional<ChatMessageInfo> findChatMessageByKey(ChatMessageKey key);

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
     * Adds or updates a newsletter in the store.
     *
     * @param newsletter the newsletter to add, must not be {@code null}
     * @return the newsletter that was added
     */
    Newsletter addNewsletter(Newsletter newsletter);

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
     * Finds a quick reply by its shortcut.
     *
     * @param shortcut the quick reply shortcut
     * @return an {@code Optional} containing the quick reply if found
     */
    Optional<QuickReply> findQuickReply(String shortcut);

    /**
     * Adds a quick reply to the store.
     *
     * @param action the quick reply, must not be {@code null}
     */
    void addQuickReply(QuickReply action);

    /**
     * Removes a quick reply from the store.
     *
     * @param shortcut the quick reply shortcut
     * @return an {@code Optional} containing the removed quick reply if it existed
     */
    Optional<QuickReply> removeQuickReply(String shortcut);

    /**
     * Finds a label by its ID.
     *
     * @param labelId the label ID
     * @return an {@code Optional} containing the label if found
     */
    Optional<Label> findLabel(int labelId);

    /**
     * Adds a label to the store.
     *
     * @param label the label, must not be {@code null}
     */
    void addLabel(Label label);

    /**
     * Removes a label from the store.
     *
     * @param labelId the label ID
     * @return an {@code Optional} containing the removed label if it existed
     */
    Optional<Label> removeLabel(int labelId);

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
     * Finds a hash state by patch type.
     *
     * @param patchType the patch type to query
     * @return an {@code Optional} containing the hash state if found
     */
    Optional<AppStateSyncHash> findWebAppHashStateByName(PatchType patchType);

    /**
     * Adds or updates a hash state for app state synchronization.
     *
     * @param state the hash state to add, must not be {@code null}
     */
    void addWebAppHashState(AppStateSyncHash state);

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
     * Finds expired missing sync keys.
     *
     * @param timeout the duration after which a key is considered expired
     * @return a sequenced collection of expired keys
     */
    SequencedCollection<MissingDeviceSyncKey> findExpiredMissingSyncKeys(Duration timeout);

    /**
     * Gets the earliest timestamp of all missing sync keys.
     *
     * @return an {@code Optional} containing the earliest timestamp, or empty
     *         if no missing keys
     */
    Optional<Instant> getEarliestMissingSyncKeyTimestamp();

    /**
     * Calculates the timeout delay for the missing sync key check.
     *
     * @param timeout the timeout duration
     * @return an {@code Optional} containing the remaining delay, or empty
     *         if no missing keys
     */
    Optional<Duration> calculateMissingSyncKeyTimeoutDelay(Duration timeout);

    /**
     * Gets metadata for a web app state collection.
     *
     * @param collectionName the collection name
     * @return the collection metadata
     */
    CollectionMetadata findWebAppState(PatchType collectionName);

    /**
     * Updates a collection's version and LT-Hash.
     *
     * @param collectionName the collection name
     * @param newVersion     the new version
     * @param newLtHash      the new LT-Hash
     */
    void updateWebAppStateVersion(PatchType collectionName, long newVersion, byte[] newLtHash);

    /**
     * Marks a web app state collection as dirty.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateDirty(PatchType collectionName);

    /**
     * Marks a web app state collection as in-flight.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateInFlight(PatchType collectionName);

    /**
     * Marks a web app state collection as up-to-date.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateUpToDate(PatchType collectionName);

    /**
     * Marks a web app state collection as pending.
     *
     * @param collectionName the collection name
     */
    void markWebAppStatePending(PatchType collectionName);

    /**
     * Marks a web app state collection as blocked.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateBlocked(PatchType collectionName);

    /**
     * Marks a web app state collection in error retry state.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateErrorRetry(PatchType collectionName);

    /**
     * Marks a web app state collection in fatal error state.
     *
     * @param collectionName the collection name
     */
    void markWebAppStateErrorFatal(PatchType collectionName);

    /**
     * Adds pending mutations to the queue for the specified collection.
     *
     * @param collectionName the collection name
     * @param patch          the patches to queue
     */
    void addPendingMutations(PatchType collectionName, Collection<? extends PendingMutation> patch);

    /**
     * Gets all pending mutations for the specified collection.
     *
     * @param collectionName the collection name
     * @return an unmodifiable sequenced collection of pending mutations
     */
    SequencedCollection<PendingMutation> findPendingMutations(PatchType collectionName);

    /**
     * Removes pending mutations for a collection.
     *
     * @param collectionName the collection name
     */
    void removePendingMutations(PatchType collectionName);

    /**
     * Clears all pending mutations for a collection.
     *
     * @param collectionName the collection name
     */
    void clearPendingMutations(PatchType collectionName);

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
    void markUserNeedsSenderKeyRotation(Jid userJid);

    /**
     * Checks if a user needs sender key rotation and clears the flag.
     *
     * @param userJid the user JID to check
     * @return {@code true} if the user needed rotation (flag is cleared)
     */
    boolean checkAndClearSenderKeyRotationNeeded(Jid userJid);

    /**
     * Checks if any of the provided users need sender key rotation.
     *
     * @param userJids the user JIDs to check
     * @return {@code true} if any user needs rotation
     */
    boolean anyUserNeedsSenderKeyRotation(Collection<Jid> userJids);

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
    Version clientVersion();

    /**
     * Sets the client version.
     *
     * @param version the client version, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setClientVersion(Version version);

    /**
     * Returns the companion version.
     *
     * @return an {@code Optional} containing the companion version, or empty
     *         if not set
     */
    Optional<Version> companionVersion();

    /**
     * Sets the companion version.
     *
     * @param version the companion version, may be {@code null}
     * @return this store instance for method chaining
     */
    WhatsAppStore setCompanionVersion(Version version);

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
    Optional<VerifiedBusinessName> findVerifiedBusinessName(Jid jid);

    /**
     * Adds or replaces a verified business name record.
     *
     * @param record the record to store
     */
    void addVerifiedBusinessName(VerifiedBusinessName record);

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
}
