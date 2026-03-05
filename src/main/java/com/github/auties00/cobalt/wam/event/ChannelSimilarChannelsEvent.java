package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.BannerStatus;
import com.github.auties00.cobalt.wam.type.BannerStatusReason;
import com.github.auties00.cobalt.wam.type.ChannelEventSurface;
import com.github.auties00.cobalt.wam.type.ChannelUserType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5202)
public interface ChannelSimilarChannelsEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<BannerStatus> bannerStatus();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<BannerStatusReason> bannerStatusReason();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> cid();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt similarChannelDisplayRank();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<ChannelEventSurface> similarChannelEventSurface();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> similarChannelId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt similarChannelRank();

    @WamProperty(index = 9, type = WamType.ENUM)
    Optional<ChannelUserType> similarChannelUserType();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt similarChannelsSessionId();

    @WamProperty(index = 11, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt updatesTabSessionId();
}
