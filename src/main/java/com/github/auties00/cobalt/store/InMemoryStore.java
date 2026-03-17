package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.media.MediaVisibility;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.PrivacySystemMessage;
import com.github.auties00.cobalt.model.newsletter.*;
import com.github.auties00.cobalt.model.setting.WallpaperSettings;
import com.github.auties00.cobalt.util.StorePathUtils;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
final class InMemoryStore extends AbstractWhatsAppStore {
    private static final String CHAT_PREFIX = "chat_";
    private static final String NEWSLETTER_PREFIX = "newsletter_";

    @ProtobufProperty(index = 82, type = ProtobufType.MAP, mapKeyType =  ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, Chat> chats;

    @ProtobufProperty(index = 83, type = ProtobufType.MAP, mapKeyType =  ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    final ConcurrentHashMap<Jid, Newsletter> newsletters;

    private final ConcurrentHashMap<String, ChatMessageInfo> status;

    private volatile Integer storeHashCode;

    private final ConcurrentMap<StoreJidPair, Integer> jidsHashCodes;

    private volatile Thread attributionThread;

    InMemoryStore(
            java.util.UUID uuid, java.lang.Long phoneNumber, com.github.auties00.cobalt.client.WhatsAppClientType clientType, java.time.Instant initializationTimeStamp, com.github.auties00.cobalt.model.jid.JidDevice device, com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel releaseChannel, boolean online, java.lang.String locale, java.lang.String name, java.lang.String verifiedName, java.net.URI profilePicture, java.lang.String about, com.github.auties00.cobalt.model.jid.Jid jid, com.github.auties00.cobalt.model.jid.Jid lid, java.lang.String businessAddress, java.lang.Double businessLongitude, java.lang.Double businessLatitude, java.lang.String businessDescription, java.lang.String businessWebsite, java.lang.String businessEmail, com.github.auties00.cobalt.model.business.profile.BusinessCategory businessCategory, java.util.concurrent.ConcurrentHashMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.contact.Contact> contacts, java.util.concurrent.ConcurrentHashMap<java.lang.String,com.github.auties00.cobalt.model.call.CallOffer> calls, java.util.concurrent.ConcurrentHashMap<com.github.auties00.cobalt.model.privacy.PrivacySettingType,com.github.auties00.cobalt.model.privacy.PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, com.github.auties00.cobalt.model.chat.ChatEphemeralTimer newChatsEphemeralTimer, com.github.auties00.cobalt.client.WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, java.lang.Integer registrationId, com.github.auties00.libsignal.key.SignalIdentityKeyPair noiseKeyPair, com.github.auties00.libsignal.key.SignalIdentityKeyPair identityKeyPair, com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity signedDeviceIdentity, com.github.auties00.libsignal.key.SignalSignedKeyPair signedKeyPair, java.util.LinkedHashMap<java.lang.Integer,com.github.auties00.libsignal.key.SignalPreKeyPair> preKeys, java.util.UUID fdid, byte[] deviceId, java.util.UUID advertisingId, byte[] identityId, byte[] backupToken, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.groups.SignalSenderKeyName,com.github.auties00.libsignal.groups.state.SignalSenderKeyRecord> senderKeys, java.util.LinkedHashMap<java.lang.String,com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey> appStateKeys, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.SignalProtocolAddress,com.github.auties00.libsignal.state.SignalSessionRecord> sessions, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.sync.SyncPatchType,com.github.auties00.cobalt.model.sync.SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Sticker> recentStickers, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Sticker> favouriteStickers, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.QuickReply> quickReplies, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.preference.Label> labels, com.github.auties00.cobalt.model.device.pairing.ClientAppVersion clientVersion, com.github.auties00.cobalt.model.device.pairing.ClientAppVersion companionVersion, java.time.Instant lastAdvCheckTime, java.util.concurrent.ConcurrentMap<com.github.auties00.libsignal.SignalProtocolAddress,com.github.auties00.libsignal.key.SignalIdentityPublicKey> remoteIdentities, java.util.concurrent.ConcurrentMap<java.lang.String,com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.business.BusinessVerifiedName> verifiedBusinessNames, java.nio.file.Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, com.github.auties00.cobalt.model.setting.ChatLockSettings chatLockSettings, java.util.List<com.github.auties00.cobalt.model.jid.Jid> favoriteChats, java.util.List<java.lang.String> primaryFeatures, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.jid.Jid,com.github.auties00.cobalt.model.chat.ChatMute> mentionEveryoneMuteExpirations, java.util.concurrent.ConcurrentMap<com.github.auties00.cobalt.model.sync.SyncPatchType,com.github.auties00.cobalt.store.AbstractWhatsAppStore.OrphanMutationEntries> orphanMutationEntries,
            ConcurrentHashMap<Jid, Chat> chats,
            ConcurrentHashMap<Jid, Newsletter> newsletters
    ) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, about, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsite, businessEmail, businessCategory, contacts, calls, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations, orphanMutationEntries);
        this.chats = chats;
        this.newsletters = newsletters;
        this.status = new ConcurrentHashMap<>();
        this.jidsHashCodes = new ConcurrentHashMap<>();
    }

