package com.github.auties00.cobalt.model.device;

import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "DeviceProps")
public final class DeviceProps {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String os;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    ClientAppVersion version;

    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    DevicePlatformType platformType;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean requireFullSync;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    HistorySyncConfig historySyncConfig;


    DeviceProps(String os, ClientAppVersion version, DevicePlatformType platformType, Boolean requireFullSync, HistorySyncConfig historySyncConfig) {
        this.os = os;
        this.version = version;
        this.platformType = platformType;
        this.requireFullSync = requireFullSync;
        this.historySyncConfig = historySyncConfig;
    }

    public Optional<String> os() {
        return Optional.ofNullable(os);
    }

    public Optional<ClientAppVersion> version() {
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

    public void setOs(String os) {
        this.os = os;
    }

    public void setVersion(ClientAppVersion version) {
        this.version = version;
    }

    public void setPlatformType(DevicePlatformType platformType) {
        this.platformType = platformType;
    }

    public void setRequireFullSync(Boolean requireFullSync) {
        this.requireFullSync = requireFullSync;
    }

    public void setHistorySyncConfig(HistorySyncConfig historySyncConfig) {
        this.historySyncConfig = historySyncConfig;
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

        public void setFullSyncDaysLimit(Integer fullSyncDaysLimit) {
            this.fullSyncDaysLimit = fullSyncDaysLimit;
    }

        public void setFullSyncSizeMbLimit(Integer fullSyncSizeMbLimit) {
            this.fullSyncSizeMbLimit = fullSyncSizeMbLimit;
    }

        public void setStorageQuotaMb(Integer storageQuotaMb) {
            this.storageQuotaMb = storageQuotaMb;
    }

        public void setInlineInitialPayloadInE2EeMsg(Boolean inlineInitialPayloadInE2EeMsg) {
            this.inlineInitialPayloadInE2EeMsg = inlineInitialPayloadInE2EeMsg;
    }

        public void setRecentSyncDaysLimit(Integer recentSyncDaysLimit) {
            this.recentSyncDaysLimit = recentSyncDaysLimit;
    }

        public void setSupportCallLogHistory(Boolean supportCallLogHistory) {
            this.supportCallLogHistory = supportCallLogHistory;
    }

        public void setSupportBotUserAgentChatHistory(Boolean supportBotUserAgentChatHistory) {
            this.supportBotUserAgentChatHistory = supportBotUserAgentChatHistory;
    }

        public void setSupportCagReactionsAndPolls(Boolean supportCagReactionsAndPolls) {
            this.supportCagReactionsAndPolls = supportCagReactionsAndPolls;
    }

        public void setSupportBizHostedMsg(Boolean supportBizHostedMsg) {
            this.supportBizHostedMsg = supportBizHostedMsg;
    }

        public void setSupportRecentSyncChunkMessageCountTuning(Boolean supportRecentSyncChunkMessageCountTuning) {
            this.supportRecentSyncChunkMessageCountTuning = supportRecentSyncChunkMessageCountTuning;
    }

        public void setSupportHostedGroupMsg(Boolean supportHostedGroupMsg) {
            this.supportHostedGroupMsg = supportHostedGroupMsg;
    }

        public void setSupportFbidBotChatHistory(Boolean supportFbidBotChatHistory) {
            this.supportFbidBotChatHistory = supportFbidBotChatHistory;
    }

        public void setSupportAddOnHistorySyncMigration(Boolean supportAddOnHistorySyncMigration) {
            this.supportAddOnHistorySyncMigration = supportAddOnHistorySyncMigration;
    }

        public void setSupportMessageAssociation(Boolean supportMessageAssociation) {
            this.supportMessageAssociation = supportMessageAssociation;
    }

        public void setSupportGroupHistory(Boolean supportGroupHistory) {
            this.supportGroupHistory = supportGroupHistory;
    }

        public void setOnDemandReady(Boolean onDemandReady) {
            this.onDemandReady = onDemandReady;
    }

        public void setSupportGuestChat(Boolean supportGuestChat) {
            this.supportGuestChat = supportGuestChat;
    }

        public void setCompleteOnDemandReady(Boolean completeOnDemandReady) {
            this.completeOnDemandReady = completeOnDemandReady;
    }

        public void setThumbnailSyncDaysLimit(Integer thumbnailSyncDaysLimit) {
            this.thumbnailSyncDaysLimit = thumbnailSyncDaysLimit;
    }

        public void setInitialSyncMaxMessagesPerChat(Integer initialSyncMaxMessagesPerChat) {
            this.initialSyncMaxMessagesPerChat = initialSyncMaxMessagesPerChat;
    }
    }
}
