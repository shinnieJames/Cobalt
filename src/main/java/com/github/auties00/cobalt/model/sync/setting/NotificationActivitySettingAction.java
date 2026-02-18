package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.NotificationActivitySettingAction")
public final class NotificationActivitySettingAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    NotificationActivitySetting notificationActivitySetting;


    NotificationActivitySettingAction(NotificationActivitySetting notificationActivitySetting) {
        this.notificationActivitySetting = notificationActivitySetting;
    }

    public Optional<NotificationActivitySetting> notificationActivitySetting() {
        return Optional.ofNullable(notificationActivitySetting);
    }

    public NotificationActivitySettingAction setNotificationActivitySetting(NotificationActivitySetting notificationActivitySetting) {
        this.notificationActivitySetting = notificationActivitySetting;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.NotificationActivitySettingAction.NotificationActivitySetting")
    public static enum NotificationActivitySetting {
        DEFAULT_ALL_MESSAGES(0),
        ALL_MESSAGES(1),
        HIGHLIGHTS(2),
        DEFAULT_HIGHLIGHTS(3);

        NotificationActivitySetting(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
