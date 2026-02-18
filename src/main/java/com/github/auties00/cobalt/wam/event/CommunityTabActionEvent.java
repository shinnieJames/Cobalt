package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.OptionalInt;

@WamEvent(id = 3496)
public interface CommunityTabActionEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt communityNoActionTabViews();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt communityTabGroupNavigations();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt communityTabToHomeViews();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt communityTabViews();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt communityTabViewsViaContextMenu();
}
