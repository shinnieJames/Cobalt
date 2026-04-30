package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebPttDailyWamEvent")
@WamEvent(id = 2938)
public interface PttDailyEvent extends WamEventSpec {
    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt pttCancelBroadcast();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt pttCancelGroup();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt pttCancelIndividual();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt pttCancelInterop();

    @WamProperty(index = 32, type = WamType.INTEGER)
    OptionalInt pttCancelNewsletter();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt pttDraftReviewBroadcast();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt pttDraftReviewGroup();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt pttDraftReviewIndividual();

    @WamProperty(index = 43, type = WamType.INTEGER)
    OptionalInt pttDraftReviewInterop();

    @WamProperty(index = 33, type = WamType.INTEGER)
    OptionalInt pttDraftReviewNewsletter();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt pttFastplaybackBroadcast();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt pttFastplaybackGroup();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt pttFastplaybackIndividual();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt pttFastplaybackInterop();

    @WamProperty(index = 34, type = WamType.INTEGER)
    OptionalInt pttFastplaybackNewsletter();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt pttLockBroadcast();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt pttLockGroup();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt pttLockIndividual();

    @WamProperty(index = 45, type = WamType.INTEGER)
    OptionalInt pttLockInterop();

    @WamProperty(index = 35, type = WamType.INTEGER)
    OptionalInt pttLockNewsletter();

    @WamProperty(index = 29, type = WamType.INTEGER)
    OptionalInt pttOutOfChatBroadcast();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt pttOutOfChatGroup();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt pttOutOfChatIndividual();

    @WamProperty(index = 46, type = WamType.INTEGER)
    OptionalInt pttOutOfChatInterop();

    @WamProperty(index = 36, type = WamType.INTEGER)
    OptionalInt pttOutOfChatNewsletter();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt pttPausedRecordBroadcast();

    @WamProperty(index = 23, type = WamType.INTEGER)
    OptionalInt pttPausedRecordGroup();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt pttPausedRecordIndividual();

    @WamProperty(index = 47, type = WamType.INTEGER)
    OptionalInt pttPausedRecordInterop();

    @WamProperty(index = 37, type = WamType.INTEGER)
    OptionalInt pttPausedRecordNewsletter();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt pttPlaybackBroadcast();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt pttPlaybackGroup();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt pttPlaybackIndividual();

    @WamProperty(index = 48, type = WamType.INTEGER)
    OptionalInt pttPlaybackInterop();

    @WamProperty(index = 38, type = WamType.INTEGER)
    OptionalInt pttPlaybackNewsletter();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt pttRecordBroadcast();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt pttRecordGroup();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt pttRecordIndividual();

    @WamProperty(index = 49, type = WamType.INTEGER)
    OptionalInt pttRecordInterop();

    @WamProperty(index = 39, type = WamType.INTEGER)
    OptionalInt pttRecordNewsletter();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt pttSendBroadcast();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt pttSendGroup();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt pttSendIndividual();

    @WamProperty(index = 50, type = WamType.INTEGER)
    OptionalInt pttSendInterop();

    @WamProperty(index = 40, type = WamType.INTEGER)
    OptionalInt pttSendNewsletter();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt pttStopTapBroadcast();

    @WamProperty(index = 26, type = WamType.INTEGER)
    OptionalInt pttStopTapGroup();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt pttStopTapIndividual();

    @WamProperty(index = 51, type = WamType.INTEGER)
    OptionalInt pttStopTapInterop();

    @WamProperty(index = 41, type = WamType.INTEGER)
    OptionalInt pttStopTapNewsletter();
}
