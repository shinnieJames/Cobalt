package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;

@WamEvent(id = 1166)
public interface WebcEmojiOpenEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> webcEmojiOpenTab();
}
