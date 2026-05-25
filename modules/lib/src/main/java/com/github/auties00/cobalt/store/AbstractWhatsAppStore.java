package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
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
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataEdit;
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
import com.github.auties00.cobalt.model.privacy.AccountDisappearingMode;
import com.github.auties00.cobalt.model.privacy.StatusPrivacySetting;
import com.github.auties00.cobalt.model.setting.AppTheme;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.setting.privacy.OptOutEntry;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
import com.github.auties00.cobalt.client.WhatsAppProxy;
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
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;
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
 * Holds the storage-agnostic state for a {@link WhatsAppStore} and implements every
 * accessor on that interface (and on {@link com.github.auties00.libsignal.SignalProtocolStore},
 * which {@link WhatsAppStore} extends) against in-memory fields.
 *
 * <p>Concrete subclasses inherit every field and every accessor body, and are only
 * responsible for deciding <em>how</em> the aggregate is persisted: a single root
 * protobuf, per-entity files, or no persistence at all. This class never touches
 * the filesystem on its own; it leaves {@link WhatsAppStore#save()},
 * {@link WhatsAppStore#delete()} and {@link WhatsAppStore#await()} unimplemented.
 *
 * <p>Each field group collapses one or more WhatsApp Web stores into a single
 * in-memory structure:
 * <ul>
 *   <li>Scalar fields (UUID, phone number, locale, profile metadata, sync flags,
 *       AB-props bundle, ADV secret) flatten the UserPrefs key-value layer that WA
 *       Web scatters across {@code WAWebUserPrefsKeys} and
 *       {@code WAWebPermanentStorage}.
 *   <li>Typed {@link ConcurrentHashMap} fields (contactsMap, calls, privacy settings,
 *       sender keys, sessionsMap, remote identities, app-state hash states, missing
 *       sync keys, mention-everyone mute expirations, orphan mutation entries,
 *       out-contactsMap, X3DH base-key dedupe) collapse the WA Web IndexedDB tables
 *       and the in-memory reactive collections of {@code WAWebContactCollection},
 *       {@code WAWebChatCollection}, {@code WAWebMsgInfoCollection} and peers.
 *   <li>Signal-protocol fields (registration id, Noise key pair, identity key pair,
 *       signed pre-key, pre-key map, sender keys, sessionsMap, remote identities,
 *       app-state sync keys) make this class directly usable as a
 *       {@link com.github.auties00.libsignal.SignalProtocolStore}, mirroring the
 *       per-table accessors exposed by {@code WAWebSignalProtocolStoreCacheApi}.
 *   <li>Per-collection sync hash states, orphan mutation entries and missing-key
 *       entries back the syncd state machine that WA Web drives from
 *       {@code WAWebSyncdCollectionsStateMachine}, {@code WAWebSyncdOrphan} and
 *       {@code WAWebSyncdStoreMissingKeys}.
 * </ul>
 *
 * @implNote
 * This implementation deliberately collapses WA Web's roughly twelve IndexedDB
 * databases, hundred-odd tables, forty-odd reactive in-memory collections, and the
 * UserPrefs key-value layer into a single object. WA Web needs the split because
 * the browser imposes a per-database transactional boundary, an async IndexedDB
 * API, and a worker context that mediates access; none of those constraints apply
 * on the JVM, so a flat field set keyed by entity type is both simpler and faster.
 *
 * @see WhatsAppStore
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public abstract class AbstractWhatsAppStore implements WhatsAppStore {
    /**
     * The fallback display name returned by {@link #name()} when no profile name
     * has been pushed by the server or set by the caller.
     *
     * @apiNote
     * Surfaces in UI strings and outbound stanzas that require a non-null
     * sender name; matches the literal placeholder WA Web shows in chat-list
     * cells for accounts whose pushname has not propagated yet.
     */
    protected static final String DEFAULT_NAME = "User";

    /**
     * The maximum number of per-user device-list entries kept in the device cache
     * before the oldest entry is evicted.
     *
     * @apiNote
     * Caps the working set of {@link DeviceList}
     * entries the store retains during a long-running session; recent senders are
     * kept hot while idle contactsMap age out.
     *
     * @implNote
     * This implementation uses a {@link ConcurrentLinkedHashMap}
     * with insertion-order eviction; WA Web has no comparable cap because each
     * device list is row-keyed in the {@code deviceList} IndexedDB table and is
     * not held in memory.
     */
    protected static final int MAX_DEVICE_LISTS = 5000;

    /**
     * The time-to-live for a cached device-list entry before it is considered stale
     * and a fresh {@code usync} round trip is required.
     *
     * @apiNote
     * Limits how long stale per-recipient device fan-out lists may be reused for
     * outgoing message sessionsMap; one day matches the refresh cadence that the
     * companion fleet honours via the {@code usync} pipeline.
     */
    protected static final Duration DEVICE_TTL = Duration.ofDays(1);

    /**
     * The filename prefix used by {@link #openWamPendingBufferWriter} when staging
     * a WAM event buffer to disk.
     *
     * @apiNote
     * Files in {@link #directory} whose name matches
     * {@code WAM_BUFFER_PREFIX + saveKey + WAM_BUFFER_SUFFIX} are recovered via
     * {@link #openWamPendingBufferReader} on next initialise so unsent batches
     * survive process exit.
     */
    private static final String WAM_BUFFER_PREFIX = "wam_buffer_";

    /**
     * The filename suffix paired with {@link #WAM_BUFFER_PREFIX} for staged WAM
     * event buffers.
     *
     * @apiNote
     * Lets {@link #clearWamPendingBuffers} distinguish buffer files from any
     * other content a subclass may have written to {@link #directory}.
     */
    private static final String WAM_BUFFER_SUFFIX = ".bin";

    /**
     * The stable per-account identifier used to scope on-disk session state, log
     * tags and the session pairing handshake.
     *
     * @apiNote
     * Returned by {@link #uuid()}; never changes for the life of a logged-in
     * account and is the directory key under which session files are persisted.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    private final UUID uuid;

    /**
     * The E.164 phone number of the logged-in account, as a raw {@code Long}, or
     * {@code null} until the server confirms registration.
     *
     * @apiNote
     * Exposed through {@link #phoneNumber()} as an {@link OptionalLong};
     * remains {@code null} on freshly-created mobile registrations until the SMS
     * code round trip completes, and on web sessionsMap until QR pairing finishes.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    private Long phoneNumber;

    /**
     * The flavour of WhatsApp client this store was initialised for.
     *
     * @apiNote
     * Determines which pairing handshake, which connection profile, and which
     * persistence layout the surrounding client uses; immutable after construction
     * so a single store cannot be reused as both web and mobile.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    private final WhatsAppClientType clientType;

    /**
     * The wall-clock timestamp at which this store was first created.
     *
     * @apiNote
     * Used to age-rate session artefacts (for example the ADV revalidation cadence)
     * and to surface session age in diagnostics; serialised at second granularity
     * to keep the protobuf wire form compact.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    private final Instant initializationTimeStamp;

    /**
     * The device descriptor (manufacturer, model, operating system family, version)
     * advertised during pairing and bundled into every outgoing client payload.
     *
     * @apiNote
     * Drives what the companion fleet sees as the linked-device identity; mutable
     * because users may rebrand a session through {@link #setDevice}.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    private WhatsAppDevice device;

    /**
     * The release channel ({@code RELEASE}, {@code BETA}, {@code ALPHA}, etc.)
     * advertised to the server during connection.
     *
     * @apiNote
     * Controls which server-side feature gates the session is bucketed into;
     * changing it forces a reconnect for the new value to take effect.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    private ClientReleaseChannel releaseChannel;

    /**
     * Whether the local account is currently advertising itself as online via
     * presence stanzas.
     *
     * @apiNote
     * Read/written by the presence subsystem; flipping it to {@code false} causes
     * the server to stop reporting this client as available to peers and gates
     * receipt fan-out behind the user privacy preferences.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    private boolean online;

    /**
     * The IETF language tag advertised to the server (for example {@code "en_US"})
     * for server-rendered notifications and system messages.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    private String locale;

    /**
     * The pushname (display name) the account broadcasts to its peers.
     *
     * @apiNote
     * Accessed through {@link #name()}, which substitutes {@link #DEFAULT_NAME}
     * when the field is {@code null}; mutated via {@link #setName}.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    private String name;

    /**
     * The server-attested business display name on Business Verified accounts.
     *
     * @apiNote
     * Set only on the business client flavour by the verification handshake;
     * peers render it with the green check on chat headers.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    private String verifiedName;

    /**
     * The CDN URI for the most recently fetched copy of the local account
     * profile picture, or {@code null} if none has been fetched yet.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    private URI profilePicture;

    /**
     * The local account published "About" text status.
     *
     * @apiNote
     * Mirrors what other contactsMap will see under the local profile name; updated
     * by direct user action and rebroadcast through a status push.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    private ContactTextStatus selfTextStatus;

    /**
     * The local account phone-number JID (for example
     * {@code 1234567890@s.whatsapp.net}).
     *
     * @apiNote
     * Distinct from {@link #lid}; this is the long-lived identifier rooted in the
     * registered phone number and is the credential used for direct chats.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    private Jid jid;

    /**
     * The local account LID, an opaque {@code @lid} identifier that hides the
     * phone number in groups, communities and Channels.
     *
     * @apiNote
     * Always paired with {@link #jid} in the {@link #lidToPhoneMappings} table;
     * may be {@code null} on pre-LID sessionsMap.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    private Jid lid;

    /**
     * The free-text street address shown on the business profile.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.STRING)
    private String businessAddress;

    /**
     * The longitude paired with {@link #businessLatitude} for the pin shown on
     * the business profile map; {@code null} if no location has been published.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.DOUBLE)
    private Double businessLongitude;

    /**
     * The latitude paired with {@link #businessLongitude} for the business
     * profile map pin.
     */
    @ProtobufProperty(index = 19, type = ProtobufType.DOUBLE)
    private Double businessLatitude;

    /**
     * The free-text "About this business" description rendered on the business
     * profile screen.
     */
    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    private String businessDescription;

    /**
     * The ordered list of website URIs published on the business profile.
     *
     * @apiNote
     * Rendered as clickable links in the business-info sheet; reordering changes
     * which website appears first.
     */
    @ProtobufProperty(index = 21, type = ProtobufType.STRING)
    private List<URI> businessWebsites;

    /**
     * The contact email shown on the business profile.
     */
    @ProtobufProperty(index = 22, type = ProtobufType.STRING)
    private String businessEmail;

    /**
     * The Meta-defined business categories assigned to the business profile.
     *
     * @apiNote
     * Drives discovery surfaces such as Business Search and ad targeting; chosen
     * from the canonical list the server publishes through a dedicated query.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    private List<BusinessCategory> businessCategories;

    /**
     * The map of every contact known to the local account, keyed by JID.
     *
     * @apiNote
     * Backs {@link #contacts()}, {@link #findContactByJid}, {@link #addContact}
     * and {@link #removeContact}; the entries are the union of address-book sync,
     * sender-side cache fills, and group/community member discovery.
     *
     * @implNote
     * This implementation collapses WA Web's {@code WAWebContactCollection}
     * in-memory reactive collection plus its IndexedDB {@code contact} table into
     * a single map; lookups are tried by the supplied JID first and then by the
     * paired LID/phone JID through {@link #findLidByPhone} / {@link #findPhoneByLid}.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentHashMap<Jid, Contact> contactsMap;
    /**
     * The cache of contact "About" text statuses, keyed by contact user JID.
     *
     * @apiNote
     * Populated lazily as the server pushes status updates; queried via
     * {@link #findContactTextStatus} which transparently retries under the paired
     * LID/phone JID.
     *
     * @implNote
     * Not annotated {@code @ProtobufProperty}: WA Web rehydrates the equivalent
     * cache from the {@code contactTextStatus} IndexedDB table on every page
     * reload, and Cobalt rebuilds it on demand instead of persisting it.
     */
    private final ConcurrentHashMap<Jid, ContactTextStatus> contactTextStatusesMap;

    /**
     * The map of currently tracked incoming calls, keyed by call id.
     *
     * @apiNote
     * Populated by the call-signaling pipeline as call offers land and
     * pruned as terminal call states are processed; queried by
     * {@link #findCallById}.
     *
     * @implNote
     * Not persisted because calls are entirely ephemeral: an in-flight call cannot
     * meaningfully survive a process restart, and WA Web likewise keeps the
     * equivalent table only in memory.
     */
    private final ConcurrentHashMap<String, IncomingCall> calls;

    /**
     * The current value of every privacy setting (last-seen, profile picture,
     * about, status, read receipts, groups, calls), keyed by setting type.
     *
     * @apiNote
     * Mirrors the user privacy preferences round trip; reads land through
     * {@link #privacySettings()} and writes through the privacy-settings sender.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettingsMap;

    /**
     * The "Keep chats archived" setting that controls whether an archived chat
     * is automatically un-archived when a new message lands on it.
     */
    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    private boolean unarchiveChats;

    /**
     * Whether timestamps should be rendered in 24-hour rather than 12-hour form.
     */
    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    private boolean twentyFourHourFormat;

    /**
     * The default disappearing-message timer applied to newly created chats.
     *
     * @apiNote
     * Surfaces in the privacy settings screen; defaults to
     * {@link ChatEphemeralTimer#OFF} until
     * the user opts in.
     */
    @ProtobufProperty(index = 30, type = ProtobufType.ENUM)
    private ChatEphemeralTimer newChatsEphemeralTimer;

    /**
     * The history-sync policy that governs how much prior chat history the server
     * may push down during the initial bootstrap.
     *
     * @apiNote
     * Set during pairing and serialised so a re-pair on the same store does not
     * re-trigger the largest sync tier.
     */
    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    private WhatsAppWebClientHistory webHistoryPolicy;

    /**
     * Whether app-state patch MACs are verified during sync.
     *
     * @apiNote
     * Defaults to {@code true} for production; set {@code false} only to step
     * through corrupted captures where strict verification would refuse to apply
     * the patch.
     */
    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    private boolean checkPatchMacs;

    /**
     * Whether the initial chat history sync has completed for this session.
     *
     * @apiNote
     * Flipped once by the history-sync handler; serialised so the bootstrap
     * pipeline does not re-fire on every reconnect.
     */
    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    private boolean syncedChats;

    /**
     * Whether the initial contactsMap history sync has completed for this session.
     */
    @ProtobufProperty(index = 36, type = ProtobufType.BOOL)
    private boolean syncedContacts;

    /**
     * Whether the initial newsletter (Channels) subscription sync has completed
     * for this session.
     */
    @ProtobufProperty(index = 37, type = ProtobufType.BOOL)
    private boolean syncedNewsletters;

    /**
     * Whether the initial status (stories) history sync has completed for this
     * session.
     */
    @ProtobufProperty(index = 38, type = ProtobufType.BOOL)
    private boolean syncedStatus;

    /**
     * Whether the business-verified-name certificate has been fetched for this
     * session.
     *
     * @apiNote
     * Only meaningful on business client flavours; gates the rendering of the
     * verified-business badge.
     */
    @ProtobufProperty(index = 40, type = ProtobufType.BOOL)
    private boolean syncedBusinessCertificate;

    /**
     * The 14-bit Signal registration identifier randomly chosen at session creation
     * and pinned into the Signal prekey bundles published on this device.
     *
     * @apiNote
     * Forms part of {@link com.github.auties00.libsignal.SignalProtocolStore}; the
     * server uses it to deduplicate prekey bundles when this device republishes.
     */
    @ProtobufProperty(index = 41, type = ProtobufType.INT32)
    private final Integer registrationId;

    /**
     * The long-lived Noise XX static key pair used for the outer transport
     * handshake with the WhatsApp server.
     *
     * @apiNote
     * Distinct from {@link #identityKeyPair}; the Noise key authenticates the
     * socket-level connection, the Signal identity key authenticates messages.
     */
    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    private final SignalIdentityKeyPair noiseKeyPair;

    /**
     * The long-lived Signal identity key pair used to sign prekey bundles and to
     * derive per-recipient session keys.
     *
     * @apiNote
     * Disclosure of the corresponding public key to peers via prekey bundles is
     * what lets them perform an X3DH handshake against this device.
     */
    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    private final SignalIdentityKeyPair identityKeyPair;

    /**
     * The ADV (advanced-device-verification) certificate proving this companion
     * device was authorised by the primary device.
     *
     * @apiNote
     * Refetched on the cadence implied by {@link #lastAdvCheckTime}; absent until
     * the first successful pairing handshake completes.
     */
    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    private ADVSignedDeviceIdentity signedDeviceIdentity;

    /**
     * The currently published signed pre-key, with its server-side identifier and
     * the ECDSA signature over its public component.
     *
     * @apiNote
     * Periodically rotated; old versions remain in {@link #preKeysMap} until the
     * server confirms the new rotation has been ingested.
     */
    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    private final SignalSignedKeyPair signedKeyPair;

    /**
     * The map of unsigned one-time pre-keys keyed by the server-assigned id, used
     * by remote peers to start a fresh Signal session against this device.
     *
     * @apiNote
     * Insertion-ordered so the consumption order matches the publication order;
     * exhaustion triggers a top-up via the prekey upload pipeline.
     */
    @ProtobufProperty(index = 48, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    private final LinkedHashMap<Integer, SignalPreKeyPair> preKeysMap;

    /**
     * The Facebook device identifier (FDID) bundled into every client payload so
     * that Meta can correlate this WhatsApp session with other Meta-app installs
     * on the same hardware.
     */
    @ProtobufProperty(index = 49, type = ProtobufType.STRING)
    private final UUID fdid;

    /**
     * The 16-byte stable device identifier announced during pairing and reused on
     * every reconnect.
     */
    @ProtobufProperty(index = 50, type = ProtobufType.BYTES)
    private final byte[] deviceId;

    /**
     * The advertising identifier surfaced by the host platform.
     *
     * @apiNote
     * Bundled into client payloads and WAM events so that ads attribution can
     * correlate the WhatsApp session with the platform-level advertising id.
     */
    @ProtobufProperty(index = 51, type = ProtobufType.STRING)
    private final UUID advertisingId;

    /**
     * The 16-byte randomly generated identity buffer used to seed deterministic
     * per-account derivations.
     */
    @ProtobufProperty(index = 52, type = ProtobufType.BYTES)
    private final byte[] identityId;

    /**
     * The 20-byte token announced to the server as the backup credential for
     * encrypted-history recovery.
     */
    @ProtobufProperty(index = 53, type = ProtobufType.BYTES)
    private final byte[] backupToken;

    /**
     * The map of Signal group-session sender keys, keyed by the (group id, sender
     * device) pair.
     *
     * @apiNote
     * Read and written by the Signal sender-key protocol when encrypting and
     * decrypting group messages; backs the {@code loadSenderKey} /
     * {@code storeSenderKey} operations on
     * {@link com.github.auties00.libsignal.SignalProtocolStore}.
     */
    @ProtobufProperty(index = 54, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeysMap;

    /**
     * The map of app-state sync keys, keyed by their base64-encoded id.
     *
     * @apiNote
     * Used by the syncd patch decoder to unwrap encrypted mutations; new keys
     * arrive through history-sync notifications and via key-share peer messages.
     */
    @ProtobufProperty(index = 55, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final LinkedHashMap<String, AppStateSyncKey> appStateKeysMap;

    /**
     * The map of Signal sessionsMap for each remote (user, device) pair.
     *
     * @apiNote
     * Backs the {@code loadSession} / {@code storeSession} operations on
     * {@link com.github.auties00.libsignal.SignalProtocolStore}; sessionsMap are
     * established lazily via X3DH the first time a message is sent to or received
     * from a remote.
     */
    @ProtobufProperty(index = 56, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessionsMap;

    /**
     * The per-syncd-collection LT-Hash and version pair tracking the cumulative
     * applied state of every app-state collection.
     *
     * @apiNote
     * Updated atomically with every mutation batch applied through the syncd
     * pipeline; compared against the server-supplied snapshot hash to detect
     * tampering or divergence.
     */
    @ProtobufProperty(index = 57, type = ProtobufType.MESSAGE, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<SyncPatchType, SyncHashValue> hashStatesMap;

    /**
     * Whether this device has completed the pairing handshake and is currently
     * registered with the server.
     *
     * @apiNote
     * Flipped to {@code true} once a pairing handshake completes and never
     * reverted; logout deletes the entire store rather than clearing this flag.
     */
    @ProtobufProperty(index = 58, type = ProtobufType.BOOL)
    private boolean registered;

    /**
     * Whether security-code-change notifications are displayed in chats when a
     * remote contact rotates their identity key.
     */
    @ProtobufProperty(index = 59, type = ProtobufType.BOOL)
    private boolean showSecurityNotifications;

    /**
     * The recently used stickers keyed by sticker hash.
     *
     * @apiNote
     * Drives the "Recent" tab in the sticker picker; populated by user picks and
     * truncated by the sticker store to the most recent set.
     */
    @ProtobufProperty(index = 60, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<String, Sticker> recentStickersMap;

    /**
     * The user-favourited stickers keyed by sticker hash.
     *
     * @apiNote
     * Drives the starred "Favourites" tab in the sticker picker and survives
     * across reinstalls via the encrypted app-state sync.
     */
    @ProtobufProperty(index = 61, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<String, Sticker> favouriteStickersMap;

    /**
     * The map of business "Quick Replies" keyed by shortcut.
     *
     * @apiNote
     * Only meaningful on business client flavours; lets the user trigger a saved
     * canned reply by typing its shortcut.
     */
    @ProtobufProperty(index = 62, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<String, QuickReply> quickRepliesMap;

    /**
     * The map of business labelsMap keyed by label id.
     *
     * @apiNote
     * Backs the chat-tagging and contact-tagging surface in the WhatsApp Business
     * UI; round-trips through app-state sync so labelsMap stay consistent across
     * linked devices.
     */
    @ProtobufProperty(index = 63, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<String, Label> labelsMap;

    /**
     * The advertised application version this client identifies itself with on
     * the server.
     *
     * @apiNote
     * Read on every reconnect; mutating it forces a reconnect for the new version
     * banner to take effect. Volatile because the version refresher and the
     * connection thread may both touch it.
     */
    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    private volatile ClientAppVersion clientVersion;

    /**
     * The currently observed application version of the primary device this
     * companion is paired with.
     *
     * @apiNote
     * Updated whenever the primary sends a version-bearing payload (notification
     * or ADV revalidation); used to gate features behind primary support.
     */
    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    private ClientAppVersion companionVersion;

    /**
     * The timestamp of the most recent successful ADV revalidation against the
     * primary device.
     *
     * @apiNote
     * Compared against a cadence threshold to decide whether to initiate the
     * next ADV revalidation round trip; persisted at millisecond granularity.
     */
    @ProtobufProperty(index = 66, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant lastAdvCheckTime;

    /**
     * The map of trusted remote Signal identity keys, keyed by (user, device).
     *
     * @apiNote
     * Backs the trust-on-first-use identity store; subsequent mismatches surface
     * through {@link #unconfirmedIdentityChanges} for explicit confirmation.
     */
    @ProtobufProperty(index = 67, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    private final ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentitiesMap;

    /**
     * The map of app-state sync keys the device knows are missing, keyed by the
     * base64-encoded key id.
     *
     * @apiNote
     * Populated when a syncd patch references a key that has not yet propagated;
     * the syncd request pipeline drains this map by asking the primary device
     * to reshare each missing key.
     */
    @ProtobufProperty(index = 68, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeysMap;

    /**
     * The shared secret used to derive the ADV (advanced-device-verification)
     * MAC key on this companion.
     *
     * @apiNote
     * Pinned during pairing; rotated only on full re-pair. Disclosure of this
     * secret would let an attacker forge ADV signatures from the local device.
     */
    @ProtobufProperty(index = 69, type = ProtobufType.BYTES)
    private byte[] advSecretKey;

    /**
     * The cache of business-verified-name certificates, keyed by the verified
     * business JID.
     *
     * @apiNote
     * Drives rendering of the verified-business badge in chats with business
     * accounts; refilled lazily as new verified businesses are interacted with.
     */
    @ProtobufProperty(index = 70, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNamesMap;

    /**
     * The on-disk directory under which subclasses persist this store; may be
     * {@code null} on in-memory stores.
     *
     * @apiNote
     * Resolved relative to the session root via
     * {@link StorePathUtils}; used both for the
     * persistence layer and for WAM event buffer staging.
     */
    @ProtobufProperty(index = 71, type = ProtobufType.STRING, mixins = {PathMixin.class, ProtobufLazyMixin.class})
    private final Path directory;

    /**
     * Whether the paired primary device advertises support for the syncd snapshot
     * recovery flow.
     *
     * @apiNote
     * Gates whether this companion may request a fresh snapshot when local
     * app-state collections diverge from the server.
     */
    @ProtobufProperty(index = 72, type = ProtobufType.BOOL)
    private boolean primaryDeviceSupportsSyncdRecovery;

    /**
     * Whether outbound link previews are suppressed (the "Disable link previews"
     * preference).
     */
    @ProtobufProperty(index = 73, type = ProtobufType.BOOL)
    private boolean disableLinkPreviews;

    /**
     * Whether the user has opted into "relay all calls" mode which forces every
     * VoIP call through Meta TURN servers instead of peer-to-peer.
     *
     * @apiNote
     * Trades a small latency hit for IP-address privacy from the call peer.
     */
    @ProtobufProperty(index = 74, type = ProtobufType.BOOL)
    private boolean relayAllCalls;

    /**
     * Whether this client has opted into the external-web-beta AB-prop bucket.
     *
     * @apiNote
     * Required for VoIP-on-web capture: WAM only enables the VoIP signaling
     * receiver on opted-in sessionsMap; toggled programmatically via the
     * external-web-beta opt-in action documented in the call-capture memory.
     */
    @ProtobufProperty(index = 75, type = ProtobufType.BOOL)
    private boolean externalWebBeta;

    /**
     * The current "Chat Lock" feature configuration (per-device passphrase, lock
     * surfaces, lock timeout).
     */
    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    private ChatLockSettings chatLockSettings;

    /**
     * The ordered list of pinned (favourite) chat JIDs.
     *
     * @apiNote
     * Drives the order of the pinned section at the top of the chat list;
     * round-trips through app-state sync.
     */
    @ProtobufProperty(index = 77, type = ProtobufType.STRING)
    private List<Jid> favoriteChats;

    /**
     * The list of feature gates the primary device has reported it understands.
     *
     * @apiNote
     * Used to short-circuit features the primary cannot interpret, avoiding
     * round trips that the primary will discard.
     */
    @ProtobufProperty(index = 78, type = ProtobufType.STRING)
    private List<String> primaryFeatures;

    /**
     * The map of per-group mute expirations for the "Mention everyone" announcement
     * style, keyed by group JID.
     *
     * @apiNote
     * Lets the notification pipeline suppress @everyone notifications on muted
     * groups; entries naturally expire at the encoded {@link ChatMute} timestamp.
     */
    @ProtobufProperty(index = 79, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.UINT64)
    private final ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirationsMap;

    /**
     * The per-collection set of orphan app-state mutations awaiting their parent
     * entity to appear.
     *
     * @apiNote
     * Backs the orphan-mutation queue; entries persist across reconnects
     * until the missing entity arrives or is explicitly garbage-collected.
     */
    @ProtobufProperty(index = 80, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentMap<SyncPatchType, OrphanMutationEntries> orphanMutationEntriesMap;

    /**
     * The map of contactsMap the local account has received messages from but does
     * not have a saved address-book entry for, keyed by JID.
     *
     * @apiNote
     * Drives the "Unknown sender" surface; queried by {@link #findOutContact}
     * and exposed via {@link #outContacts()}.
     */
    @ProtobufProperty(index = 81, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private ConcurrentHashMap<Jid, OutContact> outContactsMap;

    /**
     * The observed skew between the local wall clock and the server clock, in
     * seconds.
     *
     * @apiNote
     * Estimated from response timestamps during the handshake; applied wherever
     * a server-comparable timestamp is required so that local skew does not
     * trigger spurious replay or expiration logic.
     */
    @ProtobufProperty(index = 84, type = ProtobufType.INT64)
    private long clockSkewSeconds;

    /**
     * The timestamp of the most recent emergency push of group-scoped AB props.
     *
     * @apiNote
     * Compared against {@link #groupAbPropsRefreshId} to decide whether a fresh
     * fetch is required even when the standard refresh cadence has not yet
     * elapsed.
     */
    @ProtobufProperty(index = 85, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    private Instant groupAbPropsEmergencyPushTimestamp;

    /**
     * The opaque AB-key handed back by the server on the most recent AB-props
     * refresh.
     *
     * @apiNote
     * Echoed on the next AB-props refresh request so the server can compute a
     * deterministic delta against the last delivered bundle.
     */
    @ProtobufProperty(index = 86, type = ProtobufType.STRING)
    private String abPropsAbKey;

    /**
     * The content-hash of the currently cached AB-props bundle.
     */
    @ProtobufProperty(index = 87, type = ProtobufType.STRING)
    private String abPropsHash;

    /**
     * The server-suggested refresh interval, in seconds, between two consecutive
     * AB-props fetches.
     */
    @ProtobufProperty(index = 88, type = ProtobufType.INT64)
    private long abPropsRefresh;

    /**
     * The timestamp of the most recent successful AB-props fetch.
     */
    @ProtobufProperty(index = 89, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant abPropsLastSyncTime;

    /**
     * The opaque server-issued refresh id for the device-scoped AB-props bundle.
     */
    @ProtobufProperty(index = 90, type = ProtobufType.INT64)
    private long abPropsRefreshId;

    /**
     * The opaque server-issued refresh id for the web-scoped AB-props bundle.
     */
    @ProtobufProperty(index = 91, type = ProtobufType.INT64)
    private long abPropsWebRefreshId;

    /**
     * The opaque server-issued refresh id for the group-scoped AB-props bundle.
     */
    @ProtobufProperty(index = 92, type = ProtobufType.INT64)
    private long groupAbPropsRefreshId;

    /**
     * The map of X3DH base keys seen on prekey messages, keyed by the
     * derived-message id.
     *
     * @apiNote
     * Used by the Signal session decoder to deduplicate prekey messages that the
     * server may redeliver during retransmits; entries are evicted as the
     * encrypted-message-id rotation overwrites them.
     */
    @ProtobufProperty(index = 93, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    private final ConcurrentMap<String, byte[]> baseKeysMap;

    /**
     * The map of monotonic per-channel sequence numbers used to tag outgoing WAM
     * batches, keyed by the {@link WamChannel} id.
     *
     * @apiNote
     * Persisted across restarts so that uploaded WAM batches keep advancing
     * regardless of process lifetime.
     */
    @ProtobufProperty(index = 94, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.INT32)
    private final ConcurrentMap<Integer, Integer> wamSequenceNumbersMap;

    /**
     * The per-remote-device counter tracking the last Signal-encrypted message
     * sequence number observed in flight.
     *
     * @apiNote
     * Read and written by the encryption pipeline to bound prekey-message replay;
     * not persisted because it is reconstructed from session state on reload.
     */
    private final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange;

    /**
     * The monotonic counter providing the per-process sequence component of
     * locally generated message ids.
     */
    private final AtomicLong encryptionSequence;

    /**
     * The optional proxy configuration (HTTP CONNECT or SOCKS5) that the socket
     * subsystem should route through; {@code null} for a direct connection.
     */
    private WhatsAppProxy proxy;

    /**
     * The set of registered listeners that receive client lifecycle and message
     * delivery callbacks.
     *
     * @apiNote
     * Backed by {@link ConcurrentHashMap#newKeySet()} so concurrent
     * adds and removes during a callback dispatch are safe.
     */
    private final KeySetView<WhatsAppClientListener, Boolean> listeners;

    /**
     * The map of normalized LID JID to the paired phone-number JID.
     *
     * @apiNote
     * The forward index of the LID/phone mapping table; queried by
     * {@link #findPhoneByLid} and maintained by
     * {@link #registerLidMapping(Jid, Jid, Instant)}.
     *
     * @implNote
     * This implementation mirrors WA Web {@code WAWebLidPnCache} with an
     * additional {@link #lidMappingTimestamps} sidecar to honour late-arriving
     * mappings.
     */
    private final ConcurrentHashMap<Jid, Jid> lidToPhoneMappings;

    /**
     * The reverse map of normalized phone-number JID to the paired LID JID.
     *
     * @apiNote
     * Queried by {@link #findLidByPhone} and kept in sync with
     * {@link #lidToPhoneMappings} on every {@link #registerLidMapping} call.
     */
    private final ConcurrentHashMap<Jid, Jid> phoneToLidMappings;

    /**
     * The map of LID JID to the timestamp at which the current mapping was
     * recorded.
     *
     * @apiNote
     * Used by {@link #registerLidMapping(Jid, Jid, Instant)} to ignore stale
     * mapping updates that arrive after a newer one has already been applied.
     */
    private final ConcurrentHashMap<Jid, Instant> lidMappingTimestamps;

    /**
     * The current state of the offline-resume bootstrap: whether the server is
     * still draining buffered notifications, has completed, or has been skipped.
     *
     * @apiNote
     * Read by {@link WhatsAppClient} entry
     * points that must wait until offline delivery has drained before sending.
     */
    private volatile WhatsAppClientOfflineResumeState offlineResumeState;

    /**
     * The latch counted down once the offline-delivery drain reaches
     * {@link WhatsAppClientOfflineResumeState} terminal.
     *
     * @apiNote
     * Reset on every reconnect so callers wait on the right generation; reads
     * and writes are volatile because reconnect installs a fresh latch.
     */
    private volatile CountDownLatch offlineDeliveryLatch;

    /**
     * The set of group JIDs for which the local device must rotate and reshare
     * the Signal sender key on the next outbound message.
     *
     * @apiNote
     * Filled by participant-change notifications (member removed, member key
     * rotated) and drained as the sender-key rotation send completes.
     */
    private final KeySetView<Jid, Boolean> usersNeedingSenderKeyRotation;

    /**
     * The per-collection FIFO of pending app-state mutations awaiting batch
     * upload to the server.
     *
     * @apiNote
     * Drained by the syncd upload pipeline on every mutation flush cycle;
     * insertion order is preserved so the server sees mutations in the order the
     * user produced them.
     */
    private final ConcurrentMap<SyncPatchType, SequencedCollection<SyncPendingMutation>> webAppStatePendingMutations;

    /**
     * The per-collection metadata (current version, snapshot version, recovery
     * state) tracked alongside the syncd state machine.
     */
    private final ConcurrentMap<SyncPatchType, SyncCollectionMetadata> webAppStateCollections;

    /**
     * The per-collection in-memory index of applied sync actions, keyed by the
     * action's stable index id.
     *
     * @apiNote
     * Used to answer "do I already have this mutation?" queries during
     * patch decoding.
     */
    private final ConcurrentMap<SyncPatchType, ConcurrentMap<String, SyncActionEntry>> syncActionEntries;

    /**
     * The map of in-flight outgoing message id to the set of recipients the
     * message is still pending delivery to.
     *
     * @apiNote
     * Drained as per-recipient delivery receipts land; lets the send pipeline
     * resolve the awaiting future once every recipient has either acked or
     * timed out.
     */
    private final ConcurrentMap<String, Set<Jid>> pendingMessageRecipients;

    /**
     * The lock guarding the single in-flight {@link #clientVersion} probe so
     * concurrent reconnects do not duplicate the version refresh.
     */
    private final Object clientVersionLock;

    /**
     * The map of group/community metadata (subject, description, picture, settings,
     * participant set), keyed by chat JID.
     *
     * @apiNote
     * Backs {@link GroupMetadata}
     * lookups; refilled lazily as groups are interacted with.
     */
    private final ConcurrentMap<Jid, ChatMetadata> chatMetadata;

    /**
     * The bounded LRU cache of per-recipient device fan-out lists, capped at
     * {@link #MAX_DEVICE_LISTS} entries.
     *
     * @apiNote
     * Drives outgoing message fan-out: each entry maps a user JID to the set of
     * companion devices that must receive a copy of every direct message; aged
     * out per {@link #DEVICE_TTL}.
     */
    private final ConcurrentLinkedHashMap<Jid, DeviceList> deviceLists;

    /**
     * The set of remote JIDs whose Signal identity key has changed since the
     * device last accepted it and which the user has not yet confirmed.
     */
    private final Set<Jid> unconfirmedIdentityChanges;

    /**
     * The set of JIDs already verified as belonging to a third-party messenger
     * federated under the WhatsApp interop framework.
     *
     * @apiNote
     * Caches the result of the hosted-add verification probe so subsequent
     * conversations with the same federated identity skip the round trip.
     */
    private final Set<Jid> interopHostedVerificationCache;

    /**
     * The set of chat JIDs for which the click-through analytics tag has already
     * been recorded.
     *
     * @apiNote
     * Backs deduplication of UTM-tagged chat-open events; entries are not
     * persisted because UTM is a per-session metric.
     */
    private final Set<Jid> utmReadChatIds;

    /**
     * The set of JIDs the local account has explicitly blocked.
     */
    private final Set<Jid> blockedContacts;

    /**
     * The last server-supplied blocklist digest, or {@code null} when no
     * fetch has succeeded yet.
     *
     * @apiNote
     * Backs {@link #blocklistHash()} and {@link #setBlocklistHash(String)}.
     * The digest is passed back when the block list is next refreshed so
     * the server can answer with the cache-match short-circuit when the
     * local cache is still authoritative.
     */
    private volatile String blocklistHash;

    /**
     * Whether the local blocklist has been migrated from PN to LID
     * addressing.
     *
     * @apiNote
     * The flag flips to {@code true} the first time the server returns a
     * LID-addressed blocklist after the device itself has completed the
     * 1:1 LID migration, and flips back to {@code false} when a migrated
     * device receives an unexpected phone-number-addressed blocklist.
     */
    private volatile boolean blocklistMigrated;

    /**
     * Whether the relay has delivered a LID-addressed blocklist before
     * the device has completed its own 1:1 LID migration.
     *
     * @apiNote
     * Set when {@link WhatsAppClient} receives a LID-addressed blocklist
     * while the LID migration is still in flight; the deferred blocklist
     * migration runs after the 1:1 migration completes.
     */
    private volatile boolean receivedBlocklistMigrationBefore1x1Migration;

    /**
     * The cached server-supplied digests of marketing-message opt-out
     * lists, keyed by category.
     *
     * @apiNote
     * Drives the digest-based cache-match short-circuit when an opt-out
     * category is next refreshed, so the server can return a cache-hit
     * shape when the local view is still authoritative.
     */
    private final ConcurrentMap<String, String> optOutListHashes;

    /**
     * The cached entries of marketing-message opt-out lists, keyed by
     * category.
     */
    private final ConcurrentMap<String, List<OptOutEntry>> optOutListEntries;

    /**
     * The cached server-supplied digests of per-axis privacy contact
     * blacklists, keyed by the privacy category name.
     *
     * @apiNote
     * Drives the digest-based cache-match short-circuit on the next
     * privacy-disallowed-list fetch; the addressing mode the relay
     * used is implicit per session, so the key is the category name
     * alone.
     */
    private final ConcurrentMap<String, String> contactBlacklistHashes;

    /**
     * The cached entries of per-axis privacy contact blacklists, keyed
     * by the privacy category name.
     */
    private final ConcurrentMap<String, List<Jid>> contactBlacklistEntries;

    /**
     * The cached server-authoritative Status story privacy setting.
     */
    private volatile StatusPrivacySetting statusPrivacy;

    /**
     * The cached server-authoritative account-level Disappearing
     * Messages setting.
     */
    private volatile AccountDisappearingMode disappearingMode;

    /**
     * The cached server-authoritative list of devices linked to this
     * account.
     */
    private volatile List<Jid> linkedDevices;

    /**
     * The current state of the link between this WhatsApp account and the user
     * Meta Accounts Center identity ("Waffle").
     *
     * @apiNote
     * Volatile because the link state may be flipped from background sync
     * threads; exposed through {@link #linkedMetaAccountState()}.
     */
    private volatile WaffleAccountLinkStateAction.AccountLinkState linkedMetaAccountState;

    /**
     * The timestamp of the most recent {@link #linkedMetaAccountState} transition.
     *
     * @apiNote
     * Used to discard older link-state updates that arrive out of order on the
     * sync pipeline.
     */
    private volatile Instant linkedMetaAccountStateTimestamp;

    /**
     * Whether the local account has completed onboarding for the
     * business-automation hosted runtime.
     */
    private volatile boolean hostedAutomationOnboarded;

    /**
     * The FIFO queue of device-list sync results yet to be applied to peer
     * sessionsMap.
     *
     * @apiNote
     * Drained by the device-sync handler on every reconnect tick; persisted only
     * for the duration of the process.
     */
    private final ConcurrentLinkedQueue<PendingDeviceSync> pendingDeviceSyncs;

    /**
     * The map of group id to the set of device ids that have already been issued
     * the current sender-key distribution.
     *
     * @apiNote
     * Lets the sender-key distribution sender avoid resending the same key to
     * the same recipient on every group message.
     */
    private final ConcurrentMap<String, Set<String>> groupSenderKeyDistribution;

    /**
     * The map of pending payment notifications whose triggering message has not
     * yet been received, keyed by the absent message id.
     *
     * @apiNote
     * Queried by {@link #findOrphanPaymentNotification}; cleared by
     * {@link #removeOrphanPaymentNotification} once the parent message arrives.
     */
    private final ConcurrentMap<String, OrphanPaymentNotification> orphanPaymentNotifications;

    /**
     * The opaque routing bytes handed back by the server on the most recent
     * connection; replayed on the next handshake so the server can place this
     * session on the same backend shard.
     */
    private byte[] routingInfo;

    /**
     * The hostname returned by the server alongside {@link #routingInfo} for the
     * subsequent reconnect.
     */
    private String routingDomain;

    /**
     * The instant at which the server will stop accepting this client version
     * and force an upgrade.
     */
    private Instant clientExpiration;

    /**
     * The set of Terms-of-Service notice ids the user has been shown and either
     * dismissed or acknowledged.
     */
    private Set<String> tosNoticeIds;

    /**
     * Whether the server has reported that AI features are available to this
     * account.
     */
    private boolean aiAvailable;

    /**
     * The content-hash of the most recently received business opt-out list,
     * compared against the server-supplied hash to decide whether a refetch is
     * required.
     */
    private String businessOptOutListHash;

    /**
     * The map of business-feature gates keyed by name.
     *
     * @apiNote
     * Drives runtime gating of the business UI; queried by
     * {@link #findBusinessFeatureFlag} and reset by
     * {@link #clearBusinessFeatureFlags}.
     */
    private ConcurrentMap<String, BusinessFeatureFlag> businessFeatureFlags;

    /**
     * The map of business-marketing-campaign statuses keyed by campaign id.
     */
    private ConcurrentMap<String, BusinessCampaignStatus> businessCampaignStatuses;

    /**
     * The map of business subscription records keyed by subscription id.
     */
    private ConcurrentMap<String, BusinessSubscription> businessSubscriptions;

    /**
     * The opaque nonce associated with the current business-account session;
     * used to scope business-only round trips.
     */
    private String businessAccountNonce;

    /**
     * The map of Click-To-WhatsApp data-sharing preferences keyed by ads account
     * LID, recording the consent the user has granted on each CTWA ad account.
     */
    private ConcurrentMap<String, CtwaDataSharingPreference> ctwaDataSharingPreferences;

    /**
     * The current Small-Business data-sharing consent string echoed back to the
     * server in subsequent SMB requests.
     */
    private String smbDataSharingConsent;

    /**
     * Whether server-side outcome detection (the auto-classification of customer
     * chats into "sold" / "lost" outcomes) is enabled for the current account.
     */
    private boolean detectedOutcomesEnabled;

    /**
     * Whether the paired primary device advertises that it can absorb every
     * companion-originated mutation, bypassing the standard allow-list.
     */
    private boolean primaryAllowsAllMutations;

    /**
     * The map of business-agent state records keyed by agent identifier.
     *
     * @apiNote
     * Drives multi-agent message routing on business accounts; each entry
     * records the current online/offline state of an assigned agent.
     */
    private ConcurrentMap<String, AgentState> agentStates;

    /**
     * The map of chat-to-agent assignments keyed by chat JID.
     *
     * @apiNote
     * Pairs with {@link #agentStates}: which agent currently owns each customer
     * chat on a multi-agent business account.
     */
    private ConcurrentMap<Jid, ChatAssignment> chatAssignments;

    /**
     * The configured Customer-Payment-Instructions identifier surfaced when the
     * business sends a payment-request message.
     */
    private String paymentInstructionCpi;

    /**
     * The ordered list of custom payment methods configured on this business
     * account, in the order they should appear in the payment-method picker.
     */
    private List<CustomPaymentMethod> customPaymentMethods;

    /**
     * The currently selected merchant payment partner the business is integrated
     * with for native payments.
     */
    private MerchantPaymentPartnerAction merchantPaymentPartner;

    /**
     * The current state of the payment Terms-of-Service acknowledgement; mirrors
     * what was last applied via the payment-ToS sync action.
     */
    private PaymentTosAction paymentTos;

    /**
     * The map of saved business marketing messages keyed by id.
     */
    private ConcurrentMap<String, MarketingMessage> marketingMessages;

    /**
     * The map of marketing-message broadcasts keyed by id, recording which
     * marketing template was sent to which list and when.
     */
    private ConcurrentMap<String, MarketingMessageBroadcast> marketingMessageBroadcasts;

    /**
     * The map of configured business broadcast lists keyed by list id.
     */
    private ConcurrentMap<String, BusinessBroadcastList> businessBroadcastLists;

    /**
     * The map of business broadcast campaigns keyed by campaign id.
     */
    private ConcurrentMap<String, BusinessBroadcastCampaign> businessBroadcastCampaigns;

    /**
     * The map of per-campaign broadcast insight records keyed by campaign id.
     */
    private ConcurrentMap<String, BusinessBroadcastInsight> businessBroadcastInsights;

    /**
     * The salt used to derive per-recipient notification-content tokens, refreshed
     * by the notification subsystem when the server rotates it.
     */
    private byte[] notificationContentTokenSalt;

    /**
     * The companion-side authentication nonce that authorises this device against
     * WhatsApp's MMS (Media Management Service) when asking it to release the
     * just-applied history-sync blob from the CDN.
     *
     * @apiNote
     * Delivered once with the
     * {@link com.github.auties00.cobalt.model.message.system.history.HistorySyncType#INITIAL_BOOTSTRAP}
     * chunk and consumed by the post-apply MMS blob-deletion call; absent until
     * the first bootstrap completes.
     */
    @ProtobufProperty(index = 95, type = ProtobufType.STRING)
    private String companionMmsAuthNonce;

    /**
     * The per-account key that protects the opaque chat identifier embedded in
     * WhatsApp's shareable-chat links (the {@code wa.me/} and QR deep-link
     * surface).
     *
     * @apiNote
     * Delivered with the
     * {@link com.github.auties00.cobalt.model.message.system.history.HistorySyncType#INITIAL_BOOTSTRAP}
     * chunk and persisted for completeness; the deep-link generator runs
     * entirely server-side, so neither WA Web nor Cobalt ever invokes any
     * local encryption routine with this key. Absent until the first
     * bootstrap completes.
     */
    @ProtobufProperty(index = 96, type = ProtobufType.BYTES)
    private byte[] shareableChatLinkKey;

    /**
     * Whether the desktop client launches automatically at OS login.
     */
    @ProtobufProperty(index = 97, type = ProtobufType.BOOL)
    private Boolean startAtLogin;

    /**
     * Whether closing the main window minimises the client to the system tray
     * instead of terminating the process.
     */
    @ProtobufProperty(index = 98, type = ProtobufType.BOOL)
    private Boolean minimizeToTray;

    /**
     * Whether typed emoticons are automatically replaced with their graphical
     * emoji equivalents while composing.
     */
    @ProtobufProperty(index = 99, type = ProtobufType.BOOL)
    private Boolean replaceTextWithEmoji;

    /**
     * When banner notifications are shown on the desktop.
     */
    @ProtobufProperty(index = 100, type = ProtobufType.ENUM)
    private SettingsSyncAction.DisplayMode bannerNotificationDisplayMode;

    /**
     * When the unread counter badge is shown on the application icon.
     */
    @ProtobufProperty(index = 101, type = ProtobufType.ENUM)
    private SettingsSyncAction.DisplayMode unreadCounterBadgeDisplayMode;

    /**
     * Whether incoming message notifications are delivered at all.
     */
    @ProtobufProperty(index = 102, type = ProtobufType.BOOL)
    private Boolean messagesNotificationEnabled;

    /**
     * Whether incoming call notifications are delivered.
     */
    @ProtobufProperty(index = 103, type = ProtobufType.BOOL)
    private Boolean callsNotificationEnabled;

    /**
     * Whether reaction notifications are delivered for one-to-one chats.
     */
    @ProtobufProperty(index = 104, type = ProtobufType.BOOL)
    private Boolean reactionsNotificationEnabled;

    /**
     * Whether status reaction notifications are delivered.
     */
    @ProtobufProperty(index = 105, type = ProtobufType.BOOL)
    private Boolean statusReactionsNotificationEnabled;

    /**
     * Whether notification banners include a text preview of the message body.
     */
    @ProtobufProperty(index = 106, type = ProtobufType.BOOL)
    private Boolean textPreviewForNotificationEnabled;

    /**
     * Default tone identifier used for one-to-one chat notifications.
     */
    @ProtobufProperty(index = 107, type = ProtobufType.INT32)
    private Integer defaultNotificationToneId;

    /**
     * Default tone identifier used for group chat notifications.
     */
    @ProtobufProperty(index = 108, type = ProtobufType.INT32)
    private Integer groupDefaultNotificationToneId;

    /**
     * Selected application theme (light, dark, system-default).
     */
    @ProtobufProperty(index = 109, type = ProtobufType.ENUM)
    private AppTheme appTheme;

    /**
     * Identifier of the selected chat wallpaper.
     */
    @ProtobufProperty(index = 110, type = ProtobufType.INT32)
    private Integer wallpaperId;

    /**
     * Whether the doodle overlay is drawn on top of the chat wallpaper.
     */
    @ProtobufProperty(index = 111, type = ProtobufType.BOOL)
    private Boolean doodleWallpaperEnabled;

    /**
     * Selected font size preset for chat rendering.
     */
    @ProtobufProperty(index = 112, type = ProtobufType.INT32)
    private Integer fontSize;

    /**
     * Whether incoming images are automatically downloaded.
     */
    @ProtobufProperty(index = 113, type = ProtobufType.BOOL)
    private Boolean photosAutodownloadEnabled;

    /**
     * Whether incoming audio messages are automatically downloaded.
     */
    @ProtobufProperty(index = 114, type = ProtobufType.BOOL)
    private Boolean audiosAutodownloadEnabled;

    /**
     * Whether incoming videos are automatically downloaded.
     */
    @ProtobufProperty(index = 115, type = ProtobufType.BOOL)
    private Boolean videosAutodownloadEnabled;

    /**
     * Whether incoming documents are automatically downloaded.
     */
    @ProtobufProperty(index = 116, type = ProtobufType.BOOL)
    private Boolean documentsAutodownloadEnabled;

    /**
     * Identifier of the chat notification tone override, if set.
     */
    @ProtobufProperty(index = 117, type = ProtobufType.INT32)
    private Integer notificationToneId;

    /**
     * Quality preset applied when uploading photos and videos.
     */
    @ProtobufProperty(index = 118, type = ProtobufType.ENUM)
    private SettingsSyncAction.MediaQualitySetting mediaUploadQuality;

    /**
     * Whether spell check is enabled in the message composer.
     */
    @ProtobufProperty(index = 119, type = ProtobufType.BOOL)
    private Boolean spellCheckEnabled;

    /**
     * Whether pressing Enter sends the current message instead of inserting a
     * newline.
     */
    @ProtobufProperty(index = 120, type = ProtobufType.BOOL)
    private Boolean enterToSendEnabled;

    /**
     * Whether group message notifications are delivered.
     */
    @ProtobufProperty(index = 121, type = ProtobufType.BOOL)
    private Boolean groupMessageNotificationEnabled;

    /**
     * Whether group reaction notifications are delivered.
     */
    @ProtobufProperty(index = 122, type = ProtobufType.BOOL)
    private Boolean groupReactionsNotificationEnabled;

    /**
     * Whether status update notifications are delivered.
     */
    @ProtobufProperty(index = 123, type = ProtobufType.BOOL)
    private Boolean statusNotificationEnabled;

    /**
     * Identifier of the status notification tone.
     */
    @ProtobufProperty(index = 124, type = ProtobufType.INT32)
    private Integer statusNotificationToneId;

    /**
     * Whether call notifications play a ringtone in addition to showing the
     * banner.
     */
    @ProtobufProperty(index = 125, type = ProtobufType.BOOL)
    private Boolean playSoundForCallNotification;

    /**
     * The map of onboarding-hint state records keyed by hint id, recording which
     * of the onboarding nudges the user has dismissed or completed.
     */
    private ConcurrentMap<String, OnboardingHintState> onboardingHintStates;

    /**
     * The capabilities snapshot of the paired primary device, recording which
     * companion-routed features the primary advertises support for.
     */
    private DeviceCapabilities primaryDeviceCapabilities;

    /**
     * The per-peer device capabilities cache keyed by user JID.
     *
     * @apiNote
     * Lets fan-out short-circuit features no peer device can interpret; entries
     * are filled from the device capabilities probe.
     */
    private ConcurrentMap<Jid, DeviceCapabilitiesEntry> deviceCapabilitiesStates;

    /**
     * The map of per-message interactive state records (list-message selection,
     * template-button presses) keyed by message id.
     */
    private ConcurrentMap<String, InteractiveMessageState> interactiveMessageStates;

    /**
     * The map of business "Notes" records keyed by note id, recording the local
     * annotations attached to a customer chat.
     */
    private ConcurrentMap<String, NoteState> noteStates;

    /**
     * The map of newsletter (Channels) pin states keyed by newsletter JID.
     */
    private ConcurrentMap<Jid, NewsletterPin> newsletterPinStates;

    /**
     * Whether the local account has published a profile picture; {@code null}
     * until the server reports the value.
     */
    private Boolean hasAvatar;

    /**
     * The map of call-log entries keyed by call id.
     *
     * @apiNote
     * Drives the "Calls" tab; round-trips through app-state sync so call history
     * stays consistent across linked devices.
     */
    private ConcurrentMap<String, CallLog> callLogStates;

    /**
     * The map tracking whether each bot has been sent the initial welcome
     * request, keyed by bot JID.
     */
    private ConcurrentMap<Jid, BotWelcomeRequestState> botWelcomeRequestStates;

    /**
     * The map of AI-thread titles keyed by thread id, used by the AI chat surface
     * to label recent conversations in the side rail.
     */
    private ConcurrentMap<String, AiThreadTitle> aiThreadTitles;

    /**
     * The current "start chat by username" preference, mirroring the value last
     * applied through the chat-start-mode sync action.
     */
    private UsernameChatStartModeAction.ChatStartMode usernameChatStartMode;

    /**
     * The current notification-activity preference (which surfaces may trigger
     * push notifications) last applied through sync action.
     */
    private NotificationActivitySettingAction.NotificationActivitySetting notificationActivitySetting;

    /**
     * The ordered list of recently used emoji with their decayed usage weights.
     *
     * @apiNote
     * Drives the order of the "Frequently used" tab in the emoji picker; held
     * as a copy-on-write list because reads dominate writes.
     */
    private List<RecentEmojiWeight> recentEmojiWeights;

    /**
     * The opaque identifier announced with newsletter (Channels) subscriptions
     * so the server can correlate this device's subscriptions across reconnects.
     */
    private String newsletterSubscriptionUserIdentifier;

    /**
     * The last applied music-user-id action; records the cross-app music
     * identifier so status posts can attribute the played track.
     */
    private MusicUserIdAction musicUserIdState;

    /**
     * The opaque interest-graph blob recording which newsletter (Channels)
     * topics the user has subscribed to or explicitly muted.
     */
    private String newsletterSavedInterests;

    /**
     * Whether the user has opted into status-post notification preferences;
     * {@code null} until the server reports a value.
     */
    private Boolean statusPostOptInNotificationPreferencesEnabled;

    /**
     * The current state of the "Private Processing" feature consent.
     */
    private PrivateProcessingSettingAction.PrivateProcessingStatus privateProcessingStatus;

    /**
     * Whether the user has opted out of personalised newsletter (Channels)
     * recommendations; {@code null} until the server reports a value.
     */
    private Boolean channelsPersonalisedRecommendationOptOut;

    /**
     * The serialised definition of the user-created bot, if any.
     */
    private byte[] userCreatedBotDefinition;

    /**
     * The current state of the "MAIBA" AI business agent control toggle.
     */
    private MaibaAIFeaturesControlAction.MaibaAIFeatureStatus aiBusinessAgentStatus;

    /**
     * The timestamp of the most recent successful pairing handshake, used to
     * age-rate session credentials and to surface the pairing date in
     * diagnostics.
     */
    private Instant pairingTimestamp;

    /**
     * The map of peer-message dedupe records keyed by peer message id.
     *
     * @apiNote
     * Lets the peer-message receiver drop redeliveries of an in-band peer
     * message that has already been processed; entries are pruned as their
     * dedupe window expires.
     */
    private final ConcurrentMap<String, ChatMessageInfo> peerMessages;

    /**
     * The platform logger lazily resolved against the concrete subclass name.
     *
     * @apiNote
     * Allows subclasses to inherit a consistent logger naming scheme without
     * declaring their own static logger.
     */
    protected final System.Logger logger;

    /**
     * Builds an {@link AbstractWhatsAppStore} from the canonical set of protobuf
     * fields, generating any missing crypto material and seeding the transient
     * in-memory collections.
     *
     * @apiNote
     * Invoked by the protobuf deserialiser and by every subclass constructor;
     * non-protobuf state (listeners, locks, transient sets) is allocated here so
     * subclasses do not have to repeat the wiring. Crypto fields that arrive as
     * {@code null} ({@link #registrationId}, {@link #noiseKeyPair},
     * {@link #identityKeyPair}, {@link #signedKeyPair}, {@link #fdid},
     * {@link #deviceId}, {@link #advertisingId}, {@link #identityId},
     * {@link #backupToken}) are generated on the fly so a freshly built store is
     * immediately usable as a {@link com.github.auties00.libsignal.SignalProtocolStore}.
     *
     * @implNote
     * This implementation walks the contact map once to populate
     * {@link #lidToPhoneMappings} / {@link #phoneToLidMappings} so that LID/phone
     * lookups work on the first call after deserialisation, without waiting for
     * the syncd pipeline to repopulate them.
     *
     * @param uuid                                see {@link #uuid}
     * @param phoneNumber                         see {@link #phoneNumber}
     * @param clientType                          see {@link #clientType}
     * @param initializationTimeStamp             see {@link #initializationTimeStamp}
     * @param device                              see {@link #device}
     * @param releaseChannel                      see {@link #releaseChannel}
     * @param online                              see {@link #online}
     * @param locale                              see {@link #locale}
     * @param name                                see {@link #name}
     * @param verifiedName                        see {@link #verifiedName}
     * @param profilePicture                      see {@link #profilePicture}
     * @param selfTextStatus                      see {@link #selfTextStatus}
     * @param jid                                 see {@link #jid}
     * @param lid                                 see {@link #lid}
     * @param businessAddress                     see {@link #businessAddress}
     * @param businessLongitude                   see {@link #businessLongitude}
     * @param businessLatitude                    see {@link #businessLatitude}
     * @param businessDescription                 see {@link #businessDescription}
     * @param businessWebsites                    see {@link #businessWebsites}
     * @param businessEmail                       see {@link #businessEmail}
     * @param businessCategories                  see {@link #businessCategories}
     * @param contactsMap                            see {@link #contactsMap}
     * @param privacySettingsMap                     see {@link #privacySettingsMap}
     * @param unarchiveChats                      see {@link #unarchiveChats}
     * @param twentyFourHourFormat                see {@link #twentyFourHourFormat}
     * @param newChatsEphemeralTimer              see {@link #newChatsEphemeralTimer}
     * @param webHistoryPolicy                    see {@link #webHistoryPolicy}
     * @param checkPatchMacs                      see {@link #checkPatchMacs}
     * @param syncedChats                         see {@link #syncedChats}
     * @param syncedContacts                      see {@link #syncedContacts}
     * @param syncedNewsletters                   see {@link #syncedNewsletters}
     * @param syncedStatus                        see {@link #syncedStatus}
     * @param syncedBusinessCertificate           see {@link #syncedBusinessCertificate}
     * @param registrationId                      see {@link #registrationId}; randomised when {@code null}
     * @param noiseKeyPair                        see {@link #noiseKeyPair}; randomised when {@code null}
     * @param identityKeyPair                     see {@link #identityKeyPair}; randomised when {@code null}
     * @param signedDeviceIdentity                see {@link #signedDeviceIdentity}
     * @param signedKeyPair                       see {@link #signedKeyPair}; derived from the identity key when {@code null}
     * @param preKeysMap                             see {@link #preKeysMap}
     * @param fdid                                see {@link #fdid}; randomised when {@code null}
     * @param deviceId                            see {@link #deviceId}; randomised when {@code null}
     * @param advertisingId                       see {@link #advertisingId}; randomised when {@code null}
     * @param identityId                          see {@link #identityId}; randomised when {@code null}
     * @param backupToken                         see {@link #backupToken}; randomised when {@code null}
     * @param senderKeysMap                          see {@link #senderKeysMap}
     * @param appStateKeysMap                        see {@link #appStateKeysMap}
     * @param sessionsMap                            see {@link #sessionsMap}
     * @param hashStatesMap                          see {@link #hashStatesMap}
     * @param registered                          see {@link #registered}
     * @param showSecurityNotifications           see {@link #showSecurityNotifications}
     * @param recentStickersMap                      see {@link #recentStickersMap}
     * @param favouriteStickersMap                   see {@link #favouriteStickersMap}
     * @param quickRepliesMap                        see {@link #quickRepliesMap}
     * @param labelsMap                              see {@link #labelsMap}
     * @param clientVersion                       see {@link #clientVersion}
     * @param companionVersion                    see {@link #companionVersion}
     * @param lastAdvCheckTime                    see {@link #lastAdvCheckTime}
     * @param remoteIdentitiesMap                    see {@link #remoteIdentitiesMap}
     * @param missingSyncKeysMap                     see {@link #missingSyncKeysMap}
     * @param advSecretKey                        see {@link #advSecretKey}
     * @param verifiedBusinessNamesMap               see {@link #verifiedBusinessNamesMap}
     * @param directory                           see {@link #directory}
     * @param primaryDeviceSupportsSyncdRecovery  see {@link #primaryDeviceSupportsSyncdRecovery}
     * @param disableLinkPreviews                 see {@link #disableLinkPreviews}
     * @param relayAllCalls                       see {@link #relayAllCalls}
     * @param externalWebBeta                     see {@link #externalWebBeta}
     * @param chatLockSettings                    see {@link #chatLockSettings}
     * @param favoriteChats                       see {@link #favoriteChats}
     * @param primaryFeatures                     see {@link #primaryFeatures}
     * @param mentionEveryoneMuteExpirationsMap      see {@link #mentionEveryoneMuteExpirationsMap}
     * @param orphanMutationEntriesMap               see {@link #orphanMutationEntriesMap}
     * @param outContactsMap                         see {@link #outContactsMap}
     * @param clockSkewSeconds                    see {@link #clockSkewSeconds}
     * @param groupAbPropsEmergencyPushTimestamp  see {@link #groupAbPropsEmergencyPushTimestamp}
     * @param abPropsAbKey                        see {@link #abPropsAbKey}
     * @param abPropsHash                         see {@link #abPropsHash}
     * @param abPropsRefresh                      see {@link #abPropsRefresh}
     * @param abPropsLastSyncTime                 see {@link #abPropsLastSyncTime}
     * @param abPropsRefreshId                    see {@link #abPropsRefreshId}
     * @param abPropsWebRefreshId                 see {@link #abPropsWebRefreshId}
     * @param groupAbPropsRefreshId               see {@link #groupAbPropsRefreshId}
     * @param baseKeysMap                            see {@link #baseKeysMap}
     * @param wamSequenceNumbersMap                  see {@link #wamSequenceNumbersMap}
     * @param companionMmsAuthNonce               see {@link #companionMmsAuthNonce}
     * @param shareableChatLinkKey                see {@link #shareableChatLinkKey}
     * @throws NullPointerException if any of the non-nullable arguments
     *         ({@code uuid}, {@code clientType}, {@code device},
     *         {@code contactsMap}, {@code privacySettingsMap}, {@code preKeysMap},
     *         {@code senderKeysMap}, {@code appStateKeysMap}, {@code sessionsMap},
     *         {@code hashStatesMap}) is {@code null}
     */
    public AbstractWhatsAppStore(UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, List<URI> businessWebsites, String businessEmail, List<BusinessCategory> businessCategories, ConcurrentHashMap<Jid, Contact> contactsMap, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettingsMap, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeysMap, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeysMap, LinkedHashMap<String, AppStateSyncKey> appStateKeysMap, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessionsMap, ConcurrentMap<SyncPatchType, SyncHashValue> hashStatesMap, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickersMap, ConcurrentMap<String, Sticker> favouriteStickersMap, ConcurrentMap<String, QuickReply> quickRepliesMap, ConcurrentMap<String, Label> labelsMap, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentitiesMap, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeysMap, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNamesMap, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirationsMap, ConcurrentMap<SyncPatchType, OrphanMutationEntries> orphanMutationEntriesMap, ConcurrentHashMap<Jid, OutContact> outContactsMap, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeysMap, ConcurrentMap<Integer, Integer> wamSequenceNumbersMap, String companionMmsAuthNonce, byte[] shareableChatLinkKey, Boolean startAtLogin, Boolean minimizeToTray, Boolean replaceTextWithEmoji, SettingsSyncAction.DisplayMode bannerNotificationDisplayMode, SettingsSyncAction.DisplayMode unreadCounterBadgeDisplayMode, Boolean messagesNotificationEnabled, Boolean callsNotificationEnabled, Boolean reactionsNotificationEnabled, Boolean statusReactionsNotificationEnabled, Boolean textPreviewForNotificationEnabled, Integer defaultNotificationToneId, Integer groupDefaultNotificationToneId, AppTheme appTheme, Integer wallpaperId, Boolean doodleWallpaperEnabled, Integer fontSize, Boolean photosAutodownloadEnabled, Boolean audiosAutodownloadEnabled, Boolean videosAutodownloadEnabled, Boolean documentsAutodownloadEnabled, Integer notificationToneId, SettingsSyncAction.MediaQualitySetting mediaUploadQuality, Boolean spellCheckEnabled, Boolean enterToSendEnabled, Boolean groupMessageNotificationEnabled, Boolean groupReactionsNotificationEnabled, Boolean statusNotificationEnabled, Integer statusNotificationToneId, Boolean playSoundForCallNotification) {
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
        this.businessWebsites = requireNonNullElseGet(businessWebsites, ArrayList::new);
        this.businessEmail = businessEmail;
        this.businessCategories = requireNonNullElseGet(businessCategories, ArrayList::new);
        this.profilePicture = profilePicture;
        this.selfTextStatus = selfTextStatus;
        this.jid = jid;
        this.lid = lid;
        this.contactsMap = Objects.requireNonNull(contactsMap, "contactsMap cannot be null");
        this.contactTextStatusesMap = new ConcurrentHashMap<>();
        this.outContactsMap = requireNonNullElseGet(outContactsMap, ConcurrentHashMap::new);
        this.clockSkewSeconds = clockSkewSeconds;
        this.groupAbPropsEmergencyPushTimestamp = groupAbPropsEmergencyPushTimestamp;
        this.abPropsAbKey = abPropsAbKey;
        this.abPropsHash = abPropsHash;
        this.abPropsRefresh = abPropsRefresh;
        this.abPropsLastSyncTime = abPropsLastSyncTime;
        this.abPropsRefreshId = abPropsRefreshId;
        this.abPropsWebRefreshId = abPropsWebRefreshId;
        this.groupAbPropsRefreshId = groupAbPropsRefreshId;

        this.privacySettingsMap = Objects.requireNonNull(privacySettingsMap, "privacySettingsMap cannot be null");
        this.calls = new ConcurrentHashMap<>();
        this.unarchiveChats = unarchiveChats;
        this.twentyFourHourFormat = twentyFourHourFormat;
        this.initializationTimeStamp = requireNonNullElseGet(initializationTimeStamp, Instant::now);
        this.newChatsEphemeralTimer = Objects.requireNonNullElse(newChatsEphemeralTimer, ChatEphemeralTimer.OFF);
        this.webHistoryPolicy = webHistoryPolicy;
        this.releaseChannel = Objects.requireNonNullElse(releaseChannel, ClientReleaseChannel.RELEASE);
        this.device = Objects.requireNonNull(device, "device cannot be null");
        this.checkPatchMacs = checkPatchMacs;
        this.syncedChats = syncedChats;
        this.syncedContacts = syncedContacts;
        this.syncedNewsletters = syncedNewsletters;
        this.syncedStatus = syncedStatus;
        this.syncedBusinessCertificate = syncedBusinessCertificate;
        this.favouriteStickersMap = favouriteStickersMap;
        this.quickRepliesMap = quickRepliesMap;
        this.labelsMap = labelsMap;
        this.listeners = ConcurrentHashMap.newKeySet();
        this.lidToPhoneMappings = new ConcurrentHashMap<>();
        this.phoneToLidMappings = new ConcurrentHashMap<>();
        this.lidMappingTimestamps = new ConcurrentHashMap<>();
        for (var contact : contactsMap.values()) {
            contact.lid()
                    .ifPresent(entry -> registerLidMapping(contact.jid(), entry));
        }
        this.registrationId = requireNonNullElseGet(registrationId, () -> DataUtils.randomInt(16380) + 1);
        this.noiseKeyPair = requireNonNullElseGet(noiseKeyPair, SignalIdentityKeyPair::random);
        this.identityKeyPair = requireNonNullElseGet(identityKeyPair, SignalIdentityKeyPair::random);
        this.signedKeyPair = requireNonNullElseGet(signedKeyPair, () -> SignalSignedKeyPair.of(this.registrationId, this.identityKeyPair));
        this.preKeysMap = Objects.requireNonNull(preKeysMap, "preKeysMap cannot be null");
        this.fdid = requireNonNullElseGet(fdid, UUID::randomUUID);
        this.deviceId = requireNonNullElseGet(deviceId, () -> HexFormat.of().parseHex(UUID.randomUUID().toString().replace("-", "")));
        this.advertisingId = requireNonNullElseGet(advertisingId, UUID::randomUUID);
        this.identityId = requireNonNullElseGet(identityId, () -> DataUtils.randomByteArray(16));
        this.backupToken = requireNonNullElseGet(backupToken, () -> DataUtils.randomByteArray(20));
        this.signedDeviceIdentity = signedDeviceIdentity;
        this.senderKeysMap = Objects.requireNonNull(senderKeysMap, "senderKeysMap cannot be null");
        this.appStateKeysMap = Objects.requireNonNull(appStateKeysMap, "appStateKeysMap cannot be null");
        this.sessionsMap = Objects.requireNonNull(sessionsMap, "sessionsMap cannot be null");
        this.hashStatesMap = Objects.requireNonNull(hashStatesMap, "hashStatesMap cannot be null");
        this.registered = registered;
        this.showSecurityNotifications = showSecurityNotifications;
        this.recentStickersMap = recentStickersMap;
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
        this.optOutListHashes = new ConcurrentHashMap<>();
        this.optOutListEntries = new ConcurrentHashMap<>();
        this.contactBlacklistHashes = new ConcurrentHashMap<>();
        this.contactBlacklistEntries = new ConcurrentHashMap<>();
        this.pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
        this.groupSenderKeyDistribution = new ConcurrentHashMap<>();
        this.orphanPaymentNotifications = new ConcurrentHashMap<>();
        this.tosNoticeIds = ConcurrentHashMap.newKeySet();
        this.businessFeatureFlags = new ConcurrentHashMap<>();
        this.businessCampaignStatuses = new ConcurrentHashMap<>();
        this.businessSubscriptions = new ConcurrentHashMap<>();
        this.ctwaDataSharingPreferences = new ConcurrentHashMap<>();
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.remoteIdentitiesMap = requireNonNullElseGet(remoteIdentitiesMap, ConcurrentHashMap::new);
        this.missingSyncKeysMap = requireNonNullElseGet(missingSyncKeysMap, ConcurrentHashMap::new);
        this.advSecretKey = advSecretKey;
        this.verifiedBusinessNamesMap = requireNonNullElseGet(verifiedBusinessNamesMap, ConcurrentHashMap::new);
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
        this.mentionEveryoneMuteExpirationsMap = requireNonNullElseGet(mentionEveryoneMuteExpirationsMap, ConcurrentHashMap::new);
        this.orphanMutationEntriesMap = new ConcurrentHashMap<>();
        this.identityEncryptionRange = new ConcurrentHashMap<>();
        this.baseKeysMap = requireNonNullElseGet(baseKeysMap, ConcurrentHashMap::new);
        this.wamSequenceNumbersMap = requireNonNullElseGet(wamSequenceNumbersMap, ConcurrentHashMap::new);
        this.companionMmsAuthNonce = companionMmsAuthNonce;
        this.shareableChatLinkKey = shareableChatLinkKey;
        this.startAtLogin = startAtLogin;
        this.minimizeToTray = minimizeToTray;
        this.replaceTextWithEmoji = replaceTextWithEmoji;
        this.bannerNotificationDisplayMode = bannerNotificationDisplayMode;
        this.unreadCounterBadgeDisplayMode = unreadCounterBadgeDisplayMode;
        this.messagesNotificationEnabled = messagesNotificationEnabled;
        this.callsNotificationEnabled = callsNotificationEnabled;
        this.reactionsNotificationEnabled = reactionsNotificationEnabled;
        this.statusReactionsNotificationEnabled = statusReactionsNotificationEnabled;
        this.textPreviewForNotificationEnabled = textPreviewForNotificationEnabled;
        this.defaultNotificationToneId = defaultNotificationToneId;
        this.groupDefaultNotificationToneId = groupDefaultNotificationToneId;
        this.appTheme = appTheme;
        this.wallpaperId = wallpaperId;
        this.doodleWallpaperEnabled = doodleWallpaperEnabled;
        this.fontSize = fontSize;
        this.photosAutodownloadEnabled = photosAutodownloadEnabled;
        this.audiosAutodownloadEnabled = audiosAutodownloadEnabled;
        this.videosAutodownloadEnabled = videosAutodownloadEnabled;
        this.documentsAutodownloadEnabled = documentsAutodownloadEnabled;
        this.notificationToneId = notificationToneId;
        this.mediaUploadQuality = mediaUploadQuality;
        this.spellCheckEnabled = spellCheckEnabled;
        this.enterToSendEnabled = enterToSendEnabled;
        this.groupMessageNotificationEnabled = groupMessageNotificationEnabled;
        this.groupReactionsNotificationEnabled = groupReactionsNotificationEnabled;
        this.statusNotificationEnabled = statusNotificationEnabled;
        this.statusNotificationToneId = statusNotificationToneId;
        this.playSoundForCallNotification = playSoundForCallNotification;
        this.encryptionSequence = new AtomicLong();
        this.logger = System.getLogger(this.getClass().getName());
        this.offlineResumeState = WhatsAppClientOfflineResumeState.INIT;
        this.offlineDeliveryLatch = new CountDownLatch(1);
        this.usersNeedingSenderKeyRotation = ConcurrentHashMap.newKeySet();
        this.peerMessages = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation short-circuits to the {@link Contact} case when the
     * supplied {@link JidProvider} is itself a {@link Contact}, then routes the
     * lookup by JID server. For phone-server JIDs (regular and bot user JIDs)
     * the direct lookup is tried first, then a fallback through
     * {@link #findLidByPhone}; for LID-server JIDs the symmetric fallback runs
     * through {@link #findPhoneByLid}; for any other server type only the direct
     * lookup is attempted.
     */
    @Override
    public Optional<Contact> findContactByJid(JidProvider jid) {
        return switch (jid) {
            case Contact contact -> Optional.of(contact);
            case null -> Optional.empty();
            case Chat _, Newsletter _, Jid _, JidServer _-> {
                var targetJid = jid.toJid();
                if(targetJid.hasUserServer()) {
                    var jidContact = contactsMap.get(targetJid);
                    if(jidContact != null) {
                        yield Optional.of(jidContact);
                    } else {
                        yield findLidByPhone(targetJid)
                                .map(contactsMap::get);
                    }
                } else if(targetJid.hasLidServer()) {
                    var lidContact = contactsMap.get(targetJid);
                    if(lidContact != null) {
                        yield Optional.of(lidContact);
                    } else {
                        yield findPhoneByLid(targetJid)
                                .map(contactsMap::get);
                    }
                } else {
                    var contact = contactsMap.get(targetJid);
                    yield Optional.ofNullable(contact);
                }
            }
        };
    }

    @Override
    public Collection<Contact> contacts() {
        return List.copyOf(contactsMap.values());
    }

    @Override
    public Collection<ContactTextStatus> contactTextStatuses() {
        return List.copyOf(contactTextStatusesMap.values());
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
        contactsMap.put(contact.jid(), contact);
        return contact;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation normalises the supplied JID through
     * {@link Jid#toUserJid()} and then mirrors the LID/phone fallback chain of
     * {@link #findContactByJid} so a status cached under one identity surfaces
     * when queried by the paired identity.
     */
    @Override
    public Optional<ContactTextStatus> findContactTextStatus(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }

        var targetJid = jid.toJid().toUserJid();
        if (targetJid.hasUserServer()) {
            var direct = contactTextStatusesMap.get(targetJid);
            if (direct != null) {
                return Optional.of(direct);
            }
            return findLidByPhone(targetJid).map(contactTextStatusesMap::get);
        }

        if (targetJid.hasLidServer()) {
            var direct = contactTextStatusesMap.get(targetJid);
            if (direct != null) {
                return Optional.of(direct);
            }
            return findPhoneByLid(targetJid).map(contactTextStatusesMap::get);
        }

        return Optional.ofNullable(contactTextStatusesMap.get(targetJid));
    }

    @Override
    public void addContactTextStatus(Jid contactJid, ContactTextStatus status) {
        Objects.requireNonNull(contactJid, "contactJid cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        contactTextStatusesMap.put(contactJid.toUserJid(), status);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation tries the direct removal first and, if that misses,
     * mirrors the LID/phone fallback chain of {@link #findContactTextStatus} so
     * a status cached under one identity is removed when called by the paired
     * identity.
     */
    @Override
    public Optional<ContactTextStatus> removeContactTextStatus(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }

        var targetJid = jid.toJid().toUserJid();
        var removed = contactTextStatusesMap.remove(targetJid);
        if (removed != null) {
            return Optional.of(removed);
        }

        if (targetJid.hasUserServer()) {
            return findLidByPhone(targetJid).map(contactTextStatusesMap::remove);
        }

        if (targetJid.hasLidServer()) {
            return findPhoneByLid(targetJid).map(contactTextStatusesMap::remove);
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
        return Set.copyOf(tosNoticeIds);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation copies the supplied set into a fresh
     * {@link ConcurrentHashMap#newKeySet} so subsequent caller mutations cannot
     * leak into the stored backing set; a {@code null} argument is treated as
     * "clear".
     */
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
        return List.copyOf(businessFeatureFlags.values());
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
        return List.copyOf(businessCampaignStatuses.values());
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
        return List.copyOf(businessSubscriptions.values());
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
        return List.copyOf(ctwaDataSharingPreferences.values());
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

    /**
     * Exposes the live {@link #outContactsMap} map to concrete subclasses that need
     * mutable access during persistence.
     *
     * @apiNote
     * Returns the backing map rather than a copy so subclasses persisting
     * incremental updates do not have to round-trip through
     * {@link #outContacts()} which returns an unmodifiable view.
     *
     * @return the live backing map for {@link #outContactsMap}
     */
    protected ConcurrentHashMap<Jid, OutContact> outContactsMap() {
        return outContactsMap;
    }

    /**
     * Returns the live {@link #contactsMap} map backing this store.
     *
     * @return the live {@link #contactsMap} map
     */
    protected ConcurrentHashMap<Jid, Contact> contactsMap() {
        return contactsMap;
    }

    /**
     * Returns the live {@link #contactTextStatusesMap} map backing this store.
     *
     * @return the live {@link #contactTextStatusesMap} map
     */
    protected ConcurrentHashMap<Jid, ContactTextStatus> contactTextStatusesMap() {
        return contactTextStatusesMap;
    }

    /**
     * Returns the live {@link #privacySettingsMap} map backing this store.
     *
     * @return the live {@link #privacySettingsMap} map
     */
    protected ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettingsMap() {
        return privacySettingsMap;
    }

    /**
     * Returns the live {@link #preKeysMap} map backing this store.
     *
     * @return the live {@link #preKeysMap} map
     */
    protected LinkedHashMap<Integer, SignalPreKeyPair> preKeysMap() {
        return preKeysMap;
    }

    /**
     * Returns the live {@link #appStateKeysMap} map backing this store.
     *
     * @return the live {@link #appStateKeysMap} map
     */
    protected LinkedHashMap<String, AppStateSyncKey> appStateKeysMap() {
        return appStateKeysMap;
    }

    /**
     * Returns the live {@link #quickRepliesMap} map backing this store.
     *
     * @return the live {@link #quickRepliesMap} map
     */
    protected ConcurrentMap<String, QuickReply> quickRepliesMap() {
        return quickRepliesMap;
    }

    /**
     * Returns the live {@link #labelsMap} map backing this store.
     *
     * @return the live {@link #labelsMap} map
     */
    protected ConcurrentMap<String, Label> labelsMap() {
        return labelsMap;
    }

    /**
     * Returns the live {@link #missingSyncKeysMap} map backing this store.
     *
     * @return the live {@link #missingSyncKeysMap} map
     */
    protected ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeysMap() {
        return missingSyncKeysMap;
    }

    /**
     * Returns the live {@link #senderKeysMap} map backing this store.
     *
     * @return the live {@link #senderKeysMap} map
     */
    protected ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeysMap() {
        return senderKeysMap;
    }

    /**
     * Returns the live {@link #sessionsMap} map backing this store.
     *
     * @return the live {@link #sessionsMap} map
     */
    protected ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessionsMap() {
        return sessionsMap;
    }

    /**
     * Returns the live {@link #hashStatesMap} map backing this store.
     *
     * @return the live {@link #hashStatesMap} map
     */
    protected ConcurrentMap<SyncPatchType, SyncHashValue> hashStatesMap() {
        return hashStatesMap;
    }

    /**
     * Returns the live {@link #recentStickersMap} map backing this store.
     *
     * @return the live {@link #recentStickersMap} map
     */
    protected ConcurrentMap<String, Sticker> recentStickersMap() {
        return recentStickersMap;
    }

    /**
     * Returns the live {@link #favouriteStickersMap} map backing this store.
     *
     * @return the live {@link #favouriteStickersMap} map
     */
    protected ConcurrentMap<String, Sticker> favouriteStickersMap() {
        return favouriteStickersMap;
    }

    /**
     * Returns the live {@link #remoteIdentitiesMap} map backing this store.
     *
     * @return the live {@link #remoteIdentitiesMap} map
     */
    protected ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentitiesMap() {
        return remoteIdentitiesMap;
    }

    /**
     * Returns the live {@link #verifiedBusinessNamesMap} map backing this store.
     *
     * @return the live {@link #verifiedBusinessNamesMap} map
     */
    protected ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNamesMap() {
        return verifiedBusinessNamesMap;
    }

    /**
     * Returns the on-disk {@link #directory} under which this store persists, or
     * {@code null} on an in-memory store.
     *
     * @return the persistence {@link #directory}, or {@code null} if in-memory
     */
    public Path directory() {
        return directory;
    }

    /**
     * Returns the live {@link #mentionEveryoneMuteExpirationsMap} map backing this store.
     *
     * @return the live {@link #mentionEveryoneMuteExpirationsMap} map
     */
    protected ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirationsMap() {
        return mentionEveryoneMuteExpirationsMap;
    }

    /**
     * Returns the live {@link #orphanMutationEntriesMap} map backing this store.
     *
     * @return the live {@link #orphanMutationEntriesMap} map
     */
    protected ConcurrentMap<SyncPatchType, OrphanMutationEntries> orphanMutationEntriesMap() {
        return orphanMutationEntriesMap;
    }

    /**
     * Returns the live {@link #baseKeysMap} map backing this store.
     *
     * @return the live {@link #baseKeysMap} map
     */
    protected ConcurrentMap<String, byte[]> baseKeysMap() {
        return baseKeysMap;
    }

    /**
     * Returns the live {@link #wamSequenceNumbersMap} map backing this store.
     *
     * @return the live {@link #wamSequenceNumbersMap} map
     */
    protected ConcurrentMap<Integer, Integer> wamSequenceNumbersMap() {
        return wamSequenceNumbersMap;
    }

    @Override
    public Collection<OutContact> outContacts() {
        return List.copyOf(outContactsMap.values());
    }

    @Override
    public Optional<OutContact> findOutContact(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(outContactsMap.get(jid));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation merges into any existing {@link OutContact} for the
     * same JID rather than overwriting it: only {@code fullName} and
     * {@code firstName} are taken from the incoming entry, preserving any
     * additional state the existing record accumulated.
     */
    @Override
    public WhatsAppStore addOutContact(OutContact outContact) {
        Objects.requireNonNull(outContact, "outContact cannot be null");
        outContactsMap.merge(outContact.jid(), outContact, (existing, incoming) -> {
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
        return Optional.ofNullable(outContactsMap.remove(jid));
    }

    @Override
    public WhatsAppStore clearOutContacts() {
        outContactsMap.clear();
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the LID/phone fallback chain of
     * {@link #findContactByJid} so that a contact stored under one identity is
     * removed when called by the paired identity.
     */
    @Override
    public Optional<Contact> removeContact(JidProvider contactJid) {
        if(contactJid == null) {
            return Optional.empty();
        } else {
            var targetJid = contactJid.toJid();
            if(targetJid.hasUserServer()) {
                var jidContact = contactsMap.remove(targetJid);
                if(jidContact != null) {
                    return Optional.of(jidContact);
                } else {
                    return findLidByPhone(targetJid)
                            .map(contactsMap::remove);
                }
            } else if(targetJid.hasLidServer()) {
                var lidContact = contactsMap.remove(targetJid);
                if(lidContact != null) {
                    return Optional.of(lidContact);
                } else {
                    return findPhoneByLid(targetJid)
                            .map(contactsMap::remove);
                }
            } else {
                var chat = contactsMap.remove(targetJid);
                return Optional.ofNullable(chat);
            }
        }
    }

    @Override
    public void registerLidMapping(Jid phoneJid, Jid lidJid) {
        registerLidMapping(phoneJid, lidJid, null);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation normalises both JIDs through {@link Jid#withoutData()}
     * before indexing so that mappings stay stable across device-suffix changes.
     * When a non-null timestamp is supplied it is compared against
     * {@link #lidMappingTimestamps}, and a strictly older value is dropped so
     * out-of-order delivery of mapping events cannot rewrite a fresher binding.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation short-circuits when the supplied LID belongs to the
     * local account and otherwise consults {@link #lidToPhoneMappings} first.
     * On a miss it falls back to scanning {@link #contactsMap} for any entry whose
     * {@link Contact#lid()} matches because tests and partial-state stores may
     * have populated only the contact-side LID without registering an entry in
     * the mapping table.
     */
    @Override
    public Optional<Jid> findPhoneByLid(Jid lidJid) {
        if (lidJid == null) {
            return Optional.empty();
        }
        var localLid = lid;
        if (localLid != null && Objects.equals(lidJid.user(), localLid.user())) {
            return Optional.ofNullable(jid)
                    .map(pn -> pn.withDevice(lidJid.device()));
        }
        var mapped = lidToPhoneMappings.get(lidJid.withoutData());
        if (mapped != null) {
            return Optional.of(mapped);
        }
        return contactsMap.values()
                .stream()
                .filter(contact -> contact.lid().filter(lidJid::equals).isPresent())
                .findFirst()
                .map(Contact::jid);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation short-circuits when the supplied JID already targets
     * the LID server or belongs to the local account, then consults
     * {@link #phoneToLidMappings}. On a miss it reads {@link Contact#lid()}
     * straight from the {@link #contactsMap} map for the same dual-source reason as
     * {@link #findPhoneByLid}; the lookup deliberately bypasses
     * {@link #findContactByJid} because that method calls back into this one
     * for cross-LID resolution and would recurse forever.
     */
    @Override
    public Optional<Jid> findLidByPhone(Jid phoneJid) {
        if (phoneJid == null) {
            return Optional.empty();
        }
        if (phoneJid.hasLidServer()) {
            return Optional.of(phoneJid);
        }
        var localJid = jid;
        if (localJid != null && Objects.equals(phoneJid.user(), localJid.user())) {
            return Optional.ofNullable(lid)
                    .map(lid -> lid.withDevice(phoneJid.device()));
        }
        var normalisedPhone = phoneJid.withoutData();
        var mapped = phoneToLidMappings.get(normalisedPhone);
        if (mapped != null) {
            return Optional.of(mapped);
        }
        var contact = contactsMap.get(normalisedPhone);
        if (contact != null) {
            return contact.lid();
        }
        return Optional.empty();
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation substitutes {@link #DEFAULT_NAME} when the underlying
     * {@link #name} field is {@code null} so that callers never have to guard
     * against a missing display name; mutating {@link #name} via
     * {@link #setName} swaps it back in.
     */
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
    public List<URI> businessWebsites() {
        return businessWebsites == null ? List.of() : List.copyOf(businessWebsites);
    }

    @Override
    public WhatsAppStore setBusinessWebsites(List<URI> businessWebsites) {
        this.businessWebsites = businessWebsites;
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
    public List<BusinessCategory> businessCategories() {
        return businessCategories == null ? List.of() : List.copyOf(businessCategories);
    }

    @Override
    public WhatsAppStore setBusinessCategories(List<BusinessCategory> businessCategories) {
        this.businessCategories = businessCategories;
        return this;
    }
    
    @Override
    public Collection<IncomingCall> calls() {
        return List.copyOf(calls.values());
    }

    @Override
    public Collection<PrivacySettingEntry> privacySettings() {
        return List.copyOf(privacySettingsMap.values());
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
        return List.copyOf(listeners);
    }

    @Override
    public WhatsAppClientOfflineResumeState offlineResumeState() {
        return offlineResumeState;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation drives the {@link #offlineDeliveryLatch} alongside the
     * state field so that {@link #waitForOfflineDeliveryEnd} unblocks as soon as
     * the offline resume completes. A transition into
     * {@link WhatsAppClientOfflineResumeState#COMPLETE} counts the latch down;
     * a transition into {@link WhatsAppClientOfflineResumeState#INIT} replaces
     * the latch with a fresh one so a subsequent reconnect can wait again on
     * the same store instance.
     */
    @Override
    public WhatsAppStore setOfflineResumeState(WhatsAppClientOfflineResumeState state) {
        this.offlineResumeState = Objects.requireNonNull(state, "state cannot be null");
        if (state == WhatsAppClientOfflineResumeState.COMPLETE) {
            offlineDeliveryLatch.countDown();
        } else if (state == WhatsAppClientOfflineResumeState.INIT) {
            offlineDeliveryLatch = new CountDownLatch(1);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation caps the wait at five minutes and swallows
     * {@link InterruptedException} after restoring the interrupt flag, so
     * callers can treat the method as a best-effort barrier rather than a
     * blocking dependency. When {@link #offlineResumeState} already reports
     * {@link WhatsAppClientOfflineResumeState#COMPLETE} the call returns
     * immediately without touching {@link #offlineDeliveryLatch}.
     */
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
        return List.copyOf(preKeysMap.sequencedValues());
    }

    @Override
    public boolean hasPreKeys() {
        return !preKeysMap.isEmpty();
    }

    /**
     * Looks up a one-time {@link SignalPreKeyPair} by its numeric id.
     *
     * @apiNote
     * Drives the recipient-side of the Signal X3DH handshake; when a peer
     * sends a {@code PreKeyWhisperMessage} citing a particular pre-key id,
     * the session builder calls this method to locate the matching private
     * key in {@link #preKeysMap}. Returns {@link Optional#empty()} for an
     * unknown id or a {@code null} argument so callers can fail soft when
     * the peer references a key that has already been rotated out.
     *
     * @param id the pre-key id, or {@code null}
     * @return the matching pre-key pair, or {@link Optional#empty()} when no entry matches
     */
    public Optional<SignalPreKeyPair> findPreKeyById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(preKeysMap.get(id));
    }

    @Override
    public void addPreKey(SignalPreKeyPair preKey) {
        Objects.requireNonNull(preKey, "preKey cannot be null");
        preKeysMap.put(preKey.id(), preKey);
    }

    @Override
    public boolean removePreKey(int id) {
        return preKeysMap.remove(id) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation stores a single {@link #signedKeyPair} and matches
     * solely against its {@link SignalSignedKeyPair#id()}; any other id
     * yields {@link Optional#empty()}. Rotation is performed by replacing the
     * field via the dedicated setter, not by accumulating signed pre-keys.
     */
    @Override
    public Optional<SignalSignedKeyPair> findSignedPreKeyById(Integer id) {
        return id == signedKeyPair.id() ? Optional.of(signedKeyPair) : Optional.empty();
    }

    /**
     * Always throws {@link UnsupportedOperationException}.
     *
     * @apiNote
     * The signed pre-key is a singleton in this store; new signed pre-keys
     * are installed by replacing {@link #signedKeyPair} via its setter, not
     * by appending. The {@link com.github.auties00.libsignal.SignalProtocolStore}
     * contract that accepts arbitrary signed pre-keys is therefore not
     * supported here, and any call is a programmer error.
     *
     * @param signalSignedKeyPair ignored
     * @throws UnsupportedOperationException always
     */
    @Override
    public void addSignedPreKey(SignalSignedKeyPair signalSignedKeyPair) {
        throw new UnsupportedOperationException("Cannot add signed pre keys to a Keys instance");
    }

    @Override
    public Optional<SignalSessionRecord> findSessionByAddress(SignalProtocolAddress address) {
        return Optional.ofNullable(sessionsMap.get(address));
    }

    @Override
    public void addSession(SignalProtocolAddress address, SignalSessionRecord record) {
        sessionsMap.put(address, record);
    }

    @Override
    public Optional<SignalSenderKeyRecord> findSenderKeyByName(SignalSenderKeyName name) {
        return Optional.ofNullable(senderKeysMap.get(name));
    }

    @Override
    public void addSenderKey(SignalSenderKeyName name, SignalSenderKeyRecord newRecord) {
        senderKeysMap.put(name, newRecord);
    }

    @Override
    public boolean removeSession(SignalProtocolAddress address) {
        return sessionsMap.remove(address) != null;
    }

    @Override
    public void removeSenderKeys(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        senderKeysMap.keySet().removeIf(name ->
                name.sender().equals(signalAddress)
        );
    }

    @Override
    public void removeSenderKeys(SignalSenderKeyName senderKeyName) {
        Objects.requireNonNull(senderKeyName, "senderKeyName cannot be null");
        senderKeysMap.remove(senderKeyName);
    }

    @Override
    public void cleanupSignalSessions(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        removeSession(signalAddress);
        removeSenderKeys(deviceJid);
    }

    /**
     * Composes the {@link #baseKeysMap} map key from a Signal address and the
     * upstream message id.
     *
     * @apiNote
     * Centralises the key shape so that {@link #saveSessionBaseKey},
     * {@link #findSessionBaseKey}, {@link #hasSameBaseKey}, and
     * {@link #removeSessionBaseKey} always hash on the same string. The
     * pipe character is used as a separator because neither
     * {@link SignalProtocolAddress#toString()} nor a Signal message id can
     * contain it, which keeps the encoding unambiguous and reversible.
     *
     * @param address the Signal protocol address that owns the base key
     * @param originalMsgId the upstream message id that introduced the base key
     * @return the composite map key
     */
    private static String encodeBaseKeyKey(SignalProtocolAddress address, String originalMsgId) {
        return address.toString() + "|" + originalMsgId;
    }

    @Override
    public void saveSessionBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] baseKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(baseKey, "baseKey cannot be null");
        baseKeysMap.put(encodeBaseKeyKey(address, originalMsgId), baseKey);
    }

    @Override
    public Optional<byte[]> findSessionBaseKey(SignalProtocolAddress address, String originalMsgId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        return Optional.ofNullable(baseKeysMap.get(encodeBaseKeyKey(address, originalMsgId)));
    }

    @Override
    public boolean hasSameBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] candidate) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");
        var stored = baseKeysMap.get(encodeBaseKeyKey(address, originalMsgId));
        return stored != null && Arrays.equals(stored, candidate);
    }

    @Override
    public boolean removeSessionBaseKey(SignalProtocolAddress address, String originalMsgId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        return baseKeysMap.remove(encodeBaseKeyKey(address, originalMsgId)) != null;
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
        return List.copyOf(appStateKeysMap.sequencedValues());
    }

    @Override
    public Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id) {
        return Optional.ofNullable(appStateKeysMap.get(HexFormat.of().formatHex(id)));
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
                    .ifPresent(keyId -> appStateKeysMap.put(HexFormat.of().formatHex(keyId), key));
        }
    }

    @Override
    public void expireAppStateKeys(Instant threshold) {
        for (var entry : appStateKeysMap.entrySet()) {
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
        for (var key : appStateKeysMap.values()) {
            if (SyncKeyUtils.getKeyEpoch(key) != epoch) {
                continue;
            }

            key.keyData().ifPresent(data -> data.setTimestamp(Instant.EPOCH));
        }
    }

    @Override
    public Optional<SyncHashValue> findWebAppHashStateByName(SyncPatchType patchType) {
        return Optional.ofNullable(hashStatesMap.get(patchType));
    }

    @Override
    public void addWebAppHashState(SyncHashValue state) {
        hashStatesMap.put(state.type(), state);
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
        return List.copyOf(inner.values());
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
        return syncActionEntries.values()
                .stream()
                .flatMap(inner -> inner.values().stream())
                .toList();
    }

    @Override
    public Collection<MissingDeviceSyncKey> missingSyncKeys() {
        return List.copyOf(missingSyncKeysMap.values());
    }

    @Override
    public Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId) {
        return Optional.ofNullable(missingSyncKeysMap.get(HexFormat.of().formatHex(keyId)));
    }

    @Override
    public int missingSyncKeyCount() {
        return missingSyncKeysMap.size();
    }

    @Override
    public void addMissingSyncKey(MissingDeviceSyncKey missingKey) {
        missingSyncKeysMap.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
    }

    @Override
    public void addMissingSyncKeys(Collection<MissingDeviceSyncKey> missingKeys) {
        Objects.requireNonNull(missingKeys, "missingKeys cannot be null");
        for (var missingKey : missingKeys) {
            this.missingSyncKeysMap.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
        }
    }

    @Override
    public void removeMissingSyncKey(byte[] keyId) {
        missingSyncKeysMap.remove(HexFormat.of().formatHex(keyId));
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
        return collectionPending == null ? List.of() : List.copyOf(collectionPending);
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
        orphanMutationEntriesMap.computeIfAbsent(collectionName, _ -> new OrphanMutationEntries())
                .add(mutation);
    }

    @Override
    public List<OrphanMutationEntry> findOrphanMutations(SyncPatchType collectionName) {
        var entries = orphanMutationEntriesMap.get(collectionName);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(entries.entries());
    }

    @Override
    public List<OrphanMutationEntry> findOrphanMutationsByModel(SyncPatchType collectionName, String modelId) {
        var entries = orphanMutationEntriesMap.get(collectionName);
        if (entries == null || entries.isEmpty() || modelId == null) {
            return List.of();
        }
        return entries.entries()
                .stream()
                .filter(e -> modelId.equals(e.modelId()))
                .toList();
    }

    @Override
    public void removeOrphanMutations(SyncPatchType collectionName) {
        orphanMutationEntriesMap.remove(collectionName);
    }

    @Override
    public void removeOrphanMutations(SyncPatchType collectionName, Collection<OrphanMutationEntry> entries) {
        var data = orphanMutationEntriesMap.get(collectionName);
        if (data != null) {
            data.removeAll(entries);
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
        remoteIdentitiesMap.put(address, identityKey);
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
                return Optional.ofNullable(remoteIdentitiesMap.get(address));
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
        hashStatesMap.put(collectionName, hashState);
    }

    @Override
    public Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type) {
        return type == null
                ? Optional.empty()
                : Optional.ofNullable(privacySettingsMap.get(type));
    }

    @Override
    public void addPrivacySetting(PrivacySettingEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        privacySettingsMap.put(entry.type(), entry);
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
        return Optional.ofNullable(recentStickersMap.get(stickerHash));
    }

    @Override
    public void addRecentSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        recentStickersMap.put(stickerHash, sticker);
    }

    @Override
    public Optional<Sticker> removeRecentSticker(String stickerHash) {
        return Optional.ofNullable(recentStickersMap.remove(stickerHash));
    }

    @Override
    public SequencedCollection<Sticker> recentStickers() {
        return List.copyOf(recentStickersMap.values());
    }

    @Override
    public Optional<Sticker> findFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickersMap.get(stickerHash));
    }

    @Override
    public void addFavouriteSticker(String stickerHash, Sticker sticker) {
        Objects.requireNonNull(stickerHash, "stickerHash cannot be null");
        Objects.requireNonNull(sticker, "sticker cannot be null");
        favouriteStickersMap.put(stickerHash, sticker);
    }

    @Override
    public Optional<Sticker> removeFavouriteSticker(String stickerHash) {
        return Optional.ofNullable(favouriteStickersMap.remove(stickerHash));
    }

    @Override
    public SequencedCollection<Sticker> favouriteStickers() {
        return List.copyOf(favouriteStickersMap.values());
    }

    @Override
    public void addLabel(Label label) {
        Objects.requireNonNull(label, "label cannot be null");
        labelsMap.put(label.id(), label);
    }

    @Override
    public Collection<Label> labels() {
        return List.copyOf(labelsMap.values());
    }

    @Override
    public Optional<Label> removeLabel(String labelId) {
        return Optional.ofNullable(labelsMap.remove(labelId));
    }

    @Override
    public Optional<Label> findLabel(String labelId) {
        return Optional.ofNullable(labelsMap.get(labelId));
    }

    @Override
    public Optional<QuickReply> findQuickReply(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(quickRepliesMap.get(id));
    }

    @Override
    public void addQuickReply(QuickReply quickReply) {
        Objects.requireNonNull(quickReply, "quickReply cannot be null");
        quickRepliesMap.put(quickReply.id(), quickReply);
    }

    @Override
    public Optional<QuickReply> removeQuickReply(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(quickRepliesMap.remove(id));
    }

    @Override
    public List<QuickReply> quickReplies() {
        return List.copyOf(quickRepliesMap.values());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation lazily resolves the version from
     * {@link WhatsAppClientInfo} under a double-checked lock on
     * {@link #clientVersionLock} so the version probe runs at most once per
     * store, and only when actually required, even though the field is
     * observed by many call sites.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation only applies the local-only sync fields exposed by
     * {@link GroupMetadataEdit}; subject, description, picture, and the
     * server-controlled settings toggles are authoritative on the server and
     * round-trip through a dedicated notification, so applying them here
     * would risk overwriting fresher state. Returns {@link Optional#empty()}
     * when no group is cached for {@code groupJid}.
     */
    @Override
    public Optional<GroupMetadata> applyGroupMetadataEdit(Jid groupJid, GroupMetadataEdit edit) {
        Objects.requireNonNull(groupJid, "groupJid cannot be null");
        Objects.requireNonNull(edit, "edit cannot be null");
        var existing = chatMetadata.get(groupJid);
        if (!(existing instanceof GroupMetadata group)) {
            return Optional.empty();
        }
        edit.statusMuted().ifPresent(group::setStatusMuted);
        return Optional.of(group);
    }

    @Override
    public Optional<BusinessVerifiedName> findVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        return Optional.ofNullable(verifiedBusinessNamesMap.get(jid.toUserJid()));
    }

    @Override
    public void addVerifiedBusinessName(Jid jid, BusinessVerifiedName record) {
        Objects.requireNonNull(jid, "jid cannot be null");
        Objects.requireNonNull(record, "record cannot be null");
        verifiedBusinessNamesMap.put(jid.toUserJid(), record);
    }

    @Override
    public void removeVerifiedBusinessName(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        verifiedBusinessNamesMap.remove(jid.toUserJid());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation falls back to the paired phone/LID identity when
     * the direct lookup misses, mirroring the dual-key fallback in
     * {@link #findContactByJid}, because the device-list cache may have been
     * populated under either identity for the same user. Only phone-server
     * and LID-server JIDs trigger the alternate lookup; other JID shapes
     * return {@link Optional#empty()} immediately on a direct miss.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation enforces an LRU-style cap of {@link #MAX_DEVICE_LISTS}
     * entries: when the cache is full and the {@code userJid} is not already
     * present, the eldest entry is evicted via
     * {@link ConcurrentLinkedHashMap#pollLastEntry()} before the new mapping
     * is inserted. Repeated puts for an existing JID overwrite without
     * touching the cap.
     */
    @Override
    public void addDeviceList(DeviceList deviceList) {
        Objects.requireNonNull(deviceList, "deviceList cannot be null");

        var userJid = deviceList.userJid();

        if (deviceLists.size() >= MAX_DEVICE_LISTS && !deviceLists.containsKey(userJid)) {
            deviceLists.pollLastEntry();
        }

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
        return Set.copyOf(unconfirmedIdentityChanges);
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
        return Set.copyOf(blockedContacts);
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
    public void setBlockedContacts(Collection<Jid> contactsMap) {
        blockedContacts.clear();
        if (contactsMap != null) {
            for (var contact : contactsMap) {
                if (contact != null) {
                    blockedContacts.add(contact.toUserJid());
                }
            }
        }
    }

    @Override
    public Optional<String> blocklistHash() {
        return Optional.ofNullable(blocklistHash);
    }

    @Override
    public WhatsAppStore setBlocklistHash(String blocklistHash) {
        this.blocklistHash = blocklistHash;
        return this;
    }

    @Override
    public boolean blocklistMigrated() {
        return blocklistMigrated;
    }

    @Override
    public WhatsAppStore setBlocklistMigrated(boolean blocklistMigrated) {
        this.blocklistMigrated = blocklistMigrated;
        return this;
    }

    @Override
    public boolean receivedBlocklistMigrationBefore1x1Migration() {
        return receivedBlocklistMigrationBefore1x1Migration;
    }

    @Override
    public WhatsAppStore setReceivedBlocklistMigrationBefore1x1Migration(boolean value) {
        this.receivedBlocklistMigrationBefore1x1Migration = value;
        return this;
    }

    @Override
    public Optional<String> optOutListHash(String category) {
        return Optional.ofNullable(optOutListHashes.get(Objects.requireNonNull(category, "category cannot be null")));
    }

    @Override
    public List<OptOutEntry> optOutListEntries(String category) {
        var entries = optOutListEntries.get(Objects.requireNonNull(category, "category cannot be null"));
        return entries == null ? List.of() : entries;
    }

    @Override
    public WhatsAppStore setOptOutList(String category, String hash, List<OptOutEntry> entries) {
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(entries, "entries cannot be null");
        if (hash == null) {
            optOutListHashes.remove(category);
        } else {
            optOutListHashes.put(category, hash);
        }
        optOutListEntries.put(category, List.copyOf(entries));
        return this;
    }

    @Override
    public Optional<String> contactBlacklistHash(String category) {
        return Optional.ofNullable(contactBlacklistHashes.get(Objects.requireNonNull(category, "category cannot be null")));
    }

    @Override
    public List<Jid> contactBlacklistEntries(String category) {
        var entries = contactBlacklistEntries.get(Objects.requireNonNull(category, "category cannot be null"));
        return entries == null ? List.of() : entries;
    }

    @Override
    public WhatsAppStore setContactBlacklist(String category, String hash, List<Jid> entries) {
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(entries, "entries cannot be null");
        if (hash == null) {
            contactBlacklistHashes.remove(category);
        } else {
            contactBlacklistHashes.put(category, hash);
        }
        contactBlacklistEntries.put(category, List.copyOf(entries));
        return this;
    }

    @Override
    public Optional<StatusPrivacySetting> statusPrivacy() {
        return Optional.ofNullable(statusPrivacy);
    }

    @Override
    public WhatsAppStore setStatusPrivacy(StatusPrivacySetting statusPrivacy) {
        this.statusPrivacy = statusPrivacy;
        return this;
    }

    @Override
    public Optional<AccountDisappearingMode> disappearingMode() {
        return Optional.ofNullable(disappearingMode);
    }

    @Override
    public WhatsAppStore setDisappearingMode(AccountDisappearingMode disappearingMode) {
        this.disappearingMode = disappearingMode;
        return this;
    }

    @Override
    public List<Jid> linkedDevices() {
        var current = linkedDevices;
        return current == null ? List.of() : List.copyOf(current);
    }

    @Override
    public WhatsAppStore setLinkedDevices(Collection<Jid> linkedDevices) {
        this.linkedDevices = linkedDevices == null ? null : List.copyOf(linkedDevices);
        return this;
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
        return List.copyOf(favoriteChats);
    }

    @Override
    public WhatsAppStore setFavoriteChats(List<Jid> favoriteChats) {
        this.favoriteChats = new ArrayList<>(Objects.requireNonNull(favoriteChats, "favoriteChats cannot be null"));
        return this;
    }

    @Override
    public List<String> primaryFeatures() {
        return List.copyOf(primaryFeatures);
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
        return List.copyOf(agentStates.values());
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
        return List.copyOf(chatAssignments.values());
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
        return List.copyOf(customPaymentMethods);
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
        return List.copyOf(marketingMessages.values());
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
        return List.copyOf(marketingMessageBroadcasts.values());
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
        return List.copyOf(businessBroadcastLists.values());
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
        return List.copyOf(businessBroadcastCampaigns.values());
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
        return List.copyOf(businessBroadcastInsights.values());
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
    public Optional<String> companionMmsAuthNonce() {
        return Optional.ofNullable(companionMmsAuthNonce);
    }

    @Override
    public WhatsAppStore setCompanionMmsAuthNonce(String nonce) {
        this.companionMmsAuthNonce = nonce;
        return this;
    }

    @Override
    public Optional<byte[]> shareableChatLinkKey() {
        return Optional.ofNullable(shareableChatLinkKey);
    }

    @Override
    public WhatsAppStore setShareableChatLinkKey(byte[] key) {
        this.shareableChatLinkKey = key;
        return this;
    }

    @Override
    public boolean startAtLogin() {
        return startAtLogin != null && startAtLogin;
    }

    @Override
    public WhatsAppStore setStartAtLogin(boolean startAtLogin) {
        this.startAtLogin = startAtLogin;
        return this;
    }

    @Override
    public boolean minimizeToTray() {
        return minimizeToTray != null && minimizeToTray;
    }

    @Override
    public WhatsAppStore setMinimizeToTray(boolean minimizeToTray) {
        this.minimizeToTray = minimizeToTray;
        return this;
    }

    @Override
    public boolean replaceTextWithEmoji() {
        return replaceTextWithEmoji != null && replaceTextWithEmoji;
    }

    @Override
    public WhatsAppStore setReplaceTextWithEmoji(boolean replaceTextWithEmoji) {
        this.replaceTextWithEmoji = replaceTextWithEmoji;
        return this;
    }

    @Override
    public Optional<SettingsSyncAction.DisplayMode> bannerNotificationDisplayMode() {
        return Optional.ofNullable(bannerNotificationDisplayMode);
    }

    @Override
    public WhatsAppStore setBannerNotificationDisplayMode(SettingsSyncAction.DisplayMode mode) {
        this.bannerNotificationDisplayMode = mode;
        return this;
    }

    @Override
    public Optional<SettingsSyncAction.DisplayMode> unreadCounterBadgeDisplayMode() {
        return Optional.ofNullable(unreadCounterBadgeDisplayMode);
    }

    @Override
    public WhatsAppStore setUnreadCounterBadgeDisplayMode(SettingsSyncAction.DisplayMode mode) {
        this.unreadCounterBadgeDisplayMode = mode;
        return this;
    }

    @Override
    public boolean messagesNotificationEnabled() {
        return messagesNotificationEnabled != null && messagesNotificationEnabled;
    }

    @Override
    public WhatsAppStore setMessagesNotificationEnabled(boolean enabled) {
        this.messagesNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean callsNotificationEnabled() {
        return callsNotificationEnabled != null && callsNotificationEnabled;
    }

    @Override
    public WhatsAppStore setCallsNotificationEnabled(boolean enabled) {
        this.callsNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean reactionsNotificationEnabled() {
        return reactionsNotificationEnabled != null && reactionsNotificationEnabled;
    }

    @Override
    public WhatsAppStore setReactionsNotificationEnabled(boolean enabled) {
        this.reactionsNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean statusReactionsNotificationEnabled() {
        return statusReactionsNotificationEnabled != null && statusReactionsNotificationEnabled;
    }

    @Override
    public WhatsAppStore setStatusReactionsNotificationEnabled(boolean enabled) {
        this.statusReactionsNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean textPreviewForNotificationEnabled() {
        return textPreviewForNotificationEnabled != null && textPreviewForNotificationEnabled;
    }

    @Override
    public WhatsAppStore setTextPreviewForNotificationEnabled(boolean enabled) {
        this.textPreviewForNotificationEnabled = enabled;
        return this;
    }

    @Override
    public OptionalInt defaultNotificationToneId() {
        return defaultNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(defaultNotificationToneId);
    }

    @Override
    public WhatsAppStore setDefaultNotificationToneId(Integer toneId) {
        this.defaultNotificationToneId = toneId;
        return this;
    }

    @Override
    public OptionalInt groupDefaultNotificationToneId() {
        return groupDefaultNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(groupDefaultNotificationToneId);
    }

    @Override
    public WhatsAppStore setGroupDefaultNotificationToneId(Integer toneId) {
        this.groupDefaultNotificationToneId = toneId;
        return this;
    }

    @Override
    public Optional<AppTheme> appTheme() {
        return Optional.ofNullable(appTheme);
    }

    @Override
    public WhatsAppStore setAppTheme(AppTheme appTheme) {
        this.appTheme = appTheme;
        return this;
    }

    @Override
    public OptionalInt wallpaperId() {
        return wallpaperId == null ? OptionalInt.empty() : OptionalInt.of(wallpaperId);
    }

    @Override
    public WhatsAppStore setWallpaperId(Integer wallpaperId) {
        this.wallpaperId = wallpaperId;
        return this;
    }

    @Override
    public boolean doodleWallpaperEnabled() {
        return doodleWallpaperEnabled != null && doodleWallpaperEnabled;
    }

    @Override
    public WhatsAppStore setDoodleWallpaperEnabled(boolean enabled) {
        this.doodleWallpaperEnabled = enabled;
        return this;
    }

    @Override
    public OptionalInt fontSize() {
        return fontSize == null ? OptionalInt.empty() : OptionalInt.of(fontSize);
    }

    @Override
    public WhatsAppStore setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
        return this;
    }

    @Override
    public boolean photosAutodownloadEnabled() {
        return photosAutodownloadEnabled != null && photosAutodownloadEnabled;
    }

    @Override
    public WhatsAppStore setPhotosAutodownloadEnabled(boolean enabled) {
        this.photosAutodownloadEnabled = enabled;
        return this;
    }

    @Override
    public boolean audiosAutodownloadEnabled() {
        return audiosAutodownloadEnabled != null && audiosAutodownloadEnabled;
    }

    @Override
    public WhatsAppStore setAudiosAutodownloadEnabled(boolean enabled) {
        this.audiosAutodownloadEnabled = enabled;
        return this;
    }

    @Override
    public boolean videosAutodownloadEnabled() {
        return videosAutodownloadEnabled != null && videosAutodownloadEnabled;
    }

    @Override
    public WhatsAppStore setVideosAutodownloadEnabled(boolean enabled) {
        this.videosAutodownloadEnabled = enabled;
        return this;
    }

    @Override
    public boolean documentsAutodownloadEnabled() {
        return documentsAutodownloadEnabled != null && documentsAutodownloadEnabled;
    }

    @Override
    public WhatsAppStore setDocumentsAutodownloadEnabled(boolean enabled) {
        this.documentsAutodownloadEnabled = enabled;
        return this;
    }

    @Override
    public OptionalInt notificationToneId() {
        return notificationToneId == null ? OptionalInt.empty() : OptionalInt.of(notificationToneId);
    }

    @Override
    public WhatsAppStore setNotificationToneId(Integer toneId) {
        this.notificationToneId = toneId;
        return this;
    }

    @Override
    public Optional<SettingsSyncAction.MediaQualitySetting> mediaUploadQuality() {
        return Optional.ofNullable(mediaUploadQuality);
    }

    @Override
    public WhatsAppStore setMediaUploadQuality(SettingsSyncAction.MediaQualitySetting quality) {
        this.mediaUploadQuality = quality;
        return this;
    }

    @Override
    public boolean spellCheckEnabled() {
        return spellCheckEnabled != null && spellCheckEnabled;
    }

    @Override
    public WhatsAppStore setSpellCheckEnabled(boolean enabled) {
        this.spellCheckEnabled = enabled;
        return this;
    }

    @Override
    public boolean enterToSendEnabled() {
        return enterToSendEnabled != null && enterToSendEnabled;
    }

    @Override
    public WhatsAppStore setEnterToSendEnabled(boolean enabled) {
        this.enterToSendEnabled = enabled;
        return this;
    }

    @Override
    public boolean groupMessageNotificationEnabled() {
        return groupMessageNotificationEnabled != null && groupMessageNotificationEnabled;
    }

    @Override
    public WhatsAppStore setGroupMessageNotificationEnabled(boolean enabled) {
        this.groupMessageNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean groupReactionsNotificationEnabled() {
        return groupReactionsNotificationEnabled != null && groupReactionsNotificationEnabled;
    }

    @Override
    public WhatsAppStore setGroupReactionsNotificationEnabled(boolean enabled) {
        this.groupReactionsNotificationEnabled = enabled;
        return this;
    }

    @Override
    public boolean statusNotificationEnabled() {
        return statusNotificationEnabled != null && statusNotificationEnabled;
    }

    @Override
    public WhatsAppStore setStatusNotificationEnabled(boolean enabled) {
        this.statusNotificationEnabled = enabled;
        return this;
    }

    @Override
    public OptionalInt statusNotificationToneId() {
        return statusNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(statusNotificationToneId);
    }

    @Override
    public WhatsAppStore setStatusNotificationToneId(Integer toneId) {
        this.statusNotificationToneId = toneId;
        return this;
    }

    @Override
    public boolean playSoundForCallNotification() {
        return playSoundForCallNotification != null && playSoundForCallNotification;
    }

    @Override
    public WhatsAppStore setPlaySoundForCallNotification(boolean enabled) {
        this.playSoundForCallNotification = enabled;
        return this;
    }

    @Override
    public Collection<OnboardingHintState> onboardingHintStates() {
        return List.copyOf(onboardingHintStates.values());
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
        return List.copyOf(deviceCapabilitiesStates.values());
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
        return List.copyOf(interactiveMessageStates.values());
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
        return List.copyOf(noteStates.values());
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
        return List.copyOf(newsletterPinStates.values());
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
        return List.copyOf(callLogStates.values());
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
        return List.copyOf(botWelcomeRequestStates.values());
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
        return List.copyOf(aiThreadTitles.values());
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation enumerates the session directory, retains every
     * file whose name matches {@code WAM_BUFFER_PREFIX + key + WAM_BUFFER_SUFFIX},
     * and strips the prefix and suffix to recover the bare save key.
     * Returns an empty list when {@link #directory} is unset, when the
     * session directory cannot be resolved, or when listing the directory
     * fails; both failure modes log at {@link System.Logger.Level#WARNING}
     * rather than propagating because the WAM pipeline is best-effort.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation writes to a sibling temp file and atomically
     * renames it over the target on close via {@link AtomicMoveOutputStream},
     * so a crash mid-write never leaves a partially-flushed WAM buffer
     * visible. When {@link #directory} is unset the call returns
     * {@link OutputStream#nullOutputStream()} so a session running without
     * disk persistence can still go through the same code path without
     * special-casing.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when persistence
     * is disabled ({@link #directory} is {@code null}) or when no buffer
     * file exists for the supplied key, so callers can use the {@code Optional}
     * shape to skip the upload without distinguishing the two cases.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to
     * {@link Files#deleteIfExists(Path)} so a concurrent caller that already
     * removed the buffer reports as {@code false} rather than throwing.
     * Returns {@code false} immediately when {@link #directory} is unset.
     */
    @Override
    public boolean removeWamPendingBuffer(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return false;
        }
        return Files.deleteIfExists(wamBufferPath(saveKey));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the session directory once and deletes
     * every file whose name carries the WAM buffer prefix/suffix pair,
     * matching the inverse of the enumeration in
     * {@link #wamPendingBufferKeys()}. When {@link #directory} is unset or
     * the session directory does not exist the call is a no-op so a session
     * that never persisted any buffer can still call this safely.
     */
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
        var stored = wamSequenceNumbersMap.get(channel.id());
        return stored == null ? OptionalInt.empty() : OptionalInt.of(stored);
    }

    @Override
    public WhatsAppStore putWamSequenceNumber(WamChannel channel, int sequenceNumber) {
        Objects.requireNonNull(channel, "channel cannot be null");
        wamSequenceNumbersMap.put(channel.id(), sequenceNumber);
        return this;
    }

    /**
     * Resolves the on-disk path of the WAM buffer file for {@code saveKey}.
     *
     * @apiNote
     * Centralises the file-name shape ({@code WAM_BUFFER_PREFIX + key + WAM_BUFFER_SUFFIX})
     * so the reader, writer, deleter, and enumerator all agree on where the
     * buffer lives. Callers must validate the key via {@link #validateSaveKey}
     * before invoking this helper because the path is composed by direct
     * string concatenation without further sanitisation.
     *
     * @param saveKey the bare save key, already validated
     * @return the path of the file that backs the buffer
     * @throws IOException if the session directory cannot be resolved or created
     */
    private Path wamBufferPath(String saveKey) throws IOException {
        return StorePathUtils.getSessionFile(
                clientType, directory, uuid.toString(),
                WAM_BUFFER_PREFIX + saveKey + WAM_BUFFER_SUFFIX);
    }

    /**
     * Rejects any save key that could resolve outside the session directory.
     *
     * @apiNote
     * Defends every WAM buffer file operation against path traversal by
     * forbidding empty keys, slashes, backslashes, embedded NUL characters,
     * and a leading dot. The key is concatenated verbatim into the file
     * name by {@link #wamBufferPath}, so without this guard a malicious or
     * mis-typed key could read or overwrite an arbitrary file in the
     * session directory.
     *
     * @param saveKey the bare save key
     * @throws IllegalArgumentException if {@code saveKey} is empty or contains a forbidden character
     */
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
     * Buffers writes to a sibling temp file and, on {@link #close()},
     * atomically renames it over the target path.
     *
     * @apiNote
     * Used by {@link #openWamPendingBufferWriter(String)} so that a crash
     * mid-write never leaves a partially-flushed WAM buffer visible to a
     * subsequent {@link #openWamPendingBufferReader(String)} call. Callers
     * must {@code close()} the stream for the rename to occur; the rename
     * is the commit point, and a closed-without-write stream still produces
     * an empty target file.
     *
     * @implNote
     * This implementation extends {@link FilterOutputStream} and overrides
     * {@code write(byte[], int, int)} to skip the per-byte loop the default
     * implementation performs, which would call {@link #write(int)} once
     * per byte and dominate the cost of writing large buffers.
     */
    private static final class AtomicMoveOutputStream extends FilterOutputStream {
        /**
         * The temporary sibling file that receives every write.
         */
        private final Path tempFile;

        /**
         * The destination path that the temp file is renamed over on close.
         */
        private final Path targetFile;

        /**
         * Guards against a double-close that would attempt to rename a file
         * that has already been moved.
         */
        private boolean closed;

        /**
         * Wraps the supplied delegate stream with the atomic-move
         * close-time semantics.
         *
         * @param delegate the {@link OutputStream} that writes to {@code tempFile}
         * @param tempFile the sibling temp file that receives every write
         * @param targetFile the destination path renamed over on close
         */
        AtomicMoveOutputStream(OutputStream delegate, Path tempFile, Path targetFile) {
            super(delegate);
            this.tempFile = tempFile;
            this.targetFile = targetFile;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation forwards the range write directly to the
         * delegate stream instead of going through the default per-byte
         * loop {@link FilterOutputStream} inherits from
         * {@link OutputStream}, which would multiply WAM buffer flush cost
         * by the buffer size.
         */
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation flushes and closes the delegate, then renames
         * the temp file over the target with both
         * {@link StandardCopyOption#REPLACE_EXISTING} and
         * {@link StandardCopyOption#ATOMIC_MOVE}; the rename is the
         * publication point that makes the new buffer visible. On any
         * {@link IOException} during close or rename the temp file is
         * removed so a failed write never accumulates orphan temp files.
         * A second close is a no-op so callers can use the stream inside
         * a try-with-resources alongside an explicit {@code close}.
         */
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
        var iterator = recentStickersMap.entrySet().iterator();
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
        return Optional.ofNullable(mentionEveryoneMuteExpirationsMap.get(chatJid));
    }

    @Override
    public void setMentionEveryoneMuteExpiration(Jid chatJid, ChatMute mute) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        Objects.requireNonNull(mute, "mute cannot be null");
        mentionEveryoneMuteExpirationsMap.put(chatJid, mute);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof AbstractWhatsAppStore that
                            && initializationTimeStamp == that.initializationTimeStamp
                            && online == that.online
                            && unarchiveChats == that.unarchiveChats
                            && twentyFourHourFormat == that.twentyFourHourFormat
                            && checkPatchMacs == that.checkPatchMacs
                            && syncedChats == that.syncedChats
                            && syncedContacts == that.syncedContacts
                            && syncedNewsletters == that.syncedNewsletters
                            && syncedStatus == that.syncedStatus
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
                            && Objects.equals(businessWebsites, that.businessWebsites)
                            && Objects.equals(businessEmail, that.businessEmail)
                            && Objects.equals(businessCategories, that.businessCategories)
                            && Objects.equals(contactsMap, that.contactsMap)
                            && Objects.equals(calls, that.calls)
                            && Objects.equals(privacySettingsMap, that.privacySettingsMap)
                            && newChatsEphemeralTimer == that.newChatsEphemeralTimer
                            && Objects.equals(webHistoryPolicy, that.webHistoryPolicy)
                            && Objects.equals(registrationId, that.registrationId)
                            && Objects.equals(noiseKeyPair, that.noiseKeyPair)
                            && Objects.equals(identityKeyPair, that.identityKeyPair)
                            && Objects.equals(signedDeviceIdentity, that.signedDeviceIdentity)
                            && Objects.equals(signedKeyPair, that.signedKeyPair)
                            && Objects.equals(preKeysMap, that.preKeysMap)
                            && Objects.equals(fdid, that.fdid)
                            && Objects.deepEquals(deviceId, that.deviceId)
                            && Objects.equals(advertisingId, that.advertisingId)
                            && Objects.deepEquals(identityId, that.identityId)
                            && Objects.deepEquals(backupToken, that.backupToken)
                            && Objects.equals(senderKeysMap, that.senderKeysMap)
                            && Objects.equals(appStateKeysMap, that.appStateKeysMap)
                            && Objects.equals(sessionsMap, that.sessionsMap)
                            && Objects.equals(hashStatesMap, that.hashStatesMap)
                            && Objects.equals(recentStickersMap, that.recentStickersMap)
                            && Objects.equals(favouriteStickersMap, that.favouriteStickersMap)
                            && Objects.equals(quickRepliesMap, that.quickRepliesMap)
                            && Objects.equals(labelsMap, that.labelsMap)
                            && Objects.equals(clientVersion, that.clientVersion)
                            && Objects.equals(companionVersion, that.companionVersion)
                            && Objects.equals(lastAdvCheckTime, that.lastAdvCheckTime)
                            && Objects.equals(remoteIdentitiesMap, that.remoteIdentitiesMap)
                            && Objects.equals(identityEncryptionRange, that.identityEncryptionRange)
                            && encryptionSequence.get() == that.encryptionSequence.get()
                            && Objects.equals(missingSyncKeysMap, that.missingSyncKeysMap)
                            && Objects.deepEquals(advSecretKey, that.advSecretKey)
                            && Objects.equals(verifiedBusinessNamesMap, that.verifiedBusinessNamesMap)
                            && Objects.equals(proxy, that.proxy)
                            && Objects.equals(directory, that.directory)
                            && Objects.equals(listeners, that.listeners)
                            && Objects.equals(lidToPhoneMappings, that.lidToPhoneMappings)
                            && Objects.equals(phoneToLidMappings, that.phoneToLidMappings)
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
                            && Objects.equals(mentionEveryoneMuteExpirationsMap, that.mentionEveryoneMuteExpirationsMap)
                            && Objects.equals(baseKeysMap, that.baseKeysMap)
                            && Objects.equals(wamSequenceNumbersMap, that.wamSequenceNumbersMap)
                            && Objects.equals(companionMmsAuthNonce, that.companionMmsAuthNonce)
                            && Arrays.equals(shareableChatLinkKey, that.shareableChatLinkKey)
                            && Objects.equals(startAtLogin, that.startAtLogin)
                            && Objects.equals(minimizeToTray, that.minimizeToTray)
                            && Objects.equals(replaceTextWithEmoji, that.replaceTextWithEmoji)
                            && bannerNotificationDisplayMode == that.bannerNotificationDisplayMode
                            && unreadCounterBadgeDisplayMode == that.unreadCounterBadgeDisplayMode
                            && Objects.equals(messagesNotificationEnabled, that.messagesNotificationEnabled)
                            && Objects.equals(callsNotificationEnabled, that.callsNotificationEnabled)
                            && Objects.equals(reactionsNotificationEnabled, that.reactionsNotificationEnabled)
                            && Objects.equals(statusReactionsNotificationEnabled, that.statusReactionsNotificationEnabled)
                            && Objects.equals(textPreviewForNotificationEnabled, that.textPreviewForNotificationEnabled)
                            && Objects.equals(defaultNotificationToneId, that.defaultNotificationToneId)
                            && Objects.equals(groupDefaultNotificationToneId, that.groupDefaultNotificationToneId)
                            && appTheme == that.appTheme
                            && Objects.equals(wallpaperId, that.wallpaperId)
                            && Objects.equals(doodleWallpaperEnabled, that.doodleWallpaperEnabled)
                            && Objects.equals(fontSize, that.fontSize)
                            && Objects.equals(photosAutodownloadEnabled, that.photosAutodownloadEnabled)
                            && Objects.equals(audiosAutodownloadEnabled, that.audiosAutodownloadEnabled)
                            && Objects.equals(videosAutodownloadEnabled, that.videosAutodownloadEnabled)
                            && Objects.equals(documentsAutodownloadEnabled, that.documentsAutodownloadEnabled)
                            && Objects.equals(notificationToneId, that.notificationToneId)
                            && mediaUploadQuality == that.mediaUploadQuality
                            && Objects.equals(spellCheckEnabled, that.spellCheckEnabled)
                            && Objects.equals(enterToSendEnabled, that.enterToSendEnabled)
                            && Objects.equals(groupMessageNotificationEnabled, that.groupMessageNotificationEnabled)
                            && Objects.equals(groupReactionsNotificationEnabled, that.groupReactionsNotificationEnabled)
                            && Objects.equals(statusNotificationEnabled, that.statusNotificationEnabled)
                            && Objects.equals(statusNotificationToneId, that.statusNotificationToneId)
                            && Objects.equals(playSoundForCallNotification, that.playSoundForCallNotification);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, phoneNumber, clientType, initializationTimeStamp,
                device, releaseChannel, online, locale, name, verifiedName,
                profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude,
                businessLatitude, businessDescription, businessWebsites, businessEmail,
                businessCategories, contactsMap, calls, privacySettingsMap,
                unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy,
                checkPatchMacs, syncedChats,
                syncedContacts, syncedNewsletters, syncedStatus, syncedBusinessCertificate,
                registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeysMap,
                fdid, Arrays.hashCode(deviceId), advertisingId, Arrays.hashCode(identityId), Arrays.hashCode(backupToken),
                senderKeysMap, appStateKeysMap, sessionsMap, hashStatesMap, registered, showSecurityNotifications, recentStickersMap,
                favouriteStickersMap, quickRepliesMap, labelsMap, clientVersion, companionVersion, lastAdvCheckTime,
                remoteIdentitiesMap, identityEncryptionRange, encryptionSequence, missingSyncKeysMap, Arrays.hashCode(advSecretKey),
                verifiedBusinessNamesMap, proxy, directory, listeners, lidToPhoneMappings, phoneToLidMappings,
                offlineResumeState, offlineDeliveryLatch, usersNeedingSenderKeyRotation,
                webAppStatePendingMutations, webAppStateCollections, pendingMessageRecipients, clientVersionLock, chatMetadata,
                deviceLists, unconfirmedIdentityChanges, interopHostedVerificationCache, utmReadChatIds, pendingDeviceSyncs, groupSenderKeyDistribution,
                disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures,
                mentionEveryoneMuteExpirationsMap, baseKeysMap, wamSequenceNumbersMap,
                companionMmsAuthNonce, Arrays.hashCode(shareableChatLinkKey),
                startAtLogin, minimizeToTray, replaceTextWithEmoji,
                bannerNotificationDisplayMode, unreadCounterBadgeDisplayMode,
                messagesNotificationEnabled, callsNotificationEnabled, reactionsNotificationEnabled,
                statusReactionsNotificationEnabled, textPreviewForNotificationEnabled,
                defaultNotificationToneId, groupDefaultNotificationToneId, appTheme, wallpaperId,
                doodleWallpaperEnabled, fontSize, photosAutodownloadEnabled, audiosAutodownloadEnabled,
                videosAutodownloadEnabled, documentsAutodownloadEnabled, notificationToneId,
                mediaUploadQuality, spellCheckEnabled, enterToSendEnabled,
                groupMessageNotificationEnabled, groupReactionsNotificationEnabled,
                statusNotificationEnabled, statusNotificationToneId, playSoundForCallNotification);
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the contextual chain
     * {@code message -> contextualContent -> contextInfo} and then resolves
     * the quoted message through {@link #findMessageById(JidProvider, String)}
     * keyed on the quoted-message id and parent JID. The parent JID falls
     * back to the enclosing message's {@code key.parentJid()} so quotes
     * that omit the explicit parent (the common case for one-on-one chats)
     * still resolve. Returns {@link Optional#empty()} when any link in the
     * chain is missing.
     */
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

}