    @Override
    public WhatsAppStore save() {
        var newHashCode = hashCode();
        if (storeHashCode != newHashCode) {
            synchronized (this) {
                if(storeHashCode != newHashCode) {
                    storeHashCode = newHashCode;
                    try (var executor = newVirtualThreadPerTaskExecutor()) {
                        executor.submit(() -> {
                            try {
                                serializeStore();
                            }catch (Throwable throwable) {
                                logger.log(WARNING, "Error while serializing store", throwable);
                            }
                        });

                        chats.forEach((_, chat) -> executor.submit(() -> {
                            try {
                                serializeChat(chat);
                            } catch (Throwable error) {
                                logger.log(WARNING, "Error while serializing chat", error);
                            }
                        }));

                        newsletters.forEach((_, newsletter) -> executor.submit(() -> {
                            try {
                                serializeNewsletter(newsletter);
                            } catch (Throwable error) {
                                logger.log(WARNING, "Error while serializing newsletter", error);
                            }
                        }));
                    }

                }
            }
        }
        return this;
    }

    private void serializeStore() throws IOException {
        var path = StorePathUtils.getSessionFile(clientType, directory, uuid.toString(), "store.proto");
        Files.createDirectories(path.getParent());
        var tempFile = Files.createTempFile(path.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            InMemoryStoreSpec.encode(this, ProtobufOutputStream.toStream(stream));
        }
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete() throws IOException {
        var folderPath = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
        StorePathUtils.deleteRecursively(folderPath);
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

    void startBackgroundDeserialization() {
        if(attributionThread == null) {
            synchronized (this) {
                if(attributionThread == null) {
                    attributionThread = Thread.startVirtualThread(this::deserializeChatsAndNewsletters);
                }
            }
        }
    }

    private void deserializeChatsAndNewsletters() {
        try {
            var sessionDirectory = StorePathUtils.getSessionDirectory(clientType, directory, uuid.toString());
            try (var walker = Files.walk(sessionDirectory); var executor = newVirtualThreadPerTaskExecutor()) {
                walker.forEach(path -> executor.submit(() -> {
                    try {
                        deserializeChatOrNewsletter(path);
                    } catch (IOException throwable) {
                        logger.log(ERROR, throwable);
                    }
                }));
            }
        } catch (Throwable throwable) {
            logger.log(ERROR, throwable);
        }
    }

    private void deserializeChatOrNewsletter(Path path) throws IOException {
        var fileName = path.getFileName().toString();
        if (fileName.startsWith(CHAT_PREFIX)) {
            deserializeChat(path);
        } else if (fileName.startsWith(NEWSLETTER_PREFIX)) {
            deserializeNewsletter(path);
        }
    }

    private void deserializeChat(Path chatFile) throws IOException {
        try (var stream = Files.newInputStream(chatFile)) {
            var chat = InMemoryStoreChatSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, chat.jid());
            jidsHashCodes.put(storeJidPair, chat.hashCode());
            chats.put(chat.jid(), chat);
        }
    }

    private void deserializeNewsletter(Path newsletterFile) throws IOException {
        try (var stream = Files.newInputStream(newsletterFile)) {
            var newsletter = InMemoryStoreNewsletterSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, newsletter.jid());
            jidsHashCodes.put(storeJidPair, newsletter.hashCode());
            newsletters.put(newsletter.jid(), newsletter);
        }
    }

