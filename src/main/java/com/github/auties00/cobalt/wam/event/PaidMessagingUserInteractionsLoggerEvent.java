package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsActionTarget;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsActionType;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsComponentType;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsHeaderMediaType;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsHostStorage;
import com.github.auties00.cobalt.wam.type.PaidMessagingUserInteractionsMarketingFormat;
import com.github.auties00.cobalt.wam.type.TapTargetType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4740, channel = WamChannel.PRIVATE, privateStatsId = 113760892)
public interface PaidMessagingUserInteractionsLoggerEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsActionTarget> pmxActionTarget();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsActionType> pmxActionType();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt pmxCarouselCardIndex();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsComponentType> pmxComponentType();

    @WamProperty(index = 13, type = WamType.STRING)
    Optional<String> pmxHashedMessageId();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt pmxHashedMessageKey();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsHeaderMediaType> pmxHeaderMediaType();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsHostStorage> pmxHostStorage();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<PaidMessagingUserInteractionsMarketingFormat> pmxMarketingFormat();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt pmxMessageDeliveredTs();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt pmxMessageStanzaAcceptedTs();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> pmxQueryParams();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> pmxSenderCountryCode();

    @WamProperty(index = 15, type = WamType.ENUM)
    Optional<TapTargetType> pmxTapTargetType();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt pmxTextTruncationLimit();

    @WamProperty(index = 7, type = WamType.STRING)
    Optional<String> templateId();
}
