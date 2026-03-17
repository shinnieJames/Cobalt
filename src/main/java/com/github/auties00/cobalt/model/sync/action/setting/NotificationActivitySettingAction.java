package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.NotificationActivitySettingAction")
public final class NotificationActivitySettingAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "notificationActivitySetting";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    NotificationActivitySetting notificationActivitySetting;


    NotificationActivitySettingAction(NotificationActivitySetting notificationActivitySetting) {
        this.notificationActivitySetting = notificationActivitySetting;
    }

    public Optional<NotificationActivitySetting> notificationActivitySetting() {
        return Optional.ofNullable(notificationActivitySetting);
    }

    public void setNotificationActivitySetting(NotificationActivitySetting notificationActivitySetting) {
        this.notificationActivitySetting = notificationActivitySetting;
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
