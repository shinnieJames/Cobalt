package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ClientGroupSizeBucket;
import com.github.auties00.cobalt.wam.type.GroupTypeClient;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3796)
public interface NotificationEngagementEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.TIMER)
    Optional<Instant> avgNotifEngagementT();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<ClientGroupSizeBucket> groupSizeBucket();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<GroupTypeClient> groupTypeClient();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 18, type = WamType.BOOLEAN)
    Optional<Boolean> isWebBackgroundSyncNotif();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifShown();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifShownFb();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifShownIg();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifTapToOpen();

    @WamProperty(index = 23, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifTapToOpenFb();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt totalLinkReshareMessageNotifTapToOpenIg();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt totalMessageReminderNotifShown();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt totalMessageReminderNotifTapToOpen();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt totalNotifMarkAsRead();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt totalNotifMissedCallVoipCallback();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt totalNotifMissedCallVoipMessage();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt totalNotifOthers();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt totalNotifReply();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt totalNotifRtcVoipAccept();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt totalNotifRtcVoipDecline();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt totalNotifShowPreview();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt totalNotifShown();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt totalNotifTapToOpen();
}
