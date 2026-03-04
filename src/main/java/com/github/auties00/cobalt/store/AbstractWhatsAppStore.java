package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.business.BusinessVerifiedNameCertificate;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.mixin.PathMixin;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.util.FastRandomUtils;
import com.github.auties00.collections.ConcurrentLinkedHashMap;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord;
import com.github.auties00.libsignal.key.*;
import com.github.auties00.libsignal.state.SignalSessionRecord;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.builtin.ProtobufLazyMixin;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNullElseGet;

@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public abstract class AbstractWhatsAppStore implements WhatsAppStore {
    protected static final String DEFAULT_NAME = "User";
    protected static final int MAX_DEVICE_LISTS = 5000;
    protected static final Duration DEVICE_TTL = Duration.ofDays(1);

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    protected final UUID uuid;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    protected Long phoneNumber;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    protected final WhatsAppClientType clientType;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    protected final Instant initializationTimeStamp;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    protected JidDevice device;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    protected ClientReleaseChannel releaseChannel;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    protected boolean online;

    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    protected String locale;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    protected String name;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    protected String verifiedName;

    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    protected URI profilePicture;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    protected String about;

    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    protected Jid jid;

    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    protected Jid lid;

    @ProtobufProperty(index = 17, type = ProtobufType.STRING)
    protected String businessAddress;

    @ProtobufProperty(index = 18, type = ProtobufType.DOUBLE)
    protected Double businessLongitude;

    @ProtobufProperty(index = 19, type = ProtobufType.DOUBLE)
    protected Double businessLatitude;

    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    protected String businessDescription;

    @ProtobufProperty(index = 21, type = ProtobufType.STRING)
    protected String businessWebsite;

    @ProtobufProperty(index = 22, type = ProtobufType.STRING)
    protected String businessEmail;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    protected BusinessCategory businessCategory;

    @ProtobufProperty(index = 24, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentHashMap<Jid, Contact> contacts;

    @ProtobufProperty(index = 25, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentHashMap<String, CallOffer> calls;

    @ProtobufProperty(index = 26, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings;

    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    protected boolean unarchiveChats;

    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    protected boolean twentyFourHourFormat;

    @ProtobufProperty(index = 30, type = ProtobufType.ENUM)
    protected ChatEphemeralTimer newChatsEphemeralTimer;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    protected WhatsAppWebClientHistory webHistoryPolicy;

    @ProtobufProperty(index = 32, type = ProtobufType.BOOL)
    protected boolean automaticPresenceUpdates;

    @ProtobufProperty(index = 33, type = ProtobufType.BOOL)
    protected boolean automaticMessageReceipts;

    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    protected boolean checkPatchMacs;

    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    protected boolean syncedChats;

    @ProtobufProperty(index = 36, type = ProtobufType.BOOL)
    protected boolean syncedContacts;

    @ProtobufProperty(index = 37, type = ProtobufType.BOOL)
    protected boolean syncedNewsletters;

    @ProtobufProperty(index = 38, type = ProtobufType.BOOL)
    protected boolean syncedStatus;

    @ProtobufProperty(index = 39, type = ProtobufType.BOOL)
    protected boolean syncedWebAppState;

    @ProtobufProperty(index = 40, type = ProtobufType.BOOL)
    protected boolean syncedBusinessCertificate;

    @ProtobufProperty(index = 41, type = ProtobufType.INT32)
    protected final Integer registrationId;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    protected final SignalIdentityKeyPair noiseKeyPair;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    protected final SignalIdentityKeyPair identityKeyPair;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    protected ADVSignedDeviceIdentity signedDeviceIdentity;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    protected final SignalSignedKeyPair signedKeyPair;

    @ProtobufProperty(index = 48, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    protected final LinkedHashMap<Integer, SignalPreKeyPair> preKeys;

    @ProtobufProperty(index = 49, type = ProtobufType.STRING)
    protected final UUID fdid;

    @ProtobufProperty(index = 50, type = ProtobufType.BYTES)
    protected final byte[] deviceId;

    @ProtobufProperty(index = 51, type = ProtobufType.STRING)
    protected final UUID advertisingId;

    @ProtobufProperty(index = 52, type = ProtobufType.BYTES)
    protected final byte[] identityId;

    @ProtobufProperty(index = 53, type = ProtobufType.BYTES)
    protected final byte[] backupToken;

    @ProtobufProperty(index = 54, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys;

    @ProtobufProperty(index = 55, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final LinkedHashMap<String, AppStateSyncKey> appStateKeys;

    @ProtobufProperty(index = 56, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions;

    @ProtobufProperty(index = 57, type = ProtobufType.MESSAGE, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<SyncPatchType, SyncHashValue> hashStates;

    @ProtobufProperty(index = 58, type = ProtobufType.BOOL)
    protected boolean registered;

    @ProtobufProperty(index = 59, type = ProtobufType.BOOL)
    protected boolean showSecurityNotifications;

    @ProtobufProperty(index = 60, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<String, Sticker> recentStickers;

    @ProtobufProperty(index = 61, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<String, Sticker> favouriteStickers;

    @ProtobufProperty(index = 62, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<String, QuickReply> quickReplies;

    @ProtobufProperty(index = 63, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<Integer, Label> labels;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    protected volatile ClientAppVersion clientVersion;

    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    protected ClientAppVersion companionVersion;

    @ProtobufProperty(index = 66, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    protected Instant lastAdvCheckTime;

    @ProtobufProperty(index = 67, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    protected final ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities;

    @ProtobufProperty(index = 68, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys;

    @ProtobufProperty(index = 69, type = ProtobufType.BYTES)
    protected byte[] advSecretKey;

    @ProtobufProperty(index = 70, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<Jid, BusinessVerifiedNameCertificate> verifiedBusinessNames;

    @ProtobufProperty(index = 71, type = ProtobufType.STRING, mixins = {PathMixin.class, ProtobufLazyMixin.class})
    protected final Path directory;

    protected final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange;

    protected final AtomicLong encryptionSequence;

    protected WhatsAppClientProxy proxy;

    protected final KeySetView<WhatsAppClientListener, Boolean> listeners;

    protected final ConcurrentHashMap<Jid, Jid> lidToPhoneMappings;

    protected final ConcurrentHashMap<Jid, Jid> phoneToLidMappings;

    protected volatile MediaConnection mediaConnection;

    protected final Object mediaConnectionLock;

    protected volatile WhatsAppClientOfflineResumeState offlineResumeState;

    protected volatile CountDownLatch offlineDeliveryLatch;

    protected final KeySetView<Jid, Boolean> usersNeedingSenderKeyRotation;

    protected final ConcurrentMap<SyncPatchType, SequencedCollection<SyncPendingMutation>> webAppStatePendingMutations;

    protected final ConcurrentMap<SyncPatchType, SyncCollectionMetadata> webAppStateCollections;

    protected final ConcurrentMap<SyncPatchType, ConcurrentMap<String, SyncActionEntry>> syncActionEntries;

    protected final ConcurrentMap<String, Set<Jid>> pendingMessageRecipients;

    protected final Object clientVersionLock;

    protected final ConcurrentMap<Jid, ChatMetadata<?>> chatMetadata;

    protected final ConcurrentLinkedHashMap<Jid, DeviceList> deviceLists;

    protected final Set<Jid> unconfirmedIdentityChanges;

    protected final Set<Jid> coexHostedVerificationCache;

    protected final ConcurrentLinkedQueue<PendingDeviceSync> pendingDeviceSyncs;

    protected final ConcurrentMap<String, Set<String>> groupSenderKeyDistribution;

    protected final System.Logger logger;

    protected AbstractWhatsAppStore(
            UUID uuid,
            Long phoneNumber,
            WhatsAppClientType clientType,
            Instant initializationTimeStamp,
            JidDevice device,
            ClientReleaseChannel releaseChannel,
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
            ADVSignedDeviceIdentity signedDeviceIdentity,
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
            ConcurrentMap<SyncPatchType, SyncHashValue> hashStates,
            boolean registered,
            boolean showSecurityNotifications,
            ConcurrentMap<String, Sticker> recentStickers,
            ConcurrentMap<String, Sticker> favouriteStickers,
            ConcurrentMap<String, QuickReply> quickReplies,
            ConcurrentMap<Integer, Label> labels,
            ClientAppVersion clientVersion,
            ClientAppVersion companionVersion,
            Instant lastAdvCheckTime,
            ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities,
            ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys,
            byte[] advSecretKey,
            ConcurrentMap<Jid, BusinessVerifiedNameCertificate> verifiedBusinessNames,
            Path directory
    ) {
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
        this.contacts = Objects.requireNonNull(contacts, "contacts cannot be null");
        this.privacySettings = Objects.requireNonNull(privacySettings, "privacySettings cannot be null");
        this.calls = Objects.requireNonNull(calls, "calls cannot be null");
        this.unarchiveChats = unarchiveChats;
        this.twentyFourHourFormat = twentyFourHourFormat;
        this.initializationTimeStamp = requireNonNullElseGet(initializationTimeStamp, Instant::now);
        this.newChatsEphemeralTimer = Objects.requireNonNullElse(newChatsEphemeralTimer, ChatEphemeralTimer.OFF);
        this.webHistoryPolicy = webHistoryPolicy;
        this.automaticPresenceUpdates = automaticPresenceUpdates;
        this.automaticMessageReceipts = automaticMessageReceipts;
        this.releaseChannel = Objects.requireNonNullElse(releaseChannel, ClientReleaseChannel.RELEASE);
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
        this.listeners = ConcurrentHashMap.newKeySet();
        this.lidToPhoneMappings = new ConcurrentHashMap<>();
        this.phoneToLidMappings = new ConcurrentHashMap<>();
        for (var contact : contacts.values()) {
            contact.lid()
                    .ifPresent(entry -> registerLidMapping(contact.jid(), entry));
        }
        this.registrationId = requireNonNullElseGet(registrationId, () -> FastRandomUtils.randomInt(16380) + 1);
        this.noiseKeyPair = requireNonNullElseGet(noiseKeyPair, SignalIdentityKeyPair::random);
        this.identityKeyPair = requireNonNullElseGet(identityKeyPair, SignalIdentityKeyPair::random);
        this.signedKeyPair = requireNonNullElseGet(signedKeyPair, () -> SignalSignedKeyPair.of(this.registrationId, this.identityKeyPair));
        this.preKeys = Objects.requireNonNull(preKeys, "preKeys cannot be null");
        this.fdid = requireNonNullElseGet(fdid, UUID::randomUUID);
        this.deviceId = requireNonNullElseGet(deviceId, () -> HexFormat.of().parseHex(UUID.randomUUID().toString().replace("-", "")));
        this.advertisingId = requireNonNullElseGet(advertisingId, UUID::randomUUID);
        this.identityId = requireNonNullElseGet(identityId, () -> FastRandomUtils.randomByteArray(16));
        this.backupToken = requireNonNullElseGet(backupToken, () -> FastRandomUtils.randomByteArray(20));
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
        this.syncActionEntries = new ConcurrentHashMap<>();
        this.pendingMessageRecipients = new ConcurrentHashMap<>();
        this.chatMetadata = new ConcurrentHashMap<>();
        this.deviceLists = new ConcurrentLinkedHashMap<>();
        this.unconfirmedIdentityChanges = ConcurrentHashMap.newKeySet();
        this.coexHostedVerificationCache = ConcurrentHashMap.newKeySet();
        this.pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
        this.groupSenderKeyDistribution = new ConcurrentHashMap<>();
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.remoteIdentities = requireNonNullElseGet(remoteIdentities, ConcurrentHashMap::new);
        this.missingSyncKeys = requireNonNullElseGet(missingSyncKeys, ConcurrentHashMap::new);
        this.advSecretKey = advSecretKey;
        this.verifiedBusinessNames = requireNonNullElseGet(verifiedBusinessNames, ConcurrentHashMap::new);
        this.directory = directory;
        this.identityEncryptionRange = new ConcurrentHashMap<>();
        this.encryptionSequence = new AtomicLong();
        this.logger = System.getLogger(this.getClass().getName());
        this.mediaConnectionLock = new Object();
        this.offlineResumeState = WhatsAppClientOfflineResumeState.INIT;
        this.offlineDeliveryLatch = new CountDownLatch(1);
        this.usersNeedingSenderKeyRotation = ConcurrentHashMap.newKeySet();
    }

    @Override
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

    @Override
    public Collection<Contact> contacts() {
        return Collections.unmodifiableCollection(contacts.values());
    }

    @Override
    public Contact addNewContact(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        var newContact = new ContactBuilder()
                .jid(jid)
                .lid(phoneToLidMappings.get(jid))
                .build();
        return addContact(newContact);
    }

    @Override
    public Contact addContact(Contact contact) {
        Objects.requireNonNull(contact, "contact cannot be null");
        contacts.put(contact.jid(), contact);
        return contact;
    }

    @Override
    public Optional<Contact> removeContact(JidProvider contactJid) {
        if(contactJid == null) {
            return Optional.empty();
        } else {
            var targetJid = contactJid.toJid();
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

    @Override
    public void registerLidMapping(Jid phoneJid, Jid lidJid) {
        if (phoneJid == null || lidJid == null) {
            return;
        }
        var normalizedPhone = phoneJid.withoutData();
        var normalizedLid = lidJid.withoutData();
        lidToPhoneMappings.put(normalizedLid, normalizedPhone);
        phoneToLidMappings.put(normalizedPhone, normalizedLid);
    }

    @Override
    public Optional<Jid> findPhoneByLid(Jid lidJid) {
        return lidJid == null ? Optional.empty() : Optional.ofNullable(lidToPhoneMappings.get(lidJid.withoutData()));
    }

    @Override
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

    @Override
    public Optional<CallOffer> findCallById(String callId) {
        return callId == null
                ? Optional.empty()
                : Optional.ofNullable(calls.get(callId));
    }

    @Override
    public CallOffer addCall(CallOffer call) {
        Objects.requireNonNull(call, "call cannot be null");
        calls.put(call.callId(), call);
        return call;
    }

    @Override
    public Optional<CallOffer> removeCall(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(calls.remove(id));
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public OptionalLong phoneNumber() {
        return phoneNumber == null ? OptionalLong.empty() : OptionalLong.of(phoneNumber);
    }

    @Override
    public WhatsAppStore setPhoneNumber(Long phoneNumber) {
        this.phoneNumber = phoneNumber;
        return this;
    }

    @Override
    public WhatsAppClientType clientType() {
        return clientType;
    }

    @Override
    public Optional<WhatsAppClientProxy> proxy() {
        return Optional.ofNullable(proxy);
    }

    @Override
    public WhatsAppStore setProxy(WhatsAppClientProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public JidDevice device() {
        return device;
    }

    @Override
    public WhatsAppStore setDevice(JidDevice device) {
        this.device = Objects.requireNonNull(device, "device cannot be null");
        return this;
    }

    @Override
    public ClientReleaseChannel releaseChannel() {
        return releaseChannel;
    }

    @Override
    public WhatsAppStore setReleaseChannel(ClientReleaseChannel releaseChannel) {
        this.releaseChannel = Objects.requireNonNull(releaseChannel, "releaseChannel cannot be null");
        return this;
    }

    @Override
    public boolean online() {
        return online;
    }

    @Override
    public WhatsAppStore setOnline(boolean online) {
        this.online = online;
        return this;
    }

    @Override
    public Optional<String> locale() {
        return Optional.ofNullable(locale);
    }

    @Override
    public WhatsAppStore setLocale(String locale) {
        this.locale = locale;
        return this;
    }

    @Override
    public String name() {
        return Objects.requireNonNullElse(name, DEFAULT_NAME);
    }

    @Override
    public WhatsAppStore setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Optional<String> verifiedName() {
        return Optional.ofNullable(verifiedName);
    }

    @Override
    public WhatsAppStore setVerifiedName(String verifiedName) {
        this.verifiedName = verifiedName;
        return this;
    }

    @Override
    public Optional<URI> profilePicture() {
        return Optional.ofNullable(profilePicture);
    }

    @Override
    public WhatsAppStore setProfilePicture(URI profilePicture) {
        this.profilePicture = profilePicture;
        return this;
    }

    @Override
    public Optional<String> about() {
        return Optional.ofNullable(about);
    }

    @Override
    public WhatsAppStore setAbout(String about) {
        this.about = about;
        return this;
    }

    @Override
    public Optional<Jid> jid() {
        return Optional.ofNullable(jid);
    }

    @Override
    public WhatsAppStore setJid(Jid jid) {
        this.jid = jid;
        return this;
    }

    @Override
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    @Override
    public WhatsAppStore setLid(Jid lid) {
        this.lid = lid;
        return this;
    }

    @Override
    public Optional<String> businessAddress() {
        return Optional.ofNullable(businessAddress);
    }

    @Override
    public WhatsAppStore setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
        return this;
    }

    @Override
    public OptionalDouble businessLongitude() {
        return businessLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLongitude);
    }

    @Override
    public WhatsAppStore setBusinessLongitude(Double businessLongitude) {
        this.businessLongitude = businessLongitude;
        return this;
    }

    @Override
    public OptionalDouble businessLatitude() {
        return businessLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLatitude);
    }

    @Override
    public WhatsAppStore setBusinessLatitude(Double businessLatitude) {
        this.businessLatitude = businessLatitude;
        return this;
    }

    @Override
    public Optional<String> businessDescription() {
        return Optional.ofNullable(businessDescription);
    }

    @Override
    public WhatsAppStore setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
        return this;
    }

    @Override
    public Optional<String> businessWebsite() {
        return Optional.ofNullable(businessWebsite);
    }

    @Override
    public WhatsAppStore setBusinessWebsite(String businessWebsite) {
        this.businessWebsite = businessWebsite;
        return this;
    }

    @Override
    public Optional<String> businessEmail() {
        return Optional.ofNullable(businessEmail);
    }

    @Override
    public WhatsAppStore setBusinessEmail(String businessEmail) {
        this.businessEmail = businessEmail;
        return this;
    }

    @Override
    public Optional<BusinessCategory> businessCategory() {
        return Optional.ofNullable(businessCategory);
    }

    @Override
    public WhatsAppStore setBusinessCategory(BusinessCategory businessCategory) {
        this.businessCategory = businessCategory;
        return this;
    }
    
    @Override
    public Collection<CallOffer> calls() {
        return Collections.unmodifiableCollection(calls.values());
    }

    @Override
    public Collection<PrivacySettingEntry> privacySettings() {
        return Collections.unmodifiableCollection(privacySettings.values());
    }

    @Override
    public boolean unarchiveChats() {
        return unarchiveChats;
    }

    @Override
    public WhatsAppStore setUnarchiveChats(boolean unarchiveChats) {
        this.unarchiveChats = unarchiveChats;
        return this;
    }

    @Override
    public boolean twentyFourHourFormat() {
        return twentyFourHourFormat;
    }

    @Override
    public WhatsAppStore setTwentyFourHourFormat(boolean twentyFourHourFormat) {
        this.twentyFourHourFormat = twentyFourHourFormat;
        return this;
    }

    @Override
    public Instant initializationTimeStamp() {
        return initializationTimeStamp;
    }

    @Override
    public ChatEphemeralTimer newChatsEphemeralTimer() {
        return newChatsEphemeralTimer;
    }

    @Override
    public WhatsAppStore setNewChatsEphemeralTimer(ChatEphemeralTimer newChatsEphemeralTimer) {
        this.newChatsEphemeralTimer = Objects.requireNonNull(newChatsEphemeralTimer, "newChatsEphemeralTimer cannot be null");
        return this;
    }

    @Override
    public Optional<WhatsAppWebClientHistory> webHistoryPolicy() {
        return Optional.ofNullable(webHistoryPolicy);
    }

    @Override
    public WhatsAppStore setWebHistoryPolicy(WhatsAppWebClientHistory webHistoryPolicy) {
        this.webHistoryPolicy = webHistoryPolicy;
        return this;
    }

    @Override
    public boolean automaticPresenceUpdates() {
        return automaticPresenceUpdates;
    }

    @Override
    public WhatsAppStore setAutomaticPresenceUpdates(boolean automaticPresenceUpdates) {
        this.automaticPresenceUpdates = automaticPresenceUpdates;
        return this;
    }

    @Override
    public boolean automaticMessageReceipts() {
        return automaticMessageReceipts;
    }

    @Override
    public WhatsAppStore setAutomaticMessageReceipts(boolean automaticMessageReceipts) {
        this.automaticMessageReceipts = automaticMessageReceipts;
        return this;
    }

    @Override
    public boolean checkPatchMacs() {
        return checkPatchMacs;
    }

    @Override
    public WhatsAppStore setCheckPatchMacs(boolean checkPatchMacs) {
        this.checkPatchMacs = checkPatchMacs;
        return this;
    }

    @Override
    public boolean syncedChats() {
        return syncedChats;
    }

    @Override
    public WhatsAppStore setSyncedChats(boolean syncedChats) {
        this.syncedChats = syncedChats;
        return this;
    }

    @Override
    public boolean syncedContacts() {
        return syncedContacts;
    }

    @Override
    public WhatsAppStore setSyncedContacts(boolean syncedContacts) {
        this.syncedContacts = syncedContacts;
        return this;
    }

    @Override
    public boolean syncedNewsletters() {
        return syncedNewsletters;
    }

    @Override
    public WhatsAppStore setSyncedNewsletters(boolean syncedNewsletters) {
        this.syncedNewsletters = syncedNewsletters;
        return this;
    }

    @Override
    public boolean syncedStatus() {
        return syncedStatus;
    }

    @Override
    public WhatsAppStore setSyncedStatus(boolean syncedStatus) {
        this.syncedStatus = syncedStatus;
        return this;
    }

    @Override
    public boolean syncedWebAppState() {
        return syncedWebAppState;
    }

    @Override
    public WhatsAppStore setSyncedWebAppState(boolean syncedWebAppState) {
        this.syncedWebAppState = syncedWebAppState;
        return this;
    }

    @Override
    public boolean syncedBusinessCertificate() {
        return this.syncedBusinessCertificate;
    }

    @Override
    public WhatsAppStore setSyncedBusinessCertificate(boolean syncedBusinessCertificate) {
        this.syncedBusinessCertificate = syncedBusinessCertificate;
        return this;
    }

    @Override
    public WhatsAppClientListener addListener(WhatsAppClientListener listener) {
        listeners.add(listener);
        return listener;
    }

    @Override
    public boolean removeListener(WhatsAppClientListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public Collection<WhatsAppClientListener> listeners() {
        return Collections.unmodifiableCollection(listeners);
    }

    @Override
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

    @Override
    public WhatsAppStore setMediaConnection(MediaConnection mediaConnection) {
        this.mediaConnection = mediaConnection;
        synchronized (mediaConnectionLock) {
            this.mediaConnectionLock.notifyAll();
        }
        return this;
    }

    @Override
    public WhatsAppClientOfflineResumeState offlineResumeState() {
        return offlineResumeState;
    }

    @Override
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

    @Override
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

    @Override
    public boolean isResumeFromRestartComplete() {
        return offlineResumeState != WhatsAppClientOfflineResumeState.INIT
               && offlineResumeState != WhatsAppClientOfflineResumeState.RESUME_ON_RESTART;
    }

    @Override
    public Optional<ADVSignedDeviceIdentity> signedDeviceIdentity() {
        return Optional.ofNullable(signedDeviceIdentity);
    }

    @Override
    public WhatsAppStore setSignedDeviceIdentity(ADVSignedDeviceIdentity signedDeviceIdentity) {
        this.signedDeviceIdentity = signedDeviceIdentity;
        return this;
    }

    @Override
    public SignalSignedKeyPair signedKeyPair() {
        return signedKeyPair;
    }

    @Override
    public UUID fdid() {
        return fdid;
    }

    @Override
    public byte[] deviceId() {
        return deviceId;
    }

    @Override
    public UUID advertisingId() {
        return advertisingId;
    }

    @Override
    public byte[] identityId() {
        return identityId;
    }

    @Override
    public byte[] backupToken() {
        return backupToken;
    }

    @Override
    public int registrationId() {
        return this.registrationId;
    }

    @Override
    public SignalIdentityKeyPair noiseKeyPair() {
        return this.noiseKeyPair;
    }

    @Override
    public SignalIdentityKeyPair identityKeyPair() {
        return this.identityKeyPair;
    }

    @Override
    public Optional<byte[]> advSecretKey() {
        return Optional.ofNullable(advSecretKey);
    }

    @Override
    public WhatsAppStore setAdvSecretKey(byte[] advSecretKey) {
        this.advSecretKey = advSecretKey;
        return this;
    }

    @Override
    public SequencedCollection<SignalPreKeyPair> preKeys() {
        return preKeys.sequencedValues();
    }

    @Override
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

    @Override
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

    @Override
    public boolean removeSession(SignalProtocolAddress address) {
        return sessions.remove(address) != null;
    }

    @Override
    public void removeSenderKeys(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        senderKeys.keySet().removeIf(name ->
                name.sender().equals(signalAddress)
        );
    }

    @Override
    public void removeSenderKeys(SignalSenderKeyName senderKeyName) {
        Objects.requireNonNull(senderKeyName, "senderKeyName cannot be null");
        senderKeys.remove(senderKeyName);
    }

    @Override
    public void cleanupSignalSessions(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        removeSession(signalAddress);
        removeSenderKeys(deviceJid);
    }

    @Override
    public void markKeyRotation(Jid userJid) {
        usersNeedingSenderKeyRotation.add(userJid.toUserJid());
    }

    @Override
    public boolean clearKeyRotation(Jid userJid) {
        return usersNeedingSenderKeyRotation.remove(userJid.toUserJid());
    }

    @Override
    public boolean isKeyRotated(Collection<Jid> userJids) {
        return userJids.stream()
                .anyMatch(entry -> usersNeedingSenderKeyRotation.contains(entry.toUserJid()));
    }

    @Override
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

    @Override
    public boolean hasIdentityChanged(long sendSequence, Jid device) {
        var recorded = identityEncryptionRange.get(device.toSignalAddress());
        return recorded == null || recorded > sendSequence;
    }

    @Override
    public void clearIdentityRange(Jid device) {
        identityEncryptionRange.remove(device.toSignalAddress());
    }

    @Override
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

    @Override
    public void markSenderKeyDistributed(Jid groupJid, Jid participantJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");

        var groupKey = groupJid.toString();
        groupSenderKeyDistribution
                .computeIfAbsent(groupKey, k -> ConcurrentHashMap.newKeySet())
                .add(participantJid.toString());
    }

    @Override
    public void clearSenderKeyDistribution(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        groupSenderKeyDistribution.remove(groupJid.toString());
    }

    @Override
    public void clearSenderKeyDistributionForParticipant(Jid participantJid) {
        Objects.requireNonNull(participantJid, "participantJid cannot be null");
        var participantKey = participantJid.toString();

        for (var participants : groupSenderKeyDistribution.values()) {
            participants.remove(participantKey);
        }
    }

    @Override
    public void forgetSenderKeyDistributed(Jid groupJid, Jid participantJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");

        var groupKey = groupJid.toString();
        var participants = groupSenderKeyDistribution.get(groupKey);
        if (participants != null) {
            participants.remove(participantJid.toString());
        }
    }

    @Override
    public SequencedCollection<AppStateSyncKey> appStateKeys() {
        return Collections.unmodifiableSequencedCollection(appStateKeys.sequencedValues());
    }

    @Override
    public Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id) {
        return Optional.ofNullable(appStateKeys.get(HexFormat.of().formatHex(id)));
    }

    @Override
    public void addWebAppStateKeys(Collection<AppStateSyncKey> keys) {
        for (var key : keys) {
            key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .ifPresent(keyId -> appStateKeys.put(HexFormat.of().formatHex(keyId), key));
        }
    }

    @Override
    public Optional<SyncHashValue> findWebAppHashStateByName(SyncPatchType patchType) {
        return Optional.ofNullable(hashStates.get(patchType));
    }

    @Override
    public void addWebAppHashState(SyncHashValue state) {
        hashStates.put(state.type(), state);
    }

    @Override
    public Optional<SyncActionEntry> findSyncActionEntry(SyncPatchType patchType, byte[] indexMac) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.get(HexFormat.of().formatHex(indexMac)));
    }

    @Override
    public void putSyncActionEntry(SyncPatchType patchType, byte[] indexMac, SyncActionEntry entry) {
        syncActionEntries.computeIfAbsent(patchType, _ -> new ConcurrentHashMap<>())
                .put(HexFormat.of().formatHex(indexMac), entry);
    }

    @Override
    public Optional<SyncActionEntry> removeSyncActionEntry(SyncPatchType patchType, byte[] indexMac) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.remove(HexFormat.of().formatHex(indexMac)));
    }

    @Override
    public void clearSyncActionEntries(SyncPatchType patchType) {
        syncActionEntries.remove(patchType);
    }

    @Override
    public Collection<MissingDeviceSyncKey> missingSyncKeys() {
        return Collections.unmodifiableCollection(missingSyncKeys.values());
    }

    @Override
    public Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId) {
        return Optional.ofNullable(missingSyncKeys.get(HexFormat.of().formatHex(keyId)));
    }

    @Override
    public void addMissingSyncKey(MissingDeviceSyncKey missingKey) {
        missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
    }

    @Override
    public void removeMissingSyncKey(byte[] keyId) {
        missingSyncKeys.remove(HexFormat.of().formatHex(keyId));
    }

    @Override
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

    @Override
    public void removeReceiptRecords(String messageId) {
        pendingMessageRecipients.remove(messageId);
    }

    @Override
    public Set<Jid> findReceiptRecords(String messageId) {
        if (messageId == null) {
            return Set.of();
        }
        var recipients = pendingMessageRecipients.get(messageId);
        return recipients != null ? Set.copyOf(recipients) : Set.of();
    }

    @Override
    public void addPendingMutations(SyncPatchType collectionName, Collection<? extends SyncPendingMutation> patch) {
        webAppStatePendingMutations
                .computeIfAbsent(collectionName, k -> new ArrayList<>())
                .addAll(patch);
    }

    @Override
    public SequencedCollection<SyncPendingMutation> findPendingMutations(SyncPatchType collectionName) {
        var collectionPending = webAppStatePendingMutations.get(collectionName);
        return collectionPending == null ? List.of() : Collections.unmodifiableSequencedCollection(collectionPending);
    }

    @Override
    public void removePendingMutations(SyncPatchType collectionName) {
        webAppStatePendingMutations.remove(collectionName);
    }

    @Override
    public void clearPendingMutations(SyncPatchType collectionName) {
        webAppStatePendingMutations.remove(collectionName);
    }

    @Override
    public boolean registered() {
        return this.registered;
    }

    @Override
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

    @Override
    public void saveIdentity(SignalProtocolAddress address, SignalIdentityPublicKey identityKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(identityKey, "identityKey cannot be null");
        remoteIdentities.put(address, identityKey);
    }

    @Override
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

    public void markWebAppStateDirty(SyncPatchType collectionName) {
        webAppStateCollections.compute(collectionName, (_, current) -> {
            if (current == null || current.state() == SyncCollectionState.UP_TO_DATE) {
                return new SyncCollectionMetadata(
                        collectionName,
                        current != null ? current.version() : 0,
                        current != null ? MutationLTHash.copy(current.ltHash()) : MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        System.currentTimeMillis(),
                        SyncCollectionState.DIRTY,
                        0,  // Reset retry count
                        0   // Reset error timestamp
                );
            }
            return current;
        });
    }

    @Override
    public void markWebAppStateInFlight(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.IN_FLIGHT,
                        current.retryCount(),
                        current.lastErrorTimestamp()
                )
        );
    }

    @Override
    public void markWebAppStateUpToDate(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        System.currentTimeMillis(),
                        SyncCollectionState.UP_TO_DATE,
                        0,  // Reset retry count on success
                        0   // Reset error timestamp
                )
        );
    }

    @Override
    public void markWebAppStatePending(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.PENDING,
                        current.retryCount(),
                        current.lastErrorTimestamp()
                )
        );
    }

    @Override
    public void markWebAppStateBlocked(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.BLOCKED,
                        current.retryCount(),
                        System.currentTimeMillis()
                )
        );
    }

    @Override
    public void markWebAppStateErrorRetry(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.ERROR_RETRY,
                        current.retryCount() + 1,
                        current.lastErrorTimestamp() > 0 ? current.lastErrorTimestamp() : System.currentTimeMillis()
                )
        );
    }

    @Override
    public void markWebAppStateErrorFatal(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.ERROR_FATAL,
                        current.retryCount(),
                        System.currentTimeMillis()
                )
        );
    }

    @Override
    public SyncCollectionMetadata findWebAppState(SyncPatchType collectionName) {
        return webAppStateCollections.computeIfAbsent(collectionName, key ->
                new SyncCollectionMetadata(
                        key,
                        0,
                        MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        0,
                        SyncCollectionState.UP_TO_DATE,
                        0,
                        0
                )
        );
    }

    @Override
    public void updateWebAppStateVersion(SyncPatchType collectionName, long newVersion, byte[] newLtHash) {
        webAppStateCollections.compute(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        collectionName,
                        newVersion,
                        MutationLTHash.copy(newLtHash),
                        System.currentTimeMillis(),
                        current != null ? current.state() : SyncCollectionState.UP_TO_DATE,
                        0,  // Reset retry count on successful update
                        0   // Reset error timestamp
                )
        );
    }

    @Override
    public Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type) {
        return type == null
                ? Optional.empty()
                : Optional.ofNullable(privacySettings.get(type));
    }

    @Override
    public void addPrivacySetting(PrivacySettingEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        privacySettings.put(entry.type(), entry);
    }

    @Override
    public boolean showSecurityNotifications() {
        return showSecurityNotifications;
    }

    @Override
    public WhatsAppStore setShowSecurityNotifications(boolean showSecurityNotifications) {
        this.showSecurityNotifications = showSecurityNotifications;
        return this;
    }

    @Override
    public Optional<Sticker> findRecentSticker(String stickerHash) {
        return Optional.ofNullable(recentStickers.get(stickerHash));
    }

    @Override
    public void addRecentSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        recentStickers.put(stickerHash, sticker);
    }

    @Override
    public Optional<Sticker> removeRecentSticker(String stickerHash) {
        return Optional.ofNullable(recentStickers.remove(stickerHash));
    }

    @Override
    public Optional<Sticker> findFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickers.get(stickerHash));
    }

    @Override
    public void addFavouriteSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        favouriteStickers.put(stickerHash, sticker);
    }

    @Override
    public Optional<Sticker> removeFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickers.remove(stickerHash));
    }

    @Override
    public void addLabel(Label label) {
        Objects.requireNonNull(label, "label cannot be null");
        labels.put(label.id(), label);
    }

    @Override
    public Optional<Label> removeLabel(int labelId) {
        return Optional.ofNullable(labels.remove(labelId));
    }

    @Override
    public Optional<Label> findLabel(int labelId) {
        return Optional.ofNullable(labels.get(labelId));
    }

    @Override
    public Optional<QuickReply> findQuickReply(String shortcut) {
        return Optional.ofNullable(quickReplies.get(shortcut));
    }

    @Override
    public void addQuickReply(QuickReply action) {
        Objects.requireNonNull(action, "action cannot be null");
        quickReplies.put(action.shortcut(), action);
    }

    @Override
    public Optional<QuickReply> removeQuickReply(String shortcut) {
        return shortcut == null
                ? Optional.empty()
                : Optional.ofNullable(quickReplies.remove(shortcut));
    }

    @Override
    public ClientAppVersion clientVersion() {
        if(clientVersion == null) {
            synchronized (clientVersionLock) {
                if(clientVersion == null) {
                    clientVersion = WhatsAppClientInfo.of(device.platform()).version();
                }
            }
        }
        return clientVersion;
    }

    @Override
    public WhatsAppStore setClientVersion(ClientAppVersion clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    @Override
    public Optional<ClientAppVersion> companionVersion() {
        return Optional.ofNullable(companionVersion);
    }

    @Override
    public AbstractWhatsAppStore setCompanionVersion(ClientAppVersion companionVersion) {
        this.companionVersion = companionVersion;
        return this;
    }

    @Override
    public Optional<ChatMetadata<?>> findChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        return Optional.ofNullable(chatMetadata.get(groupJid));
    }

    @Override
    public void addChatMetadata(ChatMetadata<?> metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        chatMetadata.put(metadata.jid(), metadata);
    }

    @Override
    public void removeChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        chatMetadata.remove(groupJid);
    }

    @Override
    public Optional<BusinessVerifiedNameCertificate> findVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return Optional.ofNullable(verifiedBusinessNames.get(jid.toUserJid()));
    }

    @Override
    public void addVerifiedBusinessName(Jid jid, BusinessVerifiedNameCertificate record) {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(record, "record cannot be null");
        verifiedBusinessNames.put(jid.toUserJid(), record);
    }

    @Override
    public void removeVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        verifiedBusinessNames.remove(jid.toUserJid());
    }

    @Override
    public Optional<DeviceList> findDeviceList(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");

        var deviceList = deviceLists.get(userJid);
        if (deviceList != null) {
            return Optional.of(deviceList);
        }

        Jid alternateJid;
        if (userJid.hasUserServer()) {
            alternateJid = findLidByPhone(userJid).orElse(null);
        } else if (userJid.hasLidServer()) {
            alternateJid = findPhoneByLid(userJid).orElse(null);
        } else {
            alternateJid = null;
        }

        if (alternateJid == null) {
            return Optional.empty();
        }

        var alternateList = deviceLists.get(alternateJid);
        return Optional.ofNullable(alternateList);
    }

    @Override
    public SequencedCollection<DeviceList> deviceLists() {
        return List.copyOf(deviceLists.sequencedValues());
    }

    @Override
    public void addDeviceList(DeviceList deviceList) {
        Objects.requireNonNull(deviceList, "deviceList cannot be null");

        var userJid = deviceList.userJid();

        // Evict oldest entry if cache is full
        if (deviceLists.size() >= MAX_DEVICE_LISTS && !deviceLists.containsKey(userJid)) {
            deviceLists.pollLastEntry();
        }

        // Update or add entry
        deviceLists.put(userJid, deviceList);
    }

    @Override
    public void removeDeviceList(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        deviceLists.remove(userJid);
    }

    @Override
    public void clearDeviceLists() {
        deviceLists.clear();
    }

    @Override
    public Optional<Instant> lastAdvCheckTime() {
        return Optional.ofNullable(lastAdvCheckTime);
    }

    @Override
    public void updateAdvCheckTime() {
        this.lastAdvCheckTime = Instant.now();
    }

    @Override
    public void addPendingDeviceSync(PendingDeviceSync sync) {
        pendingDeviceSyncs.offer(sync);
    }

    @Override
    public List<PendingDeviceSync> pendingDevicesSyncs() {
        return List.copyOf(pendingDeviceSyncs);
    }

    @Override
    public void removePendingDeviceSync(PendingDeviceSync sync) {
        pendingDeviceSyncs.remove(sync);
    }

    @Override
    public void clearPendingDeviceSyncs() {
        pendingDeviceSyncs.clear();
    }

    @Override
    public void markIdentityChange(Jid deviceJid) {
        unconfirmedIdentityChanges.add(deviceJid);
    }

    @Override
    public void confirmIdentityChange(Jid deviceJid) {
        unconfirmedIdentityChanges.remove(deviceJid);
    }

    @Override
    public Set<Jid> unconfirmedIdentityChanges() {
        return Collections.unmodifiableSet(unconfirmedIdentityChanges);
    }

    @Override
    public void clearUnconfirmedIdentityChanges() {
        unconfirmedIdentityChanges.clear();
    }

    @Override
    public void addToCoexHostedVerificationCache(Jid userJid) {
        if (userJid != null) {
            coexHostedVerificationCache.add(userJid.toUserJid());
        }
    }

    @Override
    public boolean isInCoexHostedVerificationCache(Jid userJid) {
        if (userJid == null) {
            return false;
        }
        return coexHostedVerificationCache.contains(userJid.toUserJid());
    }

    @Override
    public void clearCoexHostedVerificationCache() {
        coexHostedVerificationCache.clear();
    }

    @Override
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

    @Override
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
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof AbstractWhatsAppStore that
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
                            && Objects.equals(contacts, that.contacts)
                            && Objects.equals(calls, that.calls)
                            && Objects.equals(privacySettings, that.privacySettings)
                            && newChatsEphemeralTimer == that.newChatsEphemeralTimer
                            && Objects.equals(webHistoryPolicy, that.webHistoryPolicy)
                            && Objects.equals(registrationId, that.registrationId)
                            && Objects.equals(noiseKeyPair, that.noiseKeyPair)
                            && Objects.equals(identityKeyPair, that.identityKeyPair)
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
                            && encryptionSequence.get() == that.encryptionSequence.get()
                            && Objects.equals(missingSyncKeys, that.missingSyncKeys)
                            && Objects.deepEquals(advSecretKey, that.advSecretKey)
                            && Objects.equals(verifiedBusinessNames, that.verifiedBusinessNames)
                            && Objects.equals(proxy, that.proxy)
                            && Objects.equals(directory, that.directory)
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
                            && Objects.equals(unconfirmedIdentityChanges, that.unconfirmedIdentityChanges)
                            && Objects.equals(coexHostedVerificationCache, that.coexHostedVerificationCache)
                            && Objects.equals(pendingDeviceSyncs, that.pendingDeviceSyncs)
                            && Objects.equals(groupSenderKeyDistribution, that.groupSenderKeyDistribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, phoneNumber, clientType, initializationTimeStamp,
                device, releaseChannel, online, locale, name, verifiedName,
                profilePicture, about, jid, lid, businessAddress, businessLongitude,
                businessLatitude, businessDescription, businessWebsite, businessEmail,
                businessCategory, contacts, calls, privacySettings,
                unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy,
                automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats,
                syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate,
                registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys,
                fdid, Arrays.hashCode(deviceId), advertisingId, Arrays.hashCode(identityId), Arrays.hashCode(backupToken),
                senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers,
                favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime,
                remoteIdentities, identityEncryptionRange, encryptionSequence, missingSyncKeys, Arrays.hashCode(advSecretKey),
                verifiedBusinessNames, proxy, directory, listeners, lidToPhoneMappings, phoneToLidMappings,
                mediaConnection, mediaConnectionLock, offlineResumeState, offlineDeliveryLatch, usersNeedingSenderKeyRotation,
                webAppStatePendingMutations, webAppStateCollections, pendingMessageRecipients, clientVersionLock, chatMetadata,
                deviceLists, unconfirmedIdentityChanges, coexHostedVerificationCache, pendingDeviceSyncs, groupSenderKeyDistribution);
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
}
