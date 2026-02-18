package com.github.auties00.cobalt.model.sync.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.StatusPostOptInNotificationPreferencesAction")
public final class StatusPostOptInNotificationPreferencesAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean enabled;


    StatusPostOptInNotificationPreferencesAction(Boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled != null && enabled;
    }

    public StatusPostOptInNotificationPreferencesAction setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }
}
