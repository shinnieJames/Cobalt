package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5602)
public interface WaFsSingleEmojiMessageDailyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> animatedEmojiEnabled();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt animatedEmojiReceiveCnt();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt animatedEmojiSendCnt();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt emojiClickCnt();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt emojiReplyCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt pauseAnimationCnt();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt replayAnimationCnt();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt singleEmojiReceiveCnt();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt singleEmojiSendCnt();
}
