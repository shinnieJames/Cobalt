package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.BizCatalogType;
import com.github.auties00.cobalt.wam.type.ChatOriginsType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 6464)
public interface ThreadInteractionDataBizEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt awayMsgsSent();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<BizCatalogType> bizCatalogType();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt bizConversationDepth();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt cartViews();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<ChatOriginsType> chatOrigins();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt collectionInquiriesSent();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt commerceMsgsReceived();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt commerceMsgsSent();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> entryPointConversionApp();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> entryPointConversionSource();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt firstResponseTime();

    @WamProperty(index = 41, type = WamType.INTEGER)
    OptionalInt fmxNotMvBottomSheetDismissedCount();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt fmxNotMvBottomSheetGetMvButtonClicks();

    @WamProperty(index = 43, type = WamType.INTEGER)
    OptionalInt fmxNotMvBottomSheetGetMvButtonImpressions();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt fmxNotMvBottomSheetImpressions();

    @WamProperty(index = 45, type = WamType.INTEGER)
    OptionalInt fmxNotMvBottomSheetLearnMoreButtonClicks();

    @WamProperty(index = 46, type = WamType.INTEGER)
    OptionalInt fmxNotMvClicks();

    @WamProperty(index = 13, type = WamType.BOOLEAN)
    Optional<Boolean> groupContainsBiz();

    @WamProperty(index = 47, type = WamType.BOOLEAN)
    Optional<Boolean> isBizMvFrictionEligible();

    @WamProperty(index = 14, type = WamType.BOOLEAN)
    Optional<Boolean> isCommerceViewed();

    @WamProperty(index = 15, type = WamType.BOOLEAN)
    Optional<Boolean> isCtaOnPdpClicked();

    @WamProperty(index = 16, type = WamType.BOOLEAN)
    Optional<Boolean> isLabelled();

    @WamProperty(index = 36, type = WamType.BOOLEAN)
    Optional<Boolean> isOppositePartyInitiated();

    @WamProperty(index = 17, type = WamType.BOOLEAN)
    Optional<Boolean> isUser1pBizBotChat();

    @WamProperty(index = 18, type = WamType.BOOLEAN)
    Optional<Boolean> isUser3pBotChat();

    @WamProperty(index = 19, type = WamType.BOOLEAN)
    Optional<Boolean> isUserAgent();

    @WamProperty(index = 20, type = WamType.BOOLEAN)
    Optional<Boolean> isUserCreatedAgent();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt labelledMsgs();

    @WamProperty(index = 37, type = WamType.INTEGER)
    OptionalInt locationsSent();

    @WamProperty(index = 49, type = WamType.STRING)
    Optional<String> matchedMessagesMarkedAsReadWithDeltaTime();

    @WamProperty(index = 50, type = WamType.STRING)
    Optional<String> matchedMessagesReadWithDeltaTime();

    @WamProperty(index = 48, type = WamType.INTEGER)
    OptionalInt notMvImpressions();

    @WamProperty(index = 40, type = WamType.BOOLEAN)
    Optional<Boolean> oppositePartyHasBadge();

    @WamProperty(index = 22, type = WamType.BOOLEAN)
    Optional<Boolean> oppositePartyHasBusinessIntent();

    @WamProperty(index = 23, type = WamType.INTEGER)
    OptionalInt ordersSent();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt pdpInquiriesSent();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt pdpViews();

    @WamProperty(index = 26, type = WamType.INTEGER)
    OptionalInt quickRepliesSent();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt smbMarketingMessagesReactionsSent();

    @WamProperty(index = 28, type = WamType.INTEGER)
    OptionalInt smbMarketingMessagesRepliesSent();

    @WamProperty(index = 29, type = WamType.INTEGER)
    OptionalInt smbMarketingMessagesSpamReports();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt smbMarketingMsgsReceived();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt smbMarketingMsgsSent();

    @WamProperty(index = 39, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 32, type = WamType.STRING)
    Optional<String> threadDs();

    @WamProperty(index = 33, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 38, type = WamType.STRING)
    Optional<String> threadIdByLid();

    @WamProperty(index = 35, type = WamType.BOOLEAN)
    Optional<Boolean> userHasBusinessIntent();
}
