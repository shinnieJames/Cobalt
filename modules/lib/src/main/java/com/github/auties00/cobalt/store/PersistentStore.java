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
import com.github.auties00.cobalt.model.setting.AppTheme;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;
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
 * The {@link AbstractWhatsAppStore} that persists session metadata to a single protobuf file on
 * disk and offloads every message body to an embedded {@link PersistentMessageStore LMDB} env.
 *
 * <p>Data layout under the session directory:
 * <ul>
 *   <li>{@code store.proto} holds the aggregate inherited from {@link AbstractWhatsAppStore}
 *       (scalars, settings, contacts, calls, privacy, Signal-protocol state, syncd hash states,
 *       AB-props) plus the {@link #chats} and {@link #newsletters} maps. Chat and newsletter
 *       entries carry metadata only (jid, name, unread counters, ephemeral settings); message
 *       bodies live in LMDB.</li>
 *   <li>{@code messages.lmdb/} holds the LMDB env, with chat messages keyed by
 *       {@code chatJid + msgId}, newsletter messages keyed by
 *       {@code newsletterJid + serverId}, and the global status feed keyed by message id.</li>
 * </ul>
 *
 * <p>The metadata snapshot is written through a sibling {@code .tmp} file followed by an atomic
 * move so a crash mid-write never leaves a half-written {@code store.proto} on disk. LMDB writes
 * are independently durable on every commit so {@link #save()} does not touch the env. On load the
 * factory walks the LMDB env once to recover orphan chats and newsletters whose bodies landed
 * before the next metadata snapshot.
 *
 * @apiNote
 * Cobalt embedders obtain a {@link PersistentStore} indirectly through
 * {@link WhatsAppStoreFactory#persistent()} and its overloads; the class is package-private so the
 * persistence strategy is not part of the public API surface.
 *
 * @implNote
 * This implementation captures {@link #hashCode()} into {@link #storeHashCode} at the end of each
 * successful {@link #save()} and short-circuits subsequent saves when nothing has changed. The
 * inner {@link PersistentChat} and {@link PersistentNewsletter} subtypes delegate every message
 * accessor to {@link PersistentMessageStore} and cache their counts in {@link AtomicInteger}s
 * seeded from a one-time cursor walk on attachment.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
final class PersistentStore extends AbstractWhatsAppStore {
    /**
     * The name of the metadata file written under the session directory.
     */
    private static final String STORE_FILE = "store.proto";

    /**
     * The name of the LMDB sub-directory under the session directory.
     */
    private static final String MESSAGES_DIRECTORY = "messages.lmdb";

    /**
     * The map of chat JIDs to their metadata-only {@link PersistentChat} entries.
     *
     * @apiNote
     * Holds chat metadata only; message bodies live in {@link PersistentMessageStore}.
     */
    @ProtobufProperty(index = 82, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, PersistentChat> chats;

    /**
     * The map of newsletter JIDs to their metadata-only {@link PersistentNewsletter} entries.
     *
     * @apiNote
     * Holds newsletter metadata only; message bodies live in {@link PersistentMessageStore}.
     */
    @ProtobufProperty(index = 83, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, PersistentNewsletter> newsletters;

    /**
     * The hash code captured at the end of the last successful {@link #save()}.
     *
     * @implNote
     * This implementation declares the field {@code volatile} so concurrent {@link #save()} calls
     * observe a consistent value without holding the monitor; the slow path serialises on
     * {@code synchronized(this)} before issuing the write.
     */
    private volatile Integer storeHashCode;

    /**
     * The LMDB facade backing every message-bearing accessor on the inner subtypes and on the
     * global status feed.
     *
     * @implNote
     * This implementation is wired by {@link #attachMessageStore(PersistentMessageStore)} so the
     * factory can attach the env both after a fresh {@code create(...)} and after a
     * deserialisation pass.
     */
    private volatile PersistentMessageStore messageStore;

    /**
     * Constructs a new {@code PersistentStore} with the given protobuf-decoded fields.
     *
     * @apiNote
     * Package-private and intended for the generated {@code PersistentStoreBuilder} and the
     * protobuf deserialiser. Embedders obtain instances through
     * {@link WhatsAppStoreFactory#persistent()}.
     *
     * @implNote
     * The {@link #messageStore} field is left {@code null} here and wired by
     * {@link PersistentStoreFactory} via {@link #attachMessageStore(PersistentMessageStore)}
     * immediately after construction; the parent constructor sets every scalar inherited from
     * {@link AbstractWhatsAppStore}.
     *
     * @param uuid                                  the session UUID
     * @param phoneNumber                           the session phone number, or {@code null}
     * @param clientType                            the client type (web or mobile)
     * @param initializationTimeStamp               the session initialization timestamp
     * @param device                                the synthetic device descriptor
     * @param releaseChannel                        the client release channel
     * @param online                                whether the session is online
     * @param locale                                the user locale string
     * @param name                                  the display name
     * @param verifiedName                          the verified business name
     * @param profilePicture                        the profile picture URI
     * @param selfTextStatus                        the self text status
     * @param jid                                   the user JID
     * @param lid                                   the user LID
     * @param businessAddress                       the business address
     * @param businessLongitude                     the business longitude
     * @param businessLatitude                      the business latitude
     * @param businessDescription                   the business description
     * @param businessWebsites                      the business website URIs
     * @param businessEmail                         the business email
     * @param businessCategories                    the business categories
     * @param contacts                              the contact map
     * @param privacySettings                       the privacy-setting map
     * @param unarchiveChats                        whether incoming messages unarchive chats
     * @param twentyFourHourFormat                  whether the locale uses 24-hour format
     * @param newChatsEphemeralTimer                the default ephemeral timer for new chats
     * @param webHistoryPolicy                      the web history policy
     * @param checkPatchMacs                        whether to verify syncd patch MACs
     * @param syncedChats                           whether chats have been synced
     * @param syncedContacts                        whether contacts have been synced
     * @param syncedNewsletters                     whether newsletters have been synced
     * @param syncedStatus                          whether the status feed has been synced
     * @param syncedBusinessCertificate             whether the business certificate has been synced
     * @param registrationId                        the Signal registration id
     * @param noiseKeyPair                          the Noise XX static key pair
     * @param identityKeyPair                       the Signal identity key pair
     * @param signedDeviceIdentity                  the ADV-signed device identity
     * @param signedKeyPair                         the Signal signed pre-key pair
     * @param preKeys                               the local pre-keys
     * @param fdid                                  the device-frontend identifier
     * @param deviceId                              the device identifier bytes
     * @param advertisingId                         the advertising identifier
     * @param identityId                            the identity-id bytes
     * @param backupToken                           the backup token
     * @param senderKeys                            the Signal sender-key map
     * @param appStateKeys                          the app-state sync key map
     * @param sessions                              the Signal session-record map
     * @param hashStates                            the syncd hash-state map
     * @param registered                            whether the device is registered
     * @param showSecurityNotifications             whether to show security notifications
     * @param recentStickers                        the recent-stickers map
     * @param favouriteStickers                     the favourite-stickers map
     * @param quickReplies                          the quick-replies map
     * @param labels                                the labels map
     * @param clientVersion                         the local client version
     * @param companionVersion                      the companion version
     * @param lastAdvCheckTime                      the timestamp of the last ADV check
     * @param remoteIdentities                      the remote-identity map
     * @param missingSyncKeys                       the missing device-sync-key map
     * @param advSecretKey                          the ADV secret key bytes
     * @param verifiedBusinessNames                 the verified-business-name map
     * @param directory                             the session directory path
     * @param primaryDeviceSupportsSyncdRecovery    whether the primary supports syncd recovery
     * @param disableLinkPreviews                   whether link previews are disabled
     * @param relayAllCalls                         whether to relay all calls through the TURN server
     * @param externalWebBeta                       whether the external web beta is enabled
     * @param chatLockSettings                      the chat-lock configuration
     * @param favoriteChats                         the favourite chats list
     * @param primaryFeatures                       the primary-feature list
     * @param mentionEveryoneMuteExpirations        the mention-everyone mute expirations
     * @param orphanMutationEntries                 the orphan mutation entries
     * @param outContacts                           the out-contact map
     * @param clockSkewSeconds                      the negotiated clock skew
     * @param groupAbPropsEmergencyPushTimestamp    the group-ab-props emergency push timestamp
     * @param abPropsAbKey                          the AB-props key
     * @param abPropsHash                           the AB-props hash
     * @param abPropsRefresh                        the AB-props refresh interval
     * @param abPropsLastSyncTime                   the AB-props last sync time
     * @param abPropsRefreshId                      the AB-props refresh id
     * @param abPropsWebRefreshId                   the AB-props web refresh id
     * @param groupAbPropsRefreshId                 the group-ab-props refresh id
     * @param baseKeys                              the base-keys map
     * @param wamSequenceNumbers                    the WAM sequence-number map
     * @param chats                                 the persistent chats map
     * @param newsletters                           the persistent newsletters map
     */
    PersistentStore(
            UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, List<URI> businessWebsites, String businessEmail, List<BusinessCategory> businessCategories, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations, ConcurrentMap<SyncPatchType, AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, ConcurrentHashMap<Jid, OutContact> outContacts, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeys, ConcurrentMap<Integer, Integer> wamSequenceNumbers, String companionMmsAuthNonce, byte[] shareableChatLinkKey, Boolean startAtLogin, Boolean minimizeToTray, Boolean replaceTextWithEmoji, SettingsSyncAction.DisplayMode bannerNotificationDisplayMode, SettingsSyncAction.DisplayMode unreadCounterBadgeDisplayMode, Boolean messagesNotificationEnabled, Boolean callsNotificationEnabled, Boolean reactionsNotificationEnabled, Boolean statusReactionsNotificationEnabled, Boolean textPreviewForNotificationEnabled, Integer defaultNotificationToneId, Integer groupDefaultNotificationToneId, AppTheme appTheme, Integer wallpaperId, Boolean doodleWallpaperEnabled, Integer fontSize, Boolean photosAutodownloadEnabled, Boolean audiosAutodownloadEnabled, Boolean videosAutodownloadEnabled, Boolean documentsAutodownloadEnabled, Integer notificationToneId, SettingsSyncAction.MediaQualitySetting mediaUploadQuality, Boolean spellCheckEnabled, Boolean enterToSendEnabled, Boolean groupMessageNotificationEnabled, Boolean groupReactionsNotificationEnabled, Boolean statusNotificationEnabled, Integer statusNotificationToneId, Boolean playSoundForCallNotification,
            ConcurrentHashMap<Jid, PersistentChat> chats,
            ConcurrentHashMap<Jid, PersistentNewsletter> newsletters
    ) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsites, businessEmail, businessCategories, contacts, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations, orphanMutationEntries, outContacts, clockSkewSeconds, groupAbPropsEmergencyPushTimestamp, abPropsAbKey, abPropsHash, abPropsRefresh, abPropsLastSyncTime, abPropsRefreshId, abPropsWebRefreshId, groupAbPropsRefreshId, baseKeys, wamSequenceNumbers, companionMmsAuthNonce, shareableChatLinkKey, startAtLogin, minimizeToTray, replaceTextWithEmoji, bannerNotificationDisplayMode, unreadCounterBadgeDisplayMode, messagesNotificationEnabled, callsNotificationEnabled, reactionsNotificationEnabled, statusReactionsNotificationEnabled, textPreviewForNotificationEnabled, defaultNotificationToneId, groupDefaultNotificationToneId, appTheme, wallpaperId, doodleWallpaperEnabled, fontSize, photosAutodownloadEnabled, audiosAutodownloadEnabled, videosAutodownloadEnabled, documentsAutodownloadEnabled, notificationToneId, mediaUploadQuality, spellCheckEnabled, enterToSendEnabled, groupMessageNotificationEnabled, groupReactionsNotificationEnabled, statusNotificationEnabled, statusNotificationToneId, playSoundForCallNotification);
        this.chats = chats;
        this.newsletters = newsletters;
    }

    /**
     * Wires the LMDB facade into this store and into every existing chat and newsletter entry.
     *
     * @apiNote
     * Called by {@link PersistentStoreFactory} after construction or deserialisation. After this
     * call returns, every message accessor on every {@link PersistentChat} and
     * {@link PersistentNewsletter} owned by this store is operational.
     *
     * @implNote
     * This implementation iterates {@link #chats} and {@link #newsletters} once, attaching the
     * facade and seeding their cached counters via {@link PersistentChat#attach} and
     * {@link PersistentNewsletter#attach}.
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
     * @apiNote
     * Internal accessor for {@link PersistentStoreFactory}; callers outside this package have no
     * use for the raw facade.
     *
     * @return the message store
     */
    PersistentMessageStore messageStore() {
        return messageStore;
    }

    /**
     * Returns the path to the LMDB env directory for the given session.
     *
     * @apiNote
     * Internal helper for {@link PersistentStoreFactory}; resolves
     * {@code <baseDirectory>/<clientType>/<sessionId>/messages.lmdb}.
     *
     * @param clientType    the client type
     * @param baseDirectory the root directory under which per-session folders are created
     * @param sessionId     the session UUID string or phone-number string
     * @return the LMDB env directory
     * @throws IOException if the parent session directory cannot be created
     */
    static Path messagesEnvPath(WhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return StorePathUtils.getSessionDirectory(clientType, baseDirectory, sessionId)
                .resolve(MESSAGES_DIRECTORY);
    }

    /**
     * Returns the path to the {@code store.proto} metadata file for the given session.
     *
     * @apiNote
     * Internal helper for {@link PersistentStoreFactory#load} and the
     * {@link #serializeStore() serialiser}.
     *
     * @param clientType    the client type
     * @param baseDirectory the root directory under which per-session folders are created
     * @param sessionId     the session UUID string or phone-number string
     * @return the metadata file path
     * @throws IOException if the parent session directory cannot be created
     */
    static Path storeFilePath(WhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return StorePathUtils.getSessionFile(clientType, baseDirectory, sessionId, STORE_FILE);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Idempotent. Saves issued in tight succession that do not actually change anything reduce to
     * a hash compare and return without touching disk.
     *
     * @implNote
     * This implementation uses double-checked locking on {@link #storeHashCode}. The fast path
     * compares {@code hashCode()} against the captured value without holding the monitor; the
     * slow path takes {@code synchronized(this)} and rechecks before serialising. IO failures are
     * logged at {@link System.Logger.Level#WARNING} and swallowed because callers cannot
     * meaningfully recover; the next successful save observes the same dirty state.
     */
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
     * Writes the current metadata snapshot to {@code store.proto} via a sibling temp file
     * followed by an atomic move.
     *
     * @apiNote
     * Internal helper for {@link #save()}.
     *
     * @implNote
     * This implementation writes to {@link Files#createTempFile} and then issues
     * {@link Files#move} with {@link StandardCopyOption#REPLACE_EXISTING} so a crash mid-write
     * never leaves a half-written {@code store.proto} on disk.
     *
     * @throws IOException if the file cannot be created, written, or moved
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation closes the LMDB facade before recursively removing the session
     * directory; closing first releases native handles so the directory remove can succeed on
     * Windows, where open mapped files cannot be unlinked.
     */
    @Override
    public void delete() throws IOException {
        var folderPath = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
        if (messageStore != null) {
            messageStore.close();
            messageStore = null;
        }
        StorePathUtils.deleteRecursively(folderPath);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op: the metadata snapshot decodes synchronously in
     * {@link PersistentStoreFactory#load}, so there is no pending background work to await.
     */
    @Override
    public void await() {
    }

    /**
     * Closes the LMDB env so the session can be shut down without being deleted.
     *
     * @apiNote
     * Called by the factory during ordinary shutdown. A second invocation is a no-op because
     * {@link #messageStore} is nulled after the first close.
     */
    void close() {
        if (messageStore != null) {
            messageStore.close();
            messageStore = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation tries the supplied JID directly first, then falls back across the
     * PN/LID boundary via {@link #findLidByPhone(Jid)} and {@link #findPhoneByLid(Jid)} so chats
     * that were originally keyed under a phone-number JID surface when the caller hands in the
     * matching LID and vice versa. The LID branch additionally re-tries the phone-number lookup
     * with {@link #findLidByPhone(Jid)} so chats keyed under the LID for a peer who later moved
     * to a PN still resolve.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation dispatches on the runtime type of {@code provider}: newsletters route
     * to the newsletter-aware overload, status-broadcast lookups go straight to the LMDB status
     * dbi, and everything else resolves the owning chat first and then looks up the message
     * within it.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation tries the {@code serverId} fast path (parsing {@code id} as a numeric
     * server id) and falls back to a per-newsletter cursor scan matching the optional
     * message-key id; the scan path is used when callers pass in the message-id string rather
     * than the numeric server id.
     */
    @Override
    public Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id) {
        if (newsletter == null || id == null) {
            return Optional.empty();
        }
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to {@link Chat#getMessageById(String)} so the caching that
     * {@link PersistentChat} applies through its LMDB facade is preserved.
     */
    @Override
    public Optional<ChatMessageInfo> findMessageById(Chat chat, String id) {
        if (chat == null || id == null) {
            return Optional.empty();
        }
        return chat.getMessageById(id);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns an unmodifiable view over {@link ConcurrentHashMap#values()};
     * the view is live and reflects concurrent modifications but does not permit them.
     */
    @Override
    public Collection<Chat> chats() {
        return Collections.unmodifiableCollection(chats.values());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation builds the chat via the generated {@code PersistentChatBuilder},
     * attaches the LMDB facade so subsequent message accessors work immediately, then inserts
     * into {@link #chats}.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors {@link #findChatByJid(JidProvider)}'s PN/LID-bridging removal
     * logic and additionally removes every body row for the chat from
     * {@link PersistentMessageStore} so a deleted chat's history does not outlive its metadata.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to
     * {@link PersistentMessageStore#putStatusMessage(ChatMessageInfo)} which silently drops
     * messages without a key id; the same {@code messageInfo} is returned unchanged for chaining.
     */
    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageStore.putStatusMessage(messageInfo);
        return messageInfo;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to
     * {@link PersistentMessageStore#removeStatusMessage(String)} which returns the previously
     * stored message when present.
     */
    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return id == null ? Optional.empty() : messageStore.removeStatusMessage(id);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to {@link PersistentMessageStore#getStatusMessage(String)}.
     */
    @Override
    public Optional<ChatMessageInfo> findStatusById(String id) {
        return id == null ? Optional.empty() : messageStore.getStatusMessage(id);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation does not bridge PN/LID for newsletters because newsletter JIDs use a
     * distinct server ({@link JidServer.Type#NEWSLETTER}) and cannot collide with the
     * user/LID address spaces.
     */
    @Override
    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        if (jid == null) {
            return Optional.empty();
        }
        Newsletter newsletter = newsletters.get(jid.toJid());
        return Optional.ofNullable(newsletter);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns an unmodifiable view over {@link ConcurrentHashMap#values()}.
     */
    @Override
    public Collection<Newsletter> newsletters() {
        return Collections.unmodifiableCollection(newsletters.values());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation builds the newsletter via the generated
     * {@code PersistentNewsletterBuilder}, attaches the LMDB facade, then inserts into
     * {@link #newsletters}.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation removes the metadata entry first; if a row was present, the
     * corresponding LMDB body range is dropped via
     * {@link PersistentMessageStore#removeNewsletterMessages(Jid)} so the newsletter's history
     * does not outlive its metadata.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation extracts the message id and the parent JID from {@code key} and
     * routes through {@link #findChatByJid(JidProvider)} so PN/LID bridging applies; only chat
     * messages are addressable by {@link MessageKey} in this overload.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the status stream into a fresh list inside a
     * try-with-resources block so the underlying LMDB read transaction is released before the
     * list is returned. Embedders that need lazy iteration should call
     * {@link PersistentMessageStore#streamStatusMessages()} directly through
     * {@link #messageStore()}.
     */
    @Override
    public Collection<ChatMessageInfo> status() {
        try (var stream = messageStore.streamStatusMessages()) {
            return stream.toList();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation extends the parent equality with {@link #chats} and {@link #newsletters}
     * so two persistent stores carrying identical metadata but different chat sets compare as
     * unequal.
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof PersistentStore that
                            && super.equals(o)
                            && Objects.equals(this.chats, that.chats)
                            && Objects.equals(this.newsletters, that.newsletters);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation folds the parent hash with each non-null chat and newsletter entry so
     * {@link #save()}'s short-circuit comparison detects message-count and per-entry metadata
     * changes that the parent hash alone would miss.
     */
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
     * Decodes a {@link PersistentStore} from {@code storeFile} via the generated spec.
     *
     * @apiNote
     * Internal helper for {@link PersistentStoreFactory}; kept package-private so the
     * deserialisation entry point stays co-located with the {@code PersistentStoreSpec} it
     * depends on.
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
