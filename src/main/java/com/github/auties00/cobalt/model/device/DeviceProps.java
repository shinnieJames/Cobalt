package com.github.auties00.cobalt.model.device;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "DeviceProps")
public final class DeviceProps {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String os;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    AppVersion version;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    DevicePlatformType platformType;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean requireFullSync;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    HistorySyncConfig historySyncConfig;


    DeviceProps(String os, AppVersion version, DevicePlatformType platformType, Boolean requireFullSync, HistorySyncConfig historySyncConfig) {
        this.os = os;
        this.version = version;
        this.platformType = platformType;
        this.requireFullSync = requireFullSync;
        this.historySyncConfig = historySyncConfig;
    }

    public Optional<String> os() {
        return Optional.ofNullable(os);
    }

    public Optional<AppVersion> version() {
        return Optional.ofNullable(version);
    }

    public Optional<DevicePlatformType> platformType() {
        return Optional.ofNullable(platformType);
    }

    public boolean requireFullSync() {
        return requireFullSync != null && requireFullSync;
    }

    public Optional<HistorySyncConfig> historySyncConfig() {
        return Optional.ofNullable(historySyncConfig);
    }

    public DeviceProps setOs(String os) {
        this.os = os;
        return this;
    }

    public DeviceProps setVersion(AppVersion version) {
        this.version = version;
        return this;
    }

    public DeviceProps setPlatformType(DevicePlatformType platformType) {
        this.platformType = platformType;
        return this;
    }

    public DeviceProps setRequireFullSync(Boolean requireFullSync) {
        this.requireFullSync = requireFullSync;
        return this;
    }

    public DeviceProps setHistorySyncConfig(HistorySyncConfig historySyncConfig) {
        this.historySyncConfig = historySyncConfig;
        return this;
    }

    @ProtobufMessage(name = "DeviceProps.AppVersion")
    public static final class AppVersion {
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
        Integer primary;

        @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
        Integer secondary;

        @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
        Integer tertiary;

        @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
        Integer quaternary;

        @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
        Integer quinary;


        AppVersion(Integer primary, Integer secondary, Integer tertiary, Integer quaternary, Integer quinary) {
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
            this.quaternary = quaternary;
            this.quinary = quinary;
        }

        public OptionalInt primary() {
            return primary == null ? OptionalInt.empty() : OptionalInt.of(primary);
        }

        public OptionalInt secondary() {
            return secondary == null ? OptionalInt.empty() : OptionalInt.of(secondary);
        }

        public OptionalInt tertiary() {
            return tertiary == null ? OptionalInt.empty() : OptionalInt.of(tertiary);
        }

        public OptionalInt quaternary() {
            return quaternary == null ? OptionalInt.empty() : OptionalInt.of(quaternary);
        }

        public OptionalInt quinary() {
            return quinary == null ? OptionalInt.empty() : OptionalInt.of(quinary);
        }

        public AppVersion setPrimary(Integer primary) {
            this.primary = primary;
            return this;
        }

        public AppVersion setSecondary(Integer secondary) {
            this.secondary = secondary;
            return this;
        }

        public AppVersion setTertiary(Integer tertiary) {
            this.tertiary = tertiary;
            return this;
        }

        public AppVersion setQuaternary(Integer quaternary) {
            this.quaternary = quaternary;
            return this;
        }

        public AppVersion setQuinary(Integer quinary) {
            this.quinary = quinary;
            return this;
        }
    }

    @ProtobufMessage(name = "DeviceProps.HistorySyncConfig")
    public static final class HistorySyncConfig {
        @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
        Integer fullSyncDaysLimit;

        @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
        Integer fullSyncSizeMbLimit;

        @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
        Integer storageQuotaMb;

        @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
        Boolean inlineInitialPayloadInE2EeMsg;

        @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
        Integer recentSyncDaysLimit;

        @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
        Boolean supportCallLogHistory;

        @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
        Boolean supportBotUserAgentChatHistory;

        @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
        Boolean supportCagReactionsAndPolls;

        @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
        Boolean supportBizHostedMsg;

        @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
        Boolean supportRecentSyncChunkMessageCountTuning;

        @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
        Boolean supportHostedGroupMsg;

        @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
        Boolean supportFbidBotChatHistory;

        @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
        Boolean supportAddOnHistorySyncMigration;

        @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
        Boolean supportMessageAssociation;

        @ProtobufProperty(index = 15, type = ProtobufType.BOOL)
        Boolean supportGroupHistory;

        @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
        Boolean onDemandReady;

        @ProtobufProperty(index = 17, type = ProtobufType.BOOL)
        Boolean supportGuestChat;

        @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
        Boolean completeOnDemandReady;

        @ProtobufProperty(index = 19, type = ProtobufType.UINT32)
        Integer thumbnailSyncDaysLimit;

