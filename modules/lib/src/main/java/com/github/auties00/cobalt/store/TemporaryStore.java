package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.business.BusinessVerifiedName;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMute;
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
import com.github.auties00.cobalt.model.newsletter.Newsletter;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
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
import com.github.auties00.collections.ConcurrentLinkedHashMap;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.groups.SignalSenderKeyName;
import com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.key.SignalPreKeyPair;
import com.github.auties00.libsignal.key.SignalSignedKeyPair;
import com.github.auties00.libsignal.state.SignalSessionRecord;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The {@link AbstractWhatsAppStore} that holds the entire session state in RAM and never touches
 * disk.
 *
 * @apiNote
 * Cobalt embedders obtain instances through {@link WhatsAppStoreFactory#temporary()}. Useful for
 * short-lived sessions, one-shot bots, integration tests and scratch programs that do not need
 * their state to survive a JVM restart. Restarting the program loses every chat, every
 * newsletter, every message and every key.
 *
 * @implNote
 * This implementation keeps the chat and newsletter maps as {@link ConcurrentHashMap} and the
 * status feed as a {@link ConcurrentLinkedHashMap} keyed by message id so insertion order is
 * preserved for the status accessor. {@link #save()} is a no-op, {@link #await()} is a no-op,
 * and {@link #delete()} simply clears the in-memory maps because there is no disk surface to
 * unlink. The companion {@link TemporaryStoreFactory#load load} and
 * {@link TemporaryStoreFactory#loadLatest loadLatest} entries always return
 * {@link Optional#empty()}.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
final class TemporaryStore extends AbstractWhatsAppStore {
    /**
     * The map of chat JIDs to their in-memory {@link TemporaryChat} entries.
     */
    private final ConcurrentHashMap<Jid, TemporaryChat> chats;

    /**
     * The map of newsletter JIDs to their in-memory {@link TemporaryNewsletter} entries.
     */
    private final ConcurrentHashMap<Jid, TemporaryNewsletter> newsletters;

    /**
     * The status feed keyed by message id, preserving insertion order.
     */
    private final ConcurrentLinkedHashMap<String, ChatMessageInfo> status;

    /**
     * Constructs an in-memory store with the given scalar metadata.
     *
     * @apiNote
     * Package-private and intended for {@link TemporaryStoreFactory}. The chats, newsletters and
     * status maps are constructed empty; chats and newsletters are added via
     * {@link #addNewChat(Jid)} and {@link #addNewNewsletter(Jid)}.
     *
     * @implNote
     * This implementation forwards every argument to the parent constructor and then allocates
     * three empty maps; the parent stores the per-session scalars, the maps stay local.
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
     * @param directory                             the session directory path; unused for the
     *                                              transient variant
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
     */
    TemporaryStore(
            UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, List<URI> businessWebsites, String businessEmail, List<BusinessCategory> businessCategories, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations, ConcurrentMap<SyncPatchType, AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, ConcurrentHashMap<Jid, OutContact> outContacts, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeys, ConcurrentMap<Integer, Integer> wamSequenceNumbers, String companionMmsAuthNonce, byte[] shareableChatLinkKey, Boolean startAtLogin, Boolean minimizeToTray, Boolean replaceTextWithEmoji, SettingsSyncAction.DisplayMode bannerNotificationDisplayMode, SettingsSyncAction.DisplayMode unreadCounterBadgeDisplayMode, Boolean messagesNotificationEnabled, Boolean callsNotificationEnabled, Boolean reactionsNotificationEnabled, Boolean statusReactionsNotificationEnabled, Boolean textPreviewForNotificationEnabled, Integer defaultNotificationToneId, Integer groupDefaultNotificationToneId, AppTheme appTheme, Integer wallpaperId, Boolean doodleWallpaperEnabled, Integer fontSize, Boolean photosAutodownloadEnabled, Boolean audiosAutodownloadEnabled, Boolean videosAutodownloadEnabled, Boolean documentsAutodownloadEnabled, Integer notificationToneId, SettingsSyncAction.MediaQualitySetting mediaUploadQuality, Boolean spellCheckEnabled, Boolean enterToSendEnabled, Boolean groupMessageNotificationEnabled, Boolean groupReactionsNotificationEnabled, Boolean statusNotificationEnabled, Integer statusNotificationToneId, Boolean playSoundForCallNotification
    ) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsites, businessEmail, businessCategories, contacts, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations, orphanMutationEntries, outContacts, clockSkewSeconds, groupAbPropsEmergencyPushTimestamp, abPropsAbKey, abPropsHash, abPropsRefresh, abPropsLastSyncTime, abPropsRefreshId, abPropsWebRefreshId, groupAbPropsRefreshId, baseKeys, wamSequenceNumbers, companionMmsAuthNonce, shareableChatLinkKey, startAtLogin, minimizeToTray, replaceTextWithEmoji, bannerNotificationDisplayMode, unreadCounterBadgeDisplayMode, messagesNotificationEnabled, callsNotificationEnabled, reactionsNotificationEnabled, statusReactionsNotificationEnabled, textPreviewForNotificationEnabled, defaultNotificationToneId, groupDefaultNotificationToneId, appTheme, wallpaperId, doodleWallpaperEnabled, fontSize, photosAutodownloadEnabled, audiosAutodownloadEnabled, videosAutodownloadEnabled, documentsAutodownloadEnabled, notificationToneId, mediaUploadQuality, spellCheckEnabled, enterToSendEnabled, groupMessageNotificationEnabled, groupReactionsNotificationEnabled, statusNotificationEnabled, statusNotificationToneId, playSoundForCallNotification);
        this.chats = new ConcurrentHashMap<>();
        this.newsletters = new ConcurrentHashMap<>();
        this.status = new ConcurrentLinkedHashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op and returns {@code this} so embedders can chain
     * {@code store.save()} without conditionally branching on the storage strategy.
     */
    @Override
    public WhatsAppStore save() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op: there is no asynchronous persistence work to await.
     */
    @Override
    public void await() {

    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation clears the three in-memory maps so a subsequent
     * {@link #findChatByJid(JidProvider)}, {@link #findNewsletterByJid(JidProvider)}, or
     * {@link #findStatusById(String)} returns empty.
     */
    @Override
    public void delete() {
        chats.clear();
        newsletters.clear();
        status.clear();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors {@link PersistentStore#findChatByJid(JidProvider)}'s PN/LID
     * bridging: a user-server JID falls back through {@link #findLidByPhone(Jid)} when the direct
     * lookup misses, and a LID-server JID retries via {@link #findPhoneByLid(Jid)} and then
     * {@link #findLidByPhone(Jid)} so chats keyed under either address surface uniformly.
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
                    yield Optional.ofNullable(chats.get(targetJid));
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation dispatches on the runtime type of {@code provider}: newsletters route
     * to the newsletter-aware overload, status-broadcast lookups read the local status map, and
     * everything else resolves the owning chat first.
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
                    yield Optional.ofNullable(status.get(id));
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
     * This implementation scans the newsletter's value list and accepts a match on either the
     * message-key id string or the stringified {@code serverId}; callers may pass either form.
     */
    @Override
    public Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id) {
        if (newsletter == null || id == null) {
            return Optional.empty();
        }
        try (var stream = newsletter.messages()) {
            return stream
                    .filter(entry -> Objects.equals(id, entry.key().id().orElse(null)) || Objects.equals(id, String.valueOf(entry.serverId())))
                    .findFirst();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to {@link Chat#getMessageById(String)}, which is an
     * {@code O(1)} map lookup on the {@link TemporaryChat} variant.
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
     * This implementation constructs a fresh {@link TemporaryChat} and inserts it into
     * {@link #chats}; no attachment phase is required because the chat owns its message map.
     */
    @Override
    public Chat addNewChat(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        var chat = new TemporaryChat(chatJid);
        chats.put(chatJid, chat);
        return chat;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors {@link #findChatByJid(JidProvider)}'s PN/LID bridging. Unlike
     * the persistent variant there is no message-body store to also clear; once the metadata
     * entry is removed the chat history becomes unreachable and eligible for garbage collection.
     */
    @Override
    public Optional<Chat> removeChat(JidProvider chatJid) {
        if (chatJid == null) {
            return Optional.empty();
        }
        var targetJid = chatJid.toJid();
        if (targetJid.hasUserServer()) {
            var jidChat = chats.remove(targetJid);
            if (jidChat != null) {
                return Optional.of(jidChat);
            }
            return findLidByPhone(targetJid).map(chats::remove);
        }
        if (targetJid.hasLidServer()) {
            var lidChat = chats.remove(targetJid);
            if (lidChat != null) {
                return Optional.of(lidChat);
            }
            return findPhoneByLid(targetJid).map(chats::remove);
        }
        return Optional.ofNullable(chats.remove(targetJid));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation silently drops messages without a key id because the underlying map
     * requires a non-null key; production code paths always populate the id before insertion.
     */
    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageInfo.key().id().ifPresent(id -> status.put(id, messageInfo));
        return messageInfo;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns the previously stored value, or empty if no entry was present
     * for the id.
     */
    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(status.remove(id));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads the status map directly; the lookup is an {@code O(1)} hash
     * probe.
     */
    @Override
    public Optional<ChatMessageInfo> findStatusById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(status.get(id));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation does not bridge PN/LID for newsletters because newsletter JIDs use a
     * distinct server ({@link JidServer.Type#NEWSLETTER}) and cannot collide with the user/LID
     * address spaces.
     */
    @Override
    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        return jid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.get(jid.toJid()));
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
     * This implementation constructs a fresh {@link TemporaryNewsletter} and inserts it into
     * {@link #newsletters}.
     */
    @Override
    public Newsletter addNewNewsletter(Jid newsletterJid) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var newsletter = new TemporaryNewsletter(newsletterJid);
        newsletters.put(newsletterJid, newsletter);
        return newsletter;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation removes the metadata entry; no message-body store needs additional
     * cleanup because the newsletter owns its own in-memory message map.
     */
    @Override
    public Optional<Newsletter> removeNewsletter(JidProvider newsletterJid) {
        return newsletterJid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.remove(newsletterJid.toJid()));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation extracts the message id and the parent JID from {@code key} and
     * routes through {@link #findChatByJid(JidProvider)} so PN/LID bridging applies. Only chat
     * messages are addressable here; status-broadcast and newsletter routes use the dedicated
     * accessors.
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
     * This implementation returns an unmodifiable view over the status map's values, which
     * iterates in insertion order thanks to the sequenced-map contract.
     */
    @Override
    public Collection<ChatMessageInfo> status() {
        return Collections.unmodifiableCollection(status.values());
    }
}
