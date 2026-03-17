package com.github.auties00.cobalt.model.message.event;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.EventMessage")
public final class EventMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean isCanceled;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String description;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    LocationMessage location;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String joinLink;

    @ProtobufProperty(index = 7, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant startTime;

    @ProtobufProperty(index = 8, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant endTime;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    Boolean extraGuestsAllowed;

    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    Boolean isScheduleCall;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    Boolean hasReminder;

    @ProtobufProperty(index = 12, type = ProtobufType.INT64)
    Long reminderOffsetSec;


    EventMessage(ContextInfo contextInfo, Boolean isCanceled, String name, String description, LocationMessage location, String joinLink, Instant startTime, Instant endTime, Boolean extraGuestsAllowed, Boolean isScheduleCall, Boolean hasReminder, Long reminderOffsetSec) {
        this.contextInfo = contextInfo;
        this.isCanceled = isCanceled;
        this.name = name;
        this.description = description;
        this.location = location;
        this.joinLink = joinLink;
        this.startTime = startTime;
        this.endTime = endTime;
        this.extraGuestsAllowed = extraGuestsAllowed;
        this.isScheduleCall = isScheduleCall;
        this.hasReminder = hasReminder;
        this.reminderOffsetSec = reminderOffsetSec;
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public boolean isCanceled() {
        return isCanceled != null && isCanceled;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Optional<LocationMessage> location() {
        return Optional.ofNullable(location);
    }

    public Optional<String> joinLink() {
        return Optional.ofNullable(joinLink);
    }

    public Optional<Instant> startTime() {
        return Optional.ofNullable(startTime);
    }

    public Optional<Instant> endTime() {
        return Optional.ofNullable(endTime);
    }

    public boolean extraGuestsAllowed() {
        return extraGuestsAllowed != null && extraGuestsAllowed;
    }

    public boolean isScheduleCall() {
        return isScheduleCall != null && isScheduleCall;
    }

    public boolean hasReminder() {
        return hasReminder != null && hasReminder;
    }

    public OptionalLong reminderOffsetSec() {
        return reminderOffsetSec == null ? OptionalLong.empty() : OptionalLong.of(reminderOffsetSec);
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setCanceled(Boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLocation(LocationMessage location) {
        this.location = location;
    }

    public void setJoinLink(String joinLink) {
        this.joinLink = joinLink;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public void setExtraGuestsAllowed(Boolean extraGuestsAllowed) {
        this.extraGuestsAllowed = extraGuestsAllowed;
    }

    public void setScheduleCall(Boolean isScheduleCall) {
        this.isScheduleCall = isScheduleCall;
    }

    public void setHasReminder(Boolean hasReminder) {
        this.hasReminder = hasReminder;
    }

    public void setReminderOffsetSec(Long reminderOffsetSec) {
        this.reminderOffsetSec = reminderOffsetSec;
    }
}
