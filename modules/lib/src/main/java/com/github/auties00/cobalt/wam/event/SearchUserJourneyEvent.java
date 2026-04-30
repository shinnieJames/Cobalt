package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.SearchDsSendContentType;
import com.github.auties00.cobalt.wam.type.SearchSelectedMessageSource;
import com.github.auties00.cobalt.wam.type.SearchUjCriteriaType;
import com.github.auties00.cobalt.wam.type.SearchUjDismissType;
import com.github.auties00.cobalt.wam.type.SearchUjFilterType;
import com.github.auties00.cobalt.wam.type.SearchUjItemType;
import com.github.auties00.cobalt.wam.type.SearchUseCase;
import com.github.auties00.cobalt.wam.type.SearchUserJourneyAction;
import com.github.auties00.cobalt.wam.type.TsSurface;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSearchUserJourneyWamEvent")
@WamEvent(id = 6358)
public interface SearchUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 32, type = WamType.ENUM)
    Optional<SearchDsSendContentType> searchDsSendContentType();

    @WamProperty(index = 33, type = WamType.INTEGER)
    OptionalInt searchDsTimeSpentMs();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt searchFtsAndSemanticMessagesCount();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt searchFtsMessagesCount();

    @WamProperty(index = 23, type = WamType.BOOLEAN)
    Optional<Boolean> searchHasSemanticSearchResults();

    @WamProperty(index = 24, type = WamType.ENUM)
    Optional<SearchSelectedMessageSource> searchSelectedMessageSource();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt searchSemanticMessagesCount();

    @WamProperty(index = 26, type = WamType.STRING)
    Optional<String> searchSessionQueryId();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt searchUjAiSuggestionCount();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt searchUjBizCount();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt searchUjChatsCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt searchUjContactsCount();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<SearchUjCriteriaType> searchUjCriteriaType();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<SearchUjDismissType> searchUjDismissType();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt searchUjFilterCount();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<SearchUjFilterType> searchUjFilterType();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt searchUjGroupsInCommonCount();

    @WamProperty(index = 10, type = WamType.BOOLEAN)
    Optional<Boolean> searchUjHasFuzzyResults();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt searchUjInviteCount();

    @WamProperty(index = 11, type = WamType.ENUM)
    Optional<SearchUjItemType> searchUjItemType();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt searchUjMessagesCount();

    @WamProperty(index = 34, type = WamType.INTEGER)
    OptionalInt searchUjPushnamesCount();

    @WamProperty(index = 29, type = WamType.INTEGER)
    OptionalInt searchUjRecentSearchesGroupCount();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt searchUjRecentSearchesIndividualCount();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt searchUjResultCount();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt searchUjSelectedItemRank();

    @WamProperty(index = 27, type = WamType.STRING)
    Optional<String> searchUniqueSessionId();

    @WamProperty(index = 15, type = WamType.ENUM)
    Optional<SearchUseCase> searchUseCase();

    @WamProperty(index = 16, type = WamType.ENUM)
    Optional<SearchUserJourneyAction> searchUserJourneyAction();

    @WamProperty(index = 28, type = WamType.FLOAT)
    OptionalDouble selectedMessageDistance();

    @WamProperty(index = 17, type = WamType.ENUM)
    Optional<TsSurface> uiSurface();

    @WamProperty(index = 18, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt userJourneyEventMs();

    @WamProperty(index = 20, type = WamType.STRING)
    Optional<String> userJourneyFunnelId();
}
