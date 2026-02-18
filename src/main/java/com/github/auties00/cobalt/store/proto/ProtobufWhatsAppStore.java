
package com.github.auties00.cobalt.store.proto;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentity;
import com.github.auties00.cobalt.model.auth.UserAgent.ReleaseChannel;
import com.github.auties00.cobalt.model.auth.Version;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.business.VerifiedBusinessName;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.info.ChatMessageInfo;
import com.github.auties00.cobalt.model.info.MessageInfo;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.ChatMessageKey;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterSpec;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.store.InMemoryWhatsAppStoreSpec;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord;
import com.github.auties00.libsignal.key.*;
import com.github.auties00.libsignal.state.SignalSessionRecord;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNullElseGet;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public final class ProtobufWhatsAppStore implements WhatsAppStore {
    private static final String CHAT_PREFIX = "chat_";
    private static final String NEWSLETTER_PREFIX = "newsletter_";
    private static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("user.home") + "/.cobalt/");
    private static final String DEFAULT_NAME = "User";
    private static final int MAX_DEVICE_LISTS = 5000;

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final UUID uuid;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Long phoneNumber;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    final WhatsAppClientType clientType;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    final long initializationTimeStamp;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    JidDevice device;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    ReleaseChannel releaseChannel;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    boolean online;

    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String locale;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String verifiedName;

    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    URI profilePicture;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    String about;

    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    Jid jid;

    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    Jid lid;

    @ProtobufProperty(index = 17, type = ProtobufType.STRING)
    String businessAddress;

    @ProtobufProperty(index = 18, type = ProtobufType.DOUBLE)
    Double businessLongitude;

    @ProtobufProperty(index = 19, type = ProtobufType.DOUBLE)
    Double businessLatitude;

    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    String businessDescription;

    @ProtobufProperty(index = 21, type = ProtobufType.STRING)
    String businessWebsite;

    @ProtobufProperty(index = 22, type = ProtobufType.STRING)
    String businessEmail;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    BusinessCategory businessCategory;

    @ProtobufProperty(index = 24, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, Contact> contacts;

    @ProtobufProperty(index = 25, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<String, CallOffer> calls;

    @ProtobufProperty(index = 26, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings;

    @ProtobufProperty(index = 27, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.STRING)
    final ConcurrentHashMap<String, String> properties;

    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    boolean unarchiveChats;

    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    boolean twentyFourHourFormat;

    @ProtobufProperty(index = 30, type = ProtobufType.ENUM)
    ChatEphemeralTimer newChatsEphemeralTimer;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    WhatsAppWebClientHistory webHistoryPolicy;

    @ProtobufProperty(index = 32, type = ProtobufType.BOOL)
    boolean automaticPresenceUpdates;

    @ProtobufProperty(index = 33, type = ProtobufType.BOOL)
    boolean automaticMessageReceipts;

    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    boolean checkPatchMacs;

    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    boolean syncedChats;

    @ProtobufProperty(index = 36, type = ProtobufType.BOOL)
    boolean syncedContacts;

    @ProtobufProperty(index = 37, type = ProtobufType.BOOL)
    boolean syncedNewsletters;

    @ProtobufProperty(index = 38, type = ProtobufType.BOOL)
    boolean syncedStatus;

    @ProtobufProperty(index = 39, type = ProtobufType.BOOL)
    boolean syncedWebAppState;

    @ProtobufProperty(index = 40, type = ProtobufType.BOOL)
    boolean syncedBusinessCertificate;

    @ProtobufProperty(index = 41, type = ProtobufType.INT32)
    final Integer registrationId;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    final SignalIdentityKeyPair noiseKeyPair;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    final SignalIdentityKeyPair identityKeyPair;

    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    SignalIdentityKeyPair companionKeyPair;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    SignedDeviceIdentity signedDeviceIdentity;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    final SignalSignedKeyPair signedKeyPair;

    @ProtobufProperty(index = 48, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    final LinkedHashMap<Integer, SignalPreKeyPair> preKeys;

    @ProtobufProperty(index = 49, type = ProtobufType.STRING)
    final UUID fdid;

    @ProtobufProperty(index = 50, type = ProtobufType.BYTES)
    final byte[] deviceId;

    @ProtobufProperty(index = 51, type = ProtobufType.STRING)
    final UUID advertisingId;

    @ProtobufProperty(index = 52, type = ProtobufType.BYTES)
    final byte[] identityId;

    @ProtobufProperty(index = 53, type = ProtobufType.BYTES)
    final byte[] backupToken;

    @ProtobufProperty(index = 54, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys;

    @ProtobufProperty(index = 55, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final LinkedHashMap<String, AppStateSyncKey> appStateKeys;

    @ProtobufProperty(index = 56, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions;

    @ProtobufProperty(index = 57, type = ProtobufType.MESSAGE, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<PatchType, AppStateSyncHash> hashStates;

    @ProtobufProperty(index = 58, type = ProtobufType.BOOL)
    boolean registered;

    @ProtobufProperty(index = 59, type = ProtobufType.BOOL)
    boolean showSecurityNotifications;

    @ProtobufProperty(index = 60, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<String, Sticker> recentStickers;

    @ProtobufProperty(index = 61, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<String, Sticker> favouriteStickers;

    @ProtobufProperty(index = 62, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<String, QuickReply> quickReplies;

    @ProtobufProperty(index = 63, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<Integer, Label> labels;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    volatile Version clientVersion;

    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    Version companionVersion;

    @ProtobufProperty(index = 66, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant lastAdvCheckTime;

    @ProtobufProperty(index = 67, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    final ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities;

    final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange = new ConcurrentHashMap<>();

    final AtomicLong encryptionSequence = new AtomicLong();

    @ProtobufProperty(index = 68, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys;

    @ProtobufProperty(index = 69, type = ProtobufType.BYTES)
    byte[] advSecretKey;

    @ProtobufProperty(index = 70, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.UINT64)
    final ConcurrentMap<String, Long> deviceIdentityRanges;

    @ProtobufProperty(index = 71, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentMap<String, VerifiedBusinessName> verifiedBusinessNames;

    private WhatsAppClientProxy proxy;

    private Path directory;

    final ConcurrentHashMap<Jid, Chat> chats;

    final ConcurrentHashMap<Jid, Newsletter> newsletters;

    final ConcurrentHashMap<String, ChatMessageInfo> status;

    private final ConcurrentMap<UUID, Integer> storesHashCodes;

    private final ConcurrentMap<StoreJidPair, Integer> jidsHashCodes;

    private final ReentrantKeyedLock storeLock;

    private volatile Thread attributionThread;

    private final KeySetView<WhatsAppClientListener, Boolean> listeners;

    private final ConcurrentHashMap<Jid, Jid> lidToPhoneMappings;

    private final ConcurrentHashMap<Jid, Jid> phoneToLidMappings;

    private volatile MediaConnection mediaConnection;

    private final Object mediaConnectionLock = new Object();

    private volatile WhatsAppClientOfflineResumeState offlineResumeState = WhatsAppClientOfflineResumeState.INIT;

    private volatile CountDownLatch offlineDeliveryLatch = new CountDownLatch(1);

    private final KeySetView<Jid, Boolean> usersNeedingSenderKeyRotation = ConcurrentHashMap.newKeySet();

    private final ConcurrentMap<PatchType, SequencedCollection<PendingMutation>> webAppStatePendingMutations;

    private final ConcurrentMap<PatchType, CollectionMetadata> webAppStateCollections;

    private final ConcurrentMap<String, Set<Jid>> pendingMessageRecipients;

    private final Object clientVersionLock;

    private final ConcurrentMap<Jid, ChatMetadata> chatMetadata;

    private final ConcurrentMap<Jid, DeviceList> deviceLists;

    private final LinkedList<Jid> deviceListsAccessOrder;

    private final ConcurrentHashMap<Jid, Long> offlineDeviceTimestamps;

    private final Set<Jid> unconfirmedIdentityChanges;

    private final Set<Jid> coexHostedVerificationCache;

    private final ConcurrentLinkedQueue<PendingDeviceSync> pendingDeviceSyncs;

    private final ConcurrentMap<String, Set<String>> groupSenderKeyDistribution;

    private final KeySetView<String, Boolean> revokedMessageIds = ConcurrentHashMap.newKeySet();

    private boolean serializable;

    ProtobufWhatsAppStore(
            UUID uuid,
            Long phoneNumber,
            WhatsAppClientType clientType,
            long initializationTimeStamp,
            JidDevice device,
            ReleaseChannel releaseChannel,
            boolean online,
            String locale,
            String name,
            String verifiedName,
            URI profilePicture,
            String about,
            Jid jid,
            Jid lid,
            String businessAddress,
            Double businessLongitude,
            Double businessLatitude,
            String businessDescription,
            String businessWebsite,
            String businessEmail,
            BusinessCategory businessCategory,
            ConcurrentHashMap<Jid, Contact> contacts,
            ConcurrentHashMap<String, CallOffer> calls,
            ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings,
            ConcurrentHashMap<String, String> properties,
            boolean unarchiveChats,
            boolean twentyFourHourFormat,
            ChatEphemeralTimer newChatsEphemeralTimer,
            WhatsAppWebClientHistory webHistoryPolicy,
            boolean automaticPresenceUpdates,
            boolean automaticMessageReceipts,
            boolean checkPatchMacs,
            boolean syncedChats,
            boolean syncedContacts,
            boolean syncedNewsletters,
            boolean syncedStatus,
            boolean syncedWebAppState,
            boolean syncedBusinessCertificate,
            Integer registrationId,
            SignalIdentityKeyPair noiseKeyPair,
            SignalIdentityKeyPair identityKeyPair,
            SignalIdentityKeyPair companionKeyPair,
            SignedDeviceIdentity signedDeviceIdentity,
            SignalSignedKeyPair signedKeyPair,
            LinkedHashMap<Integer, SignalPreKeyPair> preKeys,
            UUID fdid,
            byte[] deviceId,
            UUID advertisingId,
            byte[] identityId,
            byte[] backupToken,
            ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys,
            LinkedHashMap<String, AppStateSyncKey> appStateKeys,
            ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions,
            ConcurrentMap<PatchType, AppStateSyncHash> hashStates,
            boolean registered,
            boolean showSecurityNotifications,
            ConcurrentMap<String, Sticker> recentStickers,
            ConcurrentMap<String, Sticker> favouriteStickers,
            ConcurrentMap<String, QuickReply> quickReplies,
            ConcurrentMap<Integer, Label> labels,
            Version clientVersion,
            Version companionVersion,
            Instant lastAdvCheckTime,
            ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities,
            ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys,
            byte[] advSecretKey,
            ConcurrentMap<String, Long> deviceIdentityRanges,
            ConcurrentMap<String, VerifiedBusinessName> verifiedBusinessNames
    ) {
        this.storesHashCodes = new ConcurrentHashMap<>();
        this.jidsHashCodes = new ConcurrentHashMap<>();
        this.storeLock = new ReentrantKeyedLock();
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.phoneNumber = phoneNumber;
        this.clientType = Objects.requireNonNull(clientType, "clientType cannot be null");
        this.online = online;
        this.locale = locale;
        this.name = name;
        this.verifiedName = verifiedName;
        this.businessAddress = businessAddress;
        this.businessLongitude = businessLongitude;
        this.businessLatitude = businessLatitude;
        this.businessDescription = businessDescription;
        this.businessWebsite = businessWebsite;
        this.businessEmail = businessEmail;
        this.businessCategory = businessCategory;
        this.profilePicture = profilePicture;
        this.about = about;
        this.jid = jid;
        this.lid = lid;
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.contacts = Objects.requireNonNull(contacts, "contacts cannot be null");
        this.privacySettings = Objects.requireNonNull(privacySettings, "privacySettings cannot be null");
        this.calls = Objects.requireNonNull(calls, "calls cannot be null");
        this.unarchiveChats = unarchiveChats;
        this.twentyFourHourFormat = twentyFourHourFormat;
        this.initializationTimeStamp = initializationTimeStamp;
        this.newChatsEphemeralTimer = Objects.requireNonNullElse(newChatsEphemeralTimer, ChatEphemeralTimer.OFF);
        this.webHistoryPolicy = webHistoryPolicy;
        this.automaticPresenceUpdates = automaticPresenceUpdates;
        this.automaticMessageReceipts = automaticMessageReceipts;
        this.releaseChannel = Objects.requireNonNullElse(releaseChannel, ReleaseChannel.RELEASE);
        this.device = Objects.requireNonNull(device, "device cannot be null");
        this.checkPatchMacs = checkPatchMacs;
        this.syncedChats = syncedChats;
        this.syncedContacts = syncedContacts;
        this.syncedNewsletters = syncedNewsletters;
        this.syncedStatus = syncedStatus;
        this.syncedWebAppState = syncedWebAppState;
        this.syncedBusinessCertificate = syncedBusinessCertificate;
        this.favouriteStickers = favouriteStickers;
        this.quickReplies = quickReplies;
        this.labels = labels;
        this.chats = new ConcurrentHashMap<>();
        this.newsletters = new ConcurrentHashMap<>();
        this.status = new ConcurrentHashMap<>();
        this.listeners = ConcurrentHashMap.newKeySet();
        this.lidToPhoneMappings = new ConcurrentHashMap<>();
        this.phoneToLidMappings = new ConcurrentHashMap<>();
        for (var contact : contacts.values()) {
            contact.lid()
                    .ifPresent(entry -> registerLidMapping(contact.jid(), entry));
        }
        this.registrationId = Objects.requireNonNullElseGet(registrationId, () -> SecureBytes.nextInt(16380) + 1);
        this.noiseKeyPair = Objects.requireNonNullElseGet(noiseKeyPair, SignalIdentityKeyPair::random);
        this.identityKeyPair = Objects.requireNonNullElseGet(identityKeyPair, SignalIdentityKeyPair::random);
        this.companionKeyPair = companionKeyPair;
        this.signedKeyPair = Objects.requireNonNullElseGet(signedKeyPair, () -> SignalSignedKeyPair.of(this.registrationId, this.identityKeyPair));
        this.preKeys = Objects.requireNonNull(preKeys, "preKeys cannot be null");
        this.fdid = Objects.requireNonNullElseGet(fdid, UUID::randomUUID);
        this.deviceId = Objects.requireNonNullElseGet(deviceId, () -> HexFormat.of().parseHex(UUID.randomUUID().toString().replace("-", "")));
        this.advertisingId = Objects.requireNonNullElseGet(advertisingId, UUID::randomUUID);
        this.identityId = Objects.requireNonNullElseGet(identityId, () -> SecureBytes.random(16));
        this.backupToken = Objects.requireNonNullElseGet(backupToken, () -> SecureBytes.random(20));
        this.signedDeviceIdentity = signedDeviceIdentity;
        this.senderKeys = Objects.requireNonNull(senderKeys, "senderKeys cannot be null");
        this.appStateKeys = Objects.requireNonNull(appStateKeys, "appStateKeys cannot be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.hashStates = Objects.requireNonNull(hashStates, "hashStates cannot be null");
        this.registered = registered;
        this.showSecurityNotifications = showSecurityNotifications;
        this.recentStickers = recentStickers;
        this.clientVersion = clientVersion;
        this.clientVersionLock = new Object();
        this.companionVersion = companionVersion;
        this.webAppStatePendingMutations = new ConcurrentHashMap<>();
        this.webAppStateCollections = new ConcurrentHashMap<>();
        this.pendingMessageRecipients = new ConcurrentHashMap<>();
        this.chatMetadata = new ConcurrentHashMap<>();
        this.deviceLists = new ConcurrentHashMap<>();
        this.deviceListsAccessOrder = new LinkedList<>();
        this.offlineDeviceTimestamps = new ConcurrentHashMap<>();
        this.unconfirmedIdentityChanges = ConcurrentHashMap.newKeySet();
        this.coexHostedVerificationCache = ConcurrentHashMap.newKeySet();
        this.pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
        this.groupSenderKeyDistribution = new ConcurrentHashMap<>();
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.remoteIdentities = Objects.requireNonNullElseGet(remoteIdentities, ConcurrentHashMap::new);
        this.missingSyncKeys = Objects.requireNonNullElseGet(missingSyncKeys, ConcurrentHashMap::new);
        this.advSecretKey = advSecretKey;
        this.deviceIdentityRanges = Objects.requireNonNullElseGet(deviceIdentityRanges, ConcurrentHashMap::new);
        this.verifiedBusinessNames = Objects.requireNonNullElseGet(verifiedBusinessNames, ConcurrentHashMap::new);
    }

    @Override
    public WhatsAppStore save() {
        if (!serializable) {
            return this;
        }
        try {
            storeLock.lock(uuid);
            var oldHashCode = storesHashCodes.getOrDefault(uuid, -1);
            var newHashCode = hashCode();
            if (oldHashCode == newHashCode) {
                return this;
            }
            storesHashCodes.put(uuid, newHashCode);
            try (var executor = newVirtualThreadPerTaskExecutor()) {
                executor.submit(this::serializeStore);
                chats().forEach(chat -> executor.submit(() -> serializeChat(chat)));
                newsletters().forEach(newsletter -> executor.submit(() -> serializeNewsletter(newsletter)));
            }
        } finally {
            storeLock.unlock(uuid);
        }
        return this;
    }

    @Override
    public void delete() throws IOException {
        if (!serializable) {
            return;
        }

        var folderPath = getSessionDirectory(clientType, directory, uuid.toString());
        ProtobufWhatsAppStorePathUtils.deleteRecursively(folderPath);
    }

    @Override
    public void await() {
        var thread = attributionThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cannot finish deserializing store", exception);
        }
    }

    private void startBackgroundDeserialization() {
        attributionThread = Thread.startVirtualThread(this::deserializeChatsAndNewsletters);
    }

    private void deserializeChatsAndNewsletters() {
        var sessionDir = getSessionDirectory(clientType, directory, uuid.toString());
        try (var walker = Files.walk(sessionDir); var executor = newVirtualThreadPerTaskExecutor()) {
            walker.forEach(path -> executor.submit(() -> deserializeChatOrNewsletter(path)));
        }
    }

    private void deserializeChatOrNewsletter(Path path) {
        try {
            var fileName = path.getFileName().toString();
            if (fileName.startsWith(CHAT_PREFIX)) {
                deserializeChat(path);
            } else if (fileName.startsWith(NEWSLETTER_PREFIX)) {
                deserializeNewsletter(path);
            }
        } catch (Throwable throwable) {
            handleSerializeError(path, throwable);
        }
    }

    private void deserializeChat(Path chatFile) throws IOException {
        try (var stream = Files.newInputStream(chatFile)) {
            var chat = ChatSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, chat.jid());
            jidsHashCodes.put(storeJidPair, chat.hashCode());
            for (var message : chat.messages()) {
                message.setChat(chat);
                findContactByJid(message.senderJid())
                        .ifPresent(message::setSender);
            }
            addChat(chat);
        }
    }

    private void deserializeNewsletter(Path newsletterFile) throws IOException {
        try (var stream = Files.newInputStream(newsletterFile)) {
            var newsletter = NewsletterSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, newsletter.jid());
            jidsHashCodes.put(storeJidPair, newsletter.hashCode());
            for (var message : newsletter.messages()) {
                message.setNewsletter(newsletter);
            }
            addNewsletter(newsletter);
        }
    }

    private void serializeStore() {
        try {
            var path = getSessionFile(clientType, directory, uuid.toString(), "store.proto");
            Files.createDirectories(path.getParent());
            var tempFile = Files.createTempFile(path.getFileName().toString(), ".tmp");
            try (var stream = Files.newOutputStream(tempFile)) {
                InMemoryWhatsAppStoreSpec.encode(this, ProtobufOutputStream.toStream(stream));
            }
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void serializeChat(Chat chat) {
        var outputFile = getMessagesContainerPathIfUpdated(chat.jid(), chat.hashCode(), CHAT_PREFIX);
        if (outputFile == null) {
            return;
        }
        try {
            var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
            try (var stream = Files.newOutputStream(tempFile)) {
                ChatSpec.encode(chat, ProtobufOutputStream.toStream(stream));
            }
            Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable throwable) {
            handleSerializeError(outputFile, throwable);
        }
    }

    private void serializeNewsletter(Newsletter newsletter) {
        var outputFile = getMessagesContainerPathIfUpdated(newsletter.jid(), newsletter.hashCode(), NEWSLETTER_PREFIX);
        if (outputFile == null) {
            return;
        }
        try {
            var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
            try (var stream = Files.newOutputStream(tempFile)) {
                NewsletterSpec.encode(newsletter, ProtobufOutputStream.toStream(stream));
            }
            Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable throwable) {
            handleSerializeError(outputFile, throwable);
        }
    }

    private Path getMessagesContainerPathIfUpdated(Jid jid, int hashCode, String filePrefix) {
        var identifier = new StoreJidPair(uuid, jid);
        var oldHashCode = jidsHashCodes.getOrDefault(identifier, -1);
        if (oldHashCode == hashCode) {
            return null;
        }
        jidsHashCodes.put(identifier, hashCode);
        var fileName = filePrefix + jid.user() + ".proto";
        return getSessionFile(clientType, directory, uuid.toString(), fileName);
    }

    private void handleSerializeError(Path path, Throwable error) {
        var logger = System.getLogger("FileSerializer - " + path);
        logger.log(System.Logger.Level.ERROR, error);
    }

    private static Path getHome(WhatsAppClientType type, Path baseDirectory) {
        return baseDirectory.resolve(type == WhatsAppClientType.MOBILE ? "mobile" : "web");
    }

    private static Path getSessionDirectory(WhatsAppClientType clientType, Path baseDirectory, String path) {
        try {
            var result = getHome(clientType, baseDirectory).resolve(path);
            Files.createDirectories(result.getParent());
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path getSessionFile(WhatsAppClientType clientType, Path baseDirectory, String uuid, String fileName) {
        try {
            var result = getSessionDirectory(clientType, baseDirectory, uuid).resolve(fileName);
            Files.createDirectories(result.getParent());
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create directory", exception);
        }
    }

    private record StoreJidPair(UUID storeId, Jid jid) {
    }

    private static final class ReentrantKeyedLock {
        private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

        void lock(UUID key) {
            var lockWrapper = locks.compute(key, (ignored, value) -> requireNonNullElseGet(value, () -> new ReentrantLock(true)));
            lockWrapper.lock();
        }

        void unlock(UUID key) {
            var lockWrapper = locks.get(key);
            if (lockWrapper == null || !lockWrapper.isHeldByCurrentThread()) {
                throw new IllegalStateException("The lock for the key %s doesn't exist or is not held by the current thread".formatted(key));
            }
            lockWrapper.unlock();
        }
    }

    public Optional<Contact> findContactByJid(JidProvider jid) {
        return switch (jid) {
            case Contact contact -> Optional.of(contact);
            case null -> Optional.empty();
            case Chat _, Newsletter _, Jid _, JidServer _-> {
                var targetJid = jid.toJid();
                if(targetJid.hasUserServer()) {
                    var jidContact = contacts.get(targetJid);
                    if(jidContact != null) {
                        yield Optional.of(jidContact);
                    } else {
                        yield findLidByPhone(targetJid)
                                .map(contacts::get);
                    }
                } else if(targetJid.hasLidServer()) {
                    var lidContact = contacts.get(targetJid);
                    if(lidContact != null) {
                        yield Optional.of(lidContact);
                    } else {
                        yield findPhoneByLid(targetJid)
                                .map(contacts::get);
                    }
                } else {
                    var contact = contacts.get(targetJid);
                    yield Optional.ofNullable(contact);
                }
            }
        };
    }

    public Collection<Contact> contacts() {
        return Collections.unmodifiableCollection(contacts.values());
    }

    public Contact addNewContact(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        var newContact = new ContactBuilder()
                .jid(jid)
                .lid(phoneToLidMappings.get(jid))
                .build();
        return addContact(newContact);
    }

    public Contact addContact(Contact contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        contacts.put(contact.jid(), contact);
        return contact;
    }

    public Optional<Contact> removeContact(JidProvider contactJid) {
        if(contactJid == null) {
            return Optional.empty();
        } else {
            var targetJid = jid.toJid();
            if(targetJid.hasUserServer()) {
                var jidContact = contacts.remove(targetJid);
                if(jidContact != null) {
                    return Optional.of(jidContact);
                } else {
                    return findLidByPhone(targetJid)
                            .map(contacts::remove);
                }
            } else if(targetJid.hasLidServer()) {
                var lidContact = contacts.remove(targetJid);
                if(lidContact != null) {
                    return Optional.of(lidContact);
                } else {
                    return findPhoneByLid(targetJid)
                            .map(contacts::remove);
                }
            } else {
                var chat = contacts.remove(targetJid);
                return Optional.ofNullable(chat);
            }
        }
    }

    public void registerLidMapping(Jid phoneJid, Jid lidJid) {
        if (phoneJid == null || lidJid == null) {
            return;
        }
        var normalizedPhone = phoneJid.withoutData();
        var normalizedLid = lidJid.withoutData();
        lidToPhoneMappings.put(normalizedLid, normalizedPhone);
        phoneToLidMappings.put(normalizedPhone, normalizedLid);
    }

    public Optional<Jid> findPhoneByLid(Jid lidJid) {
        return lidJid == null ? Optional.empty() : Optional.ofNullable(lidToPhoneMappings.get(lidJid.withoutData()));
    }

    public Optional<Jid> findLidByPhone(Jid phoneJid) {
        if (phoneJid == null) {
            return Optional.empty();
        } else {
            var localJid = jid;
            if(localJid != null && Objects.equals(phoneJid.user(), localJid.user())) {
                return Optional.ofNullable(lid)
                        .map(lid -> lid.withDevice(phoneJid.device()));
            } else {
                var result = phoneToLidMappings.get(phoneJid.withoutData());
                return Optional.ofNullable(result);
            }
        }
    }

    public Optional<Chat> findChatByJid(JidProvider jid) {
        return switch (jid) {
            case null -> Optional.empty();
            case Chat chat -> Optional.of(chat);
            case Contact _, Newsletter _, Jid _, JidServer _-> {
                var targetJid = jid.toJid();
                if(targetJid.hasUserServer()) {
                    var jidChat = chats.get(targetJid);
                    if(jidChat != null) {
                        yield Optional.of(jidChat);
                    } else {
                        yield findLidByPhone(targetJid)
                                .map(chats::get);
                    }
                } else if(targetJid.hasLidServer()) {
                    var lidChat = chats.get(targetJid);
                    if(lidChat != null) {
                        yield Optional.of(lidChat);
                    } else {
                        yield findPhoneByLid(targetJid)
                                .map(chats::get);
                    }
                } else {
                    var chat = chats.get(targetJid);
                    yield Optional.ofNullable(chat);
                }
            }
        };
    }

    public Optional<? extends MessageInfo> findMessageById(JidProvider provider, String id) {
        return provider == null || id == null ? Optional.empty() : switch (provider) {
            case Chat chat -> findMessageById(chat, id);
            case Newsletter newsletter -> findMessageById(newsletter, id);
            case Contact contact -> findChatByJid(contact.jid())
                    .flatMap(chat -> findMessageById(chat, id));
            case Jid contactJid -> {
                if (contactJid.server().type() == JidServer.Type.NEWSLETTER) {
                    yield findNewsletterByJid(contactJid)
                            .flatMap(newsletter -> findMessageById(newsletter, id));
                } else if (Jid.statusBroadcastAccount().equals(contactJid)) {
                    yield Optional.ofNullable(status.get(id));
                } else {
                    yield findChatByJid(contactJid)
                            .flatMap(chat -> findMessageById(chat, id));
                }
            }
            case JidServer jidServer -> findChatByJid(jidServer.toJid())
                    .flatMap(chat -> findMessageById(chat, id));
        };
    }

    public Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id) {
        return newsletter == null || id == null ? Optional.empty() : newsletter.messages()
                .parallelStream()
                .filter(entry -> Objects.equals(id, entry.id()) || Objects.equals(id, String.valueOf(entry.serverId())))
                .findFirst();
    }


    public Optional<ChatMessageInfo> findMessageById(Chat chat, String id) {
        return chat == null || id == null ? Optional.empty() : chat.messages()
                .parallelStream()
                .filter(message -> Objects.equals(message.key().id(), id))
                .findAny();
    }

    public Collection<Chat> chats() {
        return Collections.unmodifiableCollection(chats.values());
    }

    public Chat addChat(Chat chat) {
        Objects.requireNonNull(chat, "chat cannot be null");
        chats.put(chat.jid(), chat);
        return chat;
    }

    public Chat addNewChat(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        var chat = new ChatBuilder()
                .jid(chatJid)
                .build();
        addChat(chat);
        return chat;
    }

    public Optional<Chat> removeChat(JidProvider chatJid) {
        if(chatJid == null) {
            return Optional.empty();
        } else {
            var targetJid = jid.toJid();
            if(targetJid.hasUserServer()) {
                var jidChat = chats.remove(targetJid);
                if(jidChat != null) {
                    return Optional.of(jidChat);
                } else {
                    return findLidByPhone(targetJid)
                            .map(chats::remove);
                }
            } else if(targetJid.hasLidServer()) {
                var lidChat = chats.remove(targetJid);
                if(lidChat != null) {
                    return Optional.of(lidChat);
                } else {
                    return findPhoneByLid(targetJid)
                            .map(chats::remove);
                }
            } else {
                var chat = chats.remove(targetJid);
                return Optional.ofNullable(chat);
            }
        }
    }

    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        status.put(messageInfo.key().id(), messageInfo);
        return messageInfo;
    }

    public Optional<ChatMessageInfo> removeStatus(String id) {
        return Optional.ofNullable(status.remove(id));
    }

    public Optional<CallOffer> findCallById(String callId) {
        return callId == null
                ? Optional.empty()
                : Optional.ofNullable(calls.get(callId));
    }

    public CallOffer addCall(CallOffer call) {
        Objects.requireNonNull(call, "call cannot be null");
        calls.put(call.id(), call);
        return call;
    }

    public Optional<CallOffer> removeCall(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(calls.remove(id));
    }

    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        return jid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.get(jid.toJid()));
    }

    public Collection<Newsletter> newsletters() {
        return Collections.unmodifiableCollection(newsletters.values());
    }

    public Newsletter addNewsletter(Newsletter newsletter) {
        newsletters.put(newsletter.jid(), newsletter);
        return newsletter;
    }

    public Newsletter addNewNewsletter(Jid newsletterJid) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var newsletter = new NewsletterBuilder()
                .jid(newsletterJid)
                .build();
        addNewsletter(newsletter);
        return newsletter;
    }

    public Optional<Newsletter> removeNewsletter(JidProvider newsletterJid) {
        return newsletterJid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.remove(newsletterJid.toJid()));
    }

    public UUID uuid() {
        return uuid;
    }

    public OptionalLong phoneNumber() {
        return phoneNumber == null ? OptionalLong.empty() : OptionalLong.of(phoneNumber);
    }

    public WhatsAppStore setPhoneNumber(Long phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    public WhatsAppClientType clientType() {
        return clientType;
    }

    public Optional<WhatsAppClientProxy> proxy() {
        return Optional.ofNullable(proxy);
    }

    public WhatsAppStore setProxy(WhatsAppClientProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public JidDevice device() {
        return device;
    }

    public WhatsAppStore setDevice(JidDevice device) {
        this.device = Objects.requireNonNull(device, "device cannot be null");
        return this;
    }

    public ReleaseChannel releaseChannel() {
        return releaseChannel;
    }

    public WhatsAppStore setReleaseChannel(ReleaseChannel releaseChannel) {
        this.releaseChannel = Objects.requireNonNull(releaseChannel, "releaseChannel cannot be null");
        return this;
    }

    public boolean online() {
        return online;
    }

    public WhatsAppStore setOnline(boolean online) {
        this.online = online;
        return this;
    }

    public Optional<String> locale() {
        return Optional.ofNullable(locale);
    }

    public WhatsAppStore setLocale(String locale) {
        this.locale = locale;
        return this;
    }

    public String name() {
        return Objects.requireNonNullElse(name, DEFAULT_NAME);
    }

    public WhatsAppStore setName(String name) {
        this.name = name;
        return this;
    }

    public Optional<String> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }

    public WhatsAppStore setVerifiedName(String verifiedName) {
        this.verifiedName = verifiedName;
        return this;
    }

    public Optional<URI> profilePicture() {
        return Optional.ofNullable(profilePicture);
    }

    public WhatsAppStore setProfilePicture(URI profilePicture) {
        this.profilePicture = profilePicture;
        return this;
    }

    public Optional<String> about() {
        return Optional.ofNullable(about);
    }

    public WhatsAppStore setAbout(String about) {
        this.about = about;
        return this;
    }

    public Optional<Jid> jid() {
        return Optional.ofNullable(jid);
    }

    public WhatsAppStore setJid(Jid jid) {
        this.jid = jid;
        return this;
    }

    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    public WhatsAppStore setLid(Jid lid) {
        this.lid = lid;
        return this;
    }

    public Optional<String> businessAddress() {
        return Optional.ofNullable(businessAddress);
    }

    public WhatsAppStore setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
        return this;
    }

    public OptionalDouble businessLongitude() {
        return businessLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLongitude);
    }

    public WhatsAppStore setBusinessLongitude(Double businessLongitude) {
        this.businessLongitude = businessLongitude;
        return this;
    }

    public OptionalDouble businessLatitude() {
        return businessLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLatitude);
    }

    public WhatsAppStore setBusinessLatitude(Double businessLatitude) {
        this.businessLatitude = businessLatitude;
        return this;
    }

    public Optional<String> businessDescription() {
        return Optional.ofNullable(businessDescription);
    }

    public WhatsAppStore setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
        return this;
    }

    public Optional<String> businessWebsite() {
        return Optional.ofNullable(businessWebsite);
    }

    public WhatsAppStore setBusinessWebsite(String businessWebsite) {
        this.businessWebsite = businessWebsite;
        return this;
    }

    public Optional<String> businessEmail() {
        return Optional.ofNullable(businessEmail);
    }

    public WhatsAppStore setBusinessEmail(String businessEmail) {
        this.businessEmail = businessEmail;
        return this;
    }

    public Optional<BusinessCategory> businessCategory() {
        return Optional.ofNullable(businessCategory);
    }

    public WhatsAppStore setBusinessCategory(BusinessCategory businessCategory) {
        this.businessCategory = businessCategory;
        return this;
    }

    public Map<String, String> properties() {
        return Collections.unmodifiableMap(properties);
    }

    public Collection<ChatMessageInfo> status() {
        return Collections.unmodifiableCollection(status.values());
    }

    public Collection<CallOffer> calls() {
        return Collections.unmodifiableCollection(calls.values());
    }

    public Collection<PrivacySettingEntry> privacySettings() {
        return Collections.unmodifiableCollection(privacySettings.values());
    }

    public boolean unarchiveChats() {
        return unarchiveChats;
    }

    public WhatsAppStore setUnarchiveChats(boolean unarchiveChats) {
        this.unarchiveChats = unarchiveChats;
        return this;
    }

    public boolean twentyFourHourFormat() {
        return twentyFourHourFormat;
    }

    public WhatsAppStore setTwentyFourHourFormat(boolean twentyFourHourFormat) {
        this.twentyFourHourFormat = twentyFourHourFormat;
        return this;
    }

    public long initializationTimeStamp() {
        return initializationTimeStamp;
    }

    public ChatEphemeralTimer newChatsEphemeralTimer() {
        return newChatsEphemeralTimer;
    }

    public WhatsAppStore setNewChatsEphemeralTimer(ChatEphemeralTimer newChatsEphemeralTimer) {
        this.newChatsEphemeralTimer = Objects.requireNonNull(newChatsEphemeralTimer, "newChatsEphemeralTimer cannot be null");
        return this;
    }

    public Optional<WhatsAppWebClientHistory> webHistoryPolicy() {
        return Optional.ofNullable(webHistoryPolicy);
    }

    public WhatsAppStore setWebHistoryPolicy(WhatsAppWebClientHistory webHistoryPolicy) {
        this.webHistoryPolicy = webHistoryPolicy;
        return this;
    }

    public boolean automaticPresenceUpdates() {
        return automaticPresenceUpdates;
    }

    public WhatsAppStore setAutomaticPresenceUpdates(boolean automaticPresenceUpdates) {
        this.automaticPresenceUpdates = automaticPresenceUpdates;
        return this;
    }

    public boolean automaticMessageReceipts() {
        return automaticMessageReceipts;
    }

    public WhatsAppStore setAutomaticMessageReceipts(boolean automaticMessageReceipts) {
        this.automaticMessageReceipts = automaticMessageReceipts;
        return this;
    }

    public boolean checkPatchMacs() {
        return checkPatchMacs;
    }

    public WhatsAppStore setCheckPatchMacs(boolean checkPatchMacs) {
        this.checkPatchMacs = checkPatchMacs;
        return this;
    }

    public boolean syncedChats() {
        return syncedChats;
    }

    public WhatsAppStore setSyncedChats(boolean syncedChats) {
        this.syncedChats = syncedChats;
        return this;
    }

    public boolean syncedContacts() {
        return syncedContacts;
    }

    public WhatsAppStore setSyncedContacts(boolean syncedContacts) {
        this.syncedContacts = syncedContacts;
        return this;
    }

    public boolean syncedNewsletters() {
        return syncedNewsletters;
    }

    public WhatsAppStore setSyncedNewsletters(boolean syncedNewsletters) {
        this.syncedNewsletters = syncedNewsletters;
        return this;
    }

    public boolean syncedStatus() {
        return syncedStatus;
    }

    public WhatsAppStore setSyncedStatus(boolean syncedStatus) {
        this.syncedStatus = syncedStatus;
        return this;
    }

    public boolean syncedWebAppState() {
        return syncedWebAppState;
    }

    public WhatsAppStore setSyncedWebAppState(boolean syncedWebAppState) {
        this.syncedWebAppState = syncedWebAppState;
        return this;
    }

    public boolean syncedBusinessCertificate() {
        return this.syncedBusinessCertificate;
    }
    public WhatsAppStore setSyncedBusinessCertificate(boolean syncedBusinessCertificate) {
        this.syncedBusinessCertificate = syncedBusinessCertificate;
        return this;
    }

    public WhatsAppClientListener addListener(WhatsAppClientListener listener) {
        listeners.add(listener);
        return listener;
    }

    public boolean removeListener(WhatsAppClientListener listener) {
        return listeners.remove(listener);
    }

    public Collection<WhatsAppClientListener> listeners() {
        return Collections.unmodifiableCollection(listeners);
    }


    public MediaConnection awaitMediaConnection() throws InterruptedException {
        if(mediaConnection == null) {
            synchronized (mediaConnectionLock) {
                if(mediaConnection == null) {
                    mediaConnectionLock.wait();
                }
            }
        }
        return mediaConnection;
    }

    public WhatsAppStore setMediaConnection(MediaConnection mediaConnection) {
        this.mediaConnection = mediaConnection;
        synchronized (mediaConnectionLock) {
            this.mediaConnectionLock.notifyAll();
        }
        return this;
    }

    public WhatsAppClientOfflineResumeState offlineResumeState() {
        return offlineResumeState;
    }

    public WhatsAppStore setOfflineResumeState(WhatsAppClientOfflineResumeState state) {
        this.offlineResumeState = Objects.requireNonNull(state, "state cannot be null");
        if (state == WhatsAppClientOfflineResumeState.COMPLETE) {
            offlineDeliveryLatch.countDown();
        } else if (state == WhatsAppClientOfflineResumeState.INIT) {
            // Reset latch on reconnect
            offlineDeliveryLatch = new CountDownLatch(1);
        }
        return this;
    }

    public void waitForOfflineDeliveryEnd() {
        if (offlineResumeState == WhatsAppClientOfflineResumeState.COMPLETE) {
            return;
        }
        try {
            offlineDeliveryLatch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isResumeFromRestartComplete() {
        return offlineResumeState != WhatsAppClientOfflineResumeState.INIT
               && offlineResumeState != WhatsAppClientOfflineResumeState.RESUME_ON_RESTART;
    }

    public Optional<SignedDeviceIdentity> signedDeviceIdentity() {
        return Optional.ofNullable(signedDeviceIdentity);
    }

    public WhatsAppStore setSignedDeviceIdentity(SignedDeviceIdentity signedDeviceIdentity) {
        this.signedDeviceIdentity = signedDeviceIdentity;
        return this;
    }

    public SignalSignedKeyPair signedKeyPair() {
        return signedKeyPair;
    }

    public UUID fdid() {
        return fdid;
    }

    public byte[] deviceId() {
        return deviceId;
    }

    public UUID advertisingId() {
        return advertisingId;
    }

    public byte[] identityId() {
        return identityId;
    }

    public byte[] backupToken() {
        return backupToken;
    }

    public int registrationId() {
        return this.registrationId;
    }

    public SignalIdentityKeyPair noiseKeyPair() {
        return this.noiseKeyPair;
    }

    public SignalIdentityKeyPair identityKeyPair() {
        return this.identityKeyPair;
    }

    public Optional<SignalIdentityKeyPair> companionKeyPair() {
        return Optional.ofNullable(companionKeyPair);
    }

    public WhatsAppStore setCompanionKeyPair(SignalIdentityKeyPair companionKeyPair) {
        this.companionKeyPair = companionKeyPair;
        return this;
    }

    public Optional<byte[]> advSecretKey() {
        return Optional.ofNullable(advSecretKey);
    }

    public WhatsAppStore setAdvSecretKey(byte[] advSecretKey) {
        this.advSecretKey = advSecretKey;
        return this;
    }

    public byte[] generateAdvSecretKey() {
        this.advSecretKey = SecureBytes.random(32);
        return this.advSecretKey;
    }

    public WhatsAppStore clearAdvSecretKey() {
        this.advSecretKey = null;
        return this;
    }

    public SequencedCollection<SignalPreKeyPair> preKeys() {
        return preKeys.sequencedValues();
    }

    public boolean hasPreKeys() {
        return !preKeys.isEmpty();
    }

    public Optional<SignalPreKeyPair> findPreKeyById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(preKeys.get(id));
    }

    @Override
    public void addPreKey(SignalPreKeyPair preKey) {
        Objects.requireNonNull(preKey, "preKey cannot be null");
        preKeys.put(preKey.id(), preKey);
    }

    @Override
    public boolean removePreKey(int id) {
        return preKeys.remove(id) != null;
    }

    @Override
    public Optional<SignalSignedKeyPair> findSignedPreKeyById(Integer id) {
        return id == signedKeyPair.id() ? Optional.of(signedKeyPair) : Optional.empty();
    }

    @Override
    public void addSignedPreKey(SignalSignedKeyPair signalSignedKeyPair) {
        throw new UnsupportedOperationException("Cannot add signed pre keys to a Keys instance");
    }

    @Override
    public Optional<SignalSessionRecord> findSessionByAddress(SignalProtocolAddress address) {
        return Optional.ofNullable(sessions.get(address));
    }

    public void addSession(SignalProtocolAddress address, SignalSessionRecord record) {
        sessions.put(address, record);
    }

    @Override
    public Optional<SignalSenderKeyRecord> findSenderKeyByName(SignalSenderKeyName name) {
        return Optional.ofNullable(senderKeys.get(name));
    }

    @Override
    public void addSenderKey(SignalSenderKeyName name, SignalSenderKeyRecord newRecord) {
        senderKeys.put(name, newRecord);
    }

    public boolean removeSession(SignalProtocolAddress address) {
        return sessions.remove(address) != null;
    }

    public void removeSenderKeysForDevice(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        senderKeys.keySet().removeIf(name ->
                name.sender().equals(signalAddress)
        );
    }


    public void removeSenderKeysForDevice(SignalSenderKeyName senderKeyName) {
        Objects.requireNonNull(senderKeyName, "senderKeyName cannot be null");
        senderKeys.remove(senderKeyName);
    }

    public void cleanupSignalSessionsForDevice(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        removeSession(signalAddress);
        removeSenderKeysForDevice(deviceJid);
    }

    public void markUserNeedsSenderKeyRotation(Jid userJid) {
        usersNeedingSenderKeyRotation.add(userJid.toUserJid());
    }

    public boolean checkAndClearSenderKeyRotationNeeded(Jid userJid) {
        return usersNeedingSenderKeyRotation.remove(userJid.toUserJid());
    }

    public boolean anyUserNeedsSenderKeyRotation(Collection<Jid> userJids) {
        return userJids.stream()
                .map(Jid::toUserJid)
                .anyMatch(usersNeedingSenderKeyRotation::contains);
    }

    public void markSenderKeyDistributed(Jid groupJid, Jid participantJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");

        var groupKey = groupJid.toString();
        groupSenderKeyDistribution
                .computeIfAbsent(groupKey, k -> ConcurrentHashMap.newKeySet())
                .add(participantJid.toString());
    }

    public boolean hasSenderKeyDistributed(Jid groupJid, Jid participantJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");

        var groupKey = groupJid.toString();
        var participants = groupSenderKeyDistribution.get(groupKey);
        if (participants == null) {
            return false;
        }
        return participants.contains(participantJid.toString());
    }

    public void clearSenderKeyDistribution(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        groupSenderKeyDistribution.remove(groupJid.toString());
    }

    public void clearSenderKeyDistributionForParticipant(Jid participantJid) {
        Objects.requireNonNull(participantJid, "participantJid cannot be null");
        var participantKey = participantJid.toString();

        for (var participants : groupSenderKeyDistribution.values()) {
            participants.remove(participantKey);
        }
    }

    public long updateIdentityRange(Collection<Jid> devices) {
        var seq = encryptionSequence.incrementAndGet();
        for (var device : devices) {
            var address = device.toSignalAddress();
            // WAWebSignalProtocolStoreUnifiedApi: only set if null or > seq
            // (i.e. record the *earliest* sequence for this identity)
            identityEncryptionRange.merge(address, seq, Math::min);
        }
        return seq;
    }

    public boolean hasIdentityChanged(long sendSequence, Jid device) {
        var recorded = identityEncryptionRange.get(device.toSignalAddress());
        return recorded == null || recorded > sendSequence;
    }

    public void clearIdentityRange(Jid device) {
        identityEncryptionRange.remove(device.toSignalAddress());
    }

    public void forgetSenderKeyDistributed(Jid groupJid, Jid participantJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");

        var groupKey = groupJid.toString();
        var participants = groupSenderKeyDistribution.get(groupKey);
        if (participants != null) {
            participants.remove(participantJid.toString());
        }
    }

    public void markMessageAsRevoked(String messageId) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        revokedMessageIds.add(messageId);
    }

    public boolean isMessageOverwrittenByRevoke(String messageId) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        return revokedMessageIds.contains(messageId);
    }

    public void clearRevokeStatus(String messageId) {
        Objects.requireNonNull(messageId, "messageId cannot be null");
        revokedMessageIds.remove(messageId);
    }

    public SequencedCollection<AppStateSyncKey> appStateKeys() {
        return Collections.unmodifiableSequencedCollection(appStateKeys.sequencedValues());
    }

    public Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id) {
        return Optional.ofNullable(appStateKeys.get(HexFormat.of().formatHex(id)));
    }

    public void addWebAppStateKeys(Collection<AppStateSyncKey> keys) {
        for (var key : keys) {
            var keyId = key.keyId();
            if(keyId == null) {
                continue;
            }

            var keyIdValue = keyId.value();
            if(keyIdValue == null) {
                continue;
            }

            appStateKeys.put(HexFormat.of().formatHex(keyIdValue), key);
        }
    }

    public Optional<AppStateSyncHash> findWebAppHashStateByName(PatchType patchType) {
        return Optional.ofNullable(hashStates.get(patchType));
    }

    public void addWebAppHashState(AppStateSyncHash state) {
        hashStates.put(state.type(), state);
    }

    public Collection<MissingDeviceSyncKey> missingSyncKeys() {
        return Collections.unmodifiableCollection(missingSyncKeys.values());
    }

    public Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId) {
        return Optional.ofNullable(missingSyncKeys.get(HexFormat.of().formatHex(keyId)));
    }

    public void addMissingSyncKey(MissingDeviceSyncKey missingKey) {
        missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
    }

    public void removeMissingSyncKey(byte[] keyId) {
        missingSyncKeys.remove(HexFormat.of().formatHex(keyId));
    }

    public SequencedCollection<MissingDeviceSyncKey> findExpiredMissingSyncKeys(Duration timeout) {
        var now = Instant.now();
        return missingSyncKeys.values()
                .stream()
                .filter(key -> Duration.between(key.timestamp(), now).compareTo(timeout) > 0)
                .toList();
    }

    public Optional<Instant> getEarliestMissingSyncKeyTimestamp() {
        return missingSyncKeys.values().stream()
                .map(MissingDeviceSyncKey::timestamp)
                .min(Instant::compareTo);
    }

    public Optional<Duration> calculateMissingSyncKeyTimeoutDelay(Duration timeout) {
        return getEarliestMissingSyncKeyTimestamp()
                .map(earliest -> {
                    var elapsed = Duration.between(earliest, Instant.now());
                    var remaining = timeout.minus(elapsed);
                    return remaining.isNegative() ? Duration.ZERO : remaining;
                });
    }

    public void updateDeviceIdentityRange(String signalAddress, long messageTimestamp) {
        deviceIdentityRanges.compute(signalAddress, (k, existing) ->
                (existing == null || existing > messageTimestamp) ? messageTimestamp : existing);
    }

    public Long getDeviceIdentityRange(String signalAddress) {
        return deviceIdentityRanges.get(signalAddress);
    }

    public boolean shouldIncludeDeviceInResend(String signalAddress, long originalTimestamp) {
        var rangeTimestamp = deviceIdentityRanges.get(signalAddress);
        // Include device if no range recorded OR range is <= original message
        return rangeTimestamp == null || rangeTimestamp <= originalTimestamp;
    }

    public void createOrMergeReceiptRecords(String messageId, Collection<Jid> recipientJids) {
        if (messageId == null || recipientJids == null || recipientJids.isEmpty()) {
            return;
        }
        pendingMessageRecipients.compute(messageId, (k, existing) -> {
            var set = existing != null ? existing : ConcurrentHashMap.<Jid>newKeySet();
            set.addAll(recipientJids);
            return set;
        });
    }

    public Set<Jid> findMessageRecipients(String messageId) {
        var recipients = pendingMessageRecipients.get(messageId);
        return recipients != null ? Collections.unmodifiableSet(recipients) : Set.of();
    }

    public void removeReceiptRecords(String messageId) {
        pendingMessageRecipients.remove(messageId);
    }

    public Set<Jid> findReceiptRecords(String messageId) {
        if (messageId == null) {
            return Set.of();
        }
        var recipients = pendingMessageRecipients.get(messageId);
        return recipients != null ? Set.copyOf(recipients) : Set.of();
    }

    public void addPendingMutations(PatchType collectionName, Collection<? extends PendingMutation> patch) {
        webAppStatePendingMutations
                .computeIfAbsent(collectionName, k -> new ArrayList<>())
                .addAll(patch);
    }

    public SequencedCollection<PendingMutation> findPendingMutations(PatchType collectionName) {
        var collectionPending = webAppStatePendingMutations.get(collectionName);
        return collectionPending == null ? List.of() : Collections.unmodifiableSequencedCollection(collectionPending);
    }

    public void removePendingMutations(PatchType collectionName) {
        webAppStatePendingMutations.remove(collectionName);
    }

    public void clearPendingMutations(PatchType collectionName) {
        webAppStatePendingMutations.remove(collectionName);
    }

    public boolean registered() {
        return this.registered;
    }

    public WhatsAppStore setRegistered(boolean registered) {
        this.registered = registered;
        return this;
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress signalProtocolAddress, SignalIdentityPublicKey signalIdentityPublicKey, SignalKeyDirection signalKeyDirection) {
        return true;
    }

    @Override
    public void addTrustedIdentity(SignalProtocolAddress signalProtocolAddress, SignalIdentityPublicKey signalIdentityPublicKey) {

    }

    public void saveIdentity(SignalProtocolAddress address, SignalIdentityPublicKey identityKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(identityKey, "identityKey cannot be null");
        remoteIdentities.put(address, identityKey);
    }

    public Optional<SignalIdentityPublicKey> findIdentityByAddress(SignalProtocolAddress address) {
        if (address == null) {
            return Optional.empty();
        } else {
            var localJid = jid;
            if (localJid != null && address.equals(localJid.toSignalAddress())) {
                return Optional.of(identityKeyPair.publicKey());
            } else {
                return Optional.ofNullable(remoteIdentities.get(address));
            }
        }
    }

    public void markWebAppStateDirty(PatchType collectionName) {
        webAppStateCollections.compute(collectionName, (_, current) -> {
            if (current == null || current.state() == CollectionState.UP_TO_DATE) {
                return new CollectionMetadata(
                        collectionName,
                        current != null ? current.version() : 0,
                        current != null ? MutationLTHash.copy(current.ltHash()) : MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        System.currentTimeMillis(),
                        CollectionState.DIRTY,
                        0,  // Reset retry count
                        0   // Reset error timestamp
                );
            }
            return current;
        });
    }

    public void markWebAppStateInFlight(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        CollectionState.IN_FLIGHT,
                        current.retryCount(),
                        current.lastErrorTimestamp()
                )
        );
    }

    public void markWebAppStateUpToDate(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        System.currentTimeMillis(),
                        CollectionState.UP_TO_DATE,
                        0,  // Reset retry count on success
                        0   // Reset error timestamp
                )
        );
    }

    public void markWebAppStatePending(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        CollectionState.PENDING,
                        current.retryCount(),
                        current.lastErrorTimestamp()
                )
        );
    }

    public void markWebAppStateBlocked(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        CollectionState.BLOCKED,
                        current.retryCount(),
                        System.currentTimeMillis()
                )
        );
    }

    public void markWebAppStateErrorRetry(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        CollectionState.ERROR_RETRY,
                        current.retryCount() + 1,
                        System.currentTimeMillis()
                )
        );
    }

    public void markWebAppStateErrorFatal(PatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new CollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        CollectionState.ERROR_FATAL,
                        current.retryCount(),
                        System.currentTimeMillis()
                )
        );
    }

    public CollectionMetadata findWebAppState(PatchType collectionName) {
        return webAppStateCollections.computeIfAbsent(collectionName, key ->
                new CollectionMetadata(
                        key,
                        0,
                        MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        0,
                        CollectionState.UP_TO_DATE,
                        0,
                        0
                )
        );
    }

    public void updateWebAppStateVersion(PatchType collectionName, long newVersion, byte[] newLtHash) {
        webAppStateCollections.compute(collectionName, (_, current) ->
                new CollectionMetadata(
                        collectionName,
                        newVersion,
                        MutationLTHash.copy(newLtHash),
                        System.currentTimeMillis(),
                        current != null ? current.state() : CollectionState.UP_TO_DATE,
                        0,  // Reset retry count on successful update
                        0   // Reset error timestamp
                )
        );
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof ProtobufWhatsAppStore that
                            && initializationTimeStamp == that.initializationTimeStamp
                            && online == that.online
                            && unarchiveChats == that.unarchiveChats
                            && twentyFourHourFormat == that.twentyFourHourFormat
                            && automaticPresenceUpdates == that.automaticPresenceUpdates
                            && automaticMessageReceipts == that.automaticMessageReceipts
                            && checkPatchMacs == that.checkPatchMacs
                            && syncedChats == that.syncedChats
                            && syncedContacts == that.syncedContacts
                            && syncedNewsletters == that.syncedNewsletters
                            && syncedStatus == that.syncedStatus
                            && syncedWebAppState == that.syncedWebAppState
                            && syncedBusinessCertificate == that.syncedBusinessCertificate
                            && registered == that.registered
                            && showSecurityNotifications == that.showSecurityNotifications
                            && Objects.equals(uuid, that.uuid)
                            && Objects.equals(phoneNumber, that.phoneNumber)
                            && clientType == that.clientType
                            && Objects.equals(device, that.device)
                            && releaseChannel == that.releaseChannel
                            && Objects.equals(locale, that.locale)
                            && Objects.equals(name, that.name)
                            && Objects.equals(verifiedName, that.verifiedName)
                            && Objects.equals(profilePicture, that.profilePicture)
                            && Objects.equals(about, that.about)
                            && Objects.equals(jid, that.jid)
                            && Objects.equals(lid, that.lid)
                            && Objects.equals(businessAddress, that.businessAddress)
                            && Objects.equals(businessLongitude, that.businessLongitude)
                            && Objects.equals(businessLatitude, that.businessLatitude)
                            && Objects.equals(businessDescription, that.businessDescription)
                            && Objects.equals(businessWebsite, that.businessWebsite)
                            && Objects.equals(businessEmail, that.businessEmail)
                            && Objects.equals(businessCategory, that.businessCategory)
                            && Objects.equals(chats, that.chats)
                            && Objects.equals(newsletters, that.newsletters)
                            && Objects.equals(status, that.status)
                            && Objects.equals(contacts, that.contacts)
                            && Objects.equals(calls, that.calls)
                            && Objects.equals(privacySettings, that.privacySettings)
                            && Objects.equals(properties, that.properties)
                            && newChatsEphemeralTimer == that.newChatsEphemeralTimer
                            && Objects.equals(webHistoryPolicy, that.webHistoryPolicy)
                            && Objects.equals(registrationId, that.registrationId)
                            && Objects.equals(noiseKeyPair, that.noiseKeyPair)
                            && Objects.equals(identityKeyPair, that.identityKeyPair)
                            && Objects.equals(companionKeyPair, that.companionKeyPair)
                            && Objects.equals(signedDeviceIdentity, that.signedDeviceIdentity)
                            && Objects.equals(signedKeyPair, that.signedKeyPair)
                            && Objects.equals(preKeys, that.preKeys)
                            && Objects.equals(fdid, that.fdid)
                            && Objects.deepEquals(deviceId, that.deviceId)
                            && Objects.equals(advertisingId, that.advertisingId)
                            && Objects.deepEquals(identityId, that.identityId)
                            && Objects.deepEquals(backupToken, that.backupToken)
                            && Objects.equals(senderKeys, that.senderKeys)
                            && Objects.equals(appStateKeys, that.appStateKeys)
                            && Objects.equals(sessions, that.sessions)
                            && Objects.equals(hashStates, that.hashStates)
                            && Objects.equals(recentStickers, that.recentStickers)
                            && Objects.equals(favouriteStickers, that.favouriteStickers)
                            && Objects.equals(quickReplies, that.quickReplies)
                            && Objects.equals(labels, that.labels)
                            && Objects.equals(clientVersion, that.clientVersion)
                            && Objects.equals(companionVersion, that.companionVersion)
                            && Objects.equals(lastAdvCheckTime, that.lastAdvCheckTime)
                            && Objects.equals(remoteIdentities, that.remoteIdentities)
                            && Objects.equals(identityEncryptionRange, that.identityEncryptionRange)
                            && Objects.equals(encryptionSequence, that.encryptionSequence)
                            && Objects.equals(missingSyncKeys, that.missingSyncKeys)
                            && Objects.deepEquals(advSecretKey, that.advSecretKey)
                            && Objects.equals(deviceIdentityRanges, that.deviceIdentityRanges)
                            && Objects.equals(verifiedBusinessNames, that.verifiedBusinessNames)
                            && Objects.equals(proxy, that.proxy)
                            && Objects.equals(directory, that.directory)
                            && Objects.equals(storesHashCodes, that.storesHashCodes)
                            && Objects.equals(jidsHashCodes, that.jidsHashCodes)
                            && Objects.equals(storeLock, that.storeLock)
                            && Objects.equals(attributionThread, that.attributionThread)
                            && Objects.equals(listeners, that.listeners)
                            && Objects.equals(lidToPhoneMappings, that.lidToPhoneMappings)
                            && Objects.equals(phoneToLidMappings, that.phoneToLidMappings)
                            && Objects.equals(mediaConnection, that.mediaConnection)
                            && Objects.equals(mediaConnectionLock, that.mediaConnectionLock)
                            && offlineResumeState == that.offlineResumeState
                            && Objects.equals(offlineDeliveryLatch, that.offlineDeliveryLatch)
                            && Objects.equals(usersNeedingSenderKeyRotation, that.usersNeedingSenderKeyRotation)
                            && Objects.equals(webAppStatePendingMutations, that.webAppStatePendingMutations)
                            && Objects.equals(webAppStateCollections, that.webAppStateCollections)
                            && Objects.equals(pendingMessageRecipients, that.pendingMessageRecipients)
                            && Objects.equals(clientVersionLock, that.clientVersionLock)
                            && Objects.equals(chatMetadata, that.chatMetadata)
                            && Objects.equals(deviceLists, that.deviceLists)
                            && Objects.equals(deviceListsAccessOrder, that.deviceListsAccessOrder)
                            && Objects.equals(offlineDeviceTimestamps, that.offlineDeviceTimestamps)
                            && Objects.equals(unconfirmedIdentityChanges, that.unconfirmedIdentityChanges)
                            && Objects.equals(coexHostedVerificationCache, that.coexHostedVerificationCache)
                            && Objects.equals(pendingDeviceSyncs, that.pendingDeviceSyncs)
                            && Objects.equals(groupSenderKeyDistribution, that.groupSenderKeyDistribution)
                            && Objects.equals(revokedMessageIds, that.revokedMessageIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, about, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsite, businessEmail, businessCategory, chats, newsletters, status, contacts, calls, privacySettings, properties, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, companionKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, Arrays.hashCode(deviceId), advertisingId, Arrays.hashCode(identityId), Arrays.hashCode(backupToken), senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, identityEncryptionRange, encryptionSequence, missingSyncKeys, Arrays.hashCode(advSecretKey), deviceIdentityRanges, verifiedBusinessNames, proxy, directory, storesHashCodes, jidsHashCodes, storeLock, attributionThread, listeners, lidToPhoneMappings, phoneToLidMappings, mediaConnection, mediaConnectionLock, offlineResumeState, offlineDeliveryLatch, usersNeedingSenderKeyRotation, webAppStatePendingMutations, webAppStateCollections, pendingMessageRecipients, clientVersionLock, chatMetadata, deviceLists, deviceListsAccessOrder, offlineDeviceTimestamps, unconfirmedIdentityChanges, coexHostedVerificationCache, pendingDeviceSyncs, groupSenderKeyDistribution, revokedMessageIds);
    }

    @Override
    public String toString() {
        return "WhatsappStore[" +
               "uuid=" + uuid +
               ", phoneNumber=" + phoneNumber +
               ", clientType=" + clientType +
               ", jid=" + jid +
               ']';
    }

    public Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type) {
        return type == null
                ? Optional.empty()
                : Optional.ofNullable(privacySettings.get(type));
    }

    public void addPrivacySetting(PrivacySettingEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        privacySettings.put(entry.type(), entry);
    }

    public Optional<ChatMessageInfo> findChatMessageByKey(ChatMessageKey key) {
        var chat = chats.get(key.chatJid());
        if(chat == null) {
            return Optional.empty();
        }

        return chat.getMessageById(key.id());
    }

    public boolean showSecurityNotifications() {
        return showSecurityNotifications;
    }

    public WhatsAppStore setShowSecurityNotifications(boolean showSecurityNotifications) {
        this.showSecurityNotifications = showSecurityNotifications;
        return this;
    }

    public Optional<Sticker> findRecentSticker(String stickerHash) {
        return Optional.ofNullable(recentStickers.get(stickerHash));
    }

    public void addRecentSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        recentStickers.put(stickerHash, sticker);
    }

    public Optional<Sticker> removeRecentSticker(String stickerHash) {
        return Optional.ofNullable(recentStickers.remove(stickerHash));
    }

    public Optional<Sticker> findFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickers.get(stickerHash));
    }

    public void addFavouriteSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        favouriteStickers.put(stickerHash, sticker);
    }

    public Optional<Sticker> removeFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickers.remove(stickerHash));
    }

    public Optional<QuickReply> findQuickReply(String shortcut) {
        return Optional.ofNullable(quickReplies.get(shortcut));
    }

    public void addQuickReply(QuickReply action) {
        Objects.requireNonNull(action, "action cannot be null");
        quickReplies.put(action.shortcut(), action);
    }

    public Optional<Label> findLabel(int labelId) {
        return Optional.ofNullable(labels.get(labelId));
    }

    public Optional<Label> removeLabel(int labelId) {
        return Optional.ofNullable(labels.remove(labelId));
    }

    public void addLabel(Label label) {
        Objects.requireNonNull(label, "label cannot be null");
        labels.put(label.id(), label);
    }

    public Optional<QuickReply> removeQuickReply(String shortcut) {
        return shortcut == null
                ? Optional.empty()
                : Optional.ofNullable(quickReplies.remove(shortcut));
    }

    public Version clientVersion() {
        if(clientVersion == null) {
            synchronized (clientVersionLock) {
                if(clientVersion == null) {
                    clientVersion = WhatsAppClientInfo.of(device.platform()).version();
                }
            }
        }
        return clientVersion;
    }

    public WhatsAppStore setClientVersion(Version clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    public Optional<Version> companionVersion() {
        return Optional.ofNullable(companionVersion);
    }

    public ProtobufWhatsAppStore setCompanionVersion(Version companionVersion) {
        this.companionVersion = companionVersion;
        return this;
    }

    public Optional<ChatMetadata> findChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        return Optional.ofNullable(chatMetadata.get(groupJid));
    }

    public void addChatMetadata(ChatMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        chatMetadata.put(metadata.jid(), metadata);
    }

    public void removeChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        chatMetadata.remove(groupJid);
    }

    public Optional<VerifiedBusinessName> findVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return Optional.ofNullable(verifiedBusinessNames.get(jid.toUserJid().toString()));
    }

    public void addVerifiedBusinessName(VerifiedBusinessName record) {
        Objects.requireNonNull(record, "record cannot be null");
        verifiedBusinessNames.put(record.jid().toUserJid().toString(), record);
    }

    public void removeVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        verifiedBusinessNames.remove(jid.toUserJid().toString());
    }

    public Optional<DeviceList> findDeviceList(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");

        synchronized (deviceListsAccessOrder) {
            var deviceList = deviceLists.get(userJid);
            if (deviceList == null) {
                // Check alternate JID (PN ↔ LID mapping)
                Jid alternateJid;
                if (userJid.hasUserServer()) {
                    alternateJid = findLidByPhone(userJid).orElse(null);
                } else if (userJid.hasLidServer()) {
                    alternateJid = findPhoneByLid(userJid).orElse(null);
                } else {
                    alternateJid = null;
                }

                if (alternateJid != null) {
                    var alternateList = deviceLists.get(alternateJid);
                    if (alternateList != null) {
                        deviceListsAccessOrder.remove(alternateJid);
                        deviceListsAccessOrder.addLast(alternateJid);
                        return Optional.of(alternateList);
                    }
                }

                return Optional.empty();
            }

            // Update LRU access order
            deviceListsAccessOrder.remove(userJid);
            deviceListsAccessOrder.addLast(userJid);

            return Optional.of(deviceList);
        }
    }

    public Set<Jid> findDeviceJids(Jid userJid) {
        return findDeviceList(userJid)
                .map(DeviceList::deviceJids)
                .orElse(Set.of());
    }

    public Collection<DeviceList> deviceLists() {
        synchronized (deviceListsAccessOrder) {
            return List.copyOf(deviceLists.values());
        }
    }

    public void addDeviceList(DeviceList deviceList) {
        Objects.requireNonNull(deviceList, "deviceList cannot be null");

        synchronized (deviceListsAccessOrder) {
            var userJid = deviceList.userJid();

            // Evict oldest entry if cache is full
            if (deviceLists.size() >= MAX_DEVICE_LISTS && !deviceLists.containsKey(userJid)) {
                var oldest = deviceListsAccessOrder.removeFirst();
                deviceLists.remove(oldest);
            }

            // Update or add entry
            deviceLists.put(userJid, deviceList);
            deviceListsAccessOrder.remove(userJid);
            deviceListsAccessOrder.addLast(userJid);
        }
    }

    public void removeDeviceList(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        synchronized (deviceListsAccessOrder) {
            deviceLists.remove(userJid);
            deviceListsAccessOrder.remove(userJid);
        }
    }

    public void clearDeviceLists() {
        synchronized (deviceListsAccessOrder) {
            deviceLists.clear();
            deviceListsAccessOrder.clear();
        }
    }

    public Optional<Instant> lastAdvCheckTime() {
        return Optional.ofNullable(lastAdvCheckTime);
    }

    public void updateAdvCheckTime() {
        this.lastAdvCheckTime = Instant.now();
    }

    public void markDeviceOffline(Jid deviceJid) {
        offlineDeviceTimestamps.put(deviceJid, System.currentTimeMillis());
    }

    public boolean isDeviceOffline(Jid deviceJid) {
        var timestamp = offlineDeviceTimestamps.get(deviceJid);
        if (timestamp == null) {
            return false;
        }
        var elapsed = System.currentTimeMillis() - timestamp;
        return elapsed < 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    }

    public void markDeviceOnline(Jid deviceJid) {
        offlineDeviceTimestamps.remove(deviceJid);
    }

    public void cleanupExpiredOfflineDevices() {
        var now = System.currentTimeMillis();
        var expirationTime = 24 * 60 * 60 * 1000; // 24 hours

        offlineDeviceTimestamps.entrySet().removeIf(entry ->
                now - entry.getValue() >= expirationTime
        );
    }

    public void addPendingDeviceSync(PendingDeviceSync sync) {
        pendingDeviceSyncs.offer(sync);
    }

    public List<PendingDeviceSync> pendingDevicesSyncs() {
        return List.copyOf(pendingDeviceSyncs);
    }

    public void removePendingDeviceSync(PendingDeviceSync sync) {
        pendingDeviceSyncs.remove(sync);
    }

    public void clearPendingDeviceSyncs() {
        pendingDeviceSyncs.clear();
    }

    public void cleanupExpiredPendingDeviceSyncs() {
        pendingDeviceSyncs.removeIf(PendingDeviceSync::isExpired);
    }

    public void markIdentityChange(Jid deviceJid) {
        unconfirmedIdentityChanges.add(deviceJid);
    }

    public void confirmIdentityChange(Jid deviceJid) {
        unconfirmedIdentityChanges.remove(deviceJid);
    }

    public Set<Jid> unconfirmedIdentityChanges() {
        return Collections.unmodifiableSet(unconfirmedIdentityChanges);
    }

    public void clearUnconfirmedIdentityChanges() {
        unconfirmedIdentityChanges.clear();
    }

    public void addToCoexHostedVerificationCache(Jid userJid) {
        if (userJid != null) {
            coexHostedVerificationCache.add(userJid.toUserJid());
        }
    }

    public boolean isInCoexHostedVerificationCache(Jid userJid) {
        if (userJid == null) {
            return false;
        }
        return coexHostedVerificationCache.contains(userJid.toUserJid());
    }

    public void assertCoexHostedVerification(Jid userJid) {
        if (!isInCoexHostedVerificationCache(userJid)) {
            throw new IllegalStateException(
                    "User " + userJid + " not found in coex verification cache"
            );
        }
    }

    public void clearCoexHostedVerificationCache() {
        coexHostedVerificationCache.clear();
    }

    public boolean hasJid(JidProvider entry) {
        if(entry == null) {
            return false;
        } else {
            var localJid = jid;
            var localLid = lid;
            var remoteJid = entry.toJid();
            return remoteJid.equals(localJid) || remoteJid.equals(localLid);
        }
    }

    public boolean hasUserJid(JidProvider entry) {
        if(entry == null) {
            return false;
        } else {
            var localJid = jid;
            var localLid = lid;
            var remoteJid = entry.toJid();
            return (localJid != null && remoteJid.hasUser(localJid.user()))
                   || (localLid != null && remoteJid.hasUser(localLid.user()));
        }
    }

    public Optional<Jid> getPhoneNumberByLid(Jid lidJid) {
        if (lidJid == null || !lidJid.hasLidServer()) {
            return Optional.empty();
        }
        // Try to find phone number from contact with matching LID
        return contacts.values().stream()
                .filter(contact -> contact.lid().map(lid -> lid.equals(lidJid)).orElse(false))
                .findFirst()
                .map(Contact::jid);
    }

    public Optional<Jid> getLidByPhoneNumber(Jid phoneNumberJid) {
        if (phoneNumberJid == null) {
            return Optional.empty();
        }
        // Check if already a LID
        if (phoneNumberJid.hasLidServer()) {
            return Optional.of(phoneNumberJid);
        }
        // Try to find LID from contact
        var contact = findContactByJid(phoneNumberJid).orElse(null);
        if (contact != null) {
            var contactLid = contact.lid();
            if (contactLid.isPresent()) {
                return contactLid;
            }
        }
        // Try to find LID from chat
        var chat = findChatByJid(phoneNumberJid).orElse(null);
        if (chat != null) {
            return chat.accountLid();
        }
        return Optional.empty();
    }
}