package com.github.auties00.cobalt.model.sync.history;

import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.chat.*;
import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.chat.group.GroupPastParticipants;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.PhoneNumberToLIDMapping;
import com.github.auties00.cobalt.model.media.MediaVisibility;
import com.github.auties00.cobalt.model.media.StickerMetadata;
import com.github.auties00.cobalt.model.message.PrivacySystemMessage;
import com.github.auties00.cobalt.model.setting.GlobalSettings;
import com.github.auties00.cobalt.model.setting.WallpaperSettings;
import com.github.auties00.collections.ConcurrentLinkedHashMap;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import it.auties.protobuf.stream.ProtobufOutputStream;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "HistorySync")
public abstract sealed class HistorySync {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    HistorySyncType syncType;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer chunkOrder;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer progress;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    List<Pushname> pushnames;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    GlobalSettings globalSettings;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] threadIdUserSecret;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT32)
    Integer threadDsTimeframeOffset;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    List<StickerMetadata> recentStickers;

    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    List<GroupPastParticipants> pastParticipants;

    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    List<CallLog> callLogs;

    @ProtobufProperty(index = 14, type = ProtobufType.ENUM)
    BotAIWaitListState aiWaitListState;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    List<PhoneNumberToLIDMapping> phoneNumberToLidMappings;

    @ProtobufProperty(index = 16, type = ProtobufType.STRING)
    String companionMetaNonce;

    @ProtobufProperty(index = 17, type = ProtobufType.BYTES)
    byte[] shareableChatIdentifierEncryptionKey;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    List<Account> accounts;


    HistorySync(HistorySyncType syncType, Integer chunkOrder, Integer progress, List<Pushname> pushnames, GlobalSettings globalSettings, byte[] threadIdUserSecret, Integer threadDsTimeframeOffset, List<StickerMetadata> recentStickers, List<GroupPastParticipants> pastParticipants, List<CallLog> callLogs, BotAIWaitListState aiWaitListState, List<PhoneNumberToLIDMapping> phoneNumberToLidMappings, String companionMetaNonce, byte[] shareableChatIdentifierEncryptionKey, List<Account> accounts) {
        this.syncType = Objects.requireNonNull(syncType);
        this.chunkOrder = chunkOrder;
        this.progress = progress;
        this.pushnames = pushnames;
        this.globalSettings = globalSettings;
        this.threadIdUserSecret = threadIdUserSecret;
        this.threadDsTimeframeOffset = threadDsTimeframeOffset;
        this.recentStickers = recentStickers;
        this.pastParticipants = pastParticipants;
        this.callLogs = callLogs;
        this.aiWaitListState = aiWaitListState;
        this.phoneNumberToLidMappings = phoneNumberToLidMappings;
        this.companionMetaNonce = companionMetaNonce;
        this.shareableChatIdentifierEncryptionKey = shareableChatIdentifierEncryptionKey;
        this.accounts = accounts;
    }

    public static HistorySync ofFull(ProtobufInputStream stream) {
        return HistorySyncFullSpec.decode(stream);
    }

    public static HistorySync ofLight(ProtobufInputStream stream) {
        return HistorySyncLightSpec.decode(stream);
    }

    public HistorySyncType syncType() {
        return syncType;
    }

    public abstract List<? extends Chat> chats();

    public abstract List<ChatMessageInfo> statusV3Messages();

    public OptionalInt chunkOrder() {
        return chunkOrder == null ? OptionalInt.empty() : OptionalInt.of(chunkOrder);
    }

    public OptionalInt progress() {
        return progress == null ? OptionalInt.empty() : OptionalInt.of(progress);
    }

    public List<Pushname> pushnames() {
        return pushnames == null ? List.of() : Collections.unmodifiableList(pushnames);
    }

    public Optional<GlobalSettings> globalSettings() {
        return Optional.ofNullable(globalSettings);
    }

    public Optional<byte[]> threadIdUserSecret() {
        return Optional.ofNullable(threadIdUserSecret);
    }

    public OptionalInt threadDsTimeframeOffset() {
        return threadDsTimeframeOffset == null ? OptionalInt.empty() : OptionalInt.of(threadDsTimeframeOffset);
    }

    public List<StickerMetadata> recentStickers() {
        return recentStickers == null ? List.of() : Collections.unmodifiableList(recentStickers);
    }

    public List<GroupPastParticipants> pastParticipants() {
        return pastParticipants == null ? List.of() : Collections.unmodifiableList(pastParticipants);
    }

    public List<CallLog> callLogRecords() {
        return callLogs == null ? List.of() : Collections.unmodifiableList(callLogs);
    }

    public Optional<BotAIWaitListState> aiWaitListState() {
        return Optional.ofNullable(aiWaitListState);
    }

    public List<PhoneNumberToLIDMapping> phoneNumberToLidMappings() {
        return phoneNumberToLidMappings == null ? List.of() : Collections.unmodifiableList(phoneNumberToLidMappings);
    }

    public Optional<String> companionMetaNonce() {
        return Optional.ofNullable(companionMetaNonce);
    }

    public Optional<byte[]> shareableChatIdentifierEncryptionKey() {
        return Optional.ofNullable(shareableChatIdentifierEncryptionKey);
    }

    public List<Account> accounts() {
        return accounts == null ? List.of() : Collections.unmodifiableList(accounts);
    }

    public void setSyncType(HistorySyncType syncType) {
        this.syncType = syncType;
    }

    public void setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void setPushnames(List<Pushname> pushnames) {
        this.pushnames = pushnames;
    }

    public void setGlobalSettings(GlobalSettings globalSettings) {
        this.globalSettings = globalSettings;
    }

    public void setThreadIdUserSecret(byte[] threadIdUserSecret) {
        this.threadIdUserSecret = threadIdUserSecret;
    }

    public void setThreadDsTimeframeOffset(Integer threadDsTimeframeOffset) {
        this.threadDsTimeframeOffset = threadDsTimeframeOffset;
    }

    public void setRecentStickers(List<StickerMetadata> recentStickers) {
        this.recentStickers = recentStickers;
    }

    public void setPastParticipants(List<GroupPastParticipants> pastParticipants) {
        this.pastParticipants = pastParticipants;
    }

    public void setCallLogRecords(List<CallLog> callLogs) {
        this.callLogs = callLogs;
    }

    public void setAiWaitListState(BotAIWaitListState aiWaitListState) {
        this.aiWaitListState = aiWaitListState;
    }

    public void setPhoneNumberToLidMappings(List<PhoneNumberToLIDMapping> phoneNumberToLidMappings) {
        this.phoneNumberToLidMappings = phoneNumberToLidMappings;
    }

    public void setCompanionMetaNonce(String companionMetaNonce) {
        this.companionMetaNonce = companionMetaNonce;
    }

    public void setShareableChatIdentifierEncryptionKey(byte[] shareableChatIdentifierEncryptionKey) {
        this.shareableChatIdentifierEncryptionKey = shareableChatIdentifierEncryptionKey;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public abstract void writeTo(ProtobufOutputStream<?> out) throws IOException;

    @ProtobufMessage
    static final class Full extends HistorySync {
        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        List<Chat> chats;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        List<ChatMessageInfo> statusV3Messages;

        Full(HistorySyncType syncType, Integer chunkOrder, Integer progress, List<Pushname> pushnames, GlobalSettings globalSettings, byte[] threadIdUserSecret, Integer threadDsTimeframeOffset, List<StickerMetadata> recentStickers, List<GroupPastParticipants> pastParticipants, List<CallLog> callLogs, BotAIWaitListState aiWaitListState, List<PhoneNumberToLIDMapping> phoneNumberToLidMappings, String companionMetaNonce, byte[] shareableChatIdentifierEncryptionKey, List<Account> accounts, List<Chat> chats, List<ChatMessageInfo> statusV3Messages) {
            super(syncType, chunkOrder, progress, pushnames, globalSettings, threadIdUserSecret, threadDsTimeframeOffset, recentStickers, pastParticipants, callLogs, aiWaitListState, phoneNumberToLidMappings, companionMetaNonce, shareableChatIdentifierEncryptionKey, accounts);
            this.chats = chats;
            this.statusV3Messages = statusV3Messages;
        }

        @Override
        public List<Chat> chats() {
            return chats == null ? List.of() : Collections.unmodifiableList(chats);
        }

        @Override
        public List<ChatMessageInfo> statusV3Messages() {
            return statusV3Messages == null ? List.of() : Collections.unmodifiableList(statusV3Messages);
        }

        @Override
        public void writeTo(ProtobufOutputStream<?> out) {
            HistorySyncFullSpec.encode(this, out);
        }

        @ProtobufMessage
        static final class Chat extends com.github.auties00.cobalt.model.chat.Chat {
            @ProtobufProperty(index = 2, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.MESSAGE)
            final ConcurrentLinkedHashMap<String, HistorySyncMsg> messages;

            Chat(Jid jid, Jid newJid, Jid oldJid, Instant lastMsgTimestamp, Integer unreadCount, Boolean readOnly, Boolean endOfHistoryTransfer, ChatEphemeralTimer ephemeralExpiration, Instant ephemeralSettingTimestamp, EndOfHistoryTransferType endOfHistoryTransferType, Instant conversationTimestamp, String name, String pHash, Boolean notSpam, Boolean archived, ChatDisappearingMode disappearingMode, Integer unreadMentionCount, Boolean markedAsUnread, List<GroupParticipant> participant, byte[] tcToken, Instant tcTokenTimestamp, byte[] contactPrimaryIdentityKey, Instant pinnedTimestamp, ChatMute mute, WallpaperSettings wallpaper, MediaVisibility mediaVisibility, Instant tcTokenSenderTimestamp, Boolean suspended, Boolean terminated, Long createdAt, String createdBy, String description, Boolean support, Boolean isParentGroup, String parentGroupId, Boolean isDefaultSubgroup, String displayName, Jid phoneNumberJid, Boolean shareOwnPhoneNumber, Boolean phoneNumberhDuplicateLidThread, Jid lid, String username, String lidOriginType, Integer commentsCount, Boolean locked, PrivacySystemMessage systemMessageToInsert, Boolean capiCreatedGroup, Jid accountLid, Boolean limitSharing, Instant limitSharingSettingTimestamp, ChatLimitSharing.TriggerType limitSharingTrigger, Boolean limitSharingInitiatedByMe, Boolean maibaAiThreadEnabled, ConcurrentLinkedHashMap<String, HistorySyncMsg> messages) {
                super(jid, newJid, oldJid, lastMsgTimestamp, unreadCount, readOnly, endOfHistoryTransfer, ephemeralExpiration, ephemeralSettingTimestamp, endOfHistoryTransferType, conversationTimestamp, name, pHash, notSpam, archived, disappearingMode, unreadMentionCount, markedAsUnread, participant, tcToken, tcTokenTimestamp, contactPrimaryIdentityKey, pinnedTimestamp, mute, wallpaper, mediaVisibility, tcTokenSenderTimestamp, suspended, terminated, createdAt, createdBy, description, support, isParentGroup, parentGroupId, isDefaultSubgroup, displayName, phoneNumberJid, shareOwnPhoneNumber, phoneNumberhDuplicateLidThread, lid, username, lidOriginType, commentsCount, locked, systemMessageToInsert, capiCreatedGroup, accountLid, limitSharing, limitSharingSettingTimestamp, limitSharingTrigger, limitSharingInitiatedByMe, maibaAiThreadEnabled);
                this.messages = messages;
            }

            @Override
            public SequencedCollection<ChatMessageInfo> messages() {
                return createMessagesView(messages.sequencedValues());
            }

            private SequencedCollection<ChatMessageInfo> createMessagesView(SequencedCollection<HistorySyncMsg> data) {
                return new SequencedCollection<>() {
                    @Override
                    public SequencedCollection<ChatMessageInfo> reversed() {
                        return createMessagesView(data.reversed());
                    }

                    @Override
                    public int size() {
                        return data.size();
                    }

                    @Override
                    public boolean isEmpty() {
                        return data.isEmpty();
                    }

                    @Override
                    public boolean contains(Object o) {
                        if(!(o instanceof ChatMessageInfo chatMessageInfo)) {
                            return false;
                        }

                        var id = chatMessageInfo.key().id();
                        return id.isPresent()
                               && messages.containsKey(id.get());
                    }

                    @Override
                    public Iterator<ChatMessageInfo> iterator() {
                        var delegate = data.iterator();
                        return new Iterator<>() {
                            @Override
                            public boolean hasNext() {
                                return delegate.hasNext();
                            }

                            @Override
                            public ChatMessageInfo next() {
                                var result = delegate.next();
                                return result != null ? result.message : null;
                            }
                        };
                    }

                    @Override
                    public Object[] toArray() {
                        return data.toArray();
                    }

                    @Override
                    public <T> T[] toArray(T[] a) {
                        return data.toArray(a);
                    }

                    @Override
                    public boolean add(ChatMessageInfo info) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean remove(Object o) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean containsAll(Collection<?> c) {
                        for (var entry : c) {
                            if (!contains(entry)) {
                                return false;
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean addAll(Collection<? extends ChatMessageInfo> c) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean removeAll(Collection<?> c) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public boolean retainAll(Collection<?> c) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void clear() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public void addMessage(ChatMessageInfo info) {
                Objects.requireNonNull(info, "info cannot be null");
                var id = info.key()
                        .id()
                        .orElseThrow(() -> new NullPointerException("id cannot be null"));
                var msg = new HistorySyncMsgBuilder()
                        .msgOrderId(-1L)
                        .message(info)
                        .build();
                messages.put(id, msg);
            }

            @Override
            public boolean removeMessage(String id) {
                return messages.get(id) != null;
            }

            @Override
            public void removeMessages() {
                messages.clear();
            }

            @Override
            public Optional<ChatMessageInfo> getMessageById(String id) {
                var msg = messages.get(id);
                if(msg == null) {
                    return Optional.empty();
                } else {
                    return msg.message();
                }
            }

            @Override
            public Optional<ChatMessageInfo> newestMessage() {
                var entry = messages.lastEntry();
                if(entry == null) {
                    return Optional.empty();
                } else {
                    return entry.getValue().message();
                }
            }

            @Override
            public Optional<ChatMessageInfo> oldestMessage() {
                var entry = messages.firstEntry();
                if(entry == null) {
                    return Optional.empty();
                } else {
                    return entry.getValue().message();
                }
            }
        }
    }

    @ProtobufMessage
    static final class Light extends HistorySync {
        Light(HistorySyncType syncType, Integer chunkOrder, Integer progress, List<Pushname> pushnames, GlobalSettings globalSettings, byte[] threadIdUserSecret, Integer threadDsTimeframeOffset, List<StickerMetadata> recentStickers, List<GroupPastParticipants> pastParticipants, List<CallLog> callLogs, BotAIWaitListState aiWaitListState, List<PhoneNumberToLIDMapping> phoneNumberToLidMappings, String companionMetaNonce, byte[] shareableChatIdentifierEncryptionKey, List<Account> accounts) {
            super(syncType, chunkOrder, progress, pushnames, globalSettings, threadIdUserSecret, threadDsTimeframeOffset, recentStickers, pastParticipants, callLogs, aiWaitListState, phoneNumberToLidMappings, companionMetaNonce, shareableChatIdentifierEncryptionKey, accounts);
        }

        @Override
        public List<Chat> chats() {
            return List.of();
        }

        @Override
        public List<ChatMessageInfo> statusV3Messages() {
            return List.of();
        }

        @Override
        public void writeTo(ProtobufOutputStream<?> out) {
            HistorySyncLightSpec.encode(this, out);
        }
    }

    @ProtobufEnum(name = "HistorySync.BotAIWaitListState")
    public enum BotAIWaitListState {
        IN_WAITLIST(0),
        AI_AVAILABLE(1);

        BotAIWaitListState(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "HistorySync.HistorySyncType")
    public enum HistorySyncType {
        INITIAL_BOOTSTRAP(0),
        INITIAL_STATUS_V3(1),
        FULL(2),
        RECENT(3),
        PUSH_NAME(4),
        NON_BLOCKING_DATA(5),
        ON_DEMAND(6);

        HistorySyncType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Account")
    public static final class Account {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String lid;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String username;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String countryCode;

        @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
        Boolean isUsernameDeleted;


        Account(String lid, String username, String countryCode, Boolean isUsernameDeleted) {
            this.lid = lid;
            this.username = username;
            this.countryCode = countryCode;
            this.isUsernameDeleted = isUsernameDeleted;
        }

        public Optional<String> lid() {
            return Optional.ofNullable(lid);
        }

        public Optional<String> username() {
            return Optional.ofNullable(username);
        }

        public Optional<String> countryCode() {
            return Optional.ofNullable(countryCode);
        }

        public boolean isUsernameDeleted() {
            return isUsernameDeleted != null && isUsernameDeleted;
        }

        public void setLid(String lid) {
            this.lid = lid;
    }

        public void setUsername(String username) {
            this.username = username;
    }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
    }

        public void setUsernameDeleted(Boolean isUsernameDeleted) {
            this.isUsernameDeleted = isUsernameDeleted;
    }
    }

    @ProtobufMessage(name = "Pushname")
    public static final class Pushname {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String id;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String pushname;


        Pushname(String id, String pushname) {
            this.id = id;
            this.pushname = pushname;
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public Optional<String> pushname() {
            return Optional.ofNullable(pushname);
        }

        public void setId(String id) {
            this.id = id;
    }

        public void setPushname(String pushname) {
            this.pushname = pushname;
    }
    }
}
