package com.github.auties00.cobalt.model.sync.history;

import com.github.auties00.cobalt.model.call.CallLog;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.chat.group.GroupPastParticipants;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.media.StickerMetadata;
import com.github.auties00.cobalt.model.jid.migration.PhoneNumberToLIDMapping;
import com.github.auties00.cobalt.model.setting.GlobalSettings;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.*;

@ProtobufMessage(name = "HistorySync")
public final class HistorySync {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    HistorySyncType syncType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<Chat> chats;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<ChatMessageInfo> statusV3Messages;

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


    HistorySync(HistorySyncType syncType, List<Chat> chats, List<ChatMessageInfo> statusV3Messages, Integer chunkOrder, Integer progress, List<Pushname> pushnames, GlobalSettings globalSettings, byte[] threadIdUserSecret, Integer threadDsTimeframeOffset, List<StickerMetadata> recentStickers, List<GroupPastParticipants> pastParticipants, List<CallLog> callLogs, BotAIWaitListState aiWaitListState, List<PhoneNumberToLIDMapping> phoneNumberToLidMappings, String companionMetaNonce, byte[] shareableChatIdentifierEncryptionKey, List<Account> accounts) {
        this.syncType = Objects.requireNonNull(syncType);
        this.chats = chats;
        this.statusV3Messages = statusV3Messages;
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

    public HistorySyncType syncType() {
        return syncType;
    }

    public List<Chat> chats() {
        return chats == null ? List.of() : Collections.unmodifiableList(chats);
    }

    public List<ChatMessageInfo> statusV3Messages() {
        return statusV3Messages == null ? List.of() : Collections.unmodifiableList(statusV3Messages);
    }

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

    public HistorySync setSyncType(HistorySyncType syncType) {
        this.syncType = syncType;
        return this;
    }

    public HistorySync setChats(List<Chat> chats) {
        this.chats = chats;
        return this;
    }

    public HistorySync setStatusV3Messages(List<ChatMessageInfo> statusV3Messages) {
        this.statusV3Messages = statusV3Messages;
        return this;
    }

    public HistorySync setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
        return this;
    }

    public HistorySync setProgress(Integer progress) {
        this.progress = progress;
        return this;
    }

    public HistorySync setPushnames(List<Pushname> pushnames) {
        this.pushnames = pushnames;
        return this;
    }

    public HistorySync setGlobalSettings(GlobalSettings globalSettings) {
        this.globalSettings = globalSettings;
        return this;
    }

    public HistorySync setThreadIdUserSecret(byte[] threadIdUserSecret) {
        this.threadIdUserSecret = threadIdUserSecret;
        return this;
    }

    public HistorySync setThreadDsTimeframeOffset(Integer threadDsTimeframeOffset) {
        this.threadDsTimeframeOffset = threadDsTimeframeOffset;
        return this;
    }

    public HistorySync setRecentStickers(List<StickerMetadata> recentStickers) {
        this.recentStickers = recentStickers;
        return this;
    }

    public HistorySync setPastParticipants(List<GroupPastParticipants> pastParticipants) {
        this.pastParticipants = pastParticipants;
        return this;
    }

    public HistorySync setCallLogRecords(List<CallLog> callLogs) {
        this.callLogs = callLogs;
        return this;
    }

    public HistorySync setAiWaitListState(BotAIWaitListState aiWaitListState) {
        this.aiWaitListState = aiWaitListState;
        return this;
    }

    public HistorySync setPhoneNumberToLidMappings(List<PhoneNumberToLIDMapping> phoneNumberToLidMappings) {
        this.phoneNumberToLidMappings = phoneNumberToLidMappings;
        return this;
    }

    public HistorySync setCompanionMetaNonce(String companionMetaNonce) {
        this.companionMetaNonce = companionMetaNonce;
        return this;
    }

    public HistorySync setShareableChatIdentifierEncryptionKey(byte[] shareableChatIdentifierEncryptionKey) {
        this.shareableChatIdentifierEncryptionKey = shareableChatIdentifierEncryptionKey;
        return this;
    }

    public HistorySync setAccounts(List<Account> accounts) {
        this.accounts = accounts;
        return this;
    }

    @ProtobufEnum(name = "HistorySync.BotAIWaitListState")
    public static enum BotAIWaitListState {
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
    public static enum HistorySyncType {
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

        public Account setLid(String lid) {
            this.lid = lid;
            return this;
        }

        public Account setUsername(String username) {
            this.username = username;
            return this;
        }

        public Account setCountryCode(String countryCode) {
            this.countryCode = countryCode;
            return this;
        }

        public Account setUsernameDeleted(Boolean isUsernameDeleted) {
            this.isUsernameDeleted = isUsernameDeleted;
            return this;
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

        public Pushname setId(String id) {
            this.id = id;
            return this;
        }

        public Pushname setPushname(String pushname) {
            this.pushname = pushname;
            return this;
        }
    }
}
