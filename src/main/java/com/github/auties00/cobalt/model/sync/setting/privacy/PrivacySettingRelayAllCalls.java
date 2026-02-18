package com.github.auties00.cobalt.model.sync.setting.privacy;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.PrivacySettingRelayAllCalls")
public final class PrivacySettingRelayAllCalls implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isEnabled;


    PrivacySettingRelayAllCalls(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public boolean isEnabled() {
        return isEnabled != null && isEnabled;
    }

    public PrivacySettingRelayAllCalls setEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
        return this;
    }
}
