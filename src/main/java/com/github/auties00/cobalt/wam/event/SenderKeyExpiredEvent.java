package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ExpiryReason;
import com.github.auties00.cobalt.wam.type.MessageChatType;
import com.github.auties00.cobalt.wam.type.SizeBucket;

import java.util.Optional;

@WamEvent(id = 3130)
public interface SenderKeyExpiredEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MessageChatType> chatType();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<SizeBucket> deviceSizeBucket();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ExpiryReason> expiryReason();
}
