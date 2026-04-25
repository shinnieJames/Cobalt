package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
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
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyData;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.mixin.PathMixin;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.payment.OrphanPaymentNotification;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.bot.MaibaAIFeaturesControlAction;
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
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
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

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNullElseGet;

/**
 * Base implementation of {@link WhatsAppStore} that aggregates every
 * session field, setting, collection and Signal-protocol key into a single
 * flat protobuf message.
 *
 * <p>This class intentionally collapses WhatsApp Web's multi-database
 * architecture into a single in-memory aggregate:
 * <ul>
 *   <li>~12 IndexedDB databases carrying chats, contacts, messages, sync
 *       keys and sync state</li>
 *   <li>~100 IndexedDB tables that WA Web queries via its reactive
 *       Collection layer</li>
 *   <li>~45 in-memory reactive {@code Collection} models (contacts,
 *       chats, calls, newsletters, etc.)</li>
 *   <li>the key-value UserPrefs store used for AB props, encrypted rid,
 *       pairing metadata and so on</li>
 * </ul>
 * All of these are exposed as {@code ConcurrentHashMap} fields,
 * {@link com.github.auties00.collections.ConcurrentLinkedHashMap} caches,
 * or plain scalar fields on this class, so that a concrete subclass only
 * needs to decide how to serialise/load the aggregate.
 *
 * <p>The Signal protocol state (identity keys, pre-keys, signed pre-keys,
 * sessions, sender keys, app-state sync keys, noise keys) lives on this
 * class as well, so that instances can be used directly as a
 * {@link com.github.auties00.libsignal.SignalProtocolStore}.
 *
 * @implNote WAWebModelStorageInitialize: each WA Web IndexedDB schema
 * module ({@code WAWebSchema*}) maps to a corresponding field or map on
 * this class. The WA Web collection modules ({@code WAWebChatCollection},
 * {@code WAWebContactCollection}, {@code WAWebCallsCollection}, ...) are
 * represented by the {@link java.util.concurrent.ConcurrentHashMap} fields
 * and accessor methods. The Signal storage module
 * {@code WAWebSignalStorage} is absorbed through the {@code SignalProtocol}
 * fields. The UserPrefs module {@code WAWebUserPrefsBase} corresponds to
 * the scalar fields (registered, showSecurityNotifications,
 * primaryDeviceSupportsSyncdRecovery, etc.).
 *
 * <p>The three {@code WAWebModelStorageInitialize} lifecycle exports map
 * to Cobalt as follows:
 * <ul>
 *   <li>{@code initializeWithoutGKs}: the per-table {@code addTable()}
 *       fan-out plus {@code WAWebModelStorageUtils.createStorage(...).
 *       initialize()} is replaced by
 *       {@code WhatsAppStoreFactory.create(...)} /
 *       {@code WhatsAppStoreFactory.load(...)}. Each WA Web
 *       {@code WAWebSchema*.addTable()} call corresponds to one of the
 *       {@link java.util.concurrent.ConcurrentHashMap} fields declared on
 *       this class; the maps are unconditionally allocated, so there is
 *       no rollout / column-packing branch
 *       ({@code WAWebDbRolloutUtil.loadSchemaVersions} and
 *       {@code WAWebStorageGatingUtils.columnPackingEnabled} have no
 *       counterpart).</li>
 *   <li>{@code destroy}: implemented by
 *       {@link WhatsAppStore#delete()}, which removes the persistent
 *       backing directory. Both branches of the WA Web fall-back chain
 *       ({@code destroyStorage()} and {@code new Dexie(DATABASE_NAME).
 *       delete()}) collapse here; the {@code s = null} reset of the
 *       cached init promise has no analog because Cobalt does not
 *       memoize one.</li>
 *   <li>{@code clearInitializePromise}: no analog. WA Web uses it as a
 *       test-only reset of the module-level {@code s} cache; Cobalt's
 *       store factory is stateless and re-materializes the aggregate
 *       on every call.</li>
 * </ul>
 *
 * <p>The {@code WAWebSignalStorage} module is absorbed in the same way:
 * <ul>
 *   <li>{@code initialize}: replaced by the constructor of this class,
 *       which allocates the seven Signal-state maps unconditionally.
 *       The WA Web sequence
 *       ({@code WAWebSchemaBasekey.addTable()}, {@code WAWebSchemaIdentity.addTable()},
 *       {@code WAWebSchemaMeta.addTable()}, {@code WAWebSchemaPrekey.addTable()},
 *       {@code WAWebSchemaSenderkey.addTable()}, {@code WAWebSchemaSession.addTable()},
 *       {@code WAWebSchemaSignedPrekey.addTable()},
 *       {@code WAWebSignalStorageUtils.createStorage().initialize()})
 *       maps to the constructor's allocation of {@link #baseKeys},
 *       {@link #remoteIdentities} (plus {@link #registrationId},
 *       {@link #identityKeyPair}, {@link #noiseKeyPair},
 *       {@link #signedDeviceIdentity} for the meta keys), {@link #preKeys},
 *       {@link #senderKeys}, {@link #sessions} and {@link #signedKeyPair}
 *       respectively. The {@code WALogger} crash-and-rethrow branch in
 *       {@code WAWebSignalStorage.initialize} has no analog because
 *       allocation cannot fail.</li>
 *   <li>{@code destroy}: covered by the same {@link WhatsAppStore#delete()}
 *       call described above; the protobuf aggregate is the only
 *       persistent backing.</li>
 *   <li>{@code getBaseKeyTable}, {@code getIdentityTable},
 *       {@code getMetaTable}, {@code getPreKeyTable},
 *       {@code getSenderKeyTable}, {@code getSessionTable},
 *       {@code getSignedPreKeyTable}: replaced by the typed accessor
 *       methods on this class; the Dexie {@code Table} object is
 *       sidestepped because the in-memory {@code ConcurrentHashMap} is
 *       directly accessible.</li>
 * </ul>
 */
@WhatsAppWebModule(moduleName = "WAWebModelStorageInitialize")
@WhatsAppWebModule(moduleName = "WAWebCollections")
@WhatsAppWebModule(moduleName = "WAWebSignalStorage")
@WhatsAppWebModule(moduleName = "WAWebSignalConst")
@WhatsAppWebModule(moduleName = "WAWebUserPrefsBase")
@WhatsAppWebModule(moduleName = "WAWebGetSyncKey")
@WhatsAppWebModule(moduleName = "WAWebGetSyncAction")
@WhatsAppWebModule(moduleName = "WAWebGetCollectionVersion")
@WhatsAppWebModule(moduleName = "WAWebGetMissingKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdOrphan")
@WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
@WhatsAppWebModule(moduleName = "WAWebSyncdCollectionsStateMachine")
@WhatsAppWebModule(moduleName = "WAWebChatUtmCache")
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
abstract class AbstractWhatsAppStore implements WhatsAppStore {
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
    protected final ConcurrentHashMap<Jid, ContactTextStatus> contactTextStatuses;

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

    /**
     * The Signal protocol registration ID, persisted under the
     * {@code signal_reg_id} key of the {@code signal-meta-store} IndexedDB
     * table in WA Web.
     *
     * @implNote Mirrors {@code WAWebSignalConst.META_KEYS.REG_ID}, retrieved
     *           through {@code WAWebSignalStorage.getMetaTable()}; Cobalt
     *           promotes the persisted scalar to a typed field rather than
     *           encoding it as a key/value row, so {@code WAWebSchemaMeta}'s
     *           {@code signal-meta-store} is fanned out across discrete
     *           protobuf properties on this class.
     */
    @ProtobufProperty(index = 41, type = ProtobufType.INT32)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getMetaTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final Integer registrationId;

    /**
     * The Signal protocol Noise static key pair used to drive the Noise XX
     * handshake on every reconnect.
     *
     * @implNote Mirrors the {@code WAWebSignalConst.META_KEYS.STATIC_PUBKEY}
     *           and {@code STATIC_PRIVKEY} entries of the
     *           {@code signal-meta-store} IndexedDB table. Cobalt fuses the
     *           public/private halves into a single value object instead of
     *           tracking two separate key/value rows.
     */
    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getMetaTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final SignalIdentityKeyPair noiseKeyPair;

    /**
     * The Signal protocol long-term identity key pair used to derive
     * sessions, sender keys and to attest the companion device.
     *
     * @implNote Mirrors the local-identity counterpart of the
     *           {@code identity-store} IndexedDB table; the table itself
     *           ({@code WAWebSignalStorage.getIdentityTable()}) holds the
     *           remote identities in {@link #remoteIdentities}, while the
     *           local identity is kept here as a typed field.
     */
    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getIdentityTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final SignalIdentityKeyPair identityKeyPair;

    /**
     * The signed companion device identity blob retained after the
     * pair-success ADV exchange.
     *
     * @implNote Mirrors {@code WAWebSignalConst.META_KEYS.ADV_SIGNED_IDENTITY},
     *           one of the keys persisted via the {@code signal-meta-store}
     *           IndexedDB table.
     */
    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getMetaTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected ADVSignedDeviceIdentity signedDeviceIdentity;

    /**
     * The current signed pre-key, used as the medial step of the X3DH
     * session-initiation exchange.
     *
     * @implNote Mirrors the {@code signed-prekey-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getSignedPreKeyTable()}). WA Web
     *           keeps every previously-signed pre-key in the table, indexed
     *           by an auto-incrementing {@code keyId}; Cobalt only retains
     *           the latest entry because the rotation flow always discards
     *           the prior signed pre-key once the server has acknowledged
     *           the new one.
     */
    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSignedPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final SignalSignedKeyPair signedKeyPair;

