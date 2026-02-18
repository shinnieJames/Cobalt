
package com.github.auties00.cobalt.store.db;

import com.github.auties00.cobalt.client.*;
import com.github.auties00.cobalt.client.info.WhatsAppClientInfo;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentity;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.auth.UserAgent.ReleaseChannel;
import com.github.auties00.cobalt.model.auth.Version;
import com.github.auties00.cobalt.model.auth.VersionSpec;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMetadata;
import com.github.auties00.cobalt.model.chat.community.CommunityMetadata;
import com.github.auties00.cobalt.model.community.CommunityMetadataSpec;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.group.GroupMetadataSpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.business.BusinessCategorySpec;
import com.github.auties00.cobalt.model.business.VerifiedBusinessName;
import com.github.auties00.cobalt.model.business.VerifiedBusinessNameSpec;
import com.github.auties00.cobalt.model.call.CallSpec;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.contact.ContactBuilder;
import com.github.auties00.cobalt.model.contact.ContactSpec;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.device.MissingDeviceSyncKeySpec;
import com.github.auties00.cobalt.model.device.sync.PendingDeviceSync;
import com.github.auties00.cobalt.model.info.*;
import com.github.auties00.cobalt.model.message.ChatMessageKey;
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterBuilder;
import com.github.auties00.cobalt.model.newsletter.NewsletterSpec;
import com.github.auties00.cobalt.model.preference.Label;
import com.github.auties00.cobalt.model.preference.QuickReply;
import com.github.auties00.cobalt.model.preference.Sticker;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntrySpec;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.sync.crypto.MutationLTHash;
import com.github.auties00.cobalt.util.SecureBytes;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecordSpec;
import com.github.auties00.libsignal.key.*;
import com.github.auties00.libsignal.state.SignalSessionRecord;
import com.github.auties00.libsignal.state.SignalSessionRecordSpec;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class DatabaseWhatsAppStore implements WhatsAppStore {
    private static final Table<?> T_CHATS = DSL.table("chats");
    private static final Table<?> T_MESSAGES = DSL.table("messages");
    private static final Table<?> T_CONTACTS = DSL.table("contacts");
    private static final Table<?> T_NEWSLETTERS = DSL.table("newsletters");
    private static final Table<?> T_NL_MESSAGES = DSL.table("newsletter_messages");
    private static final Table<?> T_CALLS = DSL.table("calls");
    private static final Table<?> T_STATUS = DSL.table("status_messages");
    private static final Table<?> T_PRIVACY = DSL.table("privacy_settings");
    private static final Table<?> T_STICKERS_R = DSL.table("stickers_recent");
    private static final Table<?> T_STICKERS_F = DSL.table("stickers_favourite");
    private static final Table<?> T_REPLIES = DSL.table("quick_replies");
    private static final Table<?> T_LABELS = DSL.table("labels");
    private static final Table<?> T_ASK = DSL.table("app_state_keys");
    private static final Table<?> T_HASH = DSL.table("hash_states");
    private static final Table<?> T_MSK = DSL.table("missing_sync_keys");
    private static final Table<?> T_VBN = DSL.table("verified_business_names");
    private static final Table<?> T_GROUPS = DSL.table("group_metadata");
    
    private static final Table<?> T_PROPS = DSL.table("session_props");
    private static final Table<?> T_SIGNAL_SESSIONS = DSL.table("signal_sessions");
    private static final Table<?> T_SIGNAL_PRE_KEYS = DSL.table("signal_pre_keys");
    private static final Table<?> T_SIGNAL_SENDER_KEYS = DSL.table("signal_sender_keys");
    private static final Table<?> T_REMOTE_IDENTITIES = DSL.table("remote_identities");
    private static final Table<?> T_LID_MAPPINGS = DSL.table("lid_mappings");
    private static final Table<?> T_DEVICE_IDENTITY_RANGES = DSL.table("device_identity_ranges");
    private static final Table<?> T_PROPERTIES_MAP = DSL.table("properties_map");
    
    private static final Field<String> JID = DSL.field("jid", String.class);
    private static final Field<String> CHAT_JID = DSL.field("chat_jid", String.class);
    private static final Field<String> NL_JID = DSL.field("nl_jid", String.class);
    private static final Field<String> ID = DSL.field("id", String.class);
    private static final Field<String> MSG_ID = DSL.field("msg_id", String.class);
    private static final Field<byte[]> DATA = DSL.field("data", byte[].class);
    private static final Field<String> HASH = DSL.field("hash", String.class);
    private static final Field<String> SHORTCUT = DSL.field("shortcut", String.class);
    private static final Field<Integer> LABEL_ID = DSL.field("label_id", Integer.class);
    private static final Field<String> TYPE = DSL.field("type", String.class);

    private static final Field<String> PROP_KEY = DSL.field("key", String.class);
    private static final Field<byte[]> PROP_VALUE = DSL.field("value", byte[].class);
    private static final Field<String> ADDRESS = DSL.field("address", String.class);
    private static final Field<Integer> PRE_KEY_ID = DSL.field("id", Integer.class);
    private static final Field<String> SK_NAME = DSL.field("name", String.class);
    private static final Field<String> PHONE = DSL.field("phone", String.class);
    private static final Field<String> LID = DSL.field("lid", String.class);
    private static final Field<Long> TIMESTAMP = DSL.field("timestamp", Long.class);
    private static final Field<String> MAP_KEY = DSL.field("key", String.class);
    private static final Field<String> MAP_VALUE = DSL.field("value", String.class);

    private static final String DEFAULT_NAME = "User";
    private static final int MAX_DEVICE_LISTS = 5000;

    private final DSLContext db;
    private final Connection connection;
    private final String jdbcUrl;

    
    private final UUID uuid;
    private final WhatsAppClientType clientType;
    private final long initializationTimeStamp;
    private final int registrationId;
    private final SignalIdentityKeyPair noiseKeyPair;
    private final SignalIdentityKeyPair identityKeyPair;
    private final SignalSignedKeyPair signedKeyPair;
    private final UUID fdid;
    private final byte[] deviceId;
    private final UUID advertisingId;
    private final byte[] identityId;
    private final byte[] backupToken;
    private Long phoneNumber;
    private JidDevice device;
    private ReleaseChannel releaseChannel;
    private boolean online;
    private String locale;
    private String name;
    private String verifiedName;
    private URI profilePicture;
    private String about;
    private Jid jid;
    private Jid lid;
    private String businessAddress;
    private Double businessLongitude;
    private Double businessLatitude;
    private String businessDescription;
    private String businessWebsite;
    private String businessEmail;
    private BusinessCategory businessCategory;
    private boolean unarchiveChats;
    private boolean twentyFourHourFormat;
    private ChatEphemeralTimer newChatsEphemeralTimer;
    private WhatsAppWebClientHistory webHistoryPolicy;
    private boolean automaticPresenceUpdates;
    private boolean automaticMessageReceipts;
    private boolean checkPatchMacs;
    private boolean syncedChats;
    private boolean syncedContacts;
    private boolean syncedNewsletters;
    private boolean syncedStatus;
    private boolean syncedWebAppState;
    private boolean syncedBusinessCertificate;
    private boolean registered;
    private boolean showSecurityNotifications;
    private SignalIdentityKeyPair companionKeyPair;
    private SignedDeviceIdentity signedDeviceIdentity;
    private byte[] advSecretKey;
    private volatile Version clientVersion;
    private Version companionVersion;
    private Instant lastAdvCheckTime;
    private final ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions = new ConcurrentHashMap<>();
    private final LinkedHashMap<Integer, SignalPreKeyPair> preKeys = new LinkedHashMap<>();
    private final ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Jid, Jid> lidToPhoneMappings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Jid, Jid> phoneToLidMappings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> deviceIdentityRanges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> propertiesMap = new ConcurrentHashMap<>();

    
    private final KeySetView<WhatsAppClientListener, Boolean> listeners = ConcurrentHashMap.newKeySet();
    private volatile MediaConnection mediaConnection;
    private final Object mediaConnectionLock = new Object();
    private volatile WhatsAppClientOfflineResumeState offlineResumeState = WhatsAppClientOfflineResumeState.INIT;
    private volatile CountDownLatch offlineDeliveryLatch = new CountDownLatch(1);
    private final Object clientVersionLock = new Object();
    private final AtomicLong encryptionSequence = new AtomicLong();
    private final ConcurrentMap<SignalProtocolAddress, Long> identityEncryptionRange = new ConcurrentHashMap<>();
    private final ConcurrentMap<PatchType, CollectionMetadata> webAppStateCollections = new ConcurrentHashMap<>();
    private final ConcurrentMap<PatchType, SequencedCollection<PendingMutation>> webAppStatePendingMutations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Jid>> pendingMessageRecipients = new ConcurrentHashMap<>();
    private final KeySetView<String, Boolean> revokedMessageIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Set<String>> groupSenderKeyDistribution = new ConcurrentHashMap<>();
    private final KeySetView<Jid, Boolean> usersNeedingSenderKeyRotation = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Jid, Long> offlineDeviceTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<Jid, DeviceList> deviceLists = new ConcurrentHashMap<>();
    private final LinkedList<Jid> deviceListsAccessOrder = new LinkedList<>();
    private final Set<Jid> unconfirmedIdentityChanges = ConcurrentHashMap.newKeySet();
    private final Set<Jid> coexHostedVerificationCache = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<PendingDeviceSync> pendingDeviceSyncs = new ConcurrentLinkedQueue<>();
    private WhatsAppClientProxy proxy;
    

    private DatabaseWhatsAppStore(
            String jdbcUrl,
            UUID uuid,
            WhatsAppClientType clientType,
            long initializationTimeStamp,
            int registrationId,
            SignalIdentityKeyPair noiseKeyPair,
            SignalIdentityKeyPair identityKeyPair,
            SignalSignedKeyPair signedKeyPair,
            UUID fdid,
            byte[] deviceId,
            UUID advertisingId,
            byte[] identityId,
            byte[] backupToken,
            JidDevice device
    ) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl);
        this.uuid = Objects.requireNonNull(uuid);
        this.clientType = Objects.requireNonNull(clientType);
        this.initializationTimeStamp = initializationTimeStamp;
        this.registrationId = registrationId;
        this.noiseKeyPair = Objects.requireNonNull(noiseKeyPair);
        this.identityKeyPair = Objects.requireNonNull(identityKeyPair);
        this.signedKeyPair = Objects.requireNonNull(signedKeyPair);
        this.fdid = Objects.requireNonNull(fdid);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.advertisingId = Objects.requireNonNull(advertisingId);
        this.identityId = Objects.requireNonNull(identityId);
        this.backupToken = Objects.requireNonNull(backupToken);
        this.device = Objects.requireNonNull(device);
        this.releaseChannel = ReleaseChannel.RELEASE;
        this.newChatsEphemeralTimer = ChatEphemeralTimer.OFF;
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            var dialect = JDBCUtils.dialect(jdbcUrl);
            this.db = DSL.using(connection, dialect);
            initSchema(dialect);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot open database: " + jdbcUrl, e);
        }
    }

    private void initSchema(SQLDialect dialect) {
        if (dialect == SQLDialect.SQLITE) {
            db.execute("PRAGMA journal_mode=WAL");
            db.execute("PRAGMA synchronous=NORMAL");
        }

        // Collection tables
        db.createTableIfNotExists(T_CHATS)
                .column(JID)
                .column(DATA)
                .constraint(DSL.primaryKey(JID))
                .execute();

        db.createTableIfNotExists(T_MESSAGES)
                .column(CHAT_JID)
                .column(MSG_ID)
                .column(DATA)
                .constraint(DSL.primaryKey(MSG_ID))
                .execute();

        db.createTableIfNotExists(T_CONTACTS)
                .column(JID)
                .column(DATA)
                .constraint(DSL.primaryKey(JID))
                .execute();

        db.createTableIfNotExists(T_NEWSLETTERS)
                .column(JID)
                .column(DATA)
                .constraint(DSL.primaryKey(JID))
                .execute();

        db.createTableIfNotExists(T_NL_MESSAGES)
                .column(NL_JID)
                .column(MSG_ID)
                .column(DATA)
                .constraint(DSL.primaryKey(NL_JID, MSG_ID))
                .execute();

        db.createTableIfNotExists(T_CALLS)
                .column(ID)
                .column(DATA)
                .constraint(DSL.primaryKey(ID))
                .execute();

        db.createTableIfNotExists(T_STATUS)
                .column(ID)
                .column(DATA)
                .constraint(DSL.primaryKey(ID))
                .execute();

        db.createTableIfNotExists(T_PRIVACY)
                .column(TYPE)
                .column(DATA)
                .constraint(DSL.primaryKey(TYPE))
                .execute();

        db.createTableIfNotExists(T_STICKERS_R)
                .column(HASH)
                .column(DATA)
                .constraint(DSL.primaryKey(HASH))
                .execute();

        db.createTableIfNotExists(T_STICKERS_F)
                .column(HASH)
                .column(DATA)
                .constraint(DSL.primaryKey(HASH))
                .execute();

        db.createTableIfNotExists(T_REPLIES)
                .column(SHORTCUT)
                .column(DATA)
                .constraint(DSL.primaryKey(SHORTCUT))
                .execute();

        db.createTableIfNotExists(T_LABELS)
                .column(LABEL_ID)
                .column(DATA)
                .constraint(DSL.primaryKey(LABEL_ID))
                .execute();

        db.createTableIfNotExists(T_ASK)
                .column(ID)
                .column(DATA)
                .constraint(DSL.primaryKey(ID))
                .execute();

        db.createTableIfNotExists(T_HASH)
                .column(TYPE)
                .column(DATA)
                .constraint(DSL.primaryKey(TYPE))
                .execute();

        db.createTableIfNotExists(T_MSK)
                .column(ID)
                .column(DATA)
                .constraint(DSL.primaryKey(ID))
                .execute();

        db.createTableIfNotExists(T_VBN)
                .column(JID)
                .column(DATA)
                .constraint(DSL.primaryKey(JID))
                .execute();

        db.createTableIfNotExists(T_GROUPS)
                .column(JID)
                .column(DATA)
                .constraint(DSL.primaryKey(JID))
                .execute();

        // Session state tables
        db.createTableIfNotExists(T_PROPS)
                .column(MAP_KEY)
                .column(MAP_VALUE)
                .constraint(DSL.primaryKey(MAP_KEY))
                .execute();

        db.createTableIfNotExists(T_SIGNAL_SESSIONS)
                .column(ADDRESS)
                .column(DATA)
                .constraint(DSL.primaryKey(ADDRESS))
                .execute();

        db.createTableIfNotExists(T_SIGNAL_PRE_KEYS)
                .column(ID)
                .column(DATA)
                .constraint(DSL.primaryKey(ID))
                .execute();

        db.createTableIfNotExists(T_SIGNAL_SENDER_KEYS)
                .column(SK_NAME)
                .column(DATA)
                .constraint(DSL.primaryKey(SK_NAME))
                .execute();

        db.createTableIfNotExists(T_REMOTE_IDENTITIES)
                .column(ADDRESS)
                .column(DATA)
                .constraint(DSL.primaryKey(ADDRESS))
                .execute();

        db.createTableIfNotExists(T_LID_MAPPINGS)
                .column(PHONE)
                .column(LID)
                .constraint(DSL.primaryKey(PHONE))
                .execute();

        db.createTableIfNotExists(T_DEVICE_IDENTITY_RANGES)
                .column(ADDRESS)
                .column(TIMESTAMP)
                .constraint(DSL.primaryKey(ADDRESS))
                .execute();

        db.createTableIfNotExists(T_PROPERTIES_MAP)
                .column(MAP_KEY)
                .column(MAP_VALUE)
                .constraint(DSL.primaryKey(MAP_KEY))
                .execute();
    }

    

    private void putString(String key, String value) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, bytes)
                    .onDuplicateKeyUpdate().set(PROP_VALUE, bytes).execute();
        }
    }

    private String getString(String key, String def) {
        return getString(db, key, def);
    }

    private static String getString(DSLContext db, String key, String def) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        return row == null || row.value1() == null ? def : new String(row.value1(), StandardCharsets.UTF_8);
    }

    private void putLong(String key, Long value) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            var bytes = ByteBuffer.allocate(8).putLong(value).array();
            db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, bytes)
                    .onDuplicateKeyUpdate().set(PROP_VALUE, bytes).execute();
        }
    }

    private Long getLong(String key) {
        return getLong(db, key);
    }

    private static Long getLong(DSLContext db, String key) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        if (row == null || row.value1() == null || row.value1().length != 8) return null;
        return ByteBuffer.wrap(row.value1()).getLong();
    }

    private void putInt(String key, Integer value) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            var bytes = ByteBuffer.allocate(4).putInt(value).array();
            db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, bytes)
                    .onDuplicateKeyUpdate().set(PROP_VALUE, bytes).execute();
        }
    }

    private Integer getInt(String key) {
        return getInt(db, key);
    }

    private static Integer getInt(DSLContext db, String key) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        if (row == null || row.value1() == null || row.value1().length != 4) return null;
        return ByteBuffer.wrap(row.value1()).getInt();
    }

    private void putBool(String key, boolean value) {
        var bytes = new byte[]{(byte) (value ? 1 : 0)};
        db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, bytes)
                .onDuplicateKeyUpdate().set(PROP_VALUE, bytes).execute();
    }

    private boolean getBool(String key, boolean def) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        if (row == null || row.value1() == null || row.value1().length != 1) return def;
        return row.value1()[0] != 0;
    }

    private void putDouble(String key, Double value) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            var bytes = ByteBuffer.allocate(8).putDouble(value).array();
            db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, bytes)
                    .onDuplicateKeyUpdate().set(PROP_VALUE, bytes).execute();
        }
    }

    private Double getDouble(String key) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        if (row == null || row.value1() == null || row.value1().length != 8) return null;
        return ByteBuffer.wrap(row.value1()).getDouble();
    }

    private void putBytes(String key, byte[] value) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            db.insertInto(T_PROPS).set(PROP_KEY, key).set(PROP_VALUE, value)
                    .onDuplicateKeyUpdate().set(PROP_VALUE, value).execute();
        }
    }

    private byte[] getBytes(String key) {
        return getBytes(db, key);
    }

    private static byte[] getBytes(DSLContext db, String key) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        return row == null ? null : row.value1();
    }

    private <T> void putProto(String key, T value, BiConsumer<T, ProtobufOutputStream> encoder) {
        if (value == null) {
            db.deleteFrom(T_PROPS).where(PROP_KEY.eq(key)).execute();
        } else {
            var bytes = encode(value, encoder);
            db.insertInto(T_PROPS)
                    .set(PROP_KEY, key)
                    .set(PROP_VALUE, bytes)
                    .onDuplicateKeyUpdate()
                        .set(PROP_VALUE, bytes)
                    .execute();
        }
    }

    private <T> T getProto(String key, Function<ProtobufInputStream, T> decoder) {
        return getProto(db, key, decoder);
    }

    private static <T> T getProto(DSLContext db, String key, Function<ProtobufInputStream, T> decoder) {
        var row = db.select(PROP_VALUE).from(T_PROPS).where(PROP_KEY.eq(key)).fetchOne();
        if (row == null || row.value1() == null) return null;
        return decoder.apply(ProtobufInputStream.fromBytes(row.value1()));
    }

    

    private void persistAllImmutableProps() {
        putString("uuid", uuid.toString());
        putString("clientType", clientType.name());
        putLong("initializationTimeStamp", initializationTimeStamp);
        putInt("registrationId", registrationId);
        putProto("noiseKeyPair", noiseKeyPair, SignalIdentityKeyPairSpec::encode);
        putProto("identityKeyPair", identityKeyPair, SignalIdentityKeyPairSpec::encode);
        putProto("signedKeyPair", signedKeyPair, SignalSignedKeyPairSpec::encode);
        putString("fdid", fdid.toString());
        putBytes("deviceId", deviceId);
        putString("advertisingId", advertisingId.toString());
        putBytes("identityId", identityId);
        putBytes("backupToken", backupToken);
    }

    private void persistAllMutableProps() {
        putLong("phoneNumber", phoneNumber);
        putProto("device", device, JidDeviceSpec::encode);
        putString("releaseChannel", releaseChannel != null ? releaseChannel.name() : null);
        putBool("online", online);
        putString("locale", locale);
        putString("name", name);
        putString("verifiedName", verifiedName);
        putString("profilePicture", profilePicture != null ? profilePicture.toString() : null);
        putString("about", about);
        putString("jid", jid != null ? jid.toString() : null);
        putString("lid", lid != null ? lid.toString() : null);
        putString("businessAddress", businessAddress);
        putDouble("businessLongitude", businessLongitude);
        putDouble("businessLatitude", businessLatitude);
        putString("businessDescription", businessDescription);
        putString("businessWebsite", businessWebsite);
        putString("businessEmail", businessEmail);
        putProto("businessCategory", businessCategory, BusinessCategorySpec::encode);
        putBool("unarchiveChats", unarchiveChats);
        putBool("twentyFourHourFormat", twentyFourHourFormat);
        putString("newChatsEphemeralTimer", newChatsEphemeralTimer != null ? newChatsEphemeralTimer.name() : null);
        putProto("webHistoryPolicy", webHistoryPolicy, WhatsAppWebClientHistorySpec::encode);
        putBool("automaticPresenceUpdates", automaticPresenceUpdates);
        putBool("automaticMessageReceipts", automaticMessageReceipts);
        putBool("checkPatchMacs", checkPatchMacs);
        putBool("syncedChats", syncedChats);
        putBool("syncedContacts", syncedContacts);
        putBool("syncedNewsletters", syncedNewsletters);
        putBool("syncedStatus", syncedStatus);
        putBool("syncedWebAppState", syncedWebAppState);
        putBool("syncedBusinessCertificate", syncedBusinessCertificate);
        putBool("registered", registered);
        putBool("showSecurityNotifications", showSecurityNotifications);
        putProto("companionKeyPair", companionKeyPair, SignalIdentityKeyPairSpec::encode);
        putProto("signedDeviceIdentity", signedDeviceIdentity, SignedDeviceIdentitySpec::encode);
        putBytes("advSecretKey", advSecretKey);
        putProto("clientVersion", clientVersion, VersionSpec::encode);
        putProto("companionVersion", companionVersion, VersionSpec::encode);
        putLong("lastAdvCheckTime", lastAdvCheckTime != null ? lastAdvCheckTime.toEpochMilli() : null);
    }

    private void loadMutableProps() {
        this.phoneNumber = getLong("phoneNumber");
        var dev = getProto("device", JidDeviceSpec::decode);
        if (dev != null) this.device = dev;
        var rc = getString("releaseChannel", null);
        if (rc != null) { try { this.releaseChannel = ReleaseChannel.valueOf(rc); } catch (IllegalArgumentException ignored) {} }
        this.online = getBool("online", false);
        this.locale = getString("locale", null);
        this.name = getString("name", null);
        this.verifiedName = getString("verifiedName", null);
        var pp = getString("profilePicture", null);
        if (pp != null) { try { this.profilePicture = URI.create(pp); } catch (IllegalArgumentException ignored) {} }
        this.about = getString("about", null);
        var jidStr = getString("jid", null);
        if (jidStr != null) this.jid = Jid.of(jidStr);
        var lidStr = getString("lid", null);
        if (lidStr != null) this.lid = Jid.of(lidStr);
        this.businessAddress = getString("businessAddress", null);
        this.businessLongitude = getDouble("businessLongitude");
        this.businessLatitude = getDouble("businessLatitude");
        this.businessDescription = getString("businessDescription", null);
        this.businessWebsite = getString("businessWebsite", null);
        this.businessEmail = getString("businessEmail", null);
        this.businessCategory = getProto("businessCategory", BusinessCategorySpec::decode);
        this.unarchiveChats = getBool("unarchiveChats", false);
        this.twentyFourHourFormat = getBool("twentyFourHourFormat", false);
        var timer = getString("newChatsEphemeralTimer", null);
        if (timer != null) { try { this.newChatsEphemeralTimer = ChatEphemeralTimer.valueOf(timer); } catch (IllegalArgumentException ignored) {} }
        this.webHistoryPolicy = getProto("webHistoryPolicy", WhatsAppWebClientHistorySpec::decode);
        this.automaticPresenceUpdates = getBool("automaticPresenceUpdates", false);
        this.automaticMessageReceipts = getBool("automaticMessageReceipts", false);
        this.checkPatchMacs = getBool("checkPatchMacs", false);
        this.syncedChats = getBool("syncedChats", false);
        this.syncedContacts = getBool("syncedContacts", false);
        this.syncedNewsletters = getBool("syncedNewsletters", false);
        this.syncedStatus = getBool("syncedStatus", false);
        this.syncedWebAppState = getBool("syncedWebAppState", false);
        this.syncedBusinessCertificate = getBool("syncedBusinessCertificate", false);
        this.registered = getBool("registered", false);
        this.showSecurityNotifications = getBool("showSecurityNotifications", false);
        this.companionKeyPair = getProto("companionKeyPair", SignalIdentityKeyPairSpec::decode);
        this.signedDeviceIdentity = getProto("signedDeviceIdentity", SignedDeviceIdentitySpec::decode);
        this.advSecretKey = getBytes("advSecretKey");
        this.clientVersion = getProto("clientVersion", VersionSpec::decode);
        this.companionVersion = getProto("companionVersion", VersionSpec::decode);
        var advTime = getLong("lastAdvCheckTime");
        if (advTime != null) this.lastAdvCheckTime = Instant.ofEpochMilli(advTime);
    }

    private void loadSignalCaches() {
        // Sessions
        db.select(ADDRESS, DATA).from(T_SIGNAL_SESSIONS).fetch().forEach(r -> {
            var addr = SignalProtocolAddress.of(r.value1());
            var record = SignalSessionRecordSpec.decode(r.value2());
            sessions.put(addr, record);
        });
        // Pre-keys
        db.select(PRE_KEY_ID, DATA).from(T_SIGNAL_PRE_KEYS).fetch().forEach(r -> {
            var pk = SignalPreKeyPairSpec.decode(r.value2());
            preKeys.put(r.value1(), pk);
        });
        // Sender keys
        db.select(SK_NAME, DATA).from(T_SIGNAL_SENDER_KEYS).fetch().forEach(r -> {
            var skName = SignalSenderKeyName.of(r.value1());
            var record = SignalSenderKeyRecordSpec.decode(r.value2());
            senderKeys.put(skName, record);
        });
        // Remote identities
        db.select(ADDRESS, DATA).from(T_REMOTE_IDENTITIES).fetch().forEach(r -> {
            var addr = SignalProtocolAddress.of(r.value1());
            var key = SignalIdentityPublicKey.ofDirect(r.value2());
            remoteIdentities.put(addr, key);
        });
    }

    private void loadLidMappings() {
        db.select(PHONE, LID).from(T_LID_MAPPINGS).fetch().forEach(r -> {
            var phoneJid = Jid.of(r.value1());
            var lidJid = Jid.of(r.value2());
            lidToPhoneMappings.put(lidJid, phoneJid);
            phoneToLidMappings.put(phoneJid, lidJid);
        });
        // Also load from contacts
        queryAll(T_CONTACTS, ContactSpec::decode).forEach(contact ->
                contact.lid().ifPresent(contactLid -> registerLidMapping(contact.jid(), contactLid))
        );
    }

    private void loadDeviceIdentityRanges() {
        db.select(ADDRESS, TIMESTAMP).from(T_DEVICE_IDENTITY_RANGES).fetch().forEach(r ->
                deviceIdentityRanges.put(r.value1(), r.value2())
        );
    }

    private void loadPropertiesMap() {
        db.select(MAP_KEY, MAP_VALUE).from(T_PROPERTIES_MAP).fetch().forEach(r ->
                propertiesMap.put(r.value1(), r.value2())
        );
    }

    

    private <T> byte[] encode(T value, BiConsumer<T, ProtobufOutputStream> encoder) {
        var out = new ByteArrayOutputStream();
        encoder.accept(value, ProtobufOutputStream.toStream(out));
        return out.toByteArray();
    }

    private <T> T decode(byte[] bytes, Function<ProtobufInputStream, T> decoder) {
        return decoder.apply(ProtobufInputStream.fromBytes(bytes));
    }

    private <T> List<T> queryAll(Table<?> table, Function<ProtobufInputStream, T> decoder) {
        return db.select(DATA).from(table).fetch().map(r -> decode(r.value1(), decoder));
    }

    private <T> Optional<T> queryOne(Table<?> table, Condition condition, Function<ProtobufInputStream, T> decoder) {
        var row = db.select(DATA).from(table).where(condition).fetchOne();
        return row == null ? Optional.empty() : Optional.of(decode(row.value1(), decoder));
    }

    private <T> void upsert(Table<?> table, Field<String> pk, String pkValue, T value, BiConsumer<T, ProtobufOutputStream> encoder) {
        var bytes = encode(value, encoder);
        db.insertInto(table).set(pk, pkValue).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes)
                .execute();
    }

    private void upsert(Table<?> table, Field<Integer> pk, int pkValue, byte[] bytes) {
        db.insertInto(table).set(pk, pkValue).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes)
                .execute();
    }

    private boolean deleteWhere(Table<?> table, Condition condition) {
        return db.deleteFrom(table).where(condition).execute() > 0;
    }

    private <T> Optional<T> removeOne(Table<?> table, Condition condition, Function<ProtobufInputStream, T> decoder) {
        var existing = queryOne(table, condition, decoder);
        existing.ifPresent(_ -> deleteWhere(table, condition));
        return existing;
    }

    

    @Override public UUID uuid() { return uuid; }
    @Override public WhatsAppClientType clientType() { return clientType; }
    @Override public long initializationTimeStamp() { return initializationTimeStamp; }
    @Override public int registrationId() { return registrationId; }
    @Override public SignalIdentityKeyPair noiseKeyPair() { return noiseKeyPair; }
    @Override public SignalIdentityKeyPair identityKeyPair() { return identityKeyPair; }
    @Override public SignalSignedKeyPair signedKeyPair() { return signedKeyPair; }
    @Override public UUID fdid() { return fdid; }
    @Override public byte[] deviceId() { return deviceId; }
    @Override public UUID advertisingId() { return advertisingId; }
    @Override public byte[] identityId() { return identityId; }
    @Override public byte[] backupToken() { return backupToken; }

    

    @Override public OptionalLong phoneNumber() { return phoneNumber == null ? OptionalLong.empty() : OptionalLong.of(phoneNumber); }
    @Override public WhatsAppStore setPhoneNumber(Long v) { this.phoneNumber = v; putLong("phoneNumber", v); return this; }

    @Override public JidDevice device() { return device; }
    @Override public WhatsAppStore setDevice(JidDevice v) { this.device = Objects.requireNonNull(v); putProto("device", v, JidDeviceSpec::encode); return this; }

    @Override public ReleaseChannel releaseChannel() { return releaseChannel; }
    @Override public WhatsAppStore setReleaseChannel(ReleaseChannel v) { this.releaseChannel = Objects.requireNonNull(v); putString("releaseChannel", v.name()); return this; }

    @Override public boolean online() { return online; }
    @Override public WhatsAppStore setOnline(boolean v) { this.online = v; putBool("online", v); return this; }

    @Override public Optional<String> locale() { return Optional.ofNullable(locale); }
    @Override public WhatsAppStore setLocale(String v) { this.locale = v; putString("locale", v); return this; }

    @Override public String name() { return Objects.requireNonNullElse(name, DEFAULT_NAME); }
    @Override public WhatsAppStore setName(String v) { this.name = v; putString("name", v); return this; }

    @Override public Optional<String> verifiedName() { return Optional.ofNullable(verifiedName); }
    @Override public WhatsAppStore setVerifiedName(String v) { this.verifiedName = v; putString("verifiedName", v); return this; }

    @Override public Optional<URI> profilePicture() { return Optional.ofNullable(profilePicture); }
    @Override public WhatsAppStore setProfilePicture(URI v) { this.profilePicture = v; putString("profilePicture", v != null ? v.toString() : null); return this; }

    @Override public Optional<String> about() { return Optional.ofNullable(about); }
    @Override public WhatsAppStore setAbout(String v) { this.about = v; putString("about", v); return this; }

    @Override public Optional<Jid> jid() { return Optional.ofNullable(jid); }
    @Override public WhatsAppStore setJid(Jid v) { this.jid = v; putString("jid", v != null ? v.toString() : null); return this; }

    @Override public Optional<Jid> lid() { return Optional.ofNullable(lid); }
    @Override public WhatsAppStore setLid(Jid v) { this.lid = v; putString("lid", v != null ? v.toString() : null); return this; }

    @Override public Optional<String> businessAddress() { return Optional.ofNullable(businessAddress); }
    @Override public WhatsAppStore setBusinessAddress(String v) { this.businessAddress = v; putString("businessAddress", v); return this; }

    @Override public OptionalDouble businessLongitude() { return businessLongitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLongitude); }
    @Override public WhatsAppStore setBusinessLongitude(Double v) { this.businessLongitude = v; putDouble("businessLongitude", v); return this; }

    @Override public OptionalDouble businessLatitude() { return businessLatitude == null ? OptionalDouble.empty() : OptionalDouble.of(businessLatitude); }
    @Override public WhatsAppStore setBusinessLatitude(Double v) { this.businessLatitude = v; putDouble("businessLatitude", v); return this; }

    @Override public Optional<String> businessDescription() { return Optional.ofNullable(businessDescription); }
    @Override public WhatsAppStore setBusinessDescription(String v) { this.businessDescription = v; putString("businessDescription", v); return this; }

    @Override public Optional<String> businessWebsite() { return Optional.ofNullable(businessWebsite); }
    @Override public WhatsAppStore setBusinessWebsite(String v) { this.businessWebsite = v; putString("businessWebsite", v); return this; }

    @Override public Optional<String> businessEmail() { return Optional.ofNullable(businessEmail); }
    @Override public WhatsAppStore setBusinessEmail(String v) { this.businessEmail = v; putString("businessEmail", v); return this; }

    @Override public Optional<BusinessCategory> businessCategory() { return Optional.ofNullable(businessCategory); }
    @Override public WhatsAppStore setBusinessCategory(BusinessCategory v) { this.businessCategory = v; putProto("businessCategory", v, BusinessCategorySpec::encode); return this; }

    @Override public Map<String, String> properties() { return Collections.unmodifiableMap(propertiesMap); }

    @Override public boolean unarchiveChats() { return unarchiveChats; }
    @Override public WhatsAppStore setUnarchiveChats(boolean v) { this.unarchiveChats = v; putBool("unarchiveChats", v); return this; }

    @Override public boolean twentyFourHourFormat() { return twentyFourHourFormat; }
    @Override public WhatsAppStore setTwentyFourHourFormat(boolean v) { this.twentyFourHourFormat = v; putBool("twentyFourHourFormat", v); return this; }

    @Override public ChatEphemeralTimer newChatsEphemeralTimer() { return newChatsEphemeralTimer; }
    @Override public WhatsAppStore setNewChatsEphemeralTimer(ChatEphemeralTimer v) { this.newChatsEphemeralTimer = Objects.requireNonNull(v); putString("newChatsEphemeralTimer", v.name()); return this; }

    @Override public Optional<WhatsAppWebClientHistory> webHistoryPolicy() { return Optional.ofNullable(webHistoryPolicy); }
    @Override public WhatsAppStore setWebHistoryPolicy(WhatsAppWebClientHistory v) { this.webHistoryPolicy = v; putProto("webHistoryPolicy", v, WhatsAppWebClientHistorySpec::encode); return this; }

    @Override public boolean automaticPresenceUpdates() { return automaticPresenceUpdates; }
    @Override public WhatsAppStore setAutomaticPresenceUpdates(boolean v) { this.automaticPresenceUpdates = v; putBool("automaticPresenceUpdates", v); return this; }

    @Override public boolean automaticMessageReceipts() { return automaticMessageReceipts; }
    @Override public WhatsAppStore setAutomaticMessageReceipts(boolean v) { this.automaticMessageReceipts = v; putBool("automaticMessageReceipts", v); return this; }

    @Override public boolean checkPatchMacs() { return checkPatchMacs; }
    @Override public WhatsAppStore setCheckPatchMacs(boolean v) { this.checkPatchMacs = v; putBool("checkPatchMacs", v); return this; }

    @Override public boolean syncedChats() { return syncedChats; }
    @Override public WhatsAppStore setSyncedChats(boolean v) { this.syncedChats = v; putBool("syncedChats", v); return this; }

    @Override public boolean syncedContacts() { return syncedContacts; }
    @Override public WhatsAppStore setSyncedContacts(boolean v) { this.syncedContacts = v; putBool("syncedContacts", v); return this; }

    @Override public boolean syncedNewsletters() { return syncedNewsletters; }
    @Override public WhatsAppStore setSyncedNewsletters(boolean v) { this.syncedNewsletters = v; putBool("syncedNewsletters", v); return this; }

    @Override public boolean syncedStatus() { return syncedStatus; }
    @Override public WhatsAppStore setSyncedStatus(boolean v) { this.syncedStatus = v; putBool("syncedStatus", v); return this; }

    @Override public boolean syncedWebAppState() { return syncedWebAppState; }
    @Override public WhatsAppStore setSyncedWebAppState(boolean v) { this.syncedWebAppState = v; putBool("syncedWebAppState", v); return this; }

    @Override public boolean syncedBusinessCertificate() { return syncedBusinessCertificate; }
    @Override public WhatsAppStore setSyncedBusinessCertificate(boolean v) { this.syncedBusinessCertificate = v; putBool("syncedBusinessCertificate", v); return this; }

    @Override public boolean registered() { return registered; }
    @Override public WhatsAppStore setRegistered(boolean v) { this.registered = v; putBool("registered", v); return this; }

    @Override public boolean showSecurityNotifications() { return showSecurityNotifications; }
    @Override public WhatsAppStore setShowSecurityNotifications(boolean v) { this.showSecurityNotifications = v; putBool("showSecurityNotifications", v); return this; }

    @Override public Optional<SignalIdentityKeyPair> companionKeyPair() { return Optional.ofNullable(companionKeyPair); }
    @Override public WhatsAppStore setCompanionKeyPair(SignalIdentityKeyPair v) { this.companionKeyPair = v; putProto("companionKeyPair", v, SignalIdentityKeyPairSpec::encode); return this; }

    @Override public Optional<SignedDeviceIdentity> signedDeviceIdentity() { return Optional.ofNullable(signedDeviceIdentity); }
    @Override public WhatsAppStore setSignedDeviceIdentity(SignedDeviceIdentity v) { this.signedDeviceIdentity = v; putProto("signedDeviceIdentity", v, SignedDeviceIdentitySpec::encode); return this; }

    @Override public Optional<byte[]> advSecretKey() { return Optional.ofNullable(advSecretKey); }
    @Override public WhatsAppStore setAdvSecretKey(byte[] v) { this.advSecretKey = v; putBytes("advSecretKey", v); return this; }

    @Override
    public byte[] generateAdvSecretKey() {
        this.advSecretKey = SecureBytes.random(32);
        putBytes("advSecretKey", this.advSecretKey);
        return this.advSecretKey;
    }

    @Override
    public WhatsAppStore clearAdvSecretKey() {
        this.advSecretKey = null;
        putBytes("advSecretKey", null);
        return this;
    }

    @Override
    public Version clientVersion() {
        if (clientVersion == null) {
            synchronized (clientVersionLock) {
                if (clientVersion == null) {
                    clientVersion = WhatsAppClientInfo.of(device.platform()).version();
                }
            }
        }
        return clientVersion;
    }

    @Override public WhatsAppStore setClientVersion(Version v) { this.clientVersion = v; putProto("clientVersion", v, VersionSpec::encode); return this; }

    @Override public Optional<Version> companionVersion() { return Optional.ofNullable(companionVersion); }
    @Override public WhatsAppStore setCompanionVersion(Version v) { this.companionVersion = v; putProto("companionVersion", v, VersionSpec::encode); return this; }

    @Override public Optional<Instant> lastAdvCheckTime() { return Optional.ofNullable(lastAdvCheckTime); }

    @Override
    public void updateAdvCheckTime() {
        this.lastAdvCheckTime = Instant.now();
        putLong("lastAdvCheckTime", lastAdvCheckTime.toEpochMilli());
    }

    

    @Override
    public Optional<SignalSessionRecord> findSessionByAddress(SignalProtocolAddress a) {
        return Optional.ofNullable(sessions.get(a));
    }

    @Override
    public void addSession(SignalProtocolAddress a, SignalSessionRecord r) {
        sessions.put(a, r);
        var bytes = SignalSessionRecordSpec.encode(r);
        db.insertInto(T_SIGNAL_SESSIONS).set(ADDRESS, a.toString()).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes).execute();
    }

    @Override
    public boolean removeSession(SignalProtocolAddress a) {
        var removed = sessions.remove(a) != null;
        if (removed) {
            db.deleteFrom(T_SIGNAL_SESSIONS).where(ADDRESS.eq(a.toString())).execute();
        }
        return removed;
    }

    @Override
    public SequencedCollection<SignalPreKeyPair> preKeys() {
        return preKeys.sequencedValues();
    }

    @Override
    public boolean hasPreKeys() {
        return !preKeys.isEmpty();
    }

    @Override
    public Optional<SignalPreKeyPair> findPreKeyById(Integer i) {
        return i == null ? Optional.empty() : Optional.ofNullable(preKeys.get(i));
    }

    @Override
    public void addPreKey(SignalPreKeyPair k) {
        Objects.requireNonNull(k);
        preKeys.put(k.id(), k);
        var bytes = SignalPreKeyPairSpec.encode(k);
        db.insertInto(T_SIGNAL_PRE_KEYS).set(PRE_KEY_ID, k.id()).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes).execute();
    }

    @Override
    public boolean removePreKey(int i) {
        var removed = preKeys.remove(i) != null;
        if (removed) {
            db.deleteFrom(T_SIGNAL_PRE_KEYS).where(PRE_KEY_ID.eq(i)).execute();
        }
        return removed;
    }

    @Override
    public Optional<SignalSignedKeyPair> findSignedPreKeyById(Integer i) {
        return i == signedKeyPair.id() ? Optional.of(signedKeyPair) : Optional.empty();
    }

    @Override
    public void addSignedPreKey(SignalSignedKeyPair k) {
        throw new UnsupportedOperationException("Cannot add signed pre keys to a Keys instance");
    }

    @Override
    public Optional<SignalSenderKeyRecord> findSenderKeyByName(SignalSenderKeyName n) {
        return Optional.ofNullable(senderKeys.get(n));
    }

    @Override
    public void addSenderKey(SignalSenderKeyName n, SignalSenderKeyRecord r) {
        senderKeys.put(n, r);
        var bytes = SignalSenderKeyRecordSpec.encode(r);
        db.insertInto(T_SIGNAL_SENDER_KEYS).set(SK_NAME, n.toString()).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes).execute();
    }

    @Override
    public void removeSenderKeysForDevice(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        var toRemove = senderKeys.keySet().stream()
                .filter(name -> name.sender().equals(signalAddress))
                .toList();
        for (var name : toRemove) {
            senderKeys.remove(name);
            db.deleteFrom(T_SIGNAL_SENDER_KEYS).where(SK_NAME.eq(name.toString())).execute();
        }
    }

    @Override
    public void removeSenderKeysForDevice(SignalSenderKeyName n) {
        Objects.requireNonNull(n);
        senderKeys.remove(n);
        db.deleteFrom(T_SIGNAL_SENDER_KEYS).where(SK_NAME.eq(n.toString())).execute();
    }

    @Override
    public void cleanupSignalSessionsForDevice(Jid deviceJid) {
        var signalAddress = deviceJid.toSignalAddress();
        removeSession(signalAddress);
        removeSenderKeysForDevice(deviceJid);
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress a, SignalIdentityPublicKey k, SignalKeyDirection d) {
        return true;
    }

    @Override
    public void addTrustedIdentity(SignalProtocolAddress a, SignalIdentityPublicKey k) {
        // no-op
    }

    @Override
    public void saveIdentity(SignalProtocolAddress a, SignalIdentityPublicKey k) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(k);
        remoteIdentities.put(a, k);
        var bytes = k.toSerialized();
        db.insertInto(T_REMOTE_IDENTITIES).set(ADDRESS, a.toString()).set(DATA, bytes)
                .onDuplicateKeyUpdate().set(DATA, bytes).execute();
    }

    @Override
    public Optional<SignalIdentityPublicKey> findIdentityByAddress(SignalProtocolAddress a) {
        if (a == null) return Optional.empty();
        var localJid = jid;
        if (localJid != null && a.equals(localJid.toSignalAddress())) {
            return Optional.of(identityKeyPair.publicKey());
        }
        return Optional.ofNullable(remoteIdentities.get(a));
    }

    

    @Override
    public void registerLidMapping(Jid phoneJid, Jid lidJid) {
        if (phoneJid == null || lidJid == null) return;
        var normalizedPhone = phoneJid.withoutData();
        var normalizedLid = lidJid.withoutData();
        lidToPhoneMappings.put(normalizedLid, normalizedPhone);
        phoneToLidMappings.put(normalizedPhone, normalizedLid);
        db.insertInto(T_LID_MAPPINGS).set(PHONE, normalizedPhone.toString()).set(LID, normalizedLid.toString())
                .onDuplicateKeyUpdate().set(LID, normalizedLid.toString()).execute();
    }

    @Override
    public Optional<Jid> findPhoneByLid(Jid lidJid) {
        return lidJid == null ? Optional.empty() : Optional.ofNullable(lidToPhoneMappings.get(lidJid.withoutData()));
    }

    @Override
    public Optional<Jid> findLidByPhone(Jid phoneJid) {
        if (phoneJid == null) return Optional.empty();
        var localJid = jid;
        if (localJid != null && Objects.equals(phoneJid.user(), localJid.user())) {
            return Optional.ofNullable(lid).map(l -> l.withDevice(phoneJid.device()));
        }
        return Optional.ofNullable(phoneToLidMappings.get(phoneJid.withoutData()));
    }

    @Override
    public Optional<Jid> getPhoneNumberByLid(Jid lidJid) {
        if (lidJid == null || !lidJid.hasLidServer()) return Optional.empty();
        return queryAll(T_CONTACTS, ContactSpec::decode).stream()
                .filter(contact -> contact.lid().map(l -> l.equals(lidJid)).orElse(false))
                .findFirst()
                .map(Contact::jid);
    }

    @Override
    public Optional<Jid> getLidByPhoneNumber(Jid phoneNumberJid) {
        if (phoneNumberJid == null) return Optional.empty();
        if (phoneNumberJid.hasLidServer()) return Optional.of(phoneNumberJid);
        var contact = findContactByJid(phoneNumberJid).orElse(null);
        if (contact != null) {
            var contactLid = contact.lid();
            if (contactLid.isPresent()) return contactLid;
        }
        var chat = findChatByJid(phoneNumberJid).orElse(null);
        if (chat != null) return chat.accountLid();
        return Optional.empty();
    }

    @Override
    public boolean hasJid(JidProvider entry) {
        if (entry == null) return false;
        var localJid = jid;
        var localLid = lid;
        var remoteJid = entry.toJid();
        return remoteJid.equals(localJid) || remoteJid.equals(localLid);
    }

    @Override
    public boolean hasUserJid(JidProvider entry) {
        if (entry == null) return false;
        var localJid = jid;
        var localLid = lid;
        var remoteJid = entry.toJid();
        return (localJid != null && remoteJid.hasUser(localJid.user()))
                || (localLid != null && remoteJid.hasUser(localLid.user()));
    }

    

    @Override
    public void updateDeviceIdentityRange(String signalAddress, long messageTimestamp) {
        deviceIdentityRanges.compute(signalAddress, (k, existing) ->
                (existing == null || existing > messageTimestamp) ? messageTimestamp : existing);
        var ts = deviceIdentityRanges.get(signalAddress);
        db.insertInto(T_DEVICE_IDENTITY_RANGES).set(ADDRESS, signalAddress).set(TIMESTAMP, ts)
                .onDuplicateKeyUpdate().set(TIMESTAMP, ts).execute();
    }

    @Override
    public Long getDeviceIdentityRange(String signalAddress) {
        return deviceIdentityRanges.get(signalAddress);
    }

    @Override
    public boolean shouldIncludeDeviceInResend(String signalAddress, long originalTimestamp) {
        var rangeTimestamp = deviceIdentityRanges.get(signalAddress);
        return rangeTimestamp == null || rangeTimestamp <= originalTimestamp;
    }

    

    @Override
    public long updateIdentityRange(Collection<Jid> devices) {
        var seq = encryptionSequence.incrementAndGet();
        for (var d : devices) {
            var address = d.toSignalAddress();
            identityEncryptionRange.merge(address, seq, Math::min);
        }
        return seq;
    }

    @Override
    public boolean hasIdentityChanged(long sendSequence, Jid d) {
        var recorded = identityEncryptionRange.get(d.toSignalAddress());
        return recorded == null || recorded > sendSequence;
    }

    @Override
    public void clearIdentityRange(Jid d) {
        identityEncryptionRange.remove(d.toSignalAddress());
    }

    

    @Override public Collection<Contact> contacts() { return queryAll(T_CONTACTS, ContactSpec::decode); }

    @Override
    public Optional<Contact> findContactByJid(JidProvider jid) {
        if (jid == null) return Optional.empty();
        var target = jid.toJid();
        var result = queryOne(T_CONTACTS, JID.eq(target.toString()), ContactSpec::decode);
        if (result.isPresent()) return result;
        return findLidByPhone(target)
                .flatMap(l -> queryOne(T_CONTACTS, JID.eq(l.toString()), ContactSpec::decode));
    }

    @Override
    public Contact addContact(Contact contact) {
        Objects.requireNonNull(contact);
        upsert(T_CONTACTS, JID, contact.jid().toString(), contact, ContactSpec::encode);
        return contact;
    }

    @Override
    public Contact addNewContact(Jid jid) {
        return addContact(new ContactBuilder().jid(Objects.requireNonNull(jid)).build());
    }

    @Override
    public Optional<Contact> removeContact(JidProvider jid) {
        return jid == null ? Optional.empty()
                : removeOne(T_CONTACTS, JID.eq(jid.toJid().toString()), ContactSpec::decode);
    }

    

    @Override public Collection<Chat> chats() { return queryAll(T_CHATS, ChatSpec::decode); }

    @Override
    public Optional<Chat> findChatByJid(JidProvider jid) {
        if (jid == null) return Optional.empty();
        var target = jid.toJid();
        var result = queryOne(T_CHATS, JID.eq(target.toString()), ChatSpec::decode);
        if (result.isPresent()) return result;
        return findLidByPhone(target)
                .flatMap(l -> queryOne(T_CHATS, JID.eq(l.toString()), ChatSpec::decode));
    }

    @Override
    public Chat addChat(Chat chat) {
        Objects.requireNonNull(chat);
        upsert(T_CHATS, JID, chat.jid().toString(), chat, ChatSpec::encode);
        return chat;
    }

    @Override
    public Chat addNewChat(Jid jid) {
        return addChat(new ChatBuilder().jid(Objects.requireNonNull(jid)).build());
    }

    @Override
    public Optional<Chat> removeChat(JidProvider jid) {
        if (jid == null) return Optional.empty();
        var chat = findChatByJid(jid);
        chat.ifPresent(c -> {
            var key = c.jid().toString();
            deleteWhere(T_CHATS, JID.eq(key));
            deleteWhere(T_MESSAGES, CHAT_JID.eq(key));
        });
        return chat;
    }

    

    @Override
    public Optional<? extends MessageInfo> findMessageById(JidProvider provider, String id) {
        if (provider == null || id == null) return Optional.empty();
        return switch (provider) {
            case Chat chat -> findMessageById(chat, id);
            case Newsletter nl -> findMessageById(nl, id);
            case Contact c -> findChatByJid(c.jid()).flatMap(chat -> findMessageById(chat, id));
            case Jid j -> j.server().type() == JidServer.Type.NEWSLETTER
                    ? findNewsletterByJid(j).flatMap(nl -> findMessageById(nl, id))
                    : findChatByJid(j).flatMap(chat -> findMessageById(chat, id));
            case JidServer js -> findChatByJid(js.toJid()).flatMap(chat -> findMessageById(chat, id));
        };
    }

    @Override
    public Optional<ChatMessageInfo> findMessageById(Chat chat, String id) {
        if (chat == null || id == null) return Optional.empty();
        var inMemory = chat.messages().parallelStream()
                .filter(m -> Objects.equals(m.key().id(), id))
                .findAny();
        return inMemory.isPresent() ? inMemory
                : queryOne(T_MESSAGES, CHAT_JID.eq(chat.jid().toString()).and(MSG_ID.eq(id)), ChatMessageInfoSpec::decode);
    }

    @Override
    public Optional<NewsletterMessageInfo> findMessageById(Newsletter nl, String id) {
        if (nl == null || id == null) return Optional.empty();
        var inMemory = nl.messages().parallelStream()
                .filter(m -> Objects.equals(id, m.id()) || Objects.equals(id, String.valueOf(m.serverId())))
                .findFirst();
        return inMemory.isPresent() ? inMemory
                : queryOne(T_NL_MESSAGES, NL_JID.eq(nl.jid().toString()).and(MSG_ID.eq(id)), NewsletterMessageInfoSpec::decode);
    }

    @Override
    public Optional<ChatMessageInfo> findChatMessageByKey(ChatMessageKey key) {
        return key == null ? Optional.empty()
                : findChatByJid(key.chatJid()).flatMap(chat -> findMessageById(chat, key.id()));
    }

    

    @Override public Collection<ChatMessageInfo> status() { return queryAll(T_STATUS, ChatMessageInfoSpec::decode); }

    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo info) {
        Objects.requireNonNull(info);
        upsert(T_STATUS, ID, info.key().id(), info, ChatMessageInfoSpec::encode);
        return info;
    }

    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return id == null ? Optional.empty() : removeOne(T_STATUS, ID.eq(id), ChatMessageInfoSpec::decode);
    }

    

    @Override public Collection<CallOffer> calls() { return queryAll(T_CALLS, CallSpec::decode); }

    @Override
    public Optional<CallOffer> findCallById(String id) {
        return id == null ? Optional.empty() : queryOne(T_CALLS, ID.eq(id), CallSpec::decode);
    }

    @Override
    public CallOffer addCall(CallOffer call) {
        Objects.requireNonNull(call);
        upsert(T_CALLS, ID, call.id(), call, CallSpec::encode);
        return call;
    }

    @Override
    public Optional<CallOffer> removeCall(String id) {
        return id == null ? Optional.empty() : removeOne(T_CALLS, ID.eq(id), CallSpec::decode);
    }

    

    @Override public Collection<Newsletter> newsletters() { return queryAll(T_NEWSLETTERS, NewsletterSpec::decode); }

    @Override
    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        return jid == null ? Optional.empty() : queryOne(T_NEWSLETTERS, JID.eq(jid.toJid().toString()), NewsletterSpec::decode);
    }

    @Override
    public Newsletter addNewsletter(Newsletter nl) {
        Objects.requireNonNull(nl);
        upsert(T_NEWSLETTERS, JID, nl.jid().toString(), nl, NewsletterSpec::encode);
        return nl;
    }

    @Override
    public Newsletter addNewNewsletter(Jid jid) {
        return addNewsletter(new NewsletterBuilder().jid(Objects.requireNonNull(jid)).build());
    }

    @Override
    public Optional<Newsletter> removeNewsletter(JidProvider jid) {
        if (jid == null) return Optional.empty();
        var nl = findNewsletterByJid(jid);
        nl.ifPresent(n -> {
            var key = n.jid().toString();
            deleteWhere(T_NEWSLETTERS, JID.eq(key));
            deleteWhere(T_NL_MESSAGES, NL_JID.eq(key));
        });
        return nl;
    }

    

    @Override public Collection<PrivacySettingEntry> privacySettings() { return queryAll(T_PRIVACY, PrivacySettingEntrySpec::decode); }

    @Override
    public Optional<PrivacySettingEntry> findPrivacySetting(PrivacySettingType type) {
        return type == null ? Optional.empty() : queryOne(T_PRIVACY, TYPE.eq(type.name()), PrivacySettingEntrySpec::decode);
    }

    @Override
    public void addPrivacySetting(PrivacySettingEntry entry) {
        Objects.requireNonNull(entry);
        upsert(T_PRIVACY, TYPE, entry.type().name(), entry, PrivacySettingEntrySpec::encode);
    }

    

    @Override public Optional<Sticker> findRecentSticker(String h) { return h == null ? Optional.empty() : queryOne(T_STICKERS_R, HASH.eq(h), StickerSpec::decode); }
    @Override public void addRecentSticker(String h, Sticker s) { upsert(T_STICKERS_R, HASH, Objects.requireNonNull(h), Objects.requireNonNull(s), StickerSpec::encode); }
    @Override public Optional<Sticker> removeRecentSticker(String h) { return h == null ? Optional.empty() : removeOne(T_STICKERS_R, HASH.eq(h), StickerSpec::decode); }
    @Override public Optional<Sticker> findFavouriteSticker(String h) { return h == null ? Optional.empty() : queryOne(T_STICKERS_F, HASH.eq(h), StickerSpec::decode); }
    @Override public void addFavouriteSticker(String h, Sticker s) { upsert(T_STICKERS_F, HASH, Objects.requireNonNull(h), Objects.requireNonNull(s), StickerSpec::encode); }
    @Override public Optional<Sticker> removeFavouriteSticker(String h) { return h == null ? Optional.empty() : removeOne(T_STICKERS_F, HASH.eq(h), StickerSpec::decode); }

    

    @Override public Optional<QuickReply> findQuickReply(String s) { return s == null ? Optional.empty() : queryOne(T_REPLIES, SHORTCUT.eq(s), QuickReplySpec::decode); }
    @Override public void addQuickReply(QuickReply r) { upsert(T_REPLIES, SHORTCUT, Objects.requireNonNull(r).shortcut(), r, QuickReplySpec::encode); }
    @Override public Optional<QuickReply> removeQuickReply(String s) { return s == null ? Optional.empty() : removeOne(T_REPLIES, SHORTCUT.eq(s), QuickReplySpec::decode); }

    

    @Override
    public Optional<Label> findLabel(int id) {
        var row = db.select(DATA).from(T_LABELS).where(LABEL_ID.eq(id)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(decode(row.value1(), LabelSpec::decode));
    }

    @Override
    public void addLabel(Label label) {
        Objects.requireNonNull(label);
        upsert(T_LABELS, LABEL_ID, label.id(), encode(label, LabelSpec::encode));
    }

    @Override
    public Optional<Label> removeLabel(int id) {
        return removeOne(T_LABELS, LABEL_ID.eq(id), LabelSpec::decode);
    }

    

    @Override public SequencedCollection<AppStateSyncKey> appStateKeys() { return queryAll(T_ASK, AppStateSyncKeySpec::decode); }

    @Override
    public Optional<AppStateSyncKey> findWebAppStateKeyById(byte[] id) {
        return id == null ? Optional.empty() : queryOne(T_ASK, ID.eq(HexFormat.of().formatHex(id)), AppStateSyncKeySpec::decode);
    }

    @Override
    public void addWebAppStateKeys(Collection<AppStateSyncKey> keys) {
        for (var key : keys) {
            var keyId = key.keyId();
            if (keyId == null || keyId.value() == null) continue;
            upsert(T_ASK, ID, HexFormat.of().formatHex(keyId.value()), key, AppStateSyncKeySpec::encode);
        }
    }

    

    @Override
    public Optional<AppStateSyncHash> findWebAppHashStateByName(PatchType type) {
        return type == null ? Optional.empty() : queryOne(T_HASH, TYPE.eq(type.name()), AppStateSyncHashSpec::decode);
    }

    @Override
    public void addWebAppHashState(AppStateSyncHash state) {
        Objects.requireNonNull(state);
        upsert(T_HASH, TYPE, state.type().name(), state, AppStateSyncHashSpec::encode);
    }

    

    @Override public Collection<MissingDeviceSyncKey> missingSyncKeys() { return queryAll(T_MSK, MissingDeviceSyncKeySpec::decode); }

    @Override
    public Optional<MissingDeviceSyncKey> findMissingSyncKey(byte[] id) {
        return id == null ? Optional.empty() : queryOne(T_MSK, ID.eq(HexFormat.of().formatHex(id)), MissingDeviceSyncKeySpec::decode);
    }

    @Override
    public void addMissingSyncKey(MissingDeviceSyncKey key) {
        Objects.requireNonNull(key);
        upsert(T_MSK, ID, HexFormat.of().formatHex(key.keyId()), key, MissingDeviceSyncKeySpec::encode);
    }

    @Override
    public void removeMissingSyncKey(byte[] id) {
        if (id != null) deleteWhere(T_MSK, ID.eq(HexFormat.of().formatHex(id)));
    }

    @Override
    public SequencedCollection<MissingDeviceSyncKey> findExpiredMissingSyncKeys(Duration t) {
        var now = Instant.now();
        return missingSyncKeys().stream()
                .filter(key -> Duration.between(key.timestamp(), now).compareTo(t) > 0)
                .toList();
    }

    @Override
    public Optional<Instant> getEarliestMissingSyncKeyTimestamp() {
        return missingSyncKeys().stream()
                .map(MissingDeviceSyncKey::timestamp)
                .min(Instant::compareTo);
    }

    @Override
    public Optional<Duration> calculateMissingSyncKeyTimeoutDelay(Duration t) {
        return getEarliestMissingSyncKeyTimestamp()
                .map(earliest -> {
                    var elapsed = Duration.between(earliest, Instant.now());
                    var remaining = t.minus(elapsed);
                    return remaining.isNegative() ? Duration.ZERO : remaining;
                });
    }

    

    @Override
    public Optional<VerifiedBusinessName> findVerifiedBusinessName(Jid jid) {
        return queryOne(T_VBN, JID.eq(Objects.requireNonNull(jid).toUserJid().toString()), VerifiedBusinessNameSpec::decode);
    }

    @Override
    public void addVerifiedBusinessName(VerifiedBusinessName record) {
        Objects.requireNonNull(record);
        upsert(T_VBN, JID, record.jid().toUserJid().toString(), record, VerifiedBusinessNameSpec::encode);
    }

    @Override
    public void removeVerifiedBusinessName(Jid jid) {
        deleteWhere(T_VBN, JID.eq(Objects.requireNonNull(jid).toUserJid().toString()));
    }

    

    @Override
    public Optional<ChatMetadata> findChatMetadata(Jid jid) {
        return queryOne(T_GROUPS, JID.eq(Objects.requireNonNull(jid).toString()), this::decodeChatMetadata);
    }

    @Override
    public void addChatMetadata(ChatMetadata metadata) {
        Objects.requireNonNull(metadata);
        upsert(T_GROUPS, JID, metadata.jid().toString(), metadata, this::encodeChatMetadata);
    }

    @Override
    public void removeChatMetadata(Jid jid) {
        deleteWhere(T_GROUPS, JID.eq(Objects.requireNonNull(jid).toString()));
    }

    private byte[] encodeChatMetadata(ChatMetadata metadata) {
        return switch (metadata) {
            case GroupMetadata group -> {
                var payload = GroupMetadataSpec.encode(group);
                var result = new byte[payload.length + 1];
                result[0] = 0;
                System.arraycopy(payload, 0, result, 1, payload.length);
                yield result;
            }
            case CommunityMetadata community -> {
                var payload = CommunityMetadataSpec.encode(community);
                var result = new byte[payload.length + 1];
                result[0] = 1;
                System.arraycopy(payload, 0, result, 1, payload.length);
                yield result;
            }
        };
    }

    private ChatMetadata decodeChatMetadata(byte[] bytes) {
        var payload = Arrays.copyOfRange(bytes, 1, bytes.length);
        return bytes[0] == 1
                ? CommunityMetadataSpec.decode(payload)
                : GroupMetadataSpec.decode(payload);
    }

    

    @Override
    public CollectionMetadata findWebAppState(PatchType c) {
        return webAppStateCollections.computeIfAbsent(c, key ->
                new CollectionMetadata(key, 0, MutationLTHash.copy(MutationLTHash.EMPTY_HASH), 0, CollectionState.UP_TO_DATE, 0, 0));
    }

    @Override
    public void updateWebAppStateVersion(PatchType c, long v, byte[] h) {
        webAppStateCollections.compute(c, (_, current) ->
                new CollectionMetadata(c, v, MutationLTHash.copy(h), System.currentTimeMillis(),
                        current != null ? current.state() : CollectionState.UP_TO_DATE, 0, 0));
    }

    @Override
    public void markWebAppStateDirty(PatchType c) {
        webAppStateCollections.compute(c, (_, current) -> {
            if (current == null || current.state() == CollectionState.UP_TO_DATE) {
                return new CollectionMetadata(c, current != null ? current.version() : 0,
                        current != null ? MutationLTHash.copy(current.ltHash()) : MutationLTHash.copy(MutationLTHash.EMPTY_HASH),
                        System.currentTimeMillis(), CollectionState.DIRTY, 0, 0);
            }
            return current;
        });
    }

    @Override
    public void markWebAppStateInFlight(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        current.lastSyncTimestamp(), CollectionState.IN_FLIGHT, current.retryCount(), current.lastErrorTimestamp()));
    }

    @Override
    public void markWebAppStateUpToDate(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        System.currentTimeMillis(), CollectionState.UP_TO_DATE, 0, 0));
    }

    @Override
    public void markWebAppStatePending(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        current.lastSyncTimestamp(), CollectionState.PENDING, current.retryCount(), current.lastErrorTimestamp()));
    }

    @Override
    public void markWebAppStateBlocked(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        current.lastSyncTimestamp(), CollectionState.BLOCKED, current.retryCount(), System.currentTimeMillis()));
    }

    @Override
    public void markWebAppStateErrorRetry(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        current.lastSyncTimestamp(), CollectionState.ERROR_RETRY, current.retryCount() + 1, System.currentTimeMillis()));
    }

    @Override
    public void markWebAppStateErrorFatal(PatchType c) {
        webAppStateCollections.computeIfPresent(c, (_, current) ->
                new CollectionMetadata(current.name(), current.version(), current.ltHash(),
                        current.lastSyncTimestamp(), CollectionState.ERROR_FATAL, current.retryCount(), System.currentTimeMillis()));
    }

    @Override
    public void addPendingMutations(PatchType c, Collection<? extends PendingMutation> p) {
        webAppStatePendingMutations.computeIfAbsent(c, k -> new ArrayList<>()).addAll(p);
    }

    @Override
    public SequencedCollection<PendingMutation> findPendingMutations(PatchType c) {
        var collectionPending = webAppStatePendingMutations.get(c);
        return collectionPending == null ? List.of() : Collections.unmodifiableSequencedCollection(collectionPending);
    }

    @Override public void removePendingMutations(PatchType c) { webAppStatePendingMutations.remove(c); }
    @Override public void clearPendingMutations(PatchType c) { webAppStatePendingMutations.remove(c); }

    

    @Override
    public void markSenderKeyDistributed(Jid g, Jid p) {
        Objects.requireNonNull(g);
        Objects.requireNonNull(p);
        groupSenderKeyDistribution.computeIfAbsent(g.toString(), k -> ConcurrentHashMap.newKeySet()).add(p.toString());
    }

    @Override
    public boolean hasSenderKeyDistributed(Jid g, Jid p) {
        Objects.requireNonNull(g);
        Objects.requireNonNull(p);
        var participants = groupSenderKeyDistribution.get(g.toString());
        return participants != null && participants.contains(p.toString());
    }

    @Override public void clearSenderKeyDistribution(Jid g) { Objects.requireNonNull(g); groupSenderKeyDistribution.remove(g.toString()); }

    @Override
    public void clearSenderKeyDistributionForParticipant(Jid p) {
        Objects.requireNonNull(p);
        var participantKey = p.toString();
        for (var participants : groupSenderKeyDistribution.values()) {
            participants.remove(participantKey);
        }
    }

    @Override
    public void forgetSenderKeyDistributed(Jid g, Jid p) {
        Objects.requireNonNull(g);
        Objects.requireNonNull(p);
        var participants = groupSenderKeyDistribution.get(g.toString());
        if (participants != null) participants.remove(p.toString());
    }

    

    @Override public void markUserNeedsSenderKeyRotation(Jid j) { usersNeedingSenderKeyRotation.add(j.toUserJid()); }
    @Override public boolean checkAndClearSenderKeyRotationNeeded(Jid j) { return usersNeedingSenderKeyRotation.remove(j.toUserJid()); }
    @Override public boolean anyUserNeedsSenderKeyRotation(Collection<Jid> j) {
        return j.stream().map(Jid::toUserJid).anyMatch(usersNeedingSenderKeyRotation::contains);
    }

    

    @Override
    public void createOrMergeReceiptRecords(String m, Collection<Jid> r) {
        if (m == null || r == null || r.isEmpty()) return;
        pendingMessageRecipients.compute(m, (k, existing) -> {
            var set = existing != null ? existing : ConcurrentHashMap.<Jid>newKeySet();
            set.addAll(r);
            return set;
        });
    }

    @Override
    public Set<Jid> findMessageRecipients(String m) {
        var recipients = pendingMessageRecipients.get(m);
        return recipients != null ? Collections.unmodifiableSet(recipients) : Set.of();
    }

    @Override public void removeReceiptRecords(String m) { pendingMessageRecipients.remove(m); }

    @Override
    public Set<Jid> findReceiptRecords(String m) {
        if (m == null) return Set.of();
        var recipients = pendingMessageRecipients.get(m);
        return recipients != null ? Set.copyOf(recipients) : Set.of();
    }

    

    @Override public void markMessageAsRevoked(String m) { Objects.requireNonNull(m); revokedMessageIds.add(m); }
    @Override public boolean isMessageOverwrittenByRevoke(String m) { Objects.requireNonNull(m); return revokedMessageIds.contains(m); }
    @Override public void clearRevokeStatus(String m) { Objects.requireNonNull(m); revokedMessageIds.remove(m); }

    

    @Override
    public Optional<DeviceList> findDeviceList(Jid j) {
        Objects.requireNonNull(j);
        synchronized (deviceListsAccessOrder) {
            var deviceList = deviceLists.get(j);
            if (deviceList == null) {
                Jid alternateJid;
                if (j.hasUserServer()) {
                    alternateJid = findLidByPhone(j).orElse(null);
                } else if (j.hasLidServer()) {
                    alternateJid = findPhoneByLid(j).orElse(null);
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
            deviceListsAccessOrder.remove(j);
            deviceListsAccessOrder.addLast(j);
            return Optional.of(deviceList);
        }
    }

    @Override
    public Set<Jid> findDeviceJids(Jid j) {
        return findDeviceList(j).map(DeviceList::deviceJids).orElse(Set.of());
    }

    @Override
    public Collection<DeviceList> deviceLists() {
        synchronized (deviceListsAccessOrder) {
            return List.copyOf(deviceLists.values());
        }
    }

    @Override
    public void addDeviceList(DeviceList d) {
        Objects.requireNonNull(d);
        synchronized (deviceListsAccessOrder) {
            var userJid = d.userJid();
            if (deviceLists.size() >= MAX_DEVICE_LISTS && !deviceLists.containsKey(userJid)) {
                var oldest = deviceListsAccessOrder.removeFirst();
                deviceLists.remove(oldest);
            }
            deviceLists.put(userJid, d);
            deviceListsAccessOrder.remove(userJid);
            deviceListsAccessOrder.addLast(userJid);
        }
    }

    @Override
    public void removeDeviceList(Jid j) {
        Objects.requireNonNull(j);
        synchronized (deviceListsAccessOrder) {
            deviceLists.remove(j);
            deviceListsAccessOrder.remove(j);
        }
    }

    @Override
    public void clearDeviceLists() {
        synchronized (deviceListsAccessOrder) {
            deviceLists.clear();
            deviceListsAccessOrder.clear();
        }
    }

    

    @Override public void markDeviceOffline(Jid j) { offlineDeviceTimestamps.put(j, System.currentTimeMillis()); }

    @Override
    public boolean isDeviceOffline(Jid j) {
        var timestamp = offlineDeviceTimestamps.get(j);
        if (timestamp == null) return false;
        return System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000;
    }

    @Override public void markDeviceOnline(Jid j) { offlineDeviceTimestamps.remove(j); }

    @Override
    public void cleanupExpiredOfflineDevices() {
        var now = System.currentTimeMillis();
        offlineDeviceTimestamps.entrySet().removeIf(entry -> now - entry.getValue() >= 24 * 60 * 60 * 1000);
    }

    

    @Override public void addPendingDeviceSync(PendingDeviceSync s) { pendingDeviceSyncs.offer(s); }
    @Override public List<PendingDeviceSync> pendingDevicesSyncs() { return List.copyOf(pendingDeviceSyncs); }
    @Override public void removePendingDeviceSync(PendingDeviceSync s) { pendingDeviceSyncs.remove(s); }
    @Override public void clearPendingDeviceSyncs() { pendingDeviceSyncs.clear(); }
    @Override public void cleanupExpiredPendingDeviceSyncs() { pendingDeviceSyncs.removeIf(PendingDeviceSync::isExpired); }

    

    @Override public void markIdentityChange(Jid j) { unconfirmedIdentityChanges.add(j); }
    @Override public void confirmIdentityChange(Jid j) { unconfirmedIdentityChanges.remove(j); }
    @Override public Set<Jid> unconfirmedIdentityChanges() { return Collections.unmodifiableSet(unconfirmedIdentityChanges); }
    @Override public void clearUnconfirmedIdentityChanges() { unconfirmedIdentityChanges.clear(); }

    

    @Override public void addToCoexHostedVerificationCache(Jid j) { if (j != null) coexHostedVerificationCache.add(j.toUserJid()); }
    @Override public boolean isInCoexHostedVerificationCache(Jid j) { return j != null && coexHostedVerificationCache.contains(j.toUserJid()); }

    @Override
    public void assertCoexHostedVerification(Jid j) {
        if (!isInCoexHostedVerificationCache(j)) {
            throw new IllegalStateException("User " + j + " not found in coex verification cache");
        }
    }

    @Override public void clearCoexHostedVerificationCache() { coexHostedVerificationCache.clear(); }

    

    @Override public WhatsAppClientListener addListener(WhatsAppClientListener l) { listeners.add(l); return l; }
    @Override public boolean removeListener(WhatsAppClientListener l) { return listeners.remove(l); }
    @Override public Collection<WhatsAppClientListener> listeners() { return Collections.unmodifiableCollection(listeners); }

    

    @Override public Optional<WhatsAppClientProxy> proxy() { return Optional.ofNullable(proxy); }
    @Override public WhatsAppStore setProxy(WhatsAppClientProxy v) { this.proxy = v; return this; }

    

    @Override
    public MediaConnection awaitMediaConnection() throws InterruptedException {
        if (mediaConnection == null) {
            synchronized (mediaConnectionLock) {
                if (mediaConnection == null) {
                    mediaConnectionLock.wait();
                }
            }
        }
        return mediaConnection;
    }

    @Override
    public WhatsAppStore setMediaConnection(MediaConnection v) {
        this.mediaConnection = v;
        synchronized (mediaConnectionLock) {
            mediaConnectionLock.notifyAll();
        }
        return this;
    }

    

    @Override public WhatsAppClientOfflineResumeState offlineResumeState() { return offlineResumeState; }

    @Override
    public WhatsAppStore setOfflineResumeState(WhatsAppClientOfflineResumeState v) {
        this.offlineResumeState = Objects.requireNonNull(v);
        if (v == WhatsAppClientOfflineResumeState.COMPLETE) {
            offlineDeliveryLatch.countDown();
        } else if (v == WhatsAppClientOfflineResumeState.INIT) {
            offlineDeliveryLatch = new CountDownLatch(1);
        }
        return this;
    }

    @Override
    public boolean isResumeFromRestartComplete() {
        return offlineResumeState != WhatsAppClientOfflineResumeState.INIT
                && offlineResumeState != WhatsAppClientOfflineResumeState.RESUME_ON_RESTART;
    }

    @Override
    public void waitForOfflineDeliveryEnd() {
        if (offlineResumeState == WhatsAppClientOfflineResumeState.COMPLETE) return;
        try {
            offlineDeliveryLatch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    

    @Override public WhatsAppStore save() { return this; }

    @Override
    public void delete() {
        for (var table : ALL_TABLES) {
            db.dropTableIfExists(table)
                    .execute();
        }
        close();
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void await() {

    }
}