    private void serializeChat(Chat chat) throws IOException {
        var outputFile = getMessagesContainerPathIfUpdated(chat.jid(), chat.hashCode(), CHAT_PREFIX);
        if (outputFile == null) {
            return;
        }

        var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            InMemoryStoreChatSpec.encode(chat, ProtobufOutputStream.toStream(stream));
        }
        Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void serializeNewsletter(Newsletter newsletter) throws IOException {
        var outputFile = getMessagesContainerPathIfUpdated(newsletter.jid(), newsletter.hashCode(), NEWSLETTER_PREFIX);
        if(outputFile == null) {
            return;
        }

        var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            InMemoryStoreNewsletterSpec.encode(newsletter, ProtobufOutputStream.toStream(stream));
        }
        Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path getMessagesContainerPathIfUpdated(Jid jid, int hashCode, String filePrefix) throws IOException {
        var identifier = new StoreJidPair(uuid, jid);
        var oldHashCode = jidsHashCodes.getOrDefault(identifier, -1);
        if (oldHashCode == hashCode) {
            return null;
        }
        jidsHashCodes.put(identifier, hashCode);
        var fileName = filePrefix + jid.user() + ".proto";
        return StorePathUtils.getSessionFile(clientType, directory, uuid.toString(), fileName);
    }

    private void handleSerializeError(Throwable error) {

        logger.log(ERROR, error);
    }

    private record StoreJidPair(UUID storeId, Jid jid) {

    }

