package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.bot.AiThreadTitle;
import com.github.auties00.cobalt.model.bot.BotWelcomeRequestState;
import com.github.auties00.cobalt.model.business.AgentState;
import com.github.auties00.cobalt.model.business.BusinessBroadcastCampaign;
import com.github.auties00.cobalt.model.business.BusinessBroadcastInsight;
import com.github.auties00.cobalt.model.business.BusinessBroadcastList;
import com.github.auties00.cobalt.model.business.BusinessCampaignStatus;
import com.github.auties00.cobalt.model.business.BusinessFeatureFlag;
import com.github.auties00.cobalt.model.business.BusinessSubscription;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.MarketingMessage;
import com.github.auties00.cobalt.model.business.MarketingMessageBroadcast;
import com.github.auties00.cobalt.model.business.NoteState;
import com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreference;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.call.IncomingCall;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatAssignment;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.chat.InteractiveMessageState;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.OutContact;
import com.github.auties00.cobalt.model.device.DeviceCapabilities;
import com.github.auties00.cobalt.model.device.DeviceCapabilitiesEntry;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.mixin.PathMixin;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
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
import com.github.auties00.cobalt.proxy.WhatsAppProxy;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.model.sync.action.media.MusicUserIdAction;
import com.github.auties00.cobalt.model.sync.action.media.RecentEmojiWeight;
import com.github.auties00.cobalt.model.sync.action.payment.CustomPaymentMethod;
import com.github.auties00.cobalt.model.sync.action.payment.MerchantPaymentPartnerAction;
import com.github.auties00.cobalt.model.sync.action.payment.PaymentTosAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.sync.key.SyncKeyUtils;
import com.github.auties00.cobalt.util.DataUtils;
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

import com.github.auties00.cobalt.util.StorePathUtils;
import com.github.auties00.cobalt.wam.model.WamChannel;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNullElseGet;

