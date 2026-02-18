package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;

@ProtobufMessage(name = "Message.InitialSecurityNotificationSettingSync")
public final class InitialSecurityNotificationSettingSync implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean securityNotificationEnabled;


    InitialSecurityNotificationSettingSync(Boolean securityNotificationEnabled) {
        this.securityNotificationEnabled = securityNotificationEnabled;
    }

    public boolean securityNotificationEnabled() {
        return securityNotificationEnabled != null && securityNotificationEnabled;
    }

    public InitialSecurityNotificationSettingSync setSecurityNotificationEnabled(Boolean securityNotificationEnabled) {
        this.securityNotificationEnabled = securityNotificationEnabled;
        return this;
    }
}