        @ProtobufProperty(index = 20, type = ProtobufType.UINT32)
        Integer initialSyncMaxMessagesPerChat;


        HistorySyncConfig(Integer fullSyncDaysLimit, Integer fullSyncSizeMbLimit, Integer storageQuotaMb, Boolean inlineInitialPayloadInE2EeMsg, Integer recentSyncDaysLimit, Boolean supportCallLogHistory, Boolean supportBotUserAgentChatHistory, Boolean supportCagReactionsAndPolls, Boolean supportBizHostedMsg, Boolean supportRecentSyncChunkMessageCountTuning, Boolean supportHostedGroupMsg, Boolean supportFbidBotChatHistory, Boolean supportAddOnHistorySyncMigration, Boolean supportMessageAssociation, Boolean supportGroupHistory, Boolean onDemandReady, Boolean supportGuestChat, Boolean completeOnDemandReady, Integer thumbnailSyncDaysLimit, Integer initialSyncMaxMessagesPerChat) {
            this.fullSyncDaysLimit = fullSyncDaysLimit;
            this.fullSyncSizeMbLimit = fullSyncSizeMbLimit;
            this.storageQuotaMb = storageQuotaMb;
            this.inlineInitialPayloadInE2EeMsg = inlineInitialPayloadInE2EeMsg;
            this.recentSyncDaysLimit = recentSyncDaysLimit;
            this.supportCallLogHistory = supportCallLogHistory;
            this.supportBotUserAgentChatHistory = supportBotUserAgentChatHistory;
            this.supportCagReactionsAndPolls = supportCagReactionsAndPolls;
            this.supportBizHostedMsg = supportBizHostedMsg;
            this.supportRecentSyncChunkMessageCountTuning = supportRecentSyncChunkMessageCountTuning;
            this.supportHostedGroupMsg = supportHostedGroupMsg;
            this.supportFbidBotChatHistory = supportFbidBotChatHistory;
            this.supportAddOnHistorySyncMigration = supportAddOnHistorySyncMigration;
            this.supportMessageAssociation = supportMessageAssociation;
            this.supportGroupHistory = supportGroupHistory;
            this.onDemandReady = onDemandReady;
            this.supportGuestChat = supportGuestChat;
            this.completeOnDemandReady = completeOnDemandReady;
            this.thumbnailSyncDaysLimit = thumbnailSyncDaysLimit;
            this.initialSyncMaxMessagesPerChat = initialSyncMaxMessagesPerChat;
        }

        public OptionalInt fullSyncDaysLimit() {
            return fullSyncDaysLimit == null ? OptionalInt.empty() : OptionalInt.of(fullSyncDaysLimit);
        }

        public OptionalInt fullSyncSizeMbLimit() {
            return fullSyncSizeMbLimit == null ? OptionalInt.empty() : OptionalInt.of(fullSyncSizeMbLimit);
        }

        public OptionalInt storageQuotaMb() {
            return storageQuotaMb == null ? OptionalInt.empty() : OptionalInt.of(storageQuotaMb);
        }

        public boolean inlineInitialPayloadInE2EeMsg() {
            return inlineInitialPayloadInE2EeMsg != null && inlineInitialPayloadInE2EeMsg;
        }

        public OptionalInt recentSyncDaysLimit() {
            return recentSyncDaysLimit == null ? OptionalInt.empty() : OptionalInt.of(recentSyncDaysLimit);
        }

        public boolean supportCallLogHistory() {
            return supportCallLogHistory != null && supportCallLogHistory;
        }

        public boolean supportBotUserAgentChatHistory() {
            return supportBotUserAgentChatHistory != null && supportBotUserAgentChatHistory;
        }

        public boolean supportCagReactionsAndPolls() {
            return supportCagReactionsAndPolls != null && supportCagReactionsAndPolls;
        }

        public boolean supportBizHostedMsg() {
            return supportBizHostedMsg != null && supportBizHostedMsg;
        }

        public boolean supportRecentSyncChunkMessageCountTuning() {
            return supportRecentSyncChunkMessageCountTuning != null && supportRecentSyncChunkMessageCountTuning;
        }

        public boolean supportHostedGroupMsg() {
            return supportHostedGroupMsg != null && supportHostedGroupMsg;
        }

        public boolean supportFbidBotChatHistory() {
            return supportFbidBotChatHistory != null && supportFbidBotChatHistory;
        }

        public boolean supportAddOnHistorySyncMigration() {
            return supportAddOnHistorySyncMigration != null && supportAddOnHistorySyncMigration;
        }

        public boolean supportMessageAssociation() {
            return supportMessageAssociation != null && supportMessageAssociation;
        }

        public boolean supportGroupHistory() {
            return supportGroupHistory != null && supportGroupHistory;
        }