    @Override
    public Optional<com.github.auties00.cobalt.model.chat.Chat> findChatByJid(JidProvider jid) {
        return switch (jid) {
            case null -> Optional.empty();
            case com.github.auties00.cobalt.model.chat.Chat chat -> Optional.of(chat);
            case Contact _, com.github.auties00.cobalt.model.newsletter.Newsletter _, Jid _, JidServer _-> {
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
                        // Multi-hop: oldLid → PN → chat, or oldLid → PN → currentLid → chat
                        var phone = findPhoneByLid(targetJid);
                        if(phone.isEmpty()) {
                            yield Optional.empty();
                        }
                        var phoneChat = chats.get(phone.get());
                        if(phoneChat != null) {
                            yield Optional.of(phoneChat);
                        }
                        yield findLidByPhone(phone.get())
                                .map(chats::get);
                    }
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
            case com.github.auties00.cobalt.model.chat.Chat chat -> findMessageById(chat, id);
            case com.github.auties00.cobalt.model.newsletter.Newsletter newsletter -> findMessageById(newsletter, id);
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

    @Override
    public Optional<NewsletterMessageInfo> findMessageById(com.github.auties00.cobalt.model.newsletter.Newsletter newsletter, String id) {
        return newsletter == null || id == null ? Optional.empty() : newsletter.messages()
                .parallelStream()
                .filter(entry -> Objects.equals(id, entry.key().id().orElse(null)) || Objects.equals(id, String.valueOf(entry.serverId())))
                .findFirst();
    }

    @Override
    public Optional<ChatMessageInfo> findMessageById(com.github.auties00.cobalt.model.chat.Chat chat, String id) {
        return chat == null || id == null ? Optional.empty() : chat.messages()
                .parallelStream()
                .filter(message -> Objects.equals(message.key().id().orElse(null), id))
                .findAny();
    }

    @Override
    public Collection<com.github.auties00.cobalt.model.chat.Chat> chats() {
        return Collections.unmodifiableCollection(chats.values());
    }

    @Override
    public com.github.auties00.cobalt.model.chat.Chat addNewChat(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        var chat = new InMemoryStoreChatBuilder()
                .jid(chatJid)
                .build();
        chats.put(chatJid, chat);
        return chat;
    }

    @Override
    public Optional<com.github.auties00.cobalt.model.chat.Chat> removeChat(JidProvider chatJid) {
        if(chatJid == null) {
            return Optional.empty();
        } else {
            var targetJid = chatJid.toJid();
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

    @Override
    public ChatMessageInfo addStatus(ChatMessageInfo messageInfo) {
        Objects.requireNonNull(messageInfo, "messageInfo cannot be null");
        messageInfo.key().id().ifPresent(id -> status.put(id, messageInfo));
        return messageInfo;
    }

    @Override
    public Optional<ChatMessageInfo> removeStatus(String id) {
        return Optional.ofNullable(status.remove(id));
    }

    @Override
    public Optional<com.github.auties00.cobalt.model.newsletter.Newsletter> findNewsletterByJid(JidProvider jid) {
        return jid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.get(jid.toJid()));
    }

    @Override
    public Collection<com.github.auties00.cobalt.model.newsletter.Newsletter> newsletters() {
        return Collections.unmodifiableCollection(newsletters.values());
    }

    @Override
    public com.github.auties00.cobalt.model.newsletter.Newsletter addNewNewsletter(Jid newsletterJid) {
        Objects.requireNonNull(newsletterJid, "newsletterJid cannot be null");
        var newsletter = new InMemoryStoreNewsletterBuilder()
                .jid(newsletterJid)
                .build();
        newsletters.put(newsletter.jid(), newsletter);
        return newsletter;
    }

    @Override
    public Optional<com.github.auties00.cobalt.model.newsletter.Newsletter> removeNewsletter(JidProvider newsletterJid) {
        return newsletterJid == null
                ? Optional.empty()
                : Optional.ofNullable(newsletters.remove(newsletterJid.toJid()));
    }

    @Override
    public Optional<? extends MessageInfo> findMessageByKey(MessageKey key) {
        var id = key.id();
        if(id.isEmpty()) {
            return Optional.empty();
        }

        var parentJid = key.parentJid();
        if(parentJid.isEmpty()) {
            return Optional.empty();
        }

        return findChatByJid(parentJid.get())
                .flatMap(chat -> chat.getMessageById(id.get()));
    }

    @Override
    public Collection<ChatMessageInfo> status() {
        return Collections.unmodifiableCollection(status.values());
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof InMemoryStore that
                            && super.equals(o)
                            && Objects.equals(this.chats, that.chats)
                            && Objects.equals(this.newsletters, that.newsletters)
                            && Objects.equals(this.status, that.status);
    }

    @Override
    public int hashCode() {
        var hashCode = super.hashCode();
        for(var entry : chats.entrySet()) {
            var value = entry.getValue();
            if(value != null) {
                hashCode = hashCode * 31 + value.hashCode();
            }
        }
        for(var entry : newsletters.entrySet()) {
            var value = entry.getValue();
            if(value != null) {
                hashCode = hashCode * 31 + value.hashCode();
            }
        }
        for(var entry : status.entrySet()) {
            var value = entry.getValue();
            if(value != null) {
                hashCode = hashCode * 31 + value.hashCode();
            }
        }
        return hashCode;
    }

    @ProtobufMessage
    static final class Chat extends com.github.auties00.cobalt.model.chat.Chat {
        Chat(Jid jid, Jid newJid, Jid oldJid, Instant lastMsgTimestamp, Integer unreadCount, Boolean readOnly, Boolean endOfHistoryTransfer, ChatEphemeralTimer ephemeralExpiration, Instant ephemeralSettingTimestamp, EndOfHistoryTransferType endOfHistoryTransferType, Instant conversationTimestamp, String name, String pHash, Boolean notSpam, Boolean archived, ChatDisappearingMode disappearingMode, Integer unreadMentionCount, Boolean markedAsUnread, List<GroupParticipant> participant, byte[] tcToken, Instant tcTokenTimestamp, byte[] contactPrimaryIdentityKey, Instant pinned, ChatMute mute, WallpaperSettings wallpaper, MediaVisibility mediaVisibility, Instant tcTokenSenderTimestamp, Boolean suspended, Boolean terminated, Long createdAt, String createdBy, String description, Boolean support, Boolean isParentGroup, String parentGroupId, Boolean isDefaultSubgroup, String displayName, Jid phoneNumberJid, Boolean shareOwnPhoneNumber, Boolean phoneNumberhDuplicateLidThread, Jid lid, String username, String lidOriginType, Integer commentsCount, Boolean locked, PrivacySystemMessage systemMessageToInsert, Boolean capiCreatedGroup, Jid accountLid, Boolean limitSharing, Instant limitSharingSettingTimestamp, ChatLimitSharing.TriggerType limitSharingTrigger, Boolean limitSharingInitiatedByMe, Boolean maibaAiThreadEnabled) {
            super(jid, newJid, oldJid, lastMsgTimestamp, unreadCount, readOnly, endOfHistoryTransfer, ephemeralExpiration, ephemeralSettingTimestamp, endOfHistoryTransferType, conversationTimestamp, name, pHash, notSpam, archived, disappearingMode, unreadMentionCount, markedAsUnread, participant, tcToken, tcTokenTimestamp, contactPrimaryIdentityKey, pinned, mute, wallpaper, mediaVisibility, tcTokenSenderTimestamp, suspended, terminated, createdAt, createdBy, description, support, isParentGroup, parentGroupId, isDefaultSubgroup, displayName, phoneNumberJid, shareOwnPhoneNumber, phoneNumberhDuplicateLidThread, lid, username, lidOriginType, commentsCount, locked, systemMessageToInsert, capiCreatedGroup, accountLid, limitSharing, limitSharingSettingTimestamp, limitSharingTrigger, limitSharingInitiatedByMe, maibaAiThreadEnabled);
        }

        @Override
        public SequencedCollection<ChatMessageInfo> messages() {
            return List.of();
        }

        @Override
        public void addMessage(ChatMessageInfo info) {
            Objects.requireNonNull(info);
        }

        @Override
        public boolean removeMessage(String id) {
            return false;
        }

        @Override
        public void removeMessages() {

        }

        @Override
        public Optional<ChatMessageInfo> getMessageById(String id) {
            return Optional.empty();
        }

        @Override
        public Optional<ChatMessageInfo> newestMessage() {
            return Optional.empty();
        }

        @Override
        public Optional<ChatMessageInfo> oldestMessage() {
            return Optional.empty();
        }
    }

    @ProtobufMessage
    static final class Newsletter extends com.github.auties00.cobalt.model.newsletter.Newsletter {
        Newsletter(Jid jid, NewsletterState state, NewsletterMetadata metadata, NewsletterViewerMetadata viewerMetadata, int unreadMessagesCount, Instant timestamp) {
            super(jid, state, metadata, viewerMetadata, unreadMessagesCount, timestamp);
        }

        @Override
        public void addMessage(NewsletterMessageInfo info) {
            Objects.requireNonNull(info, "info cannot be null");
        }

        @Override
        public boolean removeMessage(String messageId) {
            return false;
        }

        @Override
        public void removeMessages() {

        }

        @Override
        public SequencedCollection<NewsletterMessageInfo> messages() {
            return List.of();
        }

        @Override
        public Optional<NewsletterMessageInfo> getMessageById(String messageId) {
            return Optional.empty();
        }

        @Override
        public Optional<NewsletterMessageInfo> oldestMessage() {
            return Optional.empty();
        }

        @Override
        public Optional<NewsletterMessageInfo> newestMessage() {
            return Optional.empty();
        }
    }

}
