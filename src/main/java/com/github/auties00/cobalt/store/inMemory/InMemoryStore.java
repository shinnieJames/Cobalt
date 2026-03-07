package com.github.auties00.cobalt.store.inMemory;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.business.BusinessVerifiedNameCertificate;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.call.CallOffer;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKey;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidDevice;
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
import com.github.auties00.cobalt.model.sync.SyncHashValue;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.store.AbstractWhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStore;
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

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;

@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public final class InMemoryStore extends AbstractWhatsAppStore {
    private static final String CHAT_PREFIX = "chat_";
    private static final String NEWSLETTER_PREFIX = "newsletter_";

    @ProtobufProperty(index = 82, type = ProtobufType.MAP, mapKeyType =  ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentHashMap<Jid, InMemoryChat> chats;

    @ProtobufProperty(index = 83, type = ProtobufType.MAP, mapKeyType =  ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
    private final ConcurrentHashMap<Jid, InMemoryNewsletter> newsletters;

    private final ConcurrentHashMap<String, ChatMessageInfo> status;

    private volatile Integer storeHashCode;

    private final ConcurrentMap<StoreJidPair, Integer> jidsHashCodes;

    private volatile Thread attributionThread;

    InMemoryStore(UUID uuid, Long phoneNumber, WhatsAppClientType clientType, Instant initializationTimeStamp, JidDevice device, ClientReleaseChannel releaseChannel, boolean online, String locale, String name, String verifiedName, URI profilePicture, String about, Jid jid, Jid lid, String businessAddress, Double businessLongitude, Double businessLatitude, String businessDescription, String businessWebsite, String businessEmail, BusinessCategory businessCategory, ConcurrentHashMap<Jid, Contact> contacts, ConcurrentHashMap<String, CallOffer> calls, ConcurrentHashMap<PrivacySettingType, PrivacySettingEntry> privacySettings, boolean unarchiveChats, boolean twentyFourHourFormat, ChatEphemeralTimer newChatsEphemeralTimer, WhatsAppWebClientHistory webHistoryPolicy, boolean automaticPresenceUpdates, boolean automaticMessageReceipts, boolean checkPatchMacs, boolean syncedChats, boolean syncedContacts, boolean syncedNewsletters, boolean syncedStatus, boolean syncedWebAppState, boolean syncedBusinessCertificate, Integer registrationId, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, ADVSignedDeviceIdentity signedDeviceIdentity, SignalSignedKeyPair signedKeyPair, LinkedHashMap<Integer, SignalPreKeyPair> preKeys, UUID fdid, byte[] deviceId, UUID advertisingId, byte[] identityId, byte[] backupToken, ConcurrentMap<SignalSenderKeyName, SignalSenderKeyRecord> senderKeys, LinkedHashMap<String, AppStateSyncKey> appStateKeys, ConcurrentMap<SignalProtocolAddress, SignalSessionRecord> sessions, ConcurrentMap<SyncPatchType, SyncHashValue> hashStates, boolean registered, boolean showSecurityNotifications, ConcurrentMap<String, Sticker> recentStickers, ConcurrentMap<String, Sticker> favouriteStickers, ConcurrentMap<String, QuickReply> quickReplies, ConcurrentMap<String, Label> labels, ClientAppVersion clientVersion, ClientAppVersion companionVersion, Instant lastAdvCheckTime, ConcurrentMap<SignalProtocolAddress, SignalIdentityPublicKey> remoteIdentities, ConcurrentMap<String, MissingDeviceSyncKey> missingSyncKeys, byte[] advSecretKey, ConcurrentMap<Jid, BusinessVerifiedNameCertificate> verifiedBusinessNames, Path directory, boolean primaryDeviceSupportsSyncdRecovery, boolean disableLinkPreviews, boolean relayAllCalls, boolean externalWebBeta, com.github.auties00.cobalt.model.setting.ChatLockSettings chatLockSettings, List<Jid> favoriteChats, List<String> primaryFeatures, ConcurrentMap<Jid, com.github.auties00.cobalt.model.chat.ChatMute> mentionEveryoneMuteExpirations) {
        super(uuid, phoneNumber, clientType, initializationTimeStamp, device, releaseChannel, online, locale, name, verifiedName, profilePicture, about, jid, lid, businessAddress, businessLongitude, businessLatitude, businessDescription, businessWebsite, businessEmail, businessCategory, contacts, calls, privacySettings, unarchiveChats, twentyFourHourFormat, newChatsEphemeralTimer, webHistoryPolicy, automaticPresenceUpdates, automaticMessageReceipts, checkPatchMacs, syncedChats, syncedContacts, syncedNewsletters, syncedStatus, syncedWebAppState, syncedBusinessCertificate, registrationId, noiseKeyPair, identityKeyPair, signedDeviceIdentity, signedKeyPair, preKeys, fdid, deviceId, advertisingId, identityId, backupToken, senderKeys, appStateKeys, sessions, hashStates, registered, showSecurityNotifications, recentStickers, favouriteStickers, quickReplies, labels, clientVersion, companionVersion, lastAdvCheckTime, remoteIdentities, missingSyncKeys, advSecretKey, verifiedBusinessNames, directory, primaryDeviceSupportsSyncdRecovery, disableLinkPreviews, relayAllCalls, externalWebBeta, chatLockSettings, favoriteChats, primaryFeatures, mentionEveryoneMuteExpirations);
        this.chats = new ConcurrentHashMap<>();
        this.newsletters = new ConcurrentHashMap<>();
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
            var chat = InMemoryChatSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, chat.jid());
            jidsHashCodes.put(storeJidPair, chat.hashCode());
            chats.put(chat.jid(), chat);
        }
    }

    private void deserializeNewsletter(Path newsletterFile) throws IOException {
        try (var stream = Files.newInputStream(newsletterFile)) {
            var newsletter = InMemoryNewsletterSpec.decode(ProtobufInputStream.fromStream(stream));
            var storeJidPair = new StoreJidPair(uuid, newsletter.jid());
            jidsHashCodes.put(storeJidPair, newsletter.hashCode());
            newsletters.put(newsletter.jid(), newsletter);
        }
    }

    private void serializeChat(InMemoryChat chat) throws IOException {
        var outputFile = getMessagesContainerPathIfUpdated(chat.jid(), chat.hashCode(), CHAT_PREFIX);
        if (outputFile == null) {
            return;
        }

        var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            InMemoryChatSpec.encode(chat, ProtobufOutputStream.toStream(stream));
        }
        Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void serializeNewsletter(InMemoryNewsletter newsletter) throws IOException {
        var outputFile = getMessagesContainerPathIfUpdated(newsletter.jid(), newsletter.hashCode(), NEWSLETTER_PREFIX);
        if(outputFile == null) {
            return;
        }

        var tempFile = Files.createTempFile(outputFile.getFileName().toString(), ".tmp");
        try (var stream = Files.newOutputStream(tempFile)) {
            InMemoryNewsletterSpec.encode(newsletter, ProtobufOutputStream.toStream(stream));
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

    @Override
    public Optional<NewsletterMessageInfo> findMessageById(Newsletter newsletter, String id) {
        return newsletter == null || id == null ? Optional.empty() : newsletter.messages()
                .parallelStream()
                .filter(entry -> Objects.equals(id, entry.key().id().orElse(null)) || Objects.equals(id, String.valueOf(entry.serverId())))
                .findFirst();
    }

    @Override
    public Optional<ChatMessageInfo> findMessageById(Chat chat, String id) {
        return chat == null || id == null ? Optional.empty() : chat.messages()
                .parallelStream()
                .filter(message -> Objects.equals(message.key().id().orElse(null), id))
                .findAny();
    }

    @Override
    public Collection<Chat> chats() {
        return Collections.unmodifiableCollection(chats.values());
    }

    @Override
    public Chat addNewChat(Jid chatJid) {
        Objects.requireNonNull(chatJid, "chatJid cannot be null");
        var chat = new InMemoryChatBuilder()
                .jid(chatJid)
                .build();
        chats.put(chatJid, chat);
        return chat;
    }

    @Override
    public Optional<Chat> removeChat(JidProvider chatJid) {
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
        var newsletter = new InMemoryNewsletterBuilder()
                .jid(newsletterJid)
                .build();
        newsletters.put(newsletter.jid(), newsletter);
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
}
