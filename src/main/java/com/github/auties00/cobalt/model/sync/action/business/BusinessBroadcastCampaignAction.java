package com.github.auties00.cobalt.model.sync.action.business;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.BusinessBroadcastCampaignAction")
public final class BusinessBroadcastCampaignAction implements SyncAction<BusinessBroadcastCampaignActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "business_broadcast_campaign";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.INT32)
    Integer deviceId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String adId;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String msgId;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String broadcastJid;

    @ProtobufProperty(index = 6, type = ProtobufType.INT32)
    Integer reservedQuota;

    @ProtobufProperty(index = 7, type = ProtobufType.INT64)
    Long scheduledTimestamp;

    @ProtobufProperty(index = 8, type = ProtobufType.INT64)
    Long createTimestamp;

    @ProtobufProperty(index = 9, type = ProtobufType.ENUM)
    Status status;


    BusinessBroadcastCampaignAction(Integer deviceId, String adId, String name, String msgId, String broadcastJid, Integer reservedQuota, Long scheduledTimestamp, Long createTimestamp, Status status) {
        this.deviceId = deviceId;
        this.adId = adId;
        this.name = name;
        this.msgId = msgId;
        this.broadcastJid = broadcastJid;
        this.reservedQuota = reservedQuota;
        this.scheduledTimestamp = scheduledTimestamp;
        this.createTimestamp = createTimestamp;
        this.status = status;
    }

    public OptionalInt deviceId() {
        return deviceId == null ? OptionalInt.empty() : OptionalInt.of(deviceId);
    }

    public Optional<String> adId() {
        return Optional.ofNullable(adId);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> msgId() {
        return Optional.ofNullable(msgId);
    }

    public Optional<String> broadcastJid() {
        return Optional.ofNullable(broadcastJid);
    }

    public OptionalInt reservedQuota() {
        return reservedQuota == null ? OptionalInt.empty() : OptionalInt.of(reservedQuota);
    }

    public OptionalLong scheduledTimestamp() {
        return scheduledTimestamp == null ? OptionalLong.empty() : OptionalLong.of(scheduledTimestamp);
    }

    public OptionalLong createTimestamp() {
        return createTimestamp == null ? OptionalLong.empty() : OptionalLong.of(createTimestamp);
    }

    public Optional<Status> status() {
        return Optional.ofNullable(status);
    }

    public void setDeviceId(Integer deviceId) {
        this.deviceId = deviceId;
    }

    public void setAdId(String adId) {
        this.adId = adId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public void setBroadcastJid(String broadcastJid) {
        this.broadcastJid = broadcastJid;
    }

    public void setReservedQuota(Integer reservedQuota) {
        this.reservedQuota = reservedQuota;
    }

    public void setScheduledTimestamp(Long scheduledTimestamp) {
        this.scheduledTimestamp = scheduledTimestamp;
    }

    public void setCreateTimestamp(Long createTimestamp) {
        this.createTimestamp = createTimestamp;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @ProtobufEnum(name = "SyncActionValue.BusinessBroadcastCampaignAction.Status")
    public static enum Status {
        DRAFT(1),
        SCHEDULED(2),
        PROCESSING(3),
        FAILED(4),
        SENT(5);

        Status(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
