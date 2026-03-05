package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MutationCountBucket;

import java.util.Optional;

@WamEvent(id = 3180, channel = WamChannel.PRIVATE, releaseWeight = 100, privateStatsId = 0)
public interface MdAppStateSyncMutationStatsEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MutationCountBucket> applied();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MutationCountBucket> failed();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<MutationCountBucket> invalid();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<MutationCountBucket> orphan();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> syncdAction();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<MutationCountBucket> unsupported();
}
