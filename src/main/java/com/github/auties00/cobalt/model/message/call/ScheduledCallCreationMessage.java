package com.github.auties00.cobalt.model.message.call;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ScheduledCallCreationMessage")
public final class ScheduledCallCreationMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant scheduledTimestampMs;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    CallType callType;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String title;


    ScheduledCallCreationMessage(Instant scheduledTimestampMs, CallType callType, String title) {
        this.scheduledTimestampMs = scheduledTimestampMs;
        this.callType = callType;
        this.title = title;
    }

    public Optional<Instant> scheduledTimestampMs() {
        return Optional.ofNullable(scheduledTimestampMs);
    }

    public Optional<CallType> callType() {
        return Optional.ofNullable(callType);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public void setScheduledTimestampMs(Instant scheduledTimestampMs) {
        this.scheduledTimestampMs = scheduledTimestampMs;
    }

    public void setCallType(CallType callType) {
        this.callType = callType;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @ProtobufEnum(name = "Message.ScheduledCallCreationMessage.CallType")
    public static enum CallType {
        UNKNOWN(0),
        VOICE(1),
        VIDEO(2);

        CallType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
