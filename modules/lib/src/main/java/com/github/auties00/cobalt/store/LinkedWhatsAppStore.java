package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.wam.model.WamChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The persistent and transient state of a WhatsApp client session.
 *
 * <p>This is the facade over everything the official WhatsApp app keeps on disk plus everything it
 * keeps in memory while the user is logged in. The state is partitioned into seven domain sub-stores,
 * each reached through an accessor:
 *
 * <ul>
 *   <li>{@link #signalStore()} - Signal-protocol keys, sessions, sender keys and identities.
 *   <li>{@link #accountStore()} - identity (phone number, JID, LID), device, profile and business profile.
 *   <li>{@link #contactStore()} - contacts, the phone/LID mapping table, device lists and the block list.
 *   <li>{@link #chatStore()} - chats, newsletters, status, messages, calls and call history.
 *   <li>{@link #syncStore()} - the app-state (syncd) machinery and the AB-props feature-flag bundle.
 *   <li>{@link #settingsStore()} - privacy settings, preferences, stickers, labels and quick replies.
 *   <li>{@link #businessStore()} - WhatsApp Business and payments state.
 * </ul>
 *
 * <p>The facade itself owns only the session-runtime state that belongs to no single domain: the
 * registered listeners, the proxy, the offline-resume coordination, the routing hints, the
 * receipt-record buffer, the linked-device list, the WAM telemetry buffers and sequence numbers, and
 * a handful of bootstrap artefacts. Domain operations are not exposed here; callers reach them through
 * the relevant sub-store.
 *
 * @apiNote
 * Embedders never construct this directly. Instances are obtained exclusively through
 * {@link WhatsAppStoreFactory}, which chooses the backing implementation.
 *
 * @implSpec
 * Implementations control how and where session data is stored. Read methods must return defensive
 * (unmodifiable) views of any internal collection. Setters return {@code this} so they can be chained.
 *
 * @see WhatsAppStoreFactory
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public non-sealed interface LinkedWhatsAppStore extends WhatsAppStore {
    /**
     * Blocks until all asynchronous initialization operations for this store complete.
     *
     * @throws IOException if the store cannot be deserialized completely or correctly
     */
    void await() throws IOException;

    /**
     * Flushes all pending changes to the underlying storage.
     *
     * @throws IOException if the session cannot be saved
     */
    void save() throws IOException;

    /**
     * Permanently deletes this session from storage.
     *
     * @throws IOException if the session cannot be deleted
     */
    void delete() throws IOException;

    /**
     * Returns the Signal-protocol cryptographic sub-store.
     *
     * @return the signal sub-store, never {@code null}
     */
    SignalStore signalStore();

    /**
     * Returns the account-identity and profile sub-store.
     *
     * @return the account sub-store, never {@code null}
     */
    AccountStore accountStore();

    /**
     * Returns the address-book and per-peer-device sub-store.
     *
     * @return the contact sub-store, never {@code null}
     */
    ContactStore contactStore();

    /**
     * Returns the conversation (chats, newsletters, status, calls) sub-store.
     *
     * @return the chat sub-store, never {@code null}
     */
    ChatStore chatStore();

    /**
     * Returns the app-state-sync and feature-flag sub-store.
     *
     * @return the sync sub-store, never {@code null}
     */
    SyncStore syncStore();

    /**
     * Returns the user-preference and settings sub-store.
     *
     * @return the settings sub-store, never {@code null}
     */
    SettingsStore settingsStore();

    /**
     * Returns the WhatsApp Business and payments sub-store.
     *
     * @return the business sub-store, never {@code null}
     */
    BusinessStore businessStore();

    /**
     * Returns the web-GraphQL credential sub-store backing the relay and Facebook GraphQL transports.
     *
     * @return the web-session sub-store, never {@code null}
     */
    WebSessionStore webSessionStore();

    /**
     * Registers an event listener.
     *
     * @param listener the listener; may be any {@link WhatsAppListener} subtype
     *                 (a per-event functional interface or the aggregator
     *                 {@link LinkedWhatsAppClientListener})
     * @return the registered listener
     */
    WhatsAppListener addListener(WhatsAppListener listener);

    /**
     * Unregisters an event listener.
     *
     * @param listener the listener
     * @return {@code true} if the listener was registered
     */
    boolean removeListener(WhatsAppListener listener);

    /**
     * Returns the registered event listeners.
     *
     * <p>The returned collection is heterogeneous: each element is some
     * {@link WhatsAppListener} subtype, and dispatch sites recover the
     * concrete event interface through {@code instanceof} pattern matching.
     *
     * @return an unmodifiable copy of the listeners
     */
    Collection<WhatsAppListener> listeners();

    /**
     * Returns the configured proxy.
     *
     * @return the proxy, or empty if none is configured
     */
    Optional<WhatsAppClientProxy> proxy();

    /**
     * Sets the proxy.
     *
     * @param proxy the proxy, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setProxy(WhatsAppClientProxy proxy);

    /**
     * Returns the offline-resume state.
     *
     * @return the offline-resume state, never {@code null}
     */
    LinkedWhatsAppClientOfflineResumeState offlineResumeState();

    /**
     * Sets the offline-resume state, driving the offline-delivery latch accordingly.
     *
     * @param state the state, never {@code null}
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState state);

    /**
     * Returns whether resume-from-restart has progressed past the initial phase.
     *
     * @return {@code true} if resume from restart is complete
     */
    boolean isResumeFromRestartComplete();

    /**
     * Blocks until offline delivery completes or a five-minute timeout elapses.
     */
    void waitForOfflineDeliveryEnd();

    /**
     * Returns the opaque server-routing token.
     *
     * @return the routing token, or empty if none is set
     */
    Optional<byte[]> routingInfo();

    /**
     * Sets the server-routing token.
     *
     * @param routingInfo the routing token, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setRoutingInfo(byte[] routingInfo);

    /**
     * Returns the routing domain hint.
     *
     * @return the routing domain, or empty if none is set
     */
    Optional<String> routingDomain();

    /**
     * Sets the routing domain hint.
     *
     * @param routingDomain the routing domain, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setRoutingDomain(String routingDomain);

    /**
     * Returns the companion pairing expiration deadline.
     *
     * @return the expiration deadline, or empty if none is set
     */
    Optional<Instant> clientExpiration();

    /**
     * Sets the companion pairing expiration deadline.
     *
     * @param clientExpiration the deadline, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setClientExpiration(Instant clientExpiration);

    /**
     * Returns the recipient device JIDs recorded for a sent message.
     *
     * @param messageId the message id
     * @return an unmodifiable copy of the recipient set
     */
    Set<Jid> findReceiptRecords(String messageId);

    /**
     * Creates or merges receipt records for a sent message.
     *
     * @param messageId     the message id
     * @param recipientJids the recipient device JIDs
     */
    void createOrMergeReceiptRecords(String messageId, Collection<Jid> recipientJids);

    /**
     * Removes receipt records for a message.
     *
     * @param messageId the message id
     */
    void removeReceiptRecords(String messageId);

    /**
     * Returns the linked companion devices.
     *
     * @return an unmodifiable copy of the linked-device JIDs
     */
    List<Jid> linkedDevices();

    /**
     * Replaces the linked-device list.
     *
     * @param linkedDevices the linked-device JIDs, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setLinkedDevices(Collection<Jid> linkedDevices);

    /**
     * Returns the timestamp of when this device paired with the primary.
     *
     * @return the pairing timestamp, or empty if not set
     */
    Optional<Instant> pairingTimestamp();

    /**
     * Sets the pairing timestamp.
     *
     * @param pairingTimestamp the timestamp, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setPairingTimestamp(Instant pairingTimestamp);

    /**
     * Returns whether the account has a profile avatar.
     *
     * @return the avatar flag, or empty if unknown
     */
    Optional<Boolean> hasAvatar();

    /**
     * Sets the profile-avatar flag.
     *
     * @param hasAvatar the flag, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setHasAvatar(Boolean hasAvatar);

    /**
     * Returns the notification-content-token salt.
     *
     * @return the salt, or empty if none is set
     */
    Optional<byte[]> notificationContentTokenSalt();

    /**
     * Sets the notification-content-token salt.
     *
     * @param salt the salt, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setNotificationContentTokenSalt(byte[] salt);

    /**
     * Returns the companion MMS authentication nonce.
     *
     * @return the nonce, or empty if none is set
     */
    Optional<String> companionMmsAuthNonce();

    /**
     * Sets the companion MMS authentication nonce.
     *
     * @param nonce the nonce, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setCompanionMmsAuthNonce(String nonce);

    /**
     * Returns the shareable-chat-link key.
     *
     * @return the key, or empty if none is set
     */
    Optional<byte[]> shareableChatLinkKey();

    /**
     * Sets the shareable-chat-link key.
     *
     * @param key the key, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setShareableChatLinkKey(byte[] key);

    /**
     * Returns the username chat-start mode.
     *
     * @return the chat-start mode, or empty if unset
     */
    Optional<UsernameChatStartModeAction.ChatStartMode> usernameChatStartMode();

    /**
     * Sets the username chat-start mode.
     *
     * @param mode the mode, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode mode);

    /**
     * Returns the WAM sequence number for a channel.
     *
     * @param channel the WAM channel
     * @return the sequence number, or empty if none is recorded
     */
    OptionalInt findWamSequenceNumber(WamChannel channel);

    /**
     * Sets the WAM sequence number for a channel.
     *
     * @param channel        the WAM channel
     * @param sequenceNumber the sequence number
     * @return this store instance for method chaining
     */
    LinkedWhatsAppStore putWamSequenceNumber(WamChannel channel, int sequenceNumber);

    /**
     * Returns the save keys of the WAM event buffers staged on disk.
     *
     * @return an unmodifiable copy of the buffer save keys
     */
    Collection<String> wamPendingBufferKeys();

    /**
     * Opens an atomic writer for a WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return an output stream that publishes the buffer on close
     * @throws IOException if the buffer file cannot be created
     */
    OutputStream openWamPendingBufferWriter(String saveKey) throws IOException;

    /**
     * Opens a reader for a staged WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return a reader for the buffer, or empty if none is staged
     * @throws IOException if the buffer file cannot be opened
     */
    Optional<InputStream> openWamPendingBufferReader(String saveKey) throws IOException;

    /**
     * Removes a staged WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return {@code true} if a buffer was removed
     * @throws IOException if the buffer file cannot be deleted
     */
    boolean removeWamPendingBuffer(String saveKey) throws IOException;

    /**
     * Removes every staged WAM event buffer.
     *
     * @return this store instance for method chaining
     * @throws IOException if a buffer file cannot be deleted
     */
    LinkedWhatsAppStore clearWamPendingBuffers() throws IOException;
}
