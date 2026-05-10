package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.OutContact;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.newsletter.*;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.util.StorePathUtils;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.key.SignalSignedKeyPair;
import com.github.auties00.libsignal.state.SignalSessionRecord;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level.WARNING;

/**
 * Concrete {@link WhatsAppStore} that persists session metadata to a single
 * protobuf file on disk and offloads all message bodies to an embedded
 * {@link PersistentMessageStore LMDB} key-value store.
 *
 * <p>Data layout under the session directory:
 * <ul>
 *   <li>{@code store.proto} — the aggregate inherited from
 *       {@link AbstractWhatsAppStore} (scalars, settings, contacts, calls,
 *       privacy, Signal-protocol state, syncd hash states, AB-props) plus
 *       the chats and newsletters {@link Map maps}. Chat and newsletter
 *       entries hold only metadata (jid, name, unread counters, ephemeral
 *       settings, …) — message bodies are stored in LMDB.
 *   <li>{@code messages.lmdb/} — the LMDB env, with {@code data.mdb} and
 *       {@code lock.mdb} inside. Holds chat messages keyed by
 *       {@code chatJid + msgId}, newsletter messages keyed by
 *       {@code newsletterJid + serverId} and the global status feed keyed
 *       by message id.
 * </ul>
 *
 * <p>{@link #save()} captures {@link #hashCode()} on the root store and
 * skips the file write entirely when nothing has changed since the
 * previous save. The metadata write goes through a sibling {@code .tmp}
 * file followed by an atomic move so a crash mid-write never leaves a
 * half-written {@code store.proto} on disk. LMDB writes are independently
 * durable on every commit, so {@link #save()} does not touch the LMDB
 * env.
 *
 * <p>On load the factory walks the LMDB env once to recover orphan
 * chats/newsletters whose messages were committed to LMDB but whose
 * metadata was lost in the window between the last successful LMDB
 * commit and the next metadata snapshot.
 *
 * <p>The inner {@link PersistentChat} and {@link PersistentNewsletter} subclasses delegate
 * every message-collection accessor to the owning store's
 * {@link PersistentMessageStore}. Per-chat and per-newsletter message counts are
 * cached in an {@link AtomicInteger} that is initialised by a one-time
 * cursor walk on attachment, then maintained incrementally on every
 * {@code addMessage} / {@code removeMessage} call.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
final class PersistentStore extends AbstractWhatsAppStore {
    /**
     * Name of the metadata file written under the session directory.
     */
    private static final String STORE_FILE = "store.proto";

    /**
     * Name of the LMDB sub-directory under the session directory.
     */
    private static final String MESSAGES_DIRECTORY = "messages.lmdb";

    /**
     * Map of chat JIDs to their metadata-only {@link PersistentChat} entries.
     */
    @ProtobufProperty(index = 82, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, PersistentChat> chats;

    /**
     * Map of newsletter JIDs to their metadata-only {@link PersistentNewsletter}
     * entries.
     */
    @ProtobufProperty(index = 83, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, PersistentNewsletter> newsletters;

    /**
     * Hash code captured at the end of the last successful
     * {@link #save()} so subsequent saves can short-circuit when nothing
     * has changed. Volatile so concurrent {@code save()} calls observe a
     * consistent value without holding the monitor.
     */
    private volatile Integer storeHashCode;

    /**
     * The LMDB facade backing every message-bearing accessor on the
     * inner {@link PersistentChat} / {@link PersistentNewsletter} subclasses and the global
     * status feed. Initialised lazily by {@link #attachMessageStore} so
     * it can be wired both after a fresh {@code create(...)} and after a
     * deserialisation pass via the factory.
     */
    private volatile PersistentMessageStore messageStore;

    /**
     * Constructs a new {@code PersistentStore}; invoked by the
     * generated protobuf builder and deserializer.
     *
     * <p>The {@link #messageStore} field is left {@code null} here and
     * is wired by the owning {@link PersistentStoreFactory} via
     * {@link #attachMessageStore(PersistentMessageStore)} immediately after
     * construction.
     */
    PersistentStore(
            UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, String businessWebsite, String businessEmail, BusinessCategory businessCategory, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations, ConcurrentMap<SyncPatchType, AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, ConcurrentHashMap<Jid, OutContact> outContacts, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeys, ConcurrentMap<Integer, Integer> wamSequenceNumbers,
            ConcurrentHashMap<Jid, PersistentChat> chats,
            ConcurrentHashMap<Jid, PersistentNewsletter> newsletters
    ) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsite, businessEmail, businessCategory, contacts, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations, orphanMutationEntries, outContacts, clockSkewSeconds, groupAbPropsEmergencyPushTimestamp, abPropsAbKey, abPropsHash, abPropsRefresh, abPropsLastSyncTime, abPropsRefreshId, abPropsWebRefreshId, groupAbPropsRefreshId, baseKeys, wamSequenceNumbers);
        this.chats = chats;
        this.newsletters = newsletters;
    }

    /**
     * Wires the LMDB facade into this store and into every existing
     * {@link PersistentChat} / {@link PersistentNewsletter} entry so their message
     * accessors can begin delegating. Called by
     * {@link PersistentStoreFactory} after constructing or
     * deserialising the store.
     *
     * @param messageStore the freshly opened LMDB facade
     */
    void attachMessageStore(PersistentMessageStore messageStore) {
        this.messageStore = messageStore;
        for (var chat : chats.values()) {
            chat.attach(messageStore);
        }
        for (var newsletter : newsletters.values()) {
            newsletter.attach(messageStore);
        }
    }

    /**
     * Returns the LMDB facade owned by this store.
     *
     * @return the message store
     */
    PersistentMessageStore messageStore() {
        return messageStore;
    }

    /**
     * Returns the path to the LMDB env directory for the given
     * session.
     *
     * @param clientType   the client type
     * @param baseDirectory the root directory under which per-session
     *                      folders are created
     * @param sessionId     the session uuid string or phone-number
     *                      string
     * @return the LMDB env directory
     * @throws IOException if the parent session directory cannot be
     *                     created
     */
    static Path messagesEnvPath(WhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return StorePathUtils.getSessionDirectory(clientType, baseDirectory, sessionId)
                .resolve(MESSAGES_DIRECTORY);
    }

    /**
     * Returns the path to the {@code store.proto} metadata file for
     * the given session.
     *
     * @param clientType    the client type
     * @param baseDirectory the root directory under which per-session
     *                      folders are created
     * @param sessionId     the session uuid string or phone-number
     *                      string
     * @return the metadata file path
     * @throws IOException if the parent session directory cannot be
     *                     created
     */
    static Path storeFilePath(WhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return StorePathUtils.getSessionFile(clientType, baseDirectory, sessionId, STORE_FILE);
    }

    @Override
    public WhatsAppStore save() {
        var newHashCode = hashCode();
        if (storeHashCode != null && storeHashCode == newHashCode) {
            return this;
        }
        synchronized (this) {
            if (storeHashCode != null && storeHashCode == newHashCode) {
                return this;
            }
            try {
                serializeStore();
                storeHashCode = newHashCode;
            } catch (IOException error) {
                logger.log(WARNING, "Error while serializing store", error);
            }
        }
        return this;
    }

    /**
     * Writes the current metadata snapshot to {@code store.proto} via a
     * sibling {@code .tmp} file followed by an atomic move.
     *
     * @throws IOException if the file cannot be created, written or
     *                     moved
     */
    private void serializeStore() throws IOException {
        var path = storeFilePath(clientType, directory, uuid.toString());
        Files.createDirectories(path.getParent());
        var tempFile = Files.createTempFile(path.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            PersistentStoreSpec.encode(this, ProtobufOutputStream.toStream(stream));
        }
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete() throws IOException {
        var folderPath = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
        if (messageStore != null) {
            messageStore.close();
            messageStore = null;
        }
        StorePathUtils.deleteRecursively(folderPath);
    }

    @Override
    public void await() {
        // Single-file metadata snapshot decodes synchronously in the
        // factory; nothing to wait for here.
    }

    /**
     * Closes the LMDB env. Called by the factory when the session
     * shuts down without being deleted.
     */
    void close() {
        if (messageStore != null) {
            messageStore.close();
            messageStore = null;
        }
    }

    @Override
    public Optional<Chat> findChatByJid(JidProvider jid) {
        return switch (jid) {
            case null -> Optional.empty();
            case Chat chat -> Optional.of(chat);
            case Contact _, Newsletter _, Jid _, JidServer _ -> {
                var targetJid = jid.toJid();
                if (targetJid.hasUserServer()) {
                    var jidChat = chats.get(targetJid);
                    if (jidChat != null) {
                        yield Optional.of(jidChat);
                    }
                    yield findLidByPhone(targetJid).map(chats::get);
                } else if (targetJid.hasLidServer()) {
                    var lidChat = chats.get(targetJid);
                    if (lidChat != null) {
                        yield Optional.of(lidChat);
                    }
                    var phone = findPhoneByLid(targetJid);
                    if (phone.isEmpty()) {
                        yield Optional.empty();
                    }
                    var phoneChat = chats.get(phone.get());
                    if (phoneChat != null) {
                        yield Optional.of(phoneChat);
                    }
                    yield findLidByPhone(phone.get()).map(chats::get);
                } else {
                    var chat = chats.get(targetJid);
                    yield Optional.ofNullable(chat);
                }
            }
        };
    }

    @Override
    public Optional<? extends MessageInfo> findMessageById(JidProvider provider, String id) {
        return provider == null || id == null ? Optional.empty() : switch (provider) {
            case Chat chat -> findMessageById(chat, id);
            case Newsletter newsletter -> findMessageById(newsletter, id);
            case Contact contact -> findChatByJid(contact.jid()).flatMap(chat -> findMessageById(chat, id));
            case Jid contactJid -> {
                if (contactJid.server().type() == JidServer.Type.NEWSLETTER) {
                    yield findNewsletterByJid(contactJid).flatMap(newsletter -> findMessageById(newsletter, id));
                } else if (Jid.statusBroadcastAccount().equals(contactJid)) {
                    yield messageStore.getStatusMessage(id);
                } else {
                    yield findChatByJid(contactJid).flatMap(chat -> findMessageById(chat, id));
                }
            }
            case JidServer jidServer -> findChatByJid(jidServer.toJid()).flatMap(chat -> findMessageById(chat, id));
        };
    }

    @Override
    public Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id) {
        if (newsletter == null || id == null) {
            return Optional.empty();
        }
        // Newsletters store messages keyed by serverId. Try the fast
        // path first (id parsed as a numeric serverId), then fall back
        // to a per-newsletter scan matching the optional message-key id.
        try {
            var serverId = Integer.parseInt(id);
            var byServerId = messageStore.getNewsletterMessageByServerId(newsletter.jid(), serverId);
            if (byServerId.isPresent()) {
                return byServerId;
            }
        } catch (NumberFormatException _) {
            // Fall through to the scan below.
        }
        try (var stream = newsletter.messages()) {
            return stream
                    .filter(entry -> Objects.equals(id, entry.key().id().orElse(null)))
                    .findFirst();
        }
    }

    @Override
    public Optional<ChatMessageInfo> findMessageById(Chat chat, String id) {
        if (chat == null || id == null) {
            return Optional.empty();
        }
        return chat.getMessageById(id);
    }

    @Override
    public Collection<Chat> chats() {
        return Collections.unmodifiableCollection(chats.values());
    }

    @Override
    public Chat addNewChat(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        var chat = new PersistentChatBuilder()
                .jid(chatJid)
                .build();
        chat.attach(messageStore);
        chats.put(chatJid, chat);
        return chat;
    }

    @Override
    public Optional<Chat> removeChat(JidProvider chatJid) {
        if (chatJid == null) {
            return Optional.empty();
        }
        var targetJid = chatJid.toJid();
        Optional<Chat> removed;
        if (targetJid.hasUserServer()) {
            var jidChat = chats.remove(targetJid);
            removed = jidChat != null
                    ? Optional.of(jidChat)
                    : findLidByPhone(targetJid).map(chats::remove);
        } else if (targetJid.hasLidServer()) {
            var lidChat = chats.remove(targetJid);
            removed = lidChat != null
                    ? Optional.of(lidChat)
                    : findPhoneByLid(targetJid).map(chats::remove);
        } else {
            removed = Optional.ofNullable(chats.remove(targetJid));
        }
        removed.ifPresent(chat -> messageStore.removeChatMessages(chat.jid()));
        return removed;
    }

    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageStore.putStatusMessage(messageInfo);
        return messageInfo;
    }

    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return id == null ? Optional.empty() : messageStore.removeStatusMessage(id);
    }

    @Override
    public Optional<ChatMessageInfo> findStatusById(String id) {
        return id == null ? Optional.empty() : messageStore.getStatusMessage(id);
    }

    @Override
    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }
        Newsletter newsletter = newsletters.get(jid.toJid());
        return Optional.ofNullable(newsletter);
    }

    @Override
    public Collection<Newsletter> newsletters() {
        return Collections.unmodifiableCollection(newsletters.values());
    }

    @Override
    public Newsletter addNewNewsletter(Jid newsletterJid) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var newsletter = new PersistentNewsletterBuilder()
                .jid(newsletterJid)
                .build();
        newsletter.attach(messageStore);
        newsletters.put(newsletter.jid(), newsletter);
        return newsletter;
    }

    @Override
    public Optional<Newsletter> removeNewsletter(JidProvider newsletterJid) {
        if (newsletterJid == null) {
            return Optional.empty();
        }
        Newsletter removed = newsletters.remove(newsletterJid.toJid());
        if (removed != null) {
            messageStore.removeNewsletterMessages(removed.jid());
        }
        return Optional.ofNullable(removed);
    }

    @Override
    public Optional<? extends MessageInfo> findMessageByKey(MessageKey key) {
        var id = key.id();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        var parentJid = key.parentJid();
        if (parentJid.isEmpty()) {
            return Optional.empty();
        }
        return findChatByJid(parentJid.get())
                .flatMap(chat -> chat.getMessageById(id.get()));
    }

    @Override
    public Collection<ChatMessageInfo> status() {
        try (var stream = messageStore.streamStatusMessages()) {
            return stream.toList();
        }
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof PersistentStore that
                            && super.equals(o)
                            && Objects.equals(this.chats, that.chats)
                            && Objects.equals(this.newsletters, that.newsletters);
    }

    @Override
    public int hashCode() {
        var hashCode = super.hashCode();
        for (var entry : chats.entrySet()) {
            var value = entry.getValue();
            if (value != null) {
                hashCode = hashCode * 31 + value.hashCode();
            }
        }
        for (var entry : newsletters.entrySet()) {
            var value = entry.getValue();
            if (value != null) {
                hashCode = hashCode * 31 + value.hashCode();
            }
        }
        return hashCode;
    }

    /**
     * Decodes a {@link PersistentStore} from {@code storeFile} via the
     * generated spec. Used by {@link PersistentStoreFactory}; package
     * private to keep the deserialisation entry point co-located with
     * the spec it depends on.
     *
     * @param storeFile the path to {@code store.proto}
     * @return the decoded store
     * @throws IOException if the file cannot be read or decoded
     */
    static PersistentStore deserialize(Path storeFile) throws IOException {
        try (var stream = Files.newInputStream(storeFile)) {
            return PersistentStoreSpec.decode(ProtobufInputStream.fromStream(stream));
        }
    }
}
