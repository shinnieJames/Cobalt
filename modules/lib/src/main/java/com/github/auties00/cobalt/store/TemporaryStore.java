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
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
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
 * Concrete {@link WhatsAppStore} that holds the entire session state in
 * RAM and never touches disk.
 *
 * <p>Useful for short-lived sessions, one-shot bots, integration tests
 * and scratch programs that do not need their state to survive a JVM
 * restart. Restarting the program loses every chat, every newsletter,
 * every message and every key.
 *
 * <p>{@link #save()} is a no-op, {@link #await()} is a no-op,
 * {@link #delete()} clears the in-memory maps. The factory's
 * {@link TemporaryStoreFactory#load load} and
 * {@link TemporaryStoreFactory#loadLatest loadLatest} always return
 * {@link Optional#empty()} since there is nothing to load.
 *
 * <p>The inner {@link TemporaryChat} and {@link TemporaryNewsletter} subclasses keep
 * messages in a {@link ConcurrentLinkedHashMap} keyed by message id (or
 * server id for newsletters), so insertion order is preserved and
 * {@link TemporaryChat#oldestMessage()} / {@link TemporaryChat#newestMessage()} reduce to
 * a sequenced-map first/last lookup.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
final class TemporaryStore extends AbstractWhatsAppStore {
    /**
     * Map of chat JIDs to their in-memory {@link TemporaryChat} entries.
     */
    private final ConcurrentHashMap<Jid, TemporaryChat> chats;

    /**
     * Map of newsletter JIDs to their in-memory {@link TemporaryNewsletter}
     * entries.
     */
    private final ConcurrentHashMap<Jid, TemporaryNewsletter> newsletters;

    /**
     * Status feed keyed by message id.
     */
    private final ConcurrentLinkedHashMap<String, ChatMessageInfo> status;

    /**
     * Constructs a new in-memory store. All maps are constructed
     * empty; chats and newsletters are added via
     * {@link #addNewChat(Jid)} / {@link #addNewNewsletter(Jid)}.
     */
    TemporaryStore(
            UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, WhatsAppDevice device, ClientPayload.ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, ContactTextStatus selfTextStatus, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, String businessWebsite, String businessEmail, BusinessCategory businessCategory, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedName> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, ChatMute> mentionEveryoneMuteExpirations, ConcurrentMap<SyncPatchType, AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries, ConcurrentHashMap<Jid, OutContact> outContacts, long clockSkewSeconds, Instant groupAbPropsEmergencyPushTimestamp, String abPropsAbKey, String abPropsHash, long abPropsRefresh, Instant abPropsLastSyncTime, long abPropsRefreshId, long abPropsWebRefreshId, long groupAbPropsRefreshId, ConcurrentMap<String, byte[]> baseKeys, ConcurrentMap<Integer, Integer> wamSequenceNumbers
    ) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, selfTextStatus, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsite, businessEmail, businessCategory, contacts, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations, orphanMutationEntries, outContacts, clockSkewSeconds, groupAbPropsEmergencyPushTimestamp, abPropsAbKey, abPropsHash, abPropsRefresh, abPropsLastSyncTime, abPropsRefreshId, abPropsWebRefreshId, groupAbPropsRefreshId, baseKeys, wamSequenceNumbers);
        this.chats = new ConcurrentHashMap<>();
        this.newsletters = new ConcurrentHashMap<>();
        this.status = new ConcurrentLinkedHashMap<>();
    }

    @Override
    public WhatsAppStore save() {
        return this;
    }

    @Override
    public void await() {

    }

    @Override
    public void delete() {
        chats.clear();
        newsletters.clear();
        status.clear();
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
                    yield Optional.ofNullable(chats.get(targetJid));
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
                    yield Optional.ofNullable(status.get(id));
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
        try (var stream = newsletter.messages()) {
            return stream
                    .filter(entry -> Objects.equals(id, entry.key().id().orElse(null)) || Objects.equals(id, String.valueOf(entry.serverId())))
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
        var chat = new TemporaryChat(chatJid);
        chats.put(chatJid, chat);
        return chat;
    }

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

    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageInfo.key().id().ifPresent(id -> status.put(id, messageInfo));
        return messageInfo;
    }

    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(status.remove(id));
    }

    @Override
    public Optional<ChatMessageInfo> findStatusById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(status.get(id));
    }

    @Override
    public Optional<Newsletter> findNewsletterByJid(JidProvider jid) {
        return jid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.get(jid.toJid()));
    }

    @Override
    public Collection<Newsletter> newsletters() {
        return Collections.unmodifiableCollection(newsletters.values());
    }

    @Override
    public Newsletter addNewNewsletter(Jid newsletterJid) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var newsletter = new TemporaryNewsletter(newsletterJid);
        newsletters.put(newsletterJid, newsletter);
        return newsletter;
    }

    @Override
    public Optional<Newsletter> removeNewsletter(JidProvider newsletterJid) {
        return newsletterJid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.remove(newsletterJid.toJid()));
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
        return Collections.unmodifiableCollection(status.values());
    }
}
