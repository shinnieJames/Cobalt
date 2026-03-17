package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "LimitSharing")
public final class ChatLimitSharing {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean sharingLimited;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    TriggerType trigger;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant limitSharingSettingTimestamp;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean initiatedByMe;


    ChatLimitSharing(Boolean sharingLimited, TriggerType trigger, Instant limitSharingSettingTimestamp, Boolean initiatedByMe) {
        this.sharingLimited = sharingLimited;
        this.trigger = trigger;
        this.limitSharingSettingTimestamp = limitSharingSettingTimestamp;
        this.initiatedByMe = initiatedByMe;
    }

    public boolean sharingLimited() {
        return sharingLimited != null && sharingLimited;
    }

    public Optional<TriggerType> trigger() {
        return Optional.ofNullable(trigger);
    }

    public Optional<Instant> limitSharingSettingTimestamp() {
        return Optional.ofNullable(limitSharingSettingTimestamp);
    }

    public boolean initiatedByMe() {
        return initiatedByMe != null && initiatedByMe;
    }

    public void setSharingLimited(Boolean sharingLimited) {
        this.sharingLimited = sharingLimited;
    }

    public void setTrigger(TriggerType trigger) {
        this.trigger = trigger;
    }

    public void setLimitSharingSettingTimestamp(Instant limitSharingSettingTimestamp) {
        this.limitSharingSettingTimestamp = limitSharingSettingTimestamp;
    }

    public void setInitiatedByMe(Boolean initiatedByMe) {
        this.initiatedByMe = initiatedByMe;
    }

    @ProtobufEnum(name = "LimitSharing.TriggerType")
    public enum TriggerType {
        UNKNOWN(0),
        CHAT_SETTING(1),
        BIZ_SUPPORTS_FB_HOSTING(2),
        UNKNOWN_GROUP(3);

        TriggerType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