        public boolean onDemandReady() {
            return onDemandReady != null && onDemandReady;
        }

        public boolean supportGuestChat() {
            return supportGuestChat != null && supportGuestChat;
        }

        public boolean completeOnDemandReady() {
            return completeOnDemandReady != null && completeOnDemandReady;
        }

        public OptionalInt thumbnailSyncDaysLimit() {
            return thumbnailSyncDaysLimit == null ? OptionalInt.empty() : OptionalInt.of(thumbnailSyncDaysLimit);
        }

        public OptionalInt initialSyncMaxMessagesPerChat() {
            return initialSyncMaxMessagesPerChat == null ? OptionalInt.empty() : OptionalInt.of(initialSyncMaxMessagesPerChat);
        }

        public HistorySyncConfig setFullSyncDaysLimit(Integer fullSyncDaysLimit) {
            this.fullSyncDaysLimit = fullSyncDaysLimit;
            return this;
        }

        public HistorySyncConfig setFullSyncSizeMbLimit(Integer fullSyncSizeMbLimit) {
            this.fullSyncSizeMbLimit = fullSyncSizeMbLimit;
            return this;
        }

        public HistorySyncConfig setStorageQuotaMb(Integer storageQuotaMb) {
            this.storageQuotaMb = storageQuotaMb;
            return this;
        }

        public HistorySyncConfig setInlineInitialPayloadInE2EeMsg(Boolean inlineInitialPayloadInE2EeMsg) {
            this.inlineInitialPayloadInE2EeMsg = inlineInitialPayloadInE2EeMsg;
            return this;
        }

        public HistorySyncConfig setRecentSyncDaysLimit(Integer recentSyncDaysLimit) {
            this.recentSyncDaysLimit = recentSyncDaysLimit;
            return this;
        }

        public HistorySyncConfig setSupportCallLogHistory(Boolean supportCallLogHistory) {
            this.supportCallLogHistory = supportCallLogHistory;
            return this;
        }

        public HistorySyncConfig setSupportBotUserAgentChatHistory(Boolean supportBotUserAgentChatHistory) {
            this.supportBotUserAgentChatHistory = supportBotUserAgentChatHistory;
            return this;
        }

        public HistorySyncConfig setSupportCagReactionsAndPolls(Boolean supportCagReactionsAndPolls) {
            this.supportCagReactionsAndPolls = supportCagReactionsAndPolls;
            return this;
        }

        public HistorySyncConfig setSupportBizHostedMsg(Boolean supportBizHostedMsg) {
            this.supportBizHostedMsg = supportBizHostedMsg;
            return this;
        }

        public HistorySyncConfig setSupportRecentSyncChunkMessageCountTuning(Boolean supportRecentSyncChunkMessageCountTuning) {
            this.supportRecentSyncChunkMessageCountTuning = supportRecentSyncChunkMessageCountTuning;
            return this;
        }

        public HistorySyncConfig setSupportHostedGroupMsg(Boolean supportHostedGroupMsg) {
            this.supportHostedGroupMsg = supportHostedGroupMsg;
            return this;
        }

        public HistorySyncConfig setSupportFbidBotChatHistory(Boolean supportFbidBotChatHistory) {
            this.supportFbidBotChatHistory = supportFbidBotChatHistory;
            return this;
        }

        public HistorySyncConfig setSupportAddOnHistorySyncMigration(Boolean supportAddOnHistorySyncMigration) {
            this.supportAddOnHistorySyncMigration = supportAddOnHistorySyncMigration;
            return this;
        }

        public HistorySyncConfig setSupportMessageAssociation(Boolean supportMessageAssociation) {
            this.supportMessageAssociation = supportMessageAssociation;
            return this;
        }

        public HistorySyncConfig setSupportGroupHistory(Boolean supportGroupHistory) {
            this.supportGroupHistory = supportGroupHistory;
            return this;
        }

        public HistorySyncConfig setOnDemandReady(Boolean onDemandReady) {
            this.onDemandReady = onDemandReady;
            return this;
        }

        public HistorySyncConfig setSupportGuestChat(Boolean supportGuestChat) {
            this.supportGuestChat = supportGuestChat;
            return this;
        }

        public HistorySyncConfig setCompleteOnDemandReady(Boolean completeOnDemandReady) {
            this.completeOnDemandReady = completeOnDemandReady;
            return this;
        }

        public HistorySyncConfig setThumbnailSyncDaysLimit(Integer thumbnailSyncDaysLimit) {
            this.thumbnailSyncDaysLimit = thumbnailSyncDaysLimit;
            return this;
        }

        public HistorySyncConfig setInitialSyncMaxMessagesPerChat(Integer initialSyncMaxMessagesPerChat) {
            this.initialSyncMaxMessagesPerChat = initialSyncMaxMessagesPerChat;
            return this;
        }
    }
}