/**
 * Abstract base implementation of the public {@link WhatsAppStore}
 * interface. Holds the storage-agnostic state common to every concrete
 * {@link WhatsAppStore} implementation, defines the protobuf wire layout for
 * that state, and implements every accessor on the {@link WhatsAppStore}
 * interface (and on {@link com.github.auties00.libsignal.SignalProtocolStore},
 * which {@link WhatsAppStore} extends) in terms of in-memory fields.
 *
 * <p>Concrete subclasses inherit the entire field set and the entire body of
 * accessor logic from this class. They are only responsible for deciding
 * <em>how</em> the aggregate is persisted.
 *
 * <p>Every field corresponds to one logical store on WhatsApp Web:
 * <ul>
 *   <li>Scalar protobuf fields (UUID, phone number, locale, profile metadata,
 *       sync flags, AB-props bundle, ADV secret, and so on) flatten the
 *       UserPrefs key-value store and the per-session metadata that WA Web
 *       scatters across {@code WAWebUserPrefsBase}, {@code WAWebUserPrefsKeys}
 *       and {@code WAWebPermanentStorage}.
 *   <li>Typed {@link ConcurrentHashMap} fields (chats are
 *       held by the concrete subclass; contacts, calls, privacy settings,
 *       sender keys, sessions, remote identities, app-state hash states,
 *       missing sync keys, mention-everyone mute expirations, orphan mutation
 *       entries, out-contacts, X3DH base-key dedupe, …) collapse the dozen
 *       IndexedDB databases and the in-memory reactive collections that WA Web
 *       maintains in {@code WAWebModelStorageInitialize} and
 *       {@code WAWebCollections}.
 *   <li>Signal-protocol fields (registration ID, Noise key pair, identity key
 *       pair, signed pre-key, pre-key map, sender keys, sessions, remote
 *       identities, app-state sync keys) are laid out so that
 *       {@link AbstractWhatsAppStore} itself can be used directly as a
 *       {@link com.github.auties00.libsignal.SignalProtocolStore}, mirroring
 *       what WA Web's {@code WAWebSignalStorage} exposes via per-table
 *       accessors.
 *   <li>Per-collection {@code SyncHashValue} entries, orphan mutations and
 *       missing sync keys back the syncd state machine that WA Web drives
 *       from {@code WAWebSyncdCollectionsStateMachine}, {@code WAWebSyncdOrphan}
 *       and {@code WAWebSyncdStoreMissingKeys}.
 * </ul>
 *
 * <p>Persistence boundary. This class never reads from or writes to disk on
 * its own. It exposes everything as in-memory state and leaves
 * {@link WhatsAppStore#save()}, {@link WhatsAppStore#delete()} and
 * {@link WhatsAppStore#await()} unimplemented so that subclasses can choose
 * the persistence strategy (single root protobuf, per-entity files, no
 * persistence at all, …).
 *
 * @see WhatsAppStore
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public abstract class AbstractWhatsAppStore implements WhatsAppStore {
    protected static final String DEFAULT_NAME = "User";
    protected static final int MAX_DEVICE_LISTS = 5000;
    protected static final Duration DEVICE_TTL = Duration.ofDays(1);

    private static final String WAM_BUFFER_PREFIX = "wam_buffer_";

    private static final String WAM_BUFFER_SUFFIX = ".bin";

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    protected final UUID uuid;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    protected Long phoneNumber;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    protected final WhatsAppClientType clientType;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    protected final Instant initializationTimeStamp;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    protected WhatsAppDevice device;

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

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    protected ContactTextStatus selfTextStatus;

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
    protected final ConcurrentHashMap<Jid, ContactTextStatus> contactTextStatuses;

    protected final ConcurrentHashMap<String, IncomingCall> calls;

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

    @ProtobufProperty(index = 63, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<String, Label> labels;

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
    protected final ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames;

    @ProtobufProperty(index = 71, type = ProtobufType.STRING, mixins = {PathMixin.class, ProtobufLazyMixin.class})
    protected final Path directory;

    @ProtobufProperty(index = 72, type = ProtobufType.BOOL)
    protected boolean primaryDeviceSupportsSyncdRecovery;

    @ProtobufProperty(index = 73, type = ProtobufType.BOOL)
    protected boolean disableLinkPreviews;

    @ProtobufProperty(index = 74, type = ProtobufType.BOOL)
    protected boolean relayAllCalls;

    @ProtobufProperty(index = 75, type = ProtobufType.BOOL)
    protected boolean externalWebBeta;

    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    protected ChatLockSettings chatLockSettings;

    @ProtobufProperty(index = 77, type = ProtobufType.STRING)
    protected List<Jid> favoriteChats;

    @ProtobufProperty(index = 78, type = ProtobufType.STRING)
    protected List<String> primaryFeatures;

    @ProtobufProperty(index = 79, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.UINT64)
    protected final ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations;

    @ProtobufProperty(index = 80, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    protected final ConcurrentMap<SyncPatchType, OrphanMutationEntries> orphanMutationEntries;

    @ProtobufProperty(index = 81, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected ConcurrentHashMap<Jid, OutContact> outContacts;

    @ProtobufProperty(index = 84, type = ProtobufType.INT64)
    protected long clockSkewSeconds;

    @ProtobufProperty(index = 85, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    protected Instant groupAbPropsEmergencyPushTimestamp;

    @ProtobufProperty(index = 86, type = ProtobufType.STRING)
    protected String abPropsAbKey;

    @ProtobufProperty(index = 87, type = ProtobufType.STRING)
    protected String abPropsHash;

    @ProtobufProperty(index = 88, type = ProtobufType.INT64)
    protected long abPropsRefresh;

    @ProtobufProperty(index = 89, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    protected Instant abPropsLastSyncTime;

    @ProtobufProperty(index = 90, type = ProtobufType.INT64)
    protected long abPropsRefreshId;

    @ProtobufProperty(index = 91, type = ProtobufType.INT64)
    protected long abPropsWebRefreshId;

    @ProtobufProperty(index = 92, type = ProtobufType.INT64)
    protected long groupAbPropsRefreshId;

    @ProtobufProperty(index = 93, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    protected final ConcurrentMap<String, byte[]> baseKeys;

    @ProtobufProperty(index = 94, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.INT32)
    protected final ConcurrentMap<Integer, Integer> wamSequenceNumbers;

    protected final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange;

    protected final AtomicLong encryptionSequence;

    protected WhatsAppProxy proxy;

    protected final KeySetView<WhatsAppClientListener, Boolean> listeners;

    protected final ConcurrentHashMap<Jid, Jid> lidToPhoneMappings;

    protected final ConcurrentHashMap<Jid, Jid> phoneToLidMappings;

    protected final ConcurrentHashMap<Jid, Instant> lidMappingTimestamps;

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

    protected final ConcurrentMap<Jid, ChatMetadata> chatMetadata;

    protected final ConcurrentLinkedHashMap<Jid, DeviceList> deviceLists;

    protected final Set<Jid> unconfirmedIdentityChanges;

    protected final Set<Jid> interopHostedVerificationCache;

    protected final Set<Jid> utmReadChatIds;

    protected final Set<Jid> blockedContacts;

    protected volatile WaffleAccountLinkStateAction.AccountLinkState linkedMetaAccountState;

    protected volatile Instant linkedMetaAccountStateTimestamp;

    protected volatile boolean hostedAutomationOnboarded;

    protected final ConcurrentLinkedQueue<PendingDeviceSync> pendingDeviceSyncs;

    protected final ConcurrentMap<String, Set<String>> groupSenderKeyDistribution;

    protected final ConcurrentMap<String, OrphanPaymentNotification> orphanPaymentNotifications;

    protected byte[] routingInfo;

    protected String routingDomain;

    protected Instant clientExpiration;

    protected Set<String> tosNoticeIds;

    protected boolean aiAvailable;

    protected String businessOptOutListHash;

    protected ConcurrentMap<String, BusinessFeatureFlag> businessFeatureFlags;

    protected ConcurrentMap<String, BusinessCampaignStatus> businessCampaignStatuses;

    protected ConcurrentMap<String, BusinessSubscription> businessSubscriptions;

    protected String businessAccountNonce;

    protected ConcurrentMap<String, CtwaDataSharingPreference> ctwaDataSharingPreferences;

    protected String smbDataSharingConsent;

    protected boolean detectedOutcomesEnabled;

    protected boolean primaryAllowsAllMutations;

    protected ConcurrentMap<String, AgentState> agentStates;

    protected ConcurrentMap<Jid, ChatAssignment> chatAssignments;

    protected String paymentInstructionCpi;

    protected List<CustomPaymentMethod> customPaymentMethods;

    protected MerchantPaymentPartnerAction merchantPaymentPartner;

    protected PaymentTosAction paymentTos;

    protected ConcurrentMap<String, MarketingMessage> marketingMessages;

    protected ConcurrentMap<String, MarketingMessageBroadcast> marketingMessageBroadcasts;

    protected ConcurrentMap<String, BusinessBroadcastList> businessBroadcastLists;

    protected ConcurrentMap<String, BusinessBroadcastCampaign> businessBroadcastCampaigns;

    protected ConcurrentMap<String, BusinessBroadcastInsight> businessBroadcastInsights;

    protected byte[] notificationContentTokenSalt;

    protected ConcurrentMap<String, OnboardingHintState> onboardingHintStates;

    protected DeviceCapabilities primaryDeviceCapabilities;

    protected ConcurrentMap<Jid, DeviceCapabilitiesEntry> deviceCapabilitiesStates;

    protected ConcurrentMap<String, InteractiveMessageState> interactiveMessageStates;

    protected ConcurrentMap<String, NoteState> noteStates;

    protected ConcurrentMap<Jid, NewsletterPin> newsletterPinStates;

    protected Boolean hasAvatar;

    protected ConcurrentMap<String, CallLog> callLogStates;

    protected ConcurrentMap<Jid, BotWelcomeRequestState> botWelcomeRequestStates;

    protected ConcurrentMap<String, AiThreadTitle> aiThreadTitles;

    protected UsernameChatStartModeAction.ChatStartMode usernameChatStartMode;

    protected NotificationActivitySettingAction.NotificationActivitySetting notificationActivitySetting;

    protected List<RecentEmojiWeight> recentEmojiWeights;

    protected String newsletterSubscriptionUserIdentifier;

    protected MusicUserIdAction musicUserIdState;

    protected String newsletterSavedInterests;

    protected Boolean statusPostOptInNotificationPreferencesEnabled;

    protected PrivateProcessingSettingAction.PrivateProcessingStatus privateProcessingStatus;

    protected Boolean channelsPersonalisedRecommendationOptOut;

    protected byte[] userCreatedBotDefinition;

    protected MaibaAIFeaturesControlAction.MaibaAIFeatureStatus aiBusinessAgentStatus;

    protected Instant pairingTimestamp;

    protected final ConcurrentMap<String, ChatMessageInfo> peerMessages;

    protected final System.Logger logger;

    public AbstractWhatsAppStore(UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, String businessWebsite, String businessEmail, BusinessCategory businessCategory, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations, ConcurrentMap<SyncPatchType, AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, ConcurrentHashMap<Jid, OutContact> outContacts, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeys, ConcurrentMap<Integer, Integer> wamSequenceNumbers) {
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
        this.selfTextStatus = selfTextStatus;
        this.jid = jid;
        this.lid = lid;
        this.contacts = Objects.requireNonNull(contacts, "contacts cannot be null");
        this.contactTextStatuses = new ConcurrentHashMap<>();
        this.outContacts = requireNonNullElseGet(outContacts, ConcurrentHashMap::new);
        this.clockSkewSeconds = clockSkewSeconds;
        this.groupAbPropsEmergencyPushTimestamp = groupAbPropsEmergencyPushTimestamp;
        this.abPropsAbKey = abPropsAbKey;
        this.abPropsHash = abPropsHash;
        this.abPropsRefresh = abPropsRefresh;
        this.abPropsLastSyncTime = abPropsLastSyncTime;
        this.abPropsRefreshId = abPropsRefreshId;
        this.abPropsWebRefreshId = abPropsWebRefreshId;
        this.groupAbPropsRefreshId = groupAbPropsRefreshId;

        this.privacySettings = Objects.requireNonNull(privacySettings, "privacySettings cannot be null");
        this.calls = new ConcurrentHashMap<>();
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
        this.lidMappingTimestamps = new ConcurrentHashMap<>();
        for (var contact : contacts.values()) {
            contact.lid()
                    .ifPresent(entry -> registerLidMapping(contact.jid(), entry));
        }
        this.registrationId = requireNonNullElseGet(registrationId, () -> DataUtils.randomInt(16380) + 1);
        this.noiseKeyPair = requireNonNullElseGet(noiseKeyPair, SignalIdentityKeyPair::random);
        this.identityKeyPair = requireNonNullElseGet(identityKeyPair, SignalIdentityKeyPair::random);
        this.signedKeyPair = requireNonNullElseGet(signedKeyPair, () -> SignalSignedKeyPair.of(this.registrationId, this.identityKeyPair));
        this.preKeys = Objects.requireNonNull(preKeys, "preKeys cannot be null");
        this.fdid = requireNonNullElseGet(fdid, UUID::randomUUID);
        this.deviceId = requireNonNullElseGet(deviceId, () -> HexFormat.of().parseHex(UUID.randomUUID().toString().replace("-", "")));
        this.advertisingId = requireNonNullElseGet(advertisingId, UUID::randomUUID);
        this.identityId = requireNonNullElseGet(identityId, () -> DataUtils.randomByteArray(16));
        this.backupToken = requireNonNullElseGet(backupToken, () -> DataUtils.randomByteArray(20));
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
        this.interopHostedVerificationCache = ConcurrentHashMap.newKeySet();
        this.utmReadChatIds = ConcurrentHashMap.newKeySet();
        this.blockedContacts = ConcurrentHashMap.newKeySet();
        this.pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
        this.groupSenderKeyDistribution = new ConcurrentHashMap<>();
        this.orphanPaymentNotifications = new ConcurrentHashMap<>();
        this.tosNoticeIds = ConcurrentHashMap.newKeySet();
        this.businessFeatureFlags = new ConcurrentHashMap<>();
        this.businessCampaignStatuses = new ConcurrentHashMap<>();
        this.businessSubscriptions = new ConcurrentHashMap<>();
        this.ctwaDataSharingPreferences = new ConcurrentHashMap<>();
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.remoteIdentities = requireNonNullElseGet(remoteIdentities, ConcurrentHashMap::new);
        this.missingSyncKeys = requireNonNullElseGet(missingSyncKeys, ConcurrentHashMap::new);
        this.advSecretKey = advSecretKey;
        this.verifiedBusinessNames = requireNonNullElseGet(verifiedBusinessNames, ConcurrentHashMap::new);
        this.directory = directory;
        this.primaryDeviceSupportsSyncdRecovery = primaryDeviceSupportsSyncdRecovery;
        this.disableLinkPreviews = disableLinkPreviews;
        this.relayAllCalls = relayAllCalls;
        this.externalWebBeta = externalWebBeta;
        this.chatLockSettings = chatLockSettings;
        this.favoriteChats = requireNonNullElseGet(favoriteChats, ArrayList::new);
        this.primaryFeatures = requireNonNullElseGet(primaryFeatures, ArrayList::new);
        this.agentStates = new ConcurrentHashMap<>();
        this.chatAssignments = new ConcurrentHashMap<>();
        this.customPaymentMethods = new ArrayList<>();
        this.marketingMessages = new ConcurrentHashMap<>();
        this.marketingMessageBroadcasts = new ConcurrentHashMap<>();
        this.businessBroadcastLists = new ConcurrentHashMap<>();
        this.businessBroadcastCampaigns = new ConcurrentHashMap<>();
        this.businessBroadcastInsights = new ConcurrentHashMap<>();
        this.onboardingHintStates = new ConcurrentHashMap<>();
        this.deviceCapabilitiesStates = new ConcurrentHashMap<>();
        this.interactiveMessageStates = new ConcurrentHashMap<>();
        this.noteStates = new ConcurrentHashMap<>();
        this.newsletterPinStates = new ConcurrentHashMap<>();
        this.callLogStates = new ConcurrentHashMap<>();
        this.botWelcomeRequestStates = new ConcurrentHashMap<>();
        this.aiThreadTitles = new ConcurrentHashMap<>();
        this.recentEmojiWeights = new CopyOnWriteArrayList<>();
        this.mentionEveryoneMuteExpirations = requireNonNullElseGet(mentionEveryoneMuteExpirations, ConcurrentHashMap::new);
        this.orphanMutationEntries = new ConcurrentHashMap<>();
        this.identityEncryptionRange = new ConcurrentHashMap<>();
        this.baseKeys = requireNonNullElseGet(baseKeys, ConcurrentHashMap::new);
        this.wamSequenceNumbers = requireNonNullElseGet(wamSequenceNumbers, ConcurrentHashMap::new);
        this.encryptionSequence = new AtomicLong();
        this.logger = System.getLogger(this.getClass().getName());
        this.mediaConnectionLock = new Object();
        this.offlineResumeState = WhatsAppClientOfflineResumeState.INIT;
        this.offlineDeliveryLatch = new CountDownLatch(1);
        this.usersNeedingSenderKeyRotation = ConcurrentHashMap.newKeySet();
        this.peerMessages = new ConcurrentHashMap<>();
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
    public Collection<ContactTextStatus> contactTextStatuses() {
        return Collections.unmodifiableCollection(contactTextStatuses.values());
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
    public Optional<ContactTextStatus> findContactTextStatus(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }

        var targetJid = jid.toJid().toUserJid();
        if (targetJid.hasUserServer()) {
            var direct = contactTextStatuses.get(targetJid);
            if (direct != null) {
                return Optional.of(direct);
            }
            return findLidByPhone(targetJid).map(contactTextStatuses::get);
        }

        if (targetJid.hasLidServer()) {
            var direct = contactTextStatuses.get(targetJid);
            if (direct != null) {
                return Optional.of(direct);
            }
            return findPhoneByLid(targetJid).map(contactTextStatuses::get);
        }

        return Optional.ofNullable(contactTextStatuses.get(targetJid));
    }

    @Override
    public void addContactTextStatus(Jid contactJid, ContactTextStatus status) {
        Objects.requireNonNull(contactJid, "contactJid cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        contactTextStatuses.put(contactJid.toUserJid(), status);
    }

    @Override
    public Optional<ContactTextStatus> removeContactTextStatus(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }

        var targetJid = jid.toJid().toUserJid();
        var removed = contactTextStatuses.remove(targetJid);
        if (removed != null) {
            return Optional.of(removed);
        }

        if (targetJid.hasUserServer()) {
            return findLidByPhone(targetJid).map(contactTextStatuses::remove);
        }

        if (targetJid.hasLidServer()) {
            return findPhoneByLid(targetJid).map(contactTextStatuses::remove);
        }

        return Optional.empty();
    }

    @Override
    public Optional<WaffleAccountLinkStateAction.AccountLinkState> linkedMetaAccountState() {
        return Optional.ofNullable(linkedMetaAccountState);
    }

    @Override
    public WhatsAppStore setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState state) {
        this.linkedMetaAccountState = state;
        return this;
    }

    @Override
    public Optional<Instant> linkedMetaAccountStateTimestamp() {
        return Optional.ofNullable(linkedMetaAccountStateTimestamp);
    }

    @Override
    public WhatsAppStore setLinkedMetaAccountStateTimestamp(Instant timestamp) {
        this.linkedMetaAccountStateTimestamp = timestamp;
        return this;
    }

    @Override
    public boolean hostedAutomationOnboarded() {
        return hostedAutomationOnboarded;
    }

    @Override
    public WhatsAppStore setHostedAutomationOnboarded(boolean onboarded) {
        this.hostedAutomationOnboarded = onboarded;
        return this;
    }

    @Override
    public Optional<OrphanPaymentNotification> findOrphanPaymentNotification(String messageId) {
        return messageId == null ? Optional.empty() : Optional.ofNullable(orphanPaymentNotifications.get(messageId));
    }

    @Override
    public void addOrphanPaymentNotification(OrphanPaymentNotification notification) {
        Objects.requireNonNull(notification, "notification cannot be null");
        orphanPaymentNotifications.put(notification.messageId(), notification);
    }

    @Override
    public Optional<OrphanPaymentNotification> removeOrphanPaymentNotification(String messageId) {
        return messageId == null ? Optional.empty() : Optional.ofNullable(orphanPaymentNotifications.remove(messageId));
    }

    @Override
    public Optional<byte[]> routingInfo() {
        return Optional.ofNullable(routingInfo);
    }

    @Override
    public WhatsAppStore setRoutingInfo(byte[] routingInfo) {
        this.routingInfo = routingInfo;
        return this;
    }

    @Override
    public Optional<String> routingDomain() {
        return Optional.ofNullable(routingDomain);
    }

    @Override
    public WhatsAppStore setRoutingDomain(String routingDomain) {
        this.routingDomain = routingDomain;
        return this;
    }

    @Override
    public Optional<Instant> clientExpiration() {
        return Optional.ofNullable(clientExpiration);
    }

    @Override
    public WhatsAppStore setClientExpiration(Instant clientExpiration) {
        this.clientExpiration = clientExpiration;
        return this;
    }

    @Override
    public Set<String> tosNoticeIds() {
        return Collections.unmodifiableSet(tosNoticeIds);
    }

    @Override
    public WhatsAppStore setTosNoticeIds(Set<String> noticeIds) {
        this.tosNoticeIds = noticeIds == null ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet(noticeIds.size());
        if (noticeIds != null) {
            this.tosNoticeIds.addAll(noticeIds);
        }
        return this;
    }

    @Override
    public boolean aiAvailable() {
        return aiAvailable;
    }

    @Override
    public WhatsAppStore setAiAvailable(boolean aiAvailable) {
        this.aiAvailable = aiAvailable;
        return this;
    }

    @Override
    public Optional<String> businessOptOutListHash() {
        return Optional.ofNullable(businessOptOutListHash);
    }

    @Override
    public WhatsAppStore setBusinessOptOutListHash(String hash) {
        this.businessOptOutListHash = hash;
        return this;
    }

    @Override
    public Collection<BusinessFeatureFlag> businessFeatureFlags() {
        return Collections.unmodifiableCollection(businessFeatureFlags.values());
    }

    @Override
    public Optional<BusinessFeatureFlag> findBusinessFeatureFlag(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessFeatureFlags.get(name));
    }

    @Override
    public WhatsAppStore putBusinessFeatureFlag(BusinessFeatureFlag flag) {
        Objects.requireNonNull(flag, "flag cannot be null");
        businessFeatureFlags.put(flag.name(), flag);
        return this;
    }

    @Override
    public Optional<BusinessFeatureFlag> removeBusinessFeatureFlag(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessFeatureFlags.remove(name));
    }

    @Override
    public WhatsAppStore clearBusinessFeatureFlags() {
        businessFeatureFlags.clear();
        return this;
    }

    @Override
    public Collection<BusinessCampaignStatus> businessCampaignStatuses() {
        return Collections.unmodifiableCollection(businessCampaignStatuses.values());
    }

    @Override
    public Optional<BusinessCampaignStatus> findBusinessCampaignStatus(String campaignId) {
        if (campaignId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessCampaignStatuses.get(campaignId));
    }

    @Override
    public WhatsAppStore putBusinessCampaignStatus(BusinessCampaignStatus status) {
        Objects.requireNonNull(status, "status cannot be null");
        businessCampaignStatuses.put(status.campaignId(), status);
        return this;
    }

    @Override
    public Optional<BusinessCampaignStatus> removeBusinessCampaignStatus(String campaignId) {
        if (campaignId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessCampaignStatuses.remove(campaignId));
    }

    @Override
    public WhatsAppStore clearBusinessCampaignStatuses() {
        businessCampaignStatuses.clear();
        return this;
    }

    @Override
    public Collection<BusinessSubscription> businessSubscriptions() {
        return Collections.unmodifiableCollection(businessSubscriptions.values());
    }

    @Override
    public Optional<BusinessSubscription> findBusinessSubscription(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessSubscriptions.get(id));
    }

    @Override
    public WhatsAppStore putBusinessSubscription(BusinessSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        businessSubscriptions.put(subscription.id(), subscription);
        return this;
    }

    @Override
    public Optional<BusinessSubscription> removeBusinessSubscription(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessSubscriptions.remove(id));
    }

    @Override
    public WhatsAppStore clearBusinessSubscriptions() {
        businessSubscriptions.clear();
        return this;
    }

    @Override
    public Optional<String> businessAccountNonce() {
        return Optional.ofNullable(businessAccountNonce);
    }

    @Override
    public WhatsAppStore setBusinessAccountNonce(String nonce) {
        this.businessAccountNonce = nonce;
        return this;
    }

    @Override
    public Collection<CtwaDataSharingPreference> ctwaDataSharingPreferences() {
        return Collections.unmodifiableCollection(ctwaDataSharingPreferences.values());
    }

    @Override
    public Optional<CtwaDataSharingPreference> findCtwaDataSharing(String accountLid) {
        if (accountLid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctwaDataSharingPreferences.get(accountLid));
    }

    @Override
    public WhatsAppStore putCtwaDataSharing(CtwaDataSharingPreference preference) {
        Objects.requireNonNull(preference, "preference cannot be null");
        ctwaDataSharingPreferences.put(preference.accountLid(), preference);
        return this;
    }

    @Override
    public Optional<String> smbDataSharingConsent() {
        return Optional.ofNullable(smbDataSharingConsent);
    }

    @Override
    public WhatsAppStore setSmbDataSharingConsent(String consent) {
        this.smbDataSharingConsent = consent;
        return this;
    }

    @Override
    public Optional<CtwaDataSharingPreference> removeCtwaDataSharing(String accountLid) {
        if (accountLid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctwaDataSharingPreferences.remove(accountLid));
    }

    @Override
    public WhatsAppStore clearCtwaDataSharing() {
        ctwaDataSharingPreferences.clear();
        return this;
    }

    @Override
    public boolean detectedOutcomesEnabled() {
        return detectedOutcomesEnabled;
    }

    @Override
    public WhatsAppStore setDetectedOutcomesEnabled(boolean enabled) {
        this.detectedOutcomesEnabled = enabled;
        return this;
    }

    protected ConcurrentHashMap<Jid, OutContact> outContactsField() {
        return outContacts;
    }

    @Override
    public Collection<OutContact> outContacts() {
        return Collections.unmodifiableCollection(outContacts.values());
    }

    @Override
    public Optional<OutContact> findOutContact(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(outContacts.get(jid));
    }

    @Override
    public WhatsAppStore addOutContact(OutContact outContact) {
        Objects.requireNonNull(outContact, "outContact cannot be null");
        outContacts.merge(outContact.jid(), outContact, (existing, incoming) -> {
            existing.setFullName(incoming.fullName().orElse(null));
            existing.setFirstName(incoming.firstName().orElse(null));
            return existing;
        });
        return this;
    }

    @Override
    public Optional<OutContact> removeOutContact(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(outContacts.remove(jid));
    }

    @Override
    public WhatsAppStore clearOutContacts() {
        outContacts.clear();
        return this;
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
        registerLidMapping(phoneJid, lidJid, null);
    }

    @Override
    public void registerLidMapping(Jid phoneJid, Jid lidJid, Instant timestamp) {
        if (phoneJid == null || lidJid == null) {
            return;
        }
        var normalizedPhone = phoneJid.withoutData();
        var normalizedLid = lidJid.withoutData();
        if (timestamp != null) {
            var existing = lidMappingTimestamps.get(normalizedLid);
            if (existing != null && timestamp.isBefore(existing)) {
                return;
            }
            lidMappingTimestamps.put(normalizedLid, timestamp);
        }
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
    public Optional<IncomingCall> findCallById(String callId) {
        return callId == null
                ? Optional.empty()
                : Optional.ofNullable(calls.get(callId));
    }

    @Override
    public IncomingCall addCall(IncomingCall call) {
        Objects.requireNonNull(call, "call cannot be null");
        calls.put(call.callId(), call);
        return call;
    }

    @Override
    public Optional<IncomingCall> removeCall(String id) {
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
    public Optional<WhatsAppProxy> proxy() {
        return Optional.ofNullable(proxy);
    }

    @Override
    public WhatsAppStore setProxy(WhatsAppProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public WhatsAppDevice device() {
        return device;
    }

    @Override
    public WhatsAppStore setDevice(WhatsAppDevice device) {
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
    public Optional<ContactTextStatus> selfTextStatus() {
        return Optional.ofNullable(selfTextStatus);
    }

    @Override
    public WhatsAppStore setSelfTextStatus(ContactTextStatus selfTextStatus) {
        this.selfTextStatus = selfTextStatus;
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
    public Collection<IncomingCall> calls() {
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
    public boolean primaryDeviceSupportsSyncdRecovery() {
        return primaryDeviceSupportsSyncdRecovery;
    }

    @Override
    public WhatsAppStore setPrimaryDeviceSupportsSyncdRecovery(boolean supported) {
        this.primaryDeviceSupportsSyncdRecovery = supported;
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

    private static String encodeBaseKeyKey(SignalProtocolAddress address, String originalMsgId) {
        return address.toString() + "|" + originalMsgId;
    }

    @Override
    public void saveSessionBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] baseKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(baseKey, "baseKey cannot be null");
        baseKeys.put(encodeBaseKeyKey(address, originalMsgId), baseKey);
    }

    @Override
    public Optional<byte[]> findSessionBaseKey(SignalProtocolAddress address, String originalMsgId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        return Optional.ofNullable(baseKeys.get(encodeBaseKeyKey(address, originalMsgId)));
    }

    @Override
    public boolean hasSameBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] candidate) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");
        var stored = baseKeys.get(encodeBaseKeyKey(address, originalMsgId));
        return stored != null && Arrays.equals(stored, candidate);
    }

    @Override
    public boolean removeSessionBaseKey(SignalProtocolAddress address, String originalMsgId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        return baseKeys.remove(encodeBaseKeyKey(address, originalMsgId)) != null;
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
            var hasKeyData = key.keyData()
                    .flatMap(AppStateSyncKeyData::keyData)
                    .map(data -> data.length > 0)
                    .orElse(false);
            if (!hasKeyData) {
                continue;
            }
            key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .ifPresent(keyId -> appStateKeys.put(HexFormat.of().formatHex(keyId), key));
        }
    }

    @Override
    public void expireAppStateKeys(Instant threshold) {
        for (var entry : appStateKeys.entrySet()) {
            var key = entry.getValue();
            var timestamp = key.keyData()
                    .flatMap(AppStateSyncKeyData::timestamp)
                    .orElse(null);
            if (timestamp != null && !timestamp.isAfter(threshold)) {
                key.keyData().ifPresent(data -> data.setTimestamp(Instant.EPOCH));
            }
        }
    }

    @Override
    public void expireAppStateKeysByEpoch(int epoch) {
        for (var key : appStateKeys.values()) {
            if (SyncKeyUtils.getKeyEpoch(key) != epoch) {
                continue;
            }

            key.keyData().ifPresent(data -> data.setTimestamp(Instant.EPOCH));
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
    public Optional<SyncActionEntry> findSyncActionEntryByActionIndex(SyncPatchType patchType, String actionIndex) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return Optional.empty();
        }
        return inner.values()
                .stream()
                .filter(entry -> actionIndex.equals(entry.actionIndex()))
                .findFirst();
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
    public Collection<SyncActionEntry> getSyncActionEntries(SyncPatchType patchType) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return List.of();
        }
        return Collections.unmodifiableCollection(inner.values());
    }

    @Override
    public int countSyncActionEntries() {
        var total = 0;
        for (var inner : syncActionEntries.values()) {
            total += inner.size();
        }
        return total;
    }

    @Override
    public Collection<SyncActionEntry> getAllSyncActionEntries() {
        if (syncActionEntries.isEmpty()) {
            return List.of();
        }
        var all = new ArrayList<SyncActionEntry>();
        for (var inner : syncActionEntries.values()) {
            all.addAll(inner.values());
        }
        return Collections.unmodifiableCollection(all);
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
    public int missingSyncKeyCount() {
        return missingSyncKeys.size();
    }

    @Override
    public void addMissingSyncKey(MissingDeviceSyncKey missingKey) {
        missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
    }

    @Override
    public void addMissingSyncKeys(Collection<MissingDeviceSyncKey> missingKeys) {
        Objects.requireNonNull(missingKeys, "missingKeys cannot be null");
        for (var missingKey : missingKeys) {
            this.missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
        }
    }

    @Override
    public void removeMissingSyncKey(byte[] keyId) {
        missingSyncKeys.remove(HexFormat.of().formatHex(keyId));
    }

    @Override
    public void addPeerMessage(String id, ChatMessageInfo message) {
        peerMessages.put(id, message);
    }

    @Override
    public void removePeerMessage(String id) {
        peerMessages.remove(id);
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
                .computeIfAbsent(collectionName, _ -> new ConcurrentLinkedDeque<>())
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
    public void removePendingMutations(SyncPatchType collectionName, Collection<String> mutationIds) {
        if (mutationIds == null || mutationIds.isEmpty()) {
            return;
        }

        var mutationIdsSet = mutationIds instanceof Set<String> ids
                ? ids
                : new HashSet<>(mutationIds);
        webAppStatePendingMutations.computeIfPresent(collectionName, (_, pendingMutations) -> {
            pendingMutations.removeIf(pendingMutation -> mutationIdsSet.contains(pendingMutation.mutationId()));
            return pendingMutations.isEmpty() ? null : pendingMutations;
        });
    }

    @Override
    public void clearPendingMutations(SyncPatchType collectionName) {
        webAppStatePendingMutations.remove(collectionName);
    }

    @Override
    public void addOrphanMutation(SyncPatchType collectionName, OrphanMutationEntry mutation) {
        orphanMutationEntries.computeIfAbsent(collectionName, _ -> new OrphanMutationEntries())
                .data()
                .add(mutation);
    }

    @Override
    public List<OrphanMutationEntry> findOrphanMutations(SyncPatchType collectionName) {
        var entries = orphanMutationEntries.get(collectionName);
        if (entries == null || entries.data().isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries.data());
    }

    @Override
    public List<OrphanMutationEntry> findOrphanMutationsByModel(SyncPatchType collectionName, String modelId) {
        var entries = orphanMutationEntries.get(collectionName);
        if (entries == null || entries.data().isEmpty() || modelId == null) {
            return List.of();
        }
        return entries.data()
                .stream()
                .filter(e -> modelId.equals(e.modelId()))
                .toList();
    }

    @Override
    public void removeOrphanMutations(SyncPatchType collectionName) {
        orphanMutationEntries.remove(collectionName);
    }

    @Override
    public void removeOrphanMutations(SyncPatchType collectionName, Collection<OrphanMutationEntry> entries) {
        var data = orphanMutationEntries.get(collectionName);
        if (data != null) {
            data.data().removeAll(entries);
        }
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

    @Override
    public void markWebAppStateDirty(SyncPatchType collectionName) {
        webAppStateCollections.compute(collectionName, (_, current) -> {
            if (current == null) {
                return new SyncCollectionMetadataBuilder()
                        .name(collectionName)
                        .version(0)
                        .ltHash(MutationLTHash.copy(MutationLTHash.EMPTY_HASH))
                        .lastSyncTimestamp(null)
                        .state(SyncCollectionState.DIRTY)
                        .retryCount(0)
                        .lastErrorTimestamp(null)
                        .macMismatch(false)
                        .bootstrapped(false)
                        .build();
            }
            return new SyncCollectionMetadataBuilder()
                    .name(current.name())
                    .version(current.version())
                    .ltHash(current.ltHash())
                    .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                    .state(SyncCollectionState.DIRTY)
                    .retryCount(current.retryCount())
                    .lastErrorTimestamp(current.lastErrorTimestamp().orElse(null))
                    .macMismatch(current.macMismatch())
                    .bootstrapped(current.bootstrapped())
                    .build();
        });
    }

    @Override
    public void markWebAppStateInFlight(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(SyncCollectionState.IN_FLIGHT)
                        .retryCount(current.retryCount())
                        .lastErrorTimestamp(current.lastErrorTimestamp().orElse(null))
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public void markWebAppStateUpToDate(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(Instant.now())
                        .state(SyncCollectionState.UP_TO_DATE)
                        .retryCount(0)
                        .lastErrorTimestamp(null)
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public void markWebAppStatePending(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(SyncCollectionState.PENDING)
                        .retryCount(current.retryCount())
                        .lastErrorTimestamp(current.lastErrorTimestamp().orElse(null))
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public void markWebAppStateBlocked(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(SyncCollectionState.BLOCKED)
                        .retryCount(current.retryCount())
                        .lastErrorTimestamp(current.lastErrorTimestamp().orElse(null))
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public void markWebAppStateErrorRetry(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(SyncCollectionState.ERROR_RETRY)
                        .retryCount(current.retryCount() + 1)
                        .lastErrorTimestamp(current.lastErrorTimestamp().orElseGet(Instant::now))
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public void markWebAppStateErrorFatal(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(SyncCollectionState.ERROR_FATAL)
                        .retryCount(current.retryCount())
                        .lastErrorTimestamp(null)
                        .macMismatch(current.macMismatch())
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public boolean isCollectionInMacMismatchFatal(SyncPatchType collectionName) {
        var current = webAppStateCollections.get(collectionName);
        return current != null && current.macMismatch();
    }

    @Override
    public void markWebAppStateMacMismatch(SyncPatchType collectionName) {
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(current.name())
                        .version(current.version())
                        .ltHash(current.ltHash())
                        .lastSyncTimestamp(current.lastSyncTimestamp().orElse(null))
                        .state(current.state())
                        .retryCount(current.retryCount())
                        .lastErrorTimestamp(current.lastErrorTimestamp().orElse(null))
                        .macMismatch(true)
                        .bootstrapped(current.bootstrapped())
                        .build()
        );
    }

    @Override
    public SyncCollectionMetadata findWebAppState(SyncPatchType collectionName) {
        return webAppStateCollections.computeIfAbsent(collectionName, key ->
                new SyncCollectionMetadataBuilder()
                        .name(key)
                        .version(0)
                        .ltHash(MutationLTHash.copy(MutationLTHash.EMPTY_HASH))
                        .lastSyncTimestamp(null)
                        .state(SyncCollectionState.UP_TO_DATE)
                        .retryCount(0)
                        .lastErrorTimestamp(null)
                        .macMismatch(false)
                        .bootstrapped(false)
                        .build()
        );
    }

    @Override
    public void updateWebAppStateVersion(SyncPatchType collectionName, long newVersion, byte[] newLtHash) {
        var copiedHash = MutationLTHash.copy(newLtHash);
        webAppStateCollections.compute(collectionName, (_, current) ->
                new SyncCollectionMetadataBuilder()
                        .name(collectionName)
                        .version(newVersion)
                        .ltHash(copiedHash)
                        .lastSyncTimestamp(Instant.now())
                        .state(current != null ? current.state() : SyncCollectionState.UP_TO_DATE)
                        .retryCount(0)
                        .lastErrorTimestamp(null)
                        .macMismatch(current != null && current.macMismatch())
                        .bootstrapped(true)
                        .build()
        );
        var hashState = new SyncHashValue(collectionName);
        hashState.setVersion(newVersion);
        hashState.setHash(copiedHash);
        hashStates.put(collectionName, hashState);
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
    public Collection<Label> labels() {
        return Collections.unmodifiableCollection(labels.values());
    }

    @Override
    public Optional<Label> removeLabel(String labelId) {
        return Optional.ofNullable(labels.remove(labelId));
    }

    @Override
    public Optional<Label> findLabel(String labelId) {
        return Optional.ofNullable(labels.get(labelId));
    }

    @Override
    public Optional<QuickReply> findQuickReply(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(quickReplies.get(id));
    }

    @Override
    public void addQuickReply(QuickReply quickReply) {
        Objects.requireNonNull(quickReply, "quickReply cannot be null");
        quickReplies.put(quickReply.id(), quickReply);
    }

    @Override
    public Optional<QuickReply> removeQuickReply(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(quickReplies.remove(id));
    }

    @Override
    public List<QuickReply> quickReplies() {
        return List.copyOf(quickReplies.values());
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
    public Optional<ChatMetadata> findChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        return Optional.ofNullable(chatMetadata.get(groupJid));
    }

    @Override
    public void addChatMetadata(ChatMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        chatMetadata.put(metadata.jid(), metadata);
    }

    @Override
    public void removeChatMetadata(Jid groupJid) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        chatMetadata.remove(groupJid);
    }

    @Override
    public Optional<BusinessVerifiedName> findVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return Optional.ofNullable(verifiedBusinessNames.get(jid.toUserJid()));
    }

    @Override
    public void addVerifiedBusinessName(Jid jid, BusinessVerifiedName record) {
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
    public void addToInteropHostedVerificationCache(Jid userJid) {
        if (userJid != null) {
            interopHostedVerificationCache.add(userJid.toUserJid());
        }
    }

    @Override
    public boolean isInInteropHostedVerificationCache(Jid userJid) {
        if (userJid == null) {
            return false;
        }
        return interopHostedVerificationCache.contains(userJid.toUserJid());
    }

    @Override
    public void clearInteropHostedVerificationCache() {
        interopHostedVerificationCache.clear();
    }

    @Override
    public void markUtmReadForChat(Jid chatJid) {
        if (chatJid != null) {
            utmReadChatIds.add(chatJid);
        }
    }

    @Override
    public boolean hasReadUtmForChat(Jid chatJid) {
        if (chatJid == null) {
            return false;
        }
        return utmReadChatIds.contains(chatJid);
    }

    @Override
    public void deleteUtmReadChatId(Jid chatJid) {
        if (chatJid != null) {
            utmReadChatIds.remove(chatJid);
        }
    }

    @Override
    public void clearUtmReadChatIds() {
        utmReadChatIds.clear();
    }

    @Override
    public Set<Jid> blockedContacts() {
        return Collections.unmodifiableSet(blockedContacts);
    }

    @Override
    public void addBlockedContact(Jid contact) {
        if (contact != null) {
            blockedContacts.add(contact.toUserJid());
        }
    }

    @Override
    public void removeBlockedContact(Jid contact) {
        if (contact != null) {
            blockedContacts.remove(contact.toUserJid());
        }
    }

    @Override
    public void setBlockedContacts(Collection<Jid> contacts) {
        blockedContacts.clear();
        if (contacts != null) {
            for (var contact : contacts) {
                if (contact != null) {
                    blockedContacts.add(contact.toUserJid());
                }
            }
        }
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
    public boolean disableLinkPreviews() {
        return disableLinkPreviews;
    }

    @Override
    public WhatsAppStore setDisableLinkPreviews(boolean disableLinkPreviews) {
        this.disableLinkPreviews = disableLinkPreviews;
        return this;
    }

    @Override
    public boolean relayAllCalls() {
        return relayAllCalls;
    }

    @Override
    public WhatsAppStore setRelayAllCalls(boolean relayAllCalls) {
        this.relayAllCalls = relayAllCalls;
        return this;
    }

    @Override
    public boolean externalWebBeta() {
        return externalWebBeta;
    }

    @Override
    public WhatsAppStore setExternalWebBeta(boolean externalWebBeta) {
        this.externalWebBeta = externalWebBeta;
        return this;
    }

    @Override
    public Optional<ChatLockSettings> chatLockSettings() {
        return Optional.ofNullable(chatLockSettings);
    }

    @Override
    public WhatsAppStore setChatLockSettings(ChatLockSettings chatLockSettings) {
        this.chatLockSettings = chatLockSettings;
        return this;
    }

    @Override
    public List<Jid> favoriteChats() {
        return Collections.unmodifiableList(favoriteChats);
    }

    @Override
    public WhatsAppStore setFavoriteChats(List<Jid> favoriteChats) {
        this.favoriteChats = new ArrayList<>(Objects.requireNonNull(favoriteChats, "favoriteChats cannot be null"));
        return this;
    }

    @Override
    public List<String> primaryFeatures() {
        return Collections.unmodifiableList(primaryFeatures);
    }

    @Override
    public WhatsAppStore setPrimaryFeatures(List<String> primaryFeatures) {
        this.primaryFeatures = new ArrayList<>(Objects.requireNonNull(primaryFeatures, "primaryFeatures cannot be null"));
        return this;
    }

    @Override
    public boolean primaryAllowsAllMutations() {
        return primaryAllowsAllMutations;
    }

    @Override
    public WhatsAppStore setPrimaryAllowsAllMutations(boolean primaryAllowsAllMutations) {
        this.primaryAllowsAllMutations = primaryAllowsAllMutations;
        return this;
    }

    @Override
    public Collection<AgentState> agentStates() {
        return Collections.unmodifiableCollection(agentStates.values());
    }

    @Override
    public Optional<AgentState> findAgentState(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agentStates.get(agentId));
    }

    @Override
    public WhatsAppStore putAgentState(AgentState state) {
        Objects.requireNonNull(state, "state cannot be null");
        agentStates.put(state.agentId(), state);
        return this;
    }

    @Override
    public Optional<AgentState> removeAgentState(String agentId) {
        if (agentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(agentStates.remove(agentId));
    }

    @Override
    public WhatsAppStore clearAgentStates() {
        agentStates.clear();
        return this;
    }

    @Override
    public Collection<ChatAssignment> chatAssignments() {
        return Collections.unmodifiableCollection(chatAssignments.values());
    }

    @Override
    public Optional<ChatAssignment> findChatAssignment(Jid chatJid) {
        if (chatJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(chatAssignments.get(chatJid));
    }

    @Override
    public WhatsAppStore putChatAssignment(ChatAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");
        chatAssignments.put(assignment.chatJid(), assignment);
        return this;
    }

    @Override
    public Optional<ChatAssignment> removeChatAssignment(Jid chatJid) {
        if (chatJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(chatAssignments.remove(chatJid));
    }

    @Override
    public WhatsAppStore clearChatAssignments() {
        chatAssignments.clear();
        return this;
    }

    @Override
    public Optional<String> paymentInstructionCpi() {
        return Optional.ofNullable(paymentInstructionCpi);
    }

    @Override
    public WhatsAppStore setPaymentInstructionCpi(String cpi) {
        this.paymentInstructionCpi = cpi;
        return this;
    }

    @Override
    public List<CustomPaymentMethod> customPaymentMethods() {
        return Collections.unmodifiableList(customPaymentMethods);
    }

    @Override
    public WhatsAppStore setCustomPaymentMethods(List<CustomPaymentMethod> methods) {
        this.customPaymentMethods = new ArrayList<>(Objects.requireNonNull(methods, "methods cannot be null"));
        return this;
    }

    @Override
    public Optional<MerchantPaymentPartnerAction> merchantPaymentPartner() {
        return Optional.ofNullable(merchantPaymentPartner);
    }

    @Override
    public WhatsAppStore setMerchantPaymentPartner(MerchantPaymentPartnerAction partner) {
        this.merchantPaymentPartner = partner;
        return this;
    }

    @Override
    public Optional<PaymentTosAction> paymentTos() {
        return Optional.ofNullable(paymentTos);
    }

    @Override
    public WhatsAppStore setPaymentTos(PaymentTosAction tos) {
        this.paymentTos = tos;
        return this;
    }

    @Override
    public Collection<MarketingMessage> marketingMessages() {
        return Collections.unmodifiableCollection(marketingMessages.values());
    }

    @Override
    public Optional<MarketingMessage> findMarketingMessage(String templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(marketingMessages.get(templateId));
    }

    @Override
    public WhatsAppStore putMarketingMessage(MarketingMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        marketingMessages.put(message.templateId(), message);
        return this;
    }

    @Override
    public Optional<MarketingMessage> removeMarketingMessage(String templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(marketingMessages.remove(templateId));
    }

    @Override
    public WhatsAppStore clearMarketingMessages() {
        marketingMessages.clear();
        return this;
    }

    @Override
    public Collection<MarketingMessageBroadcast> marketingMessageBroadcasts() {
        return Collections.unmodifiableCollection(marketingMessageBroadcasts.values());
    }

    @Override
    public Optional<MarketingMessageBroadcast> findMarketingMessageBroadcast(String templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(marketingMessageBroadcasts.get(templateId));
    }

    @Override
    public WhatsAppStore putMarketingMessageBroadcast(MarketingMessageBroadcast broadcast) {
        Objects.requireNonNull(broadcast, "broadcast cannot be null");
        marketingMessageBroadcasts.put(broadcast.templateId(), broadcast);
        return this;
    }

    @Override
    public Optional<MarketingMessageBroadcast> removeMarketingMessageBroadcast(String templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(marketingMessageBroadcasts.remove(templateId));
    }

    @Override
    public WhatsAppStore clearMarketingMessageBroadcasts() {
        marketingMessageBroadcasts.clear();
        return this;
    }

    @Override
    public Collection<BusinessBroadcastList> businessBroadcastLists() {
        return Collections.unmodifiableCollection(businessBroadcastLists.values());
    }

    @Override
    public Optional<BusinessBroadcastList> findBusinessBroadcastList(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastLists.get(id));
    }

    @Override
    public WhatsAppStore putBusinessBroadcastList(BusinessBroadcastList list) {
        Objects.requireNonNull(list, "list cannot be null");
        businessBroadcastLists.put(list.id(), list);
        return this;
    }

    @Override
    public Optional<BusinessBroadcastList> removeBusinessBroadcastList(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastLists.remove(id));
    }

    @Override
    public WhatsAppStore clearBusinessBroadcastLists() {
        businessBroadcastLists.clear();
        return this;
    }

    @Override
    public SequencedCollection<Jid> broadcasts() {
        return businessBroadcastLists.keySet().stream()
                .map(id -> Jid.of(id, JidServer.broadcast()))
                .toList();
    }

    @Override
    public Collection<BusinessBroadcastCampaign> businessBroadcastCampaigns() {
        return Collections.unmodifiableCollection(businessBroadcastCampaigns.values());
    }

    @Override
    public Optional<BusinessBroadcastCampaign> findBusinessBroadcastCampaign(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastCampaigns.get(id));
    }

    @Override
    public WhatsAppStore putBusinessBroadcastCampaign(BusinessBroadcastCampaign campaign) {
        Objects.requireNonNull(campaign, "campaign cannot be null");
        businessBroadcastCampaigns.put(campaign.id(), campaign);
        return this;
    }

    @Override
    public Optional<BusinessBroadcastCampaign> removeBusinessBroadcastCampaign(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastCampaigns.remove(id));
    }

    @Override
    public WhatsAppStore clearBusinessBroadcastCampaigns() {
        businessBroadcastCampaigns.clear();
        return this;
    }

    @Override
    public Collection<BusinessBroadcastInsight> businessBroadcastInsights() {
        return Collections.unmodifiableCollection(businessBroadcastInsights.values());
    }

    @Override
    public Optional<BusinessBroadcastInsight> findBusinessBroadcastInsight(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastInsights.get(id));
    }

    @Override
    public WhatsAppStore putBusinessBroadcastInsight(BusinessBroadcastInsight insight) {
        Objects.requireNonNull(insight, "insight cannot be null");
        businessBroadcastInsights.put(insight.id(), insight);
        return this;
    }

    @Override
    public Optional<BusinessBroadcastInsight> removeBusinessBroadcastInsight(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(businessBroadcastInsights.remove(id));
    }

    @Override
    public WhatsAppStore clearBusinessBroadcastInsights() {
        businessBroadcastInsights.clear();
        return this;
    }

    @Override
    public Optional<byte[]> notificationContentTokenSalt() {
        return Optional.ofNullable(notificationContentTokenSalt);
    }

    @Override
    public WhatsAppStore setNotificationContentTokenSalt(byte[] salt) {
        this.notificationContentTokenSalt = salt;
        return this;
    }

    @Override
    public Collection<OnboardingHintState> onboardingHintStates() {
        return Collections.unmodifiableCollection(onboardingHintStates.values());
    }

    @Override
    public Optional<OnboardingHintState> findOnboardingHintState(String hintId) {
        if (hintId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(onboardingHintStates.get(hintId));
    }

    @Override
    public WhatsAppStore putOnboardingHintState(OnboardingHintState state) {
        Objects.requireNonNull(state, "state cannot be null");
        onboardingHintStates.put(state.hintId(), state);
        return this;
    }

    @Override
    public Optional<OnboardingHintState> removeOnboardingHintState(String hintId) {
        if (hintId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(onboardingHintStates.remove(hintId));
    }

    @Override
    public WhatsAppStore clearOnboardingHintStates() {
        onboardingHintStates.clear();
        return this;
    }

    @Override
    public Optional<DeviceCapabilities> primaryDeviceCapabilities() {
        return Optional.ofNullable(primaryDeviceCapabilities);
    }

    @Override
    public WhatsAppStore setPrimaryDeviceCapabilities(DeviceCapabilities capabilities) {
        this.primaryDeviceCapabilities = capabilities;
        return this;
    }

    @Override
    public Collection<DeviceCapabilitiesEntry> deviceCapabilitiesStates() {
        return Collections.unmodifiableCollection(deviceCapabilitiesStates.values());
    }

    @Override
    public Optional<DeviceCapabilitiesEntry> findDeviceCapabilitiesEntry(Jid deviceJid) {
        if (deviceJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(deviceCapabilitiesStates.get(deviceJid));
    }

    @Override
    public WhatsAppStore putDeviceCapabilitiesEntry(DeviceCapabilitiesEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        deviceCapabilitiesStates.put(entry.deviceJid(), entry);
        return this;
    }

    @Override
    public Optional<DeviceCapabilitiesEntry> removeDeviceCapabilitiesEntry(Jid deviceJid) {
        if (deviceJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(deviceCapabilitiesStates.remove(deviceJid));
    }

    @Override
    public WhatsAppStore clearDeviceCapabilitiesStates() {
        deviceCapabilitiesStates.clear();
        return this;
    }

    @Override
    public Collection<InteractiveMessageState> interactiveMessageStates() {
        return Collections.unmodifiableCollection(interactiveMessageStates.values());
    }

    @Override
    public Optional<InteractiveMessageState> findInteractiveMessageState(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(interactiveMessageStates.get(messageId));
    }

    @Override
    public WhatsAppStore putInteractiveMessageState(InteractiveMessageState state) {
        Objects.requireNonNull(state, "state cannot be null");
        interactiveMessageStates.put(state.messageId(), state);
        return this;
    }

    @Override
    public Optional<InteractiveMessageState> removeInteractiveMessageState(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(interactiveMessageStates.remove(messageId));
    }

    @Override
    public WhatsAppStore clearInteractiveMessageStates() {
        interactiveMessageStates.clear();
        return this;
    }

    @Override
    public Collection<NoteState> noteStates() {
        return Collections.unmodifiableCollection(noteStates.values());
    }

    @Override
    public Optional<NoteState> findNoteState(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(noteStates.get(id));
    }

    @Override
    public WhatsAppStore putNoteState(NoteState state) {
        Objects.requireNonNull(state, "state cannot be null");
        noteStates.put(state.id(), state);
        return this;
    }

    @Override
    public Optional<NoteState> removeNoteState(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(noteStates.remove(id));
    }

    @Override
    public WhatsAppStore clearNoteStates() {
        noteStates.clear();
        return this;
    }

    @Override
    public Collection<NewsletterPin> newsletterPinStates() {
        return Collections.unmodifiableCollection(newsletterPinStates.values());
    }

    @Override
    public Optional<NewsletterPin> findNewsletterPin(Jid newsletterJid) {
        if (newsletterJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(newsletterPinStates.get(newsletterJid));
    }

    @Override
    public WhatsAppStore putNewsletterPin(NewsletterPin pin) {
        Objects.requireNonNull(pin, "pin cannot be null");
        newsletterPinStates.put(pin.newsletterJid(), pin);
        return this;
    }

    @Override
    public Optional<NewsletterPin> removeNewsletterPin(Jid newsletterJid) {
        if (newsletterJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(newsletterPinStates.remove(newsletterJid));
    }

    @Override
    public WhatsAppStore clearNewsletterPins() {
        newsletterPinStates.clear();
        return this;
    }

    @Override
    public Optional<Boolean> hasAvatar() {
        return Optional.ofNullable(hasAvatar);
    }

    @Override
    public WhatsAppStore setHasAvatar(Boolean hasAvatar) {
        this.hasAvatar = hasAvatar;
        return this;
    }

    @Override
    public Collection<CallLog> callLogStates() {
        return Collections.unmodifiableCollection(callLogStates.values());
    }

    @Override
    public Optional<CallLog> findCallLog(String callId) {
        if (callId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(callLogStates.get(callId));
    }

    @Override
    public WhatsAppStore addCallLog(CallLog callLog) {
        Objects.requireNonNull(callLog, "callLog cannot be null");
        var callId = callLog.callId().orElseThrow(() -> new NullPointerException("callLog must have a callId"));
        callLogStates.put(callId, callLog);
        return this;
    }

    @Override
    public Optional<CallLog> removeCallLog(String callId) {
        if (callId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(callLogStates.remove(callId));
    }

    @Override
    public WhatsAppStore clearCallLogs() {
        callLogStates.clear();
        return this;
    }

    @Override
    public Collection<BotWelcomeRequestState> botWelcomeRequestStates() {
        return Collections.unmodifiableCollection(botWelcomeRequestStates.values());
    }

    @Override
    public Optional<BotWelcomeRequestState> findBotWelcomeRequestState(Jid botJid) {
        if (botJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(botWelcomeRequestStates.get(botJid));
    }

    @Override
    public WhatsAppStore putBotWelcomeRequestState(BotWelcomeRequestState state) {
        Objects.requireNonNull(state, "state cannot be null");
        botWelcomeRequestStates.put(state.botJid(), state);
        return this;
    }

    @Override
    public Optional<BotWelcomeRequestState> removeBotWelcomeRequestState(Jid botJid) {
        if (botJid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(botWelcomeRequestStates.remove(botJid));
    }

    @Override
    public WhatsAppStore clearBotWelcomeRequestStates() {
        botWelcomeRequestStates.clear();
        return this;
    }

    @Override
    public Collection<AiThreadTitle> aiThreadTitles() {
        return Collections.unmodifiableCollection(aiThreadTitles.values());
    }

    @Override
    public Optional<AiThreadTitle> findAiThreadTitle(String threadId) {
        if (threadId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(aiThreadTitles.get(threadId));
    }

    @Override
    public WhatsAppStore putAiThreadTitle(AiThreadTitle title) {
        Objects.requireNonNull(title, "title cannot be null");
        aiThreadTitles.put(title.threadId(), title);
        return this;
    }

    @Override
    public Optional<AiThreadTitle> removeAiThreadTitle(String threadId) {
        if (threadId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(aiThreadTitles.remove(threadId));
    }

    @Override
    public WhatsAppStore clearAiThreadTitles() {
        aiThreadTitles.clear();
        return this;
    }

    @Override
    public Collection<String> wamPendingBufferKeys() {
        if (directory == null) {
            return List.of();
        }
        Path sessionDir;
        try {
            sessionDir = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
        } catch (IOException error) {
            logger.log(System.Logger.Level.WARNING, "Cannot resolve session directory for WAM buffers", error);
            return List.of();
        }
        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }
        try (var stream = Files.list(sessionDir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(WAM_BUFFER_PREFIX) && name.endsWith(WAM_BUFFER_SUFFIX))
                    .map(name -> name.substring(WAM_BUFFER_PREFIX.length(), name.length() - WAM_BUFFER_SUFFIX.length()))
                    .toList();
        } catch (IOException error) {
            logger.log(System.Logger.Level.WARNING, "Cannot list WAM buffer files", error);
            return List.of();
        }
    }

    @Override
    public OutputStream openWamPendingBufferWriter(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return OutputStream.nullOutputStream();
        }
        var target = wamBufferPath(saveKey);
        Files.createDirectories(target.getParent());
        var temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        return new AtomicMoveOutputStream(Files.newOutputStream(temp), temp, target);
    }

    @Override
    public Optional<InputStream> openWamPendingBufferReader(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return Optional.empty();
        }
        var path = wamBufferPath(saveKey);
        if (Files.notExists(path)) {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(path));
    }

    @Override
    public boolean removeWamPendingBuffer(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return false;
        }
        return Files.deleteIfExists(wamBufferPath(saveKey));
    }

    @Override
    public WhatsAppStore clearWamPendingBuffers() throws IOException {
        if (directory == null) {
            return this;
        }
        var sessionDir = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
        if (!Files.isDirectory(sessionDir)) {
            return this;
        }
        try (var stream = Files.list(sessionDir)) {
            for (var path : (Iterable<Path>) stream::iterator) {
                var name = path.getFileName().toString();
                if (name.startsWith(WAM_BUFFER_PREFIX) && name.endsWith(WAM_BUFFER_SUFFIX)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        return this;
    }

    @Override
    public OptionalInt findWamSequenceNumber(WamChannel channel) {
        Objects.requireNonNull(channel, "channel cannot be null");
        var stored = wamSequenceNumbers.get(channel.id());
        return stored == null ? OptionalInt.empty() : OptionalInt.of(stored);
    }

    @Override
    public WhatsAppStore putWamSequenceNumber(WamChannel channel, int sequenceNumber) {
        Objects.requireNonNull(channel, "channel cannot be null");
        wamSequenceNumbers.put(channel.id(), sequenceNumber);
        return this;
    }

    private Path wamBufferPath(String saveKey) throws IOException {
        return StorePathUtils.getSessionFile(
                clientType, directory, uuid.toString(),
                WAM_BUFFER_PREFIX + saveKey + WAM_BUFFER_SUFFIX);
    }

    private static void validateSaveKey(String saveKey) {
        if (saveKey.isEmpty()) {
            throw new IllegalArgumentException("saveKey cannot be empty");
        }
        for (var i = 0; i < saveKey.length(); i++) {
            var c = saveKey.charAt(i);
            if (c == '/' || c == '\\' || c == 0 || c == '.' && (i == 0)) {
                throw new IllegalArgumentException("saveKey contains forbidden character: " + saveKey);
            }
        }
    }

    /**
     * Output stream that delegates writes to a temporary file and, on
     * {@link #close}, atomically renames it over the target so partial
     * writes never become visible.
     */
    private static final class AtomicMoveOutputStream extends FilterOutputStream {
        private final Path tempFile;
        private final Path targetFile;
        private boolean closed;

        AtomicMoveOutputStream(OutputStream delegate, Path tempFile, Path targetFile) {
            super(delegate);
            this.tempFile = tempFile;
            this.targetFile = targetFile;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
                Files.move(tempFile, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException error) {
                Files.deleteIfExists(tempFile);
                throw error;
            }
        }
    }

    @Override
    public Optional<UsernameChatStartModeAction.ChatStartMode> usernameChatStartMode() {
        return Optional.ofNullable(usernameChatStartMode);
    }

    @Override
    public WhatsAppStore setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode mode) {
        this.usernameChatStartMode = mode;
        return this;
    }

    @Override
    public Optional<NotificationActivitySettingAction.NotificationActivitySetting> notificationActivitySetting() {
        return Optional.ofNullable(notificationActivitySetting);
    }

    @Override
    public WhatsAppStore setNotificationActivitySetting(NotificationActivitySettingAction.NotificationActivitySetting setting) {
        this.notificationActivitySetting = setting;
        return this;
    }

    @Override
    public List<RecentEmojiWeight> recentEmojiWeights() {
        return List.copyOf(recentEmojiWeights);
    }

    @Override
    public WhatsAppStore setRecentEmojiWeights(List<RecentEmojiWeight> weights) {
        this.recentEmojiWeights = new CopyOnWriteArrayList<>(Objects.requireNonNull(weights, "weights cannot be null"));
        return this;
    }

    @Override
    public Optional<String> newsletterSubscriptionUserIdentifier() {
        return Optional.ofNullable(newsletterSubscriptionUserIdentifier);
    }

    @Override
    public WhatsAppStore setNewsletterSubscriptionUserIdentifier(String identifier) {
        this.newsletterSubscriptionUserIdentifier = identifier;
        return this;
    }

    @Override
    public Optional<MusicUserIdAction> musicUserIdState() {
        return Optional.ofNullable(musicUserIdState);
    }

    @Override
    public WhatsAppStore setMusicUserIdState(MusicUserIdAction action) {
        this.musicUserIdState = action;
        return this;
    }

    @Override
    public Optional<String> newsletterSavedInterests() {
        return Optional.ofNullable(newsletterSavedInterests);
    }

    @Override
    public WhatsAppStore setNewsletterSavedInterests(String interests) {
        this.newsletterSavedInterests = interests;
        return this;
    }

    @Override
    public Optional<Boolean> statusPostOptInNotificationPreferencesEnabled() {
        return Optional.ofNullable(statusPostOptInNotificationPreferencesEnabled);
    }

    @Override
    public WhatsAppStore setStatusPostOptInNotificationPreferencesEnabled(Boolean enabled) {
        this.statusPostOptInNotificationPreferencesEnabled = enabled;
        return this;
    }

    @Override
    public Optional<PrivateProcessingSettingAction.PrivateProcessingStatus> privateProcessingStatus() {
        return Optional.ofNullable(privateProcessingStatus);
    }

    @Override
    public WhatsAppStore setPrivateProcessingStatus(PrivateProcessingSettingAction.PrivateProcessingStatus status) {
        this.privateProcessingStatus = status;
        return this;
    }

    @Override
    public Optional<Boolean> channelsPersonalisedRecommendationOptOut() {
        return Optional.ofNullable(channelsPersonalisedRecommendationOptOut);
    }

    @Override
    public WhatsAppStore setChannelsPersonalisedRecommendationOptOut(Boolean optOut) {
        this.channelsPersonalisedRecommendationOptOut = optOut;
        return this;
    }

    @Override
    public Optional<byte[]> userCreatedBotDefinition() {
        return Optional.ofNullable(userCreatedBotDefinition);
    }

    @Override
    public WhatsAppStore setUserCreatedBotDefinition(byte[] definition) {
        this.userCreatedBotDefinition = definition;
        return this;
    }

    @Override
    public Optional<MaibaAIFeaturesControlAction.MaibaAIFeatureStatus> aiBusinessAgentStatus() {
        return Optional.ofNullable(aiBusinessAgentStatus);
    }

    @Override
    public WhatsAppStore setAiBusinessAgentStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus status) {
        this.aiBusinessAgentStatus = status;
        return this;
    }

    @Override
    public Optional<Instant> pairingTimestamp() {
        return Optional.ofNullable(pairingTimestamp);
    }

    @Override
    public WhatsAppStore setPairingTimestamp(Instant pairingTimestamp) {
        this.pairingTimestamp = pairingTimestamp;
        return this;
    }

    @Override
    public long clockSkewSeconds() {
        return clockSkewSeconds;
    }

    @Override
    public WhatsAppStore setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
        return this;
    }

    @Override
    public Optional<Instant> groupAbPropsEmergencyPushTimestamp() {
        return Optional.ofNullable(groupAbPropsEmergencyPushTimestamp);
    }

    @Override
    public WhatsAppStore setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) {
        this.groupAbPropsEmergencyPushTimestamp = timestamp;
        return this;
    }

    @Override
    public Optional<String> abPropsAbKey() {
        return Optional.ofNullable(abPropsAbKey);
    }

    @Override
    public WhatsAppStore setAbPropsAbKey(String abKey) {
        this.abPropsAbKey = abKey;
        return this;
    }

    @Override
    public Optional<String> abPropsHash() {
        return Optional.ofNullable(abPropsHash);
    }

    @Override
    public WhatsAppStore setAbPropsHash(String hash) {
        this.abPropsHash = hash;
        return this;
    }

    @Override
    public OptionalLong abPropsRefresh() {
        return abPropsRefresh != 0L ? OptionalLong.of(abPropsRefresh) : OptionalLong.empty();
    }

    @Override
    public WhatsAppStore setAbPropsRefresh(long refreshSeconds) {
        this.abPropsRefresh = refreshSeconds;
        return this;
    }

    @Override
    public Optional<Instant> abPropsLastSyncTime() {
        return Optional.ofNullable(abPropsLastSyncTime);
    }

    @Override
    public WhatsAppStore setAbPropsLastSyncTime(Instant lastSyncTime) {
        this.abPropsLastSyncTime = lastSyncTime;
        return this;
    }

    @Override
    public long abPropsRefreshId() {
        return abPropsRefreshId;
    }

    @Override
    public WhatsAppStore setAbPropsRefreshId(long refreshId) {
        this.abPropsRefreshId = refreshId;
        return this;
    }

    @Override
    public long abPropsWebRefreshId() {
        return abPropsWebRefreshId;
    }

    @Override
    public WhatsAppStore setAbPropsWebRefreshId(long webRefreshId) {
        this.abPropsWebRefreshId = webRefreshId;
        return this;
    }

    @Override
    public long groupAbPropsRefreshId() {
        return groupAbPropsRefreshId;
    }

    @Override
    public WhatsAppStore setGroupAbPropsRefreshId(long groupRefreshId) {
        this.groupAbPropsRefreshId = groupRefreshId;
        return this;
    }

    @Override
    public int removeAllRecentAvatarStickers() {
        var iterator = recentStickers.entrySet().iterator();
        var removed = 0;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isAvatar()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public Optional<ChatMute> mentionEveryoneMuteExpiration(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        return Optional.ofNullable(mentionEveryoneMuteExpirations.get(chatJid));
    }

    @Override
    public void setMentionEveryoneMuteExpiration(Jid chatJid, ChatMute mute) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(mute, "mute cannot be null");
        mentionEveryoneMuteExpirations.put(chatJid, mute);
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
                            && Objects.equals(selfTextStatus, that.selfTextStatus)
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
                            && Objects.equals(interopHostedVerificationCache, that.interopHostedVerificationCache)
                            && Objects.equals(utmReadChatIds, that.utmReadChatIds)
                            && Objects.equals(pendingDeviceSyncs, that.pendingDeviceSyncs)
                            && Objects.equals(groupSenderKeyDistribution, that.groupSenderKeyDistribution)
                            && disableLinkPreviews == that.disableLinkPreviews
                            && relayAllCalls == that.relayAllCalls
                            && externalWebBeta == that.externalWebBeta
                            && Objects.equals(chatLockSettings, that.chatLockSettings)
                            && Objects.equals(favoriteChats, that.favoriteChats)
                            && Objects.equals(primaryFeatures, that.primaryFeatures)
                            && Objects.equals(mentionEveryoneMuteExpirations, that.mentionEveryoneMuteExpirations)
                            && Objects.equals(baseKeys, that.baseKeys)
                            && Objects.equals(wamSequenceNumbers, that.wamSequenceNumbers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, phoneNumber, clientType, initializationTimeStamp,
                device, releaseChannel, online, locale, name, verifiedName,
                profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude,
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
                deviceLists, unconfirmedIdentityChanges, interopHostedVerificationCache, utmReadChatIds, pendingDeviceSyncs, groupSenderKeyDistribution,
                disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures,
                mentionEveryoneMuteExpirations, baseKeys, wamSequenceNumbers);
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

    @Override
    public Optional<? extends MessageInfo> findQuotedMessage(MessageInfo info) {
        return info.message()
                .contextualContent()
                .flatMap(ContextualMessage::contextInfo)
                .flatMap(context -> {
                    var quotedId = context.quotedMessageId().orElse(null);
                    var provider = context.quotedMessageParentJid()
                            .or(() -> info.key().parentJid())
                            .orElse(null);
                    if (quotedId == null || provider == null) {
                        return Optional.empty();
                    }

                    return findMessageById(provider, quotedId);
                });
    }

    @ProtobufMessage
    public static final class OrphanMutationEntries {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        final List<OrphanMutationEntry> data;

        OrphanMutationEntries(List<OrphanMutationEntry> data) {
            this.data = data;
        }

        OrphanMutationEntries() {
            this.data = new CopyOnWriteArrayList<>();
        }

        public List<OrphanMutationEntry> data() {
            return data;
        }
    }
}
