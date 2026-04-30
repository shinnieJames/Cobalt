package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.NoticeTriggeredBy;
import com.github.auties00.cobalt.wam.type.NoticeType;
import com.github.auties00.cobalt.wam.type.UserNoticeEventType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebUserNoticeWamEvent")
@WamEvent(id = 2472)
public interface UserNoticeEvent extends WamEventSpec {
    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<NoticeTriggeredBy> noticeTriggeredBy();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<NoticeType> noticeType();

    @WamProperty(index = 6, type = WamType.TIMER)
    Optional<Instant> tsMs();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt userNoticeContentVersion();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<UserNoticeEventType> userNoticeEvent();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt userNoticeId();
}
