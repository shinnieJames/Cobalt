package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.CloudAPIThreadControlNotification")
public final class CloudAPIThreadControlNotification implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    CloudAPIThreadControl status;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderNotificationTimestampMs;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String consumerLid;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String consumerPhoneNumber;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    CloudAPIThreadControlNotificationContent notificationContent;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean shouldSuppressNotification;


    CloudAPIThreadControlNotification(CloudAPIThreadControl status, Instant senderNotificationTimestampMs, String consumerLid, String consumerPhoneNumber, CloudAPIThreadControlNotificationContent notificationContent, Boolean shouldSuppressNotification) {
        this.status = status;
        this.senderNotificationTimestampMs = senderNotificationTimestampMs;
        this.consumerLid = consumerLid;
        this.consumerPhoneNumber = consumerPhoneNumber;
        this.notificationContent = notificationContent;
        this.shouldSuppressNotification = shouldSuppressNotification;
    }

    public Optional<CloudAPIThreadControl> status() {
        return Optional.ofNullable(status);
    }

    public Optional<Instant> senderNotificationTimestampMs() {
        return Optional.ofNullable(senderNotificationTimestampMs);
    }

    public Optional<String> consumerLid() {
        return Optional.ofNullable(consumerLid);
    }

    public Optional<String> consumerPhoneNumber() {
        return Optional.ofNullable(consumerPhoneNumber);
    }

    public Optional<CloudAPIThreadControlNotificationContent> notificationContent() {
        return Optional.ofNullable(notificationContent);
    }

    public boolean shouldSuppressNotification() {
        return shouldSuppressNotification != null && shouldSuppressNotification;
    }

    public void setStatus(CloudAPIThreadControl status) {
        this.status = status;
    }

    public void setSenderNotificationTimestampMs(Instant senderNotificationTimestampMs) {
        this.senderNotificationTimestampMs = senderNotificationTimestampMs;
    }

    public void setConsumerLid(String consumerLid) {
        this.consumerLid = consumerLid;
    }

    public void setConsumerPhoneNumber(String consumerPhoneNumber) {
        this.consumerPhoneNumber = consumerPhoneNumber;
    }

    public void setNotificationContent(CloudAPIThreadControlNotificationContent notificationContent) {
        this.notificationContent = notificationContent;
    }

    public void setShouldSuppressNotification(Boolean shouldSuppressNotification) {
        this.shouldSuppressNotification = shouldSuppressNotification;
    }

    @ProtobufEnum(name = "Message.CloudAPIThreadControlNotification.CloudAPIThreadControl")
    public static enum CloudAPIThreadControl {
        UNKNOWN(0),
        CONTROL_PASSED(1),
        CONTROL_TAKEN(2);

        CloudAPIThreadControl(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.CloudAPIThreadControlNotification.CloudAPIThreadControlNotificationContent")
    public static final class CloudAPIThreadControlNotificationContent {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String handoffNotificationText;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String extraJson;


        CloudAPIThreadControlNotificationContent(String handoffNotificationText, String extraJson) {
            this.handoffNotificationText = handoffNotificationText;
            this.extraJson = extraJson;
        }

        public Optional<String> handoffNotificationText() {
            return Optional.ofNullable(handoffNotificationText);
        }

        public Optional<String> extraJson() {
            return Optional.ofNullable(extraJson);
        }

        public void setHandoffNotificationText(String handoffNotificationText) {
            this.handoffNotificationText = handoffNotificationText;
    }

        public void setExtraJson(String extraJson) {
            this.extraJson = extraJson;
    }
    }
}