    /**
     * The pool of one-time pre-keys backing the X3DH initiation flow,
     * indexed by their pre-key id.
     *
     * @implNote Mirrors the {@code prekey-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getPreKeyTable()}). Cobalt does
     *           not currently track the {@code isDirectDistribution} column
     *           used by {@code WAWebSignalStoreApi.markPreKeyAsDirectDistribution}
     *           because the retry-receipt flow that flips it is not yet
     *           implemented; downstream module validation owns that gap.
     */
    @ProtobufProperty(index = 48, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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

    /**
     * The per-(group, sender-device) sender-key records used by the SKDM
     * group-encryption flow.
     *
     * @implNote Mirrors the {@code senderkey-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getSenderKeyTable()}). Cobalt
     *           collapses the table's {@code senderKeyName} composite into a
     *           {@link SignalSenderKeyName} value object and folds the
     *           {@code senderId} indexed column into the same key, so the
     *           secondary index used by WA Web to bulk-delete a sender's
     *           keys is replaced by {@link #removeSenderKeys(Jid)} which
     *           filters in-memory.
     */
    @ProtobufProperty(index = 54, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSenderKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys;

    /**
     * The app state sync keys used to encrypt and decrypt app state
     * mutations propagated between the primary device and its
     * companions.
     *
     * <p>The map is keyed by the hex-encoded {@code keyId} bytes (via
     * {@link HexFormat#formatHex(byte[])}) and uses {@link LinkedHashMap}
     * so that the {@link SequencedCollection} returned by
     * {@link #appStateKeys()} preserves insertion order — equivalent to
     * the {@code WAWebSchemaSyncKeys} IndexedDB cursor over the
     * {@code sync-keys} table.
     *
     * @implNote Mirrors the {@code sync-keys} IndexedDB table
     *           ({@code WAWebSchemaSyncKeys.getSyncKeysTable()}). The
     *           table's user-defined primary key is {@code keyId}, with
     *           additional indexed columns ({@code timestamp},
     *           {@code fingerprint}, {@code keyData} encrypted,
     *           {@code keyEpoch}). All non-{@code keyId} columns are
     *           folded into the embedded {@link AppStateSyncKey} value
     *           object since Cobalt does not query them via secondary
     *           indices — {@link #expireAppStateKeysByEpoch(int)} scans
     *           the values map in-memory rather than using the
     *           {@code keyEpoch} index.
     */
    @ProtobufProperty(index = 55, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSchemaSyncKeys",
            exports = "getSyncKeysTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final LinkedHashMap<String, AppStateSyncKey> appStateKeys;

    /**
     * The Double-Ratchet session records keyed by the peer Signal address.
     *
     * @implNote Mirrors the {@code session-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getSessionTable()}). The
     *           {@code address} column is the user-defined primary key in
     *           WA Web; Cobalt uses {@link SignalProtocolAddress} as the map
     *           key directly.
     */
    @ProtobufProperty(index = 56, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSessionTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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

    /**
     * The persisted public identity keys of every peer device this client
     * has talked to.
     *
     * @implNote Mirrors the {@code identity-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getIdentityTable()}). The WA Web
     *           row carries an {@code identityKey} blob plus the
     *           bookkeeping columns {@code rowId} and {@code sentAddonRowId}
     *           used by {@code WAWebSendMsgCommonApi.updateIdentityRange};
     *           Cobalt keeps the public key here and tracks the regular
     *           {@code rowId} sequence in {@link #identityEncryptionRange}.
     *           The {@code sentAddonRowId} column has no counterpart yet
     *           because the addon (reaction / edit) send flow that
     *           populates it is not implemented in Cobalt.
     */
    @ProtobufProperty(index = 67, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getIdentityTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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

    /**
     * The dedicated outgoing contact store, keyed by phone-number JID.
     *
     * <p>This field is the persistent backing store for the WhatsApp Web
     * "invite by contact" feature, mirroring the dedicated {@code out-contact}
     * IndexedDB table that WA Web maintains separately from its regular
     * contact table. The map is owned exclusively by the
     * {@link com.github.auties00.cobalt.sync.handler.OutContactHandler outgoing
     * contact sync handler}, which routes both upserts and removals through
     * the public store accessors below.
     *
     * @implNote WAWebDBOutContactDatabaseApi — backs the {@code out-contact}
     *           table accessed exclusively through this database API; the
     *           protobuf index slot {@code 81} is reserved for this field
     */
    @ProtobufProperty(index = 81, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    protected ConcurrentHashMap<Jid, OutContact> outContacts;

    /**
     * The server-vs-local clock skew, in seconds, captured from the
     * {@code <success>} stanza's {@code t} attribute.
     *
     * @implNote WAWebUpdateClockSkewUtils.updateClockSkew -
     *           {@code WATimeUtils.setClockSkew(n)}
     */
    @ProtobufProperty(index = 84, type = ProtobufType.INT64)
    protected long clockSkewSeconds; // WAWebUpdateClockSkewUtils.updateClockSkew

    /**
     * The timestamp of the last group AB-props emergency push signalled by
     * the server via the {@code <success>} stanza's {@code group_abprops}
     * attribute.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     */
    @ProtobufProperty(index = 85, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    protected Instant groupAbPropsEmergencyPushTimestamp; // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp

    /**
     * The {@code abKey} field of the {@code ABPROPS} JSON blob most recently
     * persisted by {@code updateAttributesLocalStorage}.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @ProtobufProperty(index = 86, type = ProtobufType.STRING)
    protected String abPropsAbKey; // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * The {@code hash} field of the {@code ABPROPS} JSON blob most recently
     * persisted by {@code updateAttributesLocalStorage}.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @ProtobufProperty(index = 87, type = ProtobufType.STRING)
    protected String abPropsHash; // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * The clamped {@code refresh} interval (seconds) of the {@code ABPROPS}
     * JSON blob. Stored as a primitive so the protobuf manifest does not need
     * a wrapper; the explicit {@code 0} sentinel is interpreted as "never
     * recorded" by {@link #abPropsRefresh()}, which then surfaces the
     * {@code 86400} default to mirror {@code WAWebABPropsLocalStorage.getRefresh}.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @ProtobufProperty(index = 88, type = ProtobufType.INT64)
    protected long abPropsRefresh; // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * The {@code lastSyncTime} field of the {@code ABPROPS} JSON blob most
     * recently persisted by {@code updateAttributesLocalStorage}.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @ProtobufProperty(index = 89, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    protected Instant abPropsLastSyncTime; // WAWebABPropsLocalStorage.updateAttributesLocalStorage

    /**
     * The {@code ABPROPS_REFRESH_ID} {@code localStorage} value.
     *
     * @implNote WAWebABPropsLocalStorage.setRefreshId
     */
    @ProtobufProperty(index = 90, type = ProtobufType.INT64)
    protected long abPropsRefreshId; // WAWebABPropsLocalStorage.setRefreshId

    /**
     * The {@code UserPrefs.AbpropsWebRefreshId} {@code localStorage} value.
     *
     * @implNote WAWebABPropsLocalStorage.setWebRefreshId
     */
    @ProtobufProperty(index = 91, type = ProtobufType.INT64)
    protected long abPropsWebRefreshId; // WAWebABPropsLocalStorage.setWebRefreshId

    /**
     * The {@code GROUP_ABPROPS_REFRESH_ID} {@code localStorage} value.
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
     */
    @ProtobufProperty(index = 92, type = ProtobufType.INT64)
    protected long groupAbPropsRefreshId; // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId

    /**
     * The Alice base-key dedupe table for inbound pre-key messages.
     *
     * <p>Each entry is keyed by the encoded peer address and the
     * {@code originalMsgId} of the pre-key {@code <message>} stanza, and
     * holds the 32-byte X3DH ephemeral public key (Alice's base key) of the
     * inbound session-initiation. The receive path uses this map to detect
     * a peer replaying the same pre-key message (which would otherwise
     * cause a duplicate session to be derived) and to skip re-decryption
     * when {@link #hasSameBaseKey} reports a match.
     *
     * <p>The map is keyed by a flat {@code "<address>|<originalMsgId>"}
     * string because the protobuf manifest only supports scalar map keys;
     * lookups go through the {@link #saveSessionBaseKey},
     * {@link #findSessionBaseKey}, {@link #hasSameBaseKey} and
     * {@link #removeSessionBaseKey} accessors which take the typed
     * {@code (address, originalMsgId)} pair.
     *
     * @implNote Mirrors the {@code baseKey-store} IndexedDB table
     *           ({@code WAWebSignalStorage.getBaseKeyTable()}); the
     *           composite index on {@code [address, originalMsgId]} is
     *           collapsed into the encoded map key. The auto-incrementing
     *           {@code id} column has no Cobalt counterpart because
     *           {@link #removeSessionBaseKey} addresses entries by
     *           composite key directly. The base-key dedupe semantics
     *           themselves match {@code WAWebSignalSessionApi.saveSessionBaseKey}
     *           and {@code WAWebSignalSessionApi.hasSameBaseKey}.
     */
    @ProtobufProperty(index = 93, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.BYTES)
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getBaseKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    protected final ConcurrentMap<String, byte[]> baseKeys;

    protected final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange;

    protected final AtomicLong encryptionSequence;

    protected WhatsAppClientProxy proxy;

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

    protected final Set<Jid> coexHostedVerificationCache;

    /**
     * The set of chat JIDs for which a click-to-WhatsApp (CTWA) UTM
     * attribution payload has already been consumed during this session.
     *
     * @implNote Mirrors {@code WAWebChatUtmCache.utmReadChatIds}, the in-memory
     *           {@code Set<string>} that guards against re-reading the
     *           per-chat UTM value stored in {@code WAWebUtmBizPrefs} more
     *           than once. The cache is cleared when
     *           {@code WAWebChatUtmCache.clearAll} is invoked and entries
     *           are removed when a new UTM payload is persisted via
     *           {@code WAWebUpdateUtmAction.addUtmToChat}. The WA Web
     *           keys are string forms of the chat id; Cobalt uses the
     *           underlying {@link Jid} for type safety, matching the
     *           convention of {@link #unconfirmedIdentityChanges} and
     *           {@link #coexHostedVerificationCache}.
     */
    protected final Set<Jid> utmReadChatIds;

    /**
     * The set of contacts currently blocked by this account. Mirrors
     * WA Web's in-memory blocklist collection populated by
     * {@code WAWebApiBlocklist.updateBlocklist} / consumed by
     * {@code WAWebGetBlocklistJob.getBlocklist}.
     */
    protected final Set<Jid> blockedContacts;

    protected volatile WaffleAccountLinkStateAction.AccountLinkState waffleAccountLinkState;

    protected volatile Instant waffleAccountLinkStateTimestamp;

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

    protected Map<String, Boolean> businessFeatureFlags;

    protected Map<String, String> businessCampaignStatuses;

    protected Map<String, String> businessSubscriptionStatuses;

    protected Map<String, Long> businessSubscriptionExpirations;

    protected Map<String, Long> businessSubscriptionCreationTimes;

    protected String businessAccountNonce;

    protected ConcurrentHashMap<String, Boolean> ctwaPerCustomerDataSharing;

    protected boolean detectedOutcomesEnabled;

    protected boolean primaryAllowsAllMutations;

    protected Map<String, AgentAction> agentStates;

    protected Map<String, String> chatAssignmentStates;

    protected Map<String, Boolean> chatAssignmentOpenedStates;

    protected String paymentInstructionCpi;

    protected List<CustomPaymentMethod> customPaymentMethods;

    protected MerchantPaymentPartnerAction merchantPaymentPartner;

    protected PaymentTosAction paymentTos;

    protected Map<String, MarketingMessageAction> marketingMessages;

    protected Map<String, String> marketingMessageBroadcasts;

    protected Map<String, BusinessBroadcastListAction> businessBroadcastLists;

    protected Map<String, BusinessBroadcastCampaignAction> businessBroadcastCampaigns;

    protected Map<String, BusinessBroadcastInsightsAction> businessBroadcastInsights;

    protected byte[] nctSalt;

    protected Map<String, Boolean> nuxStates;

    protected com.github.auties00.cobalt.model.device.DeviceCapabilities primaryDeviceCapabilities;

    protected Map<String, com.github.auties00.cobalt.model.device.DeviceCapabilities> deviceCapabilitiesStates;

    protected Map<String, com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction> interactiveMessageStates;

    protected Map<String, com.github.auties00.cobalt.model.sync.action.media.NoteEditAction> noteStates;

    protected Map<String, Instant> newsletterPinStates;

    protected Boolean hasAvatar;

    protected Map<String, com.github.auties00.cobalt.model.call.CallLog> callLogStates;

    protected Map<String, Boolean> botWelcomeRequestStates;

    protected Map<String, String> aiThreadTitles;

    protected UsernameChatStartModeAction.ChatStartMode usernameChatStartMode;

    protected NotificationActivitySettingAction.NotificationActivitySetting notificationActivitySetting;

    protected List<RecentEmojiWeight> recentEmojiWeights;

    protected String wamoUserIdentifier;

    protected MusicUserIdAction musicUserIdState;

    protected String newsletterSavedInterests;

    protected Boolean statusPostOptInNotificationPreferencesEnabled;

    protected PrivateProcessingSettingAction.PrivateProcessingStatus privateProcessingStatus;

    protected Boolean channelsPersonalisedRecommendationOptOut;

    protected byte[] ugcBotDefinition;

    protected MaibaAIFeaturesControlAction.MaibaAIFeatureStatus maibaAiFeatureStatus;

    protected Instant pairingTimestamp;

    protected final ConcurrentMap<String, com.github.auties00.cobalt.model.chat.ChatMessageInfo> peerMessages;

    protected final System.Logger logger;

    AbstractWhatsAppStore(java.util.UUID uuid, java.lang.Long phoneNumber, com.github.auties00.cobalt.client.WhatsAppClientType clientType, java.time.Instant initializationTimeStamp, com.github.auties00.cobalt.client.WhatsAppDevice device, com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel releaseChannel, boolean online, java.lang.String locale, java.lang.String name, java.lang.String verifiedName, java.net.URI profilePicture, java.lang.String about, com.github.auties00.cobalt.model.jid.Jid jid, com.github.auties00.cobalt.model.jid.Jid lid, java.lang.String businessAddress, java.lang.Double businessLongitude, java.lang.Double businessLatitude, java.lang.String businessDescription, java.lang.String businessWebsite, java.lang.String businessEmail, com.github.auties00.cobalt.model.business.profile.BusinessCategory businessCategory, java.util.concurrent.ConcurrentHashMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.contact.Contact> contacts, java.util.concurrent.ConcurrentHashMap<java.lang.String,com.github.auties00.cobalt.model.call.CallOffer> calls, java.util.concurrent.ConcurrentHashMap<com.github.auties00.cobalt.model.privacy.PrivacySettingType,com.github.auties00.cobalt.model.privacy.PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, com.github.auties00.cobalt.model.chat.ChatEphemeralTimer newChatsEphemeralTimer, com.github.auties00.cobalt.client.WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, java.lang.Integer registrationId, com.github.auties00.libsignal.key.SignalIdentityKeyPair noiseKeyPair, com.github.auties00.libsignal.key.SignalIdentityKeyPair identityKeyPair, com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity signedDeviceIdentity, com.github.auties00.libsignal.key.SignalSignedKeyPair signedKeyPair, java.util.LinkedHashMap<java.lang.Integer,com.github.auties00.libsignal.key.SignalPreKeyPair> preKeys, java.util.UUID fdid, byte[] deviceId, java.util.UUID advertisingId, byte[] identityId, byte[] backupToken, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.groups.SignalSenderKeyName,com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord> senderKeys, java.util.LinkedHashMap<java.lang.String,com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey> appStateKeys, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.SignalProtocolAddress,com.github.auties00.libsignal.state.SignalSessionRecord> sessions, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.sync.SyncPatchType,com.github.auties00.cobalt.model.sync.SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Sticker> recentStickers, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Sticker> favouriteStickers, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.QuickReply> quickReplies, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Label> labels, com.github.auties00.cobalt.model.device.pairing.ClientAppVersion clientVersion, com.github.auties00.cobalt.model.device.pairing.ClientAppVersion companionVersion, java.time.Instant lastAdvCheckTime, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.SignalProtocolAddress,com.github.auties00.libsignal.key.SignalIdentityPublicKey> remoteIdentities, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.business.BusinessVerifiedName> verifiedBusinessNames, java.nio.file.Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, com.github.auties00.cobalt.model.setting.ChatLockSettings chatLockSettings, java.util.List<com.github.auties00.cobalt.model.jid.Jid> favoriteChats, java.util.List<java.lang.String> primaryFeatures, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.chat.ChatMute> mentionEveryoneMuteExpirations, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.sync.SyncPatchType,com.github.auties00.cobalt.store.AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, java.util.concurrent.ConcurrentHashMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.contact.OutContact> outContacts, long clockSkewSeconds, java.time.Instant groupAbPropsEmergencyPushTimestamp, java.lang.String abPropsAbKey, java.lang.String abPropsHash, long abPropsRefresh, java.time.Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, java.util.concurrent.ConcurrentMap<java.lang.String, byte[]> baseKeys) {
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
        this.contactTextStatuses = new ConcurrentHashMap<>();
        this.outContacts = requireNonNullElseGet(outContacts, ConcurrentHashMap::new); // WAWebDBOutContactDatabaseApi — initialise the dedicated out-contact store
        this.clockSkewSeconds = clockSkewSeconds; // WAWebUpdateClockSkewUtils.updateClockSkew
        this.groupAbPropsEmergencyPushTimestamp = groupAbPropsEmergencyPushTimestamp; // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
        this.abPropsAbKey = abPropsAbKey; // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsHash = abPropsHash; // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsRefresh = abPropsRefresh; // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsLastSyncTime = abPropsLastSyncTime; // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsRefreshId = abPropsRefreshId; // WAWebABPropsLocalStorage.setRefreshId
        this.abPropsWebRefreshId = abPropsWebRefreshId; // WAWebABPropsLocalStorage.setWebRefreshId
        this.groupAbPropsRefreshId = groupAbPropsRefreshId; // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId

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
        this.coexHostedVerificationCache = ConcurrentHashMap.newKeySet();
        this.utmReadChatIds = ConcurrentHashMap.newKeySet();
        this.blockedContacts = ConcurrentHashMap.newKeySet();
        this.pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
        this.groupSenderKeyDistribution = new ConcurrentHashMap<>();
        this.orphanPaymentNotifications = new ConcurrentHashMap<>();
        this.tosNoticeIds = ConcurrentHashMap.newKeySet();
        this.businessFeatureFlags = new ConcurrentHashMap<>();
        this.businessCampaignStatuses = new ConcurrentHashMap<>();
        this.businessSubscriptionStatuses = new ConcurrentHashMap<>();
        this.businessSubscriptionExpirations = new ConcurrentHashMap<>();
        this.businessSubscriptionCreationTimes = new ConcurrentHashMap<>();
        this.ctwaPerCustomerDataSharing = new ConcurrentHashMap<>();
        this.lastAdvCheckTime = lastAdvCheckTime;
        this.remoteIdentities = requireNonNullElseGet(remoteIdentities, ConcurrentHashMap::new);
        this.missingSyncKeys = requireNonNullElseGet(missingSyncKeys, ConcurrentHashMap::new);
        this.advSecretKey = advSecretKey;
        this.verifiedBusinessNames = requireNonNullElseGet(verifiedBusinessNames, ConcurrentHashMap::new);
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
        this.primaryDeviceSupportsSyncdRecovery = primaryDeviceSupportsSyncdRecovery;
        this.disableLinkPreviews = disableLinkPreviews;
        this.relayAllCalls = relayAllCalls;
        this.externalWebBeta = externalWebBeta;
        this.chatLockSettings = chatLockSettings;
        this.favoriteChats = requireNonNullElseGet(favoriteChats, ArrayList::new);
        this.primaryFeatures = requireNonNullElseGet(primaryFeatures, ArrayList::new);
        this.agentStates = new ConcurrentHashMap<>();
        this.chatAssignmentStates = new ConcurrentHashMap<>();
        this.chatAssignmentOpenedStates = new ConcurrentHashMap<>();
        this.customPaymentMethods = new ArrayList<>();
        this.marketingMessages = new ConcurrentHashMap<>();
        this.marketingMessageBroadcasts = new ConcurrentHashMap<>();
        this.businessBroadcastLists = new ConcurrentHashMap<>();
        this.businessBroadcastCampaigns = new ConcurrentHashMap<>();
        this.businessBroadcastInsights = new ConcurrentHashMap<>();
        this.nuxStates = new ConcurrentHashMap<>();
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
        this.baseKeys = requireNonNullElseGet(baseKeys, ConcurrentHashMap::new); // WAWebSignalStorage.getBaseKeyTable
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
    public Optional<WaffleAccountLinkStateAction.AccountLinkState> waffleAccountLinkState() {
        return Optional.ofNullable(waffleAccountLinkState);
    }

    @Override
    public WhatsAppStore setWaffleAccountLinkState(WaffleAccountLinkStateAction.AccountLinkState state) {
        this.waffleAccountLinkState = state;
        return this;
    }

    @Override
    public Optional<Instant> waffleAccountLinkStateTimestamp() {
        return Optional.ofNullable(waffleAccountLinkStateTimestamp);
    }

    @Override
    public WhatsAppStore setWaffleAccountLinkStateTimestamp(Instant timestamp) {
        this.waffleAccountLinkStateTimestamp = timestamp;
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
    public Map<String, Boolean> businessFeatureFlags() {
        return Collections.unmodifiableMap(businessFeatureFlags);
    }

    @Override
    public WhatsAppStore setBusinessFeatureFlags(Map<String, Boolean> flags) {
        this.businessFeatureFlags = flags == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(flags);
        return this;
    }

    @Override
    public Map<String, String> businessCampaignStatuses() {
        return Collections.unmodifiableMap(businessCampaignStatuses);
    }

    @Override
    public WhatsAppStore setBusinessCampaignStatuses(Map<String, String> statuses) {
        this.businessCampaignStatuses = statuses == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(statuses);
        return this;
    }

    @Override
    public Map<String, String> businessSubscriptionStatuses() {
        return Collections.unmodifiableMap(businessSubscriptionStatuses);
    }

    @Override
    public WhatsAppStore setBusinessSubscriptionStatuses(Map<String, String> statuses) {
        this.businessSubscriptionStatuses = statuses == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(statuses);
        return this;
    }

    @Override
    public Map<String, Long> businessSubscriptionExpirations() {
        return Collections.unmodifiableMap(businessSubscriptionExpirations);
    }

    @Override
    public WhatsAppStore setBusinessSubscriptionExpirations(Map<String, Long> expirations) {
        this.businessSubscriptionExpirations = expirations == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(expirations);
        return this;
    }

    @Override
    public Map<String, Long> businessSubscriptionCreationTimes() {
        return Collections.unmodifiableMap(businessSubscriptionCreationTimes);
    }

    @Override
    public WhatsAppStore setBusinessSubscriptionCreationTimes(Map<String, Long> creationTimes) {
        this.businessSubscriptionCreationTimes = creationTimes == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(creationTimes);
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

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           returns an unmodifiable view backed by the live
     *           {@link ConcurrentHashMap} keyed by account LID raw string
     */
    @Override
    public Map<String, Boolean> ctwaPerCustomerDataSharing() {
        return Collections.unmodifiableMap(ctwaPerCustomerDataSharing);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code get(lidRawString)} read on the IDB table;
     *           returns {@link Optional#empty()} when {@code accountLid} is
     *           {@code null} or no entry exists for it
     */
    @Override
    public Optional<Boolean> findCtwaDataSharing(String accountLid) {
        if (accountLid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ctwaPerCustomerDataSharing.get(accountLid));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code createOrReplace} write on the IDB table
     *           with the row {@code (lidRawString, dataSharing3pdEnabled)}
     */
    @Override
    public WhatsAppStore setCtwaDataSharing(String accountLid, Boolean enabled) {
        Objects.requireNonNull(accountLid, "accountLid cannot be null");
        Objects.requireNonNull(enabled, "enabled cannot be null");
        ctwaPerCustomerDataSharing.put(accountLid, enabled);
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebSchemaCtwaPerCustomerDataSharing (data-sharing-3pd-lid-v2 IDB table) —
     *           equivalent to a {@code remove(lidRawString)} delete on the IDB
     *           table; a {@code null} {@code accountLid} is a no-op
     */
    @Override
    public WhatsAppStore removeCtwaDataSharing(String accountLid) {
        if (accountLid != null) {
            ctwaPerCustomerDataSharing.remove(accountLid);
        }
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
     * Returns the underlying outgoing contact map for protobuf serialization.
     *
     * <p>This package-private accessor exposes the live mutable map so that
     * generated protobuf encode/decode glue can iterate the entries directly
     * without the unmodifiable wrapper applied by {@link #outContacts()}.
     *
     * @return the live {@code ConcurrentHashMap} backing the outgoing contact
     *         store
     * @implNote WAWebDBOutContactDatabaseApi — internal accessor used to
     *           serialise the dedicated {@code out-contact} table
     */
    protected ConcurrentHashMap<Jid, OutContact> outContactsField() {
        return outContacts;
    }

    /**
     * Returns an unmodifiable view of all outgoing contacts.
     *
     * @return an unmodifiable map of outgoing contacts keyed by JID
     * @implNote WAWebDBOutContactDatabaseApi — replaces the
     *           {@code getAllOutContacts} accessor on the WA Web database API
     */
    @Override
    public Map<Jid, OutContact> outContacts() {
        return Collections.unmodifiableMap(outContacts);
    }

    /**
     * Finds an outgoing contact by its phone-number JID.
     *
     * @param jid the JID to look up, may be {@code null}
     * @return an {@code Optional} containing the outgoing contact if it exists
     * @implNote WAWebDBOutContactDatabaseApi — mirrors the
     *           {@code getOutContact} lookup on the WA Web database API
     */
    @Override
    public Optional<OutContact> findOutContact(Jid jid) {
        if (jid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(outContacts.get(jid));
    }

    /**
     * Adds or merges an outgoing contact in the store.
     *
     * <p>If an entry with the same JID is already present, its name fields are
     * overwritten with the values supplied by the new record. This mirrors the
     * batched upsert semantics used by WA Web's
     * {@code putOutContactBatch} call.
     *
     * @param outContact the outgoing contact to add or merge, must not be
     *                   {@code null}
     * @return this store instance for method chaining
     * @implNote WAWebDBOutContactDatabaseApi.putOutContactBatch — bulk upsert
     *           into the {@code out-contact} table
     */
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

    /**
     * Removes an outgoing contact from the store by its phone-number JID.
     *
     * @param jid the JID of the outgoing contact to remove, may be {@code null}
     * @return this store instance for method chaining
     * @implNote WAWebDBOutContactDatabaseApi.removeOutContactBatch — bulk
     *           removal from the {@code out-contact} table
     */
    @Override
    public WhatsAppStore removeOutContact(Jid jid) {
        if (jid != null) {
            outContacts.remove(jid);
        }
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
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public SequencedCollection<SignalPreKeyPair> preKeys() {
        return preKeys.sequencedValues();
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public boolean hasPreKeys() {
        return !preKeys.isEmpty();
    }

    /**
     * Returns the pre-key with the given numeric id, if any.
     *
     * @param id the pre-key id, or {@code null} for a no-op lookup
     * @return an {@link Optional} containing the pre-key pair, or
     *         {@link Optional#empty()} if the id is {@code null} or no
     *         such pre-key exists
     * @implNote Mirrors {@code WAWebSignalStoreApi.loadPreKey} via
     *           {@code WAWebSignalStorage.getPreKeyTable().equals(...)}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SignalPreKeyPair> findPreKeyById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(preKeys.get(id));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addPreKey(SignalPreKeyPair preKey) {
        Objects.requireNonNull(preKey, "preKey cannot be null");
        preKeys.put(preKey.id(), preKey);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public boolean removePreKey(int id) {
        return preKeys.remove(id) != null;
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSignedPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SignalSignedKeyPair> findSignedPreKeyById(Integer id) {
        return id == signedKeyPair.id() ? Optional.of(signedKeyPair) : Optional.empty();
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSignedPreKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addSignedPreKey(SignalSignedKeyPair signalSignedKeyPair) {
        throw new UnsupportedOperationException("Cannot add signed pre keys to a Keys instance");
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSessionTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SignalSessionRecord> findSessionByAddress(SignalProtocolAddress address) {
        return Optional.ofNullable(sessions.get(address));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSessionTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addSession(SignalProtocolAddress address, SignalSessionRecord record) {
        sessions.put(address, record);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSenderKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SignalSenderKeyRecord> findSenderKeyByName(SignalSenderKeyName name) {
        return Optional.ofNullable(senderKeys.get(name));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSenderKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addSenderKey(SignalSenderKeyName name, SignalSenderKeyRecord newRecord) {
        senderKeys.put(name, newRecord);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSessionTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public boolean removeSession(SignalProtocolAddress address) {
        return sessions.remove(address) != null;
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSenderKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void removeSenderKeys(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        senderKeys.keySet().removeIf(name ->
                name.sender().equals(signalAddress)
        );
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getSenderKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void removeSenderKeys(SignalSenderKeyName senderKeyName) {
        Objects.requireNonNull(senderKeyName, "senderKeyName cannot be null");
        senderKeys.remove(senderKeyName);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = {"getSessionTable", "getSenderKeyTable"},
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void cleanupSignalSessions(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        removeSession(signalAddress);
        removeSenderKeys(deviceJid);
    }

    /**
     * Encodes a {@code (address, originalMsgId)} pair as the flat string
     * key used by {@link #baseKeys}.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the WA Web {@code originalMsgId} from the
     *                      pre-key {@code <message>} stanza
     * @return the encoded composite key
     */
    private static String encodeBaseKeyKey(SignalProtocolAddress address, String originalMsgId) {
        return address.toString() + "|" + originalMsgId;
    }

    /**
     * Persists Alice's X3DH base key for a pre-key message so that a
     * future replay of the same message can be deduplicated.
     *
     * @param address       the peer Signal address that initiated the
     *                      session
     * @param originalMsgId the {@code originalMsgId} carried by the
     *                      pre-key stanza
     * @param baseKey       the 32-byte X3DH ephemeral public key (Alice's
     *                      base key) extracted from the pre-key message
     * @throws NullPointerException if any argument is {@code null}
     * @implNote {@code WAWebSignalSessionApi.saveSessionBaseKey} delegates
     *           to {@code WAWebSignalStoreApi.saveBaseKey}, which performs
     *           {@code getBaseKeyTable().createOrReplace(...)}. Cobalt
     *           collapses the persistent table into the in-memory
     *           {@link #baseKeys} map.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getBaseKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void saveSessionBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] baseKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(baseKey, "baseKey cannot be null");
        baseKeys.put(encodeBaseKeyKey(address, originalMsgId), baseKey);
    }

    /**
     * Returns the previously-saved Alice base key for a
     * {@code (address, originalMsgId)} pair, if any.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @return an {@link Optional} containing the 32-byte base key, or
     *         {@link Optional#empty()} if no entry exists
     * @throws NullPointerException if any argument is {@code null}
     * @implNote Mirrors {@code WAWebSignalStoreApi.loadBaseKey}, which
     *           queries the {@code baseKey-store} composite index
     *           {@code [address, originalMsgId]} for the Dexie row.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getBaseKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<byte[]> findSessionBaseKey(SignalProtocolAddress address, String originalMsgId) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        return Optional.ofNullable(baseKeys.get(encodeBaseKeyKey(address, originalMsgId)));
    }

    /**
     * Reports whether a stored base key matches the candidate one for the
     * given {@code (address, originalMsgId)} pair.
     *
     * <p>Used by the receive path to detect a duplicate pre-key message
     * (the same peer replaying the same {@code originalMsgId} with the
     * same Alice base key) and skip re-deriving the session.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @param candidate     the candidate base key extracted from the
     *                      newly-received pre-key message
     * @return {@code true} if a base key was previously stored for this
     *         pair and equals {@code candidate}, {@code false} otherwise
     * @throws NullPointerException if any argument is {@code null}
     * @implNote Mirrors {@code WAWebSignalSessionApi.hasSameBaseKey}, which
     *           combines {@code WAWebSignalStoreApi.loadBaseKey} with a
     *           constant-time byte comparison ({@code bufferEqual}).
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getBaseKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public boolean hasSameBaseKey(SignalProtocolAddress address, String originalMsgId, byte[] candidate) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(originalMsgId, "originalMsgId cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");
        var stored = baseKeys.get(encodeBaseKeyKey(address, originalMsgId));
        return stored != null && Arrays.equals(stored, candidate);
    }

    /**
     * Removes the persisted base key for a {@code (address, originalMsgId)}
     * pair, if any.
     *
     * @param address       the peer Signal address
     * @param originalMsgId the {@code originalMsgId} of the pre-key stanza
     * @return {@code true} if an entry was removed
     * @throws NullPointerException if any argument is {@code null}
     * @implNote Mirrors {@code WAWebSignalStoreApi.deleteBaseKey}, which
     *           looks the row up by the composite index then removes it
     *           by primary key.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getBaseKeyTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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

    /**
     * {@inheritDoc}
     *
     * @implNote Per WAWebGetSyncKey.getAllSyncKeysInTransaction:
     *           {@code runInTransaction({SyncKeyStore: true}, t => t.SyncKeyStore.getAll())}
     *           which delegates to
     *           {@code WAWebSyncdDb.getAllSyncKeys() ->
     *           getSyncKeysTable().all().map(convertToSyncKeyFromRow)}.
     *           Cobalt returns the in-memory map's sequenced values
     *           wrapped in an unmodifiable view.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getAllSyncKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public SequencedCollection<AppStateSyncKey> appStateKeys() {
        // WAWebSyncdDb.getAllSyncKeys: getSyncKeysTable().all()
        return Collections.unmodifiableSequencedCollection(appStateKeys.sequencedValues());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Per WAWebGetSyncKey.getSyncKeyInTransaction_DO_NOT_USE:
     *           {@code runInTransaction({SyncKeyStore: true}, t => t.SyncKeyStore.get(id))}
     *           which delegates to
     *           {@code WAWebSyncdDb.getSyncKey(id) ->
     *           getSyncKeysTable().get(new Uint8Array(fromSyncKeyId(id)))}.
     *           {@code WASyncdKeyTypes.fromSyncKeyId} is the identity
     *           function so the lookup is by raw {@code keyId} bytes.
     *           Cobalt keys the in-memory map by hex-encoded
     *           {@code keyId} for {@link String}-keyed storage.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "getSyncKeyInTransaction_DO_NOT_USE",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id) {
        // WAWebSyncdDb.getSyncKey: getSyncKeysTable().get(new Uint8Array(fromSyncKeyId(e)))
        return Optional.ofNullable(appStateKeys.get(HexFormat.of().formatHex(id)));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Per WAWebGetSyncKey.setSyncKeyInTransaction:
     *           {@code runInTransaction({SyncKeyStore: true}, t => t.SyncKeyStore.set(key))}
     *           which delegates to
     *           {@code WAWebSyncdDb.createSyncKey(key) ->
     *           getSyncKeysTable().createOrReplace(convertFromSyncKeyToRow(key))}.
     *           Cobalt accepts a {@link Collection} per call to amortise
     *           the rotation/key-share batched store path
     *           ({@code SyncKeyRotationService}) — WA Web invokes
     *           {@code setSyncKeyInTransaction} once per key from
     *           {@code WAWebSyncdHandleKeyShare.handleKeyShare} and
     *           {@code WAWebSyncdKeyManagement}. The empty
     *           {@code keyData} guard is an ADAPTED defensive filter
     *           with no JS counterpart: WA Web's call sites pass
     *           individual fully-populated keys after a {@code n != null}
     *           check, while Cobalt's bulk wrapper additionally rejects
     *           rows whose payload is absent or zero-length.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "setSyncKeyInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addWebAppStateKeys(Collection<AppStateSyncKey> keys) {
        for (var key : keys) {
            // ADAPTED: defensive filter with no WA Web counterpart — reject keys without payload.
            var hasKeyData = key.keyData()
                    .flatMap(AppStateSyncKeyData::keyData)
                    .map(data -> data.length > 0)
                    .orElse(false);
            if (!hasKeyData) {
                continue;
            }
            // WAWebSyncdDb.createSyncKey: getSyncKeysTable().createOrReplace(convertFromSyncKeyToRow(e))
            key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .ifPresent(keyId -> appStateKeys.put(HexFormat.of().formatHex(keyId), key));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote NO_WA_BASIS — WA Web's {@code SyncKeyStore} only exposes
     *           {@code expire(epoch)} (mapped to
     *           {@link #expireAppStateKeysByEpoch(int)}) and
     *           {@code clear()} ({@code WAWebSchemaSyncKeys.getSyncKeysTable().clear}).
     *           This timestamp-threshold variant has no equivalent and
     *           is currently unused; retained pending cleanup by the
     *           phantom-sweep agent.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote Per WAWebGetSyncKey.expireSyncKeyInTransaction:
     *           {@code runInTransaction({SyncKeyStore: true}, t => t.SyncKeyStore.expire(epoch))}
     *           which delegates to
     *           {@code WAWebSyncdDb.expireSyncKey(epoch)}:
     *           <pre>{@code
     *             const t = yield getSyncKeysTable().equals(["keyEpoch"], epoch);
     *             t.forEach(e => getSyncKeysTable().merge({keyId: e.keyId},
     *                                                    {timestamp: 0}));
     *           }</pre>
     *           — i.e. the IndexedDB {@code keyEpoch} index lookup is
     *           replaced by an in-memory scan over
     *           {@link #appStateKeys}, and the partial-row merge is
     *           replaced by mutating the embedded
     *           {@link AppStateSyncKeyData#setTimestamp} to
     *           {@link Instant#EPOCH} (the Java equivalent of
     *           {@code timestamp: 0}). Note that this is a soft-mark,
     *           NOT a deletion — keys remain in the map.
     */
    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncKey",
            exports = "expireSyncKeyInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void expireAppStateKeysByEpoch(int epoch) {
        for (var key : appStateKeys.values()) {
            // WAWebSyncdDb.expireSyncKey: getSyncKeysTable().equals(["keyEpoch"], e) — Cobalt scans values, no secondary index.
            if (com.github.auties00.cobalt.sync.key.SyncKeyUtils.getKeyEpoch(key) != epoch) {
                continue;
            }

            // WAWebSyncdDb.expireSyncKey: getSyncKeysTable().merge({keyId: e.keyId}, {timestamp: 0})
            key.keyData().ifPresent(data -> data.setTimestamp(Instant.EPOCH));
        }
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "getCollectionVersionLtHashInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SyncHashValue> findWebAppHashStateByName(SyncPatchType patchType) {
        return Optional.ofNullable(hashStates.get(patchType));
    }

    @Override
    public void addWebAppHashState(SyncHashValue state) {
        hashStates.put(state.type(), state);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByIndexMacsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SyncActionEntry> findSyncActionEntry(SyncPatchType patchType, byte[] indexMac) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.get(HexFormat.of().formatHex(indexMac)));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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
    @WhatsAppWebExport(
            moduleName = "WAWebSyncActionStore",
            exports = "WAWebSyncActionStore",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void putSyncActionEntry(SyncPatchType patchType, byte[] indexMac, SyncActionEntry entry) {
        syncActionEntries.computeIfAbsent(patchType, _ -> new ConcurrentHashMap<>())
                .put(HexFormat.of().formatHex(indexMac), entry); // WAWebSyncActionStore.bulkSet/bulkUpdate: setSyncActionRows / updateSyncActionRows
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByIndexMacsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<SyncActionEntry> removeSyncActionEntry(SyncPatchType patchType, byte[] indexMac) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inner.remove(HexFormat.of().formatHex(indexMac)));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByCollectionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void clearSyncActionEntries(SyncPatchType patchType) {
        syncActionEntries.remove(patchType);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getSyncActionsByCollectionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Collection<SyncActionEntry> getSyncActionEntries(SyncPatchType patchType) {
        var inner = syncActionEntries.get(patchType);
        if (inner == null) {
            return List.of();
        }
        return Collections.unmodifiableCollection(inner.values());
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "countSyncActionsInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public int countSyncActionEntries() {
        var total = 0;
        for (var inner : syncActionEntries.values()) {
            total += inner.size(); // WAWebSyncActionStore.count: getSyncActionsTable().count()
        }
        return total;
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getAllSyncActions",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Collection<SyncActionEntry> getAllSyncActionEntries() {
        if (syncActionEntries.isEmpty()) {
            return List.of();
        }
        var all = new ArrayList<SyncActionEntry>();
        for (var inner : syncActionEntries.values()) {
            all.addAll(inner.values()); // WAWebSyncActionStore.getAll: getSyncActionsTable().all()
        }
        return Collections.unmodifiableCollection(all);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getAllMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Collection<MissingDeviceSyncKey> missingSyncKeys() {
        return Collections.unmodifiableCollection(missingSyncKeys.values()); // WAWebMissingKeyStore.getAll: WAWebSyncdDb.getAllMissingKeys()
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkGetMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] keyId) {
        // ADAPTED: WAWebMissingKeyStore.bulkGet takes a list of keyHex strings;
        //         Cobalt looks up a single entry by raw keyId bytes encoded with HexFormat.
        return Optional.ofNullable(missingSyncKeys.get(HexFormat.of().formatHex(keyId)));
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "getMissingKeyCountTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public int missingSyncKeyCount() {
        return missingSyncKeys.size(); // WAWebMissingKeyStore.count: WAWebSyncdDb.getMissingKeyCount()
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addMissingSyncKey(MissingDeviceSyncKey missingKey) {
        // Single-element form of WAWebMissingKeyStore.bulkUpdate:
        //   WAWebSyncdDb.createOrUpdateMissingKeys([{keyHex, keyId, timestamp, deviceResponses}])
        missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetMissingKey",
            exports = "bulkUpdateMissingKeysInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addMissingSyncKeys(Collection<MissingDeviceSyncKey> missingKeys) {
        Objects.requireNonNull(missingKeys, "missingKeys cannot be null");
        // WAWebMissingKeyStore.bulkUpdate: WAWebSyncdDb.createOrUpdateMissingKeys(records)
        for (var missingKey : missingKeys) {
            this.missingSyncKeys.put(HexFormat.of().formatHex(missingKey.keyId()), missingKey);
        }
    }

    @Override
    public void removeMissingSyncKey(byte[] keyId) {
        // Single-element form of WAWebMissingKeyStore.bulkRemove:
        //   WAWebSyncdDb.bulkRemoveMissingKeys([keyHex])
        missingSyncKeys.remove(HexFormat.of().formatHex(keyId));
    }

    @Override
    public void addPeerMessage(String id, com.github.auties00.cobalt.model.chat.ChatMessageInfo message) {
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
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "checkOrphanMutations",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void addOrphanMutation(SyncPatchType collectionName, OrphanMutationEntry mutation) {
        orphanMutationEntries.computeIfAbsent(collectionName, _ -> new OrphanMutationEntries())
                .data()
                .add(mutation);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetSyncAction",
            exports = "getOrphanSyncActionsByModelTypeInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "applyAllOrphansAndUnsupported",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void removeOrphanMutations(SyncPatchType collectionName) {
        orphanMutationEntries.remove(collectionName);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdOrphan",
            exports = "applyAllOrphansAndUnsupported",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getIdentityTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void saveIdentity(SignalProtocolAddress address, SignalIdentityPublicKey identityKey) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(identityKey, "identityKey cannot be null");
        remoteIdentities.put(address, identityKey);
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSignalStorage",
            exports = "getIdentityTable",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
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

    public void markWebAppStateDirty(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty
        webAppStateCollections.compute(collectionName, (_, current) -> {
            if (current == null) {
                return new SyncCollectionMetadata(
                        collectionName,
                        0,
                        MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        0, // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: no lastSyncTimestamp field in WA Web state entry
                        SyncCollectionState.DIRTY, // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: state = Dirty
                        0,
                        0, // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: finiteFailureStartTime = undefined for new entries
                        false,
                        false
                );
            }
            return new SyncCollectionMetadata(
                    current.name(),
                    current.version(),
                    current.ltHash(), // ADAPTED: Cobalt shares record reference, no copy needed
                    current.lastSyncTimestamp(), // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: WA Web does not update lastSyncTimestamp
                    SyncCollectionState.DIRTY, // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: state = Dirty
                    current.retryCount(), // ADAPTED: retryCount preserved (WA Web tracks retries via global counter W in WAWebSyncd)
                    current.lastErrorTimestamp(), // WAWebSyncdCollectionsStateMachine.moveCollectionsToDirty: unconditionally preserves finiteFailureStartTime
                    current.macMismatch(),
                    current.bootstrapped()
            );
        });
    }

    @Override
    public void markWebAppStateInFlight(SyncPatchType collectionName) { // NO_WA_BASIS: InFlight is a Cobalt-only state (WA Web uses in-memory Set A for inflight tracking)
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.IN_FLIGHT,
                        current.retryCount(),
                        current.lastErrorTimestamp(),
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    public void markWebAppStateUpToDate(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.moveCollectionsToUpToDate
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        System.currentTimeMillis(),
                        SyncCollectionState.UP_TO_DATE, // WAWebSyncdCollectionsStateMachine.moveCollectionsToUpToDate: state = UpToDate
                        0, // ADAPTED: reset retry count (WA Web uses global counter W)
                        0, // WAWebSyncdCollectionsStateMachine.moveCollectionsToUpToDate: finiteFailureStartTime = undefined
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    public void markWebAppStatePending(SyncPatchType collectionName) { // NO_WA_BASIS: Pending is a Cobalt-only state (WA Web uses in-memory Set F for pending tracking)
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.PENDING,
                        current.retryCount(),
                        current.lastErrorTimestamp(),
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    public void markWebAppStateBlocked(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.moveCollectionsToBlocked
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.BLOCKED, // WAWebSyncdCollectionsStateMachine.moveCollectionsToBlocked: state = Blocked
                        current.retryCount(),
                        current.lastErrorTimestamp(), // WAWebSyncdCollectionsStateMachine.moveCollectionsToBlocked: preserves finiteFailureStartTime
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    public void markWebAppStateErrorRetry(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.moveCollectionsToFiniteRetry
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.ERROR_RETRY, // WAWebSyncdCollectionsStateMachine.moveCollectionsToFiniteRetry: state = FailingFiniteRetry
                        current.retryCount() + 1, // ADAPTED: per-collection retry count (WA Web uses global W counter in WAWebSyncd)
                        current.lastErrorTimestamp() > 0 ? current.lastErrorTimestamp() : System.currentTimeMillis(), // WAWebSyncdCollectionsStateMachine.moveCollectionsToFiniteRetry: finiteFailureStartTime ?? unixTimeMs()
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    public void markWebAppStateErrorFatal(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.moveCollectionsToFatal
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        SyncCollectionState.ERROR_FATAL, // WAWebSyncdCollectionsStateMachine.moveCollectionsToFatal: state = Fatal
                        current.retryCount(),
                        0, // WAWebSyncdCollectionsStateMachine.moveCollectionsToFatal: does not include finiteFailureStartTime (undefined)
                        current.macMismatch(),
                        current.bootstrapped()
                )
        );
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "getIsCollectionInMacMismatchFatalInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public boolean isCollectionInMacMismatchFatal(SyncPatchType collectionName) {
        // WAWebGetCollectionVersion.getIsCollectionInMacMismatchFatalInTransaction:
        // n.get(e).then(e => e?.isCollectionInMacMismatchFatal)
        // The optional-chain `e?.isCollectionInMacMismatchFatal` resolves to `undefined`
        // (falsy) when no entry exists; findWebAppState returns a default with
        // macMismatch == false in that case, matching that semantics.
        var current = webAppStateCollections.get(collectionName);
        return current != null && current.macMismatch();
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "updateIsCollectionInMacMismatchFatalInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void markWebAppStateMacMismatch(SyncPatchType collectionName) {
        // Per WA Web: isCollectionInMacMismatchFatal is a persistent boolean
        // that survives all state transitions, not a state enum value
        webAppStateCollections.computeIfPresent(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        current.name(),
                        current.version(),
                        current.ltHash(),
                        current.lastSyncTimestamp(),
                        current.state(),
                        current.retryCount(),
                        current.lastErrorTimestamp(),
                        true,
                        current.bootstrapped()
                )
        );
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebSyncdCollectionsStateMachine",
            exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED
    ) // WAWebSyncdCollectionsStateMachine.getCollectionState
    public SyncCollectionMetadata findWebAppState(SyncPatchType collectionName) { // WAWebSyncdCollectionsStateMachine.getCollectionState
        return webAppStateCollections.computeIfAbsent(collectionName, key -> // WAWebSyncdCollectionsStateMachine.getCollectionState: missing -> moveCollectionsToUpToDate, return UpToDate
                new SyncCollectionMetadata(
                        key,
                        0,
                        MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        0,
                        SyncCollectionState.UP_TO_DATE, // WAWebSyncdCollectionsStateMachine.getCollectionState: default = UpToDate
                        0,
                        0, // WAWebSyncdCollectionsStateMachine.moveCollectionsToUpToDate: finiteFailureStartTime = undefined
                        false,
                        false
                )
        );
    }

    @Override
    @WhatsAppWebExport(
            moduleName = "WAWebGetCollectionVersion",
            exports = "updateCollectionVersionAndLtHashInTransaction",
            adaptation = WhatsAppAdaptation.ADAPTED
    )
    public void updateWebAppStateVersion(SyncPatchType collectionName, long newVersion, byte[] newLtHash) {
        var copiedHash = MutationLTHash.copy(newLtHash);
        // Per WA Web: update both collection metadata and hash state atomically
        // to prevent inconsistent state on crash between writes
        webAppStateCollections.compute(collectionName, (_, current) ->
                new SyncCollectionMetadata(
                        collectionName,
                        newVersion,
                        copiedHash,
                        System.currentTimeMillis(),
                        current != null ? current.state() : SyncCollectionState.UP_TO_DATE,
                        0,  // Reset retry count on successful update
                        0,  // Reset error timestamp
                        current != null && current.macMismatch(),
                        true  // Collection has been synced at least once
                )
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

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebSchemaQuickReply.getQuickReplyTable().getAll()
     */
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

    /**
     * Records that the stored CTWA UTM payload for the given chat has been
     * consumed, so that {@link #hasReadUtmForChat(Jid)} returns {@code true}
     * on subsequent reads until the entry is evicted.
     *
     * @param chatJid the chat JID whose UTM payload has just been read;
     *                {@code null} is ignored
     * @implNote Mirrors {@code WAWebChatUtmCache.read(t)} which performs
     *           {@code this.utmReadChatIds.add(t)} on the singleton instance.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatUtmCache", exports = "default.read", adaptation = WhatsAppAdaptation.ADAPTED)
    public void markUtmReadForChat(Jid chatJid) {
        if (chatJid != null) {
            utmReadChatIds.add(chatJid); // WAWebChatUtmCache.read
        }
    }

    /**
     * Returns whether the CTWA UTM payload for the given chat has already
     * been consumed during this session.
     *
     * @param chatJid the chat JID to check
     * @return {@code true} if {@link #markUtmReadForChat(Jid)} was invoked
     *         for this chat and the entry has not since been evicted
     * @implNote Mirrors {@code WAWebChatUtmCache.hasRead(t)} which returns
     *           {@code this.utmReadChatIds.has(t)} on the singleton instance.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatUtmCache", exports = "default.hasRead", adaptation = WhatsAppAdaptation.ADAPTED)
    public boolean hasReadUtmForChat(Jid chatJid) {
        if (chatJid == null) {
            return false;
        }
        return utmReadChatIds.contains(chatJid); // WAWebChatUtmCache.hasRead
    }

    /**
     * Evicts the given chat from the UTM-read cache so that its UTM payload
     * will be re-read the next time it is requested. Invoked by
     * {@code WAWebUpdateUtmAction.addUtmToChat} after a new UTM value is
     * persisted for the chat.
     *
     * @param chatJid the chat JID to forget; {@code null} is ignored
     * @implNote Mirrors {@code WAWebChatUtmCache.deleteChatId(t)} which
     *           performs {@code this.utmReadChatIds.delete(t)} on the
     *           singleton instance.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatUtmCache", exports = "default.deleteChatId", adaptation = WhatsAppAdaptation.ADAPTED)
    public void deleteUtmReadChatId(Jid chatJid) {
        if (chatJid != null) {
            utmReadChatIds.remove(chatJid); // WAWebChatUtmCache.deleteChatId
        }
    }

    /**
     * Clears every entry from the UTM-read cache. Mirrors the
     * {@code clearAll} lifecycle hook called during logout / account-swap.
     *
     * @implNote Mirrors {@code WAWebChatUtmCache.clearAll()} which performs
     *           {@code this.utmReadChatIds.clear()} on the singleton
     *           instance.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatUtmCache", exports = "default.clearAll", adaptation = WhatsAppAdaptation.ADAPTED)
    public void clearUtmReadChatIds() {
        utmReadChatIds.clear(); // WAWebChatUtmCache.clearAll
    }

    /**
     * {@inheritDoc}
     *
     * @implNote {@code WAWebApiBlocklist.getBlocklist}
     */
    @Override
    public Set<Jid> blockedContacts() {
        return Collections.unmodifiableSet(blockedContacts);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote {@code WAWebApiBlocklist.updateBlocklist} add branch
     */
    @Override
    public void addBlockedContact(Jid contact) {
        if (contact != null) {
            blockedContacts.add(contact.toUserJid());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote {@code WAWebApiBlocklist.updateBlocklist} remove branch
     */
    @Override
    public void removeBlockedContact(Jid contact) {
        if (contact != null) {
            blockedContacts.remove(contact.toUserJid());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote {@code WAWebGetBlocklistJob.getBlocklist} bulk replace on
     * {@code GetBlockListResponseSuccessWithMismatch}
     */
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
    public Map<String, AgentAction> agentStates() {
        return Collections.unmodifiableMap(agentStates);
    }

    @Override
    public WhatsAppStore setAgentStates(Map<String, AgentAction> states) {
        this.agentStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, String> chatAssignmentStates() {
        return Collections.unmodifiableMap(chatAssignmentStates);
    }

    @Override
    public WhatsAppStore setChatAssignmentStates(Map<String, String> states) {
        this.chatAssignmentStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, Boolean> chatAssignmentOpenedStates() {
        return Collections.unmodifiableMap(chatAssignmentOpenedStates);
    }

    @Override
    public WhatsAppStore setChatAssignmentOpenedStates(Map<String, Boolean> states) {
        this.chatAssignmentOpenedStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
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
    public Map<String, MarketingMessageAction> marketingMessages() {
        return Collections.unmodifiableMap(marketingMessages);
    }

    @Override
    public WhatsAppStore setMarketingMessages(Map<String, MarketingMessageAction> messages) {
        this.marketingMessages = new ConcurrentHashMap<>(Objects.requireNonNull(messages, "messages cannot be null"));
        return this;
    }

    @Override
    public Map<String, String> marketingMessageBroadcasts() {
        return Collections.unmodifiableMap(marketingMessageBroadcasts);
    }

    @Override
    public WhatsAppStore setMarketingMessageBroadcasts(Map<String, String> broadcasts) {
        this.marketingMessageBroadcasts = new ConcurrentHashMap<>(Objects.requireNonNull(broadcasts, "broadcasts cannot be null"));
        return this;
    }

    @Override
    public Map<String, BusinessBroadcastListAction> businessBroadcastLists() {
        return Collections.unmodifiableMap(businessBroadcastLists);
    }

    @Override
    public WhatsAppStore setBusinessBroadcastLists(Map<String, BusinessBroadcastListAction> lists) {
        this.businessBroadcastLists = new ConcurrentHashMap<>(Objects.requireNonNull(lists, "lists cannot be null"));
        return this;
    }

    /**
     * Returns the JIDs of every stored business broadcast list.
     *
     * <p>Projects the stored broadcast list identifiers into JIDs on the
     * broadcast server so callers can address the broadcast targets directly.
     *
     * @return a snapshot list of broadcast list JIDs; empty if no broadcast
     *         lists are known
     *
     * @implNote WAWebBroadcastListStorageUtils.getAllBroadcastLists
     */
    @Override
    public SequencedCollection<Jid> broadcasts() {
        return businessBroadcastLists.keySet().stream()
                .map(id -> Jid.of(id, com.github.auties00.cobalt.model.jid.JidServer.broadcast())) // ADAPTED: map stored string ids to broadcast-server JIDs
                .toList();
    }

    @Override
    public Map<String, BusinessBroadcastCampaignAction> businessBroadcastCampaigns() {
        return Collections.unmodifiableMap(businessBroadcastCampaigns);
    }

    @Override
    public WhatsAppStore setBusinessBroadcastCampaigns(Map<String, BusinessBroadcastCampaignAction> campaigns) {
        this.businessBroadcastCampaigns = new ConcurrentHashMap<>(Objects.requireNonNull(campaigns, "campaigns cannot be null"));
        return this;
    }

    @Override
    public Map<String, BusinessBroadcastInsightsAction> businessBroadcastInsights() {
        return Collections.unmodifiableMap(businessBroadcastInsights);
    }

    @Override
    public WhatsAppStore setBusinessBroadcastInsights(Map<String, BusinessBroadcastInsightsAction> insights) {
        this.businessBroadcastInsights = new ConcurrentHashMap<>(Objects.requireNonNull(insights, "insights cannot be null"));
        return this;
    }

    @Override
    public Optional<byte[]> nctSalt() {
        return Optional.ofNullable(nctSalt);
    }

    @Override
    public WhatsAppStore setNctSalt(byte[] salt) {
        this.nctSalt = salt;
        return this;
    }

    @Override
    public Map<String, Boolean> nuxStates() {
        return Collections.unmodifiableMap(nuxStates);
    }

    @Override
    public WhatsAppStore setNuxStates(Map<String, Boolean> states) {
        this.nuxStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Optional<com.github.auties00.cobalt.model.device.DeviceCapabilities> primaryDeviceCapabilities() {
        return Optional.ofNullable(primaryDeviceCapabilities);
    }

    @Override
    public WhatsAppStore setPrimaryDeviceCapabilities(com.github.auties00.cobalt.model.device.DeviceCapabilities capabilities) {
        this.primaryDeviceCapabilities = capabilities;
        return this;
    }

    @Override
    public Map<String, com.github.auties00.cobalt.model.device.DeviceCapabilities> deviceCapabilitiesStates() {
        return Collections.unmodifiableMap(deviceCapabilitiesStates);
    }

    @Override
    public WhatsAppStore setDeviceCapabilitiesStates(
            Map<String, com.github.auties00.cobalt.model.device.DeviceCapabilities> states
    ) {
        this.deviceCapabilitiesStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction> interactiveMessageStates() {
        return Collections.unmodifiableMap(interactiveMessageStates);
    }

    @Override
    public WhatsAppStore setInteractiveMessageStates(
            Map<String, com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction> states
    ) {
        this.interactiveMessageStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, com.github.auties00.cobalt.model.sync.action.media.NoteEditAction> noteStates() {
        return Collections.unmodifiableMap(noteStates);
    }

    @Override
    public WhatsAppStore setNoteStates(Map<String, com.github.auties00.cobalt.model.sync.action.media.NoteEditAction> states) {
        this.noteStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, Instant> newsletterPinStates() {
        return Collections.unmodifiableMap(newsletterPinStates);
    }

    @Override
    public WhatsAppStore setNewsletterPinStates(Map<String, Instant> states) {
        this.newsletterPinStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
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
    public Map<String, com.github.auties00.cobalt.model.call.CallLog> callLogStates() {
        return Collections.unmodifiableMap(callLogStates);
    }

    @Override
    public WhatsAppStore setCallLogStates(Map<String, com.github.auties00.cobalt.model.call.CallLog> states) {
        this.callLogStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, Boolean> botWelcomeRequestStates() {
        return Collections.unmodifiableMap(botWelcomeRequestStates);
    }

    @Override
    public WhatsAppStore setBotWelcomeRequestStates(Map<String, Boolean> states) {
        this.botWelcomeRequestStates = new ConcurrentHashMap<>(Objects.requireNonNull(states, "states cannot be null"));
        return this;
    }

    @Override
    public Map<String, String> aiThreadTitles() {
        return Collections.unmodifiableMap(aiThreadTitles);
    }

    @Override
    public WhatsAppStore setAiThreadTitles(Map<String, String> titles) {
        this.aiThreadTitles = new ConcurrentHashMap<>(Objects.requireNonNull(titles, "titles cannot be null"));
        return this;
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
    public Optional<String> wamoUserIdentifier() {
        return Optional.ofNullable(wamoUserIdentifier);
    }

    @Override
    public WhatsAppStore setWamoUserIdentifier(String identifier) {
        this.wamoUserIdentifier = identifier;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.MusicUserIdAction
     */
    @Override
    public Optional<MusicUserIdAction> musicUserIdState() {
        return Optional.ofNullable(musicUserIdState);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.MusicUserIdAction
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.PrivacySettingChannelsPersonalisedRecommendationAction
     */
    @Override
    public Optional<Boolean> channelsPersonalisedRecommendationOptOut() {
        return Optional.ofNullable(channelsPersonalisedRecommendationOptOut);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.PrivacySettingChannelsPersonalisedRecommendationAction
     */
    @Override
    public WhatsAppStore setChannelsPersonalisedRecommendationOptOut(Boolean optOut) {
        this.channelsPersonalisedRecommendationOptOut = optOut;
        return this;
    }

    @Override
    public Optional<byte[]> ugcBotDefinition() {
        return Optional.ofNullable(ugcBotDefinition);
    }

    @Override
    public WhatsAppStore setUgcBotDefinition(byte[] definition) {
        this.ugcBotDefinition = definition;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.MaibaAIFeaturesControlAction
     */
    @Override
    public Optional<MaibaAIFeaturesControlAction.MaibaAIFeatureStatus> maibaAiFeatureStatus() {
        return Optional.ofNullable(maibaAiFeatureStatus);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebProtobufSyncAction.pb.MaibaAIFeaturesControlAction
     */
    @Override
    public WhatsAppStore setMaibaAiFeatureStatus(MaibaAIFeaturesControlAction.MaibaAIFeatureStatus status) {
        this.maibaAiFeatureStatus = status;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebUserPrefsMultiDevice.getPairingTimestamp
     */
    @Override
    public Optional<Instant> pairingTimestamp() {
        return Optional.ofNullable(pairingTimestamp);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebUserPrefsMultiDevice.getPairingTimestamp
     */
    @Override
    public WhatsAppStore setPairingTimestamp(Instant pairingTimestamp) {
        this.pairingTimestamp = pairingTimestamp;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebUpdateClockSkewUtils.updateClockSkew
     */
    @Override
    public long clockSkewSeconds() { // WAWebUpdateClockSkewUtils.updateClockSkew
        return clockSkewSeconds;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebUpdateClockSkewUtils.updateClockSkew
     */
    @Override
    public WhatsAppStore setClockSkewSeconds(long clockSkewSeconds) { // WAWebUpdateClockSkewUtils.updateClockSkew - WATimeUtils.setClockSkew(n)
        this.clockSkewSeconds = clockSkewSeconds;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     */
    @Override
    public Optional<Instant> groupAbPropsEmergencyPushTimestamp() { // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
        return Optional.ofNullable(groupAbPropsEmergencyPushTimestamp);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
     */
    @Override
    public WhatsAppStore setGroupAbPropsEmergencyPushTimestamp(Instant timestamp) { // WAWebABPropsLocalStorage.setGroupAbPropsEmergencyPushTimestamp
        this.groupAbPropsEmergencyPushTimestamp = timestamp;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.getABKey
     */
    @Override
    public Optional<String> abPropsAbKey() { // WAWebABPropsLocalStorage.getABKey
        return Optional.ofNullable(abPropsAbKey);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @Override
    public WhatsAppStore setAbPropsAbKey(String abKey) { // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsAbKey = abKey;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.getHash
     */
    @Override
    public Optional<String> abPropsHash() { // WAWebABPropsLocalStorage.getHash
        return Optional.ofNullable(abPropsHash);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @Override
    public WhatsAppStore setAbPropsHash(String hash) { // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsHash = hash;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the persisted refresh interval, or the JS-side
     * {@code parseInt(86400, 10)} default when no value has been recorded.
     *
     * @implNote WAWebABPropsLocalStorage.getRefresh
     */
    @Override
    public long abPropsRefresh() { // WAWebABPropsLocalStorage.getRefresh
        return abPropsRefresh != 0L ? abPropsRefresh : 86400L;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The refresh value is clamped into the {@code [600, 604800]}
     * inclusive range, matching the bounds checked by the JS export.
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @Override
    public WhatsAppStore setAbPropsRefresh(long refreshSeconds) { // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        var clamped = refreshSeconds;
        if (clamped < 600L) {
            clamped = 600L;
        } else if (clamped > 604800L) {
            clamped = 604800L;
        }
        this.abPropsRefresh = clamped;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @Override
    public Optional<Instant> abPropsLastSyncTime() { // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        return Optional.ofNullable(abPropsLastSyncTime);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.updateAttributesLocalStorage
     */
    @Override
    public WhatsAppStore setAbPropsLastSyncTime(Instant lastSyncTime) { // WAWebABPropsLocalStorage.updateAttributesLocalStorage
        this.abPropsLastSyncTime = lastSyncTime;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.getRefreshId
     */
    @Override
    public long abPropsRefreshId() { // WAWebABPropsLocalStorage.getRefreshId
        return abPropsRefreshId;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.setRefreshId
     */
    @Override
    public WhatsAppStore setAbPropsRefreshId(long refreshId) { // WAWebABPropsLocalStorage.setRefreshId
        this.abPropsRefreshId = refreshId;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.getWebRefreshId
     */
    @Override
    public long abPropsWebRefreshId() { // WAWebABPropsLocalStorage.getWebRefreshId
        return abPropsWebRefreshId;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.setWebRefreshId
     */
    @Override
    public WhatsAppStore setAbPropsWebRefreshId(long webRefreshId) { // WAWebABPropsLocalStorage.setWebRefreshId
        this.abPropsWebRefreshId = webRefreshId;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.getGroupAbPropsRefreshId
     */
    @Override
    public long groupAbPropsRefreshId() { // WAWebABPropsLocalStorage.getGroupAbPropsRefreshId
        return groupAbPropsRefreshId;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
     */
    @Override
    public WhatsAppStore setGroupAbPropsRefreshId(long groupRefreshId) { // WAWebABPropsLocalStorage.setGroupAbPropsRefreshId
        this.groupAbPropsRefreshId = groupRefreshId;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote WAWebRecentStickerCollectionMd.removeAllRecentAvatarStickers
     */
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
                            && Objects.equals(baseKeys, that.baseKeys);
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
                deviceLists, unconfirmedIdentityChanges, coexHostedVerificationCache, utmReadChatIds, pendingDeviceSyncs, groupSenderKeyDistribution,
                disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures,
                mentionEveryoneMuteExpirations, baseKeys);
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
