package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MetaAiUpsellCtaOperationType;
import com.github.auties00.cobalt.wam.type.MetaAiUpsellCtaSourceType;

import java.util.Optional;

@WamEvent(id = 6532)
public interface MetaAiUpsellCtaEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MetaAiUpsellCtaOperationType> metaAiUpsellCtaOperation();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MetaAiUpsellCtaSourceType> metaAiUpsellCtaSource();
}
