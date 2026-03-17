package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

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

    public void setSecurityNotificationEnabled(Boolean securityNotificationEnabled) {
        this.securityNotificationEnabled = securityNotificationEnabled;
    }
}
